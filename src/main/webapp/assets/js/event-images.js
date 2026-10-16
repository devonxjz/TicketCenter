(function(root) {
    "use strict";

    const FIELD_NAMES = ["title", "description", "categoryId", "venueName", "venueAddress",
        "saleStart", "saleEnd", "startTime", "endTime"
    ];
    const TIME_NAMES = ["saleStart", "saleEnd", "startTime", "endTime"];
    const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const MAX_FILE_BYTES = 5 * 1024 * 1024;

    function validateFile(file) {
        if (!file) return;
        if (!["image/png", "image/jpeg"].includes(file.type)) throw new Error("Vui lòng chọn ảnh PNG hoặc JPEG.");
        if (file.size === 0) throw new Error("Ảnh đã chọn không có nội dung.");
        if (file.size > MAX_FILE_BYTES) throw new Error("Ảnh vượt quá giới hạn 5 MiB.");
    }

    function fieldsForRequest(values) {
        const fields = {};
        for (const name of FIELD_NAMES) {
            const value = String(values[name] || "").trim();
            if (!value) throw new Error("Vui lòng điền đầy đủ thông tin chương trình.");
            fields[name] = TIME_NAMES.includes(name) ? new Date(value).toISOString() : value;
        }
        if (!UUID.test(fields.categoryId)) throw new Error("Vui lòng chọn thể loại hợp lệ.");
        if (!(fields.saleStart < fields.saleEnd && fields.saleEnd <= fields.startTime && fields.startTime < fields
                .endTime)) {
            throw new Error("Lịch phải thỏa mãn mở bán < đóng bán ≤ bắt đầu < kết thúc.");
        }
        return fields;
    }

    function editorMode(status) {
        if (!status || ["DRAFT", "REJECTED"].includes(status)) return "edit";
        return status === "PUBLISHED" ? "cover" : "readonly";
    }

    function saveRequest(current, organizationId, values, file) {
        const mode = editorMode(current?.status);
        if (mode === "readonly") throw new Error("Trạng thái sự kiện không cho phép chỉnh sửa.");
        validateFile(file);
        if (mode === "cover") {
            if (!file) throw new Error("Chọn ảnh mới để thay ảnh bìa. Ảnh hiện tại đang được giữ nguyên.");
            return {
                path: "/api/events/" + current.id + "/cover",
                fields: {
                    expectedVersion: String(current.version)
                }
            };
        }
        if (!UUID.test(organizationId)) throw new Error("Vui lòng chọn tổ chức hợp lệ.");
        const fields = fieldsForRequest(values);
        if (current) fields.expectedVersion = String(current.version);
        return {
            path: current ? "/api/events/" + current.id + "/edit" : "/api/organizations/" + organizationId +
                "/events",
            fields
        };
    }

    function cloudCover(url) {
        try {
            const parsed = new URL(url);
            return parsed.protocol === "https:" && parsed.hostname === "res.cloudinary.com" &&
                !parsed.username && !parsed.password && parsed.pathname.includes("/image/upload/");
        } catch (_) {
            return false;
        }
    }

    function retryKey(storage, slot, fingerprint, randomUUID) {
        let previous;
        try {
            previous = JSON.parse(storage.getItem(slot));
        } catch (_) {
            previous = null;
        }
        if (previous && previous.fingerprint === fingerprint && UUID.test(previous.key)) return previous.key;
        const key = randomUUID();
        try {
            storage.setItem(slot, JSON.stringify({
                fingerprint,
                key
            }));
        } catch (_) {}
        return key;
    }

    async function fingerprint(fields, file, crypto) {
        const bytes = file ? await file.arrayBuffer() : new Uint8Array();
        const digest = await crypto.subtle.digest("SHA-256", bytes);
        const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
        return JSON.stringify([fields, file ? hash : null]);
    }

    const exported = {
        FIELD_NAMES,
        validateFile,
        fieldsForRequest,
        cloudCover,
        retryKey,
        fingerprint,
        editorMode,
        saveRequest
    };
    if (typeof module !== "undefined" && module.exports) {
        module.exports = exported;
        return;
    }
    root.EventImages = exported;

    const document = root.document;
    const page = document.body.dataset.eventPage;
    const context = document.body.dataset.context || "";
    const get = id => document.getElementById(id);
    const params = new URLSearchParams(root.location.search);
    const message = (text, error = false) => {
        get("event-message").textContent = text;
        get("event-message").className = error ? "alert alert-danger" : "text-muted";
    };

    async function api(path, options = {}) {
        let response;
        try {
            response = await root.fetch(context + path, {
                credentials: "same-origin",
                ...options
            });
        } catch (_) {
            throw new Error("Không thể kết nối. Vui lòng thử lại với cùng thông tin.");
        }
        let envelope;
        try {
            envelope = await response.json();
        } catch (_) {
            throw new Error("Máy chủ trả về dữ liệu không hợp lệ. Vui lòng thử lại.");
        }
        if (!response.ok) {
            if (envelope.error?.code === "REQUEST_ABORTED") {
                const error = new Error("Yêu cầu trước đã hết hạn. Bấm thử lưu lại để tạo yêu cầu mới.");
                error.code = "REQUEST_ABORTED";
                throw error;
            }
            if (response.status === 401) throw new Error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
            if (response.status === 403) throw new Error(envelope.error?.message ||
                "Bạn không có quyền quản lý sự kiện này.");
            if (response.status === 409) throw new Error(envelope.error?.message ||
                "Sự kiện đã thay đổi. Tải lại trang để xem phiên bản mới.");
            throw new Error(envelope.error?.message || "Chưa thể hoàn tất yêu cầu. Vui lòng thử lại.");
        }
        return envelope.data;
    }

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function dateText(value) {
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? "" : date.toLocaleString("vi-VN");
    }

    function localTime(value) {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "";
        const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
        return local.toISOString().slice(0, 23);
    }

    function cover(container, url, title, category, localPreview = false) {
        container.replaceChildren();
        const placeholder = () => {
            container.replaceChildren(element("div", "cover-placeholder", category || "Ảnh sự kiện"));
        };
        if (!localPreview && !cloudCover(url)) {
            placeholder();
            return;
        }
        const image = element("img", "event-cover");
        image.alt = title || "Ảnh bìa sự kiện";
        image.addEventListener("error", placeholder, {
            once: true
        });
        image.src = url;
        container.append(image);
    }

    function cards(events) {
        get("event-list").replaceChildren();
        for (const event of events) {
            if (!UUID.test(event.id)) continue;
            const column = element("div", "col-12 col-sm-6 col-lg-3");
            const card = element("a", "event-card");
            card.href = context + "/event-detail?id=" + encodeURIComponent(event.id);
            const image = element("div", "event-image");
            cover(image, event.coverImageUrl, event.title, event.categoryName);
            const content = element("div", "event-content");
            content.append(element("h2", "event-title", event.title),
                element("div", "event-meta", dateText(event.startTime)),
                element("div", "event-meta", event.venueName));
            card.append(image, content);
            column.append(card);
            get("event-list").append(column);
        }
        message(events.length ? "" : "Chưa có sự kiện đang công bố.");
    }

    async function catalog() {
        const events = await api("/api/events");
        cards(events);
        const highlights = events.filter(event => UUID.test(event.id)).slice(0, 3);
        for (const [index, event] of highlights.entries()) {
            const item = element("div", "carousel-item" + (index === 0 ? " active" : ""));
            const link = element("a");
            link.href = context + "/event-detail?id=" + encodeURIComponent(event.id);
            cover(link, event.coverImageUrl, event.title, event.categoryName);
            item.append(link);
            get("event-highlights").append(item);
        }
        get("heroCarousel").hidden = highlights.length === 0;
    }

    function statusText(status) {
        return {
            DRAFT: "Bản nháp",
            REJECTED: "Bị từ chối",
            PENDING_APPROVAL: "Chờ duyệt",
            PUBLISHED: "Đã công bố",
            CANCELLED: "Đã hủy",
            COMPLETED: "Đã kết thúc"
        } [status] || status;
    }

    function managerRows(events) {
        get("event-list").replaceChildren();
        for (const event of events) {
            if (!UUID.test(event.id)) continue;
            const row = element("tr");
            const imageCell = element("td");
            imageCell.style.width = "140px";
            cover(imageCell, event.coverImageUrl, event.title, event.categoryName);
            const title = element("td");
            title.append(element("strong", "d-block", event.title), element("span", "small text-muted", event.id));
            const actions = element("td");
            const edit = element("a", "btn btn-sm btn-dark rounded-0 me-2", "Sửa");
            edit.href = context + "/organizer/event-editor?id=" + encodeURIComponent(event.id);
            actions.append(edit);
            if (event.status === "PUBLISHED") {
                const view = element("a", "btn btn-sm btn-outline-dark rounded-0 me-2", "Xem");
                view.href = context + "/event-detail?id=" + encodeURIComponent(event.id);
                actions.prepend(view);
            }
            const report = element("a", "btn btn-sm btn-outline-dark rounded-0", "Báo cáo");
            report.href = context + "/organizer/reports?eventId=" + encodeURIComponent(event.id);
            actions.append(report);
            row.append(imageCell, title, element("td", "", dateText(event.startTime)),
                element("td", "", event.venueName), element("td", "", statusText(event.status)), actions);
            get("event-list").append(row);
        }
        message(events.length ? "" : "Không có sự kiện phù hợp. Bạn có thể tạo bản nháp mới.");
    }

    function options(select, values, selected) {
        select.replaceChildren();
        for (const value of values) {
            const option = element("option", "", value.name);
            option.value = value.id;
            select.append(option);
        }
        if (selected && values.some(value => value.id === selected)) select.value = selected;
    }

    async function managerList() {
        const available = await api("/api/manager/event-options");
        const select = get("event-organization");
        options(select, available.organizations, params.get("organizationId"));
        if (!available.organizations.length) {
            message("Bạn chưa có tổ chức với quyền quản lý.");
            return;
        }
        select.disabled = false;
        let generation = 0;
        let loaded = [];
        const render = () => {
            const query = get("event-search").value.trim().toLocaleLowerCase("vi-VN");
            managerRows(loaded.filter(event => [event.title, event.venueName].some(value =>
                String(value || "").toLocaleLowerCase("vi-VN").includes(query))));
        };
        get("event-search").addEventListener("input", render);
        const load = async () => {
            const current = ++generation;
            loaded = [];
            message("Đang tải sự kiện...");
            get("event-list").replaceChildren();
            get("event-create-link").href = context + "/organizer/events/new?organizationId=" +
                encodeURIComponent(select.value);
            try {
                const events = await api("/api/manager/events?organizationId=" + encodeURIComponent(
                    select.value));
                if (current === generation) {
                    loaded = events;
                    render();
                }
            } catch (error) {
                if (current === generation) message(error.message, true);
            }
        };
        select.addEventListener("change", load);
        await load();
    }

    async function detail() {
        const id = params.get("id");
        if (!UUID.test(id || "")) throw new Error("Chọn một sự kiện từ danh sách để xem chi tiết.");
        const event = await api("/api/events/" + encodeURIComponent(id));
        get("event-title").textContent = event.title;
        document.title = event.title + " | TicketsCenter";
        get("event-description").textContent = event.description;
        get("event-start").textContent = dateText(event.startTime);
        get("event-end").textContent = dateText(event.endTime);
        get("event-venue").textContent = event.venueName;
        get("event-address").textContent = event.venueAddress;
        get("event-sale").textContent = dateText(event.saleStart) + " đến " + dateText(event.saleEnd);
        cover(get("event-cover"), event.coverImageUrl, event.title, event.categoryName);
        get("event-detail").hidden = false;
        message("");
    }

    async function editor() {
        const form = get("event-form");
        const fieldset = get("event-fields");
        const fileInput = get("event-file");
        const organization = get("event-organization");
        let eventId = params.get("id");
        if (eventId && !UUID.test(eventId)) throw new Error("Mã sự kiện không hợp lệ.");
        let current = null;
        let previewUrl = null;
        let pending = false;
        const available = await api("/api/manager/event-options");
        options(organization, available.organizations, params.get("organizationId"));
        options(get("categoryId"), available.categories);
        if (!available.organizations.length || !available.categories.length) {
            throw new Error("Chưa có tổ chức hoặc thể loại để tạo sự kiện.");
        }
        const originalPreview = () => {
            cover(get("event-cover-preview"), current?.coverImageUrl, current?.title,
                get("categoryId").selectedOptions[0]?.textContent);
        };
        const releasePreview = () => {
            if (previewUrl) root.URL.revokeObjectURL(previewUrl);
            previewUrl = null;
        };
        const fill = event => {
            current = event;
            eventId = event.id;
            for (const name of FIELD_NAMES) get(name).value = TIME_NAMES.includes(name) ? localTime(event[
                name]) : event[name] || "";
            organization.value = event.organizationId;
            organization.disabled = true;
            get("editor-heading").textContent = "Biên tập sự kiện";
            originalPreview();
        };
        const applyMode = () => {
            const mode = editorMode(current?.status);
            fieldset.disabled = pending || mode === "readonly";
            for (const name of FIELD_NAMES) get(name).disabled = mode !== "edit";
            organization.disabled = current !== null;
            get("event-save").textContent = mode === "cover" ? "Lưu ảnh bìa" : "Lưu bản nháp";
            get("event-file-help").textContent = mode === "cover" ?
                "Sự kiện đã công bố. Chọn ảnh PNG hoặc JPEG tối đa 5 MiB để thay ảnh bìa." :
                "Tối đa 5 MiB. Không chọn ảnh mới để giữ ảnh hiện tại. Sự kiện mới không có ảnh dùng ảnh mặc định theo thể loại.";
            return mode;
        };
        if (eventId) fill(await api("/api/manager/events/" + encodeURIComponent(eventId)));
        else originalPreview();
        form.hidden = false;
        const mode = applyMode();
        message(mode === "readonly" ? "Sự kiện " + statusText(current.status).toLowerCase() +
            " không được sửa thông tin hoặc ảnh bìa." :
            mode === "cover" ? "Bạn có thể thay ảnh bìa của sự kiện đã công bố." : "");
        fileInput.addEventListener("change", () => {
            releasePreview();
            const file = fileInput.files[0];
            try {
                validateFile(file);
                fileInput.setCustomValidity("");
                if (file) {
                    previewUrl = root.URL.createObjectURL(file);
                    cover(get("event-cover-preview"), previewUrl, get("title").value, "", true);
                } else originalPreview();
                message("");
            } catch (error) {
                fileInput.setCustomValidity(error.message);
                originalPreview();
                message(error.message, true);
            }
        });
        get("categoryId").addEventListener("change", () => {
            if (!fileInput.files[0]) originalPreview();
        });
        root.addEventListener("pagehide", releasePreview, {
            once: true
        });
        const memory = new Map();
        const memoryStorage = {
            getItem: key => memory.get(key),
            setItem: (key, value) => memory.set(key, value),
            removeItem: key => memory.delete(key)
        };
        let storage;
        try {
            storage = root.sessionStorage;
            storage.setItem("tc:event-storage-check", "1");
            storage.removeItem("tc:event-storage-check");
        } catch (_) {
            storage = memoryStorage;
        }
        form.addEventListener("submit", async submit => {
            submit.preventDefault();
            if (pending || fieldset.disabled || !form.reportValidity()) return;
            pending = true;
            fieldset.disabled = true;
            let completed = false;
            let requestSlot = null;
            try {
                const file = fileInput.files[0];
                const values = Object.fromEntries(FIELD_NAMES.map(name => [name, get(name).value]));
                const {
                    path,
                    fields
                } = saveRequest(current, organization.value, values, file);
                const signature = await fingerprint(fields, file, root.crypto);
                const slot = "tc:event-request:" + path;
                requestSlot = slot;
                const key = retryKey(storage, slot, signature, () => root.crypto.randomUUID());
                const multipart = new root.FormData();
                for (const [name, value] of Object.entries(fields)) multipart.append(name, value);
                if (file) multipart.append("file", file);
                fieldset.disabled = true;
                message("Đang lưu sự kiện và ảnh bìa...");
                const csrf = await api("/api/auth/csrf");
                if (!csrf?.token) throw new Error(
                    "Không lấy được mã bảo vệ phiên. Vui lòng tải lại trang.");
                const saved = await api(path, {
                    method: "POST",
                    headers: {
                        "X-CSRF-Token": csrf.token,
                        "Idempotency-Key": key
                    },
                    body: multipart
                });
                releasePreview();
                fileInput.value = "";
                fill(saved);
                const query = new URLSearchParams({
                    id: saved.id
                });
                root.history.replaceState(null, "", root.location.pathname + "?" + query);
                completed = true;
                message("Đã lưu sự kiện.");
            } catch (error) {
                if (error.code === "REQUEST_ABORTED" && requestSlot) storage.removeItem(
                requestSlot);
                originalPreview();
                message(error.message, true);
            } finally {
                pending = false;
                applyMode();
                if (!completed) get("event-save").textContent = "Thử lưu lại";
            }
        });
    }

    const start = {
        catalog,
        detail,
        "manager-list": managerList,
        editor
    } [page];
    if (start) start().catch(error => message(error.message, true));
})(typeof window !== "undefined" ? window : globalThis);
