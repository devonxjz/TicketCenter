"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const { HoldClient, selectionPayload, currentHold, catalog, total, vnd, countdown } = require("../../main/webapp/assets/js/ticket-hold.js");
test('embedded booking resolves eventId and legacy id links without trusting malformed identifiers', () => {
    const { resolveEventId } = require('../../main/webapp/assets/js/ticket-hold.js');
    const id = '5318d6e0-16fc-5da5-a40b-14cde62ac2f7';
    assert.equal(resolveEventId('?eventId=' + id), id);
    assert.equal(resolveEventId('?id=' + id), id);
    assert.equal(resolveEventId('?eventId=invalid'), null);
});

const ACTOR = "10000000-0000-4000-8000-000000000001";
const OTHER = "10000000-0000-4000-8000-000000000002";
const EVENT = "20000000-0000-4000-8000-000000000001";
const STANDING = "30000000-0000-4000-8000-000000000001";
const SEATED = "30000000-0000-4000-8000-000000000002";
const SEAT = "40000000-0000-4000-8000-000000000001";
const HOLD = "50000000-0000-4000-8000-000000000001";
const TIME = "2026-10-11T10:00:00Z";
const options = () => ({ eventId: EVENT, title: "<img onerror=alert(1)>", serverTime: TIME,
    saleEnd: "2026-10-11T11:00:00Z", zones: [
        { id: STANDING, name: "Floor", type: "STANDING", unitPrice: "150000", availableStanding: 30, availableSeats: [] },
        { id: SEATED, name: "Balcony", type: "SEATED", unitPrice: "200000", availableStanding: null, availableSeats: [{ id: SEAT, label: "A1" }] }
    ] });
const selected = () => [{ zoneId: STANDING, seatId: null, quantity: 2 }];
const hold = () => ({ id: HOLD, eventId: EVENT, createdAt: TIME, expiresAt: "2026-10-11T10:10:00Z", status: "ACTIVE",
    items: [{ zoneId: STANDING, seatId: null, quantity: 2, unitPrice: "150000" }] });
const receipt = outcome => ({ ...hold(), status: undefined, outcome });

function fixture(config = {}) {
    const values = new Map();
    const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
    const server = { actor: ACTOR, hold: null, time: TIME, options: options(), create: null, cancel: null };
    const calls = [];
    let sequence = 0;
    let now = 0;
    const reply = (body, status = 200) => ({ ok: status < 400, status, json: async () => body });
    const error = (status, code = "HOLD_UNAVAILABLE") => reply({ error: { code, message: code } }, status);
    const fetch = async (path, request) => {
        calls.push({ path, ...request });
        if (path === "/api/me") return server.actor ? reply({ data: { id: server.actor } }) : error(401, "UNAUTHENTICATED");
        if (path === "/api/auth/csrf") return reply({ data: { token: "session-token" } });
        if (path === "/api/me/hold") return reply({ serverTime: server.time, hold: server.hold });
        if (path.startsWith("/api/hold-options?")) return reply(server.options);
        if (path === "/api/holds") {
            if (server.create) return server.create(request, reply, error);
            server.hold = hold(); return reply(receipt("CREATED"), 201);
        }
        if (path === "/api/holds/" + HOLD + "/cancel") {
            if (server.cancel) return server.cancel(request, reply, error);
            server.hold = null; return reply(receipt("CANCELLED"));
        }
        throw new Error("Unexpected route " + path);
    };
    const create = (overrides = {}) => new HoldClient({ fetch, storage, randomUUID: () =>
        "60000000-0000-4000-8000-" + String(++sequence).padStart(12, "0"), now: () => now, eventId: EVENT, ...config, ...overrides });
    return { create, server, calls, values, storage, reply, error, advance: ms => { now += ms; } };
}

test("canonical request contains only UUID inventory and quantities, sorts selections, and limits total to eight", () => {
    assert.deepEqual(selectionPayload(options(), [{ zoneId: SEATED, seatId: SEAT, quantity: 1 }, ...selected()]), {
        eventId: EVENT, selections: [...selected(), { zoneId: SEATED, seatId: SEAT, quantity: 1 }]
    });
    assert.throws(() => selectionPayload(options(), [...selected(), ...selected()]), /chỉ được chọn một lần/);
    assert.throws(() => selectionPayload(options(), [{ zoneId: STANDING, quantity: 8 }, { zoneId: SEATED, seatId: SEAT, quantity: 1 }]), /tối đa 8/);
    assert.throws(() => selectionPayload(options(), [{ zoneId: SEATED, seatId: SEAT, quantity: 2 }]), /không còn/);
    assert.throws(() => selectionPayload(options(), [{ zoneId: STANDING, quantity: 1.5 }]), /không hợp lệ/);
    const depleted = options(); depleted.zones[0].availableStanding = 1;
    assert.throws(() => selectionPayload(depleted, selected()), /không còn/);
    assert.throws(() => selectionPayload(options(), [{ zoneId: OTHER, quantity: 1 }]), /không hợp lệ/);
});

