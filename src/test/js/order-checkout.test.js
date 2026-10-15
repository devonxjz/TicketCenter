"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const { OrderClient, checkout, orderSnapshot, eligibility } = require("../../main/webapp/assets/js/order-checkout.js");

const ACTOR = "10000000-0000-4000-8000-000000000001";
const OTHER = "10000000-0000-4000-8000-000000000002";
const EVENT = "20000000-0000-4000-8000-000000000001";
const ZONE = "30000000-0000-4000-8000-000000000001";
const SEAT = "40000000-0000-4000-8000-000000000001";
const HOLD = "50000000-0000-4000-8000-000000000001";
const ORDER = "70000000-0000-4000-8000-000000000001";
const ITEM = "80000000-0000-4000-8000-000000000001";
const TIME = "2026-10-11T10:00:00Z";
const hold = () => ({ id: HOLD, eventId: EVENT, status: "ACTIVE", createdAt: TIME, expiresAt: "2026-10-11T10:10:00Z",
    items: [{ zoneId: ZONE, seatId: null, quantity: 2, unitPrice: "150000" }] });
const order = () => ({ orderId: ORDER, orderCode: "ORD-01", userId: ACTOR, eventId: EVENT, holdId: HOLD, status: "PENDING_PAYMENT",
    expiresAt: "2026-10-11T10:10:00Z", subtotal: "300000", discountAmount: "0", totalAmount: "300000", appliedCouponCode: null, version: 0,
    items: [{ orderItemId: ITEM, zoneId: ZONE, seatId: null, zoneName: "<img onerror=alert(1)>", seatLabel: null,
        quantity: 2, unitPrice: "150000", lineTotal: "300000" }], paymentResolution: "CLEAR", holdStatus: "ACTIVE", canEditCoupon: true });
const preview = (code = "SALE20") => ({ serverTime: TIME, orderId: ORDER, couponCode: code, eligible: true, reason: "ELIGIBLE",
    remainingUses: 1, discountAmount: "60000", totalAmount: "240000", validUntil: "2026-10-11T10:10:00Z" });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };

function fixture() {
    const values = new Map(), calls = [], server = { actor: ACTOR, hold: hold(), order: null, time: TIME, post: null, csrf: null, current: null, me: null, preview: null };
    let key = 0, clock = 0;
    const storage = { getItem: name => values.get(name) ?? null, setItem: (name, value) => values.set(name, value), removeItem: name => values.delete(name) };
    const reply = (body, status = 200) => ({ ok: status < 400, status, json: async () => body });
    const error = (status, code = "ORDER_UNAVAILABLE") => reply({ error: { code, message: "Máy chủ " + code } }, status);
    const fetch = async (path, options) => {
        calls.push({ path, ...options });
        if (path === "/api/me") return server.me ? server.me(reply, error) : reply({ data: { id: server.actor } });
        if (path === "/api/auth/csrf") return server.csrf ? server.csrf(reply, error) : reply({ data: { token: "fresh-token" } });
        if (path === "/api/me/hold") return reply({ serverTime: server.time, hold: server.hold });
        if (path.startsWith("/api/checkout?")) return server.current ? server.current(reply, error) : reply({ serverTime: server.time, order: server.order });
        if (path.startsWith("/api/checkout/coupons?")) return server.coupons ? server.coupons(reply, error) : reply({ serverTime: TIME, holdId: HOLD, coupons: [preview()] });
        if (path.includes("/coupon-eligibility?")) return server.preview ? server.preview(path, reply, error) : reply(preview(decodeURIComponent(path.split("couponCode=")[1])));
        if (options.method === "POST") {
            if (server.post) return server.post(path, options, reply, error);
            if (path === "/api/orders") { server.order = order(); return reply(server.order, 201); }
            const code = JSON.parse(options.body).couponCode;
            server.order = { ...server.order, appliedCouponCode: code, discountAmount: code === null ? "0" : "60000", totalAmount: code === null ? "300000" : "240000", version: server.order.version + 1 };
            return reply(server.order);
        }
        throw new Error("Unexpected route " + path);
    };
    const create = (overrides = {}) => new OrderClient({ fetch, storage, now: () => clock,
        randomUUID: () => "60000000-0000-4000-8000-" + String(++key).padStart(12, "0"), ...overrides });
    return { create, server, calls, values, storage, reply, error, advance: value => { clock += value; } };
}

