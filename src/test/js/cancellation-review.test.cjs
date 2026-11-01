'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const ID='00000000-0000-0000-0000-000000000001';
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function fixture(admin){
 const nodes=new Map(),calls=[],saved=new Map();
 const get=id=>{if(!nodes.has(id))nodes.set(id,{value:'',hidden:false,disabled:false,textContent:'',children:[],listeners:{},append(item){this.children.push(item);},replaceChildren(){this.children=[];},addEventListener(n,fn){this.listeners[n]=fn;},querySelector(){return get(id+'-button');}});return nodes.get(id);};
 const context={document:{body:{dataset:{context:'/app',cancellationAdmin:String(admin)}},hidden:false,getElementById:get,createElement:()=>({value:'',textContent:''}),addEventListener(){}},location:{search:''},URLSearchParams,crypto:{randomUUID:()=> 'stable-key'},sessionStorage:{getItem:k=>saved.get(k)||null,setItem:(k,v)=>saved.set(k,v),removeItem:k=>saved.delete(k)},window:{confirm:()=>true,addEventListener(){}},setTimeout:()=>1,clearTimeout(){},fetch:(path,options)=>new Promise((resolve,reject)=>calls.push({path,options,resolve,reject}))};
 vm.createContext(context);vm.runInContext(fs.readFileSync('src/main/webapp/assets/js/event/cancellation-review.js','utf8'),context);
 return {get,calls,reply(i,data,status=200){calls[i].resolve({ok:status===200,status,json:async()=>data});},select(id){get('cancellation-event-id').value=id;return get('cancellation-select').listeners.submit({preventDefault(){}});}};
}
test('manager picker uses names and the existing top-level list pagination contract',async()=>{
 const f=fixture(false);f.reply(0,{data:[{organizationId:'org',name:'Organizer'}]});await tick();
 assert.match(f.calls[1].path,/pageSize=6&status=PUBLISHED/);
 f.reply(1,{data:[{eventId:ID,title:'Actual event'}],total:21,page:1,pageSize:6});await tick();
 assert.equal(f.get('cancellation-event-id').children[1].textContent,'Actual event');assert.equal(f.get('cancellation-picker-next').disabled,false);
});
test('ambiguous approval retries the same durable key and never claims refund success',async()=>{
 const f=fixture(true);f.reply(0,{data:{items:[{eventId:ID,title:'Actual event',requestStatus:'PENDING'}],total:1}});await tick();
 f.select(ID);f.reply(1,{data:{eventId:ID,title:'Actual event',eventStatus:'PUBLISHED',requestStatus:'PENDING',reason:'Dangerous weather conditions',progress:null}});await tick();
 const first=f.get('cancellation-approve').listeners.click();f.reply(2,{data:{token:'csrf'}});await tick();
 assert.equal(f.calls[3].options.headers['Idempotency-Key'],'stable-key');assert.equal(f.calls[3].options.body,'{}');
 f.calls[3].reject(new Error('Network timeout'));await first;assert.equal(f.get('cancellation-retry').hidden,false);
 assert.equal(f.get('cancellation-progress-panel').hidden,true);assert.match(f.get('cancellation-status').textContent,/Chưa xác định/);
 const retry=f.get('cancellation-retry').listeners.click();f.reply(4,{data:{token:'csrf'}});await tick();assert.equal(f.calls[5].options.headers['Idempotency-Key'],'stable-key');
 f.reply(5,{data:{requestStatus:'APPROVED'}});await tick();f.reply(6,{data:{eventId:ID,title:'Actual event',eventStatus:'CANCELLED',requestStatus:'APPROVED',progress:{status:'COMPLETED',totalOrders:1,processedOrders:1,successfulRefunds:0,pendingRefunds:1,failedRefunds:0,unknownRefunds:0,usedTicketExceptions:0}}});await retry;
 assert.match(f.get('cancellation-refund-counts').textContent,/Đã hoàn: 0/);assert.match(f.get('cancellation-refund-counts').textContent,/đang xử lý: 1/);
});

test('confirmed cancellation preserves a failed refresh without claiming visible database status',async()=>{
 const f=fixture(true);f.reply(0,{data:{items:[{eventId:ID,title:'Actual event',requestStatus:'PENDING'}],total:1}});await tick();
 f.select(ID);f.reply(1,{data:{eventId:ID,title:'Actual event',eventStatus:'PUBLISHED',requestStatus:'PENDING',progress:null}});await tick();
 const done=f.get('cancellation-approve').listeners.click();assert.equal(f.get('cancellation-organization').disabled,true);
 f.reply(2,{data:{token:'csrf'}});await tick();f.reply(3,{data:{requestStatus:'APPROVED'}});await tick();
 f.calls[4].reject(new Error('Refresh unavailable'));await done;
 assert.match(f.get('cancellation-status').textContent,/Đã ghi nhận yêu cầu, chưa tải được trạng thái mới/);
 assert.match(f.get('cancellation-status').textContent,/Refresh unavailable/);
 assert.equal(f.get('cancellation-detail').hidden,true);assert.equal(f.get('cancellation-retry').hidden,true);
});
