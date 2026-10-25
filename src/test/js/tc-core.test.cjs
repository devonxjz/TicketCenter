'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const TC = require('../../main/webapp/assets/js/core/tc-core.js');

test('TC.money formats BigInt and string numbers correctly in VND', () => {
    assert.equal(TC.money(100000), '100.000 đ');
    assert.equal(TC.money('5000000'), '5.000.000 đ');
    assert.equal(TC.money(0), '0 đ');
    assert.equal(TC.money(null), '—');
    assert.equal(TC.money(undefined), '—');
});

test('TC.escapeHtml neutralizes XSS characters', () => {
    assert.equal(TC.escapeHtml('<script>alert("xss")</script>'), '&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;');
    assert.equal(TC.escapeHtml('Tom & Jerry'), 'Tom &amp; Jerry');
    assert.equal(TC.escapeHtml("O'Reilly"), 'O&#039;Reilly');
    assert.equal(TC.escapeHtml(null), '');
    assert.equal(TC.escapeHtml(undefined), '');
});

test('TC.date returns formatted date or fallback', () => {
    assert.equal(TC.date(null), '—');
    assert.equal(TC.date(''), '—');
    const d = TC.date('2026-10-18T12:00:00Z');
    assert.ok(d.includes('2026') || d.includes('/'));
});

test('TC.api handles successful response and error envelopes', async () => {
    globalThis.fetch = async (url, options) => {
        if (url.includes('/api/ok')) {
            return { ok: true, status: 200, json: async () => ({ data: { id: 1 } }) };
        }
        return { ok: false, status: 400, json: async () => ({ error: { message: 'Dữ liệu sai' } }) };
    };

    const res = await TC.api('/api/ok');
    assert.equal(res.data.id, 1);

    await assert.rejects(
        () => TC.api('/api/fail'),
        /Dữ liệu sai/
    );
});

test('TC.mutate sends POST with CSRF and Idempotency-Key headers', async () => {
    let capturedOptions = null;
    globalThis.fetch = async (url, options) => {
        if (url.includes('/api/auth/csrf')) {
            return { ok: true, status: 200, json: async () => ({ data: { token: 'mock-csrf' } }) };
        }
        capturedOptions = options;
        return { ok: true, status: 200, json: async () => ({ data: { success: true } }) };
    };

    const res = await TC.mutate('/api/action', { name: 'test' }, 'idemp-123');
    assert.equal(res.data.success, true);
    assert.equal(capturedOptions.method, 'POST');
    assert.equal(capturedOptions.headers['X-CSRF-Token'], 'mock-csrf');
    assert.equal(capturedOptions.headers['Idempotency-Key'], 'idemp-123');
    const body = JSON.parse(capturedOptions.body);
    assert.equal(body.name, 'test');
    assert.equal(body._csrf, 'mock-csrf');
});