test("reload finds a current order by Hold and requires explicit creation", async () => {
    const f = fixture(), client = f.create(); await client.refresh();
    assert.equal(client.state.phase, "ready"); assert.equal(client.creatable(), true);
    assert.deepEqual(f.calls.map(call => call.path), ["/api/me", "/api/me/hold", "/api/checkout?holdId=" + HOLD, "/api/checkout/coupons?holdId=" + HOLD]);
    f.server.order = order(); const second = f.create(); await second.refresh();
    assert.equal(second.state.order.orderId, ORDER); assert.equal(second.creatable(), false); assert.equal(second.editable(), true);
    assert.equal(f.calls.every(call => call.credentials === "same-origin" && call.cache === "no-store" && !call.method), true);
});

test("explicit creation stores immutable command before POST and confirms current SQL after receipt", async () => {
    const f = fixture(), client = f.create(); await client.refresh();
    f.server.post = (path, options, reply) => {
        const saved = JSON.parse(f.storage.getItem("tc:order-command:v1:" + ACTOR));
        assert.equal(path, "/api/orders"); assert.equal(saved.body, '{"holdId":"' + HOLD + '"}');
        assert.equal(saved.body, options.body); assert.equal(saved.key, options.headers["Idempotency-Key"]);
        assert.equal(options.headers["X-CSRF-Token"], "fresh-token");
        f.server.order = order(); return reply(order(), 201);
    };
    await client.create();
    assert.equal(client.state.order.orderId, ORDER); assert.equal(client.state.phase, "ready");
    assert.equal(f.calls.at(-1).path, "/api/checkout/coupons?holdId=" + HOLD); assert.equal(f.values.size, 0);
});

test("apply, replacement and removal use exact codes and authoritative totals", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(); await client.refresh();
    await client.applyCoupon(" SALE20 "); assert.equal(client.state.order.appliedCouponCode, " SALE20 "); assert.equal(client.state.order.totalAmount, "240000");
    await client.applyCoupon("Second"); assert.equal(client.state.order.appliedCouponCode, "Second");
    await client.removeCoupon(); assert.equal(client.state.order.appliedCouponCode, null); assert.equal(client.state.order.totalAmount, "300000");
    assert.deepEqual(f.calls.filter(call => call.method === "POST").map(call => JSON.parse(call.body)), [
        { couponCode: " SALE20 " }, { couponCode: "Second" }, { couponCode: null }
    ]); assert.equal(client.state.order.version, 3);
});

test("preview is read-only and preserves the code including spaces and URL characters", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(); await client.refresh();
    await client.preview(" SALE&20 ");
    assert.equal(client.state.preview.couponCode, " SALE&20 "); assert.equal(client.state.preview.totalAmount, "240000");
    assert.equal(client.state.order.appliedCouponCode, null); assert.equal(f.values.size, 0);
    assert.equal(f.calls.at(-1).path, "/api/orders/" + ORDER + "/coupon-eligibility?couponCode=%20SALE%2620%20");
});

test("ineligible preview clears guessed money and reports the actual reason", async () => {
    const f = fixture(); f.server.order = order(); f.server.preview = (_, reply) => reply({ ...preview(), eligible: false, reason: "QUOTA_EXHAUSTED", remainingUses: 0, discountAmount: null, totalAmount: null });
    const client = f.create(); await client.refresh(); await client.preview("SALE20");
    assert.equal(client.state.preview.reason, "QUOTA_EXHAUSTED"); assert.equal(client.state.preview.totalAmount, null); assert.equal(client.state.order.totalAmount, "300000");
});

test("503 keeps exact key and bytes across reload and retry refreshes CSRF and identity", async () => {
    const f = fixture(), client = f.create(); await client.refresh(); f.server.post = (_, __, ___, error) => error(503);
    await client.create(); const first = f.calls.find(call => call.method === "POST");
    assert.equal(client.state.phase, "uncertain"); assert.equal(client.state.pending.kind, "create");
    const reload = f.create(); await reload.refresh(); await reload.applyCoupon("OTHER");
    assert.equal(reload.state.pending.key, first.headers["Idempotency-Key"]); assert.equal(f.calls.filter(call => call.method === "POST").length, 1);
    f.server.post = null; await reload.retry(); const second = f.calls.filter(call => call.method === "POST").at(-1);
    assert.equal(second.headers["Idempotency-Key"], first.headers["Idempotency-Key"]); assert.equal(second.body, first.body);
    assert.equal(reload.state.order.orderId, ORDER); assert.equal(f.values.size, 0);
});

