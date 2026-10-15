const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const ui=require('../../main/webapp/assets/js/finance-admin-readside.js');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const actor='10000000-0000-0000-0000-000000000001';

test('audit query keeps UTC bounds, actor and canonical table alias without report metric',()=>{
 const q=ui.query({from:'2026-10-01T00:00',to:'2026-10-02T00:00',actorId:actor,targetTable:'tc_events',action:'TEST_HTTP',metric:'CASH_FLOW'},1);
 assert.equal(q.get('from'),'2026-10-01T00:00:00.000Z');assert.equal(q.get('actorId'),actor);assert.equal(q.get('targetTable'),'tc_events');assert.equal(q.has('metric'),false);
 assert.throws(()=>ui.query({from:'2026-02-31T00:00',to:'2026-03-02T00:00'},1));
 assert.throws(()=>ui.query({from:'2026-10-01T00:00',to:'2026-10-01T00:00'},1));
 assert.throws(()=>ui.query({from:'2026-10-01T00:00',to:'2026-10-02T00:00',actorId:'1'},1));
});
test('money and signed chart retain exact large VND and zero-day behavior',()=>{
 assert.equal(ui.formatVnd('9007199254740993'),'9.007.199.254.740.993 ₫');assert.equal(ui.formatVnd('-50'),'-50 ₫');
 const bars=ui.chartBars([{date:'2026-10-01',cohortGrossRevenue:'100',netCashFlow:'-50'},{date:'2026-10-02',cohortGrossRevenue:'0',netCashFlow:'0'}]);
 assert.equal(bars[0].revenueHeight,120);assert.equal(bars[0].cashHeight,60);assert.equal(bars[0].cashNegative,true);assert.equal(bars[1].cashHeight,0);
 assert.throws(()=>ui.chartBars([{date:'2026-10-01',cohortGrossRevenue:'1.5',netCashFlow:'0'}]));
});
test('errors expose only fixed text and validated correlation, never server secret',async()=>{
 await assert.rejects(ui.fetchResponse('/api/admin/audit-logs',async()=>new Response(JSON.stringify({error:{message:'SELECT secret password',correlationId:actor}}),{status:503,headers:{'Content-Type':'application/json'}})),e=>e.message.includes(actor)&&!e.message.includes('secret'));
 await assert.rejects(ui.fetchResponse('/api/admin/overview',async()=>new Response('<html>login</html>',{headers:{'Content-Type':'text/html'}})));
});
function harness(page='audit'){
 class Element{constructor(value=''){this.value=value;this.children=[];this.handlers={};this.textContent='';this.dataset={};}append(...children){this.children.push(...children);}replaceChildren(...children){this.children=[...children];this.textContent='';}addEventListener(event,handler){this.handlers[event]=handler;}setAttribute(){}scrollIntoView(){}}
 const ids=Object.fromEntries(['finance-readside','filters','organization','event','notice','rows','columns','previous','next','pagination','summary','queue-summary','basis','audit-detail','daily-chart','daily-values'].map(id=>[id,new Element()]));
 const form=ids.filters;form.elements={from:new Element(),to:new Element(),action:new Element(),actorId:new Element(),targetTable:new Element(),pageSize:new Element('20')};
 ids['finance-readside'].dataset={context:'/ticketscenter',page,admin:'true'};
 const requests=[],document={getElementById:id=>ids[id],createElement:()=>new Element(),createElementNS:()=>new Element(),createTextNode:text=>({textContent:text})};
 const network=url=>new Promise(resolve=>requests.push({url,resolve}));
 const response=data=>new Response(JSON.stringify({data}),{headers:{'Content-Type':'application/json'}});
 vm.runInNewContext(fs.readFileSync(require.resolve('../../main/webapp/assets/js/finance-admin-readside.js'),'utf8'),{document,fetch:network,URLSearchParams,Date,Intl,BigInt,Option:class extends Element{constructor(text,value){super(value);this.textContent=text;}},FormData:class{entries(){return Object.entries({...Object.fromEntries(Object.entries(form.elements).map(([key,e])=>[key,e.value])),organizationId:ids.organization.value,eventId:ids.event.value})[Symbol.iterator]();}}});
 const audit=()=>response({rows:[{id:actor,action:'TEST_HTTP',entityType:'Event',entityId:actor,actorId:actor,createdAt:'2026-10-01T00:00:00Z'}],page:1,pageSize:20,total:1,from:'2026-10-01T00:00:00Z',to:'2026-10-02T00:00:00Z'});
 return {ids,requests,response,audit,async ready(){if(page==='audit'){requests[0].resolve(response({organizations:[],events:[]}));await tick();await tick();}},change(name,value){const e=form.elements[name]||ids[name];e.value=value;form.handlers.change({target:e});},detail(){return ids.rows.children[0].children.at(-1).children[0].handlers.click();}};
}
for(const [name,value]of Object.entries({action:'PAYOUT_RESULT',actorId:actor,from:'2026-10-01T00:00',event:actor,pageSize:'50'}))test('editing '+name+' discards pending audit rows',async()=>{
 const h=harness();await h.ready();h.change(name,value);h.requests[1].resolve(h.audit());await tick();await tick();assert.equal(h.ids.rows.children.length,0);assert.match(h.ids.notice.textContent,/Bộ lọc/);
});
test('editing filters discards pending audit detail',async()=>{
 const h=harness();await h.ready();h.requests[1].resolve(h.audit());await tick();await tick();const pending=h.detail();h.change('action','PAYOUT_RESULT');h.requests.at(-1).resolve(h.response({id:actor,action:'OLD',metadata:{amount:'100'}}));await pending;assert.equal(h.ids['audit-detail'].children.length,0);
});
test('typing a text filter invalidates pending rows before blur',async()=>{
 const h=harness();await h.ready();h.ids.filters.elements.action.value='PAYOUT_RESULT';h.ids.filters.handlers.input?.({target:h.ids.filters.elements.action});h.requests[1].resolve(h.audit());await tick();await tick();assert.equal(h.ids.rows.children.length,0);assert.match(h.ids.notice.textContent,/Bộ lọc/);
});
test('organization change bubbling to the form does not discard its own scope response',async()=>{
 const h=harness();await h.ready();h.requests[1].resolve(h.audit());await tick();await tick();
 h.ids.organization.value=actor;const pending=h.ids.organization.handlers.change();h.ids.filters.handlers.change({target:h.ids.organization});
 h.requests.at(-1).resolve(h.response({organizations:[{id:actor,name:'New organization'}],events:[{id:actor,title:'New event'}]}));await pending;
 assert.equal(h.ids.organization.children.length,2);assert.equal(h.ids.event.children.length,2);assert.equal(h.ids.event.disabled,false);
});
test('typing an action during initial option load preserves organization choices without old rows',async()=>{
 const h=harness();h.change('action','PAYOUT_RESULT');h.requests[0].resolve(h.response({organizations:[{id:actor,name:'Organization'}],events:[]}));await tick();await tick();
 assert.equal(h.ids.organization.children.length,2);assert.equal(h.ids.rows.children.length,0);assert.match(h.ids.notice.textContent,/Bộ lọc/);
});
test('editing an unrelated filter during organization option load preserves its event choices',async()=>{
 const h=harness();await h.ready();h.requests[1].resolve(h.audit());await tick();await tick();h.ids.organization.value=actor;
 const pending=h.ids.organization.handlers.change();h.change('action','PAYOUT_RESULT');h.requests.at(-1).resolve(h.response({organizations:[{id:actor,name:'Organization'}],events:[{id:actor,title:'Event'}]}));await pending;
 assert.equal(h.ids.organization.children.length,2);assert.equal(h.ids.event.children.length,2);assert.equal(h.ids.event.disabled,false);assert.equal(h.ids.rows.children.length,0);
});
test('selecting another detail prevents earlier response overwriting current detail',async()=>{
 const h=harness();await h.ready();h.requests[1].resolve(h.audit());await tick();await tick();const first=h.detail(),firstRequest=h.requests.at(-1);const second=h.detail();h.requests.at(-1).resolve(h.response({id:actor,action:'CURRENT',metadata:{}}));await second;
 const current=h.ids['audit-detail'].children;firstRequest.resolve(h.response({id:actor,action:'OLD',metadata:{}}));await first;assert.deepEqual(h.ids['audit-detail'].children,current);
});
test('overview initialization requests only admin overview and renders seven days',async()=>{
 const h=harness('overview');assert.equal(h.requests[0].url,'/ticketscenter/api/admin/overview');
 h.requests[0].resolve(h.response({timezone:'UTC',totalUsers:3,activeOrganizations:2,todayCohortGrossRevenue:'9007199254740993',todayCohortCommission:'5',todayCashFlow:{ticketCaptured:'100',compensationCaptured:'0',ticketRefund:'0',compensationRefund:'0',netCashFlow:'100'},dailyMetrics:Array.from({length:7},(_,i)=>({date:'2026-10-0'+(i+1),cohortGrossRevenue:'100',netCashFlow:'-10'}))}));await tick();await tick();assert.equal(h.ids['daily-values'].children.length,7);assert.ok(h.ids.summary.children.length>0);
});

