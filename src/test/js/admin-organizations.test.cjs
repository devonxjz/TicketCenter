'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
function fixture(payload,ok=true){
 const nodes=new Map(),calls=[];
 const get=id=>{if(!nodes.has(id))nodes.set(id,{value:'',innerHTML:'',classList:{add(){},remove(){}},addEventListener(){}});return nodes.get(id);};
 const document={getElementById:get,querySelector:selector=>({getAttribute:()=>selector.includes('context-path')?'/app':'csrf'}),querySelectorAll:()=>[],addEventListener(){}};
 const crypto={randomUUID:()=> '11111111-1111-1111-1111-111111111111'};
 const context={document,window:{crypto},crypto,bootstrap:{Modal:class{show(){}hide(){}}},Date,setTimeout(){},console:{error(){}},fetch:async(path,options)=>{calls.push({path,options});return{ok,statusText:'Unavailable',json:async()=>payload};}};
 vm.createContext(context);vm.runInContext(fs.readFileSync('src/main/webapp/assets/js/admin-organizations.js','utf8'),context);
 return{context,get,calls};
}
test('organization list renders the live API envelope and pagination count',async()=>{
 const f=fixture({data:{items:[{organizationId:'11111111-1111-1111-1111-111111111111',name:'Real organizer',status:'PENDING_APPROVAL',submittedAt:'2026-10-13T00:00:00Z'}],total:17}});
 await f.context.loadRequests();
 assert.equal(f.calls[0].path,'/app/api/admin/organization-requests?page=1&pageSize=6&status=PENDING_APPROVAL');
 assert.match(f.get('orgTableBody').innerHTML,/Real organizer/);assert.match(f.get('paginationInfo').textContent,/17/);
});
test('unavailable or malformed responses display errors instead of silently showing an empty list',async()=>{
 for(const f of [fixture({},false),fixture({data:{items:[],total:'invalid'}})]){
  await f.context.loadRequests();assert.match(f.get('orgTableBody').innerHTML,/Có lỗi xảy ra/);
  assert.doesNotMatch(f.get('orgTableBody').innerHTML,/Không có hồ sơ nào/);
 }
});
test('untrusted organizer names and alert messages cannot become executable markup',()=>{
 const f=fixture({});f.context.renderTable([{organizationId:'11111111-1111-1111-1111-111111111111',name:'O\'Brien <img src=x onerror=alert(1)>',status:'PENDING_APPROVAL'}]);
 const html=f.get('orgTableBody').innerHTML;assert.match(html,/&lt;img/);assert.doesNotMatch(html,/onclick="openApproveModal/);
 f.context.showAlert('danger','<img src=x onerror=alert(1)>');assert.doesNotMatch(f.get('alertPlaceholder').innerHTML,/<img/);
});

test('wrapped approval and rejection success refresh the live queue only after server confirmation',async()=>{
 for(const action of ['Approve','Reject']){
  const f=fixture({data:{success:true}});
  f.get('approveOrgId').value=f.get('rejectOrgId').value='11111111-1111-1111-1111-111111111111';
  f.get('approveRatePercent').value='5.0';f.get('approveFixedFee').value='10000';
  f.get('rejectReason').value='Hồ sơ chưa cung cấp đủ thông tin';
  await f.context['submit'+action]({preventDefault(){}});
  assert.equal(f.calls[0].options.method,'POST');assert.equal(f.calls[0].options.headers['X-CSRF-Token'],'csrf');
  assert.equal(f.calls[1].options.method,'GET');assert.match(f.get('alertPlaceholder').innerHTML,/alert-success/);
 }
 const f=fixture({error:{message:'Conflict'}},false);f.get('rejectReason').value='Hồ sơ chưa cung cấp đủ thông tin';
 await f.context.submitReject({preventDefault(){}});assert.equal(f.calls.length,1);assert.match(f.get('alertPlaceholder').innerHTML,/alert-danger/);
});
