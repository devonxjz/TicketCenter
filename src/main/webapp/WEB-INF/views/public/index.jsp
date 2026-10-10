<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head><title>Khám phá sự kiện | TicketsCenter</title><jsp:include page="/WEB-INF/views/layout/meta-head.jsp" /></head>
<body data-context="${pageContext.request.contextPath}">
<nav class="navbar border-bottom bg-white"><div class="container"><a class="navbar-brand fw-bold" href="${pageContext.request.contextPath}/">TicketsCenter</a><a class="btn btn-outline-dark" href="${pageContext.request.contextPath}/auth/login">Tài khoản</a></div></nav>
<main class="container py-5">
    <h1 class="fw-bold">Khám phá sự kiện</h1><p class="text-muted">Tìm sự kiện và xem tình trạng vé đang được mở bán.</p>
    <form id="filters" class="row g-3 my-3" aria-label="Tìm kiếm sự kiện">
        <div class="col-md-5"><label for="keyword" class="form-label">Từ khóa</label><input id="keyword" name="keyword" class="form-control" maxlength="100" placeholder="Tên sự kiện"></div>
        <div class="col-md-4"><label for="category" class="form-label">Danh mục</label><select id="category" name="categoryId" class="form-select"><option value="">Tất cả danh mục</option></select></div>
        <div class="col-md-3"><label for="sort" class="form-label">Sắp xếp</label><select id="sort" name="sort" class="form-select"><option value="startTime_asc">Sắp diễn ra</option><option value="startTime_desc">Diễn ra muộn nhất</option><option value="price_asc">Giá tăng dần</option><option value="price_desc">Giá giảm dần</option></select></div>
        <div class="col-md-4"><label for="from" class="form-label">Từ ngày (giờ địa phương)</label><input id="from" name="from" type="datetime-local" class="form-control"></div>
        <div class="col-md-4"><label for="to" class="form-label">Trước ngày (giờ địa phương)</label><input id="to" name="to" type="datetime-local" class="form-control"></div>
        <div class="col-md-4 d-flex align-items-end"><button class="btn btn-dark w-100" type="submit">Tìm kiếm</button></div>
    </form>
    <p id="status" role="status" aria-live="polite">Đang tải sự kiện…</p>
    <div id="events" class="row g-4"></div>
    <nav class="d-flex justify-content-center align-items-center gap-3 mt-4" aria-label="Phân trang"><button id="previous" class="btn btn-outline-dark" disabled>Trang trước</button><span id="page-label"></span><button id="next" class="btn btn-outline-dark" disabled>Trang sau</button></nav>
</main>
<jsp:include page="/WEB-INF/views/layout/footer.jsp" />
<script>
(() => {
    const base = document.body.dataset.context, form = document.getElementById('filters');
    const status = document.getElementById('status'), cards = document.getElementById('events');
    const previous = document.getElementById('previous'), next = document.getElementById('next');
    let page = 1, revision = 0;
    function text(tag, value, className) { const el = document.createElement(tag); el.textContent = value; el.className = className || ''; return el; }
    async function api(path) {
        const response = await fetch(base + path, {headers: {Accept: 'application/json'}});
        const payload = await response.json();
        if (!response.ok) throw new Error(payload.error ? payload.error.message : 'Không thể tải sự kiện.');
        return payload;
    }
    async function load() {
        const current = ++revision;
        status.textContent = 'Đang tải sự kiện…'; cards.replaceChildren(); previous.disabled = next.disabled = true;
        document.getElementById('page-label').textContent = '';
        try {
            const query = new URLSearchParams({page: String(page), pageSize: '12'});
            for (const [key, raw] of new FormData(form)) {
                if (!raw.trim()) continue;
                const value = key === 'from' || key === 'to' ? new Date(raw).toISOString() : raw.trim();
                query.set(key, value);
            }
            if (query.has('from') && query.has('to') && query.get('from') >= query.get('to')) throw new Error('Ngày bắt đầu phải trước ngày kết thúc.');
            const result = await api('/api/events?' + query);
            if (current !== revision) return;
            status.textContent = result.total === 0 ? 'Chưa có sự kiện phù hợp.' : 'Tìm thấy ' + result.total + ' sự kiện.';
            for (const event of result.data) {
                const column = text('div', '', 'col-md-6 col-lg-4'), card = text('article', '', 'card h-100');
                if (event.coverImageUrl) {
                    try { const url = new URL(event.coverImageUrl, location.origin);
                        if (url.protocol === 'https:' || url.protocol === 'http:') {
                            const image = document.createElement('img'); image.src = url.href; image.alt = event.title; image.loading = 'lazy';
                            image.className = 'card-img-top'; image.style.cssText = 'height:200px;object-fit:cover'; card.append(image);
                        }
                    } catch (_) { }
                }
                const body = text('div', '', 'card-body'); body.append(text('h2', event.title, 'h5'));
                body.append(text('p', new Date(event.startTime).toLocaleString('vi-VN'), 'text-muted small'));
                body.append(text('p', event.venueName));
                body.append(text('p', event.minPrice === null ? 'Chưa có giá vé' : 'Từ ' + BigInt(event.minPrice).toLocaleString('vi-VN') + ' đ', 'fw-bold'));
                const link = text('a', 'Xem chi tiết', 'btn btn-dark'); link.href = base + '/event-detail?eventId=' + encodeURIComponent(event.eventId);
                body.append(link); card.append(body); column.append(card); cards.append(column);
            }
            previous.disabled = page <= 1; next.disabled = page * result.pageSize >= result.total;
            document.getElementById('page-label').textContent = 'Trang ' + page;
        } catch (error) { if (current === revision) { cards.replaceChildren(); status.textContent = error.message; } }
    }
    form.addEventListener('submit', event => { event.preventDefault(); page = 1; load(); });
    previous.addEventListener('click', () => { page--; load(); }); next.addEventListener('click', () => { page++; load(); });
    api('/api/event-categories').then(result => {
        for (const category of result.data) { const option = text('option', category.name); option.value = category.categoryId; document.getElementById('category').append(option); }
    }).catch(() => { document.getElementById('category').disabled = true; });
    load();
})();
</script>
</body></html>
