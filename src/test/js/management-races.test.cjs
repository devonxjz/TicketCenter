'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
function fixture(kind, csrf = true){
 const nodes=new Map(),calls=[],events={},modals=new Map();
 const get=id=>{if(!nodes.has(id))nodes.set(id,{value:id.includes('Filter')?'ALL':'',innerHTML:'',textContent:'',style:{},dataset:{},listeners:{},classList:{add(){},remove(){}},addEventListener(name,fn){this.listeners[name]=fn;},querySelector(){return get(id+'option');},querySelectorAll(){return[];},appendChild(row){this.innerHTML+=row.innerHTML;},reset(){}});return nodes.get(id);};
 const tabs=['PENDING_APPROVAL','APPROVED','REJECTED','ALL'].map(status=>{const tab=get('tab-'+status);tab.getAttribute=()=>status;return tab;});
 class Modal{constructor(el){this.el=el;modals.set(el, this);}show(){this.el.visible=true;}hide(){this.el.visible=false;}static getOrCreateInstance(el){return modals.get(el)||new Modal(el);}}
 const document={readyState:'loading',getElementById:get,querySelector:s=>s.includes('meta')?{getAttribute:()=>s.includes('context-path')?'/app':csrf?'csrf':''}:null,querySelectorAll:s=>s==='#orgStatusTabs button'?tabs:[],createElement:()=>({innerHTML:''}),addEventListener(){}};
 const window={tcCurrentOrgId:'org-a',crypto:{randomUUID:()=> 'test-key'},addEventListener(n,fn){events[n]=fn;}};
 const context={document,window,crypto:window.crypto,localStorage:{getItem(){return null;}},bootstrap:{Modal},Date,setTimeout(){},console:{error(){},warn(){}},fetch:(path,options)=>new Promise((resolve,reject)=>calls.push({path,options,resolve,reject}))};
 vm.createContext(context);
 let source=fs.readFileSync('src/main/webapp/assets/js/'+kind+'.js','utf8');
 // Expose existing lexical functions only in the test VM; ship no test hooks.
 if(kind==='organization-members')source=source.replace(/\}\)\(\);\s*$/, 'window.testActions={openChangeRoleModal,openDeactivateModal,handleActivateMember};})();');
 vm.runInContext(source,context);
 return{context,get,calls,events,window,reply(index,payload,ok=true){calls[index].resolve({ok,statusText:'Unavailable',status:ok?200:500,json:async()=>payload});},submit(id){return get(id).listeners.submit({preventDefault(){}});},switchOrg(id){window.tcCurrentOrgId=id;events['tc:org-changed']({detail:{organizationId:id}});}};
}
const member=name=>({userId:name,userName:name,fullName:name,email:name+'@example.test',role:'CHECK_IN_STAFF',isActive:true});
const tick=()=>new Promise(resolve=>setImmediate(resolve));
test('member responses and errors from an older organization cannot replace the active table',async()=>{
 for(const fail of [false,true]){
  const f=fixture('organization-members'),first=f.window.TcMembersList.load();f.switchOrg('org-b');
  f.reply(1,{data:[member('B')]});await tick();
  if(fail)f.calls[0].reject(new Error('old failure'));else f.reply(0,{data:[member('A')]});
  await first;assert.match(f.get('memberTableBody').innerHTML,/>B/);assert.doesNotMatch(f.get('memberTableBody').innerHTML,/old failure|>A/);
 }
});
test('organization switch closes member modals and blocks submits from their previous scope',async()=>{
 for(const action of ['add','role','deactivate']){
  const f=fixture('organization-members');
  if(action==='add')f.get('btnOpenAddMember').listeners.click();
  if(action==='role')f.window.testActions.openChangeRoleModal(member('A'));
  if(action==='deactivate')f.window.testActions.openDeactivateModal(member('A'));
  const modal={add:'addMemberModal',role:'changeRoleModal',deactivate:'deactivateModal'}[action];
  f.switchOrg('org-b');assert.equal(f.get(modal).visible,false);
  f.get('addUserName').value='new-user';await f.submit({add:'addMemberForm',role:'changeRoleForm',deactivate:'deactivateForm'}[action]);await tick();
  assert.equal(f.calls.filter(c=>c.options.method==='POST').length,0);
 }
});
test('late member mutation errors cannot populate a new organization modal',async()=>{
 const f=fixture('organization-members');f.window.testActions.openDeactivateModal(member('A'));
 const pending=f.submit('deactivateForm');await tick();assert.match(f.calls[0].path,/organizations\/org-a\/members\/A\/deactivate/);
 f.switchOrg('org-b');f.window.testActions.openDeactivateModal(member('B'));
 f.calls[0].reject(new Error('old mutation failure'));await pending;
 assert.equal(f.get('deactivateModal').visible,true);assert.equal(f.get('deactivateTargetName').textContent,'B');assert.equal(f.get('deactivateAlert').textContent,'');
});
test('admin list keeps the latest response and discards late errors',async()=>{
 for(const fail of [false,true]){
  const f=fixture('admin-organizations'),a=f.context.loadRequests(),b=f.context.loadRequests();
  f.reply(1,{data:{items:[{organizationId:'b',name:'Latest',status:'APPROVED'}],total:7}});await b;
  if(fail)f.calls[0].reject(new Error('old failure'));else f.reply(0,{data:{items:[{organizationId:'a',name:'Old',status:'PENDING_APPROVAL'}],total:2}});
  await a;assert.match(f.get('orgTableBody').innerHTML,/Latest/);assert.match(f.get('paginationInfo').textContent,/7/);assert.doesNotMatch(f.get('orgTableBody').innerHTML,/old failure|Old/);
 }
});
test('late approval detail and mutation cannot overwrite another approval modal',async()=>{
 const f=fixture('admin-organizations');f.context.openApproveModal('a','A');
 const pending=f.context.submitApprove({preventDefault(){}});f.context.openApproveModal('b','B');
 f.reply(2,{data:{eventTitle:'Event B'}});await tick();f.reply(0,{data:{eventTitle:'Event A'}});await tick();
 assert.equal(f.get('approveEventTitle').textContent,'Event B');
 f.reply(1,{data:{success:true}});await pending;
 assert.equal(f.get('approveModal').visible,true);assert.equal(f.get('approveOrgId').value,'b');assert.equal(f.get('alertPlaceholder').innerHTML,'');
});

