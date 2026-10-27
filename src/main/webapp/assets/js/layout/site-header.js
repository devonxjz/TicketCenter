(function(root) {
    'use strict';

    /**
     * Site Shell: Tìm kiếm toàn trang (an toàn XSS), đồng bộ bộ lọc catalog và xử lý đăng xuất.
     * Tư duy tuần tự: 1. Hằng số & Helpers -> 2. Trạng thái -> 3. Render -> 4. Hành động -> 5. Sự kiện
     */

    // 1. HẰNG SỐ & PURE HELPERS

    async function signOut(fetchFn, base) {
        const before = await fetchFn(base + '/api/auth/csrf', {
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { 'Accept': 'application/json' }
        });
        const csrf = await before.json();
        if (!before.ok || !csrf.data?.token) {
            throw new Error(csrf.error?.message || 'Không thể xác nhận phiên đăng nhập.');
        }

        const response = await fetchFn(base + '/api/auth/logout', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
                'X-CSRF-Token': csrf.data.token
            },
            body: '{}'
        });
        const result = await response.json();
        if (!response.ok || !result.data?.loggedOut) {
            throw new Error(result.error?.message || 'Chưa đăng xuất được. Vui lòng thử lại.');
        }
    }

    function buildSearchQuery(params) {
        const options = params || {};
        const query = new URLSearchParams({
            pageSize: String(options.pageSize || 6),
            sort: options.sort || 'startTime_asc'
        });
        const cat = String(options.categoryId || '').trim();
        const kw = String(options.keyword || '').trim();
        if (cat) query.set('categoryId', cat);
        if (kw) query.set('keyword', kw);
        return query;
    }

    function formatPrice(value) {
        if (value === null || value === undefined) return 'Chưa có giá vé';
        try {
            const num = BigInt(value);
            return num === 0n ? 'Miễn phí' : 'Từ ' + num.toLocaleString('vi-VN') + ' đ';
        } catch (_) {
            return 'Từ ' + value + ' đ';
        }
    }

    function formatEventTime(value) {
        if (!value) return '';
        const date = new Date(value);
        if (!Number.isFinite(date.getTime())) return '';
        return new Intl.DateTimeFormat('vi-VN', {
            dateStyle: 'medium',
            timeStyle: 'short',
            timeZone: 'Asia/Ho_Chi_Minh'
        }).format(date);
    }

    function escapeHtml(value) {
        return String(value === null || value === undefined ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // Chỉ chấp nhận http(s) hoặc đường dẫn nội bộ cùng domain; loại bỏ javascript:, data:
    function safeImageUrl(value) {
        const url = String(value || '').trim();
        return /^(https?:\/\/|\/(?!\/))/i.test(url) ? url : '';
    }

    function buildDropdownHtml(base, title, count, events) {
        let itemsHtml = '';
        if (events && events.length) {
            itemsHtml = events.map((ev, i) => {
                const cover = safeImageUrl(ev.coverImageUrl);
                const titleStr = escapeHtml(ev.title || 'Sự kiện');
                const timeStr = escapeHtml(formatEventTime(ev.startTime) || 'Đang cập nhật');
                const venueStr = escapeHtml(ev.venueName || 'Chưa có địa điểm');
                const catStr = escapeHtml(ev.category || 'Sự kiện');
                const priceStr = escapeHtml(formatPrice(ev.minPrice));

                return `
                <a class="tc-search-dropdown-item" href="${escapeHtml(base)}/event-detail?eventId=${encodeURIComponent(ev.eventId)}" data-index="${i}">
                    <div class="tc-sdd-thumb-wrapper">
                        ${cover ? `<img class="tc-sdd-thumb" src="${escapeHtml(cover)}" alt="${titleStr}">` : ''}
                        <div class="tc-sdd-thumb-fallback" style="${cover ? 'display:none' : 'display:block'}"><i class="fa-solid fa-ticket"></i></div>
                    </div>
                    <div class="tc-sdd-info">
                        <div class="tc-sdd-title">${titleStr}</div>
                        <div class="tc-sdd-meta">
                            <span><i class="fa-regular fa-clock me-1 text-muted"></i>${timeStr}</span>
                            <span><i class="fa-solid fa-location-dot me-1 text-muted"></i>${venueStr}</span>
                        </div>
                        <div class="tc-sdd-bottom">
                            <span class="tc-sdd-badge">${catStr}</span>
                            <span class="tc-sdd-price">${priceStr}</span>
                        </div>
                    </div>
                </a>`;
            }).join('');
        } else {
            itemsHtml = '<div class="tc-search-dropdown-empty text-center py-4 text-muted"><i class="fa-regular fa-calendar-xmark fs-4 d-block mb-2 text-secondary"></i><span>Không tìm thấy sự kiện nào phù hợp.</span></div>';
        }

        return `
            <div class="tc-search-dropdown-header">
                <span class="tc-search-dropdown-title"><i class="fa-solid fa-magnifying-glass text-danger me-1"></i>${escapeHtml(title)}</span>
                <span class="tc-search-dropdown-count badge bg-light text-dark border">${escapeHtml(count)} sự kiện</span>
            </div>
            <div class="tc-search-dropdown-list" role="listbox">${itemsHtml}</div>
            <div class="tc-search-dropdown-footer">
                <button type="button" class="tc-search-dropdown-view-all">Xem tất cả kết quả trên trang sự kiện <i class="fa-solid fa-arrow-right ms-1"></i></button>
            </div>
        `;
    }

    // Xuất cho môi trường Node test runner
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {
            signOut,
            buildSearchQuery,
            formatPrice,
            formatEventTime,
            escapeHtml,
            safeImageUrl,
            buildDropdownHtml
        };
        return;
    }

    // 2. KHỞI TẠO DOM GỐC & TRẠNG THÁI
    const doc = root.document;
    const nav = doc.querySelector('[data-site-header]');
    const main = doc.querySelector('main');
    if (main && !main.id) main.id = 'main-content';
    if (!nav) return;

    const base = nav.dataset.context || '';
    const category = doc.getElementById('header-category');
    const notice = doc.getElementById('site-notice');
    const query = new URLSearchParams(root.location.search);
    const search = nav.querySelector('input[name=keyword]');
    const searchForm = nav.querySelector('form.tc-header-search');
    const dropdown = doc.getElementById('header-search-dropdown');

    if (search) {
        search.value = query.get('keyword') || '';
    }

    let searchRevision = 0;
    let activeItemIndex = -1;
    let searchTimer = null;

    // 3. RENDER FUNCTIONS

    function closeDropdown() {
        if (dropdown) {
            dropdown.hidden = true;
            activeItemIndex = -1;
        }
    }

    function setActiveItem(index) {
        const items = dropdown?.querySelectorAll('.tc-search-dropdown-item');
        if (!items?.length) return;
        dropdown.querySelector('.tc-search-dropdown-item.active')?.classList.remove('active');
        activeItemIndex = (index + items.length) % items.length;
        const item = items[activeItemIndex];
        item.classList.add('active');
        item.scrollIntoView({ block: 'nearest' });
    }

    function renderDropdown(title, count, events, selectedCat, keyword) {
        if (!dropdown) return;
        activeItemIndex = -1;
        dropdown.innerHTML = buildDropdownHtml(base, title, count, events);

        dropdown.querySelectorAll('img.tc-sdd-thumb').forEach(img => {
            img.addEventListener('error', () => {
                img.hidden = true;
                if (img.nextElementSibling) img.nextElementSibling.style.display = 'block';
            }, { once: true });
        });

        dropdown.querySelector('.tc-search-dropdown-view-all')?.addEventListener('click', e => {
            e.preventDefault();
            closeDropdown();
            if (doc.body.dataset.publicPage === 'catalog') {
                syncCatalogFilters(selectedCat, keyword);
                doc.getElementById('events')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
            } else {
                const url = new URL(base + '/', root.location.origin);
                if (selectedCat) url.searchParams.set('categoryId', selectedCat);
                if (keyword) url.searchParams.set('keyword', keyword);
                root.location.assign(url.href);
            }
        });

        dropdown.querySelectorAll('.tc-search-dropdown-item').forEach((item, i) => {
            item.addEventListener('mouseenter', () => setActiveItem(i));
        });

        dropdown.hidden = false;
    }

    // 4. HÀNH ĐỘNG (ACTIONS)

    function syncCatalogFilters(catVal, kwVal) {
        if (doc.body.dataset.publicPage !== 'catalog') return;
        const filters = doc.getElementById('filters');
        if (!filters) return;

        if (typeof catVal === 'string' && filters.elements.namedItem('categoryId')) {
            filters.elements.namedItem('categoryId').value = catVal;
        }
        if (typeof kwVal === 'string' && filters.elements.namedItem('keyword')) {
            filters.elements.namedItem('keyword').value = kwVal;
        }

        const tabs = doc.getElementById('catalog-categories');
        if (tabs && typeof catVal === 'string') {
            for (const t of tabs.children) {
                const active = t.dataset.category === catVal;
                t.classList.toggle('active', active);
                t.setAttribute('aria-pressed', String(active));
            }
        }
        filters.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
    }

    async function fetchAndShowEvents(catVal, kwVal) {
        if (!dropdown) return;
        const cat = (catVal || '').trim();
        const kw = (kwVal || '').trim();
        if (!cat && !kw) {
            closeDropdown();
            return;
        }

        dropdown.hidden = false;
        dropdown.innerHTML = '<div class="tc-search-dropdown-status text-center py-4 text-muted"><div class="spinner-border spinner-border-sm text-danger me-2" role="status"></div><span>Đang tìm kiếm sự kiện phù hợp…</span></div>';

        const rev = ++searchRevision;
        const q = buildSearchQuery({ categoryId: cat, keyword: kw, pageSize: 6 });

        try {
            const response = await root.fetch(base + '/api/events?' + q.toString(), {
                credentials: 'same-origin',
                cache: 'no-store',
                headers: { 'Accept': 'application/json' }
            });
            const payload = await response.json();
            if (rev !== searchRevision) return;
            if (!response.ok) throw new Error('Không thể tải sự kiện');

            const catName = category?.selectedIndex > 0 ? category.options[category.selectedIndex].text : '';
            let title = 'Sự kiện liên quan';
            if (catName && kw) title = `Sự kiện "${kw}" thuộc ${catName}`;
            else if (catName) title = `Sự kiện danh mục: ${catName}`;
            else if (kw) title = `Kết quả tìm kiếm cho: "${kw}"`;

            renderDropdown(title, payload.total || (payload.data ? payload.data.length : 0), payload.data || [], cat, kw);
        } catch (_) {
            if (rev !== searchRevision) return;
            dropdown.innerHTML = '<div class="tc-search-dropdown-empty text-center py-3 text-muted"><i class="fa-solid fa-triangle-exclamation text-warning me-2"></i><span>Không thể tải sự kiện lúc này. Vui lòng thử lại.</span></div>';
        }
    }

    function debouncedFetch(cat, kw) {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => fetchAndShowEvents(cat, kw), 240);
    }

    // 5. GẮN SỰ KIỆN & KHỞI TẠO (EVENTS & INITIALIZATION)

    if (category) {
        root.fetch(base + '/api/event-categories', {
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { 'Accept': 'application/json' }
        }).then(async response => {
            const payload = await response.json();
            if (!response.ok) throw new Error('Danh mục chưa khả dụng');
            for (const value of payload.data) {
                const option = doc.createElement('option');
                option.value = value.categoryId;
                option.textContent = value.name;
                category.append(option);
            }
            category.value = doc.getElementById('category')?.value || new URLSearchParams(root.location.search).get('categoryId') || '';
        }).catch(() => {
            category.disabled = true;
            category.setAttribute('title', 'Chưa tải được danh mục. Bạn vẫn có thể tìm theo từ khóa.');
        });

        category.addEventListener('change', () => {
            const catVal = category.value;
            const kwVal = (search?.value || '').trim();
            fetchAndShowEvents(catVal, kwVal);
            syncCatalogFilters(catVal, kwVal);
        });
    }

    if (search) {
        search.addEventListener('input', () => {
            debouncedFetch(category?.value || '', search.value.trim());
        });
        search.addEventListener('focus', () => {
            const catVal = category?.value || '';
            const kwVal = (search.value || '').trim();
            if (catVal || kwVal) fetchAndShowEvents(catVal, kwVal);
        });
        search.addEventListener('keydown', e => {
            if (!dropdown || dropdown.hidden) return;
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                setActiveItem(activeItemIndex + 1);
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                setActiveItem(activeItemIndex - 1);
            } else if (e.key === 'Enter') {
                const items = dropdown.querySelectorAll('.tc-search-dropdown-item');
                if (activeItemIndex >= 0 && items[activeItemIndex]) {
                    e.preventDefault();
                    items[activeItemIndex].click();
                }
            } else if (e.key === 'Escape') {
                closeDropdown();
            }
        });
    }

    doc.addEventListener('click', event => {
        if (searchForm && !searchForm.contains(event.target)) closeDropdown();
    });

    const logout = doc.getElementById('site-logout');
    if (logout) {
        logout.addEventListener('click', async () => {
            logout.disabled = true;
            try {
                await signOut(root.fetch.bind(root), base);
                root.location.assign(base + '/');
            } catch (error) {
                logout.disabled = false;
                if (notice) {
                    notice.textContent = error.message || 'Chưa đăng xuất được. Vui lòng thử lại.';
                    notice.hidden = false;
                }
            }
        });
    }

    if (searchForm) {
        searchForm.addEventListener('submit', event => {
            closeDropdown();
            if (doc.body.dataset.publicPage === 'catalog') {
                const filters = doc.getElementById('filters');
                if (filters) {
                    event.preventDefault();
                    const kw = (searchForm.querySelector('input[name=keyword]')?.value || '').trim();
                    const cat = searchForm.querySelector('select[name=categoryId]')?.value || '';
                    const kwInput = filters.elements.namedItem('keyword');
                    if (kwInput) kwInput.value = kw;
                    const catSelect = filters.elements.namedItem('categoryId');
                    if (catSelect) catSelect.value = cat;
                    const tabs = doc.getElementById('catalog-categories');
                    if (tabs) {
                        for (const t of tabs.children) {
                            const active = t.dataset.category === cat;
                            t.classList.toggle('active', active);
                            t.setAttribute('aria-pressed', String(active));
                        }
                    }
                    filters.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
                    doc.getElementById('events')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
            }
        });
    }

    const mainKeyword = doc.getElementById('keyword');
    if (mainKeyword && search) {
        mainKeyword.addEventListener('input', () => {
            search.value = mainKeyword.value;
        });
    }
})(typeof window !== 'undefined' ? window : globalThis);

// Hiển thị <time class="tc-local-time" datetime="ISO-8601"> theo giờ địa phương của người xem
(function() {
    if (typeof document === 'undefined') return;
    const format = () => {
        for (const node of document.querySelectorAll('time.tc-local-time[datetime]')) {
            const value = new Date(node.getAttribute('datetime'));
            if (!Number.isNaN(value.getTime())) {
                node.textContent = value.toLocaleString('vi-VN', { dateStyle: 'medium', timeStyle: 'short' });
            }
        }
    };
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', format);
    } else {
        format();
    }
})();