test("network loss after commit and historical coupon replay cannot replace current totals", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(); await client.refresh();
    f.server.post = () => { f.server.order = { ...order(), appliedCouponCode: "NEW", discountAmount: "90000", totalAmount: "210000", version: 2 }; throw new Error("connection lost"); };
    await client.applyCoupon("SALE20"); assert.equal(client.state.phase, "uncertain");
    const reload = f.create(); await reload.refresh(); assert.equal(reload.state.order.appliedCouponCode, "NEW");
    f.server.post = (_, __, reply) => reply({ orderId: ORDER, appliedCouponCode: "SALE20", subtotal: "300000", discountAmount: "60000", totalAmount: "240000", version: 1 });
    await reload.retry(); assert.equal(reload.state.order.appliedCouponCode, "NEW"); assert.equal(reload.state.order.totalAmount, "210000"); assert.equal(reload.state.order.version, 2);
});

test("confirmed rejection discards only its command and re-reads before a new key", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(); await client.refresh();
    f.server.post = (_, __, ___, error) => error(400, "COUPON_INVALID"); await client.applyCoupon("BAD");
    const first = f.calls.find(call => call.method === "POST"); assert.equal(client.state.phase, "ready"); assert.equal(client.state.pending, null);
    assert.equal(client.state.order.totalAmount, "300000"); assert.match(client.state.message, /COUPON_INVALID/);
    f.server.post = null; await client.applyCoupon("GOOD"); const second = f.calls.filter(call => call.method === "POST").at(-1);
    assert.notEqual(first.headers["Idempotency-Key"], second.headers["Idempotency-Key"]); assert.equal(client.state.order.appliedCouponCode, "GOOD");
});

for (const paymentResolution of ["PAYMENT_PENDING", "UNKNOWN"]) test(paymentResolution + " blocks coupon writes and preview", async () => {
    const f = fixture(); f.server.order = { ...order(), paymentResolution }; const client = f.create(); await client.refresh();
    await client.applyCoupon("SALE20"); await client.removeCoupon(); await client.preview("SALE20");
    assert.equal(client.state.order.paymentResolution, paymentResolution); assert.equal(client.editable(), false); assert.equal(client.state.preview, null);
    assert.equal(f.calls.filter(call => call.method === "POST").length, 0);
});

for (const status of ["PAID", "CANCELLED", "EXPIRED"]) test(status + " order remains visible with mutation disabled", async () => {
    const f = fixture(); f.server.order = { ...order(), status }; const client = f.create(); await client.refresh(); await client.applyCoupon("SALE20");
    assert.equal(client.state.order.status, status); assert.equal(client.editable(), false); assert.equal(client.creatable(), false);
});

test("released Hold and elapsed server deadline prevent edits even when canEditCoupon is true", async () => {
    const f = fixture(); f.server.order = { ...order(), holdStatus: "RELEASED" }; const client = f.create(); await client.refresh();
    assert.equal(client.editable(), false); f.server.order = order(); await client.refresh(); assert.equal(client.editable(), true);
    f.advance(600001); assert.equal(client.editable(), false); await client.applyCoupon("SALE20"); assert.equal(client.state.order.appliedCouponCode, null);
});

test("foreign-owner and malformed current projection never enable mutation", async () => {
    const f = fixture(); f.server.order = { ...order(), userId: OTHER }; const client = f.create(); await client.refresh();
    assert.equal(client.state.phase, "uncertain"); assert.equal(client.state.order, null); assert.equal(client.editable(), false);
    f.server.order = { ...order(), totalAmount: "300000.00" }; await client.refresh(); assert.equal(client.state.order, null);
});

