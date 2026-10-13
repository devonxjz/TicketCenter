'use strict';
const fs = require('node:fs');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const root = 'http://127.0.0.1:18086/ticketscenter';
const fixture = Object.fromEntries(fs.readFileSync('target/khanh06-web-fixture.properties', 'utf8').split(/\r?\n/).filter(line => line && !line.startsWith('#')).map(line => line.split('=')));
(async () => {
    const options = {headless: true};
    if (process.env.TC_KHANH06_BROWSER_EXECUTABLE) options.executablePath = process.env.TC_KHANH06_BROWSER_EXECUTABLE;
    const browser = await chromium.launch(options);
    const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
    try {
        const csrf = (await (await context.request.get(root + '/api/auth/csrf')).json()).data.token;
        const login = await context.request.post(root + '/api/auth/login', {headers: {'X-CSRF-Token': csrf}, data: {identifier: fixture.admin, password: process.env.TC_SCAN_FIXTURE_PASSWORD}});
        assert.equal(login.status(), 200);
        const page = await context.newPage(), errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.goto(root + '/events/review?eventId=' + fixture.uiEvent);
        await page.locator('#review').waitFor({state: 'visible'});
        await page.locator('#cancel-event').click();
        await page.locator('#cancellation-dialog').waitFor({state: 'visible'});
        await page.locator('#cancellation-reason').fill('Ngắn');
        await page.locator('#confirm-cancellation').click();
        assert.equal(await page.locator('#cancellation-dialog').evaluate(e => e.open), true);
        await page.locator('#cancellation-reason').fill('Thời tiết nguy hiểm <img src=x onerror=alert(1)>');
        const keys = [];let first = true;
        await page.route('**/api/admin/events/' + fixture.uiEvent + '/cancel', async route => {
            keys.push(route.request().headers()['idempotency-key']);
            if (first) {first = false;const response = await route.fetch();assert.equal(response.status(), 200);await route.abort('failed');}
            else await route.continue();
        });
        await page.locator('#confirm-cancellation').click();
        await page.waitForFunction(() => document.getElementById('cancellation-error').textContent.includes('Chưa xác định'));
        assert.equal(await page.locator('#cancellation-reason').evaluate(e => e.readOnly), true);
        await page.locator('#confirm-cancellation').click();
        await page.waitForFunction(() => !document.getElementById('cancellation-dialog').open);
        assert.equal(keys.length, 2);assert.equal(keys[0], keys[1]);
        await page.waitForFunction(() => !document.getElementById('cancellation-progress-panel').hidden);
        await page.waitForFunction(() => document.getElementById('review-summary').textContent.includes('Đã hủy'));
        await page.waitForFunction(() => document.getElementById('cancellation-counts').textContent.startsWith('1/1'));
        assert.equal(await page.locator('#cancel-event').isDisabled(), true);
        assert.equal(await page.locator('#cancellation-results img').count(), 0);
        await page.screenshot({path: 'target/khanh06-review-desktop.png', fullPage: true});
        await page.setViewportSize({width: 390, height: 844});
        await page.screenshot({path: 'target/khanh06-review-mobile.png', fullPage: true});
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
        assert.deepEqual(errors, []);
        fs.writeFileSync('target/khanh06-browser-acceptance.json', JSON.stringify({realAdminLogin: true, ui20Review: true, reasonValidation: true, lostResponseReplayedSameKey: true, noScriptErrors: true, mobileNoOverflow: true}, null, 2));
        console.log('KHANH-06 browser: modal, validation, lost response, replay, progress and mobile checks passed.');
    } finally {await context.close();await browser.close();}
})().catch(error => {console.error(error);process.exitCode = 1;});
