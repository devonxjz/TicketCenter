const test = require('node:test');
const assert = require('node:assert/strict');
test('logout uses session CSRF and never navigates on an unconfirmed failure', async () => {
    const { signOut } = require('../../main/webapp/assets/js/site-shell.js');
    const calls = [];
    await signOut(async (path, options) => { calls.push({path, options}); return {ok:true,json:async()=>path.endsWith('/csrf')?{data:{token:'session-token'}}:{data:{loggedOut:true}}}; }, '/ticketscenter');
    assert.equal(calls[1].path, '/ticketscenter/api/auth/logout');
    assert.equal(calls[1].options.method, 'POST');
    assert.equal(calls[1].options.headers['X-CSRF-Token'], 'session-token');
    await assert.rejects(signOut(async () => ({ok:false,json:async()=>({error:{message:'Phiên chưa được xác nhận'}})}), '/ticketscenter'), /Phiên chưa được xác nhận/);
});
