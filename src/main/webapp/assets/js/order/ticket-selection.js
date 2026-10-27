(function(root) {
    "use strict";

    const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const ZERO = "00000000-0000-0000-0000-000000000000";
    const uuid = value => typeof value === "string" && UUID.test(value) && value.toLowerCase() !== ZERO;

    function resolveEventId(search) {
        const query = new URLSearchParams(search),
            value = query.get('eventId') || query.get('id');
        return uuid(value) ? value.toLowerCase() : null;
    }
    const money = value => typeof value === "string" && /^(0|[1-9][0-9]*)$/.test(value);
    const instant = value => typeof value === "string" && value.endsWith("Z") && Number.isFinite(Date.parse(value));

    class ApiError extends Error {
        constructor(status, code, message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    class StaleOperation extends Error {}

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
        if (!data || !instant(data.serverTime) || data.hold === undefined) throw new Error(
            "Không đọc được thời gian máy chủ.");
        const hold = data.hold;
        if (hold !== null) {
            if (!uuid(hold.id) || !uuid(hold.eventId) || hold.status !== "ACTIVE" ||
                !instant(hold.createdAt) || !instant(hold.expiresAt) || Date.parse(hold.createdAt) >= Date.parse(
                    hold.expiresAt)) {
                throw new Error("Dữ liệu lượt giữ không hợp lệ. Hãy cập nhật trạng thái.");
            }
            parseItems(hold.items);
        }
        return {
            serverTime: data.serverTime,
            hold: hold && Date.parse(hold.expiresAt) > Date.parse(data.serverTime) ? hold : null
        };
    }

    function catalog(data, eventId) {
        if (!data || data.eventId !== eventId || typeof data.title !== "string" || !instant(data.saleEnd) ||
            !instant(data.serverTime) || !Array.isArray(data.zones)) throw new Error(
            "Không đọc được khu vé của sự kiện.");
        const ids = new Set();
        for (const zone of data.zones) {
            if (!uuid(zone.id) || ids.has(zone.id) || typeof zone.name !== "string" || !money(zone.unitPrice) ||
                !["STANDING", "SEATED"].includes(zone.type) || !Array.isArray(zone.availableSeats)) {
                throw new Error("Dữ liệu khu vé không hợp lệ.");
            }
            ids.add(zone.id);
            if (zone.type === "STANDING" && (!Number.isInteger(zone.availableStanding) || zone.availableStanding <
                    0 || zone.availableSeats.length)) {
                throw new Error("Số lượng vé đứng không hợp lệ.");
            }
            for (const seat of zone.availableSeats) {
                if (!uuid(seat.id) || ids.has(seat.id) || typeof seat.label !== "string") throw new Error(
                    "Dữ liệu ghế không hợp lệ.");
                ids.add(seat.id);
            }
        }
        return data;
    }

    function seatRows(zone, payload) {
        if (!payload || !Array.isArray(payload.data)) throw new Error("Không đọc được sơ đồ ghế.");
        const allowed = new Set(zone.availableSeats.map(seat => seat.id.toLowerCase()));
        const seen = new Set(),
            rows = new Map();
        for (const seat of payload.data) {
            if (!uuid(seat.seatId) || !uuid(seat.zoneId) || seat.zoneId.toLowerCase() !== zone.id.toLowerCase() ||
                seen.has(seat.seatId.toLowerCase()) || typeof seat.rowName !== 'string' ||
                !Number.isInteger(seat.seatNumber) || typeof seat.seatLabel !== 'string' ||
                !['AVAILABLE', 'HELD', 'SOLD'].includes(seat.status)) throw new Error('Dữ liệu ghế không hợp lệ.');
            seen.add(seat.seatId.toLowerCase());
            const selectable = seat.status === 'AVAILABLE' && allowed.has(seat.seatId.toLowerCase());
            const value = {
                id: seat.seatId.toLowerCase(),
                label: seat.seatLabel,
                number: seat.seatNumber,
                selectable,
                status: seat.status === 'AVAILABLE' && !selectable ? 'UNAVAILABLE' : seat.status
            };
            if (!rows.has(seat.rowName)) rows.set(seat.rowName, []);
            rows.get(seat.rowName).push(value);
        }
        for (const seat of zone.availableSeats)
            if (!seen.has(seat.id.toLowerCase())) {
                if (!rows.has('')) rows.set('', []);
                rows.get('').push({
                    id: seat.id,
                    label: seat.label,
                    number: 0,
                    status: 'AVAILABLE',
                    selectable: true
                });
            }
        return Array.from(rows, ([name, seats]) => ({
                name,
                seats: seats.sort((a, b) => a.number - b.number || a.label.localeCompare(b.label, 'vi', {
                    numeric: true
                }))
            }))
            .sort((a, b) => a.name.localeCompare(b.name, 'vi', {
                numeric: true
            }));
    }

    function selectionPayload(options, selections) {
        if (!Array.isArray(selections) || !selections.length) throw new Error("Chọn ít nhất một vé.");
        let count = 0;
        const chosen = new Set();
        const normalized = selections.map(selection => {
            const zone = options.zones.find(value => value.id === selection.zoneId);
            const quantity = selection.quantity;
            const seatId = selection.seatId === undefined ? null : selection.seatId;
            if (!zone || !Number.isInteger(quantity) || quantity < 1 || quantity > 8) throw new Error(
                "Lựa chọn vé không hợp lệ.");
            const identity = zone.id + ":" + seatId;
            if (chosen.has(identity)) throw new Error("Một ghế hoặc khu vé đứng chỉ được chọn một lần.");
            chosen.add(identity);
            if (zone.type === "STANDING" ? seatId !== null || quantity > zone.availableStanding :
                quantity !== 1 || !zone.availableSeats.some(seat => seat.id === seatId)) throw new Error(
                "Vé đã chọn không còn trong danh sách khả dụng.");
            count += quantity;
            return {
                zoneId: zone.id,
                seatId,
                quantity
            };
        });
        if (count > 8) throw new Error("Mỗi lượt giữ được chọn tối đa 8 vé.");
        normalized.sort((a, b) => a.zoneId.localeCompare(b.zoneId) || String(a.seatId).localeCompare(String(b
            .seatId)));
        return {
            eventId: options.eventId,
            selections: normalized
        };
    }

    function total(items) {
        return items.reduce((sum, item) => sum + BigInt(item.unitPrice) * BigInt(item.quantity), 0n);
    }

    function vnd(value) {
        return BigInt(value).toLocaleString("vi-VN") + " đ";
    }
    const displayText = value => typeof value === "string" ? value.trim() : "";

    function countdown(milliseconds) {
        const seconds = Math.max(0, Math.ceil(milliseconds / 1000));
        return String(Math.floor(seconds / 60)).padStart(2, "0") + ":" + String(seconds % 60).padStart(2, "0");
    }

    /** @typedef {{phase: 'loading'|'selecting'|'submitting'|'uncertain'|'active'|'expired', hold: object|null, pending: object|null, options: object|null, message: string, auth: boolean}} HoldState */
    class HoldClient {
        constructor({
            fetch,
            storage,
            randomUUID,
            now,
            eventId = null,
            page = "selection",
            changed = () => {}
        }) {
            this.fetch = fetch;
            this.storage = storage;
            this.randomUUID = randomUUID;
            this.now = now;
            this.eventId = uuid(eventId) ? eventId.toLowerCase() : null;
            this.page = page;
            this.changed = changed;
            this.actorId = null;
            this.anchor = null;
            this.epoch = 0;
            this.state = {
                phase: "loading",
                hold: null,
                pending: null,
                options: null,
                message: "Đang đối chiếu lượt giữ...",
                auth: false
            };
        }

        set(phase, values = {}) {
            this.state = Object.freeze({
                ...this.state,
                phase,
                ...values
            });
            this.changed(this.state);
        }

        begin(actorId) {
            return Object.freeze({
                epoch: ++this.epoch,
                actorId
            });
        }

        owns(operation) {
            return operation.epoch === this.epoch && (operation.actorId === null || operation.actorId === this
                .actorId);
        }

        requireCurrent(operation) {
            if (!this.owns(operation)) throw new StaleOperation();
        }

        apply(operation, phase, values, anchor = this.anchor) {
            this.requireCurrent(operation);
            this.anchor = anchor;
            this.set(phase, values);
        }

        async read(operation, path, options) {
            this.requireCurrent(operation);
            try {
                const response = await this.request(path, options);
                this.requireCurrent(operation);
                return response;
            } catch (error) {
                this.requireCurrent(operation);
                throw error;
            }
        }

        async request(path, options = {}) {
            let response;
            try {
                response = await this.fetch(path, {
                    credentials: "same-origin",
                    cache: "no-store",
                    ...options
                });
            } catch (_) {
                throw new Error("Không thể kết nối. Hãy cập nhật hoặc thử lại cùng yêu cầu đã lưu.");
            }
            let data;
            try {
                data = await response.json();
            } catch (_) {
                throw new Error("Máy chủ trả về dữ liệu không hợp lệ. Hãy cập nhật hoặc thử lại yêu cầu.");
            }
            if (!response.ok) throw new ApiError(response.status, data.error?.code,
                data.error?.message || "Chưa thể hoàn tất yêu cầu. Hãy thử lại.");
            return data;
        }

        async actor(operation) {
            const response = await this.read(operation, "/api/me");
            if (!uuid(response.data?.id)) throw new Error("Không xác định được phiên đăng nhập hiện tại.");
            return response.data.id.toLowerCase();
        }

        slot(actorId = this.actorId) {
            return "tc:hold-command:v1:" + actorId;
        }

        matches(pending, saved) {
            try {
                const stored = JSON.parse(saved);
                return stored.actorId === pending.actorId && stored.key === pending.key && stored.body ===
                    pending.body &&
                    stored.kind === pending.kind && stored.path === pending.path;
            } catch (_) {
                return false;
            }
        }

        store(operation, pending) {
            this.requireCurrent(operation);
            const slot = this.slot(pending.actorId);
            const saved = this.storage.getItem(slot);
            if (saved !== null && !this.matches(pending, saved)) throw new Error(
                "Yêu cầu đã lưu đã thay đổi. Hãy cập nhật trạng thái.");
            this.storage.setItem(slot, JSON.stringify(pending));
            if (!this.matches(pending, this.storage.getItem(slot))) throw new Error(
                "Không lưu được yêu cầu. Kiểm tra quyền lưu trữ của trình duyệt.");
        }

        forget(operation, pending) {
            this.requireCurrent(operation);
            const slot = this.slot(pending.actorId);
            if (!this.matches(pending, this.storage.getItem(slot))) return false;
            this.storage.removeItem(slot);
            return true;
        }

        restore() {
            const saved = this.storage.getItem(this.slot());
            if (saved === null) return null;
            const pending = JSON.parse(saved);
            if (pending.actorId !== this.actorId || !uuid(pending.key) || !["create", "cancel"].includes(pending
                    .kind) || typeof pending.body !== "string") {
                throw new Error("Yêu cầu đã lưu không hợp lệ. Không gửi lại yêu cầu này.");
            }
            const body = JSON.parse(pending.body);
            if (pending.kind === "create") {
                if (pending.path !== "/api/holds" || !uuid(body.eventId) || !Array.isArray(body.selections) ||
                    !body.selections.length || body.selections.some(item => !uuid(item.zoneId) ||
                        !(item.seatId === null || uuid(item.seatId)) || !Number.isInteger(item.quantity) ||
                        item.quantity < 1 || item.quantity > 8 || (item.seatId !== null && item.quantity !== 1)
                        ) ||
                    body.selections.reduce((sum, item) => sum + item.quantity, 0) > 8) throw new Error(
                    "Lựa chọn đã lưu không hợp lệ.");
            } else if (!/^\/api\/holds\/[0-9a-f-]{36}\/cancel$/i.test(pending.path) ||
                !uuid(pending.path.split("/")[3]) || pending.body !== "{}") throw new Error(
                "Yêu cầu hủy đã lưu không hợp lệ.");
            return Object.freeze(pending);
        }

        persist(operation, kind, path, body) {
            if (this.restore()) throw new Error(
                "Còn yêu cầu chưa rõ kết quả. Hãy cập nhật trạng thái để thử lại.");
            const pending = Object.freeze({
                actorId: this.actorId,
                kind,
                path,
                body: JSON.stringify(body),
                key: this.randomUUID()
            });
            if (!uuid(pending.key)) throw new Error("Không tạo được mã yêu cầu an toàn.");
            this.apply(operation, "submitting", {
                pending,
                message: "Đang gửi yêu cầu..."
            });
            this.store(operation, pending);
            return pending;
        }

        remaining() {
            return this.state.hold && this.anchor ? Date.parse(this.state.hold.expiresAt) - this.anchor
                .serverMs - (this.now() - this.anchor.clientMs) : 0;
        }

        async reconcile(operation, {
            receipt = false,
            message = ""
        } = {}) {
            const current = currentHold(await this.read(operation, "/api/me/hold"));
            const hold = current.hold;
            let anchor = {
                serverMs: Date.parse(current.serverTime),
                clientMs: this.now()
            };
            const pending = this.state.pending;
            if (pending) {
                this.apply(operation, "uncertain", {
                    hold,
                    options: null,
                    message: message || "Kết quả yêu cầu chưa rõ. Thử lại cùng yêu cầu đã lưu."
                }, anchor);
            } else if (hold) {
                this.apply(operation, "active", {
                    hold,
                    options: null,
                    message: message || "Máy chủ đã xác nhận lượt giữ hiện tại."
                }, anchor);
            } else if (this.page === "selection" && this.eventId) {
                const options = catalog(await this.read(operation, "/api/hold-options?eventId=" +
                    encodeURIComponent(this.eventId)), this.eventId);
                anchor = {
                    serverMs: Date.parse(options.serverTime),
                    clientMs: this.now()
                };
                if (Date.parse(options.saleEnd) <= Date.parse(options.serverTime)) {
                    this.apply(operation, "expired", {
                        hold: null,
                        options: null,
                        message: "Sự kiện đã đóng bán."
                    }, anchor);
                } else {
                    await Promise.all(options.zones.filter(zone => zone.type === 'SEATED').map(async zone => {
                        try {
                            zone.seatRows = seatRows(zone, await this.read(operation,
                                '/api/zones/' + zone.id + '/seats'));
                        } catch (error) {
                            this.requireCurrent(operation);
                            if (error.status === 401 || error.status === 403) throw error;
                            zone.seatRows = seatRows(zone, {
                                data: []
                            });
                            zone.layoutUnavailable = true;
                        }
                    }));
                    this.apply(operation, "selecting", {
                        hold: null,
                        options,
                        message: message || (receipt ?
                            "Lượt giữ trước không còn hiệu lực. Bạn có thể chọn vé mới." :
                            "Chọn vé từ danh sách khả dụng hiện tại.")
                    }, anchor);
                }
            } else {
                this.apply(operation, "expired", {
                    hold: null,
                    options: null,
                    message: message || (receipt ? "Lượt giữ không còn hiệu lực." :
                        "Bạn chưa có lượt giữ đang hiệu lực. Chọn một sự kiện để giữ vé.")
                }, anchor);
            }
        }

        async refresh() {
            let operation = this.begin(null);
            this.apply(operation, "loading", {
                pending: null,
                options: null,
                hold: null,
                auth: false,
                message: "Đang đối chiếu lượt giữ..."
            }, null);
            try {
                const actorId = await this.actor(operation);
                this.requireCurrent(operation);
                this.actorId = actorId;
                operation = Object.freeze({
                    ...operation,
                    actorId
                });
                this.apply(operation, "loading", {
                    pending: this.restore(),
                    hold: null
                });
                await this.reconcile(operation);
            } catch (error) {
                this.failed(operation, error);
            }
        }

        failed(operation, error) {
            if (!this.owns(operation)) return;
            const auth = error.status === 401 || error.status === 403;
            if (auth) ++this.epoch;
            this.set("uncertain", {
                options: null,
                hold: auth ? null : this.state.hold,
                auth,
                message: error.message || "Không thể kết nối. Hãy thử lại cùng yêu cầu."
            });
        }

        async submit(selections) {
            if (this.state.phase !== "selecting" || this.state.pending) return;
            const operation = this.begin(this.actorId);
            try {
                const body = selectionPayload(this.state.options, selections);
                const pending = this.persist(operation, "create", "/api/holds", body);
                await this.execute(operation, pending);
            } catch (error) {
                if (!this.owns(operation)) return;
                if (this.state.pending) this.failed(operation, error);
                else this.apply(operation, "selecting", {
                    message: error.message
                });
            }
        }

        async cancel() {
            if (this.state.phase !== "active" || this.state.pending || this.remaining() <= 0) return;
            const operation = this.begin(this.actorId);
            try {
                const pending = this.persist(operation, "cancel", "/api/holds/" + this.state.hold.id +
                    "/cancel", {});
                await this.execute(operation, pending);
            } catch (error) {
                this.failed(operation, error);
            }
        }

        async retry() {
            if (this.state.phase !== "uncertain" || !this.state.pending) return;
            const operation = this.begin(this.actorId);
            this.apply(operation, "submitting", {
                auth: false,
                message: "Đang đối chiếu cùng yêu cầu đã lưu..."
            });
            await this.execute(operation, this.state.pending);
        }

        async execute(operation, pending) {
            let sent = false;
            try {
                this.store(operation, pending);
                const csrf = await this.read(operation, "/api/auth/csrf");
                if (typeof csrf.data?.token !== "string" || !csrf.data.token) throw new Error(
                    "Không lấy được mã bảo vệ phiên. Thử lại cùng yêu cầu.");
                if (await this.actor(operation) !== pending.actorId) {
                    this.requireCurrent(operation);
                    await this.refresh();
                    return;
                }
                sent = true;
                const receipt = await this.read(operation, pending.path, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        "X-CSRF-Token": csrf.data.token,
                        "Idempotency-Key": pending.key
                    },
                    body: pending.body
                });
                if (!uuid(receipt?.id) || !uuid(receipt.eventId) || !instant(receipt.expiresAt) ||
                    (pending.kind === "create" ? receipt.eventId !== JSON.parse(pending.body).eventId : receipt
                        .id !== pending.path.split("/")[3]) ||
                    receipt.outcome !== (pending.kind === "create" ? "CREATED" : "CANCELLED")) throw new Error(
                    "Chưa xác định được kết quả yêu cầu. Thử lại cùng mã yêu cầu.");
                if (!this.forget(operation, pending)) {
                    await this.refresh();
                    return;
                }
                this.apply(operation, "loading", {
                    pending: null,
                    message: "Đang kiểm tra hiệu lực lượt giữ..."
                });
                await this.reconcile(operation, {
                    receipt: true
                });
            } catch (error) {
                if (!this.owns(operation)) return;
                if (sent && error instanceof ApiError && [400, 404, 409, 413, 415].includes(error.status)) {
                    try {
                        if (!this.forget(operation, pending)) {
                            await this.refresh();
                            return;
                        }
                        this.apply(operation, "loading", {
                            pending: null,
                            hold: null
                        });
                        await this.reconcile(operation, {
                            message: error.message
                        });
                    } catch (readError) {
                        this.failed(operation, readError);
                    }
                } else {
                    this.failed(operation, error);
                }
            }
        }
    }

    const exported = {
        HoldClient,
        ApiError,
        selectionPayload,
        currentHold,
        catalog,
        seatRows,
        total,
        vnd,
        countdown,
        resolveEventId
    };
    if (typeof module !== "undefined" && module.exports) {
        module.exports = exported;
        return;
    }
    root.TicketHold = exported;

    const document = root.document;
    const context = document.body.dataset.context || "";
    const page = document.body.dataset.holdPage;
    const get = id => document.getElementById(id);
    const element = (tag, text) => {
        const node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        return node;
    };
    const dateText = value => new Date(value).toLocaleString("vi-VN");
    let renderedOptions = null;
    let expiryRead = null;
    let payNow = false;
    let submittingHold = false;
    let seatZones = [];
    let client;

    function payOverlay(show, message) {
        let overlay = get("hold-pay-overlay");
        if (!show) {
            if (overlay) overlay.hidden = true;
            return;
        }
        if (!overlay) {
            overlay = element("div");
            overlay.id = "hold-pay-overlay";
            overlay.setAttribute("role", "status");
            overlay.style.cssText =
                "position:fixed;inset:0;z-index:2000;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;padding:24px;background:rgba(255,255,255,0.92);backdrop-filter:blur(6px);text-align:center";
            const spinner = element("div");
            spinner.className = "spinner-border text-primary";
            const text = element("p", message || "Đang giữ vé và chuyển đến trang thanh toán…");
            text.id = "hold-pay-overlay-text";
            text.className = "fw-semibold mb-0 text-dark";
            overlay.append(spinner, text);
            document.body.append(overlay);
        } else {
            const text = get("hold-pay-overlay-text");
            if (text && message) text.textContent = message;
        }
        overlay.hidden = false;
    }

    // Chuyển sang thanh toán đơn vé. Nếu autoPay=true thì tiến hành chuyển khoản payOS ngay.
    function openCheckout(holdId, autoPay = false) {
        root.location.assign(context + "/checkout?prepare=1&holdId=" + encodeURIComponent(holdId) + (autoPay ?
            "&pay=now" : ""));
    }

    function selections() {
        return ['hold-zones', 'standing-zones'].flatMap(id => Array.from(get(id)?.querySelectorAll('input') || []))
            .filter(input => !input.dataset.seatCount).filter(input =>
                input.type === "checkbox" ? input.checked : Number(input.value) > 0).map(input => ({
                zoneId: input.dataset.zoneId,
                seatId: input.dataset.seatId || null,
                quantity: input.type === "checkbox" ? 1 : Number(input.value)
            }));
    }

    // Khu ghế ngồi: chọn số lượng trước, sau đó chọn đúng bấy nhiêu ghế trong danh sách ghế còn trống máy chủ trả về.
    function syncSeatZones() {
        let wantedSeats = 0;
        const missing = [],
            invalid = [];
        for (const zone of seatZones) {
            if (zone.autoQuantity) zone.counter.value = String(zone.boxes.filter(box => box.checked).length);
            const raw = Number(zone.counter.value);
            const invalidCount = !Number.isInteger(raw) || raw < 0 || raw > Math.min(8, zone.boxes.length);
            if (invalidCount) invalid.push(zone.name);
            const wanted = Number.isInteger(raw) && raw > 0 ? Math.min(raw, zone.boxes.length, 8) : 0;
            const checked = zone.boxes.filter(box => box.checked);
            for (const extra of checked.slice(wanted)) extra.checked = false;
            const picked = Math.min(checked.length, wanted);
            zone.wanted = wanted;
            zone.picked = picked;
            if (!invalidCount && wanted === 0) zone.autoQuantity = true;
            zone.seats.hidden = false;
            zone.hint.hidden = false;
            zone.hint.textContent = invalidCount ? "Nhập số ghế nguyên từ 0 đến " + Math.min(8, zone.boxes.length) +
                "." : wanted === 0 ? "Bấm vào ghế trống để chọn, hoặc đặt số ghế trước." : picked === wanted ?
                "Đã chọn " + wanted + " ghế." : "Chọn thêm " + (wanted - picked) + " ghế (" + picked + " / " +
                wanted + ").";
            zone.hint.className = invalidCount ? "small text-danger fw-semibold mb-0" : wanted === 0 ?
                "small text-muted mb-0" : picked === wanted ? "small text-success mb-0" :
                "small text-danger fw-semibold mb-0";
            wantedSeats += wanted;
            if (picked < wanted) missing.push({
                name: zone.name,
                left: wanted - picked
            });
        }
        return {
            wantedSeats,
            missing,
            invalid
        };
    }

    function summary() {
        const seatState = syncSeatZones();
        const selected = selections();
        const count = selected.reduce((sum, item) => sum + item.quantity, 0);
        const standingCount = selected.filter(item => item.seatId === null).reduce((sum, item) => sum + item
            .quantity, 0);
        const plannedCount = standingCount + seatState.wantedSeats;
        const options = client.state.options;
        const items = selected.map(item => ({
            ...item,
            unitPrice: options.zones.find(zone => zone.id === item.zoneId).unitPrice
        }));
        const countEl = get("selection-count");
        if (countEl) {
            countEl.textContent = count > 8 || plannedCount > 8 ? "Đã chọn " + count + " / 8 vé (Tối đa 8 vé)" :
                "Đã chọn " + count + " / 8 vé";
            countEl.className = count > 8 || plannedCount > 8 ? "text-danger fw-bold" : "";
        }
        const validTotal = count <= 8 && plannedCount <= 8 && !seatState.invalid.length && selected.every(item =>
            Number.isInteger(item.quantity) && item.quantity > 0 && item.quantity <= 8);
        const totalEl = get("selection-total");
        if (totalEl) {
            totalEl.textContent = validTotal ? vnd(total(items)) : "Lựa chọn không hợp lệ";
        }
        const selectionList = get('selection-items');
        if (selectionList) {
            selectionList.replaceChildren();
            for (const item of items) {
                const zone = options.zones.find(zone => zone.id === item.zoneId),
                    seat = zone.availableSeats.find(seat => seat.id === item.seatId);
                const row = element('li');
                row.append(element('span', seat ? 'Ghế ' + seat.label + ' · ' + zone.name : item.quantity + ' × ' +
                        zone.name),
                    element('strong', Number.isInteger(item.quantity) && item.quantity > 0 && item.quantity <=
                        8 ? vnd(BigInt(item.unitPrice) * BigInt(item.quantity)) : 'Số lượng không hợp lệ'));
                selectionList.append(row);
            }
            if (count > 8 || plannedCount > 8) {
                const warn = element('li', 'Tổng số vé không được vượt quá 8 vé/lượt.');
                warn.className = 'text-danger fw-semibold';
                selectionList.append(warn);
            }
            for (const gap of seatState.missing) {
                const warn = element('li', 'Khu ' + gap.name + ': chọn thêm ' + gap.left + ' ghế.');
                warn.className = 'text-danger fw-semibold';
                selectionList.append(warn);
            }
            if (!items.length && !seatState.missing.length) selectionList.append(element('li', 'Chưa chọn vé.'));
        }
        let valid = false;
        try {
            selectionPayload(options, selected);
            valid = true;
        } catch (_) {}
        const submitBtn = get("hold-submit");
        if (submitBtn) {
            submitBtn.disabled = !valid || count > 8 || plannedCount > 8 || seatState.invalid.length > 0 ||
                seatState.missing.length > 0 || client.state.phase !== "selecting";
        }
        if (get("hold-pay-now")) get("hold-pay-now").disabled = !submitBtn || submitBtn.disabled;
        for (const zonesEl of [get('hold-zones'), get('standing-zones')].filter(Boolean)) {
            for (const input of zonesEl.querySelectorAll("input[type=checkbox]")) input.disabled = input.dataset
                .unavailable === 'true' || count >= 8 && !input.checked;
            for (const zone of seatZones) {
                for (const box of zone.boxes)
                    if (zone.autoQuantity && !box.checked && plannedCount >= 8) box.disabled = true;
                for (const box of zone.boxes)
                    if (!zone.autoQuantity && !box.checked && zone.picked >= zone.wanted) box.disabled = true;
                // Số ghế còn có thể thêm = 8 trừ các vé đã chọn ở khu khác.
                zone.counter.max = String(Math.max(0, Math.min(zone.boxes.length, 8 - (plannedCount - zone
                    .wanted))));
            }
            for (const input of zonesEl.querySelectorAll("input.hold-quantity")) {
                const val = Number(input.value) || 0;
                if (val > 8 || count > 8) {
                    input.classList.add("is-invalid");
                } else {
                    input.classList.remove("is-invalid");
                }
            }
            for (const button of zonesEl.querySelectorAll('button[data-quantity-step]')) {
                const input = button.quantityInput,
                    value = Number(input.value) || 0;
                button.disabled = client.state.phase !== 'selecting' || (button.dataset.quantityStep === '-1' ?
                    value <= 0 : value >= Number(input.max) || plannedCount >= 8);
            }
        }
    }

    function renderOptions(options) {
        if (document.body.dataset.publicPage !== 'detail') get("hold-heading").textContent = options.title;
        get("sale-deadline").textContent = "Đóng bán lúc " + dateText(options.saleEnd);
        get("hold-zones").replaceChildren();
        get('standing-zones')?.replaceChildren();
        const tabs = get('zone-tabs');
        tabs?.replaceChildren();
        const zonePanels = [];

        function quantityStepper(input, zoneName) {
            const stepper = element('div');
            stepper.className = 'hold-stepper';
            for (const delta of [-1, 1]) {
                const button = element('button', delta < 0 ? '−' : '+');
                button.type = 'button';
                button.dataset.quantityStep = String(delta);
                button.quantityInput = input;
                button.setAttribute('aria-label', (delta < 0 ? 'Giảm' : 'Tăng') + ' số lượng vé ' + zoneName);
                button.addEventListener('click', () => {
                    if (client.state.phase !== 'selecting') return;
                    const count = selections().filter(item => item.seatId === null).reduce((sum, item) =>
                        sum + item.quantity, 0) + seatZones.reduce((sum, zone) => sum + zone.wanted, 0);
                    const next = (Number(input.value) || 0) + delta;
                    if (next >= 0 && next <= Number(input.max) && (delta < 0 || count < 8)) {
                        if (input.seatZone) input.seatZone.autoQuantity = false;
                        input.value = String(next);
                        summary();
                    }
                });
                stepper.append(button);
                if (delta < 0) stepper.append(input);
            }
            return stepper;
        }

        function activateZone(index, focus = false, reveal = false) {
            if (client.state.phase !== 'selecting') return;
            zonePanels.forEach(({
                field,
                tab,
                zone
            }, position) => {
                const active = position === index;
                field.hidden = !active;
                if (tab) {
                    tab.setAttribute('aria-selected', String(active));
                    tab.tabIndex = active ? 0 : -1;
                    if (active && focus) tab.focus();
                    if (active && reveal) tab.scrollIntoView?.({
                        block: 'nearest',
                        inline: 'nearest'
                    });
                }
                if (active) {
                    get('hold-form').dataset.ticketLayout = zone.type === 'SEATED' ? 'seated' : 'standing';
                    if (get('seat-workspace')) get('seat-workspace').hidden = zone.type !== 'SEATED';
                    if (get('standing-zones')) get('standing-zones').hidden = zone.type !== 'STANDING';
                    if (get('seat-map-heading')) get('seat-map-heading').textContent = 'Chọn ghế · ' + zone
                        .name;
                }
            });
        }
        seatZones = [];
        for (const zone of options.zones) {
            const field = element("fieldset");
            field.id = 'zone-panel-' + zone.id;
            field.hidden = true;
            field.setAttribute('role', 'tabpanel');
            const tab = tabs ? element('button', zone.name) : null;
            if (tab) {
                const index = zonePanels.length;
                tab.type = 'button';
                tab.id = 'zone-tab-' + zone.id;
                tab.className = 'hold-zone-tab';
                tab.setAttribute('role', 'tab');
                tab.setAttribute('aria-controls', field.id);
                field.setAttribute('aria-labelledby', tab.id);
                tab.addEventListener('click', () => activateZone(index, false, true));
                tab.addEventListener('keydown', event => {
                    let next;
                    if (event.key === 'ArrowRight') next = (index + 1) % zonePanels.length;
                    else if (event.key === 'ArrowLeft') next = (index - 1 + zonePanels.length) % zonePanels
                        .length;
                    else if (event.key === 'Home') next = 0;
                    else if (event.key === 'End') next = zonePanels.length - 1;
                    else return;
                    event.preventDefault();
                    activateZone(next, true, true);
                });
                tabs.append(tab);
            }
            zonePanels.push({
                field,
                tab,
                zone
            });
            field.className = "hold-zone " + (zone.type === 'STANDING' ? 'hold-standing-tier' : /vip/i.test(zone
                .name) ? 'hold-seated-tier is-premium' : 'hold-seated-tier');
            field.append(element("legend", zone.type === 'STANDING' ? zone.name : zone.name + " · " + vnd(zone
                .unitPrice)));
            if (zone.type === "STANDING") {
                const availability = element('p', zone.availableStanding ? 'Vé đứng · Còn ' + zone
                    .availableStanding + ' vé' : 'Vé đứng · Đã hết vé');
                availability.className = 'hold-stock';
                field.append(availability);
                const controls = element('div');
                controls.className = 'hold-standing-controls';
                const price = element('strong', vnd(zone.unitPrice) + ' / vé');
                price.className = 'hold-standing-price';
                controls.append(price);
                const label = element("label", "Số lượng");
                label.className = 'hold-quantity-label';
                const input = element("input");
                input.type = "number";
                input.min = "0";
                input.max = String(Math.min(8, zone.availableStanding));
                input.step = "1";
                input.value = "0";
                input.className = "hold-quantity";
                input.dataset.zoneId = zone.id;
                input.disabled = zone.availableStanding === 0;
                input.setAttribute('aria-label', 'Số lượng vé ' + zone.name);
                label.append(quantityStepper(input, zone.name));
                controls.append(label);
                field.append(controls);
            } else {
                // Chọn số lượng trước; sơ đồ ghế thật (theo hàng, kèm ghế đang giữ/đã bán) hiện ra sau đó.
                const stock = element("p", zone.availableSeats.length ? "Vé ngồi · " + zone.availableSeats.length +
                    " ghế trống" : "Vé ngồi · Đã hết ghế trống");
                stock.className = "hold-seat-stock";
                field.append(stock);
                const countLabel = element("label", "Số ghế");
                countLabel.className = "hold-seat-count-label";
                const counter = element("input");
                counter.type = "number";
                counter.min = "0";
                counter.max = String(Math.min(8, zone.availableSeats.length));
                counter.step = "1";
                counter.value = "0";
                counter.className = "hold-quantity";
                counter.dataset.seatCount = "1";
                counter.setAttribute("aria-label", "Số ghế muốn đặt tại " + zone.name);
                counter.disabled = !zone.availableSeats.length;
                countLabel.append(quantityStepper(counter, zone.name));
                const hint = element("p");
                hint.hidden = true;
                const panel = element("div");
                panel.hidden = true;

                const seats = element("div");
                seats.className = "hold-seat-map";
                if (zone.layoutUnavailable) seats.append(element('p',
                    'Chưa tải được bố cục; danh sách bên dưới gồm các ghế còn trống do máy chủ xác nhận.'));
                const boxes = [];
                for (const row of zone.seatRows || seatRows(zone, {
                        data: []
                    })) {
                    const line = element('div');
                    line.className = 'hold-seat-row';
                    const name = element('span', row.name ? 'Hàng ' + row.name : 'Ghế');
                    name.className = 'hold-row-label';
                    line.append(name);
                    for (const seat of row.seats) {
                        const label = element("label");
                        label.className = "hold-seat seat-" + seat.status.toLowerCase();
                        const input = element("input");
                        input.type = "checkbox";
                        input.dataset.zoneId = zone.id;
                        input.dataset.seatId = seat.id;
                        input.dataset.unavailable = String(!seat.selectable);
                        input.disabled = !seat.selectable;
                        const status = {
                            AVAILABLE: 'Còn trống',
                            HELD: 'Đang được giữ',
                            SOLD: 'Đã bán',
                            UNAVAILABLE: 'Không còn khả dụng'
                        } [seat.status];
                        input.setAttribute('aria-label', 'Ghế ' + seat.label + ' · ' + status);
                        label.title = 'Ghế ' + seat.label + ' · ' + status;
                        label.append(input, element("span", seat.label));
                        line.append(label);
                        if (seat.selectable) boxes.push(input);
                    }
                    seats.append(line);
                }
                panel.append(seats);
                field.append(countLabel, hint, panel);
                const seatZone = {
                    name: zone.name,
                    counter,
                    hint,
                    seats: panel,
                    boxes,
                    wanted: 0,
                    picked: 0,
                    autoQuantity: true
                };
                counter.seatZone = seatZone;
                counter.addEventListener('input', () => {
                    seatZone.autoQuantity = false;
                });
                seatZones.push(seatZone);
            }
            (zone.type === 'STANDING' && get('standing-zones') ? get('standing-zones') : get('hold-zones')).append(
                field);
        }
        get("hold-zones").oninput = summary;
        if (get('standing-zones')) get('standing-zones').oninput = summary;
        activateZone(0);
        summary();
    }

    function render(state) {
        document.body.dataset.holdState = state.phase;
        get("hold-message").textContent = state.message;
        get("hold-message").className = state.phase === "uncertain" ? "alert alert-warning" : "text-muted";
        get("hold-auth").hidden = !state.auth;
        get("hold-recovery").hidden = state.phase !== "uncertain" || !state.pending;
        get("hold-retry").disabled = state.phase !== "uncertain";
        get("hold-refresh").disabled = ["loading", "submitting"].includes(state.phase);
        get("hold-cancel").disabled = state.phase !== "active" || client.remaining() <= 0;
        const form = get("hold-form");
        if (form) {
            form.hidden = state.phase !== "selecting";
            get("hold-zones").disabled = state.phase !== "selecting";
            if (get('standing-zones')) get('standing-zones').disabled = state.phase !== 'selecting';
            if (state.phase !== "selecting") {
                get("hold-submit").disabled = true;
                if (get("hold-pay-now")) get("hold-pay-now").disabled = true;
            }
            if (state.options && state.options !== renderedOptions) {
                renderedOptions = state.options;
                renderOptions(state.options);
            }
        }
        // "Thanh toán ngay": lượt giữ vừa được máy chủ xác nhận thì chuyển thẳng sang thanh toán.
        if (submittingHold && ["selecting", "expired", "uncertain"].includes(state.phase)) {
            submittingHold = false;
            payNow = false;
            payOverlay(false);
        }
        if (submittingHold && state.phase === "active" && state.hold) {
            const shouldAutoPay = payNow;
            submittingHold = false;
            payNow = false;
            openCheckout(state.hold.id, shouldAutoPay);
        }
        get("current-hold").hidden = !state.hold;
        if (page === "current") get("hold-heading").textContent = displayText(state.hold?.eventTitle) ||
            "Lượt giữ vé";
        if (!state.hold) return;
        const hold = state.hold;
        if (page === "selection" && document.body.dataset.publicPage !== 'detail') get("hold-heading").textContent =
            displayText(hold.eventTitle) || "Lượt giữ vé";
        get("hold-id").textContent = displayText(hold.eventTitle);
        get("hold-deadline").textContent = "Hết hạn lúc " + dateText(hold.expiresAt);
        get("hold-items").replaceChildren();
        for (const item of hold.items) {
            const zone = displayText(item.zoneName);
            const seat = displayText(item.seatLabel);
            const ticket = item.seatId ? (seat ? "Ghế " + seat : "Vé ghế") : "Vé đứng";
            get("hold-items").append(element("li", (zone ? "Khu " + zone + " · " : "") + ticket +
                " · " + item.quantity + " vé × " + vnd(item.unitPrice)));
        }
        get("hold-total").textContent = vnd(total(hold.items));
        const view = get("hold-view");
        view.href = context + (page === "selection" ? "/checkout" : "/event-detail?id=" + encodeURIComponent(hold
            .eventId));
        get("hold-countdown").textContent = countdown(client.remaining());
    }

    client = new HoldClient({
        fetch: (path, options) => root.fetch(context + path, options),
        storage: {
            getItem: key => root.sessionStorage.getItem(key),
            setItem: (key, value) => root.sessionStorage.setItem(key, value),
            removeItem: key => root.sessionStorage.removeItem(key)
        },
        randomUUID: () => root.crypto.randomUUID(),
        now: () => root.performance.now(),
        eventId: resolveEventId(root.location.search),
        page,
        changed: render
    });
    get("hold-form")?.addEventListener("submit", event => {
        event.preventDefault();
        payNow = event.submitter?.id === "hold-pay-now" || !event.submitter;
        submittingHold = true;
        payOverlay(true, payNow ? "Đang giữ vé và kết nối đến trang chuyển khoản…" :
            "Đang giữ chỗ vé cho bạn…");
        void client.submit(selections());
    });
    get("hold-pay-link")?.addEventListener("click", event => {
        if (client.state.phase !== "active" || !client.state.hold || client.remaining() <= 0) return;
        event.preventDefault();
        openCheckout(client.state.hold.id, true);
    });
    get("hold-cancel").addEventListener("click", () => void client.cancel());
    get("hold-retry").addEventListener("click", () => void client.retry());
    get("hold-refresh").addEventListener("click", () => void client.refresh());
    const tick = () => {
        if (client.state.hold) {
            const left = client.remaining();
            get("hold-countdown").textContent = countdown(left);
            if (left <= 0) {
                get("hold-cancel").disabled = true;
                if (expiryRead !== client.state.hold.id && !["loading", "submitting"].includes(client.state
                        .phase)) {
                    expiryRead = client.state.hold.id;
                    void client.refresh();
                }
            }
        } else if (client.state.phase === "selecting" && client.anchor &&
            Date.parse(client.state.options.saleEnd) <= client.anchor.serverMs + root.performance.now() - client
            .anchor.clientMs) {
            client.set("expired", {
                options: null,
                message: "Sự kiện đã đóng bán. Hãy cập nhật trạng thái."
            });
        }
    };
    let timer = root.setInterval(tick, 250);
    root.addEventListener("pagehide", () => {
        root.clearInterval(timer);
        timer = null;
    });
    root.addEventListener("pageshow", event => {
        if (!event.persisted) return;
        submittingHold = false;
        payNow = false;
        payOverlay(false);
        expiryRead = null;
        if (timer === null) timer = root.setInterval(tick, 250);
        void client.refresh();
    });
    void client.refresh();
})(typeof window !== "undefined" ? window : globalThis);
