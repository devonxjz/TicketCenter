'use strict';
const fs=require('node:fs');
const assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root='http://127.0.0.1:18084/ticketscenter';
const fixture=Object.fromEntries(fs.readFileSync('target/khanh04-web-fixture.properties','utf8').split(/\r?\n/).filter(line=>line&&!line.startsWith('#')).map(line=>line.split('=')));
(async()=>{
  const options={headless:true};if(process.env.TC_ADMIN_REFUND_BROWSER_EXECUTABLE)options.executablePath=process.env.TC_ADMIN_REFUND_BROWSER_EXECUTABLE;
  const browser=await chromium.launch(options),context=await browser.newContext({viewport:{width:1440,height:1000}});
  try{
    let csrf=(await (await context.request.get(root+'/api/auth/csrf')).json()).data.token;
    const login=await context.request.post(root+'/api/auth/login',{headers:{'X-CSRF-Token':csrf},data:{identifier:fixture.admin,password:process.env.TC_SCAN_FIXTURE_PASSWORD}});assert.equal(login.status(),200);
    const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(root+'/admin/refund-requests');await page.locator('#rows tr').first().waitFor();
    await page.selectOption('#status','REQUESTED');await page.locator('#filter button').click();await page.waitForFunction(()=>!document.querySelector('#feedback').textContent.includes('Đang tải'));
    await page.screenshot({path:'target/khanh04-list-desktop.png',fullPage:true});
    await page.goto(root+'/admin/refund-requests/'+fixture.uiApprove);await page.locator('#approve').waitFor({state:'visible'});
    assert.ok(await page.locator('#facts').innerText().then(s=>s.includes('<img src=x')));assert.equal(await page.locator('#facts img').count(),0);assert.equal(await page.evaluate(()=>window.__refundXss),undefined);
    const keys=[];let loseFirst=true;
    await page.route('**/api/admin/refund-requests/'+fixture.uiApprove+'/decision',async route=>{
      keys.push(route.request().headers()['idempotency-key']);
      if(loseFirst){loseFirst=false;const result=await route.fetch();assert.equal(result.status(),200);await route.abort('failed');}else await route.continue();
    });
    await page.locator('#approve').click();await page.locator('#decisionText').fill('Đã kiểm tra điều kiện hoàn vé');await page.locator('#confirm').click();
    await page.waitForFunction(()=>document.querySelector('#dialogError').textContent.includes('Kết nối gián đoạn'));
    await page.locator('#confirm').click();await page.waitForFunction(()=>!document.querySelector('#decisionDialog').open);
    await page.waitForFunction(()=>document.querySelector('#attempts').children.length===1);
    assert.equal(keys.length,2);assert.equal(keys[0],keys[1]);assert.equal(await page.locator('#approve').isVisible(),false);
    await page.screenshot({path:'target/khanh04-review-desktop.png',fullPage:true});
    await page.goto(root+'/admin/refund-requests/'+fixture.uiReject);await page.locator('#reject').waitFor({state:'visible'});await page.locator('#reject').click();
    assert.equal(await page.locator('#decisionText').evaluate(e=>e.checkValidity()),false);
    await page.locator('#decisionText').fill('Hồ sơ không đáp ứng điều kiện đã công bố');await page.locator('#confirm').click();await page.waitForFunction(()=>!document.querySelector('#decisionDialog').open);
    await page.waitForFunction(()=>document.querySelector('#tickets').textContent.includes('Còn hiệu lực'));
    await page.goto(root+'/admin/refund-requests/'+fixture.uiRetry);await page.locator('#retry').waitFor({state:'visible'});await page.locator('#retry').click();
    await page.locator('#confirm').click();await page.waitForFunction(()=>!document.querySelector('#decisionDialog').open);await page.waitForFunction(()=>document.querySelector('#attempts').children.length===2);assert.equal(await page.locator('#retry').isVisible(),false);
    await page.setViewportSize({width:390,height:844});await page.goto(root+'/admin/refund-requests/'+fixture.unknown);await page.locator('#detail').waitFor({state:'visible'});
    assert.equal(await page.locator('#retry').isVisible(),false);assert.match(await page.locator('#stateHint').innerText(),/Cần đối soát/);
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    await page.screenshot({path:'target/khanh04-review-mobile.png',fullPage:true});
    assert.deepEqual(errors,[]);fs.writeFileSync('target/khanh04-browser-acceptance.json',JSON.stringify({passed:true,checks:['filtered list','desktop review','escaped stored buyer name','approve confirmation','lost response same-key replay','exactly one attempt','required reject reason','reject restoration','failed retry new attempt','UNKNOWN retry hidden','mobile no page overflow','no JavaScript errors']},null,2));
    process.stdout.write('Browser acceptance: 12 checks passed; desktop and mobile screenshots saved\n');
  }finally{await browser.close();}
})().catch(error=>{process.stderr.write(error.stack+'\n');process.exitCode=1;});