test("integer VND snapshots remain exact above Number safe range", () => {
    const value = total([{ unitPrice: "9007199254740993", quantity: 2 }, { unitPrice: "1", quantity: 1 }]);
    assert.equal(value, 18014398509481987n);
    assert.equal(vnd(value), "18.014.398.509.481.987 đ");
    assert.throws(() => catalog({ ...options(), zones: [{ ...options().zones[0], unitPrice: "1.5" }] }, EVENT));
});

test("load uses normal cookie reads and wrappers, and never creates a hold", async () => {
    const f = fixture(); const client = f.create(); await client.refresh();
    assert.equal(client.state.phase, "selecting");
    assert.deepEqual(f.calls.map(call => call.path), ["/api/me", "/api/me/hold", "/api/hold-options?eventId=" + EVENT]);
    assert.equal(f.calls.every(call => call.credentials === "same-origin" && call.cache === "no-store" && !call.method), true);
});

test("submit persists before POST, uses cookie CSRF, and confirms active status by GET", async () => {
    const f = fixture(); const client = f.create(); await client.refresh();
    f.server.create = (request, reply) => {
        const saved = JSON.parse(f.storage.getItem("tc:hold-command:v1:" + ACTOR));
        assert.equal(saved.key, request.headers["Idempotency-Key"]);
        assert.equal(saved.body, request.body);
        assert.equal(request.headers["X-CSRF-Token"], "session-token");
        assert.equal(request.headers["Content-Type"], "application/json");
        assert.equal(request.credentials, "same-origin");
        f.server.hold = hold(); return reply(receipt("CREATED"), 201);
    };
    await client.submit(selected());
    assert.equal(client.state.phase, "active");
    assert.equal(client.state.hold.id, HOLD);
    assert.equal(f.calls.at(-1).path, "/api/me/hold");
    assert.equal(f.values.size, 0);
});

test("503 freezes the selection and key, reload only reads, and explicit retry sends identical bytes", async () => {
    const f = fixture(); const first = f.create(); await first.refresh();
    f.server.create = (_, __, error) => error(503);
    await first.submit(selected());
    const posted = f.calls.find(call => call.method === "POST");
    assert.equal(first.state.phase, "uncertain");
    await first.submit([{ zoneId: STANDING, quantity: 3 }]);
    assert.equal(f.calls.filter(call => call.method === "POST").length, 1);
    const reloaded = f.create(); await reloaded.refresh();
    assert.equal(reloaded.state.phase, "uncertain");
    assert.equal(f.calls.filter(call => call.method === "POST").length, 1);
    assert.equal(reloaded.state.pending.key, posted.headers["Idempotency-Key"]);
    f.server.create = (request, reply) => { f.server.hold = hold(); return reply(receipt("CREATED"), 201); };
    await reloaded.retry();
    const retried = f.calls.filter(call => call.method === "POST").at(-1);
    assert.equal(retried.body, posted.body);
    assert.equal(retried.headers["Idempotency-Key"], posted.headers["Idempotency-Key"]);
    assert.equal(reloaded.state.phase, "active");
});

test("lost commit response remains uncertain even when GET finds a hold, and historical receipt cannot activate an expired hold", async () => {
    const f = fixture({ page: "current" }); const client = f.create();
    await client.refresh();
    client.page = "selection"; await client.refresh();
    f.server.create = () => { f.server.hold = hold(); throw new Error("connection lost"); };
    await client.submit(selected());
    const reloaded = f.create(); await reloaded.refresh();
    assert.equal(reloaded.state.phase, "uncertain");
    assert.equal(reloaded.state.hold.id, HOLD);
    f.server.hold = null; f.server.time = "2026-10-11T10:12:00Z";
    f.server.create = (_, reply) => reply(receipt("CREATED"), 201);
    await reloaded.retry();
    assert.equal(reloaded.state.phase, "expired");
    assert.equal(reloaded.state.hold, null);
    assert.equal(f.values.size, 0);
});