test("current actor switch after CSRF fetch prevents transmission and preserves old actor command", async () => {
    const f = fixture(), client = f.create(); await client.refresh();
    f.server.csrf = reply => { f.server.actor = OTHER; f.server.hold = null; return reply({ data: { token: "other-token" } }); };
    await client.create(); assert.equal(client.actorId, OTHER); assert.equal(client.state.order, null); assert.equal(client.state.pending, null);
    assert.equal(f.calls.filter(call => call.method === "POST").length, 0);
    assert.equal(JSON.parse(f.storage.getItem("tc:order-command:v1:" + ACTOR)).kind, "create");
});

test("stale response after actor refresh cannot mutate the UI or remove pending storage", async () => {
    const f = fixture(), client = f.create(); await client.refresh(); const wait = deferred();
    f.server.post = () => wait.promise; const request = client.create();
    while (!f.calls.some(call => call.method === "POST")) await new Promise(resolve => setImmediate(resolve));
    f.server.actor = OTHER; f.server.hold = null; await client.refresh(); wait.resolve(f.reply(order(), 201)); await request;
    assert.equal(client.actorId, OTHER); assert.equal(client.state.order, null); assert.equal(client.state.pending, null);
    assert.equal(JSON.parse(f.storage.getItem("tc:order-command:v1:" + ACTOR)).kind, "create");
});

test("a stale current read cannot overwrite a newer refresh", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(), wait = deferred();
    f.server.current = () => wait.promise; const first = client.refresh();
    while (!f.calls.some(call => call.path.startsWith("/api/checkout?"))) await new Promise(resolve => setImmediate(resolve));
    f.server.current = null; f.server.order = { ...order(), status: "EXPIRED" }; await client.refresh(); wait.resolve(f.reply({ serverTime: TIME, order: order() })); await first;
    assert.equal(client.state.order.status, "EXPIRED"); assert.equal(client.editable(), false);
});

test("another pending storage value cannot be removed by an earlier successful reply", async () => {
    const f = fixture(), client = f.create(); await client.refresh();
    f.server.post = (_, options, reply) => {
        const saved = JSON.parse(f.storage.getItem("tc:order-command:v1:" + ACTOR));
        f.storage.setItem("tc:order-command:v1:" + ACTOR, JSON.stringify({ ...saved, key: "60000000-0000-4000-8000-999999999999" }));
        f.server.order = order(); return reply(order(), 201);
    };
    await client.create(); assert.equal(client.state.phase, "uncertain"); assert.equal(client.state.order.orderId, ORDER);
    assert.equal(client.state.pending.key, "60000000-0000-4000-8000-999999999999");
});

test("malformed successful receipt retains a replayable key", async () => {
    const f = fixture(), client = f.create(); await client.refresh(); f.server.post = (_, __, reply) => reply({ orderId: ORDER }, 201);
    await client.create(); assert.equal(client.state.phase, "uncertain"); assert.equal(client.state.pending.holdId, HOLD); assert.equal(f.values.size, 1);
});

test("CSRF rejection retains the original coupon bytes and refreshes the token on retry", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(); await client.refresh();
    f.server.post = (_, __, ___, error) => error(403, "CSRF_TOKEN_INVALID"); await client.applyCoupon("Exact Code");
    const pending = client.state.pending; assert.equal(pending.body, '{"couponCode":"Exact Code"}'); assert.equal(client.state.order, null);
    f.server.post = null; await client.retry();
    assert.equal(client.state.order.appliedCouponCode, "Exact Code");
    const posts = f.calls.filter(call => call.method === "POST"); assert.equal(posts[1].headers["Idempotency-Key"], pending.key);
});

test("storage failure blocks transmission and never substitutes a memory-only key", async () => {
    const f = fixture(); const client = f.create({ storage: { ...f.storage, setItem() { throw new Error("Không được lưu"); } } }); await client.refresh();
    await client.create(); assert.equal(client.state.pending, null); assert.equal(client.state.phase, "uncertain"); assert.equal(client.state.hold.id, HOLD);
    assert.match(client.state.message, /Không được lưu/); assert.equal(f.calls.filter(call => call.method === "POST").length, 0);
});

