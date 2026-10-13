const test = require('node:test');
const assert = require('node:assert/strict');
function implementation() { return require('../../main/webapp/assets/js/public-events.js'); }
test('detail accepts current and legacy event links but rejects invalid and zero IDs', () => {
  const { eventId } = implementation();
  const id = '5318d6e0-16fc-5da5-a40b-14cde62ac2f7';
  assert.equal(eventId('?eventId=' + id), id);
  assert.equal(eventId('?id=' + id), id);
  assert.equal(eventId('?eventId=not-an-id'), null);
  assert.equal(eventId('?eventId=00000000-0000-0000-0000-000000000000'), null);
});
test('API failure is shown rather than substituting sample events', async () => {
  const { requestJson } = implementation();
  await assert.rejects(requestJson(async () => ({ok: false, json: async () => ({error:{message:'Dữ liệu chưa khả dụng'}})}), '/api/events'), /Dữ liệu chưa khả dụng/);
  let options;
  const result = await requestJson(async (_url, value) => { options = value; return {ok:true,json:async()=>({data:[]})}; }, '/api/events');
  assert.deepEqual(result.data, []);
  assert.equal(options.credentials, 'same-origin');
  assert.equal(options.cache, 'no-store');
});
test('missing price stays unknown and integer VND never loses precision', () => {
  const { price } = implementation();
  assert.equal(price(null), 'Chưa có giá vé');
  assert.equal(price('0'), 'Miễn phí');
  assert.equal(price('9007199254740993'), '9.007.199.254.740.993 đ');
});