test("rejected conflict refreshes current SQL and inventory before a changed command gets a new key", async () => {
    const f = fixture(); const client = f.create(); await client.refresh();
    f.server.create = (_, __, error) => error(409, "HOLD_CONFLICT");
    await client.submit(selected());
    const first = f.calls.find(call => call.method === "POST");
    assert.equal(client.state.phase, "selecting");
    assert.deepEqual(f.calls.slice(-2).map(call => call.path), ["/api/me/hold", "/api/hold-options?eventId=" + EVENT]);
    f.server.create = null;
    await client.submit([{ zoneId: STANDING, quantity: 3 }]);
    const second = f.calls.filter(call => call.method === "POST").at(-1);
    assert.notEqual(second.headers["Idempotency-Key"], first.headers["Idempotency-Key"]);
    assert.notEqual(second.body, first.body);
});

test("current active hold blocks new selection globally, and cancellation confirms a current read", async () => {
    const f = fixture({ page: "current" }); f.server.hold = hold(); const client = f.create(); await client.refresh();
    assert.equal(client.state.phase, "active");
    assert.equal(f.calls.some(call => call.path.startsWith("/api/hold-options")), false);
    await client.submit(selected());
    assert.equal(f.calls.some(call => call.method === "POST"), false);
    await client.cancel();
    const post = f.calls.find(call => call.method === "POST");
    assert.equal(post.path, "/api/holds/" + HOLD + "/cancel");
    assert.equal(post.body, "{}");
    assert.equal(client.state.phase, "expired");
    assert.equal(f.calls.at(-1).path, "/api/me/hold");
});

test("uncertain cancellation retains its own key and cannot be replaced by create", async () => {
    const f = fixture({ page: "current" }); f.server.hold = hold(); const client = f.create(); await client.refresh();
    f.server.cancel = () => { throw new Error("network lost"); };
    await client.cancel();
    const command = client.state.pending;
    const reloaded = f.create(); await reloaded.refresh(); await reloaded.submit(selected());
    assert.equal(reloaded.state.pending.kind, "cancel");
    f.server.cancel = null; await reloaded.retry();
    const posts = f.calls.filter(call => call.method === "POST");
    assert.equal(posts.length, 2);
    assert.equal(posts[1].headers["Idempotency-Key"], command.key);
    assert.equal(posts[1].body, "{}");
});

test("server anchored countdown ignores client wall clock and expires exactly at the deadline", async () => {
    const f = fixture({ page: "current" }); f.server.hold = hold(); const client = f.create(); await client.refresh();
    assert.equal(client.remaining(), 600000);
    assert.equal(countdown(client.remaining()), "10:00");
    f.advance(599999); assert.equal(countdown(client.remaining()), "00:01");
    f.advance(1); assert.equal(countdown(client.remaining()), "00:00");
    await client.cancel(); assert.equal(f.calls.some(call => call.method === "POST"), false);
    assert.equal(currentHold({ serverTime: "2026-10-11T10:10:00Z", hold: hold() }).hold, null);
});

test("account changes never send another actor's persisted command", async () => {
    const f = fixture(); const client = f.create(); await client.refresh();
    f.server.create = (_, __, error) => error(503);
    await client.submit(selected());
    f.server.actor = OTHER;
    await client.retry();
    assert.equal(f.calls.filter(call => call.method === "POST").length, 1);
    assert.equal(client.state.phase, "selecting");
    assert.equal(client.state.pending, null);
    assert.equal(f.values.has("tc:hold-command:v1:" + ACTOR), true);
    const reloaded = f.create(); await reloaded.refresh();
    assert.equal(reloaded.state.pending, null);
});

test("authentication failure hides the old hold and keeps the original pending command for the same actor", async () => {
    const f = fixture(); const client = f.create(); await client.refresh();
    f.server.create = (_, __, error) => error(503);
    await client.submit(selected()); const saved = client.state.pending;
    f.server.actor = null; await client.retry();
    assert.equal(client.state.auth, true);
    assert.equal(client.state.hold, null);
    assert.equal(client.state.pending.key, saved.key);
    assert.equal(f.calls.filter(call => call.method === "POST").length, 1);
});

