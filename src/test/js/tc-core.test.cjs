'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');

const metas = {};
globalThis.document = {
    querySelectorAll: selector => {
        const name = /^meta\[name="([^"]+)"\]$/.exec(selector)?.[1];
        return name in metas ? [{getAttribute: () => metas[name]}] : [];
    },
    createElement: tag => ({tag, dataset: {}, children: [], append(...c) { this.children.push(...c); }})
};
const TC = require('../../main/webapp/assets/js/core/tc-core.js');

test('TC.money formats exact VND amounts', () => {
    assert.equal(TC.money(100000), '100.000 ₫');
    assert.equal(TC.money('9007199254740993'), '9.007.199.254.740.993 ₫');
    assert.equal(TC.money(0n), '0 ₫');
    assert.equal(TC.money(null), '—');
    assert.equal(TC.money(undefined), '—');
});

test('TC.escapeHtml neutralizes XSS characters', () => {
    assert.equal(TC.escapeHtml('<script>alert("xss")</script>'), '&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;');
    assert.equal(TC.escapeHtml('Tom & Jerry'), 'Tom &amp; Jerry');
    assert.equal(TC.escapeHtml("O'Reilly"), 'O&#039;Reilly');
    assert.equal(TC.escapeHtml(null), '');
});

test('TC.dateTime returns formatted date or fallback', () => {
    assert.equal(TC.dateTime(null), '—');
    assert.equal(TC.dateTime('not-a-date'), 'not-a-date');
    assert.match(TC.dateTime('2026-10-18T12:00:00Z'), /2026/);
});

test('TC.el writes text through textContent', () => {
    const child = TC.el('span', {text: '<img src=x>'});
    const row = TC.el('div', {className: 'row', dataset: {id: 'a'}, hidden: true}, child);
    assert.equal(child.textContent, '<img src=x>');
    assert.equal(row.className, 'row');
    assert.equal(row.dataset.id, 'a');
    assert.equal(row.hidden, true);
    assert.deepEqual(row.children, [child]);
});

test('TC.api prefixes the context path and unwraps the data envelope', async () => {
    metas['context-path'] = '/tc';
    const urls = [];
    globalThis.fetch = async url => {
        urls.push(url);
        if (url.endsWith('/api/ok')) return {ok: true, status: 200, json: async () => ({data: {id: 1}})};
        return {ok: false, status: 409, json: async () => ({error: {code: 'CONFLICT', message: 'Dữ liệu sai'}})};
    };

    assert.deepEqual(await TC.api('/api/ok'), {id: 1});
    await assert.rejects(() => TC.api('/api/fail'), {message: 'Dữ liệu sai', status: 409, code: 'CONFLICT'});
    assert.deepEqual(urls, ['/tc/api/ok', '/tc/api/fail']);
});

test('TC.send posts JSON with CSRF and the given Idempotency-Key', async () => {
    metas['csrf-token'] = 'meta-csrf';
    let captured;
    globalThis.fetch = async (url, options) => {
        captured = options;
        return {ok: true, status: 200, json: async () => ({data: {success: true}})};
    };

    assert.deepEqual(await TC.send('POST', '/api/action', {name: 'test'}, 'idemp-123'), {success: true});
    assert.equal(captured.method, 'POST');
    assert.equal(captured.credentials, 'same-origin');
    assert.equal(captured.headers['X-CSRF-Token'], 'meta-csrf');
    assert.equal(captured.headers['Idempotency-Key'], 'idemp-123');
    assert.deepEqual(JSON.parse(captured.body), {name: 'test'});
});
