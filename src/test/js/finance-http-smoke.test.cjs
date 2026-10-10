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
test('report HTTP metrics retain exact money and shared UTC filters',async()=>{
 await login(fixture.admin);
 for(const metric of ['COHORT_REVENUE','CASH_FLOW','EVENT_SALES','SETTLEMENT_BALANCE']){
  const result=await request('/api/admin/reports?'+filters(metric));assert.equal(result.status,200);const data=result.data.data;
  assert.equal(data.metric,metric);assert.equal(data.timezone,'UTC');assert.equal(data.rows.length,1);assert.equal(data.rows[0].eventId,fixture.event);
  if(metric==='CASH_FLOW'){assert.equal(data.rows[0].ticketCaptured,'100');assert.equal(data.rows[0].netCashFlow,'100');}
  else assert.equal(data.rows[0].netPayable,'93');
 }
});
test('manager report and options restrict exact organization; staff export is denied before CSV bytes',async()=>{
 await login(fixture.manager);
 let result=await request('/api/organizations/'+fixture.org+'/reports?'+filters());assert.equal(result.status,200);
 result=await request('/api/admin/reports?'+filters());assert.equal(result.status,403);
 result=await request('/api/reports/options?organizationId='+fixture.org);assert.equal(result.status,200);assert.equal(result.data.data.organizations.length,1);assert.equal(result.data.data.organizations[0].id,fixture.org);
 result=await request('/api/organizations/'+fixture.otherOrg+'/reports?'+filters().replace(fixture.org,fixture.otherOrg));assert.equal(result.status,403);
 await login(fixture.staff);result=await request('/api/reports/export?'+filters().replace(fixture.org,fixture.otherOrg));assert.equal(result.status,403);assert.match(result.response.headers.get('content-type'),/application\/json/);
});
test('CSV HTTP streams BOM, exact values and Excel-safe formula cells',async()=>{
 await login(fixture.admin);
 const response=await fetch(origin+'/ticketscenter/api/reports/export?'+filters('EVENT_SALES',fixture.formulaEvent),{headers:{Cookie:cookie}});
 assert.equal(response.status,200);assert.match(response.headers.get('content-type'),/text\/csv/);assert.equal(response.headers.get('cache-control'),'no-store');assert.match(response.headers.get('content-disposition'),/^attachment; filename="ticketscenter-event_sales.csv"$/);
 const bytes=Buffer.from(await response.arrayBuffer());assert.deepEqual([...bytes.subarray(0,3)],[0xef,0xbb,0xbf]);assert.ok(bytes.toString('utf8').includes('"\t=1+1"'));assert.ok(bytes.toString('utf8').includes(fixture.formulaEvent));
});

test('report JSP renders authorized scope and actual summary/export UI',async()=>{await login(fixture.admin);let result=await request('/finance/reports');assert.equal(result.status,200);assert.match(result.data,/finance-reports\.js/);assert.match(result.data,/id="summary"/);assert.match(result.data,/id="top-events"/);await login(fixture.manager);result=await request('/finance/reports?organizationId='+fixture.org);assert.equal(result.status,200);await login(fixture.staff);result=await request('/finance/reports');assert.equal(result.status,403);});
test('manager cannot export another organization and unknown format fails before CSV bytes',async()=>{await login(fixture.manager);let result=await request('/api/reports/export?'+filters().replace(fixture.org,fixture.otherOrg));assert.equal(result.status,403);assert.match(result.response.headers.get('content-type'),/application\/json/);result=await request('/api/reports/export?'+filters()+'&format=xlsx');assert.equal(result.status,400);result=await request('/api/organizations/'+fixture.org+'/overview');assert.equal(result.status,404);});

test('admin overview HTTP returns real UTC KPI and seven exact-money chart days',async()=>{
 await login(fixture.admin);const result=await request('/api/admin/overview');assert.equal(result.status,200);
 const data=result.data.data;assert.equal(data.timezone,'UTC');assert.equal(data.organizationId,null);
 assert.ok(data.totalUsers>=3);assert.equal(data.dailyMetrics.length,7);
 assert.match(data.todayCohortGrossRevenue,/^\d+$/);assert.match(data.todayCohortCommission,/^\d+$/);
 assert.equal(data.dailyMetrics.at(-1).from,data.from);
 for(const row of data.dailyMetrics){assert.match(row.cohortGrossRevenue,/^\d+$/);assert.match(row.netCashFlow,/^-?\d+$/);}
 assert.equal(result.response.headers.get('cache-control'),'no-store');
});
test('audit HTTP list and detail return allowlisted metadata with exact ownership filters',async()=>{
 await login(fixture.admin);const params=new URLSearchParams({from:'2026-01-01T00:00:00Z',to:'2026-01-02T00:00:00Z',action:'TEST_HTTP',actorId:fixture.admin,targetTable:'tc_events',organizationId:fixture.org,eventId:fixture.event});
 let result=await request('/api/admin/audit-logs?'+params);assert.equal(result.status,200);assert.equal(result.data.data.total,1);
 assert.equal(result.data.data.rows[0].id,fixture.audit);
 result=await request('/api/admin/audit-logs/'+fixture.audit);assert.equal(result.status,200);assert.equal(result.data.data.id,fixture.audit);assert.equal(result.data.data.metadata.amount,'100');
 assert.deepEqual(Object.keys(result.data.data.metadata).sort(),['amount','eventId','organizationId']);
 assert.doesNotMatch(JSON.stringify(result.data),/DO_NOT_EXPOSE|123456|PRIVATE_TEST_BANK|passwordHash|metadataJson/);
});
test('audit HTTP rejects duplicate or invalid filters and unknown detail without SQL disclosure',async()=>{
 await login(fixture.admin);
 for(const [key,value]of [['action','A'],['actorId','1'],['targetTable','tc_unknown'],['from','invalid'],['pageSize','1000']]){
  const params=new URLSearchParams({from:'2026-01-01T00:00:00Z',to:'2026-01-02T00:00:00Z'});params.set(key,value);if(key==='action')params.append('action','B');
  const result=await request('/api/admin/audit-logs?'+params);assert.equal(result.status,400);assert.doesNotMatch(JSON.stringify(result.data),/SELECT|SQLException|password/);
 }
 let result=await request('/api/admin/audit-logs/'+crypto.randomUUID());assert.equal(result.status,404);
 result=await request('/api/admin/audit-logs/invalid');assert.equal(result.status,404);
});
test('admin readside APIs reject absent session, manager and check-in staff',async()=>{
 const paths=['/api/admin/overview','/api/admin/audit-logs?from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z','/api/admin/audit-logs/'+fixture.audit];
 for(const path of paths)assert.equal((await request(path,'GET',undefined,undefined,false)).status,401);
 for(const actor of [fixture.manager,fixture.staff]){await login(actor);for(const path of paths)assert.equal((await request(path)).status,403);}
});
test('admin audit and overview JSPs render dedicated UI and deny manager/staff',async()=>{
 await login(fixture.admin);
 for(const path of ['/finance/audit','/finance/overview']){const result=await request(path);assert.equal(result.status,200);assert.match(result.data,/finance-admin-readside\.js/);assert.doesNotMatch(result.data,/finance-reports\.js|<script>fixture/);assert.equal(result.response.headers.get('cache-control'),'no-store');}
 for(const actor of [fixture.manager,fixture.staff]){await login(actor);for(const path of ['/finance/audit','/finance/overview'])assert.equal((await request(path)).status,403);}
});