test("blocked persistent storage cannot send a mutation or silently use memory-only retry keys", async () => {
    const f = fixture(); const client = f.create({ storage: { getItem: () => null, setItem: () => { throw new Error("storage denied"); }, removeItem: () => {} } });
    await client.refresh(); await client.submit(selected());
    assert.equal(client.state.phase, "uncertain");
    assert.equal(f.calls.filter(call => call.method === "POST").length, 0);
    await client.retry();
    assert.equal(f.calls.filter(call => call.method === "POST").length, 0);
});

test("concurrent submit clicks produce one immutable command and one POST", async () => {
    const f = fixture(); const client = f.create(); await client.refresh();
    let release;
    f.server.create = async (_, reply) => { await new Promise(resolve => { release = resolve; }); f.server.hold = hold(); return reply(receipt("CREATED"), 201); };
    const first = client.submit(selected());
    await client.submit([{ zoneId: STANDING, quantity: 4 }]);
    while (!release) await new Promise(resolve => setImmediate(resolve));
    release(); await first;
    assert.equal(f.calls.filter(call => call.method === "POST").length, 1);
    assert.equal(JSON.parse(f.calls.find(call => call.method === "POST").body).selections[0].quantity, 2);
});

test("malformed success is uncertain and retry uses the saved key", async () => {
    const f = fixture(); const client = f.create(); await client.refresh();
    f.server.create = (_, reply) => reply({ data: receipt("CREATED") }, 201);
    await client.submit(selected());
    assert.equal(client.state.phase, "uncertain");
    assert.equal(f.values.size, 1);
    const key = client.state.pending.key;
    f.server.create = null; await client.retry();
    assert.equal(f.calls.filter(call => call.method === "POST").at(-1).headers["Idempotency-Key"], key);
    assert.equal(client.state.phase, "active");
});

test("CSRF rejection keeps the same command and a fresh token can resolve it", async () => {
    const f = fixture(); const client = f.create(); await client.refresh();
    f.server.create = (_, __, error) => error(403, "CSRF_INVALID");
    await client.submit(selected());
    const key = client.state.pending.key;
    assert.equal(client.state.auth, true);
    f.server.create = null; await client.retry();
    assert.equal(client.state.phase, "active");
    assert.equal(f.calls.filter(call => call.method === "POST").at(-1).headers["Idempotency-Key"], key);
    assert.equal(f.calls.filter(call => call.path === "/api/auth/csrf").length, 2);
});

test("a failed current read after confirmed mutation cannot turn the historical receipt into an active hold", async () => {
    const f = fixture(); const client = f.create(); await client.refresh();
    f.server.create = (_, reply) => { f.server.time = "invalid"; return reply(receipt("CREATED"), 201); };
    await client.submit(selected());
    assert.equal(client.state.phase, "uncertain");
    assert.equal(client.state.hold, null);
    assert.equal(client.state.pending, null);
    assert.equal(f.values.size, 0);
    f.server.time = TIME; f.server.hold = hold(); await client.refresh();
    assert.equal(client.state.phase, "active");
    assert.equal(f.calls.filter(call => call.method === "POST").length, 1);
});

test("session switch during CSRF acquisition cannot send the previous actor's intent", async () => {
    const f = fixture();
    const baseFetch = f.create().fetch;
    const client = f.create({ fetch: async (path, request) => {
        if (path === "/api/auth/csrf") f.server.actor = OTHER;
        return baseFetch(path, request);
    } });
    await client.refresh(); await client.submit(selected());
    assert.equal(f.calls.filter(call => call.method === "POST").length, 0);
    assert.equal(client.actorId, OTHER);
    assert.equal(client.state.pending, null);
    assert.equal(client.state.phase, "selecting");
    const saved = JSON.parse(f.storage.getItem("tc:hold-command:v1:" + ACTOR));
    assert.equal(saved.actorId, ACTOR);
    assert.deepEqual(JSON.parse(saved.body), { eventId: EVENT, selections: selected() });
});

test("session switch after the final actor check uses the old CSRF token and cannot adopt the new actor's intent", async () => {
    const f = fixture();
    const baseFetch = f.create().fetch;
    let actorReads = 0;
    let accepted = 0;
    const client = f.create({ fetch: async (path, request) => {
        if (path === "/api/auth/csrf") return f.reply({ data: { token: "actor-a-token" } });
        if (path === "/api/me") {
            const response = await baseFetch(path, request);
            if (++actorReads === 2) f.server.actor = OTHER;
            return response;
        }
        if (request.method === "POST") {
            assert.equal(request.headers["X-CSRF-Token"], "actor-a-token");
            if (f.server.actor === OTHER) return f.error(403, "CSRF_INVALID");
            accepted++;
        }
        return baseFetch(path, request);
    } });
    await client.refresh(); await client.submit(selected());
    assert.equal(accepted, 0);
    assert.equal(client.state.phase, "uncertain");
    assert.equal(client.state.pending.actorId, ACTOR);
    assert.equal(client.state.auth, true);
});