test("preview for an older actor cannot populate a refreshed checkout", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(); await client.refresh(); const wait = deferred();
    f.server.preview = () => wait.promise; const request = client.preview("SALE20");
    while (!f.calls.some(call => call.path.includes("coupon-eligibility"))) await new Promise(resolve => setImmediate(resolve));
    f.server.actor = OTHER; f.server.hold = null; f.server.order = null; await client.refresh(); wait.resolve(f.reply(preview())); await request;
    assert.equal(client.actorId, OTHER); assert.equal(client.state.phase, "ready"); assert.equal(client.state.preview, null);
});

test("BFCache pause fences outstanding work and re-anchors to server time on resume", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(); await client.refresh(); f.advance(120000);
    assert.equal(client.remaining(), 480000); client.pause(); assert.equal(client.state.phase, "paused"); assert.equal(client.editable(), false);
    f.server.time = "2026-10-11T10:09:59Z"; await client.refresh(); assert.equal(client.remaining(), 1000);
    f.advance(1001); assert.equal(client.editable(), false);
});

test("whole-VND item sums and cap remain exact above Number safe range", () => {
    const large = { ...order(), subtotal: "18014398509481986", discountAmount: "5404319552844595", totalAmount: "12610078956637391",
        items: [{ ...order().items[0], unitPrice: "9007199254740993", lineTotal: "18014398509481986" }] };
    assert.equal(orderSnapshot(large, ACTOR, HOLD).totalAmount, "12610078956637391");
    assert.throws(() => orderSnapshot({ ...large, discountAmount: "5404319552844596", totalAmount: "12610078956637390" }, ACTOR, HOLD));
    assert.throws(() => orderSnapshot({ ...order(), items: [{ ...order().items[0], seatId: SEAT }] }, ACTOR, HOLD));
});

test("boundary rejects unknown payment states and contradictory previews", () => {
    assert.equal(checkout({ serverTime: TIME, order: null }, ACTOR, HOLD).order, null);
    assert.throws(() => checkout({ serverTime: TIME, order: { ...order(), paymentResolution: "COMPLETE" } }, ACTOR, HOLD));
    assert.throws(() => eligibility({ ...preview(), eligible: false }, ORDER, "SALE20"));
    assert.equal(eligibility(preview(), ORDER, "SALE20").remainingUses, 1);
});

test("invalid or empty coupon input leaves the current order unchanged", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(); await client.refresh(); await client.applyCoupon("");
    assert.equal(client.state.order.totalAmount, "300000"); assert.equal(client.state.pending, null); assert.match(client.state.message, /1 đến 80/);
    await client.refresh(); await client.preview("\uD800"); assert.equal(client.state.preview, null); assert.equal(client.state.order.orderId, ORDER);
});

test("payment persists one command and retries uncertain network results with the same key", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(); await client.refresh();
    f.server.post = () => { throw new Error("connection lost"); };
    await client.pay();
    assert.equal(client.state.phase, "uncertain");
    const pending = client.state.pending;
    assert.equal(pending.kind, "payment"); assert.equal(pending.body, "{}");
    f.server.post = (path, options, reply) => {
        assert.equal(path, "/api/orders/" + ORDER + "/payments");
        assert.equal(options.headers["Idempotency-Key"], pending.key);
        assert.equal(options.headers["X-CSRF-Token"], "fresh-token");
        return reply({paymentId: ITEM, txnRef: "PAY-01", amount: "300000", currency: "VND", zeroOrder: false,
            redirectUrl: "https://pay.payos.vn/web/0123456789abcdef"});
    };
    await client.retry();
    assert.equal(client.state.paymentRedirect, "https://pay.payos.vn/web/0123456789abcdef");
    assert.equal(f.values.size, 0);
});

test("payment rejects malformed redirect receipts without forgetting the replay key", async () => {
    const f = fixture(); f.server.order = order(); const client = f.create(); await client.refresh();
    f.server.post = (_, __, reply) => reply({paymentId: ITEM, txnRef: "PAY-01", amount: "300000", currency: "VND", zeroOrder: false, redirectUrl: "javascript:alert(1)"});
    await client.pay(); assert.equal(client.state.phase, "uncertain"); assert.equal(client.state.pending.kind, "payment");
    assert.equal(client.state.paymentRedirect, null);
});

