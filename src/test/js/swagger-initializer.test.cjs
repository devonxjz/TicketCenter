const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const initializer = path.resolve(__dirname, '../../main/webapp/swagger/swagger-initializer.js');

function runtime(pathname = '/ticketscenter/swagger/index.html', fetchImpl = async () => ({ ok: true, json: async () => ({ data: { token: 'session-token' } }) })) {
  const context = { URL, console, fetch: fetchImpl, location: { origin: 'http://localhost:8080', pathname, href: 'http://localhost:8080' + pathname } };
  context.window = context;
  context.globalThis = context;
  vm.runInNewContext(fs.existsSync(initializer) ? fs.readFileSync(initializer, 'utf8') : '', context);
  assert.ok(context.TicketCenterSwagger, 'Swagger runtime must be available');
  return context.TicketCenterSwagger;
}

test('uses the current WAR context for spec, API server and CSRF requests', async () => {
  const calls = [];
  const app = runtime(undefined, async (url, options) => { calls.push({ url, options }); return { ok: true, json: async () => ({ data: { token: 'new-session-token' } }) }; });
  const options = app.createOptions({ paths: {} });
  assert.equal(options.spec.servers[0].url, '/ticketscenter');
  const request = await options.requestInterceptor({ url: 'http://localhost:8080/ticketscenter/api/auth/login', method: 'POST', headers: { 'X-CSRF-Token': 'stale' } });
  assert.equal(calls[0].url, '/ticketscenter/api/auth/csrf');
  assert.equal(calls[0].options.credentials, 'same-origin');
  assert.equal(request.headers['X-CSRF-Token'], 'new-session-token');
  assert.equal(request.credentials, 'same-origin');
});

test('root deployment stays at root and read-only calls never fetch CSRF', async () => {
  let calls = 0;
  const options = runtime('/swagger/', async () => { calls++; throw new Error('unexpected fetch'); }).createOptions({ paths: {} });
  assert.equal(options.spec.servers[0].url, '/');
  await options.requestInterceptor({ url: '/api/me', method: 'GET', headers: {} });
  assert.equal(calls, 0);
});

test('blocks requests outside this origin and application context before fetching a token', async () => {
  let calls = 0;
  const options = runtime(undefined, async () => { calls++; }).createOptions({ paths: {} });
  for (const url of ['https://external.example/api/orders', 'http://localhost:8080/other/api/orders']) {
    await assert.rejects(options.requestInterceptor({ url, method: 'POST', headers: {} }), /application|origin/);
  }
  assert.equal(calls, 0);
});

test('fails closed when CSRF endpoint is unavailable or returns malformed data', async () => {
  for (const result of [{ ok: false }, { ok: true, json: async () => ({ data: {} }) }]) {
    const options = runtime(undefined, async () => result).createOptions({ paths: {} });
    await assert.rejects(options.requestInterceptor({ url: '/ticketscenter/api/auth/login', method: 'POST', headers: {} }), /CSRF/);
  }
});

test('refreshes the session token for each mutation without changing idempotency keys', async () => {
  let calls = 0;
  const options = runtime(undefined, async () => ({ ok: true, json: async () => ({ data: { token: 'token-' + ++calls } }) })).createOptions({ paths: {} });
  for (let index = 1; index <= 2; index++) {
    const request = await options.requestInterceptor({ url: '/ticketscenter/api/auth/login', method: 'POST', headers: { 'Idempotency-Key': 'caller-key' } });
    assert.equal(request.headers['X-CSRF-Token'], 'token-' + index);
    assert.equal(request.headers['Idempotency-Key'], 'caller-key');
  }
  assert.equal(options.persistAuthorization, false);
  assert.equal(options.validatorUrl, null);
  assert.equal(options.queryConfigEnabled, false);
});

test('planned operations cannot be executed even with concrete path parameters', async () => {
  let calls = 0;
  const options = runtime(undefined, async () => { calls++; }).createOptions({ paths: { '/api/events/{eventId}': { get: { 'x-implementation-status': 'planned' } } } });
  await assert.rejects(options.requestInterceptor({ url: '/ticketscenter/api/events/event-1', method: 'GET', headers: {} }), /chưa|planned/);
  assert.equal(calls, 0);
});

test('Swagger selector enables implemented operations and hides planned Try it out buttons', () => {
  const options = runtime().createOptions({ paths: {} });
  const wrapper = options.plugins[0]().statePlugins.spec.wrapSelectors.allowTryItOutFor;
  const state = { getIn(keys) {
    return keys.join('/') === 'json/paths//health/live/get/x-implementation-status' ? 'implemented' : 'planned';
  } };
  const selector = wrapper(() => true);
  assert.equal(selector(state, '/health/live', 'get'), true);
  assert.equal(selector(state, '/api/events', 'get'), false);
  assert.equal(wrapper(() => false)(state, '/health/live', 'get'), false);
});

test('bodyless logout supplies the media type required by the application filter', async () => {
  const options = runtime().createOptions({ paths: { '/api/auth/logout': { post: { 'x-implementation-status': 'implemented' } } } });
  const request = await options.requestInterceptor({ url: '/ticketscenter/api/auth/logout', method: 'POST', headers: { accept: 'application/json' } });
  assert.equal(request.headers['Content-Type'], 'application/json');
  const form = await options.requestInterceptor({ url: '/ticketscenter/api/auth/logout', method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' } });
  assert.equal(form.headers['content-type'], 'application/x-www-form-urlencoded');
  assert.equal(form.headers['Content-Type'], undefined);
});