test("cached history restore freezes controls, rereads actor and server time, and restarts one expiry timer on every restore", async () => {
    const f = fixture({ page: "current" }); f.server.hold = hold();
    const baseFetch = f.create().fetch;
    let now = 0;
    let gate = null;
    class Element {
        constructor() { this.dataset = {}; this.children = []; this.textContent = ""; }
        append(...nodes) { this.children.push(...nodes); }
        replaceChildren(...nodes) { this.children = nodes; }
        addEventListener() {}
        querySelectorAll() { return []; }
    }
    const nodes = new Map();
    const body = new Element(); body.dataset = { holdPage: "current", context: "" };
    const document = { body, createElement: () => new Element(), getElementById: id => {
        if (id === "hold-form") return null;
        if (!nodes.has(id)) nodes.set(id, new Element());
        return nodes.get(id);
    } };
    const events = new Map();
    const intervals = new Map();
    let timerId = 0;
    const root = { document, sessionStorage: f.storage, crypto: { randomUUID: () => "60000000-0000-4000-8000-000000000001" },
        performance: { now: () => now }, location: { search: "" }, fetch: async (path, request) => {
            if (path === "/api/me" && gate) await gate;
            return baseFetch(path, request);
        }, setInterval: fn => { intervals.set(++timerId, fn); return timerId; },
        clearInterval: id => intervals.delete(id), addEventListener: (name, fn) => events.set(name, fn) };
    const flush = async () => { for (let i = 0; i < 5; i++) await new Promise(resolve => setImmediate(resolve)); };
    vm.runInNewContext(fs.readFileSync(require.resolve("../../main/webapp/assets/js/ticket-hold.js"), "utf8"), { window: root, URLSearchParams });
    await flush();
    assert.equal(body.dataset.holdState, "active");
    assert.equal(nodes.get("hold-cancel").disabled, false);
    assert.equal(intervals.size, 1);
    events.get("pagehide")({ persisted: true });
    assert.equal(intervals.size, 0);
    now += 660000;
    f.server.time = "2026-10-11T10:11:00Z"; f.server.hold = null; f.server.actor = OTHER;
    let release;
    gate = new Promise(resolve => { release = resolve; });
    events.get("pageshow")({ persisted: true });
    assert.equal(body.dataset.holdState, "loading");
    assert.equal(nodes.get("hold-cancel").disabled, true);
    assert.equal(nodes.get("hold-retry").disabled, true);
    assert.equal(intervals.size, 1);
    release(); gate = null; await flush();
    assert.equal(body.dataset.holdState, "expired");
    assert.equal(nodes.get("current-hold").hidden, true);
    assert.equal(f.calls.filter(call => call.path === "/api/me").length, 2);
    assert.equal(f.calls.filter(call => call.path === "/api/me/hold").length, 2);
    events.get("pagehide")({ persisted: true });
    assert.equal(intervals.size, 0);
    f.server.hold = { ...hold(), createdAt: f.server.time, expiresAt: "2026-10-11T10:21:00Z" };
    events.get("pageshow")({ persisted: true }); await flush();
    assert.equal(body.dataset.holdState, "active");
    assert.equal(nodes.get("hold-countdown").textContent, "10:00");
    assert.equal(intervals.size, 1);
    events.get("pageshow")({ persisted: false });
    assert.equal(intervals.size, 1);
    now += 600000; f.server.time = "2026-10-11T10:21:00Z"; f.server.hold = null;
    for (const tick of intervals.values()) tick();
    assert.equal(nodes.get("hold-cancel").disabled, true);
    await flush();
    assert.equal(body.dataset.holdState, "expired");
    assert.equal(f.calls.filter(call => call.method === "POST").length, 0);
    assert.equal(f.calls.filter(call => call.path === "/api/me/hold").length, 4);
});

function deferred() {
    let resolve;
    const promise = new Promise(done => { resolve = done; });
    return { promise, resolve };
}

