(function(root) {
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
        const response = await fetch(url, {
            credentials: 'same-origin',
            cache: 'no-store',
            headers: {
                Accept: 'application/json'
            }
        });
        const payload = await response.json();
        if (!response.ok) throw new Error(payload.error?.message || 'Không thể tải dữ liệu. Vui lòng thử lại.');
        return payload;
    }

    function localDateTime(value) {
        if (!value) return '';
        const date = new Date(value);
        if (!Number.isFinite(date.getTime())) return '';
        return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    }

    function catalogState(search) {
        const query = new URLSearchParams(search),
            page = Number(query.get('page'));
        return {
            page: Number.isSafeInteger(page) && page > 0 ? page : 1,
            keyword: query.get('keyword') || '',
            categoryId: query.get('categoryId') || '',
            sort: ['startTime_asc', 'startTime_desc', 'price_asc', 'price_desc'].includes(query.get('sort')) ? query
                .get('sort') : 'startTime_asc',
            from: localDateTime(query.get('from')),
            to: localDateTime(query.get('to'))
        };
    }

    function catalogQuery(entries, page, now = new Date()) {
        const today = new Date(now);
        today.setHours(0, 0, 0, 0);
        const query = new URLSearchParams({
            page: String(page),
            pageSize: '6',
            from: today.toISOString()
        });
        for (const [key, raw] of entries) {
            const value = String(raw).trim();
            if (!value) continue;
            if (key === 'from' || key === 'to') {
                const date = new Date(value);
                if (!Number.isFinite(date.getTime())) throw new Error('Vui lòng chọn ngày hợp lệ.');
                query.set(key, date.toISOString());
            } else query.set(key, value);
        }
        if (query.has('to') && new Date(query.get('to')) <= new Date(query.get('from')))
            throw new Error('Ngày kết thúc phải sau ngày bắt đầu.');
        return query;
    }
    const exports = {
        eventId,
        price,
        requestJson,
        localDateTime,
        catalogState,
        catalogQuery
    };
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exports;
        return;
    }
    root.PublicEvents = exports;
    const doc = root.document,
        page = doc.body.dataset.publicPage;
    if (!page) return;
    const base = doc.body.dataset.context || '',
        get = id => doc.getElementById(id);
    const api = path => requestJson(root.fetch.bind(root), base + path);
    const node = (tag, text, className = '') => {
        const el = doc.createElement(tag);
        el.className = className;
        if (text !== undefined) el.textContent = text;
        return el;
    };
    const date = value => new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'Asia/Ho_Chi_Minh'
    }).format(new Date(value));

    function image(image, url, title) {
        image.hidden = true;
        try {
            const parsed = new URL(url, root.location.origin);
            if (url && ['https:', 'http:'].includes(parsed.protocol) && !parsed.username && !parsed.password) {
                image.src = parsed.href;
                image.alt = title;
                image.hidden = false;
                image.addEventListener('error', () => {
                    image.hidden = true;
                }, {
                    once: true
                });
            }
        } catch (_) {}
    }

    function eventCard(event) {
        const column = node('div', undefined, 'col-md-6 col-xl-4');
        const card = node('article', undefined, 'event-card tc-event-card');
        const cover = node('a', undefined, 'event-image tc-card-cover');
        cover.href = base + '/event-detail?eventId=' + encodeURIComponent(event.eventId);
        cover.setAttribute('aria-label', 'Xem ' + event.title);
        const fallback = node('span', 'Chưa có ảnh', 'tc-image-empty');
        const img = node('img');
        img.loading = 'lazy';
        image(img, event.coverImageUrl, event.title);
        cover.append(fallback, img, node('span', event.category || 'Sự kiện', 'event-category-badge'));
        const body = node('div', undefined, 'event-card-body');
        const heading = node('h2', undefined, 'event-title');
        const link = node('a', event.title);
        link.href = cover.href;
        heading.append(link);
        body.append(heading, node('p', date(event.startTime), 'tc-event-meta'), node('p', event.venueName,
            'tc-event-meta'), node('p', price(event.minPrice), 'event-price'));
        const action = node('a', 'Xem chi tiết', 'btn-outline-custom');
        action.href = cover.href;
        body.append(action);
        card.append(cover, body);
        column.append(card);
        return column;
    }
    async function catalogPage() {
        const form = get('filters'),
            cards = get('events'),
            status = get('status');
        let revision = 0,
            pageNumber = 1;
        let slides = [],
            slideIndex = 0,
            paused = root.matchMedia('(prefers-reduced-motion: reduce)').matches,
            timer;

        function moveSlide(index) {
            if (!slides.length) return;
            slideIndex = (index + slides.length) % slides.length;
            get('hero-track').style.transform = 'translateX(-' + (slideIndex * 100) + '%)';
            [...get('hero-dots').children].forEach((button, i) => button.setAttribute('aria-current', String(
                i === slideIndex)));
            slides.forEach((slide, i) => {
                slide.inert = i !== slideIndex;
                slide.setAttribute('aria-hidden', String(i !== slideIndex));
            });
        }

        function startBanner() {
            root.clearInterval(timer);
            if (!paused && slides.length > 1) timer = root.setInterval(() => {
                if (!doc.hidden && !get('catalog-featured').matches(':hover') && !get(
                        'catalog-featured').contains(doc.activeElement)) moveSlide(slideIndex + 1);
            }, 5500);
        }

        function banner(events) {
            try {
                const items = events.filter(event => event.coverImageUrl);
                const track = get('hero-track'),
                    dots = get('hero-dots');
                track.replaceChildren();
                dots.replaceChildren();
                slides = [];
                for (const [i, event] of items.entries()) {
                    const slide = node('a', undefined, 'tc-hero-slide');
                    slide.href = base + '/event-detail?eventId=' + encodeURIComponent(event.eventId);
                    const img = node('img');
                    image(img, event.coverImageUrl, event.title);
                    img.addEventListener('error', () => {
                        slide.classList.add('tc-hero-no-image');
                    });
                    const caption = node('div', undefined, 'tc-hero-caption');
                    caption.append(node('span', event.category || 'Sự kiện', 'tc-eyebrow'), node('h2', event
                        .title), node('p', date(event.startTime) + ' · ' + event.venueName), node(
                        'strong', price(event.minPrice)));
                    slide.append(img, caption);
                    track.append(slide);
                    slides.push(slide);
                    const dot = node('button');
                    dot.type = 'button';
                    dot.setAttribute('aria-label', 'Xem ' + event.title);
                    dot.addEventListener('click', () => {
                        moveSlide(i);
                        startBanner();
                    });
                    dots.append(dot);
                }
                get('catalog-featured').hidden = !slides.length;
                moveSlide(0);
                startBanner();
                for (const id of ['hero-previous', 'hero-next', 'hero-pause']) get(id).hidden = slides.length <
                    2;
                get('hero-pause').textContent = paused ? '▶' : 'Ⅱ';
                get('hero-pause').setAttribute('aria-label', paused ? 'Tiếp tục banner' : 'Tạm dừng banner');
            } catch (_) {
                get('catalog-featured').hidden = true;
            }
        }
        get('hero-previous').addEventListener('click', () => {
            moveSlide(slideIndex - 1);
            startBanner();
        });
        get('hero-next').addEventListener('click', () => {
            moveSlide(slideIndex + 1);
            startBanner();
        });
        get('hero-pause').addEventListener('click', () => {
            paused = !paused;
            get('hero-pause').textContent = paused ? '▶' : 'Ⅱ';
            get('hero-pause').setAttribute('aria-label', paused ? 'Tiếp tục banner' :
            'Tạm dừng banner');
            startBanner();
        });
        let categories = [];

        function restore() {
            const state = catalogState(root.location.search),
                search = new URLSearchParams(root.location.search);
            if (!state.categoryId && search.has('category')) state.categoryId = categories.find(category =>
                category.slug === search.get('category'))?.categoryId || '';
            pageNumber = state.page;
            for (const key of ['keyword', 'categoryId', 'sort', 'from', 'to']) form.elements.namedItem(key)
                .value = state[key];
        }

        function syncHeader() {
            const keyword = doc.querySelector('[data-site-header] input[name=keyword]');
            if (keyword) keyword.value = form.elements.namedItem('keyword').value;
            if (get('header-category')) get('header-category').value = get('category').value;
        }
        async function load(remember = true) {
            const current = ++revision;
            syncHeader();
            for (const tab of get('catalog-categories').children) {
                const active = tab.dataset.category === get('category').value;
                tab.classList.toggle('active', active);
                tab.setAttribute('aria-pressed', String(active));
            }
            get('page-label').replaceChildren();
            get('previous').disabled = get('next').disabled = true;
            cards.setAttribute('aria-busy', 'true');
            status.textContent = 'Đang tải sự kiện…';
            get('retry-events').hidden = true;
            try {
                const query = catalogQuery(new FormData(form), pageNumber);
                if (remember) {
                    const url = new URL(root.location.href),
                        historyQuery = new URLSearchParams(query);
                    historyQuery.delete('pageSize');
                    if (!form.elements.namedItem('from').value) historyQuery.delete('from');
                    url.search = historyQuery.toString();
                    if (url.href !== root.location.href) root.history.pushState({}, '', url);
                }
                const result = await api('/api/events?' + query);
                if (current !== revision) return;
                cards.replaceChildren(...result.data.map(eventCard));
                banner(result.data);
                status.textContent = result.total ? 'Tìm thấy ' + result.total + ' sự kiện.' :
                    'Không có sự kiện phù hợp. Thử đổi bộ lọc hoặc từ khóa.';
                if (!result.data.length) cards.append(node('p', 'Không có sự kiện trong khoảng đã chọn.',
                    'tc-empty-state'));
                root.TicketUI?.renderPagination(get('page-label'), {
                    page: pageNumber,
                    total: result.total,
                    pageSize: result.pageSize,
                    previous: get('previous'),
                    next: get('next'),
                    onPageChange: target => {
                        pageNumber = target;
                        void load();
                    }
                });
                get('previous').disabled = pageNumber <= 1;
                get('next').disabled = pageNumber * result.pageSize >= result.total;
            } catch (error) {
                if (current !== revision) return;
                cards.replaceChildren();
                get('catalog-featured').hidden = true;
                root.clearInterval(timer);
                status.textContent = error.message;
                get('retry-events').hidden = false;
                get('page-label').textContent = '';
            } finally {
                if (current === revision) cards.removeAttribute('aria-busy');
            }
        }
        form.addEventListener('submit', e => {
            e.preventDefault();
            pageNumber = 1;
            void load();
        });
        get('previous').addEventListener('click', () => {
            pageNumber--;
            void load();
        });
        get('next').addEventListener('click', () => {
            pageNumber++;
            void load();
        });
        get('retry-events').addEventListener('click', () => void load());
        root.addEventListener('popstate', () => {
            restore();
            void load(false);
        });
        try {
            categories = (await api('/api/event-categories')).data;
            const tabs = get('catalog-categories');

            function tab(text, value) {
                const button = node('button', text);
                button.type = 'button';
                button.dataset.category = value;
                button.addEventListener('click', () => {
                    get('category').value = value;
                    pageNumber = 1;
                    [...tabs.children].forEach(t => {
                        t.classList.toggle('active', t === button);
                        t.setAttribute('aria-pressed', String(t === button));
                    });
                    void load();
                });
                tabs.append(button);
            }
            tab('Tất cả', '');
            for (const category of categories) {
                const option = node('option', category.name);
                option.value = category.categoryId;
                get('category').append(option);
                tab(category.name, category.categoryId);
            }
        } catch (_) {
            get('category').disabled = true;
            get('category-help').textContent = 'Chưa tải được danh mục. Bạn vẫn có thể tìm bằng từ khóa.';
        }
        restore();
        for (const tab of get('catalog-categories').children) {
            tab.classList.toggle('active', tab.dataset.category === get('category').value);
            tab.setAttribute('aria-pressed', String(tab.dataset.category === get('category').value));
        }
        await load(false);
    }
    async function detailPage() {
        const id = eventId(root.location.search),
            status = get('status');
        if (!id) {
            status.textContent = 'Không tìm thấy mã sự kiện hợp lệ. Vui lòng chọn sự kiện từ danh sách.';
            get('event-retry').hidden = true;
            return;
        }
        try {
            const event = (await api('/api/events/' + encodeURIComponent(id))).data;
            doc.title = event.title + ' | TicketsCenter';
            get('title').textContent = event.title;
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
            get('event').hidden = false;
            get('event-breadcrumb').hidden = false;
            get('event-retry').hidden = true;
            status.textContent = '';
            get('event-hold-link').href = '#bookingSection';
            get('booking-login').href = base + '/auth/login?redirect=' + encodeURIComponent(root.location
                .pathname + root.location.search + '#bookingSection');
            get('sale-state').textContent = event.isSaleActive ? 'Đang mở bán' : 'Hiện chưa mở bán';
            const zones = (await api('/api/events/' + encodeURIComponent(id) + '/zones')).data;
            get('zones').replaceChildren();
            for (const zone of zones) {
                const panel = node('div', undefined, 'tc-zone-line');
                panel.append(node('strong', zone.name), node('span', price(zone.basePrice)), node('span',
                    'Còn ' + zone.availableSeats + ' / ' + zone.totalCapacity + ' vé', 'text-muted'));
                get('zones').append(panel);
            }
            get('zone-status').textContent = zones.length ? '' : 'Chưa có khu vực vé.';
        } catch (error) {
            status.textContent = error.message;
            get('event-retry').hidden = false;
        }
        get('event-retry').onclick = () => {
            get('event-retry').disabled = true;
            void detailPage().finally(() => {
                get('event-retry').disabled = false;
            });
        };
    }
    void(page === 'catalog' ? catalogPage() : detailPage());
})(typeof window !== 'undefined' ? window : globalThis);
