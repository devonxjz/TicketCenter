'use strict';
(() => {
    const base = document.body.dataset.context || '';
    const $ = id => document.getElementById(id);
    const node = (tag, text, css) => { const e = document.createElement(tag); e.textContent = text || ''; e.className = css || ''; return e; };
    const lock = (element, disabled) => { element.disabled=disabled; element.dataset.locked=String(disabled); };
    const money = value => BigInt(value || '0').toLocaleString('vi-VN') + ' đ';
    const labels = {DRAFT:'Bản nháp',PENDING_APPROVAL:'Chờ duyệt',REJECTED:'Bị từ chối',PUBLISHED:'Đã công khai',CANCELLED:'Đã hủy'};
    const date = value => value ? new Date(value).toLocaleString('vi-VN') : '';
    function localTime(value) { const d = new Date(value); return new Date(d.getTime() - d.getTimezoneOffset()*60000).toISOString().slice(0,23); }
    function message(text, bad = false) { const e = $('status'); e.textContent = text; e.className = bad ? 'alert alert-danger' : 'text-muted'; }
    async function api(path, options = {}) {
        const response = await fetch(base + path, {...options, credentials:'same-origin', headers:{Accept:'application/json',...(options.headers || {})}});
        let result; try { result = await response.json(); } catch (_) { throw new Error('Không thể đọc phản hồi máy chủ. Vui lòng thử lại.'); }
        if (!response.ok) { const error = new Error(result.error?.message || 'Không thể xử lý yêu cầu.'); error.status = response.status;error.code=result.error?.code; throw error; }
        return result;
    }
    async function token() { return (await api('/api/auth/csrf')).data.token; }
    async function intent(path, content) {
        const bytes = new TextEncoder().encode(path + '|' + content);
        const digest = await crypto.subtle.digest('SHA-256', bytes);
        const name = 'tc:dong:intent:' + Array.from(new Uint8Array(digest)).map(x=>x.toString(16).padStart(2,'0')).join('');
        let key; try { key = sessionStorage.getItem(name); } catch (_) { }
        if (!key) { key = crypto.randomUUID(); try { sessionStorage.setItem(name,key); } catch (_) { } }
        return {key,clear() { try { sessionStorage.removeItem(name); } catch (_) { } }};
    }
    async function mutate(path, payload = {}) {
        const body = JSON.stringify(payload), operation = await intent(path,body);
        try {
            const result = await api(path,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':await token(),'Idempotency-Key':operation.key},body});
            operation.clear();return result.data;
        } catch (e) { if ([400,403,404,415].includes(e.status) || e.code==='REQUEST_ABORTED') operation.clear();throw e; }
    }
    async function upload(path,file,version) {
        if (!file || file.size > 5242880) throw new Error('Chọn ảnh PNG/JPEG/WEBP tối đa 5 MB.');
        const digest = await crypto.subtle.digest('SHA-256',await file.arrayBuffer());
        const operation = await intent(path,String(version)+'|'+Array.from(new Uint8Array(digest)).map(x=>x.toString(16).padStart(2,'0')).join(''));
        const csrf = await token();
        return new Promise((resolve,reject) => {
            const xhr = new XMLHttpRequest(); xhr.open('POST',base+path);xhr.setRequestHeader('X-CSRF-Token',csrf);xhr.setRequestHeader('Idempotency-Key',operation.key);
            xhr.upload.onprogress = e => { if(e.lengthComputable) $('upload-progress').value=Math.round(e.loaded/e.total*100); };
            xhr.onerror = () => reject(new Error('Mất kết nối tải ảnh. Vui lòng thử lại.'));
            xhr.onload = () => { try { const result=JSON.parse(xhr.responseText);if(xhr.status>=200 && xhr.status<300) {operation.clear();resolve(result.data);} else {if([400,403,404,415].includes(xhr.status) || result.error?.code==='REQUEST_ABORTED')operation.clear();reject(new Error(result.error?.message || 'Không thể tải ảnh.'));} } catch (_) {reject(new Error('Phản hồi ảnh không hợp lệ.'));} };
            const data=new FormData();data.append('file',file);data.append('expectedVersion',String(version));xhr.send(data);
        });
    }
    async function action(button, work) { const before=button.disabled;button.disabled=true;try { await work(); } catch(e) { message(e.message,true); } finally { button.disabled=button.dataset.locked===undefined?before:button.dataset.locked==='true'; } }
    function button(text, work, css='btn btn-outline-dark btn-sm') { const b=node('button',text,css);b.type='button';b.addEventListener('click',()=>action(b,work));return b; }
    function image(url,target) {
        target.hidden=true;
        if(!url) return;
        try { const parsed=new URL(url,location.origin);if(['http:','https:'].includes(parsed.protocol) && !parsed.username) {target.src=parsed.href;target.hidden=false;} } catch (_) { }
    }
    async function organizations(select, preferred) {
        const items=(await api('/api/event-organizations')).data;select.replaceChildren();
        for(const item of items) {const option=node('option',item.name);option.value=item.organizationId;select.append(option);}
        if(preferred && items.some(item=>item.organizationId.toLowerCase()===preferred.toLowerCase())) select.value=preferred.toLowerCase();
        if(!items.length) throw new Error('Bạn chưa quản lý tổ chức được phê duyệt nào.');
        if(preferred && !items.some(item=>item.organizationId.toLowerCase()===preferred.toLowerCase())) throw new Error('Bạn không có quyền với tổ chức trong đường dẫn.');
        return select.value;
    }
    async function listPage() {
        let page=1,revision=0;await organizations($('organization'),new URLSearchParams(location.search).get('organizationId'));
        async function load() {
            const current=++revision;$('create-event').hidden=true;message('Đang tải sự kiện…');$('events').replaceChildren();$('previous').disabled=$('next').disabled=true;
            const org=$('organization').value,query=new URLSearchParams({page:String(page),pageSize:'20'});
            if($('filter-status').value) query.set('status',$('filter-status').value);
            const result=await api('/api/organizations/'+org+'/events?'+query);
            if(current!==revision)return;
            $('create-event').href=base+'/events/manage/editor?organizationId='+org;$('create-event').hidden=false;
            for(const event of result.data) {
                const tr=document.createElement('tr'),coverCell=node('td'),thumbnail=node('img');thumbnail.alt=event.title;thumbnail.className='organizer-event-thumbnail';image(event.coverImageUrl,thumbnail);coverCell.append(thumbnail);if(thumbnail.hidden)coverCell.append(node('span','Chưa có ảnh','small text-muted'));
                const title=node('td','','organizer-event-title');title.append(node('strong',event.title));
                const state=node('td');state.append(node('span',labels[event.status] || event.status,'status-pill '+({PUBLISHED:'status-success',PENDING_APPROVAL:'status-amber',REJECTED:'status-red',CANCELLED:'status-red'}[event.status] || 'status-subtle')));
                tr.append(coverCell,title,state,node('td',date(event.startTime)),node('td',String(event.totalCapacity)));
                const actionCell=node('td','','organizer-event-actions'),actions=node('div');const link=node('a','Xem / sửa','btn btn-outline-dark btn-sm');link.href=base+'/events/manage/editor?organizationId='+org+'&eventId='+event.eventId;actions.append(link);
                if(['DRAFT','REJECTED'].includes(event.status)) actions.append(button('Gửi duyệt',async()=>{await mutate('/api/events/'+event.eventId+'/submit',{expectedVersion:String(event.version)});await load();}));
                if(event.status==='DRAFT') actions.append(button('Xóa nháp',async()=>{if(!confirm('Xóa bản nháp này và các khu/ghế chưa có giao dịch?'))return;await mutate('/api/events/'+event.eventId+'/delete',{expectedVersion:String(event.version)});await load();},'btn btn-outline-danger btn-sm'));
                actionCell.append(actions);tr.append(actionCell);$('events').append(tr);
            }
            if(!result.data.length){const empty=node('td','Chưa có sự kiện phù hợp. Tạo sự kiện mới hoặc thay đổi bộ lọc.','organizer-empty');empty.colSpan=6;const row=node('tr');row.append(empty);$('events').append(row);}
            message(result.total ? 'Có '+result.total+' sự kiện.' : 'Chưa có sự kiện phù hợp.');$('page-label').textContent='Trang '+page;
            $('previous').disabled=page<=1;$('next').disabled=page*result.pageSize>=result.total;
        }
        $('filters').addEventListener('submit',e=>{e.preventDefault();page=1;load().catch(e=>message(e.message,true));});
        $('previous').addEventListener('click',()=>{page--;load().catch(e=>message(e.message,true));});$('next').addEventListener('click',()=>{page++;load().catch(e=>message(e.message,true));});
        await load();
    }
    function zones(items,target,event,refresh,editing) {
        target.replaceChildren();
        for(const zone of items) {
            const panel=node('section','',editing?'organizer-zone':'border rounded p-3 mb-3');panel.append(node('h3',zone.name+' · '+zone.zoneType,'h5'),node('p',money(zone.basePrice)+' · Còn '+zone.availableSeats+' / '+zone.totalCapacity+' vé','text-muted'));
            const seatRows=node('div','','d-grid gap-2');const rows=new Map();
            for(const seat of zone.seats || []) {if(!rows.has(seat.rowName)) {const row=node('div','','d-flex flex-wrap gap-2 align-items-center');row.append(node('strong','Hàng '+seat.rowName,'small me-2'));rows.set(seat.rowName,row);seatRows.append(row);} const state=seat.status==='AVAILABLE'?'Trống':seat.status==='HELD'?'Đang giữ':'Đã bán';rows.get(seat.rowName).append(node('span',seat.seatLabel+' · '+state,'badge p-2 '+(seat.status==='AVAILABLE'?'bg-success':seat.status==='HELD'?'bg-warning text-dark':'bg-secondary')));}
            if(editing && ['DRAFT','REJECTED','PUBLISHED'].includes(event.status)) {
                const price=node('input');price.type='text';price.inputMode='numeric';price.value=zone.basePrice;price.className='form-control';price.setAttribute('aria-label','Giá VND của '+zone.name);
                const controls=node('div','','d-flex flex-wrap gap-2 mb-3');controls.append(price);
                controls.append(button('Lưu giá',async()=>{await mutate('/api/zones/'+zone.zoneId+'/edit',{basePrice:price.value,expectedVersion:String(event.version)});await refresh();}));
                if(['DRAFT','REJECTED'].includes(event.status)) {
                    if(zone.zoneType==='STANDING') {
                        const capacity=node('input');capacity.type='number';capacity.min='1';capacity.max='10000000';capacity.value=String(zone.totalCapacity);capacity.className='form-control';capacity.setAttribute('aria-label','Sức chứa '+zone.name);controls.append(capacity);
                        controls.append(button('Lưu sức chứa',async()=>{await mutate('/api/zones/'+zone.zoneId+'/edit',{basePrice:price.value,capacity:capacity.value,expectedVersion:String(event.version)});await refresh();}));
                    } else {
                        const rowCount=node('input'),per=node('input');for(const input of [rowCount,per]){input.type='number';input.min='1';input.max='1000';input.className='form-control';}
                        rowCount.value=String(rows.size);per.value=String(Math.max(0,...(zone.seats || []).map(s=>s.seatNumber)));rowCount.setAttribute('aria-label','Số hàng '+zone.name);per.setAttribute('aria-label','Ghế mỗi hàng '+zone.name);controls.append(rowCount,per);
                        controls.append(button('Tạo lại ma trận',async()=>{if(!confirm('Tạo lại toàn bộ ghế chưa có giao dịch của khu này?'))return;await mutate('/api/zones/'+zone.zoneId+'/edit',{basePrice:price.value,rows:rowCount.value,seatsPerRow:per.value,expectedVersion:String(event.version)});await refresh();}));
                    }
                    controls.append(button('Xóa khu',async()=>{if(!confirm('Xóa khu vực này?'))return;await mutate('/api/zones/'+zone.zoneId+'/delete',{expectedVersion:String(event.version)});await refresh();},'btn btn-outline-danger btn-sm'));
                }
                panel.append(controls);
            }
            panel.append(seatRows);target.append(panel);
        }
        if(!items.length) target.append(node('p','Chưa có khu vực. Thêm khu ngồi hoặc khu đứng trước khi gửi duyệt.','text-muted'));
    }
    async function editorPage() {
        const params=new URLSearchParams(location.search);let eventId=params.get('eventId') || params.get('id'),event=null;
        await organizations($('organization'),params.get('organizationId'));
        for(const category of (await api('/api/event-categories')).data) {const option=node('option',category.name);option.value=category.categoryId;$('categoryId').append(option);}
        async function load() {
            if(!eventId) {message('Điền hồ sơ và lưu nháp để thêm ảnh, khu và ghế.');$('inventory').hidden=$('media').hidden=true;lock($('save-event'),false);return;}
            event=(await api('/api/organizations/'+$('organization').value+'/events/'+eventId)).data;
            $('organization').value=event.organizationId;$('organization').disabled=true;
            for(const name of ['title','description','categoryId','venueName','venueAddress']) $(name).value=event[name] || '';
            for(const name of ['saleStartTime','saleEndTime','startTime','endTime']) $(name).value=localTime(event[name]);
            const editable=['DRAFT','REJECTED'].includes(event.status),published=event.status==='PUBLISHED';
            $('draft-state').textContent=labels[event.status] || event.status;$('rejection').textContent=event.rejectionReason || '';
            for(const input of $('event-form').querySelectorAll('input,textarea,select')) if(input.id!=='organization') input.disabled=!(editable || (published && input.id==='description'));
            lock($('save-event'),!(editable || published));$('submit-event').hidden=!editable;$('delete-event').hidden=event.status!=='DRAFT';
            $('inventory').hidden=$('media').hidden=false;image(event.coverImageUrl,$('cover-preview'));
            for(const input of $('zone-form').querySelectorAll('input,select,button')) lock(input,!editable);
            lock($('upload-cover'),!(editable || published));$('cover-file').disabled=!(editable || published);
            zones((await api('/api/events/'+eventId+'/layout')).data,$('zones'),event,load,true);message('Hồ sơ và tồn kho đã được cập nhật.');
        }
        $('event-form').addEventListener('submit',e=>{e.preventDefault();action($('save-event'),async()=>{
            const payload={description:$('description').value};
            if(event?.status!=='PUBLISHED') {
                for(const name of ['title','categoryId','venueName','venueAddress']) payload[name]=$(name).value;
                for(const name of ['saleStartTime','saleEndTime','startTime','endTime']) {const value=$(name).value;if(!value)throw new Error('Cần đầy đủ lịch sự kiện.');payload[name]=new Date(value).toISOString();}
                if(!(payload.saleStartTime<payload.saleEndTime && payload.saleEndTime<=payload.startTime && payload.startTime<payload.endTime)) throw new Error('Lịch phải thỏa mở bán < kết thúc bán ≤ bắt đầu < kết thúc sự kiện.');
                if(!eventId && new Date(payload.saleStartTime)<=new Date()) throw new Error('Thời gian mở bán phải ở tương lai.');
            }
            if(event) payload.expectedVersion=String(event.version);
            let result;
            if(!eventId) {
                const path='/api/organizations/'+$('organization').value+'/events',operation=await intent(path,JSON.stringify(payload)),form=new FormData();
                for(const [name,value] of Object.entries(payload)) form.append(name==='saleStartTime'?'saleStart':name==='saleEndTime'?'saleEnd':name,value);
                try {result=(await api(path,{method:'POST',headers:{'X-CSRF-Token':await token(),'Idempotency-Key':operation.key},body:form})).data;operation.clear();}
                catch(e) {if([400,403,404,415].includes(e.status) || e.code==='REQUEST_ABORTED')operation.clear();throw e;}
                location.replace(base+'/events/manage/editor?organizationId='+result.organizationId+'&eventId='+(result.eventId || result.id));return;
            }
            result=await mutate('/api/events/'+eventId+'/edit',payload);await load();
        });});
        $('submit-event').addEventListener('click',()=>action($('submit-event'),async()=>{await mutate('/api/events/'+eventId+'/submit',{expectedVersion:String(event.version)});await load();}));
        $('delete-event').addEventListener('click',()=>action($('delete-event'),async()=>{if(!confirm('Xóa bản nháp này?'))return;await mutate('/api/events/'+eventId+'/delete',{expectedVersion:String(event.version)});location.assign(base+'/events/manage?organizationId='+event.organizationId);}));
        $('zoneType').addEventListener('change',()=>{$('seat-fields').hidden=$('zoneType').value!=='SEATED';$('standing-fields').hidden=$('zoneType').value!=='STANDING';});
        $('zone-form').addEventListener('submit',e=>{e.preventDefault();action($('add-zone'),async()=>{
            const payload={name:$('zone-name').value,zoneType:$('zoneType').value,basePrice:$('zone-price').value,expectedVersion:String(event.version)};
            if(payload.zoneType==='SEATED'){payload.rows=$('rows').value;payload.seatsPerRow=$('seatsPerRow').value;}else payload.capacity=$('capacity').value;
            await mutate('/api/events/'+eventId+'/zones',payload);await load();
        });});
        let preview;
        $('cover-file').addEventListener('change',()=>{if(preview)URL.revokeObjectURL(preview);const file=$('cover-file').files[0];if(file){preview=URL.createObjectURL(file);$('cover-preview').src=preview;$('cover-preview').hidden=false;}});
        $('upload-cover').addEventListener('click',()=>action($('upload-cover'),async()=>{$('upload-progress').value=0;await upload('/api/events/'+eventId+'/cover',$('cover-file').files[0],event.version);await load();message('Ảnh mới đã được lưu.');}));
        await load();
    }
    async function reviewPage() {
        let page=1,selected=null,reviewRevision=0,listRevision=0;
        async function review(id) {
            const current=++reviewRevision;selected=null;$('review').hidden=true;
            lock($('publish'),true);lock($('reject'),true);$('cancel-event').disabled=true;
            $('rule').replaceChildren();$('reason').value='';
            let detail,layout,policy;
            try { [detail,layout,policy]=await Promise.all([api('/api/admin/events/'+id),api('/api/admin/events/'+id+'/layout'),api('/api/admin/events/'+id+'/commission-rules')]); }
            catch(e) {if(current===reviewRevision)throw e;return;}
            if(current!==reviewRevision)return;
            selected=detail.data;$('review').hidden=false;$('review-title').textContent=selected.title;
            $('review-summary').textContent=selected.organizationName+' · '+labels[selected.status]+' · '+date(selected.startTime);
            $('review-schedule').textContent=selected.venueName+' · '+selected.venueAddress+'\nMở bán: '+date(selected.saleStartTime)+' – '+date(selected.saleEndTime)+'\nDiễn ra: '+date(selected.startTime)+' – '+date(selected.endTime)+'\nTổng sức chứa: '+selected.totalCapacity+' vé';
            $('review-description').textContent=selected.description;$('review-rejection').textContent=selected.rejectionReason || '';image(selected.coverImageUrl,$('review-cover'));
            zones(layout.data,$('review-zones'),selected,async()=>{},false);
            const rules=policy.data;
            for(const rule of rules){const option=node('option',rule.ratePercent+'% + '+money(rule.fixedFee)+' · '+date(rule.effectiveTo));option.value=rule.commissionRuleId;$('rule').append(option);}
            lock($('publish'),selected.status!=='PENDING_APPROVAL' || !rules.length);lock($('reject'),selected.status!=='PENDING_APPROVAL');
            $('rule-note').textContent=rules.length?'Chính sách có hiệu lực hiện tại và tại ngày sự kiện.':'Chưa có chính sách phù hợp. Cần thiết lập hoa hồng trước khi công khai.';
            $('cancel-event').disabled=selected.status!=='PUBLISHED';$('cancellation-status').textContent='Chọn xem tiến trình để kiểm tra trạng thái hủy.';
        }
        async function load(){const current=++listRevision;message('Đang tải hồ sơ…');$('previous').disabled=$('next').disabled=true;const query=new URLSearchParams({status:$('filter-status').value,page:String(page),pageSize:'20'});const result=await api('/api/admin/events?'+query);if(current!==listRevision)return;$('events').replaceChildren();
            for(const item of result.data){const tr=document.createElement('tr');tr.append(node('td',item.title),node('td',item.organizationName),node('td',labels[item.status]),node('td',date(item.startTime)));const td=node('td');td.append(button('Xem hồ sơ',()=>review(item.eventId)));tr.append(td);$('events').append(tr);}
            message(result.total?'Có '+result.total+' hồ sơ.':'Chưa có hồ sơ phù hợp.');$('page-label').textContent='Trang '+page;$('previous').disabled=page<=1;$('next').disabled=page*result.pageSize>=result.total;}
        $('filters').addEventListener('submit',e=>{e.preventDefault();page=1;load().catch(e=>message(e.message,true));});$('previous').addEventListener('click',()=>{page--;load().catch(e=>message(e.message,true));});$('next').addEventListener('click',()=>{page++;load().catch(e=>message(e.message,true));});
        $('publish').addEventListener('click',()=>action($('publish'),async()=>{const chosen=selected;if(!chosen)return;await mutate('/api/admin/events/'+chosen.eventId+'/publish',{commissionRuleId:$('rule').value,expectedVersion:String(chosen.version)});if(selected?.eventId===chosen.eventId)await review(chosen.eventId);await load();}));
        $('reject').addEventListener('click',()=>action($('reject'),async()=>{const chosen=selected;if(!chosen)return;await mutate('/api/admin/events/'+chosen.eventId+'/reject',{reason:$('reason').value,expectedVersion:String(chosen.version)});if(selected?.eventId===chosen.eventId)await review(chosen.eventId);await load();}));
        $('cancel-event').addEventListener('click',()=>action($('cancel-event'),async()=>{if(!confirm('Gửi yêu cầu hủy sự kiện này?'))return;await mutate('/api/admin/events/'+selected.eventId+'/cancel',{});$('cancellation-status').textContent='Đã gửi yêu cầu hủy.';}));
        $('cancellation-progress').addEventListener('click',()=>action($('cancellation-progress'),async()=>{if(!selected)throw new Error('Chọn hồ sơ trước.');const result=await api('/api/admin/events/'+selected.eventId+'/cancellation-progress');$('cancellation-status').textContent=JSON.stringify(result.data);}));
        await load();const id=new URLSearchParams(location.search).get('eventId');if(id)await review(id);
    }
    const page=document.body.dataset.dongPage;
    ({list:listPage,editor:editorPage,review:reviewPage}[page] || (()=>{}))().catch(e=>message(e.message,true));
})();