function commandFor(actorId, key = "60000000-0000-4000-8000-000000000099") {
    return { actorId, kind: "create", path: "/api/holds", body: JSON.stringify({ eventId: EVENT, selections: selected() }), key };
}

test("late current Hold response cannot overwrite a newer actor or its server-time anchor", async () => {
    const f = fixture({ page: "current" }); const baseFetch = f.create().fetch;
    const started = deferred(); const release = deferred(); let first = true;
    const client = f.create({ fetch: async (path, request) => {
        if (path === "/api/me/hold" && first) {
            first = false; started.resolve(); await release.promise;
            return f.reply({ serverTime: TIME, hold: hold() });
        }
        return baseFetch(path, request);
    } });
    const oldRead = client.refresh(); await started.promise;
    f.server.actor = OTHER; f.server.time = "2026-10-11T10:11:00Z";
    await client.refresh();
    release.resolve(); await oldRead;
    assert.equal(client.actorId, OTHER);
    assert.equal(client.state.phase, "expired");
    assert.equal(client.state.hold, null);
    assert.equal(client.anchor.serverMs, Date.parse("2026-10-11T10:11:00Z"));
});

for (const outcome of ["success", "conflict", "network"]) {
    test("late actor A mutation " + outcome + " preserves both actors' immutable saved commands and actor B state", async () => {
        const f = fixture(); const client = f.create(); await client.refresh();
        const started = deferred(); const release = deferred();
        f.server.create = async (_, reply, error) => {
            started.resolve(); await release.promise;
            if (outcome === "network") throw new Error("lost response");
            return outcome === "conflict" ? error(409, "HOLD_CONFLICT") : reply(receipt("CREATED"), 201);
        };
        const oldSubmit = client.submit(selected()); await started.promise;
        const savedA = f.storage.getItem("tc:hold-command:v1:" + ACTOR);
        const pendingB = commandFor(OTHER);
        const savedB = JSON.stringify(pendingB);
        f.storage.setItem("tc:hold-command:v1:" + OTHER, savedB);
        f.server.actor = OTHER;
        await client.refresh();
        assert.equal(client.state.phase, "uncertain");
        release.resolve(); await oldSubmit;
        assert.equal(client.actorId, OTHER);
        assert.equal(client.state.phase, "uncertain");
        assert.equal(client.state.pending.key, pendingB.key);
        assert.equal(f.storage.getItem("tc:hold-command:v1:" + OTHER), savedB);
        assert.equal(f.storage.getItem("tc:hold-command:v1:" + ACTOR), savedA);
        assert.equal(f.calls.filter(call => call.method === "POST").length, 1);
    });
}

test("a receipt removes only its exact stored command and preserves a replacement for the same actor", async () => {
    const f = fixture(); const client = f.create(); await client.refresh();
    const started = deferred(); const release = deferred();
    f.server.create = async (_, reply) => { started.resolve(); await release.promise; return reply(receipt("CREATED"), 201); };
    const oldSubmit = client.submit(selected()); await started.promise;
    const replacement = commandFor(ACTOR);
    replacement.body = JSON.stringify({ eventId: EVENT, selections: [{ zoneId: STANDING, seatId: null, quantity: 3 }] });
    const saved = JSON.stringify(replacement);
    f.storage.setItem("tc:hold-command:v1:" + ACTOR, saved);
    release.resolve(); await oldSubmit;
    assert.equal(client.state.phase, "uncertain");
    assert.equal(client.state.pending.key, replacement.key);
    assert.equal(client.state.pending.body, replacement.body);
    assert.equal(f.storage.getItem("tc:hold-command:v1:" + ACTOR), saved);
});

test("an older options response cannot replace the newer actor's selection catalog", async () => {
    const f = fixture(); const baseFetch = f.create().fetch;
    const started = deferred(); const release = deferred(); let first = true;
    const client = f.create({ fetch: async (path, request) => {
        if (path.startsWith("/api/hold-options") && first) {
            first = false; started.resolve(); await release.promise;
            return f.reply(options());
        }
        return baseFetch(path, request);
    } });
    const oldRead = client.refresh(); await started.promise;
    f.server.actor = OTHER; f.server.options = { ...options(), title: "Latest catalog", zones: [] };
    await client.refresh(); release.resolve(); await oldRead;
    assert.equal(client.actorId, OTHER);
    assert.equal(client.state.phase, "selecting");
    assert.equal(client.state.options.title, "Latest catalog");
    assert.deepEqual(client.state.options.zones, []);
});

