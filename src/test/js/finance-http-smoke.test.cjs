'use strict';
const {test,before}=require('node:test');
const assert=require('node:assert/strict');
const origin=process.env.TC_FINANCE_SMOKE_ORIGIN;
const fixture=JSON.parse(process.env.TC_FINANCE_HTTP_IDS||'{}');
const token=process.env.TC_FINANCE_HTTP_FIXTURE_TOKEN;
let cookie='',csrf='',settlement,rule;
async function request(path,method='GET',body,key,session=true){
 const headers={};if(session&&cookie)headers.Cookie=cookie;
 if(body!==undefined){headers['Content-Type']='application/json';headers['X-CSRF-Token']=csrf;if(key)headers['Idempotency-Key']=key;}
 const response=await fetch(origin+'/ticketscenter'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
 const type=response.headers.get('content-type')||'';
 return {status:response.status,data:type.includes('application/json')?await response.json():await response.text(),response};
}
async function login(actor){
 const response=await fetch(origin+'/ticketscenter/__fixture/session',{method:'POST',headers:{'Content-Type':'application/json','X-Fixture-Token':token},body:JSON.stringify({actorId:actor})});
 assert.equal(response.status,200,'Controlled fixture session must succeed');
 cookie=response.headers.get('set-cookie').split(';')[0];csrf=(await response.json()).data.csrfToken;
}
before(()=>{assert.ok(origin&&token&&fixture.admin,'Explicit isolated fixture required');});
test('fixture authentication rejects missing control token',async()=>{
 const response=await fetch(origin+'/ticketscenter/__fixture/session',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({actorId:fixture.admin})});assert.equal(response.status,403);
});
test('financial HTTP APIs reject absent session and CSRF before mutation',async()=>{
 let result=await request('/api/admin/events/'+fixture.event+'/settlement','GET',undefined,undefined,false);assert.equal(result.status,401);
 await login(fixture.admin);
 const saved=csrf;csrf='wrong';result=await request('/api/admin/events/'+fixture.event+'/settlement/recalculate','POST',{},crypto.randomUUID());csrf=saved;assert.equal(result.status,403);
});
test('commission HTTP create/read/replay and linked edit preserve canonical contract',async()=>{
 await login(fixture.admin);
 const terms={ratePercent:'5.0000',fixedFee:'2',effectiveFrom:'2020-01-01T00:00:00Z',effectiveTo:'2099-01-01T00:00:00Z'},key=crypto.randomUUID();
 const path='/api/admin/organizations/'+fixture.org+'/commission-rules';
 let result=await request(path,'POST',terms,key);assert.equal(result.status,201);rule=result.data.data.ruleId;assert.equal(result.data.data.fixedFee,'2');
 result=await request(path,'POST',terms,key);assert.equal(result.status,200);assert.equal(result.data.data.ruleId,rule);assert.equal(result.data.data.isReplay,true);
 result=await request(path);assert.equal(result.status,200);assert.ok(result.data.data.some(row=>row.ruleId===rule));
 result=await request('/api/admin/commission-rules/'+fixture.rule+'/edit','POST',terms,crypto.randomUUID());assert.equal(result.status,409);assert.equal(result.data.error.code,'RULE_ALREADY_ATTACHED_TO_EVENTS');
});
test('settlement HTTP recalc/blockers/confirm freeze exact amounts',async()=>{
 await login(fixture.admin);let result=await request('/api/admin/events/'+fixture.event+'/settlement/recalculate','POST',{},crypto.randomUUID());
 assert.equal(result.status,200);assert.equal(result.data.data.netPayable,'93');settlement=result.data.data.settlementId;
 result=await request('/api/admin/events/'+fixture.event+'/settlement-blockers');assert.equal(result.data.data.blockerCount,0);
 result=await request('/api/admin/settlements/'+settlement+'/confirm','POST',{},crypto.randomUUID());assert.equal(result.status,200);assert.equal(result.data.data.status,'CONFIRMED');
 result=await request('/api/admin/settlements/'+settlement+'/confirm','POST',{},crypto.randomUUID());assert.equal(result.data.data.isReplay,true);
 result=await request('/api/admin/events/'+fixture.event+'/settlement/recalculate','POST',{},crypto.randomUUID());assert.equal(result.status,409);
});
test('payout HTTP stable ID transfers once and preserves available balance',async()=>{
 await login(fixture.admin);const key=crypto.randomUUID(),terms={amount:'40',bankAccountNumber:'123456789',bankName:'TestBank',accountHolderName:'TestHolder',note:'HTTP fixture'};
 let result=await request('/api/admin/settlements/'+settlement+'/payouts','POST',terms,key);assert.equal(result.status,200);assert.equal(result.data.data.status,'SUCCEEDED');assert.equal(result.data.data.availableToPay,'53');
 result=await request('/api/admin/settlements/'+settlement+'/payouts','POST',terms,key);assert.equal(result.status,200);assert.equal(result.data.data.isReplay,true);assert.equal(result.data.data.paidAmount,'40');
 result=await request('/api/admin/settlements/'+settlement+'/payouts','POST',{...terms,amount:'41'},key);assert.equal(result.status,409);
 result=await request('/api/admin/settlements/'+settlement+'/payouts');assert.equal(result.status,200);assert.equal(result.data.data.length,1);
});
test('payout HTTP recovers reservation-only PENDING from stored history without bank terms',async()=>{
 await login(fixture.admin);let result=await request('/api/admin/events/'+fixture.pendingEvent+'/settlement');const sid=result.data.data.settlementId;
 result=await request('/api/admin/settlements/'+sid+'/payouts');assert.equal(result.data.data[0].payoutId,fixture.pendingPayout);assert.equal(result.data.data[0].verificationState,'PENDING');
 result=await request('/api/admin/settlements/'+sid+'/payouts','POST',{amount:'40',bankAccountNumber:'123',bankName:'TestBank',accountHolderName:'TestHolder'},crypto.randomUUID());assert.equal(result.status,409);assert.equal(result.data.error.code,'PAYOUT_RECONCILIATION_REQUIRED');
 result=await request('/api/admin/settlements/'+settlement+'/payouts/'+fixture.pendingPayout+'/reconcile','POST',{},fixture.pendingPayout);assert.equal(result.status,404);
 const path='/api/admin/settlements/'+sid+'/payouts/'+fixture.pendingPayout+'/reconcile';result=await request(path,'POST',{},fixture.pendingPayout);assert.equal(result.status,200);assert.equal(result.data.data.status,'SUCCEEDED');assert.equal(result.data.data.paidAmount,'40');assert.equal(result.data.data.availableToPay,'53');
 result=await request(path,'POST',{},fixture.pendingPayout);assert.equal(result.status,200);assert.equal(result.data.data.isReplay,true);assert.equal(result.data.data.paidAmount,'40');
});
test('nonadmin HTTP mutation rejected by current DB role',async()=>{
 await login(fixture.manager);const result=await request('/api/admin/events/'+fixture.event+'/settlement','GET');assert.equal(result.status,403);
});
test('financial JSP pages render real selectors and escaped content',async()=>{
 await login(fixture.admin);
 for(const path of ['/finance/commission','/finance/settlements']){const result=await request(path);assert.equal(result.status,200);assert.match(result.data,/finance-app/);assert.match(result.data,/HTTP Fixture/);assert.doesNotMatch(result.data,/<script>fixture/);}
});
const filters=(metric='COHORT_REVENUE',eventId=fixture.event)=>new URLSearchParams({metric,organizationId:fixture.org,eventId,from:'2019-01-01T00:00:00Z',to:'2099-01-01T00:00:00Z'}).toString();
