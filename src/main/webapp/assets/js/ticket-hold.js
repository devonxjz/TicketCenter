(function (root) {
    "use strict";

    const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const ZERO = "00000000-0000-0000-0000-000000000000";
    const uuid = value => typeof value === "string" && UUID.test(value) && value.toLowerCase() !== ZERO;
    const money = value => typeof value === "string" && /^(0|[1-9][0-9]*)$/.test(value);
    const instant = value => typeof value === "string" && value.endsWith("Z") && Number.isFinite(Date.parse(value));

    class ApiError extends Error {
        constructor(status, code, message) { super(message); this.status = status; this.code = code; }
    }

    function parseItems(items) {
        if (!Array.isArray(items) || !items.length || items.some(item => !uuid(item.zoneId) ||
            !(item.seatId === null || uuid(item.seatId)) || !Number.isInteger(item.quantity) ||
            item.quantity < 1 || item.quantity > 8 || (item.seatId !== null && item.quantity !== 1) ||
            !money(item.unitPrice)) || items.reduce((sum, item) => sum + item.quantity, 0) > 8) {
            throw new Error("Dữ liệu vé giữ không hợp lệ. Hãy cập nhật trạng thái.");
        }
        return items;
    }

    function currentHold(data) {
        if (!data || !instant(data.serverTime) || data.hold === undefined) throw new Error("Không đọc được thời gian máy chủ.");
        const hold = data.hold;
        if (hold !== null) {
            if (!uuid(hold.id) || !uuid(hold.eventId) || hold.status !== "ACTIVE" ||
                !instant(hold.createdAt) || !instant(hold.expiresAt) || Date.parse(hold.createdAt) >= Date.parse(hold.expiresAt)) {
                throw new Error("Dữ liệu lượt giữ không hợp lệ. Hãy cập nhật trạng thái.");
            }
            parseItems(hold.items);
        }
        return { serverTime: data.serverTime, hold: hold && Date.parse(hold.expiresAt) > Date.parse(data.serverTime) ? hold : null };
    }

    function catalog(data, eventId) {
        if (!data || data.eventId !== eventId || typeof data.title !== "string" || !instant(data.saleEnd) ||
            !instant(data.serverTime) || !Array.isArray(data.zones)) throw new Error("Không đọc được khu vé của sự kiện.");
        const ids = new Set();
        for (const zone of data.zones) {
            if (!uuid(zone.id) || ids.has(zone.id) || typeof zone.name !== "string" || !money(zone.unitPrice) ||
                !["STANDING", "SEATED"].includes(zone.type) || !Array.isArray(zone.availableSeats)) {
                throw new Error("Dữ liệu khu vé không hợp lệ.");
            }
            ids.add(zone.id);
            if (zone.type === "STANDING" && (!Number.isInteger(zone.availableStanding) || zone.availableStanding < 0 || zone.availableSeats.length)) {
                throw new Error("Số lượng vé đứng không hợp lệ.");
            }
            for (const seat of zone.availableSeats) {
                if (!uuid(seat.id) || ids.has(seat.id) || typeof seat.label !== "string") throw new Error("Dữ liệu ghế không hợp lệ.");
                ids.add(seat.id);
            }
        }
        return data;
    }

    function selectionPayload(options, selections) {
        if (!Array.isArray(selections) || !selections.length) throw new Error("Chọn ít nhất một vé.");
        let count = 0;
        const chosen = new Set();
        const normalized = selections.map(selection => {
            const zone = options.zones.find(value => value.id === selection.zoneId);
            const quantity = selection.quantity;
            const seatId = selection.seatId === undefined ? null : selection.seatId;
            if (!zone || !Number.isInteger(quantity) || quantity < 1 || quantity > 8) throw new Error("Lựa chọn vé không hợp lệ.");
            const identity = zone.id + ":" + seatId;
            if (chosen.has(identity)) throw new Error("Một ghế hoặc khu vé đứng chỉ được chọn một lần.");
            chosen.add(identity);
            if (zone.type === "STANDING" ? seatId !== null || quantity > zone.availableStanding :
                quantity !== 1 || !zone.availableSeats.some(seat => seat.id === seatId)) throw new Error("Vé đã chọn không còn trong danh sách khả dụng.");
            count += quantity;
            return { zoneId: zone.id, seatId, quantity };
        });
        if (count > 8) throw new Error("Mỗi lượt giữ được chọn tối đa 8 vé.");
        normalized.sort((a, b) => a.zoneId.localeCompare(b.zoneId) || String(a.seatId).localeCompare(String(b.seatId)));
        return { eventId: options.eventId, selections: normalized };
    }

    function total(items) { return items.reduce((sum, item) => sum + BigInt(item.unitPrice) * BigInt(item.quantity), 0n); }
    function vnd(value) { return BigInt(value).toLocaleString("vi-VN") + " đ"; }
    function countdown(milliseconds) {
        const seconds = Math.max(0, Math.ceil(milliseconds / 1000));
        return String(Math.floor(seconds / 60)).padStart(2, "0") + ":" + String(seconds % 60).padStart(2, "0");
    }

    /** @typedef {{phase: 'loading'|'selecting'|'submitting'|'uncertain'|'active'|'expired', hold: object|null, pending: object|null, options: object|null, message: string, auth: boolean}} HoldState */
    class HoldClient {
        constructor({ fetch, storage, randomUUID, now, eventId = null, page = "selection", changed = () => {} }) {
            this.fetch = fetch;
            this.storage = storage;
            this.randomUUID = randomUUID;
            this.now = now;
            this.eventId = uuid(eventId) ? eventId.toLowerCase() : null;
            this.page = page;
            this.changed = changed;
            this.actorId = null;
            this.anchor = null;
            this.state = { phase: "loading", hold: null, pending: null, options: null, message: "Đang đối chiếu lượt giữ...", auth: false };
        }

        set(phase, values = {}) {
            this.state = Object.freeze({ ...this.state, phase, ...values });
            this.changed(this.state);
        }

        async request(path, options = {}) {
            let response;
            try { response = await this.fetch(path, { credentials: "same-origin", cache: "no-store", ...options }); }
            catch (_) { throw new Error("Không thể kết nối. Hãy cập nhật hoặc thử lại cùng yêu cầu đã lưu."); }
            let data;
            try { data = await response.json(); } catch (_) { throw new Error("Máy chủ trả về dữ liệu không hợp lệ. Hãy cập nhật hoặc thử lại yêu cầu."); }
            if (!response.ok) throw new ApiError(response.status, data.error?.code,
                data.error?.message || "Chưa thể hoàn tất yêu cầu. Hãy thử lại.");
            return data;
        }

        async actor() {
            const response = await this.request("/api/me");
            if (!uuid(response.data?.id)) throw new Error("Không xác định được phiên đăng nhập hiện tại.");
            return response.data.id.toLowerCase();
        }

        slot() { return "tc:hold-command:v1:" + this.actorId; }

        restore() {
            const saved = this.storage.getItem(this.slot());
            if (saved === null) return null;
            const pending = JSON.parse(saved);
            if (pending.actorId !== this.actorId || !uuid(pending.key) || !["create", "cancel"].includes(pending.kind) || typeof pending.body !== "string") {
                throw new Error("Yêu cầu đã lưu không hợp lệ. Không gửi lại yêu cầu này.");
            }
            const body = JSON.parse(pending.body);
            if (pending.kind === "create") {
                if (pending.path !== "/api/holds" || !uuid(body.eventId) || !Array.isArray(body.selections) ||
                    !body.selections.length || body.selections.some(item => !uuid(item.zoneId) ||
                        !(item.seatId === null || uuid(item.seatId)) || !Number.isInteger(item.quantity) ||
                        item.quantity < 1 || item.quantity > 8 || (item.seatId !== null && item.quantity !== 1)) ||
                    body.selections.reduce((sum, item) => sum + item.quantity, 0) > 8) throw new Error("Lựa chọn đã lưu không hợp lệ.");
            } else if (!/^\/api\/holds\/[0-9a-f-]{36}\/cancel$/i.test(pending.path) ||
                !uuid(pending.path.split("/")[3]) || pending.body !== "{}") throw new Error("Yêu cầu hủy đã lưu không hợp lệ.");
            return Object.freeze(pending);
        }

        persist(kind, path, body) {
            if (this.restore()) throw new Error("Còn yêu cầu chưa rõ kết quả. Hãy cập nhật trạng thái để thử lại.");
            const pending = Object.freeze({ actorId: this.actorId, kind, path, body: JSON.stringify(body), key: this.randomUUID() });
            if (!uuid(pending.key)) throw new Error("Không tạo được mã yêu cầu an toàn.");
            this.set("submitting", { pending, message: "Đang gửi yêu cầu..." });
            this.storage.setItem(this.slot(), JSON.stringify(pending));
            if (this.storage.getItem(this.slot()) !== JSON.stringify(pending)) throw new Error("Không lưu được yêu cầu. Kiểm tra quyền lưu trữ của trình duyệt.");
            return pending;
        }

        async readCurrent() {
            const current = currentHold(await this.request("/api/me/hold"));
            this.anchor = { serverMs: Date.parse(current.serverTime), clientMs: this.now() };
            return current.hold;
        }

        remaining() {
            return this.state.hold && this.anchor ? Date.parse(this.state.hold.expiresAt) - this.anchor.serverMs - (this.now() - this.anchor.clientMs) : 0;
        }

        async reconcile({ receipt = false, message = "" } = {}) {
            const hold = await this.readCurrent();
            const pending = this.state.pending;
            if (pending) {
                this.set("uncertain", { hold, options: null, message: message || "Kết quả yêu cầu chưa rõ. Thử lại cùng yêu cầu đã lưu." });
            } else if (hold) {
                this.set("active", { hold, options: null, message: message || "Máy chủ đã xác nhận lượt giữ hiện tại." });
            } else if (this.page === "selection" && this.eventId) {
                const options = catalog(await this.request("/api/hold-options?eventId=" + encodeURIComponent(this.eventId)), this.eventId);
                this.anchor = { serverMs: Date.parse(options.serverTime), clientMs: this.now() };
                if (Date.parse(options.saleEnd) <= Date.parse(options.serverTime)) {
                    this.set("expired", { hold: null, options: null, message: "Sự kiện đã đóng bán." });
                } else {
                    this.set("selecting", { hold: null, options, message: message || (receipt ? "Lượt giữ trước không còn hiệu lực. Bạn có thể chọn vé mới." : "Chọn vé từ danh sách khả dụng hiện tại.") });
                }
            } else {
                this.set("expired", { hold: null, options: null, message: message || (receipt ? "Lượt giữ không còn hiệu lực." : "Bạn chưa có lượt giữ đang hiệu lực. Chọn một sự kiện để giữ vé.") });
            }
        }

        async refresh() {
            if (this.state.phase === "submitting") return;
            this.set("loading", { options: null, hold: null, auth: false, message: "Đang đối chiếu lượt giữ..." });
            try {
                this.actorId = await this.actor();
                this.set("loading", { pending: this.restore(), hold: null });
                await this.reconcile();
            } catch (error) { this.failed(error); }
        }

        failed(error) {
            const auth = error.status === 401 || error.status === 403;
            this.set("uncertain", { options: null, hold: auth ? null : this.state.hold, auth,
                message: error.message || "Không thể kết nối. Hãy thử lại cùng yêu cầu." });
        }

        async submit(selections) {
            if (this.state.phase !== "selecting" || this.state.pending) return;
            try {
                const body = selectionPayload(this.state.options, selections);
                const pending = this.persist("create", "/api/holds", body);
                await this.execute(pending);
            } catch (error) { if (this.state.pending) this.failed(error); else this.set("selecting", { message: error.message }); }
        }

        async cancel() {
            if (this.state.phase !== "active" || this.state.pending || this.remaining() <= 0) return;
            try {
                const pending = this.persist("cancel", "/api/holds/" + this.state.hold.id + "/cancel", {});
                await this.execute(pending);
            } catch (error) { this.failed(error); }
        }

        async retry() {
            if (this.state.phase !== "uncertain" || !this.state.pending) return;
            this.set("submitting", { auth: false, message: "Đang đối chiếu cùng yêu cầu đã lưu..." });
            await this.execute(this.state.pending);
        }

        async execute(pending) {
            let sent = false;
            try {
                if (await this.actor() !== pending.actorId) {
                    this.set("uncertain", { pending: null, hold: null, message: "Phiên đăng nhập đã đổi. Đang đọc lượt giữ của tài khoản hiện tại." });
                    await this.refresh();
                    return;
                }
                this.storage.setItem(this.slot(), JSON.stringify(pending));
                if (this.storage.getItem(this.slot()) !== JSON.stringify(pending)) throw new Error("Không lưu được yêu cầu. Kiểm tra quyền lưu trữ của trình duyệt.");
                const csrf = await this.request("/api/auth/csrf");
                if (typeof csrf.data?.token !== "string" || !csrf.data.token) throw new Error("Không lấy được mã bảo vệ phiên. Thử lại cùng yêu cầu.");
                sent = true;
                const receipt = await this.request(pending.path, { method: "POST", headers: {
                    "Content-Type": "application/json", "X-CSRF-Token": csrf.data.token, "Idempotency-Key": pending.key
                }, body: pending.body });
                if (!uuid(receipt?.id) || !uuid(receipt.eventId) || !instant(receipt.expiresAt) ||
                    (pending.kind === "create" ? receipt.eventId !== JSON.parse(pending.body).eventId : receipt.id !== pending.path.split("/")[3]) ||
                    receipt.outcome !== (pending.kind === "create" ? "CREATED" : "CANCELLED")) throw new Error("Chưa xác định được kết quả yêu cầu. Thử lại cùng mã yêu cầu.");
                this.storage.removeItem(this.slot());
                this.set("loading", { pending: null, message: "Đang kiểm tra hiệu lực lượt giữ..." });
                await this.reconcile({ receipt: true });
            } catch (error) {
                if (sent && this.state.pending === pending && error instanceof ApiError && [400, 404, 409, 413, 415].includes(error.status)) {
                    try {
                        this.storage.removeItem(this.slot());
                        this.set("loading", { pending: null, hold: null });
                        await this.reconcile({ message: error.message });
                    } catch (readError) { this.failed(readError); }
                } else { this.failed(error); }
            }
        }
    }

    const exported = { HoldClient, ApiError, selectionPayload, currentHold, catalog, total, vnd, countdown };
    if (typeof module !== "undefined" && module.exports) { module.exports = exported; return; }
    root.TicketHold = exported;

    const document = root.document;
    const context = document.body.dataset.context || "";
    const page = document.body.dataset.holdPage;
    const get = id => document.getElementById(id);
    const element = (tag, text) => { const node = document.createElement(tag); if (text !== undefined) node.textContent = text; return node; };
    const dateText = value => new Date(value).toLocaleString("vi-VN");
    let renderedOptions = null;
    let expiryRead = null;
    let client;

    function selections() {
        return Array.from(get("hold-zones")?.querySelectorAll("input") || []).filter(input =>
            input.type === "checkbox" ? input.checked : Number(input.value) > 0).map(input => ({
                zoneId: input.dataset.zoneId, seatId: input.dataset.seatId || null,
                quantity: input.type === "checkbox" ? 1 : Number(input.value)
            }));
    }

    function summary() {
        const selected = selections();
        const count = selected.reduce((sum, item) => sum + item.quantity, 0);
        const options = client.state.options;
        const items = selected.map(item => ({ ...item, unitPrice: options.zones.find(zone => zone.id === item.zoneId).unitPrice }));
        get("selection-count").textContent = "Đã chọn " + count + " / 8 vé";
        get("selection-total").textContent = selected.every(item => Number.isInteger(item.quantity) && item.quantity > 0 && item.quantity <= 8) ? vnd(total(items)) : "Lựa chọn không hợp lệ";
        let valid = false;
        try { selectionPayload(options, selected); valid = true; } catch (_) { }
        get("hold-submit").disabled = !valid || client.state.phase !== "selecting";
        for (const input of get("hold-zones").querySelectorAll("input[type=checkbox]")) input.disabled = count >= 8 && !input.checked;
    }

    function renderOptions(options) {
        get("hold-heading").textContent = options.title;
        get("sale-deadline").textContent = "Đóng bán lúc " + dateText(options.saleEnd);
        get("hold-zones").replaceChildren();
        for (const zone of options.zones) {
            const field = element("fieldset");
            field.className = "hold-zone";
            field.append(element("legend", zone.name + " · " + vnd(zone.unitPrice)));
            if (zone.type === "STANDING") {
                field.append(element("p", "Còn " + zone.availableStanding + " vé đứng"));
                const label = element("label", "Số lượng");
                const input = element("input");
                input.type = "number"; input.min = "0"; input.max = String(Math.min(8, zone.availableStanding)); input.step = "1"; input.value = "0";
                input.className = "hold-quantity"; input.dataset.zoneId = zone.id; input.disabled = zone.availableStanding === 0;
                label.append(input); field.append(label);
            } else {
                const seats = element("div"); seats.className = "hold-seats";
                if (!zone.availableSeats.length) seats.append(element("p", "Không còn ghế khả dụng."));
                for (const seat of zone.availableSeats) {
                    const label = element("label"); label.className = "hold-seat";
                    const input = element("input"); input.type = "checkbox"; input.dataset.zoneId = zone.id; input.dataset.seatId = seat.id;
                    label.append(input, element("span", seat.label)); seats.append(label);
                }
                field.append(seats);
            }
            get("hold-zones").append(field);
        }
        get("hold-zones").oninput = summary;
        summary();
    }

    function render(state) {
        document.body.dataset.holdState = state.phase;
        get("hold-message").textContent = state.message;
        get("hold-message").className = state.phase === "uncertain" ? "alert alert-warning" : "text-muted";
        get("hold-auth").hidden = !state.auth;
        get("hold-recovery").hidden = !state.pending;
        get("hold-retry").disabled = state.phase !== "uncertain";
        get("hold-refresh").disabled = ["loading", "submitting"].includes(state.phase);
        const form = get("hold-form");
        if (form) {
            form.hidden = state.phase !== "selecting";
            get("hold-zones").disabled = state.phase !== "selecting";
            if (state.options && state.options !== renderedOptions) {
                renderedOptions = state.options;
                renderOptions(state.options);
            }
        }
        get("current-hold").hidden = !state.hold;
        if (!state.hold) return;
        const hold = state.hold;
        get("hold-id").textContent = "Mã lượt giữ " + hold.id + " · Sự kiện " + hold.eventId;
        get("hold-deadline").textContent = "Hết hạn lúc " + dateText(hold.expiresAt);
        get("hold-items").replaceChildren();
        for (const item of hold.items) get("hold-items").append(element("li", "Khu " + item.zoneId +
            (item.seatId ? " · Ghế " + item.seatId : " · Vé đứng") + " · " + item.quantity + " vé × " + vnd(item.unitPrice)));
        get("hold-total").textContent = vnd(total(hold.items));
        get("hold-cancel").disabled = state.phase !== "active" || client.remaining() <= 0;
        const view = get("hold-view");
        view.href = context + (page === "selection" ? "/checkout" : "/event-detail?id=" + encodeURIComponent(hold.eventId));
        get("hold-countdown").textContent = countdown(client.remaining());
    }

    client = new HoldClient({ fetch: (path, options) => root.fetch(context + path, options),
        storage: { getItem: key => root.sessionStorage.getItem(key), setItem: (key, value) => root.sessionStorage.setItem(key, value), removeItem: key => root.sessionStorage.removeItem(key) },
        randomUUID: () => root.crypto.randomUUID(), now: () => root.performance.now(),
        eventId: new URLSearchParams(root.location.search).get("eventId"), page, changed: render });
    get("hold-form")?.addEventListener("submit", event => { event.preventDefault(); void client.submit(selections()); });
    get("hold-cancel").addEventListener("click", () => void client.cancel());
    get("hold-retry").addEventListener("click", () => void client.retry());
    get("hold-refresh").addEventListener("click", () => void client.refresh());
    const timer = root.setInterval(() => {
        if (client.state.hold) {
            const left = client.remaining();
            get("hold-countdown").textContent = countdown(left);
            if (left <= 0) {
                get("hold-cancel").disabled = true;
                if (expiryRead !== client.state.hold.id && !["loading", "submitting"].includes(client.state.phase)) {
                    expiryRead = client.state.hold.id;
                    void client.refresh();
                }
            }
        } else if (client.state.phase === "selecting" && client.anchor &&
            Date.parse(client.state.options.saleEnd) <= client.anchor.serverMs + root.performance.now() - client.anchor.clientMs) {
            client.set("expired", { options: null, message: "Sự kiện đã đóng bán. Hãy cập nhật trạng thái." });
        }
    }, 250);
    root.addEventListener("pagehide", () => root.clearInterval(timer), { once: true });
    void client.refresh();
})(typeof window !== "undefined" ? window : globalThis);
