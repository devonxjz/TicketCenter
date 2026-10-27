'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
class Element {
 constructor(){this.children=[];this.listeners={};this.dataset={};this.hidden=true;this.disabled=false;this.value='';this.textContent='';this.attributes={};this.isConnected=true;}
 append(...items){this.children.push(...items);}
 replaceChildren(...items){this.children=[];this.append(...items);}
 addEventListener(name,fn){this.listeners[name]=fn;}
 setAttribute(name,value){this.attributes[name]=value;}
 focus(){this.focused=true;}
 scrollIntoView(){this.scrolled=true;}
}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
async function harness(){
 const elements=new Map(),requests=[];
 const get=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
 get('filter-status').value='PENDING_APPROVAL';
 vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/event/event-management.js','utf8'),{
  document:{body:{dataset:{context:'/tickets',dongPage:'review'}},getElementById:get,createElement:()=>new Element()},URLSearchParams,URL,matchMedia:()=>({matches:false}),location:{search:'',origin:'http://localhost'},
  fetch:(url,options)=>{if(url.includes('?'))return Promise.resolve({ok:true,json:async()=>({data:['a','b'].map(eventId=>({eventId,title:eventId,organizationName:'Org',status:'PENDING_APPROVAL'})),total:2,pageSize:6})});return new Promise(resolve=>requests.push({url,options,resolve}));}
 });
 await flush();
 const open=index=>get('events').children[index].children[4].children[0];
 function answer(id,fail=false){for(const request of requests.filter(r=>!r.done&&r.url.startsWith('/tickets/api/admin/events/'+id))){request.done=true;const data=request.url.endsWith('/layout')?[]:request.url.endsWith('/commission-rules')?[{commissionRuleId:'rule',ratePercent:5,fixedFee:0}]:{eventId:id,title:'Event '+id,organizationName:'Org',status:'PENDING_APPROVAL',coverImageUrl:'https://example.com/image.jpg'};request.resolve({ok:!fail,status:fail?503:200,json:async()=>fail?{error:{message:'Mất kết nối'}}:{data}});}}
 return {get,open,answer,requests};
}
test('opening a dossier immediately shows loading, scrolls and focuses before APIs complete',async()=>{
 const h=await harness(),pending=h.open(0).listeners.click();
 assert.equal(h.get('review').hidden,false);assert.equal(h.get('review').attributes['aria-busy'],'true');
 assert.equal(h.get('review').scrolled,true);assert.equal(h.get('review-title').focused,true);assert.equal(h.get('publish').disabled,true);
 h.answer('a');await pending;
 assert.equal(h.get('review-title').textContent,'Event a');assert.equal(h.get('review').attributes['aria-busy'],'false');assert.equal(h.get('publish').disabled,false);
 assert.ok(h.requests.every(r=>!r.options.method));
});
test('a stale dossier response cannot overwrite the latest selection',async()=>{
 const h=await harness(),a=h.open(0).listeners.click(),b=h.open(1).listeners.click();h.answer('b');await b;h.answer('a');await a;
 assert.equal(h.get('review-title').textContent,'Event b');
});
test('closing during loading invalidates the response and returns focus to the opener',async()=>{
 const h=await harness(),pending=h.open(0).listeners.click();h.get('review-close').listeners.click();h.answer('a');await pending;
 assert.equal(h.get('review').hidden,true);assert.equal(h.open(0).focused,true);assert.equal(h.get('publish').disabled,true);
});
test('failed dossier loads show an in-panel error and retry without enabling approval',async()=>{
 const h=await harness(),pending=h.open(0).listeners.click();h.answer('a',true);await pending;
 assert.equal(h.get('review').hidden,false);assert.match(h.get('review-notice').textContent,/Mất kết nối/);assert.equal(h.get('review-retry').hidden,false);assert.equal(h.get('publish').disabled,true);
 const retry=h.get('review-retry').listeners.click();h.answer('a');await retry;
 assert.equal(h.get('review-title').textContent,'Event a');assert.equal(h.get('review-retry').hidden,true);
});
