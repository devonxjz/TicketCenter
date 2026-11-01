'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ORG = '11111111-1111-4111-8111-111111111111';
class Element {
 constructor(tag='div') { this.tagName=tag;this.children=[];this.listeners={};this.value='';this.textContent='';this.hidden=true; }
 append(...items) { this.children.push(...items);if(this.tagName==='select'&&!this.value)this.value=this.children[0]?.value||''; }
 replaceChildren(...items) { this.children=[];this.append(...items); }
 addEventListener(name,callback) { this.listeners[name]=callback; }
 get selectedOptions() { return this.children.filter(child=>child.value===this.value); }
}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
function dashboardHarness({gate,fail=false}={}) {
 const elements=new Map();
 const get=id=>{if(!elements.has(id))elements.set(id,new Element(id==='organization'?'select':'div'));return elements.get(id);};
 vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/organizer/organizer-pages.js','utf8'),{
  document:{body:{dataset:{livePage:'organizer-dashboard',context:'/tickets'}},getElementById:get,createElement:tag=>new Element(tag)},
  Node:Element, URLSearchParams,location:{search:'',pathname:'/tickets/organizer/dashboard'},
  fetch:async url=>{
   if(url==='/tickets/api/event-organizations')return {ok:true,status:200,json:async()=>({data:[{organizationId:ORG,name:'Fixture organization'}]})};
   if(gate)await gate;
   if(fail)return {ok:false,status:403,json:async()=>({error:{message:'Không có quyền truy cập'}})};
   return {ok:true,status:200,json:async()=>url.includes('/overview')?{data:{eventCount:1,pendingEvents:1,paidOrders:0,todayCohortGrossRevenue:0,todayCashFlow:{netCashFlow:0},dailyMetrics:[]}}:{data:[{eventId:ORG,title:'Fixture event',status:'PENDING_APPROVAL',totalCapacity:20}]}};
  }
 });
 return {get};
}
test('organizer event actions stay hidden until authorized data and UUID routes are loaded',async()=>{
 let release;const gate=new Promise(resolve=>release=resolve),h=dashboardHarness({gate});await flush();
 assert.equal(h.get('create-event').hidden,true);assert.equal(h.get('all-events').hidden,true);
 release();await flush();
 assert.equal(h.get('create-event').hidden,false);
 assert.equal(h.get('create-event').href,'/tickets/events/manage/editor?organizationId='+ORG);
 assert.equal(h.get('all-events').href,'/tickets/events/manage?organizationId='+ORG);
 assert.equal(h.get('events').children[0].children[3].children[0].textContent,'Chờ duyệt');
});
test('organizer permission error never exposes operational navigation',async()=>{
 const h=dashboardHarness({fail:true});await flush();
 assert.equal(h.get('create-event').hidden,true);assert.equal(h.get('all-events').hidden,true);
 assert.equal(h.get('live-status').textContent,'Không có quyền truy cập');
});
