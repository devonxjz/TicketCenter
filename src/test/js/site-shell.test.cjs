const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');

test('logout sends a JSON request accepted by API content-type validation', async (t) => {
    const { signOut } = require('../../main/webapp/assets/js/site-shell.js');
    const server = http.createServer(async (request, response) => {
        response.setHeader('Content-Type', 'application/json');
        if (request.url === '/ticketscenter/api/auth/csrf') {
            response.end(JSON.stringify({ data: { token: 'session-token' } }));
            return;
        }
        const chunks = [];
        for await (const chunk of request) chunks.push(chunk);
        if (!request.headers['content-type']?.startsWith('application/json')) {
            response.writeHead(415);
            response.end(JSON.stringify({ error: { message: 'Unsupported content type' } }));
            return;
        }
        assert.equal(request.method, 'POST');
        assert.equal(request.url, '/ticketscenter/api/auth/logout');
        assert.equal(request.headers['x-csrf-token'], 'session-token');
        assert.deepEqual(JSON.parse(Buffer.concat(chunks).toString()), {});
        response.end(JSON.stringify({ data: { loggedOut: true } }));
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    t.after(() => new Promise(resolve => server.close(resolve)));
    await signOut(fetch, `http://127.0.0.1:${server.address().port}/ticketscenter`);
});

test('logout uses session CSRF and never navigates on an unconfirmed failure', async () => {
    const { signOut } = require('../../main/webapp/assets/js/site-shell.js');
    const calls = [];
    await signOut(async (path, options) => { calls.push({path, options}); return {ok:true,json:async()=>path.endsWith('/csrf')?{data:{token:'session-token'}}:{data:{loggedOut:true}}}; }, '/ticketscenter');
    assert.equal(calls[1].path, '/ticketscenter/api/auth/logout');
    assert.equal(calls[1].options.method, 'POST');
    assert.equal(calls[1].options.headers['X-CSRF-Token'], 'session-token');
    await assert.rejects(signOut(async () => ({ok:false,json:async()=>({error:{message:'Phiên chưa được xác nhận'}})}), '/ticketscenter'), /Phiên chưa được xác nhận/);
});

test('buildSearchQuery constructs query string with sanitized category and keyword', () => {
    const { buildSearchQuery } = require('../../main/webapp/assets/js/site-shell.js');
    const q1 = buildSearchQuery({ categoryId: '65b09860-f9ae-55a4-aa0f-4915d4072a10' });
    assert.equal(q1.get('categoryId'), '65b09860-f9ae-55a4-aa0f-4915d4072a10');
    assert.equal(q1.get('pageSize'), '6');
    assert.equal(q1.get('sort'), 'startTime_asc');
    assert.equal(q1.has('keyword'), false);

    const q2 = buildSearchQuery({ categoryId: '65b09860-f9ae-55a4-aa0f-4915d4072a10', keyword: '  Kịch sân khấu  ' });
    assert.equal(q2.get('categoryId'), '65b09860-f9ae-55a4-aa0f-4915d4072a10');
    assert.equal(q2.get('keyword'), 'Kịch sân khấu');

    const q3 = buildSearchQuery({ categoryId: '', keyword: '   ' });
    assert.equal(q3.has('categoryId'), false);
    assert.equal(q3.has('keyword'), false);
    assert.equal(q3.get('pageSize'), '6');
});

test('formatPrice and formatEventTime format event metadata safely for UI', () => {
    const { formatPrice, formatEventTime } = require('../../main/webapp/assets/js/site-shell.js');
    assert.equal(formatPrice(null), 'Chưa có giá vé');
    assert.equal(formatPrice(undefined), 'Chưa có giá vé');
    assert.equal(formatPrice(0), 'Miễn phí');
    assert.equal(formatPrice('0'), 'Miễn phí');
    assert.equal(formatPrice(300000), 'Từ 300.000 đ');
    assert.equal(formatPrice('1050000'), 'Từ 1.050.000 đ');

    assert.equal(formatEventTime(null), '');
    assert.equal(formatEventTime('invalid-date'), '');
    const dateFormatted = formatEventTime('2026-10-16T12:30:00Z');
    assert.ok(dateFormatted.includes('2026') || dateFormatted.includes('16'));
});


test('search dropdown escapes every untrusted event field', () => {
    const { buildDropdownHtml } = require('../../main/webapp/assets/js/site-shell.js');
    const payload = '<img src=x onerror=alert(1)>';
    const html = buildDropdownHtml('/ticketscenter', 'Kết quả: "' + payload + '"', 1, [{
        eventId: 'e1" onmouseover="alert(1)',
        title: payload,
        venueName: payload,
        category: payload,
        coverImageUrl: 'x" onerror="alert(1)',
        startTime: '2026-11-01T12:00:00Z',
        minPrice: '100000',
    }]);
    assert.ok(!html.includes('<img src=x'), 'raw markup from a field must not survive');
    assert.ok(!/<[^>]*\son\w+\s*=/i.test(html), 'no real tag may carry an inline on* handler');
    assert.ok(!html.includes('" onmouseover="'), 'attribute breakout must be escaped');
    assert.ok(html.includes('&lt;img src=x onerror=alert(1)&gt;'));
});

test('search dropdown keeps javascript: and data: cover URLs out of img src', () => {
    const { buildDropdownHtml } = require('../../main/webapp/assets/js/site-shell.js');
    for (const url of ['javascript:alert(1)', 'data:text/html,<script>alert(1)</script>', ' JaVaScRiPt:alert(1)']) {
        const html = buildDropdownHtml('', 't', 1, [{ eventId: 'e1', title: 'ok', coverImageUrl: url }]);
        assert.ok(!/<img\b/i.test(html), 'unsafe scheme must not render an <img>: ' + url);
    }
    const ok = buildDropdownHtml('', 't', 1, [{ eventId: 'e1', title: 'ok', coverImageUrl: '/ticketscenter/media/a.jpg' }]);
    assert.ok(ok.includes('src="/ticketscenter/media/a.jpg"'));
});

test('search dropdown shows the empty state and keeps plain text readable', () => {
    const { buildDropdownHtml } = require('../../main/webapp/assets/js/site-shell.js');
    assert.ok(buildDropdownHtml('', 't', 0, []).includes('Không tìm thấy sự kiện nào phù hợp.'));
    const html = buildDropdownHtml('', 't', 1, [{ eventId: 'e1', title: 'Rock & Roll "Live"', venueName: "Nhà hát O'Neil" }]);
    assert.ok(html.includes('Rock &amp; Roll &quot;Live&quot;'));
    assert.ok(html.includes('Nhà hát O&#39;Neil'));
});