test('a scope change while obtaining CSRF prevents the pending mutation from being sent',async()=>{
 const f=fixture('organization-members',false);f.get('btnOpenAddMember').listeners.click();f.get('addUserName').value='user';
 const pending=f.submit('addMemberForm');assert.equal(f.calls[0].path,'/app/api/auth/csrf');
 f.switchOrg('org-b');f.reply(0,{data:{token:'new-csrf'}});await pending;
 assert.equal(f.calls.filter(c=>c.options.method==='POST').length,0);
});
test('late successful member mutations cannot close the new modal or refresh its organization',async()=>{
 const f=fixture('organization-members');f.window.testActions.openDeactivateModal(member('A'));
 const pending=f.submit('deactivateForm');await tick();f.switchOrg('org-b');f.window.testActions.openDeactivateModal(member('B'));
 f.reply(0,{message:'Success'});await pending;
 assert.equal(f.get('deactivateModal').visible,true);assert.equal(f.get('membersAlertPlaceholder').innerHTML,'');
 assert.equal(f.calls.length,2);assert.match(f.calls[1].path,/organizations\/org-b\/members$/);
});
test('sole active manager cannot be demoted or deactivated even through a submitted modal',async()=>{
 const f=fixture('organization-members'),manager={...member('manager'),role:'MANAGER'};
 const loaded=f.window.TcMembersList.load();f.reply(0,{data:[manager]});await loaded;
 f.window.testActions.openChangeRoleModal(manager);f.get('changeRoleSelect').value='CHECK_IN_STAFF';await f.submit('changeRoleForm');
 assert.match(f.get('changeRoleAlert').textContent,/duy nhất/);
 f.window.testActions.openDeactivateModal(manager);await f.submit('deactivateForm');
 assert.equal(f.calls.filter(c=>c.options.method==='POST').length,0);
});
test('duplicate submits are ignored and member mutations retain their CSRF header and payload',async()=>{
 const f=fixture('organization-members');f.window.testActions.openDeactivateModal(member('A'));
 const pending=f.submit('deactivateForm');await f.submit('deactivateForm');await tick();
 assert.equal(f.calls.length,1);assert.equal(f.calls[0].options.headers['X-CSRF-Token'],'csrf');assert.equal(JSON.parse(f.calls[0].options.body)._csrf,'csrf');
 f.calls[0].reject(new Error('failure'));await pending;assert.equal(f.get('btnDeactivateSubmit').disabled,false);
});
test('closed admin dialogs invalidate submissions and late detail responses',async()=>{
 const f=fixture('admin-organizations');f.context.openApproveModal('a','A');
 f.get('approveModal').listeners['hide.bs.modal']();f.context.openApproveModal('b','B');
 f.reply(1,{data:{eventTitle:'B'}});await tick();f.reply(0,{data:{eventTitle:'A'}});await tick();assert.equal(f.get('approveEventTitle').textContent,'B');
 f.get('approveModal').listeners['hide.bs.modal']();await f.context.submitApprove({preventDefault(){}});assert.equal(f.calls.length,2);
});