test("payment completion for zero order is confirmed from current server state", async () => {
    const f = fixture(); f.server.order = {...order(), subtotal:"0", totalAmount:"0", items:[{...order().items[0],unitPrice:"0",lineTotal:"0"}]};
    const client = f.create(); await client.refresh();
    f.server.post = (_, __, reply) => { f.server.order = {...f.server.order,status:"PAID",holdStatus:"CONSUMED",canEditCoupon:false};
        return reply({orderId: ORDER, zeroOrder:true, status:"PAID", amount:"0", currency:"VND"}); };
    await client.pay(); assert.equal(client.state.order.status,"PAID"); assert.equal(client.state.paymentRedirect,null);
    assert.equal(client.editable(),false); assert.equal(f.values.size,0);
});

test("payment remains blocked while expiry, hold or pending provider state disallows editing", async () => {
    const f = fixture(); f.server.order = {...order(),paymentResolution:"PAYMENT_PENDING",canEditCoupon:false};
    const client = f.create(); await client.refresh(); await client.pay(); assert.equal(f.calls.filter(c=>c.method === "POST").length,0);
});

function browserFixture() {
    const fixtureApi = fixture(), handlers = new Map(), timers = new Map();
    let timerId = 0, observer;
    class Element {
        constructor() { this.textContent = ""; this.hidden = false; this.disabled = false; this.value = ""; this.dataset = {}; this.children = []; this.handlers = new Map(); }
        addEventListener(name, handler) { this.handlers.set(name, handler); }
        replaceChildren(...children) { this.children = children; }
        append(child) { this.children.push(child); }
        set innerHTML(_) { throw new Error("Unsafe HTML rendering"); }
        fire(name) { this.handlers.get(name)?.({ preventDefault() {} }); }
    }
    const ids = ["order-pay", "payment-message", "order-detail-link", "order-checkout", "order-message", "order-recovery", "order-retry", "order-create", "order-refresh", "order-summary",
        "order-coupon-code", "order-coupon-help", "order-preview-result", "order-code", "order-status",
        "order-items", "order-subtotal", "order-discount", "order-total", "order-applied-coupon", "order-coupon-form", "hold-cancel", "hold-retry", "hold-refresh"];
    const elements = Object.fromEntries(ids.map(id => [id, new Element()]));
    const document = { body: { dataset: { context: "" } }, visibilityState: "visible", getElementById: id => elements[id], createElement: () => new Element(),
        addEventListener: (name, handler) => handlers.set("document:" + name, handler) };
    const redirects = [];
    const window = { document, location: {assign: url => redirects.push(url)}, TicketHold: require("../../main/webapp/assets/js/ticket-hold.js"), fetch: fixtureApi.create().fetch,
        sessionStorage: fixtureApi.storage, performance: { now: () => 0 }, crypto: { randomUUID: () => "60000000-0000-4000-8000-000000000123" },
        setTimeout: (fn, delay) => { const id = ++timerId; timers.set(id, { fn, delay }); return id; }, clearTimeout: id => timers.delete(id),
        addEventListener: (name, handler) => handlers.set(name, handler), MutationObserver: class {
            constructor(fn) { observer = fn; } observe() {}
        } };
    vm.runInNewContext(fs.readFileSync(require.resolve("../../main/webapp/assets/js/order-checkout.js"), "utf8"), { window, URLSearchParams, URL });
    const settle = async () => { for (let i = 0; i < 10; i++) await new Promise(resolve => setImmediate(resolve)); };
    return { ...fixtureApi, redirects, elements, handlers, timers, document, settle, holdState: state => { document.body.dataset.holdState = state; observer(); },
        expired: () => { document.body.dataset.holdState = "expired"; observer(); } };
}

test('coupon entry works for an active hold before a separate order creation step', async () => {
    const f=browserFixture();await f.settle();
    assert.equal(f.elements['order-coupon-code'].disabled,false);
    assert.equal(f.calls.some(call=>call.method==='POST'),false,'viewing checkout must remain read only');
    f.elements['order-coupon-code'].value='SALE20';
    f.elements['order-coupon-code'].fire('change');await f.settle();
    const writes=f.calls.filter(call=>call.method==='POST');
    assert.deepEqual(writes.map(call=>call.path),['/api/orders','/api/orders/'+ORDER+'/coupon']);
    assert.equal(JSON.parse(writes[1].body).couponCode,'SALE20');
    assert.equal(f.elements['order-total'].textContent,'240.000 đ');
    assert.equal(f.calls.some(call=>call.path.includes('/payments')),false);
});

