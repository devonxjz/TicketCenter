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
