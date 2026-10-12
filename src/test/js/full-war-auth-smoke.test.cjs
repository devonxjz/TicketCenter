'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const crypto=require('node:crypto');
const configured=Boolean(process.env.TC_FULL_WAR_AUTH_FILE);

test('full WAR uses real identity, session, CSRF and finance authorization', {skip:!configured}, async t=>{
 const fixture=JSON.parse(fs.readFileSync(process.env.TC_FULL_WAR_AUTH_FILE,'utf8'));
 assert.match(fixture.origin,/^http:\/\/127\.0\.0\.1:\d+\/ticketscenter$/);
 assert.equal(fixture.adminEmail,'vuong.acceptance@test.invalid');
 class Browser {
  cookie='';csrf='';
  async request(path,method='GET',body,csrf=true,contentType='application/json') {
   const headers={Accept:'application/json'};
   if(this.cookie)headers.Cookie=this.cookie;
   if(body!==undefined)headers['Content-Type']=contentType;
   if(csrf&&this.csrf)headers['X-CSRF-Token']=this.csrf;
   const response=await fetch(fixture.origin+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),redirect:'manual',signal:AbortSignal.timeout(15000)});
   const cookie=response.headers.getSetCookie().find(value=>value.startsWith('JSESSIONID='));
   if(cookie){assert.match(cookie,/HttpOnly/i);assert.match(cookie,/Path=\/ticketscenter/);this.cookie=cookie.split(';')[0];}
   const text=await response.text();let data;try{data=JSON.parse(text);}catch{}
   return {status:response.status,response,data,text};
  }
  async login(email,password,role) {
   const before=await this.request('/api/auth/csrf');assert.equal(before.status,200);
   const oldCookie=this.cookie,oldToken=before.data.data.token;
   const result=await this.request('/api/auth/login','POST',{identifier:email,password},false);
   assert.equal(result.status,200);assert.equal(result.data.data.role,role);assert.equal(result.data.data.authenticated,true);
   assert.notEqual(this.cookie,oldCookie);this.csrf=result.response.headers.get('x-csrf-token');assert.ok(this.csrf);assert.notEqual(this.csrf,oldToken);
   assert.equal((await this.request('/api/me')).status,200);
  }
 }
 const anonymous=new Browser(),admin=new Browser(),manager=new Browser(),staff=new Browser(),buyer=new Browser();
 let org;
 const period='?from=2026-01-01T00%3A00%3A00Z&to=2027-01-01T00%3A00%3A00Z';
 await t.test('full annotation deployment is live and database ready',async()=>{for(const path of ['/health/live','/health/ready']){const r=await anonymous.request(path);assert.equal(r.status,200);assert.equal(r.data.data.status,'UP');}assert.equal((await anonymous.request('/api/openapi.json')).status,200);});
 await t.test('real login JSP compiles with declared local JSTL runtime',async()=>{const r=await anonymous.request('/auth/login');assert.equal(r.status,200);assert.match(r.text,/<form/);});
 await t.test('anonymous requests cannot read finance and no session bridge is deployed',async()=>{for(const path of ['/api/admin/overview','/api/admin/audit-logs','/api/organizations/'+crypto.randomUUID()+'/overview'])assert.equal((await anonymous.request(path)).status,401);assert.equal((await anonymous.request('/__fixture/session')).status,404);});
 await t.test('wrong password is rejected by real identity service',async()=>{const r=await anonymous.request('/api/auth/login','POST',{identifier:fixture.adminEmail,password:'wrong-password'},false);assert.equal(r.status,401);assert.equal(r.data.error.code,'INVALID_CREDENTIALS');});
 await t.test('ADMIN login rotates session ID and CSRF token',async()=>admin.login(fixture.adminEmail,fixture.adminPassword,'ADMIN'));
 await t.test('ADMIN can read actual overview, audit and finance JSP pages',async()=>{
  for(const path of ['/api/admin/overview','/api/admin/audit-logs'+period,'/finance/overview','/finance/audit','/finance/commission','/finance/settlements','/finance/reports']){
   const r=await admin.request(path);assert.equal(r.status,200,path);
   if(path.startsWith('/finance/')){
    assert.match(r.text,/<nav[^>]+aria-label="Điều hướng tài chính"/, 'Finance pages must provide the same accessible navigation');
    assert.match(r.text,/id="adminNotificationBell"/, 'Keep the shared organization notification header');
    assert.ok(r.text.includes('TicketsCenter · Tài chính'),'The included footer must render Vietnamese as UTF-8');
    assert.match(r.text,new RegExp('href="/ticketscenter'+path+'"[^>]*aria-current="page"'), 'Identify the current page');
   }
   if(path==='/finance/audit'||path==='/finance/reports'){
    assert.match(r.text,/<label>Tổ chức<select/,'Shared scope labels must render as UTF-8');
    assert.match(r.text,/<label>Từ thời điểm \(UTC, bao gồm\)/);
    assert.match(r.text,/<label>Số dòng mỗi trang<select/);
   }
  }
 });
 await t.test('MANAGER login and exact organization read dispatch succeed',async()=>{await manager.login('abcc@manager.com','123','MANAGER');const options=await manager.request('/api/reports/options');assert.equal(options.status,200);org=options.data.data.organizations[0].id;assert.match(org,/^[a-f0-9-]{36}$/);const r=await manager.request('/api/organizations/'+org+'/overview');assert.equal(r.status,200);assert.equal(r.data.data.organizationId,org);assert.equal((await manager.request('/api/organizations/'+org+'/reports'+period)).status,200);
  const page=await manager.request('/finance/reports');assert.equal(page.status,200);
  assert.match(page.text,/<nav[^>]+aria-label="Điều hướng tài chính"/);
  assert.match(page.text,/href="\/ticketscenter\/finance\/reports"[^>]*aria-current="page"/);
  assert.doesNotMatch(page.text,/id="adminNotificationBell"|href="[^\"]*\/finance\/(?:commission|settlements|audit|overview)"/,'Manager must not receive Admin navigation');
 });
 await t.test('organization routing rejects malformed suffix/filter and isolates tenants',async()=>{assert.equal((await manager.request('/api/organizations/'+crypto.randomUUID()+'/overview')).status,403);assert.equal((await manager.request('/api/organizations/'+org+'/overview/extra')).status,404);assert.equal((await manager.request('/api/organizations/'+org+'/overview?unexpected=1')).status,400);assert.equal((await manager.request('/api/organizations/not-a-uuid/overview')).status,400);});
 await t.test('manager cannot read ADMIN overview or audit',async()=>{for(const path of ['/api/admin/overview','/api/admin/audit-logs'+period,'/finance/overview','/finance/audit'])assert.equal((await manager.request(path)).status,403,path);});
 await t.test('STAFF login does not confer organization finance or ADMIN access',async()=>{await staff.login('abcc@staff.com','123','STAFF');for(const path of ['/api/organizations/'+org+'/overview','/api/organizations/'+org+'/reports'+period,'/api/admin/overview','/finance/overview','/finance/reports','/finance/audit','/finance/commission','/finance/settlements'])assert.equal((await staff.request(path)).status,403,path);});
 await t.test('CUSTOMER login does not confer finance access',async()=>{await buyer.login('buyer@ticketscenter.vn','123','CUSTOMER');for(const path of ['/api/organizations/'+org+'/overview','/api/admin/audit-logs'+period,'/finance/reports'])assert.equal((await buyer.request(path)).status,403,path);});
 await t.test('real manager session can export CSV while STAFF is rejected before CSV bytes',async()=>{const path='/api/reports/export'+period+'&organizationId='+org;const good=await manager.request(path);assert.equal(good.status,200);assert.match(good.response.headers.get('content-type'),/^text\/csv/);assert.match(good.response.headers.get('content-disposition'),/attachment/);const denied=await staff.request(path);assert.equal(denied.status,403);assert.match(denied.response.headers.get('content-type'),/^application\/json/);assert.equal(denied.data.error.code,'FORBIDDEN');});
 await t.test('authenticated mutation without CSRF is rejected before business execution',async()=>{const r=await admin.request('/api/admin/settlements/'+crypto.randomUUID()+'/confirm','POST',{},false);assert.equal(r.status,403);assert.equal((await admin.request('/api/me')).status,200);});
 await t.test('Event organization POST remains with its owner servlet',async()=>{
  const path='/api/organizations/'+org+'/events';
  const invalid=await manager.request(path,'POST',{});assert.equal(invalid.status,400);assert.equal(invalid.data.error.code,'INVALID_EVENT_INPUT');
  const media=await manager.request(path,'POST',{},true,'text/plain');assert.equal(media.status,415);assert.equal(media.data.error.code,'CONTENT_TYPE_INVALID');
 });
 await t.test('existing prototype menu entries lead to the live finance pages',async()=>{
  for(const [oldPath,target] of [['/admin','/finance/overview'],['/admin/dashboard','/finance/overview'],['/admin/settlements','/finance/settlements'],['/admin/audit-logs','/finance/audit']]){
   const r=await admin.request(oldPath);assert.equal(r.status,302,oldPath);assert.equal(r.response.headers.get('location'),'/ticketscenter'+target);
   assert.equal((await admin.request(target)).status,200);
  }
  const r=await manager.request('/organizer/reports');assert.equal(r.status,302);assert.equal(r.response.headers.get('location'),'/ticketscenter/finance/reports');assert.equal((await manager.request('/finance/reports')).status,200);
  assert.equal((await admin.request('/admin/settlements','HEAD')).status,302);
  assert.equal((await admin.request('/admin/settlements','POST',{})).status,405,'Never redirect a write request through a navigation alias');
 });
 await t.test('logout requires valid CSRF, invalidates the real session and is idempotent',async()=>{assert.equal((await admin.request('/api/auth/logout','POST',{},false)).status,403);const r=await admin.request('/api/auth/logout','POST',{});assert.equal(r.status,200);assert.equal(r.data.data.loggedOut,true);assert.equal((await admin.request('/api/me')).status,401);assert.equal((await admin.request('/api/admin/overview')).status,401);assert.equal((await admin.request('/api/auth/logout','POST',{},false)).status,200);});
});
