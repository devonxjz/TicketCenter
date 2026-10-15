const test = require('node:test');
const assert = require('node:assert/strict');
const ui = require('../../main/webapp/assets/js/finance-reports.js');
const vm=require('node:vm'),fs=require('node:fs');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
test('report periods include historical sales and count recent UTC calendar days exactly',()=>{
 assert.deepEqual(ui.periodBounds('all'),{from:'1970-01-01T00:00:00',to:'9999-12-31T23:59:59'});
 assert.deepEqual(ui.periodBounds('30',new Date('2026-10-14T08:00:00Z')),{from:'2026-09-15T00:00:00',to:'2026-10-15T00:00:00'});
 assert.deepEqual(ui.periodBounds('7',new Date('2026-01-02T08:00:00Z')),{from:'2025-12-27T00:00:00',to:'2026-01-03T00:00:00'});
 assert.throws(()=>ui.periodBounds('bad'));
});
test('default report includes historical sales and a period change invalidates the earlier result and matches CSV',async()=>{
 const h=harness();await h.ready();const initial=new URL(h.requests[1].url,'http://localhost');
 assert.equal(initial.searchParams.get('from'),'1970-01-01T00:00:00.000Z');assert.equal(initial.searchParams.get('to'),'9999-12-31T23:59:59.000Z');
 h.ids['report-period'].value='30';h.ids['report-period'].handlers.change();await tick();
 const recent=new URL(h.requests[2].url,'http://localhost');assert.equal(Date.parse(recent.searchParams.get('to'))-Date.parse(recent.searchParams.get('from')),30*86400000);
 h.requests[1].resolve(h.result());await tick();assert.equal(h.ids.rows.children.length,0);
 h.requests[2].resolve(h.response({metric:'COHORT_REVENUE',rows:[{eventTitle:'Actual event',grossRevenue:'500000'}],summary:{grossRevenue:'500000',paidOrderCount:1},total:1,pageSize:20,topEvents:[],from:recent.searchParams.get('from'),to:recent.searchParams.get('to')}));await tick();await tick();
 assert.match(h.ids['report-period-summary'].textContent,/30 ngày gần nhất/);
 const download=h.ids.export.handlers.click();await tick();const exported=new URL(h.requests[3].url,'http://localhost');
 for(const key of ['from','to','metric'])assert.equal(exported.searchParams.get(key),recent.searchParams.get(key));
 h.requests[3].resolve(new Response('eventTitle,grossRevenue\r\nActual event,500000',{headers:{'Content-Type':'text/csv'}}));await download;assert.equal(h.downloads,1);
});
test('missing settlement shows absence of a statement instead of financial zero cards',async()=>{
 const h=harness();h.ids.filters.elements.metric.value='SETTLEMENT_BALANCE';await h.ready();
 h.requests[1].resolve(h.response({metric:'SETTLEMENT_BALANCE',rows:[],summary:{totalEvents:0,grossRevenue:'0',netPayable:'0'},total:0,pageSize:20,topEvents:[]}));await tick();await tick();
 assert.equal(h.ids.summary.children.length,1);assert.match(h.ids.summary.children[0].textContent,/Chưa có bảng quyết toán/);
});
function harness(){
 class Element{constructor(value=''){this.value=value;this.children=[];this.handlers={};this.textContent='';this.dataset={};}append(...children){this.children.push(...children);}replaceChildren(...children){this.children=[...children];this.textContent='';}addEventListener(event,handler){this.handlers[event]=handler;}click(){downloads++;}remove(){} }
 const ids=Object.fromEntries(['finance-readside','filters','organization','event','notice','rows','columns','previous','next','pagination','summary','top-events','basis','export','report-period','report-period-summary'].map(x=>[x,new Element()]));
 if(ids['report-period'])ids['report-period'].value='all';const form=ids.filters;form.elements={from:new Element(),to:new Element(),metric:new Element('COHORT_REVENUE'),pageSize:new Element('20')};ids['finance-readside'].dataset={context:'/ticketscenter',admin:'true'};
 const requests=[];let downloads=0;const document={getElementById:id=>ids[id],createElement:()=>new Element(),body:new Element()};
 const network=url=>new Promise(resolve=>requests.push({url,resolve}));
 const response=data=>new Response(JSON.stringify({data}),{headers:{'Content-Type':'application/json'}});
 vm.runInNewContext(fs.readFileSync(require.resolve('../../main/webapp/assets/js/finance-reports.js'),'utf8'),{document,fetch:network,URLSearchParams,URL:{createObjectURL:()=> 'blob:test',revokeObjectURL(){}},Option:class extends Element{constructor(text,value){super(value);this.textContent=text;}},FormData:class{entries(){return Object.entries({...Object.fromEntries(Object.entries(form.elements).map(([key,input])=>[key,input.value])),organizationId:ids.organization.value,eventId:ids.event.value})[Symbol.iterator]();}},Date,Intl,BigInt,setTimeout:()=>0});
 return {ids,requests,response,get downloads(){return downloads;},async ready(){requests[0].resolve(response({organizations:[],events:[]}));await tick();await tick();},result(metric='COHORT_REVENUE'){return response({metric,rows:[{eventId:'Old report'}],summary:{totalEvents:1},topEvents:[],pageSize:20,total:1,timeBasis:''});},change(name,value){const target=form.elements[name]||ids[name];target.value=value;form.handlers.change?.({target});}};
}

