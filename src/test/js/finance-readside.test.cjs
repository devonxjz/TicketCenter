const test = require('node:test');
const assert = require('node:assert/strict');
const ui = require('../../main/webapp/assets/js/finance-readside.js');

test('report query preserves exact tenant, metric and UTC half-open bounds for CSV', () => {
  const values = { organizationId: '10000000-0000-0000-0000-000000000001', eventId: '',
    from: '2026-10-01T00:00', to: '2026-10-02T00:00', metric: 'CASH_FLOW', pageSize: '50' };
  assert.equal(ui.query(values, 'reports', 2).toString(),
    'from=2026-10-01T00%3A00%3A00.000Z&to=2026-10-02T00%3A00%3A00.000Z&page=2&pageSize=50&organizationId=10000000-0000-0000-0000-000000000001&metric=CASH_FLOW');
});
test('invalid calendar dates, inverted intervals and non-canonical tenants fail locally', () => {
  const base = {from: '2026-10-01T00:00', to: '2026-10-02T00:00'};
  assert.throws(() => ui.query({...base, from: '2026-02-31T00:00'}, 'reports', 1));
  assert.throws(() => ui.query({...base, to: base.from}, 'reports', 1));
  assert.throws(() => ui.query({...base, organizationId: '1'}, 'reports', 1));
});
test('audit filter query encodes values and excludes report-only metric', () => {
  const query = ui.query({from: '2026-10-01T00:00', to: '2026-10-02T00:00',
    action: 'PAYOUT_CREATED', entityType: 'Settlement', entityId: 'ref_1', metric: 'CASH_FLOW'}, 'audit', 1);
  assert.equal(query.get('action'), 'PAYOUT_CREATED');
  assert.equal(query.get('entityId'), 'ref_1');
  assert.equal(query.has('metric'), false);
});
test('VND formatting keeps amounts beyond Number safe integer precision', () => {
  assert.equal(ui.formatVnd('9007199254740993'), '9.007.199.254.740.993 ₫');
  assert.equal(ui.formatVnd('-12345'), '-12.345 ₫');
  assert.equal(ui.formatVnd(null), '—');
  assert.equal(ui.formatVnd('1.5'), '—');
});
test('failed responses expose a fixed message and optional correlation, never raw SQL', async () => {
  const network = async () => new Response(JSON.stringify({error:{code:'REPORT_UNAVAILABLE',message:'SELECT bank_password secret',correlationId:'10000000-0000-0000-0000-000000000001'}}),
    {status:503,headers:{'Content-Type':'application/json'}});
  await assert.rejects(ui.fetchResponse('/api/admin/reports', 'application/json', network),
    error => error.message.includes('10000000-0000-0000-0000-000000000001') && !error.message.includes('secret'));
});
test('login HTML or JSON error cannot be saved as a successful CSV export', async () => {
  await assert.rejects(ui.fetchResponse('/api/reports/export', 'text/csv', async () =>
    new Response('<html>Login</html>', {headers:{'Content-Type':'text/html'}})));
  await assert.rejects(ui.fetchResponse('/api/reports/export', 'text/csv', async () =>
    new Response('{"error":{}}', {status:403,headers:{'Content-Type':'application/json'}})));
});
test('successful response uses same-origin credentials and returns the original stream', async () => {
  const response = new Response('eventId\n', {headers:{'Content-Type':'text/csv;charset=UTF-8'}});
  const actual = await ui.fetchResponse('/api/reports/export', 'text/csv', async (_url, options) => {
    assert.equal(options.credentials, 'same-origin');
    assert.equal(options.headers.Accept, 'text/csv');
    return response;
  });
  assert.equal(await actual.text(), 'eventId\n');
});
