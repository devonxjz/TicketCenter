(function () {
  'use strict';
  const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  function formatVnd(value) {
    return /^-?\d+$/.test(String(value)) ? new Intl.NumberFormat('vi-VN').format(BigInt(value))+' ₫' : '—';
  }
  function utc(value) {
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value || '')) throw new Error('Thời điểm UTC không hợp lệ.');
    const input=value.length===16?value+':00':value,date=new Date(input+'Z');
    if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0,19)!==input) throw new Error('Thời điểm UTC không hợp lệ.');
    return date.toISOString();
  }
  function query(values,page) {
    const from=utc(values.from),to=utc(values.to);
    if (from>=to) throw new Error('Mốc Đến phải sau mốc Từ.');
    const params=new URLSearchParams({from,to,page:String(page),pageSize:values.pageSize||'20'});
    for (const key of ['organizationId','eventId','actorId','entityId','entityType','targetTable','action']) {
      const value=values[key];if (!value) continue;
      if (['organizationId','eventId','actorId'].includes(key) && !uuid.test(value)) throw new Error('Mã định danh không hợp lệ.');
      params.set(key,value);
    }
    return params;
  }
  function chartBars(days) {
    let largest=0n;
    const rows=days.map(day=>{
      if (!/^\d+$/.test(day.cohortGrossRevenue) || !/^-?\d+$/.test(day.netCashFlow)) throw new Error('Số liệu biểu đồ không hợp lệ.');
      const revenue=BigInt(day.cohortGrossRevenue),cash=BigInt(day.netCashFlow),absolute=cash<0n?-cash:cash;
      largest=largest>revenue?largest:revenue;largest=largest>absolute?largest:absolute;
      return {day,revenue,cash,absolute};
    });
    const height=amount=>amount===0n||largest===0n?0:Math.max(1,Number(amount*120n/largest));
    return rows.map(({day,revenue,cash,absolute})=>({date:day.date,cohortGrossRevenue:String(revenue),netCashFlow:String(cash),revenueHeight:height(revenue),cashHeight:height(absolute),cashNegative:cash<0n}));
  }
  async function fetchResponse(url,network=fetch) {
    const response=await network(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});
    const json=(response.headers.get('Content-Type')||'').toLowerCase().startsWith('application/json');
    if (!response.ok || !json) {
      const messages={400:'Bộ lọc không hợp lệ. Kiểm tra lại thông tin.',401:'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',403:'Bạn không có quyền xem dữ liệu này.',404:'Không tìm thấy nhật ký.',503:'Dữ liệu tạm thời không khả dụng. Vui lòng thử lại.'};
      let reference='';
      if (json) {try {const payload=await response.json();if(uuid.test(payload.error?.correlationId||''))reference=' Mã tra cứu: '+payload.error.correlationId;}catch (_) {}}
      throw new Error((messages[response.status]||'Không thể tải dữ liệu. Vui lòng thử lại.')+reference);
    }
    return response;
  }
  if (typeof module!=='undefined' && module.exports) module.exports={query,formatVnd,chartBars,fetchResponse};
  if (typeof document==='undefined') return;
  const app=document.getElementById('finance-readside');if(!app)return;
  const context=app.dataset.context||'',audit=app.dataset.page==='audit';
  const form=document.getElementById('filters'),notice=document.getElementById('notice');
  const org=document.getElementById('organization'),event=document.getElementById('event');
  let page=1,sequence=0,revision=0,detailSequence=0,scopeSequence=0;
  const labels={id:'Mã nhật ký',createdAt:'Thời gian (UTC)',actorName:'Người thực hiện',entityName:'Đối tượng',actorId:'Người thực hiện',action:'Thao tác',entityType:'Loại đối tượng',entityId:'Mã đối tượng',eventId:'Sự kiện',organizationId:'Tổ chức',amount:'Số tiền',totalUsers:'Tổng người dùng',activeOrganizations:'Tổ chức đã duyệt',pendingOrganizations:'Tổ chức chờ duyệt',pendingEvents:'Sự kiện chờ duyệt',pendingRefunds:'Yêu cầu hoàn chờ xử lý',draftSettlements:'Quyết toán nháp',asOf:'Cập nhật (UTC)',todayCohortGrossRevenue:'Doanh thu đơn thanh toán hôm nay',todayCohortCommission:'Hoa hồng của đơn thanh toán hôm nay',ticketCaptured:'Thu tiền vé',compensationCaptured:'Thu thanh toán thừa',ticketRefund:'Chi hoàn vé',compensationRefund:'Chi hoàn thanh toán thừa',netCashFlow:'Dòng tiền ròng'};
  Object.assign(labels,{ratePercent:'Tỷ lệ hoa hồng (%)',fixedFee:'Phí cố định',grossRevenue:'Doanh thu gộp',totalRefund:'Tổng hoàn vé',totalCommission:'Tổng hoa hồng',netPayable:'Phải trả tổ chức',paidAmount:'Đã chi',pendingAmount:'Đang chờ chi',previousStatus:'Trạng thái trước',newStatus:'Trạng thái sau',status:'Trạng thái',result:'Kết quả',settlementId:'Quyết toán',commissionRuleId:'Chính sách hoa hồng',payoutId:'Lần chi trả',refundId:'Hoàn tiền',orderId:'Đơn hàng',effectiveFrom:'Hiệu lực từ',effectiveTo:'Hiệu lực đến',processedAt:'Xử lý lúc',confirmedAt:'Chốt lúc'});
  const money=new Set(['amount','grossRevenue','totalRefund','totalCommission','netPayable','paidAmount','pendingAmount','fixedFee','todayCohortGrossRevenue','todayCohortCommission','ticketCaptured','compensationCaptured','ticketRefund','compensationRefund','netCashFlow']);
  function node(tag,text) {const el=document.createElement(tag);if(text!=null)el.textContent=String(text);return el;}
  function display(key,value) {if(value==null)return '—';if(money.has(key))return formatVnd(value);return ({Event:'Sự kiện',Organization:'Tổ chức',Refund:'Hoàn tiền',Settlement:'Quyết toán',CommissionRule:'Hoa hồng',SETTLEMENT_CONFIRMED:'Chốt quyết toán',SETTLEMENT_RECALCULATED:'Tính lại quyết toán',REFUND_APPROVED:'Duyệt hoàn tiền',REFUND_REJECTED:'Từ chối hoàn tiền',EVENT_PUBLISHED:'Công khai sự kiện',DRAFT:'Bản nháp',CONFIRMED:'Đã chốt',PAID:'Đã chi trả'}[value])||String(value);}
  function clear() {
    ++detailSequence;
    for(const id of ['rows','columns','summary','queue-summary','audit-detail','daily-chart','daily-values'])document.getElementById(id)?.replaceChildren();
    for(const id of ['previous','next']){const button=document.getElementById(id);if(button)button.disabled=true;}
    const pagination=document.getElementById('pagination');if(pagination)pagination.textContent='';
  }
  function invalidate() {++revision;++sequence;clear();app.setAttribute('aria-busy','false');notice.textContent='Bộ lọc đã đổi. Bấm Lọc nhật ký để tải dữ liệu mới.';}
  async function data(url) {return (await (await fetchResponse(context+url)).json()).data;}
  async function scope(selected) {
    const current=++scopeSequence;
    event.replaceChildren(new Option('Tất cả sự kiện',''));event.disabled=true;
    const result=await data('/api/reports/options'+(selected?'?organizationId='+encodeURIComponent(selected):''));
    if(current!==scopeSequence)return false;
    org.replaceChildren(new Option('Tất cả tổ chức',''));
    for(const item of result.organizations)org.append(new Option(item.name,item.id));
    org.value=selected||'';
    for(const item of result.events)event.append(new Option(item.title,item.id));
    event.disabled=!selected;return true;
  }
  async function detail(id) {
    const panel=document.getElementById('audit-detail'),current=++detailSequence,selected=revision;
    panel.replaceChildren();
    try {
      const result=await data('/api/admin/audit-logs/'+encodeURIComponent(id));
      if(current!==detailSequence||selected!==revision)return;
      const fields=node('dl');
      for(const key of ['createdAt','actorName','action','entityType','entityName'])fields.append(node('dt',labels[key]),node('dd',display(key,result[key])));
      const metadata=node('dl');for(const [key,value]of Object.entries(result.metadata||{}).filter(([key])=>!/(Id|Key)$/.test(key)))metadata.append(node('dt',labels[key]||key),node('dd',display(key,value)));
      panel.append(node('h2','Chi tiết nhật ký'),fields,node('h3','Thông tin được phép hiển thị'),Object.keys(result.metadata||{}).length?metadata:node('p','Không có thông tin bổ sung.'));
      panel.scrollIntoView({behavior:'smooth',block:'start'});
    } catch(error) {if(current===detailSequence&&selected===revision)notice.textContent=error.message;}
  }
  function table(result) {
    if(!Array.isArray(result.rows))throw new Error('Dữ liệu nhật ký không hợp lệ.');
    const keys=['createdAt','action','entityType','entityName','actorName'],head=node('tr');
    for(const key of keys){const th=node('th',labels[key]);th.scope='col';head.append(th);}head.append(node('th','Chi tiết'));
    document.getElementById('columns').append(head);
    const rows=document.getElementById('rows');
    for(const row of result.rows){const tr=node('tr');for(const key of keys)tr.append(node('td',display(key,row[key])));const td=node('td'),button=node('button','Xem');button.type='button';button.addEventListener('click',()=>detail(row.id));td.append(button);tr.append(td);rows.append(tr);}
    if(!result.rows.length){const tr=node('tr'),td=node('td','Không có nhật ký trong khoảng đã chọn.');td.colSpan=6;tr.append(td);rows.append(tr);}
    document.getElementById('previous').disabled=result.page<=1;
    document.getElementById('next').disabled=result.page*result.pageSize>=result.total;
    document.getElementById('pagination').textContent='Trang '+result.page+' · '+result.total+' dòng';
  }
  function svgNode(tag,attributes,text) {const el=document.createElementNS('http://www.w3.org/2000/svg',tag);for(const [key,value]of Object.entries(attributes))el.setAttribute(key,String(value));if(text!=null)el.textContent=String(text);return el;}
  function overview(result) {
    const summary=document.getElementById('summary');
    const queue=document.getElementById('queue-summary');
    if(queue)for(const [key,path,linkLabel]of [
      ['pendingOrganizations','/admin/orgs/approval','Xem hồ sơ tổ chức →'],
      ['pendingEvents','/admin/events/approval','Thẩm định sự kiện →'],
      ['pendingRefunds','/admin/refund-requests','Xử lý hoàn vé →'],
      ['draftSettlements','/finance/settlements','Đối soát bảng kê →']
    ]){
      if(result[key]==null)continue;
      const card=node('div'),link=node('a',linkLabel);card.className='finance-queue';link.href=context+path;
      card.append(node('dt',labels[key]),node('dd',String(result[key])),link);queue.append(card);
    }
    function stat(key,value){const card=node('div');card.className=key==='asOf'?'finance-stat finance-stat-wide':'finance-stat';card.append(node('dt',labels[key]||key),node('dd',display(key,value)));summary.append(card);}
    for(const key of ['totalUsers','activeOrganizations','asOf','todayCohortGrossRevenue','todayCohortCommission'])if(result[key]!=null)stat(key,result[key]);
    for(const [key,value]of Object.entries(result.todayCashFlow||{}))stat(key,value);
    const bars=chartBars(result.dailyMetrics||[]),svg=svgNode('svg',{viewBox:'0 0 640 310',role:'img','aria-label':'Doanh thu và dòng tiền trong 7 ngày UTC'});
    svg.append(svgNode('line',{x1:20,y1:150,x2:620,y2:150,stroke:'#555'}));
    const rows=document.getElementById('daily-values');
    bars.forEach((bar,i)=>{const x=36+i*84;
      for(const [offset,height,negative,color]of [[0,bar.revenueHeight,false,'#e50914'],[26,bar.cashHeight,bar.cashNegative,'#111111']]){
        const rect=svgNode('rect',{x:x+offset,y:negative?150:150-height,width:22,height,fill:color});rect.append(svgNode('title',{},bar.date+' · '+formatVnd(offset?bar.netCashFlow:bar.cohortGrossRevenue)));svg.append(rect);
      }
      svg.append(svgNode('text',{x:x+24,y:300,'text-anchor':'middle','font-size':12},String(bar.date).slice(5)));
      const tr=node('tr');tr.append(node('td',bar.date),node('td',formatVnd(bar.cohortGrossRevenue)),node('td',formatVnd(bar.netCashFlow)));rows.append(tr);
    });document.getElementById('daily-chart').append(svg);
  }
  async function load(newPage=1) {
    const current=++sequence,selected=revision;clear();notice.textContent='Đang tải dữ liệu…';app.setAttribute('aria-busy','true');
    try {
      const url=audit?'/api/admin/audit-logs?'+query(Object.fromEntries(new FormData(form).entries()),newPage):'/api/admin/overview';
      const result=await data(url);if(current!==sequence||selected!==revision)return;
      if(audit)table(result);else overview(result);page=newPage;notice.textContent='';
    } catch(error) {if(current===sequence&&selected===revision){clear();notice.textContent=error.message;}}
    finally {if(current===sequence&&selected===revision)app.setAttribute('aria-busy','false');}
  }
  form.addEventListener('submit',e=>{e.preventDefault();load(1);});
  if(audit){
    form.addEventListener('change',e=>{if(e.target!==org)invalidate();});
    form.addEventListener('input',invalidate);
    org.addEventListener('change',async()=>{invalidate();const selected=revision;try{await scope(org.value);}catch(error){if(selected===revision)notice.textContent=error.message;}});
    document.getElementById('previous').addEventListener('click',()=>load(Math.max(1,page-1)));
    document.getElementById('next').addEventListener('click',()=>load(page+1));
  }
  (async()=>{try {
    if(audit){const today=new Date();today.setUTCHours(0,0,0,0);const from=new Date(today),to=new Date(today);from.setUTCDate(from.getUTCDate()-30);to.setUTCDate(to.getUTCDate()+1);form.elements.from.value=from.toISOString().slice(0,19);form.elements.to.value=to.toISOString().slice(0,19);const selected=revision;if(!await scope('')||selected!==revision)return;}
    await load(1);
  }catch(error){notice.textContent=error.message;}})();
})();