test("actual browser adapter renders immutable labels as text and wires create/coupon controls", async () => {
    const f = browserFixture(); await f.settle();
    assert.equal(f.elements["order-create"].hidden, false); assert.equal(f.elements["order-create"].disabled, false);
    f.elements["order-create"].fire("click"); await f.settle();
    assert.equal(f.elements["order-code"].textContent, "Đơn vé của bạn");
    assert.equal(f.elements["order-items"].children[0].textContent, "<img onerror=alert(1)> · Vé đứng · 2 vé × 150.000 đ = 300.000 đ");
    assert.equal(f.elements["order-total"].textContent, "300.000 đ"); assert.equal(f.elements["order-coupon-code"].disabled, false);
    assert.equal(f.elements["order-coupon-code"].children[1].value, "SALE20");
    f.elements["order-coupon-code"].value = "SALE20"; f.elements["order-coupon-code"].fire("change"); await f.settle();
    assert.equal(f.elements["order-total"].textContent, "240.000 đ");
    f.elements["order-coupon-code"].value = ""; f.elements["order-coupon-code"].fire("change"); await f.settle();
    assert.equal(f.elements["order-total"].textContent, "300.000 đ");
});

test("browser lifecycle maintains one expiry timeout and reconciles actual Hold expiry", async () => {
    const f = browserFixture(); f.server.order = order(); await f.settle();
    assert.equal(f.timers.size, 1);
    f.handlers.get("pagehide")(); assert.equal(f.timers.size, 0); assert.equal(f.elements["order-coupon-code"].disabled, true);
    f.handlers.get("pageshow")({ persisted: true }); await f.settle(); assert.equal(f.timers.size, 1);
    f.handlers.get("pageshow")({ persisted: true }); await f.settle(); assert.equal(f.timers.size, 1);
    f.document.visibilityState = "hidden"; f.handlers.get("document:visibilitychange")(); assert.equal(f.timers.size, 0);
    f.server.hold = null; f.server.order = { ...order(), status: "EXPIRED", holdStatus: "RELEASED", canEditCoupon: false }; f.server.time = "2026-10-11T10:12:00Z";
    f.document.visibilityState = "visible"; f.handlers.get("document:visibilitychange")(); await f.settle();
    assert.equal(f.timers.size, 0); assert.equal(f.elements["order-status"].textContent, "Đã hết hạn. Lượt giữ không còn hoạt động."); assert.equal(f.elements["order-coupon-code"].disabled, true);
    f.expired(); await f.settle(); assert.equal(f.elements["order-create"].hidden, true);
});

test("Hold cancellation or uncertain recovery freezes Order controls until current Hold resolves", async () => {
    const f = browserFixture(); f.server.order = order(); await f.settle(); assert.equal(f.elements["order-coupon-code"].disabled, false);
    f.holdState("submitting"); assert.equal(f.elements["order-coupon-code"].disabled, true); assert.equal(f.timers.size, 0);
    f.holdState("uncertain"); assert.equal(f.elements["order-create"].disabled, true); assert.equal(f.elements["order-coupon-code"].disabled, true);
    f.holdState("active"); await f.settle(); assert.equal(f.elements["order-coupon-code"].disabled, false); assert.equal(f.timers.size, 1);
});

test("browser payment button redirects only after a valid server receipt and links to the real order page", async () => {
    const f = browserFixture(); f.server.order = order(); await f.settle();
    assert.equal(f.elements["order-pay"].disabled,false);
    assert.equal(f.elements["order-detail-link"].href,"/orders/" + ORDER);
    f.server.post = (_, __, reply) => reply({paymentId:ITEM,txnRef:"PAY-01",amount:"300000",currency:"VND",zeroOrder:false,redirectUrl:"https://pay.payos.vn/web/0123456789abcdef"});
    f.elements["order-pay"].fire("click"); await f.settle();
    assert.deepEqual(f.redirects,["https://pay.payos.vn/web/0123456789abcdef"]);
    assert.equal(f.elements["order-pay"].disabled,true);
});

