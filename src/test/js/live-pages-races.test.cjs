const test = require('node:test'), assert = require('node:assert/strict'), vm = require('node:vm'), fs = require('node:fs');
const source = fs.readFileSync(require.resolve('../../main/webapp/assets/js/live-pages.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
class Element {
  constructor() { this.children=[]; this.listeners={}; this.value=''; this.textContent=''; this.hidden=false; }
  append(...children) { this.children.push(...children); if (!this.value && children[0]?.value) this.value=children[0].value; }
  replaceChildren(...children) { this.children=children; }
  addEventListener(type, callback) { this.listeners[type]=callback; }
  querySelector() { return null; }
  get selectedOptions() { return this.children.filter(option => option.value===this.value); }
}
function page(kind) {
  const elements=new Map(), pending=[];
  const get=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
  get('coupon-type').value='PERCENTAGE';
  const context={document:{body:{dataset:{livePage:kind,context:''}},getElementById:get,createElement:()=>new Element()},Node:Element,
    URLSearchParams,location:{search:'',pathname:'/',href:''},crypto:{randomUUID:()=> 'test-key'},
    fetch:async url=>{
      if(url==='/api/event-organizations')return {ok:true,status:200,json:async()=>({data:[{organizationId:'org-a',name:'Tổ chức A'},{organizationId:'org-b',name:'Tổ chức B'}]})};
      return new Promise(resolve=>pending.push({url,reply:(data,status=200)=>resolve({ok:status<400,status,json:async()=>data})}));
    }};
  vm.runInNewContext(source,context);
  function reply(url,data,status) { const index=pending.findIndex(request=>request.url===url);assert.ok(index>=0,url);pending.splice(index,1)[0].reply(data,status); }
  return {get,reply,pending};
}
test('switching organizations ignores an older coupon response and clears old rows', async()=>{
  const {get,reply}=page('organizer-coupons');await flush();
  get('organization').value='org-b';get('organization').listeners.change();
  const coupon=code=>({id:code,code,active:true,discountType:'PERCENTAGE',percentageValue:10,consumedUses:0,maxUses:10,availableUses:10});
  reply('/api/organizations/org-b/coupons',{coupons:[coupon('B')]});await flush();
  reply('/api/organizations/org-a/coupons',{coupons:[coupon('A')]});await flush();
  assert.equal(get('coupons').children[0].children[0].textContent,'B');
  assert.equal(get('live-status').textContent,'');
});
test('an older dashboard failure cannot overwrite the newly selected organization', async()=>{
  const {get,reply}=page('organizer-dashboard');await flush();
  get('organization').value='org-b';get('organization').listeners.change();
  reply('/api/organizations/org-b/overview',{data:{eventCount:2,pendingEvents:1,paidOrders:3,todayCohortGrossRevenue:'1000',todayCashFlow:{netCashFlow:'900'},asOf:'2026-10-13T00:00:00Z',dailyMetrics:[]}});
  reply('/api/organizations/org-b/events?page=1&pageSize=6',{data:[]});await flush();
  reply('/api/organizations/org-a/overview',{error:{message:'Old request failed'}},500);
  reply('/api/organizations/org-a/events?page=1&pageSize=6',{data:[]});await flush();
  assert.equal(get('org-name').textContent,'Tổ chức B');
  assert.equal(get('metric-events').textContent,'2');
  assert.equal(get('live-status').textContent,'');
});
test('coupon type requires only the visible amount input', async()=>{
  const {get}=page('organizer-coupons');await flush();
  assert.equal(get('coupon-percent').required,true);assert.equal(get('coupon-fixed').disabled,true);
  get('coupon-type').value='FIXED';get('coupon-type').listeners.change();
  assert.equal(get('coupon-percent').disabled,true);assert.equal(get('coupon-fixed').required,true);
});
