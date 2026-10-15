(function (root) {
    "use strict";

    const holdApi = typeof module !== "undefined" && module.exports ? require("./ticket-hold.js") : root.TicketHold;
    const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const uuid = value => typeof value === "string" && UUID.test(value) && value !== "00000000-0000-0000-0000-000000000000";
    const money = value => typeof value === "string" && /^(0|[1-9][0-9]{0,18})$/.test(value);
    const instant = value => typeof value === "string" && value.endsWith("Z") && Number.isFinite(Date.parse(value));
    const coupon = value => typeof value === "string" && value.length > 0 && value.length <= 80 && !/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/u.test(value);
    const fail = () => { throw new Error("Dữ liệu đơn hàng không hợp lệ. Hãy đối chiếu lại trạng thái."); };

    function totals(data) {
        if (![data.subtotal, data.discountAmount, data.totalAmount].every(money)) fail();
        const subtotal = BigInt(data.subtotal), discount = BigInt(data.discountAmount);
        if (discount * 10n > subtotal * 3n || subtotal - discount !== BigInt(data.totalAmount)) fail();
    }

    function orderSnapshot(data, actorId, holdId) {
        if (!data || !uuid(data.orderId) || data.userId !== actorId || data.holdId !== holdId || !uuid(data.eventId) ||
            typeof data.orderCode !== "string" || !data.orderCode.length || data.orderCode.length > 64 ||
            !["PENDING_PAYMENT", "PAID", "CANCELLED", "EXPIRED"].includes(data.status) || !instant(data.expiresAt) ||
            !Number.isSafeInteger(data.version) || data.version < 0 ||
            !(data.appliedCouponCode === null || coupon(data.appliedCouponCode)) || !Array.isArray(data.items) || !data.items.length) fail();
        totals(data);
        const ids = new Set();
        let count = 0, subtotal = 0n;
        for (const item of data.items) {
            if (!uuid(item.orderItemId) || ids.has(item.orderItemId) || !uuid(item.zoneId) ||
                !(item.seatId === null || uuid(item.seatId)) || typeof item.zoneName !== "string" ||
                !(item.seatLabel === null || typeof item.seatLabel === "string") ||
                !Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 8 ||
                (item.seatId !== null && item.quantity !== 1) || !money(item.unitPrice) || !money(item.lineTotal) ||
                BigInt(item.unitPrice) * BigInt(item.quantity) !== BigInt(item.lineTotal)) fail();
            ids.add(item.orderItemId); count += item.quantity; subtotal += BigInt(item.lineTotal);
        }
        if (count > 8 || subtotal !== BigInt(data.subtotal)) fail();
        return data;
    }

    function checkout(data, actorId, holdId) {
        if (!data || !instant(data.serverTime) || data.order === undefined) fail();
        if (data.order !== null) {
            orderSnapshot(data.order, actorId, holdId);
            if (!["CLEAR", "PAYMENT_PENDING", "UNKNOWN"].includes(data.order.paymentResolution) ||
                !["ACTIVE", "CONSUMED", "RELEASED"].includes(data.order.holdStatus) || typeof data.order.canEditCoupon !== "boolean") fail();
        }
        return data;
    }

    function eligibility(data, orderId, code) {
        if (!data || !instant(data.serverTime) || data.orderId !== orderId || data.couponCode !== code ||
            typeof data.eligible !== "boolean" || !Number.isSafeInteger(data.remainingUses) || data.remainingUses < 0 ||
            !["ELIGIBLE", "NOT_FOUND", "WRONG_ORGANIZATION", "INACTIVE", "NOT_STARTED", "EXPIRED", "QUOTA_EXHAUSTED", "INVALID_CONFIGURATION"].includes(data.reason) ||
            !(data.validUntil === null || instant(data.validUntil))) fail();
        if (data.eligible ? data.reason !== "ELIGIBLE" || !money(data.discountAmount) || !money(data.totalAmount) :
            data.reason === "ELIGIBLE" || data.discountAmount !== null || data.totalAmount !== null) fail();
        return data;
    }

    class ApiError extends Error {
        constructor(status, body) { super(body.error?.message || "Chưa thể hoàn tất yêu cầu."); this.status = status; this.code = body.error?.code; }
    }
    class StaleOperation extends Error {}

    class OrderClient {
        constructor({ fetch, storage, randomUUID, now, changed = () => {} }) {
            Object.assign(this, { fetch, storage, randomUUID, now, changed });
            this.actorId = null; this.epoch = 0; this.anchor = null; this.holdId = null;
            this.state = { phase: "loading", hold: null, order: null, pending: null, preview: null, coupons: null, couponMessage: "", paymentRedirect: null, message: "Đang đối chiếu đơn hàng..." };
        }
        set(phase, values = {}) { this.state = Object.freeze({ ...this.state, phase, ...values }); this.changed(this.state); }
        begin(actorId = this.actorId) { return { epoch: ++this.epoch, actorId }; }
        owns(op) { return op.epoch === this.epoch && (op.actorId === null || op.actorId === this.actorId); }
        guard(op) { if (!this.owns(op)) throw new StaleOperation(); }
        apply(op, phase, values) { this.guard(op); this.set(phase, values); }
        async read(op, path, options = {}) {
            this.guard(op);
            try {
                let response;
                try { response = await this.fetch(path, { credentials: "same-origin", cache: "no-store", ...options }); }
                catch (_) { throw new Error("Không thể kết nối. Hãy đối chiếu hoặc thử lại cùng yêu cầu đã lưu."); }
                this.guard(op);
                let data;
                try { data = await response.json(); }
                catch (_) { throw new Error("Máy chủ trả dữ liệu không hợp lệ. Hãy thử lại cùng yêu cầu đã lưu."); }
                this.guard(op);
                if (!response.ok) throw new ApiError(response.status, data);
                return data;
            } catch (error) { this.guard(op); throw error; }
        }
        async identity(op) {
            const response = await this.read(op, "/api/me");
            if (!uuid(response.data?.id)) throw new Error("Không xác định được phiên đăng nhập.");
            return response.data.id.toLowerCase();
        }
        slot(actorId = this.actorId) { return "tc:order-command:v1:" + actorId; }
        restore() {
            const saved = this.storage.getItem(this.slot());
            if (saved === null) return null;
            const pending = JSON.parse(saved), body = JSON.parse(pending.body);
            if (pending.actorId !== this.actorId || !uuid(pending.key) || !uuid(pending.holdId) ||
                !(pending.kind === "create" && pending.path === "/api/orders" && body.holdId === pending.holdId && Object.keys(body).length === 1 ||
                    pending.kind === "coupon" && uuid(pending.orderId) && pending.path === "/api/orders/" + pending.orderId + "/coupon" &&
                    Object.keys(body).length === 1 && (body.couponCode === null || coupon(body.couponCode)) ||
                    pending.kind === "payment" && uuid(pending.orderId) && pending.path === "/api/orders/" + pending.orderId + "/payments" && Object.keys(body).length === 0)) {
                throw new Error("Yêu cầu đã lưu không hợp lệ. Không thể gửi lại.");
            }
            return Object.freeze(pending);
        }
        matches(pending) { return this.storage.getItem(this.slot(pending.actorId)) === JSON.stringify(pending); }
        save(op, pending) {
            this.guard(op);
            const saved = this.storage.getItem(this.slot(pending.actorId));
            if (saved !== null && saved !== JSON.stringify(pending)) throw new Error("Yêu cầu đã lưu đã thay đổi. Hãy đối chiếu lại.");
            this.storage.setItem(this.slot(pending.actorId), JSON.stringify(pending));
            if (!this.matches(pending)) throw new Error("Không lưu được yêu cầu an toàn. Kiểm tra quyền lưu trữ trình duyệt.");
        }
        forget(op, pending) { this.guard(op); if (!this.matches(pending)) return false; this.storage.removeItem(this.slot(pending.actorId)); return true; }
        remaining() { return this.anchor ? this.anchor.expiresMs - this.anchor.serverMs - (this.now() - this.anchor.clientMs) : 0; }
        editable() {
            const order = this.state.order;
            return this.state.phase === "ready" && !this.state.pending && this.remaining() > 0 && order && order.status === "PENDING_PAYMENT" &&
                order.holdStatus === "ACTIVE" && order.paymentResolution === "CLEAR" && order.canEditCoupon;
        }
        // Back from payOS leaves the payment PENDING; the server reuses it and its payOS link, so paying again is allowed.
        payable() {
            const order = this.state.order;
            return this.state.phase === "ready" && !this.state.pending && this.remaining() > 0 && order && order.status === "PENDING_PAYMENT" &&
                order.holdStatus === "ACTIVE" && ["CLEAR", "PAYMENT_PENDING"].includes(order.paymentResolution);
        }
        creatable() { return this.state.phase === "ready" && !this.state.pending && !this.state.order && this.state.hold && this.remaining() > 0; }
        async reconcile(op, message = "") {
            const current = holdApi.currentHold(await this.read(op, "/api/me/hold"));
            const pending = this.state.pending;
            const holdId = current.hold?.id || pending?.holdId || this.holdId;
            const result = holdId ? checkout(await this.read(op, "/api/checkout?holdId=" + encodeURIComponent(holdId)), this.actorId, holdId) :
                { serverTime: current.serverTime, order: null };
            this.guard(op);
            this.holdId = holdId;
            const expires = result.order?.expiresAt || current.hold?.expiresAt;
            this.anchor = expires ? { serverMs: Date.parse(result.serverTime), clientMs: this.now(), expiresMs: Date.parse(expires) } : null;
            const active = current.hold && (!result.order || result.order.holdId === current.hold.id);
            let coupons = null, couponMessage = "";
            if (!pending && active && (!result.order || result.order.canEditCoupon)) {
                try {
                    const choices = await this.read(op, "/api/checkout/coupons?holdId=" + encodeURIComponent(holdId));
                    const subtotal = result.order ? BigInt(result.order.subtotal) : current.hold.items.reduce((sum, item) => sum + BigInt(item.unitPrice) * BigInt(item.quantity), 0n);
                    if (!choices || choices.holdId !== holdId || !instant(choices.serverTime) || !Array.isArray(choices.coupons)) fail();
                    const codes = new Set();
                    for (const choice of choices.coupons) {
                        if (!coupon(choice.couponCode) || codes.has(choice.couponCode) || !money(choice.discountAmount) || !money(choice.totalAmount) ||
                            !instant(choice.validUntil) || Date.parse(choice.validUntil) <= Date.parse(choices.serverTime) ||
                            BigInt(choice.discountAmount) * 10n > subtotal * 3n || BigInt(choice.discountAmount) + BigInt(choice.totalAmount) !== subtotal) fail();
                        codes.add(choice.couponCode);
                    }
                    coupons = choices.coupons;
                } catch (error) {
                    this.guard(op);
                    if ([401, 403].includes(error.status)) throw error;
                    couponMessage = "Chưa tải được mã giảm giá. Chọn Cập nhật đơn hàng để thử lại.";
                }
            }
            this.apply(op, pending ? "uncertain" : "ready", { hold: active ? current.hold : null, order: result.order, preview: null, coupons, couponMessage, notice: Boolean(message),
                message: message || (pending ? "Kết quả yêu cầu chưa rõ. Thử lại cùng mã yêu cầu đã lưu." :
                    result.order ? "Đã đối chiếu trạng thái đơn hàng với máy chủ." : current.hold ? "Xác nhận tạo đơn từ vé đang giữ." : "Không còn lượt giữ hiệu lực. Hãy chọn vé lại.") });
        }
        async refresh() {
            let op = this.begin(null);
            this.apply(op, "loading", { hold: null, order: null, pending: null, preview: null, coupons: null, couponMessage: "", paymentRedirect: null, message: "Đang đối chiếu đơn hàng..." });
            try {
                const actorId = await this.identity(op); this.guard(op);
                if (actorId !== this.actorId) this.holdId = null;
                this.actorId = actorId; op = { ...op, actorId };
                this.apply(op, "loading", { pending: this.restore() });
                await this.reconcile(op);
            } catch (error) { this.failed(op, error); }
        }
        failed(op, error) {
            if (!this.owns(op)) return;
            const auth = [401, 403].includes(error.status);
            if (auth) { ++this.epoch; this.anchor = null; }
            this.set("uncertain", { hold: auth ? null : this.state.hold, order: auth ? null : this.state.order, coupons: auth ? null : this.state.coupons, preview: null,
                message: error instanceof SyntaxError ? "Yêu cầu đã lưu không hợp lệ. Không thể gửi lại." : error instanceof ApiError ? error.message :
                    error.message || "Không thể kết nối. Hãy thử lại cùng yêu cầu đã lưu." });
        }
        async command(kind, body) {
            if (kind === "create" ? !this.creatable() : kind === "payment" ? !this.payable() : !this.editable()) return;
            const op = this.begin();
            try {
                if (kind === "coupon" && body.couponCode !== null && !coupon(body.couponCode)) throw new Error("Nhập mã giảm giá từ 1 đến 80 ký tự.");
                if (this.restore()) throw new Error("Còn yêu cầu chưa rõ kết quả. Hãy thử lại yêu cầu đã lưu.");
                const pending = Object.freeze({ actorId: this.actorId, kind, holdId: this.holdId, orderId: this.state.order?.orderId || null,
                    path: kind === "create" ? "/api/orders" : "/api/orders/" + this.state.order.orderId + (kind === "payment" ? "/payments" : "/coupon"), body: JSON.stringify(body), key: this.randomUUID() });
                if (!uuid(pending.key)) throw new Error("Không tạo được mã yêu cầu an toàn.");
                this.save(op, pending);
                this.apply(op, "submitting", { pending, preview: null, message: "Đang gửi yêu cầu đơn hàng..." });
                await this.execute(op, pending);
            } catch (error) { this.failed(op, error); }
        }
        create() { return this.command("create", { holdId: this.holdId }); }
        applyCoupon(code) { return this.command("coupon", { couponCode: code }); }
        removeCoupon() { return this.command("coupon", { couponCode: null }); }
        pay() { return this.command("payment", {}); }
        async retry() {
            if (this.state.phase !== "uncertain" || !this.state.pending) return;
            const op = this.begin();
            this.apply(op, "submitting", { message: "Đang đối chiếu cùng yêu cầu đã lưu..." });
            await this.execute(op, this.state.pending);
        }
        async execute(op, pending) {
            let sent = false;
            try {
                this.save(op, pending);
                const csrf = await this.read(op, "/api/auth/csrf");
                if (typeof csrf.data?.token !== "string" || !csrf.data.token) throw new Error("Không lấy được mã bảo vệ phiên. Hãy thử lại.");
                if (await this.identity(op) !== pending.actorId) { await this.refresh(); return; }
                this.guard(op); sent = true;
                const receipt = await this.read(op, pending.path, { method: "POST", headers: {
                    "Content-Type": "application/json", "X-CSRF-Token": csrf.data.token, "Idempotency-Key": pending.key
                }, body: pending.body });
                if (pending.kind === "create") orderSnapshot(receipt, pending.actorId, pending.holdId);
                else if (pending.kind === "payment") {
                    if (receipt.currency !== "VND" || typeof receipt.zeroOrder !== "boolean" || !money(receipt.amount)) fail();
                    if (receipt.zeroOrder) {
                        if (receipt.orderId !== pending.orderId || receipt.amount !== "0" || receipt.status !== "PAID") fail();
                    } else {
                        if (!uuid(receipt.paymentId) || typeof receipt.txnRef !== "string" || !receipt.txnRef || typeof receipt.redirectUrl !== "string") fail();
                        const target = new URL(receipt.redirectUrl);
                        if (target.protocol !== "https:" || target.username || target.password) fail();
                    }
                }
                else {
                    if (receipt?.orderId !== pending.orderId || !(receipt.appliedCouponCode === null || coupon(receipt.appliedCouponCode)) ||
                        !Number.isSafeInteger(receipt.version) || receipt.version < 0) fail();
                    totals(receipt);
                }
                if (!this.forget(op, pending)) { await this.refresh(); return; }
                if (pending.kind === "payment" && !receipt.zeroOrder) {
                    this.apply(op, "redirecting", { pending: null, preview: null, paymentRedirect: receipt.redirectUrl, message: "Đang chuyển đến cổng thanh toán…" });
                    return;
                }
                this.apply(op, "loading", { pending: null, order: null, preview: null });
                await this.reconcile(op);
            } catch (error) {
                if (!this.owns(op)) return;
                if (sent && error instanceof ApiError && [400, 404, 409, 413, 415].includes(error.status)) {
                    try {
                        if (!this.forget(op, pending)) { await this.refresh(); return; }
                        this.apply(op, "loading", { pending: null, preview: null });
                        await this.reconcile(op, error.message);
                    } catch (readError) { this.failed(op, readError); }
                } else this.failed(op, error);
            }
        }
        async preview(code) {
            if (!this.editable()) return;
            const op = this.begin(), orderId = this.state.order.orderId;
            this.apply(op, "previewing", { preview: null, message: "Đang kiểm tra mã giảm giá..." });
            try {
                if (!coupon(code)) throw new Error("Nhập mã giảm giá từ 1 đến 80 ký tự.");
                if (await this.identity(op) !== this.actorId) { await this.refresh(); return; }
                const result = eligibility(await this.read(op, "/api/orders/" + orderId + "/coupon-eligibility?couponCode=" + encodeURIComponent(code)), orderId, code);
                if (result.eligible && (BigInt(result.discountAmount) + BigInt(result.totalAmount) !== BigInt(this.state.order.subtotal) ||
                    BigInt(result.discountAmount) * 10n > BigInt(this.state.order.subtotal) * 3n)) fail();
                this.apply(op, "ready", { preview: result, message: "Xem trước không giữ lượt sử dụng. Máy chủ kiểm tra lại khi áp dụng." });
            } catch (error) { this.failed(op, error); }
        }
        pause() { ++this.epoch; this.set("paused", { preview: null, message: "Đang chờ đối chiếu lại trạng thái." }); }
    }

    const exported = { OrderClient, checkout, orderSnapshot, eligibility };
    if (typeof module !== "undefined" && module.exports) { module.exports = exported; return; }
    root.OrderCheckout = exported;
    const document = root.document, context = document.body.dataset.context || "";
    const get = id => document.getElementById(id);
    if (!get("order-checkout")) return;
    const reason = { NOT_FOUND: "Không tìm thấy mã", WRONG_ORGANIZATION: "Mã không thuộc tổ chức của sự kiện", INACTIVE: "Mã đang tắt",
        NOT_STARTED: "Mã chưa bắt đầu", EXPIRED: "Mã đã hết hạn", QUOTA_EXHAUSTED: "Mã đã hết lượt", INVALID_CONFIGURATION: "Mã có cấu hình không hợp lệ" };
    const status = { PENDING_PAYMENT: "Chờ thanh toán", PAID: "Đã thanh toán", CANCELLED: "Đã hủy", EXPIRED: "Đã hết hạn" };
    let expiryTimer = null;
    const query = new URLSearchParams(root.location.search);
    let prepareOrder = query.get('prepare') === '1' || query.get('pay') === 'now';
    let autoPay = query.get('pay') === 'now';

    function autoPayOverlay(show, message) {
        const overlay = get("checkout-autopay-overlay");
        if (!overlay) return;
        overlay.hidden = !show;
        const text = get("checkout-autopay-text");
        if (text && message) text.textContent = message;
    }

    function advancePreparation(state, holdBusy) {
        if (holdBusy) return;
        if (query.get('holdId') && query.get('holdId') !== state.hold?.id) return;
        if (prepareOrder && state.phase === 'ready') {
            prepareOrder = false;
            if (client.creatable()) {
                if (autoPay) autoPayOverlay(true, "Đang khởi tạo đơn hàng và kết nối thanh toán…");
                root.setTimeout(() => void client.create(), 0);
                return;
            }
        }
        if (autoPay && state.phase === 'ready' && state.order && client.payable()) {
            autoPay = false;
            autoPayOverlay(true, "Đang mở cổng thanh toán chuyển khoản payOS VietQR…");
            root.setTimeout(() => void client.pay(), 0);
        }
        else if (autoPay && state.phase === 'ready' && !prepareOrder && !client.creatable() && !client.payable()) {
            autoPay = false;
            autoPayOverlay(false);
        }
    }
    const client = new OrderClient({ fetch: (path, options) => root.fetch(context + path, options), storage: {
        getItem: key => root.sessionStorage.getItem(key), setItem: (key, value) => root.sessionStorage.setItem(key, value), removeItem: key => root.sessionStorage.removeItem(key)
    }, randomUUID: () => root.crypto.randomUUID(), now: () => root.performance.now(), changed: render });
    function render(state) {
        root.clearTimeout(expiryTimer); expiryTimer = null;
        get("order-checkout").dataset.state = state.phase;
        get("order-message").textContent = state.message;
        get("order-message").hidden = state.phase === "ready" && !state.notice;
        get("order-recovery").hidden = state.phase !== "uncertain" || !state.pending;
        get("order-retry").disabled = state.phase !== "uncertain";
        get("order-create").hidden = !state.hold || !!state.order;
        const holdBusy = ["loading", "submitting", "uncertain"].includes(document.body.dataset.holdState);
        get("order-create").disabled = holdBusy || !client.creatable();
        get("order-refresh").disabled = state.phase === "submitting";
        get("order-summary").hidden = !state.order;
        get('order-coupon-form').hidden = !state.hold && !state.order;
        const select = get("order-coupon-code");
        select.disabled = holdBusy || !(client.creatable() || client.editable()) || state.coupons === null;
        select.replaceChildren();
        const empty = document.createElement("option");
        empty.value = ""; empty.textContent = "Không dùng mã giảm giá"; select.append(empty);
        for (const choice of state.coupons || []) {
            const option = document.createElement("option");
            option.value = choice.couponCode;
            option.textContent = choice.couponCode + " · Giảm " + holdApi.vnd(choice.discountAmount);
            select.append(option);
        }
        const applied = state.order?.appliedCouponCode;
        if (applied && !(state.coupons || []).some(choice => choice.couponCode === applied)) {
            const option = document.createElement("option"); option.value = applied;
            option.textContent = applied + " · Đang áp dụng"; select.append(option);
        }
        select.value = applied || "";
        get("order-coupon-help").textContent = state.couponMessage || (state.phase === "ready" && !(client.creatable() || client.editable()) ? "Đơn hàng này hiện không thể thay đổi mã giảm giá." : state.coupons === null ? "Đang đối chiếu ưu đãi…" :
            state.coupons.length ? "Chọn mã để tự động áp dụng. Số tiền thanh toán sẽ cập nhật bên dưới." : "Hiện chưa có mã giảm giá đủ điều kiện cho vé này.");
        if (get("order-pay")) {
            get("order-pay").disabled = holdBusy || !client.payable();
            get("order-pay").textContent = state.order?.totalAmount === "0" ? "Hoàn tất đơn miễn phí" : "Thanh toán ngay (payOS QR)";
            get("payment-message").textContent = state.phase === "redirecting" ? state.message : "";
            get("order-detail-link").hidden = !state.order;
            if (state.order) get("order-detail-link").href = context + "/orders/" + state.order.orderId;
            if (state.paymentRedirect && state.phase === "redirecting") {
                autoPayOverlay(true, "Đang chuyển đến cổng thanh toán VietQR payOS…");
                root.location.assign(state.paymentRedirect);
            }
        }
        if (state.phase === "failed" || state.phase === "uncertain") {
            autoPayOverlay(false);
        }
        const preview = state.preview;
        get("order-preview-result").textContent = preview ? preview.eligible ? "Mã " + preview.couponCode + " giảm dự kiến " + holdApi.vnd(preview.discountAmount) +
            ". Tổng dự kiến " + holdApi.vnd(preview.totalAmount) + ". Còn " + preview.remainingUses + " lượt tại " + new Date(preview.serverTime).toLocaleString("vi-VN") + "." : reason[preview.reason] : "";
        if (state.order) {
            const order = state.order;
            get("order-code").textContent = state.hold?.eventTitle || "Đơn vé của bạn";
            get("order-status").textContent = status[order.status] + (order.holdStatus !== "ACTIVE" ? ". Lượt giữ không còn hoạt động." :
                order.paymentResolution === "PAYMENT_PENDING" ? ". Giao dịch chưa hoàn tất, bạn có thể bấm Thanh toán ngay để mở lại payOS." :
                order.paymentResolution !== "CLEAR" ? ". Đang đối chiếu thanh toán, chưa thể đổi mã giảm giá." : "");
            get("order-items").replaceChildren();
            for (const item of order.items) {
                const node = document.createElement("li");
                node.textContent = item.zoneName + " · " + (item.seatId ? "Ghế " + (item.seatLabel || "chưa có nhãn") : "Vé đứng") +
                    " · " + item.quantity + " vé × " + holdApi.vnd(item.unitPrice) + " = " + holdApi.vnd(item.lineTotal);
                get("order-items").append(node);
            }
            get("order-subtotal").textContent = holdApi.vnd(order.subtotal);
            get("order-discount").textContent = holdApi.vnd(order.discountAmount);
            get("order-total").textContent = holdApi.vnd(order.totalAmount);
            get("order-applied-coupon").textContent = order.appliedCouponCode === null ? "" : "Mã đang áp dụng " + order.appliedCouponCode;
        }
        advancePreparation(state, holdBusy);
        if (["ready", "uncertain"].includes(state.phase) && client.remaining() > 0) {
            expiryTimer = root.setTimeout(() => void client.refresh(), Math.min(2147483647, client.remaining() + 1));
        }
    }
    get("order-create").addEventListener("click", () => void client.create());
    get("order-coupon-form").addEventListener("submit", event => event.preventDefault());
    get("order-coupon-code").addEventListener("change", async () => {
        if (!(client.creatable() || client.editable())) return;
        const code = get("order-coupon-code").value;
        if (code && !(client.state.coupons || []).some(choice => choice.couponCode === code)) return;
        if ((client.state.order?.appliedCouponCode || "") === code) return;
        const actorId = client.actorId, holdId = client.holdId;
        if (client.creatable()) await client.create();
        if (client.actorId === actorId && client.holdId === holdId && client.editable()) {
            if (code) await client.applyCoupon(code); else await client.removeCoupon();
        }
    });
    get("order-pay")?.addEventListener("click", async () => {
        if (!client.payable()) return;
        autoPayOverlay(true, "Đang mở cổng thanh toán chuyển khoản payOS VietQR…");
        await client.pay();
        if (client.state.phase !== "redirecting") autoPayOverlay(false);
    });
    get("order-retry").addEventListener("click", () => void client.retry());
    get("order-refresh").addEventListener("click", () => void client.refresh());
    for (const id of ["hold-cancel", "hold-retry", "hold-refresh"]) get(id).addEventListener("click", () => {
        client.pause();
    });
    const observer = new root.MutationObserver(() => {
        if (["loading", "submitting", "uncertain"].includes(document.body.dataset.holdState)) client.pause();
        else if (["active", "expired"].includes(document.body.dataset.holdState)) void client.refresh();
    });
    observer.observe(document.body, { attributes: true, attributeFilter: ["data-hold-state"] });
    root.addEventListener("pagehide", () => { root.clearTimeout(expiryTimer); expiryTimer = null; client.pause(); });
    root.addEventListener("pageshow", event => { if (event.persisted) { autoPayOverlay(false); void client.refresh(); } });
    root.addEventListener("focus", () => {
        // Native select popups can return window focus before committing their change.
        // Rebuilding checkout here would disable the select and discard that choice.
        if (document.activeElement?.tagName !== "SELECT") void client.refresh();
    });
    document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") void client.refresh(); else client.pause(); });
    void client.refresh();
})(typeof window !== "undefined" ? window : globalThis);