test("an older authentication failure cannot expire or hide a newer session", async () => {
    const f = fixture({ page: "current" }); const baseFetch = f.create().fetch;
    const started = deferred(); const release = deferred(); let first = true;
    const client = f.create({ fetch: async (path, request) => {
        if (path === "/api/me" && first) {
            first = false; started.resolve(); await release.promise;
            return f.error(401, "UNAUTHENTICATED");
        }
        return baseFetch(path, request);
    } });
    const oldRead = client.refresh(); await started.promise;
    f.server.actor = OTHER; f.server.hold = hold();
    await client.refresh(); release.resolve(); await oldRead;
    assert.equal(client.actorId, OTHER);
    assert.equal(client.state.phase, "active");
    assert.equal(client.state.auth, false);
    assert.equal(client.state.hold.id, HOLD);
});

for (const pathToDelay of ["/api/auth/csrf", "/api/me"]) {
    test("refresh fences the old mutation while " + pathToDelay + " is waiting and prevents its POST", async () => {
        const f = fixture(); const baseFetch = f.create().fetch;
        const started = deferred(); const release = deferred(); let delay = false;
        const client = f.create({ fetch: async (path, request) => {
            if (delay && path === pathToDelay) {
                delay = false;
                const response = await baseFetch(path, request);
                started.resolve(); await release.promise;
                return response;
            }
            return baseFetch(path, request);
        } });
        await client.refresh(); delay = true;
        const oldSubmit = client.submit(selected()); await started.promise;
        const pendingB = commandFor(OTHER);
        const savedB = JSON.stringify(pendingB);
        f.storage.setItem("tc:hold-command:v1:" + OTHER, savedB);
        f.server.actor = OTHER;
        await client.refresh(); release.resolve(); await oldSubmit;
        assert.equal(client.actorId, OTHER);
        assert.equal(client.state.pending.key, pendingB.key);
        assert.equal(f.storage.getItem("tc:hold-command:v1:" + OTHER), savedB);
        assert.equal(f.calls.filter(call => call.method === "POST").length, 0);
    });
}

test("production summary renders optional snapshot labels as text and omits raw inventory and event UUIDs", async () => {
    const f = fixture({ page: "current" });
    f.server.hold = { ...hold(), eventTitle: "Concert <img src=x onerror=alert(1)>", items: [
        { zoneId: SEATED, seatId: SEAT, quantity: 1, unitPrice: "200000", zoneName: "Balcony", seatLabel: "A1 <svg onload=alert(1)>" },
        { ...hold().items[0], zoneName: "Floor" }
    ] };
    class Element {
        constructor(tag = "div") { this.tag = tag; this.dataset = {}; this.children = []; this.textContent = ""; }
        set innerHTML(_) { assert.fail("API text must never be parsed as HTML"); }
        set outerHTML(_) { assert.fail("API text must never be parsed as HTML"); }
        append(...nodes) { this.children.push(...nodes); }
        replaceChildren(...nodes) { this.children = nodes; }
        addEventListener() {}
        querySelectorAll() { return []; }
    }
    const nodes = new Map(); const events = new Map();
    const body = new Element(); body.dataset = { holdPage: "current", context: "" };
    const root = { document: { body, createElement: tag => new Element(tag), getElementById: id => {
        if (id === "hold-form") return null;
        if (!nodes.has(id)) nodes.set(id, new Element());
        return nodes.get(id);
    } }, sessionStorage: f.storage, crypto: { randomUUID: () => "60000000-0000-4000-8000-000000000001" },
        performance: { now: () => 0 }, location: { search: "" }, fetch: f.create().fetch,
        setInterval: () => 1, clearInterval: () => {}, addEventListener: (name, fn) => events.set(name, fn) };
    const flush = async () => { for (let i = 0; i < 5; i++) await new Promise(resolve => setImmediate(resolve)); };
    vm.runInNewContext(fs.readFileSync(require.resolve("../../main/webapp/assets/js/ticket-hold.js"), "utf8"), { window: root, URLSearchParams });
    await flush();
    assert.equal(body.dataset.holdState, "active");
    assert.equal(nodes.get("hold-heading").textContent, "Concert <img src=x onerror=alert(1)>");
    assert.deepEqual(nodes.get("hold-items").children.map(node => node.textContent), [
        "Khu Balcony · Ghế A1 <svg onload=alert(1)> · 1 vé × 200.000 đ", "Khu Floor · Vé đứng · 2 vé × 150.000 đ"
    ]);
    assert.equal(nodes.get("hold-items").children.every(node => node.tag === "li" && node.children.length === 0), true);
    assert.equal(nodes.get("hold-total").textContent, "500.000 đ");
    assert.equal(nodes.get("hold-id").textContent, "Mã lượt giữ " + HOLD);
    const visible = [nodes.get("hold-heading").textContent, nodes.get("hold-id").textContent,
        ...nodes.get("hold-items").children.map(node => node.textContent)].join(" ");
    for (const id of [EVENT, STANDING, SEATED, SEAT]) assert.equal(visible.includes(id), false);
    f.server.hold = { ...hold(), eventTitle: { invalid: true }, items: [
        { zoneId: SEATED, seatId: SEAT, quantity: 1, unitPrice: "200000", zoneName: 123, seatLabel: {} },
        { ...hold().items[0], zoneName: " " }
    ] };
    events.get("pageshow")({ persisted: true }); await flush();
    assert.equal(nodes.get("hold-heading").textContent, "Lượt giữ vé");
    assert.deepEqual(nodes.get("hold-items").children.map(node => node.textContent), [
        "Vé ghế · 1 vé × 200.000 đ", "Vé đứng · 2 vé × 150.000 đ"
    ]);
});

