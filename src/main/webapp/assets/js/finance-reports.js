/* Finance read-side pages: safe text rendering and one filter contract for JSON/CSV. */
(function () {
  'use strict';
  const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  function utc(value) {
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(value || ''))
      throw new Error('Nhập đầy đủ thời gian UTC.');
    const date = new Date(value + 'Z');
    if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 16) !== value.slice(0, 16))
      throw new Error('Thời gian UTC không hợp lệ.');
    return date.toISOString();
  }
  function query(values, pageType, page) {
    const from = utc(values.from), to = utc(values.to);
    if (from >= to) throw new Error('Thời gian kết thúc phải sau thời gian bắt đầu.');
    const size = values.pageSize || '20';
    if (!/^[0-9]+$/.test(size) || Number(size) < 1 || Number(size) > 100 || !Number.isInteger(page) || page < 1)
      throw new Error('Phân trang không hợp lệ.');
    const result = new URLSearchParams({ from, to, page: String(page), pageSize: size });
    for (const name of pageType === 'audit' ? ['organizationId', 'eventId', 'actorId'] : ['organizationId', 'eventId']) {
      if (values[name]) {
        if (!uuid.test(values[name])) throw new Error('Tổ chức hoặc sự kiện không hợp lệ.');
        result.set(name, values[name]);
      }
    }
    if (pageType === 'reports') {
      const metric = values.metric || 'COHORT_REVENUE';
      if (!['COHORT_REVENUE', 'CASH_FLOW', 'EVENT_SALES', 'SETTLEMENT_BALANCE'].includes(metric))
        throw new Error('Loại báo cáo không hợp lệ.');
      result.set('metric', metric);
    } else if (pageType === 'audit') {
      for (const name of ['action', 'entityType', 'entityId']) if (values[name]) result.set(name, values[name]);
    }
    return result;
  }
  function formatVnd(value) {
    if (value == null || !/^-?[0-9]+$/.test(String(value))) return '—';
    return new Intl.NumberFormat('vi-VN').format(BigInt(value)) + ' ₫';
  }
  async function fetchResponse(url, type, network = fetch) {
    const response = await network(url, { credentials: 'same-origin', cache: 'no-store', headers: { Accept: type } });
    if (!response.ok || !(response.headers.get('Content-Type') || '').toLowerCase().startsWith(type)) {
      const messages = { 401: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
        403: 'Bạn không có quyền xem dữ liệu này.', 404: 'Không tìm thấy dữ liệu trong phạm vi đã chọn.',
        400: 'Bộ lọc không hợp lệ. Kiểm tra lại thông tin.', 503: 'Dữ liệu tạm thời không khả dụng. Vui lòng thử lại.' };
      let reference = '';
      if ((response.headers.get('Content-Type') || '').includes('application/json')) {
        try { const payload = await response.json(); if (uuid.test(payload.error?.correlationId || '')) reference = ' Mã tra cứu: ' + payload.error.correlationId; } catch (_) { /* Fixed error only. */ }
      }
      throw new Error((messages[response.status] || 'Không thể tải dữ liệu. Vui lòng đăng nhập lại hoặc thử lại.') + reference);
    }
    return response;
  }
  const api = { query, formatVnd, fetchResponse };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof document === 'undefined') return;
  const app=document.getElementById('finance-readside'); if(!app)return;
  const context=app.dataset.context||'', isAdmin=app.dataset.admin==='true';
  const form=document.getElementById('filters'), org=document.getElementById('organization'), event=document.getElementById('event');
  const notice=document.getElementById('notice'), tbody=document.getElementById('rows'), head=document.getElementById('columns');
  const previous=document.getElementById('previous'), next=document.getElementById('next'), pagination=document.getElementById('pagination');
  let page=1, sequence=0, scopeSequence=0, loadingScope=false;
  const money=new Set(['grossRevenue','totalRefund','remainingAmount','totalCommission','netPayable','ticketCaptured','compensationCaptured','ticketRefund','compensationRefund','netCashFlow','paidAmount','pendingAmount','availableToPay']);
  const labels={eventId:'Mã sự kiện',organizationId:'Mã tổ chức',eventTitle:'Tên sự kiện',eventStatus:'Trạng thái',endTime:'Kết thúc (UTC)',paidOrderCount:'Đơn đã thanh toán',ticketCount:'Vé đã phát hành',validTicketCount:'Vé hợp lệ',refundedTicketCount:'Vé đã hoàn',ticketsCheckedInCount:'Vé từng check-in',grossRevenue:'Doanh thu vé',totalRefund:'Hoàn tiền vé',remainingAmount:'Doanh thu sau hoàn',totalCommission:'Hoa hồng',netPayable:'Phải trả tổ chức',ticketCaptured:'Thu tiền vé',compensationCaptured:'Thu thanh toán thừa',ticketRefund:'Chi hoàn vé',compensationRefund:'Chi hoàn thanh toán thừa',netCashFlow:'Dòng tiền ròng',settlementId:'Mã quyết toán',status:'Trạng thái',settlementStatus:'Quyết toán',isConfirmed:'Bảng kê',paidAmount:'Đã chi',pendingAmount:'Chờ chi',availableToPay:'Có thể chi',confirmedAt:'Chốt lúc (UTC)',totalEvents:'Số sự kiện trong báo cáo'};
  const columns={COHORT_REVENUE:['eventId','paidOrderCount','grossRevenue','totalRefund','remainingAmount','totalCommission','netPayable'],CASH_FLOW:['eventId','ticketCaptured','compensationCaptured','ticketRefund','compensationRefund','netCashFlow'],EVENT_SALES:['eventId','eventTitle','eventStatus','endTime','paidOrderCount','ticketCount','validTicketCount','refundedTicketCount','ticketsCheckedInCount','grossRevenue','totalRefund','totalCommission','netPayable','settlementStatus','isConfirmed'],SETTLEMENT_BALANCE:['settlementId','eventId','status','grossRevenue','totalRefund','totalCommission','netPayable','paidAmount','pendingAmount','availableToPay','confirmedAt']};
  const basis={ORDER_PAID_AT_WITH_REFUNDS_BEFORE_TO:'Theo ngày đơn thanh toán; chỉ hoàn vé hoàn tất trước mốc Đến. Hoa hồng tính theo từng đơn.',PAYMENT_PAID_AT_AND_REFUND_PROCESSED_AT:'Theo ngày thu/chi hoàn trong khoảng đã chọn; thanh toán thừa được tách riêng.',EVENT_END_TIME_CURRENT_OR_CONFIRMED_TOTALS:'Lọc theo ngày kết thúc sự kiện; tiền hiện tại hoặc snapshot đã chốt. Số vé/check-in là số hiện tại, không phải phát sinh trong kỳ.'};
  function node(tag,text){const n=document.createElement(tag);if(text!=null)n.textContent=String(text);return n;}
  function values(){return Object.fromEntries(new FormData(form).entries());}
  function display(key,value){if(value==null)return '—';if(money.has(key))return formatVnd(value);if(key==='isConfirmed')return value?'Đã chốt':'Ước tính';return String(value);}
  function clear(){head.replaceChildren();tbody.replaceChildren();document.getElementById('summary').replaceChildren();document.getElementById('top-events').replaceChildren();previous.disabled=true;next.disabled=true;pagination.textContent='';document.getElementById('basis').textContent='';}
  async function data(url){return(await(await fetchResponse(context+url,'application/json')).json()).data;}
  async function scope(selected){const current=++scopeSequence;loadingScope=true;event.replaceChildren(new Option('Tất cả sự kiện',''));event.disabled=true;
    try{const result=await data('/api/reports/options'+(selected?'?organizationId='+encodeURIComponent(selected):''));if(current!==scopeSequence)return;org.replaceChildren();if(isAdmin)org.append(new Option('Tất cả tổ chức',''));for(const x of result.organizations)org.append(new Option(x.name,x.id));org.value=selected||(isAdmin?'':result.organizations[0]?.id||'');for(const x of result.events)event.append(new Option(x.title,x.id));event.disabled=!org.value;}finally{if(current===scopeSequence)loadingScope=false;}}
  function render(result){const keys=columns[result.metric];if(!keys||!Array.isArray(result.rows))throw Error('Dữ liệu báo cáo không hợp lệ.');const tr=node('tr');for(const key of keys){const th=node('th',labels[key]);th.scope='col';tr.append(th);}head.append(tr);
    for(const row of result.rows){const tr=node('tr');for(const key of keys)tr.append(node('td',display(key,row[key])));tbody.append(tr);}if(!result.rows.length){const td=node('td','Không có dữ liệu theo bộ lọc.');td.colSpan=keys.length;const tr=node('tr');tr.append(td);tbody.append(tr);}
    const dl=node('dl');for(const [key,value]of Object.entries(result.summary||{}))dl.append(node('dt',labels[key]||key),node('dd',display(key,value)));document.getElementById('summary').append(dl);
    const top=document.getElementById('top-events');if((result.topEvents||[]).length){top.append(node('h2','Sự kiện có doanh thu vé cao nhất theo bộ lọc'));const list=node('ol');for(const row of result.topEvents)list.append(node('li',(row.eventTitle||row.eventId)+' · '+formatVnd(row.grossRevenue)));top.append(list);}
    previous.disabled=page<=1;next.disabled=page*result.pageSize>=result.total;pagination.textContent='Trang '+page+' · '+result.total+' dòng';document.getElementById('basis').textContent=basis[result.timeBasis]||'';notice.textContent='Đã tải báo cáo; tổng hợp gồm toàn bộ dòng theo bộ lọc.';}
  async function load(target){const current=++sequence;clear();notice.textContent='Đang tải báo cáo…';try{if(loadingScope)throw Error('Đang tải danh sách sự kiện. Vui lòng thử lại.');const params=query(values(),'reports',target),selected=params.get('organizationId');const route=isAdmin?'/api/admin/reports':'/api/organizations/'+encodeURIComponent(selected||'')+'/reports';const result=await data(route+'?'+params);if(current!==sequence)return;page=target;render(result);}catch(error){if(current===sequence)notice.textContent=error.message;}}
  form.addEventListener('submit',e=>{e.preventDefault();load(1);});org.addEventListener('change',async()=>{++sequence;clear();try{await scope(org.value);await load(1);}catch(error){notice.textContent=error.message;}});
  previous.addEventListener('click',()=>load(Math.max(1,page-1)));next.addEventListener('click',()=>load(page+1));
  const exportButton=document.getElementById('export');exportButton.addEventListener('click',async()=>{exportButton.disabled=true;notice.textContent='Đang chuẩn bị CSV…';try{if(loadingScope)throw Error('Đang tải danh sách sự kiện. Vui lòng thử lại.');const params=query(values(),'reports',1);params.set('format','csv');params.set('scope',params.has('organizationId')?'ORGANIZATION':'PLATFORM');const response=await fetchResponse(context+'/api/reports/export?'+params,'text/csv');const blob=await response.blob(),url=URL.createObjectURL(blob),link=node('a');link.href=url;link.download='ticketscenter-'+params.get('metric').toLowerCase()+'.csv';document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);notice.textContent='Đã tải CSV gồm toàn bộ dòng theo bộ lọc.';}catch(error){notice.textContent=error.message;}finally{exportButton.disabled=false;}});
  (async()=>{try{const today=new Date();today.setUTCHours(0,0,0,0);const start=new Date(today);start.setUTCDate(start.getUTCDate()-30);const end=new Date(today);end.setUTCDate(end.getUTCDate()+1);form.elements.from.value=start.toISOString().slice(0,19);form.elements.to.value=end.toISOString().slice(0,19);await scope(app.dataset.organization||'');if(!isAdmin&&org.value)await scope(org.value);await load(1);}catch(error){notice.textContent=error.message;}})();
})();