test('switching an admin tab invalidates its decision dialog and late list response',async()=>{
 const f=fixture('admin-organizations'),old=f.context.loadRequests();f.context.openRejectModal('a','A');
 f.get('tab-APPROVED').listeners.click();assert.equal(f.get('rejectModal').visible,false);
 await f.context.submitReject({preventDefault(){}});assert.equal(f.calls.length,2);
 assert.match(f.calls[1].path,/status=APPROVED$/);
 f.reply(1,{data:{items:[{organizationId:'b',name:'Approved B',status:'APPROVED'}],total:1}});await tick();
 f.reply(0,{data:{items:[{organizationId:'a',name:'Pending A',status:'PENDING_APPROVAL'}],total:4}});await old;
 assert.match(f.get('orgTableBody').innerHTML,/Approved B/);assert.match(f.get('tableHeaderTitle').textContent,/đã phê duyệt/);assert.doesNotMatch(f.get('orgTableBody').innerHTML,/Pending A/);
});
test('admin numbered pagination preserves status and invalidates the previous page modal',async()=>{
 const f=fixture('admin-organizations');let pager;
 f.window.TicketUI={renderPagination(host,options){assert.equal(host,f.get('paginationList'));pager=options;}};
 const loaded=f.context.loadRequests();assert.equal(pager.disabled,true);
 f.reply(0,{data:{items:[],total:17}});await loaded;
 assert.equal(pager.pageSize,6);assert.equal(pager.total,17);assert.equal(pager.disabled,false);
 f.context.openRejectModal('a','A');pager.onPageChange(2);
 assert.equal(f.get('rejectModal').visible,false);assert.equal(f.calls[1].path,'/app/api/admin/organization-requests?page=2&pageSize=6&status=PENDING_APPROVAL');
});

test('admin queue reload moves to the last existing page after the total shrinks',async()=>{
 const f=fixture('admin-organizations');const first=f.context.loadRequests();f.reply(0,{data:{items:[],total:7}});await first;
 f.context.changePage(1);f.reply(1,{data:{items:[],total:6}});await tick();
 assert.equal(f.calls[2].path,'/app/api/admin/organization-requests?page=1&pageSize=6&status=PENDING_APPROVAL');
 f.reply(2,{data:{items:[{organizationId:'a',name:'Remaining',status:'PENDING_APPROVAL'}],total:6}});await tick();
 assert.match(f.get('paginationInfo').textContent,/Trang 1 \/ 1/);assert.match(f.get('orgTableBody').innerHTML,/Remaining/);
});