// The shared notification handler also runs on finance pages. Approval must only
// remove rows from the organization approval table, never a matching audit row.
function notificationApprovalHarness(pendingKind) {
 class Element {
  constructor(text=''){this.textContent=text;this.removed=false;this.classList={add(){},remove(){}};this.style={};}
  remove(){this.removed=true;} appendChild(){} setAttribute(){}
 }
 const financeRow=new Element('Organization audit '+actor),pendingRow=pendingKind?new Element('Organization '+actor):null;
 const pending={querySelectorAll:selector=>selector==='tr[data-org-id]'&&pendingKind==='attributed'&&!pendingRow.removed?[pendingRow]:[]};
 const ids={pendingOrgTableBody:pendingKind?pending:null,adminNotificationBadge:new Element('1'),adminNotificationCountText:new Element('1'),adminNotificationEmpty:new Element(),pendingOrgTitleCount:new Element()};
 const requests=[],document={
  getElementById:id=>ids[id]||null,createElement:()=>new Element(),body:new Element(),addEventListener(){},
  querySelector(selector){
   if(selector.startsWith('meta['))return {getAttribute:()=>selector.includes('csrf-token')?'synthetic-csrf':'/ticketscenter'};
   if(selector===`tr[data-org-id="${actor}"]`)return pendingKind==='attributed'?pendingRow:null;
   if(selector===`#pendingOrgTableBody tr[data-org-id="${actor}"]`)return pendingKind==='attributed'?pendingRow:null;
   return null;
  },
  querySelectorAll(selector){
   if(selector==='table tbody tr')return [financeRow,...(pendingRow?[pendingRow]:[])];
   if(selector==='#pendingOrgTableBody tr')return pendingRow?[pendingRow]:[];
   return [];
  }
 };
 const window={},fetch=async(url,options)=>{requests.push({url,options});return {ok:true};};
 vm.runInNewContext(fs.readFileSync(require.resolve('../../main/webapp/assets/js/admin-notifications.js'),'utf8'),{document,window,fetch,console,setTimeout(){}});
 return {window,financeRow,pendingRow,ids,requests};
}
test('shared notification approval preserves a finance audit row containing organization UUID',async()=>{
 const h=notificationApprovalHarness();await h.window.approveFromNotification(null,actor);
 assert.equal(h.financeRow.removed,false);
 assert.equal(h.requests[0].url,'/ticketscenter/api/admin/organization-requests/'+actor+'/approve');
 assert.equal(h.requests[0].options.method,'POST');assert.equal(h.requests[0].options.headers['X-CSRF-Token'],'synthetic-csrf');
});
for(const kind of ['attributed','legacy'])test('notification approval still updates its own '+kind+' pending organization row',async()=>{
 const h=notificationApprovalHarness(kind);await h.window.approveFromNotification(null,actor);
 assert.equal(h.pendingRow.removed,true);assert.equal(h.financeRow.removed,false);
});


test('overview queues use live counts and real destinations, and are cleared during refresh',async()=>{
 const h=harness('overview');
 h.requests[0].resolve(h.response({pendingOrganizations:2,pendingEvents:0,pendingRefunds:7,draftSettlements:3,dailyMetrics:[]}));
 await tick();await tick();
 const cards=h.ids['queue-summary'].children;
 assert.equal(cards.length,4);
 assert.deepEqual(cards.map(card=>card.children[1].textContent),['2','0','7','3']);
 assert.deepEqual(cards.map(card=>card.children[2].href),[
  '/ticketscenter/admin/orgs/approval','/ticketscenter/admin/events/approval',
  '/ticketscenter/admin/refund-requests','/ticketscenter/finance/settlements'
 ]);
 h.ids.filters.handlers.submit({preventDefault(){}});
 assert.equal(h.ids['queue-summary'].children.length,0);
 h.requests[1].resolve(new Response('Unavailable',{status:503}));await tick();await tick();
 assert.equal(h.ids['queue-summary'].children.length,0);
 assert.match(h.ids.notice.textContent,/khả dụng/);
});
