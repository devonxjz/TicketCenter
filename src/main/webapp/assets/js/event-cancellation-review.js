'use strict';
(() => {
    const $ = id => document.getElementById(id);
    if (!$('cancellation-select')) return;
    const base=document.body.dataset.context||'',admin=document.body.dataset.cancellationAdmin==='true';
    const prefix=admin?'/api/admin/events/':'/api/events/';
    let current=null,busy=false,pending=null,revision=0,timer=null,pickerPage=1,pickerRevision=0,pickerTotal=0;
    const initial=new URLSearchParams(location.search).get('id');
    const storage='tc:event-cancellation-review:pending';
    try{const value=JSON.parse(sessionStorage.getItem(storage));if(value&&typeof value.id==='string'&&typeof value.key==='string'&&['request','approve','reject'].includes(value.action)&&typeof value.body==='object')pending=value;}catch(_){}
    const message=text=>{$('cancellation-status').textContent=text;};
    const save=()=>{try{if(pending)sessionStorage.setItem(storage,JSON.stringify(pending));else sessionStorage.removeItem(storage);}catch(_){}};
    function stop(){if(timer)clearTimeout(timer);timer=null;}
    async function api(path,options={},full=false) {
        const response=await fetch(base+path,{...options,credentials:'same-origin',headers:{Accept:'application/json',...options.headers}});
        let result;try{result=await response.json();}catch(_){throw new Error('Không đọc được kết quả. Kiểm tra lại lệnh đã gửi.');}
        if(!response.ok){const error=new Error(result.error?.message||'Không thể xử lý yêu cầu hủy.');error.status=response.status;throw error;}
        return full?result:result.data;
    }
    async function picker(){
        const rev=++pickerRevision;$('cancellation-picker-previous').disabled=$('cancellation-picker-next').disabled=true;
        try{const path=admin?'/api/admin/event-cancellations':`/api/organizations/${$('cancellation-organization').value}/events`;
            const result=await api(path+`?page=${pickerPage}&pageSize=6`+(admin?'':'&status=PUBLISHED'),{},!admin);if(rev!==pickerRevision)return;
            const items=admin?(result.items||[]):(result.data||[]);pickerTotal=Number(result.total)||0;$('cancellation-event-id').replaceChildren();
            const placeholder=document.createElement('option');placeholder.value='';placeholder.textContent=admin?'Chọn yêu cầu hủy':'Chọn sự kiện';$('cancellation-event-id').append(placeholder);
            for(const item of items){const option=document.createElement('option');option.value=item.eventId;option.textContent=`${item.title}${item.requestStatus?' · '+({PENDING:'Chờ duyệt',APPROVED:'Đã duyệt',REJECTED:'Từ chối'}[item.requestStatus]||item.requestStatus):''}`;$('cancellation-event-id').append(option);}
            if(current){const match=items.some(item=>item.eventId===current.eventId);if(!match){const option=document.createElement('option');option.value=current.eventId;option.textContent=current.title;$('cancellation-event-id').append(option);}$('cancellation-event-id').value=current.eventId;}
            $('cancellation-picker-page').textContent=pickerTotal?`Trang ${pickerPage} / ${Math.ceil(pickerTotal/6)}`:'Không có sự kiện';
            $('cancellation-picker-previous').disabled=pickerPage<=1;$('cancellation-picker-next').disabled=pickerPage*6>=pickerTotal;
            window.TicketUI?.renderPagination($('cancellation-picker-page'),{page:pickerPage,total:pickerTotal,pageSize:6,previous:$('cancellation-picker-previous'),next:$('cancellation-picker-next'),disabled:busy,onPageChange:page=>{pickerPage=page;picker();}});
            if(!items.length&&!current)message(admin?'Chưa có yêu cầu hủy nào.':'Tổ chức chưa có sự kiện đã công khai.');
        }catch(error){if(rev===pickerRevision)message(error.message);}
    }
    async function initialize(){
        try{if(!admin){const organizations=await api('/api/event-organizations');$('cancellation-organization-field').hidden=false;
            for(const org of organizations){const option=document.createElement('option');option.value=org.organizationId;option.textContent=org.name;$('cancellation-organization').append(option);}
            if(!organizations.length){message('Bạn chưa quản lý tổ chức được phê duyệt nào.');return;}}
            const id=initial||pending?.id;if(id)await load(id);await picker();
        }catch(error){message(error.message);}
    }
    function controls(){
        const waiting=current?.requestStatus==='PENDING',retry=!!pending&&!!current&&pending.id===current.eventId;
        $('cancellation-request').hidden=!current||current.eventStatus!=='PUBLISHED'||!['NONE','REJECTED'].includes(current.requestStatus);
        $('cancellation-approve').hidden=!admin||!waiting;$('cancellation-reject').hidden=!admin||!waiting;
        $('cancellation-retry').hidden=!retry;
        for(const id of ['cancellation-request','cancellation-approve','cancellation-reject'])$(id).disabled=busy||!!pending;
        $('cancellation-retry').disabled=busy;$('cancellation-reason').readOnly=busy||!!pending;
        $('cancellation-event-id').disabled=busy;
        $('cancellation-organization').disabled=busy;
        $('cancellation-picker-previous').disabled=busy||pickerPage<=1;
        $('cancellation-picker-next').disabled=busy||pickerPage*6>=pickerTotal;
        window.TicketUI?.renderPagination($('cancellation-picker-page'),{page:pickerPage,total:pickerTotal,pageSize:6,previous:$('cancellation-picker-previous'),next:$('cancellation-picker-next'),disabled:busy,onPageChange:page=>{pickerPage=page;picker();}});
        $('cancellation-select').querySelector('button').disabled=busy;
    }
    function render(data){
        current=data;$('cancellation-detail').hidden=false;$('cancellation-title').textContent=data.title;
        const states={NONE:'Chưa có yêu cầu hủy',PENDING:'Đang chờ duyệt',APPROVED:'Đã duyệt hủy',REJECTED:'Yêu cầu bị từ chối'};
        $('cancellation-state').textContent=`${states[data.requestStatus]||data.requestStatus} · ${({PUBLISHED:'Đã công khai',CANCELLED:'Đã hủy',DRAFT:'Bản nháp',PENDING_APPROVAL:'Chờ duyệt',REJECTED:'Bị từ chối'}[data.eventStatus]||'Đang đối chiếu')}`;
        $('cancellation-request-reason').hidden=!data.reason;$('cancellation-request-reason').textContent=data.reason||'';
        $('cancellation-rejection').hidden=!data.rejectionReason;$('cancellation-rejection').textContent=`Lý do từ chối: ${data.rejectionReason||''}`;
        $('cancellation-progress-panel').hidden=!data.progress;
        if(data.progress){const p=data.progress,total=Number(p.totalOrders),processed=Number(p.processedOrders);
            $('cancellation-progress-bar').value=total===0?100:processed/total*100;
            $('cancellation-progress-counts').textContent=`${processed}/${total} đơn đã tạo hoặc tiếp nhận nghĩa vụ hoàn vé.`;
            $('cancellation-refund-counts').textContent=`Đã hoàn: ${p.successfulRefunds}; đang xử lý: ${p.pendingRefunds}; thất bại: ${p.failedRefunds}; cần đối soát: ${p.unknownRefunds}; vé đã check-in cần xem xét: ${p.usedTicketExceptions}.`;
        }
        controls();
    }
    async function load(id,quiet=false){
        stop();const rev=++revision;if(!quiet)message('Đang tải trạng thái từ hệ thống…');
        try{const data=await api(prefix+encodeURIComponent(id)+'/cancellation-request');if(rev!==revision)return false;render(data);
            if(!quiet)message(pending?'Có một lệnh chưa rõ kết quả. Chọn đúng sự kiện và kiểm tra lại lệnh đã gửi.':'Đã tải trạng thái hiện tại.');
            if(data.progress&&(data.progress.status!=='COMPLETED'||Number(data.progress.pendingRefunds)>0)&&!document.hidden)timer=setTimeout(()=>load(id,true),5000);return true;
        }catch(error){if(rev!==revision)return false;message(error.message);current=null;$('cancellation-detail').hidden=true;controls();return false;}
    }
    async function submit(action){
        if(busy||!current)return;
        if(!pending){let body={};if(action!=='approve'){const reason=$('cancellation-reason').value.trim();if(reason.length<10||reason.length>500){message('Nhập lý do từ 10 đến 500 ký tự.');return;}body={reason};}
            if(action==='approve'&&!window.confirm('Duyệt yêu cầu sẽ hủy sự kiện, dừng bán vé và chuẩn bị nghĩa vụ hoàn vé. Tiếp tục?'))return;
            pending={id:current.eventId,key:crypto.randomUUID(),action,body};save();
        }
        if(pending.id!==current.eventId){message('Chọn lại sự kiện có lệnh chưa rõ kết quả.');return;}
        busy=true;pickerRevision++;stop();controls();message('Đang gửi yêu cầu…');
        const id=pending.id;
        try{const csrf=await api('/api/auth/csrf');const endpoint=pending.action==='request'?'cancellation-request':`cancellation-${pending.action}`;
            await api(prefix+id+'/'+endpoint,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':csrf.token,'Idempotency-Key':pending.key},body:JSON.stringify(pending.body)});
            pending=null;save();$('cancellation-reason').value='';const refreshed=await load(id);message(refreshed?'Đã ghi nhận yêu cầu. Trạng thái bên dưới được đọc từ hệ thống.':'Đã ghi nhận yêu cầu, chưa tải được trạng thái mới. '+$('cancellation-status').textContent);
        }catch(error){if([400,401,403,404,409,415].includes(error.status)){pending=null;save();message(error.message);}else message('Chưa xác định được kết quả. Kiểm tra lại lệnh đã gửi với cùng mã yêu cầu.');}
        finally{busy=false;controls();}
    }
    $('cancellation-select').addEventListener('submit',e=>{e.preventDefault();if(busy)return;const id=$('cancellation-event-id').value.trim();if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)){message('Vui lòng chọn sự kiện trong danh sách.');return;}load(id);});
    $('cancellation-organization').addEventListener('change',()=>{if(busy)return;pickerPage=1;revision++;stop();current=null;$('cancellation-detail').hidden=true;controls();picker();});
    $('cancellation-picker-previous').addEventListener('click',()=>{if(!busy&&pickerPage>1){pickerPage--;picker();}});
    $('cancellation-picker-next').addEventListener('click',()=>{if(!busy&&pickerPage*6<pickerTotal){pickerPage++;picker();}});
    for(const action of ['request','approve','reject'])$('cancellation-'+action).addEventListener('click',()=>submit(action));
    $('cancellation-retry').addEventListener('click',()=>submit(pending?.action));
    $('cancellation-action-form').addEventListener('submit',e=>e.preventDefault());
    document.addEventListener('visibilitychange',()=>{stop();if(!document.hidden&&current)load(current.eventId,true);});window.addEventListener('pagehide',stop);
    initialize();controls();
})();