test("browser payment button shows free order text and completes zero order directly", async () => {
    const f = browserFixture();
    f.server.order = { ...order(), subtotal: "0", totalAmount: "0",
        items: [{ orderItemId: ITEM, zoneId: ZONE, seatId: null, zoneName: "Free Zone", seatLabel: null, quantity: 2, unitPrice: "0", lineTotal: "0" }] };
    await f.settle();
    assert.equal(f.elements["order-pay"].disabled, false);
    assert.equal(f.elements["order-pay"].textContent, "Hoàn tất đơn miễn phí");
    f.server.post = (_, __, reply) => reply({ orderId: ORDER, amount: "0", currency: "VND", status: "PAID", zeroOrder: true, replayed: false });
    f.server.order = { ...f.server.order, status: "PAID", canEditCoupon: false };
    f.elements["order-pay"].fire("click");
    await f.settle();
    assert.deepEqual(f.redirects, []);
    assert.equal(f.elements["order-status"].textContent, "Đã thanh toán");
});


test("coupon list failure keeps payment available and reports the failure inline", async () => {
    const f = browserFixture(); f.server.order = order(); f.server.coupons = (_, error) => error(500);
    await f.settle();
    assert.equal(f.elements["order-pay"].disabled, false);
    assert.equal(f.elements["order-coupon-code"].disabled, true);
    assert.match(f.elements["order-coupon-help"].textContent, /Chưa tải được/);
});

test("coupon list rejects a mismatched Hold", async () => {
    const f = browserFixture(); f.server.order = order();
    f.server.coupons = reply => reply({serverTime: TIME, holdId: OTHER, coupons: [preview()]});
    await f.settle(); assert.equal(f.elements["order-coupon-code"].disabled, true);
});


test("coupon list rejects discounts above the cap and leaves payment available", async () => {
    const f = browserFixture(); f.server.order = order();
    f.server.coupons = reply => reply({serverTime: TIME, holdId: HOLD, coupons: [{...preview(),discountAmount:"120000",totalAmount:"180000"}]});
    await f.settle(); assert.equal(f.elements["order-coupon-code"].disabled, true); assert.equal(f.elements["order-pay"].disabled, false);
});

test("an empty eligible list shows the empty state and keeps checkout usable", async () => {
    const f = browserFixture(); f.server.order = order();
    f.server.coupons = reply => reply({serverTime: TIME, holdId: HOLD, coupons: []});
    await f.settle(); assert.equal(f.elements["order-coupon-code"].children.length, 1);
    assert.match(f.elements["order-coupon-help"].textContent, /chưa có mã/); assert.equal(f.elements["order-pay"].disabled, false);
});

test("coupon rejection remains visible after returning to a ready checkout", async () => {
    const f = browserFixture(); f.server.order = order(); await f.settle();
    assert.equal(f.elements["order-message"].hidden, true);
    f.server.post = (_, __, reply) => reply({error:{code:"QUOTA_EXHAUSTED",message:"Mã đã hết lượt sử dụng."}},400);
    f.elements["order-coupon-code"].value = "SALE20";
    f.elements["order-coupon-code"].fire("change"); await f.settle();
    assert.equal(f.elements["order-message"].textContent,"Mã đã hết lượt sử dụng.");
    assert.equal(f.elements["order-message"].hidden,false);
    assert.equal(f.elements["order-coupon-code"].value,"");
    assert.equal(f.elements["order-pay"].disabled,false);
});

test("native coupon popup focus preserves the selection while normal window focus still reconciles", async () => {
    const f = browserFixture(); f.server.order = order(); await f.settle();
    const select = f.elements["order-coupon-code"], options = select.children;
    select.tagName = "SELECT"; f.document.activeElement = select;
    const before = f.calls.length;
    f.handlers.get("focus")();
    assert.equal(select.disabled, false);
    assert.equal(select.children, options);
    await f.settle(); assert.equal(f.calls.length, before);
    select.value = "SALE20"; select.fire("change"); await f.settle();
    assert.equal(f.elements["order-total"].textContent, "240.000 đ");
    f.document.activeElement = {tagName:"BODY"};
    const after = f.calls.length; f.handlers.get("focus")(); await f.settle();
    assert.ok(f.calls.length > after);
    assert.equal(select.disabled, false);
});
