'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const script=fs.readFileSync(path.resolve(__dirname,'../../main/webapp/assets/js/finance-workbench.js'),'utf8');
const flush=async()=>{for(let i=0;i<4;i++) await new Promise(resolve=>setImmediate(resolve));};
const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return {promise,resolve,reject};};

function workbench({pending=false}={}) {
 class Element {
  constructor(tag,id=''){this.tagName=tag.toUpperCase();this.id=id;this.dataset={};this.value='';this.disabled=false;this.hidden=false;this.children=[];this.listeners={};this.textContent='';}
  addEventListener(type,listener){this.listeners[type]=listener;}
  dispatch(type){return this.listeners[type]?.({preventDefault(){}});}
  append(...nodes){this.children.push(...nodes);}
  appendChild(node){this.append(node);return node;}
  replaceChildren(...nodes){this.children=nodes;}
  get lastElementChild(){return this.children[this.children.length-1] || null;}
  querySelectorAll(selector){const found=[];for(const child of this.children){if(child.tagName.toLowerCase()===selector)found.push(child);found.push(...child.querySelectorAll(selector));}return found;}
 }
 const elements={};
 const get=id=>elements[id] ||= new Element(id==='event'?'select':'div',id);
 get('finance-app').dataset={page:'settlements',context:'',csrf:'test-csrf',payoutMode:'fixture-simulated'};
 for(const id of ['recalculate','confirm','payout-submit','reconcile']) {const button=get(id);button.tagName='BUTTON';button.disabled=id!=='reconcile';button.hidden=id==='reconcile';}
 const form=get('payout-form');form.elements=[];
 for(const name of ['amount','bankAccountNumber','bankName','accountHolderName','note']) {const input=new Element('input');input.name=name;input.value=name==='amount'?'40':'Test';form.elements.push(input);form.elements[name]=input;form.append(input);}
 form.append(get('payout-submit'),get('reconcile'));
 const summaries={A:summary('SA',pending?'40':'0'),B:summary('SB','0')};
 const histories={SA:pending?[{payoutId:'old-payout',amount:'40',status:'PENDING',verificationState:'UNKNOWN'}]:[],SB:[]};
 const requests=[];let postHandler=async()=>({status:'SUCCEEDED'}),getHandler=null,key=0;
 const fetch=async(url,options)=>{
  const request={url,...options};requests.push(request);
  let data;
  if(options.method==='POST') data=await postHandler(request);
  else if(getHandler) data=await getHandler(request);
  else data=read(request);
  return {ok:true,json:async()=>({data})};
 };
 function read(request){if(request.url.endsWith('settlement-blockers'))return {items:[]};if(request.url.endsWith('/payouts'))return histories[request.url.includes('/SA/')?'SA':'SB'];return summaries[request.url.includes('/A/')?'A':'B'];}
 vm.runInNewContext(script,{document:{getElementById:get,createElement:tag=>new Element(tag)},fetch,crypto:{randomUUID:()=>`key-${++key}`},Date,BigInt});
 return {get,form,summaries,histories,requests,read,setPost:handler=>postHandler=handler,setGet:handler=>getHandler=handler,posts:()=>requests.filter(r=>r.method==='POST'),choose:async id=>{get('event').value=id;get('event').dispatch('change');await flush();},historyButtons:()=>get('payouts').querySelectorAll('button')};
}
function summary(id,pending){return {settlementId:id,status:'CONFIRMED',endTime:'2020-01-01T00:00:00Z',grossRevenue:'100',totalRefund:'0',totalCommission:'0',netPayable:'100',paidAmount:'0',pendingAmount:pending,availableToPay:pending==='0'?'100':'60'};}

test('restored pending history can reconcile by stored payout ID without bank terms',async()=>{
 const ui=workbench({pending:true});await ui.choose('A');
 assert.equal(ui.get('payout-submit').disabled,true);
 assert.equal(ui.historyButtons().length,1,'A restored pending payout needs a recovery action');
 ui.historyButtons()[0].dispatch('click');await flush();
 const request=ui.posts()[0];assert.equal(request.url,'/api/admin/settlements/SA/payouts/old-payout/reconcile');
 assert.equal(request.body,'{}');assert.equal(request.headers['Idempotency-Key'],'old-payout');
});

