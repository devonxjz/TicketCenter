<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Chọn sự kiện soát vé | TicketsCenter</title>
  <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-ui.css">
</head>
<body class="checkin-body">
<a class="portal-skip" href="#main-content">Đến nội dung chính</a><header class="checkin-topbar"><a class="navbar-brand-custom" href="${pageContext.request.contextPath}/">TicketsCenter</a><span class="portal-label">Cổng soát vé</span><a href="${pageContext.request.contextPath}/">Khám phá sự kiện &rarr;</a></header>
<main id="main-content"><h1>Chọn sự kiện soát vé</h1><p class="hint">Chọn sự kiện của tổ chức để bắt đầu ca làm việc.</p>
  <div class="toolbar"><span>Danh sách sự kiện</span><button type="button" id="reload">Làm mới</button></div>
  <div id="notice" role="status" aria-live="polite"></div>
  <div id="events" aria-live="polite"><p>Đang tải sự kiện…</p></div><nav class="tc-pagination" aria-label="Chuyển trang"><button id="event-previous" type="button" class="tc-page-button" aria-label="Trang trước" disabled>‹</button><span id="event-page"></span><button id="event-next" type="button" class="tc-page-button" aria-label="Trang sau" disabled>›</button></nav>
  <p class="hint">Giờ hiển thị theo thiết bị của bạn. Cổng mở trước giờ bắt đầu 60 phút và đóng khi sự kiện kết thúc.</p>
</main>
<script>
(() => {
  // Derive the WAR context from the page URL, never interpolate user input into script source.
  const base = location.pathname.replace(/\/check-ins\/?$/, '');
  const org = new URLSearchParams(location.search).get('organizationId');
  const host = document.getElementById('events'), notice = document.getElementById('notice');
  const labels = {OPEN:'Đang mở soát vé',NOT_YET_OPEN:'Chưa tới giờ',ENDED:'Đã kết thúc',CANCELLED:'Bị hủy',UNAVAILABLE:'Chưa mở soát vé'};
  let loading = false, items=[], page=1;
  function render(){const total=Math.max(1,Math.ceil(items.length/6));page=Math.min(page,total);host.replaceChildren(...items.slice((page-1)*6,page*6).map(card));document.getElementById('event-page').textContent='Trang '+page+' / '+total;document.getElementById('event-previous').disabled=page===1;document.getElementById('event-next').disabled=page===total;}
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
        // Permission and the check-in window are verified again on every scan.
        location.assign(base+'/organizer/checkin-scanner?eventId='+encodeURIComponent(event.eventId));
      } catch(error) {notice.textContent=error.message;button.disabled=false;}
    });
    article.append(stats,button);return article;
  }
  async function load() {
    if(loading)return;loading=true;document.getElementById('reload').disabled=true;
    try {
      const events=await json(base+'/api/check-ins/events'+(org ? '?organizationId='+encodeURIComponent(org):''));
      items=events;render();
      if(events.length===0)host.append(element('p','Chưa có sự kiện đang diễn ra hoặc sắp diễn ra trong tổ chức của bạn.','empty'));
    } catch(error) {notice.textContent=error.message;host.replaceChildren();}
    finally {loading=false;document.getElementById('reload').disabled=false;}
  }
  document.getElementById('event-previous').addEventListener('click',()=>{if(page>1){page--;render();}});
  document.getElementById('event-next').addEventListener('click',()=>{if(page*6<items.length){page++;render();}});
  document.getElementById('reload').addEventListener('click',()=>{notice.textContent='';load();});
  load();setInterval(()=>{if(!document.hidden)load();},30000);
})();
</script>
</body>
</html>
