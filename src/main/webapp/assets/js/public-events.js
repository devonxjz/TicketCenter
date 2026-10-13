(function (root) {
    'use strict';
    const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
    function eventId(search) {
        const query = new URLSearchParams(search);
        const id = query.get('eventId') || query.get('id');
        return id && UUID.test(id) && id !== '00000000-0000-0000-0000-000000000000' ? id.toLowerCase() : null;
    }
    function price(value) {
        if (value === null || value === undefined) return 'Chưa có giá vé';
        return BigInt(value) === 0n ? 'Miễn phí' : BigInt(value).toLocaleString('vi-VN') + ' đ';
    }
    async function requestJson(fetch, url) {
        const response = await fetch(url, {credentials: 'same-origin', cache: 'no-store', headers: {Accept: 'application/json'}});
        const payload = await response.json();
        if (!response.ok) throw new Error(payload.error?.message || 'Không thể tải dữ liệu. Vui lòng thử lại.');
        return payload;
    }
    const exports = {eventId, price, requestJson};
    if (typeof module !== 'undefined' && module.exports) { module.exports = exports; return; }
    root.PublicEvents = exports;
    const doc = root.document, page = doc.body.dataset.publicPage;
    if (!page) return;
    const base = doc.body.dataset.context || '', get = id => doc.getElementById(id);
    const api = path => requestJson(root.fetch.bind(root), base + path);
    const node = (tag, text, className = '') => { const el = doc.createElement(tag); el.className = className; if (text !== undefined) el.textContent = text; return el; };
    const date = value => new Intl.DateTimeFormat('vi-VN', {dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh'}).format(new Date(value));
    function image(image, url, title) {
        image.hidden = true;
        try {
            const parsed = new URL(url, root.location.origin);
            if (url && ['https:', 'http:'].includes(parsed.protocol) && !parsed.username && !parsed.password) {
                image.src = parsed.href; image.alt = title; image.hidden = false;
                image.addEventListener('error', () => { image.hidden = true; }, {once: true});
            }
        } catch (_) { }
    }
    function eventCard(event) {
        const column = node('div', undefined, 'col-md-6 col-xl-4');
        const card = node('article', undefined, 'event-card tc-event-card');
        const cover = node('a', undefined, 'event-image tc-card-cover'); cover.href = base + '/event-detail?eventId=' + encodeURIComponent(event.eventId);
        cover.setAttribute('aria-label', 'Xem ' + event.title);
        const fallback = node('span', 'Chưa có ảnh', 'tc-image-empty');
        const img = node('img'); img.loading = 'lazy'; image(img, event.coverImageUrl, event.title);
        cover.append(fallback, img, node('span', event.category || 'Sự kiện', 'event-category-badge'));
        const body = node('div', undefined, 'event-card-body');
        const heading = node('h2', undefined, 'event-title'); const link = node('a', event.title); link.href = cover.href; heading.append(link);
        body.append(heading, node('p', date(event.startTime), 'tc-event-meta'), node('p', event.venueName, 'tc-event-meta'), node('p', price(event.minPrice), 'event-price'));
        const action = node('a', 'Xem chi tiết', 'btn-outline-custom'); action.href = cover.href; body.append(action);
        card.append(cover, body); column.append(card); return column;
    }
    async function catalogPage() {
        const form = get('filters'), cards = get('events'), status = get('status');
        let revision = 0, pageNumber = 1;
        const search = new URLSearchParams(root.location.search);
        for (const key of ['keyword', 'sort', 'from', 'to']) if (search.has(key)) form.elements.namedItem(key).value = search.get(key);
        async function load() {
            const current = ++revision;
            const query = new URLSearchParams({page: String(pageNumber), pageSize: '12'});
            for (const [key, value] of new FormData(form)) if (value.trim()) {
                if (key === 'from' || key === 'to') query.set(key, new Date(value).toISOString()); else query.set(key, value.trim());
            }
            get('previous').disabled = get('next').disabled = true; cards.setAttribute('aria-busy', 'true');
            status.textContent = 'Đang tải sự kiện…'; get('retry-events').hidden = true;
            try {
                const result = await api('/api/events?' + query);
                if (current !== revision) return;
                cards.replaceChildren(...result.data.map(eventCard));
                status.textContent = result.total ? 'Tìm thấy ' + result.total + ' sự kiện.' : 'Không có sự kiện phù hợp. Thử đổi bộ lọc hoặc từ khóa.';
                if (!result.data.length) cards.append(node('p', 'Không có sự kiện trong khoảng đã chọn.', 'tc-empty-state'));
                get('page-label').textContent = 'Trang ' + pageNumber + (result.totalPages ? ' / ' + result.totalPages : '');
                get('previous').disabled = pageNumber <= 1; get('next').disabled = pageNumber * result.pageSize >= result.total;
                const hero = get('catalog-featured'), event = result.data.find(event => event.isSaleActive);
                hero.hidden = !event || Boolean(query.get('keyword') || query.get('categoryId') || query.get('from') || query.get('to')) || pageNumber !== 1;
                if (!hero.hidden) {
                    get('featured-title').textContent = event.title; get('featured-time').textContent = date(event.startTime);
                    get('featured-venue').textContent = event.venueName; get('featured-price').textContent = price(event.minPrice);
                    get('featured-link').href = base + '/event-detail?eventId=' + encodeURIComponent(event.eventId);
                    image(get('featured-image'), event.coverImageUrl, event.title);
                }
            } catch (error) {
                if (current !== revision) return;
                cards.replaceChildren(); get('catalog-featured').hidden = true; status.textContent = error.message; get('retry-events').hidden = false;
            } finally { if (current === revision) cards.removeAttribute('aria-busy'); }
        }
        form.addEventListener('submit', e => { e.preventDefault(); pageNumber = 1; void load(); });
        get('previous').addEventListener('click', () => { pageNumber--; void load(); });
        get('next').addEventListener('click', () => { pageNumber++; void load(); });
        get('retry-events').addEventListener('click', () => void load());
        try {
            const categories = await api('/api/event-categories');
            for (const category of categories.data) {
                const option = node('option', category.name); option.value = category.categoryId; get('category').append(option);
            }
            if (search.has('categoryId')) get('category').value = search.get('categoryId');
            if (search.has('category')) {
                const selected = categories.data.find(category => category.slug === search.get('category'));
                if (selected) get('category').value = selected.categoryId;
            }
        } catch (_) { get('category').disabled = true; get('category-help').textContent = 'Chưa tải được danh mục. Bạn vẫn có thể tìm bằng từ khóa.'; }
        await load();
    }
    async function detailPage() {
        const id = eventId(root.location.search), status = get('status');
        if (!id) { status.textContent = 'Không tìm thấy mã sự kiện hợp lệ. Vui lòng chọn sự kiện từ danh sách.'; get('event-retry').hidden = true; return; }
        try {
            const event = (await api('/api/events/' + encodeURIComponent(id))).data;
            doc.title = event.title + ' | TicketsCenter'; get('title').textContent = event.title;
            get('breadcrumb-event').textContent = event.title;
            get('event-category').textContent = event.category || 'Sự kiện';
            get('breadcrumb-category').textContent = event.category || 'Sự kiện';
            get('breadcrumb-category').href = base + '/?categoryId=' + encodeURIComponent(event.categoryId);
            get('time').textContent = date(event.startTime) + ' – ' + date(event.endTime);
            get('venue').textContent = [event.venueName, event.venueAddress].filter(Boolean).join(' · ');
            get('organizer').textContent = event.organizationName || 'Chưa có thông tin tổ chức';
            get('sale').textContent = date(event.saleStartTime) + ' – ' + date(event.saleEndTime);
            get('description').textContent = event.description || 'Ban tổ chức chưa cung cấp mô tả sự kiện.';
            get('event-price').textContent = price(event.minPrice);
            image(get('cover'), event.coverImageUrl, event.title);
            get('event').hidden = false; get('event-breadcrumb').hidden = false; get('event-retry').hidden = true; status.textContent = '';
            get('event-hold-link').href = '#bookingSection';
            get('booking-login').href = base + '/auth/login?redirect=' + encodeURIComponent(root.location.pathname + root.location.search + '#bookingSection');
            get('sale-state').textContent = event.isSaleActive ? 'Đang mở bán' : 'Hiện chưa mở bán';
            const zones = (await api('/api/events/' + encodeURIComponent(id) + '/zones')).data;
            get('zones').replaceChildren();
            for (const zone of zones) {
                const panel = node('div', undefined, 'tc-zone-line');
                panel.append(node('strong', zone.name), node('span', price(zone.basePrice)), node('span', 'Còn ' + zone.availableSeats + ' / ' + zone.totalCapacity + ' vé', 'text-muted'));
                get('zones').append(panel);
            }
            get('zone-status').textContent = zones.length ? '' : 'Chưa có khu vực vé.';
        } catch (error) { status.textContent = error.message; get('event-retry').hidden = false; }
        get('event-retry').onclick = () => { get('event-retry').disabled = true; void detailPage().finally(() => { get('event-retry').disabled = false; }); };
    }
    void (page === 'catalog' ? catalogPage() : detailPage());
})(typeof window !== 'undefined' ? window : globalThis);