test("selection summary reports invalid when total tickets exceed 8 across multiple zones", async () => {
    const f = fixture({ page: "selection", eventId: EVENT });
    f.server.hold = null;
    const STANDING2 = "30000000-0000-4000-8000-000000000003";
    f.server.options = {
        ...options(),
        zones: [
            ...options().zones,
            { id: STANDING2, name: "Floor 2", type: "STANDING", unitPrice: "160000", availableStanding: 30, availableSeats: [] }
        ]
    };
    class Element {
        constructor(tag = "div") {
            this.tag = tag; this.dataset = {}; this.children = []; this.textContent = "";
            this.className = "";
            this.classList = {
                add: cls => { if (!this.className.includes(cls)) this.className = (this.className + " " + cls).trim(); },
                remove: cls => { this.className = this.className.replace(cls, "").trim(); }
            };
        }
        append(...nodes) { this.children.push(...nodes); }
        replaceChildren(...nodes) { this.children = nodes; }
        addEventListener() {}
        querySelectorAll(selector) {
            if (selector === "input") return this.inputs || [];
            if (selector === "input[type=checkbox]") return (this.inputs || []).filter(i => i.type === "checkbox");
            if (selector === "input.hold-quantity") return (this.inputs || []).filter(i => i.className.includes("hold-quantity"));
            return [];
        }
    }
    const nodes = new Map();
    const body = new Element(); body.dataset = { holdPage: "selection", context: "" };
    const root = {
        document: {
            body, createElement: tag => new Element(tag),
            getElementById: id => {
                if (!nodes.has(id)) nodes.set(id, new Element());
                return nodes.get(id);
            }
        },
        sessionStorage: f.storage,
        crypto: { randomUUID: () => "60000000-0000-4000-8000-000000000001" },
        performance: { now: () => 0 },
        location: { search: "?id=" + EVENT },
        fetch: f.create().fetch,
        setInterval: () => 1, clearInterval: () => {}, addEventListener: () => {}
    };
    const flush = async () => { for (let i = 0; i < 5; i++) await new Promise(resolve => setImmediate(resolve)); };
    vm.runInNewContext(fs.readFileSync(require.resolve("../../main/webapp/assets/js/ticket-hold.js"), "utf8"), { window: root, URLSearchParams });
    await flush();

    const zonesEl = nodes.get("hold-zones");
    const input1 = new Element("input"); input1.className = "hold-quantity"; input1.dataset = { zoneId: STANDING }; input1.value = "5";
    const input2 = new Element("input"); input2.className = "hold-quantity"; input2.dataset = { zoneId: STANDING2 }; input2.value = "6";
    zonesEl.inputs = [input1, input2];

    zonesEl.oninput();

    assert.equal(nodes.get("selection-count").textContent, "Đã chọn 11 / 8 vé (Tối đa 8 vé)");
    assert.equal(nodes.get("selection-total").textContent, "Lựa chọn không hợp lệ");
    assert.equal(nodes.get("hold-submit").disabled, true);
    assert.equal(input1.className.includes("is-invalid"), true);
    assert.equal(input2.className.includes("is-invalid"), true);
});