test('payout locks selection, all mutations and bank inputs through the refresh; duplicate submissions are ignored',async()=>{
 const ui=workbench();await ui.choose('A');const transfer=deferred(),refresh=deferred();ui.setPost(()=>transfer.promise);
 ui.form.dispatch('submit');
 for(const id of ['event','recalculate','confirm','payout-submit','reconcile'])assert.equal(ui.get(id).disabled,true,id+' must remain locked');
 assert.ok(ui.form.elements.every(input=>input.disabled),'Bank terms must be immutable while sending');
 ui.form.dispatch('submit');ui.get('confirm').dispatch('click');ui.get('recalculate').dispatch('click');ui.get('reconcile').dispatch('click');
 ui.get('event').value='B';ui.get('event').dispatch('change');
 assert.equal(ui.posts().length,1);assert.equal(ui.get('event').value,'A');
 ui.setGet(request=>request.url.endsWith('/settlement')?refresh.promise:ui.read(request));
 transfer.resolve({status:'SUCCEEDED'});await flush();assert.equal(ui.get('event').disabled,true,'Refresh is part of the mutation');
 refresh.resolve(ui.summaries.A);await flush();assert.equal(ui.get('event').disabled,false);assert.equal(ui.get('payout-submit').disabled,false);assert.ok(ui.form.elements.every(input=>!input.disabled));
});

test('lost payout response refreshes durable pending history and unlocks recovery controls',async()=>{
 const ui=workbench();await ui.choose('A');ui.setPost(async()=>{ui.summaries.A=summary('SA','40');ui.histories.SA=[{payoutId:'key-1',amount:'40',status:'PENDING',verificationState:'UNKNOWN'}];throw new Error('Lost response');});
 ui.form.dispatch('submit');await flush();assert.equal(ui.get('event').disabled,false);assert.equal(ui.get('payout-submit').disabled,true);assert.equal(ui.historyButtons().length,1);
 ui.setPost(async()=>({status:'SUCCEEDED'}));ui.historyButtons()[0].dispatch('click');await flush();assert.equal(ui.posts()[1].body,'{}');
});

test('failed recalculate restores valid action state after refresh',async()=>{
 const ui=workbench();ui.summaries.A.status='DRAFT';await ui.choose('A');ui.setPost(async()=>{throw new Error('Unavailable');});
 ui.get('recalculate').dispatch('click');await flush();assert.equal(ui.get('event').disabled,false);assert.equal(ui.get('recalculate').disabled,false);assert.equal(ui.get('confirm').disabled,false);
});

test('failed refresh unlocks selection and form while money actions remain unavailable',async()=>{
 const ui=workbench();await ui.choose('A');ui.setPost(async()=>{ui.setGet(async()=>{throw new Error('Database unavailable');});return {status:'SUCCEEDED'};});
 ui.form.dispatch('submit');await flush();assert.equal(ui.get('event').disabled,false);assert.ok(ui.form.elements.every(input=>!input.disabled));
 for(const id of ['recalculate','confirm','payout-submit','reconcile'])assert.equal(ui.get(id).disabled,true,id+' must await authoritative data');
 assert.equal(ui.get('notice').textContent,'Database unavailable');
});

test('late event reads cannot replace the current event or its recovery action',async()=>{
 const ui=workbench();const old=deferred();ui.setGet(request=>request.url==='/api/admin/events/A/settlement'?old.promise:ui.read(request));
 ui.get('event').value='A';ui.get('event').dispatch('change');await ui.choose('B');old.resolve(ui.summaries.A);await flush();
 assert.equal(ui.get('event').value,'B');ui.form.dispatch('submit');await flush();assert.equal(ui.posts()[0].url,'/api/admin/settlements/SB/payouts');
});
