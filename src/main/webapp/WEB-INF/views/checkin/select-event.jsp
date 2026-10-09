<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Chọn sự kiện soát vé | TicketCenter</title>
  <style>
    *{box-sizing:border-box}body{margin:0;background:#f4f6fb;color:#17243d;font-family:system-ui,sans-serif}
    header{background:#142544;color:#fff;padding:24px max(20px,calc((100vw - 1040px)/2))}
    header p{color:#c7d5ed;margin:8px 0 0}main{max-width:1040px;margin:auto;padding:24px 20px}
    h1{font-size:1.5rem;margin:0}h2{font-size:1.15rem;margin:12px 0}
    .toolbar{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:18px}
    #events{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,300px),1fr));gap:18px}
    article{background:#fff;border:1px solid #dce2ee;border-radius:14px;padding:22px;box-shadow:0 3px 12px #14254408}
    .badge{display:inline-block;border-radius:30px;padding:5px 12px;font-size:.85rem;font-weight:650}
    .OPEN{background:#d9f7e7;color:#14643c}.NOT_YET_OPEN{background:#fff2cc;color:#805500}
    .ENDED,.CANCELLED,.UNAVAILABLE{background:#ffe1e1;color:#922d2d}
    .detail{color:#53647e;font-size:.95rem;line-height:1.65}.stats{display:flex;gap:24px;margin:20px 0}
    .stats strong{display:block;font-size:1.3rem}.stats span{color:#63728a;font-size:.85rem}
    button{background:#214eae;color:#fff;border:0;border-radius:8px;padding:12px 16px;font:inherit;cursor:pointer;min-height:44px}
    article button{width:100%}button:disabled{background:#e3e7ee;color:#68758b;cursor:default}
    button:focus-visible{outline:3px solid #d79419;outline-offset:3px}
    #notice{padding:14px;border-radius:8px;background:#fff2cc;margin-bottom:18px}#notice:empty{display:none}
    .hint{color:#63728a;font-size:.85rem}.empty{padding:32px;background:#fff;border-radius:12px;text-align:center}
  </style>
</head>
<body>
<header><h1>Chọn sự kiện soát vé</h1><p>Chọn sự kiện của tổ chức để bắt đầu ca làm việc.</p></header>
<main>
  <div class="toolbar"><span>Danh sách sự kiện</span><button type="button" id="reload">Làm mới</button></div>
  <div id="notice" role="status" aria-live="polite"></div>
  <div id="events" aria-live="polite"><p>Đang tải sự kiện…</p></div>
  <p class="hint">Giờ hiển thị theo thiết bị của bạn. Cổng mở trước giờ bắt đầu 60 phút và đóng khi sự kiện kết thúc.</p>
</main>
<script>
(() => {
  // Derive the WAR context from the page URL, never interpolate user input into script source.
  const base = location.pathname.replace(/\/check-ins\/?$/, '');
  const org = new URLSearchParams(location.search).get('organizationId');
  const host = document.getElementById('events'), notice = document.getElementById('notice');
  const labels = {OPEN:'Đang mở soát vé',NOT_YET_OPEN:'Chưa tới giờ',ENDED:'Đã kết thúc',CANCELLED:'Bị hủy',UNAVAILABLE:'Chưa mở soát vé'};
  let loading = false;
  function element(tag, text, className) { const el=document.createElement(tag); if(text !== undefined) el.textContent=text; if(className) el.className=className; return el; }
  function date(value) { return new Intl.DateTimeFormat('vi-VN',{dateStyle:'medium',timeStyle:'short'}).format(new Date(value)); }
  async function json(url) {
    const response=await fetch(url,{credentials:'same-origin',headers:{Accept:'application/json'},cache:'no-store'});
    const body=await response.json();
    if(!response.ok) throw new Error(body.error?.message || 'Không thể tải dữ liệu. Vui lòng thử lại.');
    return body.data;
  }
  function card(event) {
    const article=element('article'), badge=element('span',labels[event.status] || 'Chưa mở soát vé','badge '+event.status);
    article.append(badge,element('h2',event.title),element('p',event.venue,'detail'),element('p','Bắt đầu: '+date(event.startTime),'detail'),
      element('p','Kết thúc: '+date(event.endTime),'detail'),element('p','Mở cổng: '+date(event.checkInOpenTime),'detail'));
    const stats=element('div',undefined,'stats');
    for(const [count,label] of [[event.totalTicketsIssued,'Vé đã phát hành'],[event.totalCheckedIn,'Đã vào cửa']]) {
      const box=element('div');box.append(element('strong',String(count)),element('span',label));stats.append(box);
    }
    const button=element('button','Vào điểm danh soát vé');button.type='button';button.disabled=!event.isCheckInOpen;
    button.addEventListener('click',async()=>{
      button.disabled=true;notice.textContent='';
      try {
        const window=await json(base+'/api/events/'+encodeURIComponent(event.eventId)+'/check-in-window');
        if(!window.isOpen) {notice.textContent=window.message;await load();return;}
        // UI-16 consumes this event context; permission/time are checked again on each scan.
        location.assign(base+'/check-ins/scanner?eventId='+encodeURIComponent(event.eventId));
      } catch(error) {notice.textContent=error.message;button.disabled=false;}
    });
    article.append(stats,button);return article;
  }
  async function load() {
    if(loading)return;loading=true;document.getElementById('reload').disabled=true;
    try {
      const events=await json(base+'/api/check-ins/events'+(org ? '?organizationId='+encodeURIComponent(org):''));
      host.replaceChildren(...events.map(card));
      if(events.length===0)host.append(element('p','Chưa có sự kiện đang diễn ra hoặc sắp diễn ra trong tổ chức của bạn.','empty'));
    } catch(error) {notice.textContent=error.message;host.replaceChildren();}
    finally {loading=false;document.getElementById('reload').disabled=false;}
  }
  document.getElementById('reload').addEventListener('click',()=>{notice.textContent='';load();});
  load();setInterval(()=>{if(!document.hidden)load();},30000);
})();
</script>
</body>
</html>