for(const [name,value]of Object.entries({metric:'CASH_FLOW',event:'10000000-0000-0000-0000-000000000001',from:'2026-10-01T00:00',to:'2026-10-02T00:00',pageSize:'50'}))test('editing '+name+' rejects an in-flight report from the previous selection',async()=>{
 const h=harness();await h.ready();h.change(name,value);h.requests[1].resolve(h.result());await tick();await tick();assert.equal(h.ids.rows.children.length,0);assert.match(h.ids.notice.textContent,/Bộ lọc/);
});
test('editing filters during CSV preparation cancels download for the obsolete selection',async()=>{
 const h=harness();await h.ready();h.requests[1].resolve(h.result());await tick();await tick();const pending=h.ids.export.handlers.click();h.change('metric','CASH_FLOW');h.requests.at(-1).resolve(new Response('old csv',{headers:{'Content-Type':'text/csv'}}));await pending;assert.equal(h.downloads,0);assert.match(h.ids.notice.textContent,/Bộ lọc/);
});

test('report query preserves exact tenant, metric and UTC half-open bounds for CSV', () => {
  const values = { organizationId: '10000000-0000-0000-0000-000000000001', eventId: '',
    from: '2026-10-01T00:00', to: '2026-10-02T00:00', metric: 'CASH_FLOW', pageSize: '50' };
  assert.equal(ui.query(values, 'reports', 2).toString(),
    'from=2026-10-01T00%3A00%3A00.000Z&to=2026-10-02T00%3A00%3A00.000Z&page=2&pageSize=50&organizationId=10000000-0000-0000-0000-000000000001&metric=CASH_FLOW');
});
test('invalid calendar dates, inverted intervals and non-canonical tenants fail locally', () => {
  const base = {from: '2026-10-01T00:00', to: '2026-10-02T00:00'};
  assert.throws(() => ui.query({...base, from: '2026-02-31T00:00'}, 'reports', 1));
  assert.throws(() => ui.query({...base, to: base.from}, 'reports', 1));
  assert.throws(() => ui.query({...base, organizationId: '1'}, 'reports', 1));
});
test('VND formatting keeps amounts beyond Number safe integer precision', () => {
  assert.equal(ui.formatVnd('9007199254740993'), '9.007.199.254.740.993 ₫');
  assert.equal(ui.formatVnd('-12345'), '-12.345 ₫');
  assert.equal(ui.formatVnd(null), '—');
  assert.equal(ui.formatVnd('1.5'), '—');
});
test('failed responses expose a fixed message and optional correlation, never raw SQL', async () => {
  const network = async () => new Response(JSON.stringify({error:{code:'REPORT_UNAVAILABLE',message:'SELECT bank_password secret',correlationId:'10000000-0000-0000-0000-000000000001'}}),
    {status:503,headers:{'Content-Type':'application/json'}});
  await assert.rejects(ui.fetchResponse('/api/admin/reports', 'application/json', network),
    error => error.message.includes('10000000-0000-0000-0000-000000000001') && !error.message.includes('secret'));
});
test('login HTML or JSON error cannot be saved as a successful CSV export', async () => {
  await assert.rejects(ui.fetchResponse('/api/reports/export', 'text/csv', async () =>
    new Response('<html>Login</html>', {headers:{'Content-Type':'text/html'}})));
  await assert.rejects(ui.fetchResponse('/api/reports/export', 'text/csv', async () =>
    new Response('{"error":{}}', {status:403,headers:{'Content-Type':'application/json'}})));
});
test('successful response uses same-origin credentials and returns the original stream', async () => {
  const response = new Response('eventId\n', {headers:{'Content-Type':'text/csv;charset=UTF-8'}});
  const actual = await ui.fetchResponse('/api/reports/export', 'text/csv', async (_url, options) => {
    assert.equal(options.credentials, 'same-origin');
    assert.equal(options.headers.Accept, 'text/csv');
    return response;
  });
  assert.equal(await actual.text(), 'eventId\n');
});

test('page initialization does not crash when optional UI elements are missing', () => {
  class Element{constructor(value=''){this.value=value;this.children=[];this.handlers={};this.textContent='';this.dataset={};}append(...children){this.children.push(...children);}replaceChildren(...children){this.children=[...children];this.textContent='';}addEventListener(event,handler){this.handlers[event]=handler;}click(){}remove(){} }
  const ids=Object.fromEntries(['finance-readside','filters','organization','event','notice','rows','columns'].map(x=>[x,new Element()]));
  if(ids['report-period'])ids['report-period'].value='all';const form=ids.filters;form.elements={from:new Element(),to:new Element(),metric:new Element('COHORT_REVENUE'),pageSize:new Element('20')};ids['finance-readside'].dataset={context:'/ticketscenter',admin:'true'};
  const document={getElementById:id=>ids[id]||null,createElement:()=>new Element(),body:new Element()};
  assert.doesNotThrow(() => {
    vm.runInNewContext(fs.readFileSync(require.resolve('../../main/webapp/assets/js/finance-reports.js'),'utf8'),{
      document,fetch:()=>new Promise(()=>{}),URLSearchParams,URL:{createObjectURL:()=>'blob:test',revokeObjectURL(){}},Option:class extends Element{constructor(text,value){super(value);this.textContent=text;}},FormData:class{entries(){return [][Symbol.iterator]();}},Date,Intl,BigInt,setTimeout:()=>0
    });
  });
});
