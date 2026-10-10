const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const vm = require('node:vm');

const origin = process.env.TC_SWAGGER_SMOKE_ORIGIN;
if (!origin || !/^http:\/\/127\.0\.0\.1:\d+$/.test(origin)) {
  throw new Error('Run via src/test/scripts/swagger-smoke.ps1 against its isolated loopback Tomcat.');
}

function boundedFetch(url, options = {}) {
  return fetch(url, { ...options, signal: AbortSignal.timeout(5000) });
}

function session() {
  let cookie;
  return async (url, options = {}) => {
    const headers = { ...options.headers };
    if (cookie) headers.Cookie = cookie;
    const response = await boundedFetch(url, { ...options, headers });
    const update = response.headers.get('set-cookie');
    if (update) cookie = update.split(';')[0];
    return response;
  };
}

for (const context of ['', '/ticketscenter']) {
  const base = origin + context;
  const label = context || 'ROOT';

  test(label + ': WAR serves UI, redirects, spec aliases and pinned offline assets', async () => {
    for (const route of ['/swagger', '/swagger/']) {
      const redirect = await boundedFetch(base + route, { redirect: 'manual' });
      assert.equal(redirect.status, 302);
      assert.equal(new URL(redirect.headers.get('location'), base).pathname, context + '/swagger/index.html');
    }
    const page = await boundedFetch(base + '/swagger/index.html');
    assert.equal(page.status, 200);
    assert.match(page.headers.get('content-type'), /text\/html/);
    const html = await page.text();
    assert.match(html, /swagger-initializer\.js/);
    assert.doesNotMatch(html, /(?:src|href)=["'](?:https?:)?\/\//i, 'UI must use bundled resources');
    for (const route of ['/api/openapi.json', '/swagger/openapi.json']) {
      const response = await boundedFetch(base + route);
      assert.equal(response.status, 200);
      assert.match(response.headers.get('content-type'), /application\/json/);
      assert.equal((await response.json()).openapi, '3.0.3');
    }
    for (const [file, hash] of [
      ['swagger-ui-bundle.js', '050bc415ee7048dcd881682678f720264e7da5e373f7461d7c58c755305255f7'],
      ['swagger-ui.css', '1ac324f7dcd27e4b9386b4bd6421271ec147e922a22c05ba24b11515e9aa6321']
    ]) {
      const response = await boundedFetch(base + '/swagger/vendor/' + file);
      assert.equal(response.status, 200);
      assert.equal(crypto.createHash('sha256').update(Buffer.from(await response.arrayBuffer())).digest('hex'), hash);
    }
  });

  test(label + ': health and readiness responses match documented source contracts', async () => {
    const live = await boundedFetch(base + '/health/live');
    assert.equal(live.status, 200);
    assert.deepEqual(await live.json(), { data: { status: 'UP' } });
    const ready = await boundedFetch(base + '/health/ready');
    assert.equal(ready.status, 503, 'Fixture deliberately does not initialize persistence');
    assert.deepEqual(await ready.json(), { error: { code: 'DEPENDENCY_UNAVAILABLE' } });
  });

  test(label + ': real validation and CSRF filters reject invalid requests before service lookup', async () => {
    const call = session();
    const me = await call(base + '/api/me');
    assert.equal(me.status, 401);
    const noCsrf = await call(base + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
    // Login is explicitly public in the owner's CsrfFilter; absent service returns503.
    assert.equal(noCsrf.status, 503);
    assert.equal((await noCsrf.json()).error.code, 'DATABASE_UNAVAILABLE');
    const protectedMutation = await call(base + '/api/__smoke/protected', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
    assert.equal(protectedMutation.status, 403);
    assert.equal((await protectedMutation.json()).error.code, 'CSRF_TOKEN_INVALID');
    const wrongType = await call(base + '/api/auth/login', { method: 'POST', body: '{}' });
    assert.equal(wrongType.status, 415);
    assert.equal((await wrongType.json()).error.code, 'CONTENT_TYPE_INVALID');
    const oversized = await call(base + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: 'x'.repeat(65537) });
    assert.equal(oversized.status, 413);
    assert.equal((await oversized.json()).error.code, 'PAYLOAD_TOO_LARGE');
  });

  test(label + ': tokens are session-scoped and valid CSRF reaches the actual service boundary', async () => {
    const call = session();
    const csrf = await call(base + '/api/auth/csrf');
    assert.equal(csrf.status, 200);
    assert.match(csrf.headers.get('set-cookie') || '', /HttpOnly/i);
    assert.match(csrf.headers.get('cache-control') || '', /no-store/i);
    const token = (await csrf.json()).data.token;
    assert.ok(typeof token === 'string' && token.length > 0, 'Runtime token required; value omitted from evidence');
    const foreign = await session()(base + '/api/__smoke/protected', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': token }, body: '{}' });
    assert.equal(foreign.status, 403);
    const passed = await call(base + '/api/__smoke/protected', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': token }, body: '{}' });
    assert.equal(passed.status,404,'Valid CSRF passes the real filter to an unmapped protected route');
    const valid = await call(base + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': token }, body: '{}' });
    assert.equal(valid.status, 503);
    assert.equal((await valid.json()).error.code, 'DATABASE_UNAVAILABLE');
  });

  test(label + ': deployed initializer performs bodyless logout through real filters', async () => {
    const call = session();
    const spec = await (await call(base + '/api/openapi.json')).json();
    const initializer = await (await call(base + '/swagger/swagger-initializer.js')).text();
    const runtime = { URL, console, location: { origin, pathname: context + '/swagger/index.html', href: base + '/swagger/index.html' }, fetch: (url, options) => call(new URL(url, base + '/'), options) };
    vm.runInNewContext(initializer, runtime);
    const options = runtime.TicketCenterSwagger.createOptions(spec);
    assert.equal(options.spec.servers[0].url, context || '/');
    const request = await options.requestInterceptor({ url: base + '/api/auth/logout', method: 'POST', headers: { accept: 'application/json' } });
    assert.equal(request.headers['Content-Type'], 'application/json');
    const logout = await call(request.url, { method: request.method, headers: request.headers });
    assert.equal(logout.status, 200);
    assert.deepEqual(await logout.json(), { data: { loggedOut: true } });
    const replay = await call(base + '/api/auth/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' } });
    assert.equal(replay.status, 200, 'Logout remains idempotent after session invalidation');
    const eventUrl = base + '/api/events/event-1';
    assert.equal(spec.paths['/api/events/{eventId}'].get['x-implementation-status'], 'implemented');
    assert.equal((await options.requestInterceptor({ url: eventUrl, method: 'GET', headers: {} })).url, eventUrl);
    let unavailable = 0;
    for (const [path, item] of Object.entries(spec.paths)) {
      for (const [method, operation] of Object.entries(item)) {
        if (!/^(get|post|put|patch|delete|head|options|trace)$/.test(method)
            || operation['x-implementation-status'] === 'implemented') continue;
        ++unavailable;
        const url = base + path.replace(/\{[^}]+\}/g, 'contract-placeholder');
        await assert.rejects(options.requestInterceptor({ url, method: method.toUpperCase(), headers: {} }), /planned\/legacy/,
          method.toUpperCase() + ' ' + path + ' must remain documentation-only');
      }
    }
    assert.ok(unavailable > 0, 'The deployed contract contains planned or legacy operations');
  });
}
