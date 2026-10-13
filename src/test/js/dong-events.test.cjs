'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const {webcrypto}=require('node:crypto');
const A='11111111-1111-4111-8111-111111111111',B='22222222-2222-4222-8222-222222222222';
class Element {
 constructor(tag='div'){this.tagName=tag;this.children=[];this.dataset={};this.style={};this.listeners={};this.disabled=false;this.hidden=false;this.value='';this.textContent='';}
 append(...items){this.children.push(...items);if(this.tagName==='select'&&!this.value)this.value=this.children[0]?.value||'';}
 replaceChildren(...items){this.children=[...items];if(this.tagName==='select')this.value=this.children[0]?.value||'';}
 addEventListener(name,callback){this.listeners[name]=callback;}
 setAttribute(name,value){this[name]=value;}
 emit(name){if(name==='click'&&this.disabled)return Promise.resolve();return this.listeners[name]?.({preventDefault(){}});}
}
function deferred(){let resolve;const promise=new Promise(r=>{resolve=r;});return {promise,resolve};}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
function event(id){return {eventId:id,title:id===A?'Event A':'Event B',organizationName:'Fixture org',status:'PENDING_APPROVAL',version:1,totalCapacity:1};}
function harness(delays={}){
 const ids=new Map(),posts=[],requests=[];
 const get=id=>{if(!ids.has(id))ids.set(id,new Element());return ids.get(id);};get('filter-status').value='PENDING_APPROVAL';get('rule').tagName='select';
 const body=new Element('body');body.dataset={dongPage:'review',contextPath:''};
 const document={body,getElementById:get,createElement:tag=>new Element(tag),listeners:{},
  addEventListener(name,listener){this.listeners[name]=listener;},dispatchEvent(event){this.listeners[event.type]?.(event);}};
 const response=value=>({ok:true,status:200,json:async()=>value});
 const fetch=async(path,options={})=>{
  requests.push(path);
  if(options.method==='POST'){posts.push({path,body:JSON.parse(options.body)});return response({data:{}});}
  if(delays[path])await delays[path].promise;
  if(path.startsWith('/api/admin/events?'))return response({data:[event(A),event(B)],total:2,pageSize:20});
  if(path==='/api/auth/csrf')return response({data:{token:'fixture-csrf'}});
  const id=path.includes(A)?A:B;
  if(path.endsWith('/layout'))return response({data:[{zoneId:id,name:id===A?'Zone A':'Zone B',zoneType:'STANDING',basePrice:'100',availableSeats:1,totalCapacity:1}]});
  if(path.endsWith('/commission-rules'))return response({data:[{commissionRuleId:id,ratePercent:'5',fixedFee:'0'}]});
  return response({data:event(id)});
 };
 const session=new Map();
 vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/dong-events.js','utf8'),{
  document,fetch,URL,URLSearchParams,TextEncoder,crypto:webcrypto,XMLHttpRequest:function(){},CustomEvent:class{constructor(type,options){this.type=type;this.detail=options.detail;}},
  location:{search:'',origin:'http://localhost'},confirm:()=>true,
  sessionStorage:{getItem:k=>session.get(k),setItem:(k,v)=>session.set(k,v),removeItem:k=>session.delete(k)}
 });
 return {get,posts,requests,rowButton:index=>get('events').children[index].children[4].children[0]};
}

test('late Event detail cannot replace the newly selected review or its policy',async()=>{
 const delayed=deferred(),h=harness({['/api/admin/events/'+A]:delayed});await flush();
 const first=h.rowButton(0).emit('click');await flush();
 await h.rowButton(1).emit('click');assert.equal(h.get('review-title').textContent,'Event B');
 delayed.resolve();await first;
 assert.equal(h.get('review-title').textContent,'Event B');assert.equal(h.get('rule').children[0].value,B);
 await h.get('publish').emit('click');assert.equal(h.posts.length,1);assert.equal(h.posts[0].path,'/api/admin/events/'+B+'/publish');assert.equal(h.posts[0].body.commissionRuleId,B);
});

test('late layout and rules from an old review cannot overwrite active Event inventory or policy',async()=>{
 const delayed=deferred(),h=harness({['/api/admin/events/'+A+'/layout']:delayed});await flush();
 const first=h.rowButton(0).emit('click');await flush();
 await h.rowButton(1).emit('click');delayed.resolve();await first;
 assert.equal(h.get('review-title').textContent,'Event B');assert.equal(h.get('rule').children[0].value,B);
 assert.match(h.get('review-zones').children[0].children[0].textContent,/Zone B/);
});

test('switching reviews disables decisions until the new complete review is loaded',async()=>{
 const delayed=deferred(),h=harness({['/api/admin/events/'+B]:delayed});await flush();
 await h.rowButton(0).emit('click');assert.equal(h.get('publish').disabled,false);
 const second=h.rowButton(1).emit('click');await flush();
 assert.equal(h.get('publish').disabled,true);assert.equal(h.get('reject').disabled,true);
 delayed.resolve();await second;assert.equal(h.get('review-title').textContent,'Event B');assert.equal(h.get('publish').disabled,false);
});
