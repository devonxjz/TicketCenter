<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head><title>Chi tiết sự kiện | TicketsCenter</title><jsp:include page="/WEB-INF/views/layout/meta-head.jsp" /></head>
<body data-context="${pageContext.request.contextPath}">
<nav class="navbar border-bottom bg-white"><div class="container"><a class="navbar-brand fw-bold" href="${pageContext.request.contextPath}/">TicketsCenter</a><a href="${pageContext.request.contextPath}/">Danh sách sự kiện</a></div></nav>
<main class="container py-5">
    <p id="status" role="status" aria-live="polite">Đang tải sự kiện…</p>
    <article id="event" hidden><img id="cover" alt="" class="w-100 mb-4" style="max-height:420px;object-fit:cover" hidden>
        <h1 id="title"></h1><p id="time" class="text-muted"></p><p id="venue"></p>
        <p id="sale" class="small text-muted"></p><p id="description" style="white-space:pre-wrap"></p>
        <h2 class="h4 mt-5">Khu vực và tình trạng vé</h2>
        <p class="text-muted small">Tình trạng vé được cập nhật định kỳ. Vé được xác nhận khi yêu cầu giữ vé thành công.</p>
        <p id="zone-status" role="status" aria-live="polite">Đang tải khu vực…</p><div id="zones" class="row g-3"></div>
    </article>
</main>
<jsp:include page="/WEB-INF/views/layout/footer.jsp" />
<script>
(async () => {
    const base = document.body.dataset.context, status = document.getElementById('status');
    function text(tag, value, className) { const el = document.createElement(tag); el.textContent = value; el.className = className || ''; return el; }
    async function api(path) {
        const response = await fetch(base + path, {headers: {Accept: 'application/json'}}), payload = await response.json();
        if (!response.ok) throw new Error(payload.error ? payload.error.message : 'Không thể tải dữ liệu.');
        return payload.data;
    }
    const id = new URLSearchParams(location.search).get('eventId');
    if (!id || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(id)) { status.textContent = 'ID sự kiện không hợp lệ. Vui lòng chọn sự kiện từ danh sách.'; return; }
    try {
        const event = await api('/api/events/' + encodeURIComponent(id));
        document.title = event.title + ' | TicketsCenter';
        document.getElementById('title').textContent = event.title;
        document.getElementById('time').textContent = new Date(event.startTime).toLocaleString('vi-VN') + ' – ' + new Date(event.endTime).toLocaleString('vi-VN');
        document.getElementById('venue').textContent = event.venueName + ' · ' + event.venueAddress;
        document.getElementById('sale').textContent = 'Mở bán: ' + new Date(event.saleStartTime).toLocaleString('vi-VN') + ' – ' + new Date(event.saleEndTime).toLocaleString('vi-VN');
        document.getElementById('description').textContent = event.description || '';
        if (event.coverImageUrl) {
            try { const url = new URL(event.coverImageUrl, location.origin); if (['https:', 'http:'].includes(url.protocol)) {
                const cover = document.getElementById('cover'); cover.src = url.href; cover.alt = event.title; cover.hidden = false;
            } } catch (_) { }
        }
        document.getElementById('event').hidden = false; status.textContent = '';
    } catch (error) { status.textContent = error.message; return; }
    const zoneStatus = document.getElementById('zone-status');
    try {
        const zones = await api('/api/events/' + encodeURIComponent(id) + '/zones');
        zoneStatus.textContent = zones.length ? '' : 'Chưa có khu vực vé.';
        for (const zone of zones) {
            const column = text('div', '', 'col-lg-6'), panel = text('section', '', 'border rounded p-4 h-100');
            panel.append(text('h3', zone.name, 'h5'));
            panel.append(text('p', BigInt(zone.basePrice).toLocaleString('vi-VN') + ' đ · Còn ' + zone.availableSeats + ' / ' + zone.totalCapacity + ' vé'));
            if (zone.zoneType === 'SEATED') {
                const button = text('button', 'Xem sơ đồ ghế', 'btn btn-outline-dark'), seats = text('div', '', 'd-flex flex-wrap gap-2 mt-3');
                const seatStatus = text('p', '', 'small mt-2'); seatStatus.setAttribute('role', 'status'); button.type = 'button';
                button.addEventListener('click', async () => {
                    button.disabled = true; seats.replaceChildren(); seatStatus.textContent = 'Đang tải ghế…';
                    try {
                        const items = await api('/api/zones/' + encodeURIComponent(zone.zoneId) + '/seats');
                        seatStatus.textContent = items.length ? 'Trống / Đang giữ / Đã bán' : 'Chưa có ghế.';
                        const rows = new Map();
                        for (const seat of items) {
                            if (!rows.has(seat.rowName)) {
                                const row = text('div', '', 'd-flex flex-wrap align-items-center gap-2 w-100');
                                row.append(text('strong', 'Hàng ' + seat.rowName, 'small me-2'));
                                rows.set(seat.rowName, row); seats.append(row);
                            }
                            const label = seat.status === 'AVAILABLE' ? 'Trống' : seat.status === 'HELD' ? 'Đang giữ' : 'Đã bán';
                            rows.get(seat.rowName).append(text('span', seat.seatLabel + ' · ' + label, 'badge p-2 ' + (seat.status === 'AVAILABLE' ? 'bg-success' : seat.status === 'HELD' ? 'bg-warning text-dark' : 'bg-secondary')));
                        }
                    } catch (error) { seatStatus.textContent = error.message; }
                    finally { button.disabled = false; }
                });
                panel.append(button, seatStatus, seats);
            } else { panel.append(text('p', 'Khu vực đứng tự do', 'text-muted')); }
            column.append(panel); document.getElementById('zones').append(column);
        }
    } catch (error) { zoneStatus.textContent = error.message; }
})();
</script>
</body></html>
