const test = require('node:test');
const assert = require('node:assert/strict');
function implementation() { return require('../../main/webapp/assets/js/event/event-catalog.js'); }
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
test('catalog restores page, filters and local datetime inputs from shared URLs', () => {
  const {catalogState, localDateTime} = implementation();
  const state = catalogState('?page=3&keyword=Nhạc&sort=price_desc&from=2026-10-14T02%3A00%3A00Z');
  assert.equal(state.page, 3);
  assert.equal(state.keyword, 'Nhạc');
  assert.equal(state.sort, 'price_desc');
  assert.equal(state.from, localDateTime('2026-10-14T02:00:00Z'));
  assert.match(state.from, /^2026-10-\d{2}T\d{2}:\d{2}$/);
  assert.equal(catalogState('?page=-1&sort=invalid&from=invalid').page, 1);
  assert.equal(catalogState('?from=invalid').from, '');
});
test('catalog validates date range and requests six events without losing the instant', () => {
  const {catalogQuery, localDateTime} = implementation();
  const from = localDateTime('2026-10-14T02:00:00Z'), to = localDateTime('2026-10-15T02:00:00Z');
  const query = catalogQuery([['from',from],['to',to],['keyword',' Nhạc ']], 2);
  assert.equal(query.get('pageSize'), '6');
  assert.equal(query.get('page'), '2');
  assert.equal(query.get('from'), '2026-10-14T02:00:00.000Z');
  assert.equal(query.get('keyword'), 'Nhạc');
  assert.throws(() => catalogQuery([['from','invalid']],1), /ngày hợp lệ/);
  assert.throws(() => catalogQuery([['from',to],['to',from]],1), /phải sau/);
});
