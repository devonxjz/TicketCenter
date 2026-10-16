(function(root) {
    'use strict';
    async function signOut(fetch, base) {
        const before = await fetch(base + '/api/auth/csrf', {
                credentials: 'same-origin',
                cache: 'no-store',
                headers: {
                    Accept: 'application/json'
                }
            }),
            csrf = await before.json();
        if (!before.ok || !csrf.data?.token) throw new Error(csrf.error?.message ||
            'Không thể xác nhận phiên đăng nhập.');
        const response = await fetch(base + '/api/auth/logout', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    Accept: 'application/json',
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': csrf.data.token
                },
                body: '{}'
            }),
            result = await response.json();
        if (!response.ok || !result.data?.loggedOut) throw new Error(result.error?.message ||
            'Chưa đăng xuất được. Vui lòng thử lại.');
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

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {
            signOut,
            buildSearchQuery,
            formatPrice,
            formatEventTime
        };
        return;
    }

    const doc = root.document,
        nav = doc.querySelector('[data-site-header]'),
        main = doc.querySelector('main');
    if (main && !main.id) main.id = 'main-content';
    if (!nav) return;

    const base = nav.dataset.context || '',
        category = doc.getElementById('header-category'),
        notice = doc.getElementById('site-notice'),
        query = new URLSearchParams(root.location.search),
        search = nav.querySelector('input[name=keyword]'),
        searchForm = nav.querySelector('form.tc-header-search'),
        dropdown = doc.getElementById('header-search-dropdown');

    if (search) search.value = query.get('keyword') || '';

    let searchRevision = 0,
        activeItemIndex = -1,
        searchTimer;

    function closeDropdown() {
        if (dropdown) {
            dropdown.hidden = true;
            activeItemIndex = -1;
        }
    }

    function syncCatalogFilters(catVal, kwVal) {
        if (doc.body.dataset.publicPage === 'catalog') {
            const filters = doc.getElementById('filters');
            if (filters) {
                if (typeof catVal === 'string' && filters.elements.namedItem('categoryId')) filters.elements
                    .namedItem('categoryId').value = catVal;
                if (typeof kwVal === 'string' && filters.elements.namedItem('keyword')) filters.elements.namedItem(
                    'keyword').value = kwVal;
                const tabs = doc.getElementById('catalog-categories');
                if (tabs && typeof catVal === 'string') {
                    for (const t of tabs.children) {
                        const active = t.dataset.category === catVal;
                        t.classList.toggle('active', active);
                        t.setAttribute('aria-pressed', String(active));
                    }
                }
                filters.dispatchEvent(new Event('submit', {
                    cancelable: true,
                    bubbles: true
                }));
            }
        }
    }

    function setActiveItem(index) {
        const items = dropdown?.querySelectorAll('.tc-search-dropdown-item');
        if (!items?.length) return;
        dropdown.querySelector('.tc-search-dropdown-item.active')?.classList.remove('active');
        activeItemIndex = (index + items.length) % items.length;
        const item = items[activeItemIndex];
        item.classList.add('active');
        item.scrollIntoView({
            block: 'nearest'
        });
    }

    function renderDropdown(title, count, events, selectedCat, keyword) {
        if (!dropdown) return;
        activeItemIndex = -1;
        const itemsHtml = (events && events.length) ? events.map((ev, i) => `
   <a class="tc-search-dropdown-item" href="${base}/event-detail?eventId=${encodeURIComponent(ev.eventId)}" data-index="${i}">
    <div class="tc-sdd-thumb-wrapper">
     ${ev.coverImageUrl?`<img class="tc-sdd-thumb" src="${ev.coverImageUrl}" alt="${ev.title||'Sự kiện'}" onerror="this.hidden=true;this.nextElementSibling.style.display='block'">`:''}
     <div class="tc-sdd-thumb-fallback" style="${ev.coverImageUrl?'display:none':'display:block'}"><i class="fa-solid fa-ticket"></i></div>
    </div>
    <div class="tc-sdd-info">
     <div class="tc-sdd-title">${ev.title||'Sự kiện'}</div>
     <div class="tc-sdd-meta">
      <span><i class="fa-regular fa-clock me-1 text-muted"></i>${formatEventTime(ev.startTime)||'Đang cập nhật'}</span>
      <span><i class="fa-solid fa-location-dot me-1 text-muted"></i>${ev.venueName||'Chưa có địa điểm'}</span>
     </div>
     <div class="tc-sdd-bottom">
      <span class="tc-sdd-badge">${ev.category||'Sự kiện'}</span>
      <span class="tc-sdd-price">${formatPrice(ev.minPrice)}</span>
     </div>
    </div>
   </a>`).join('') :
            '<div class="tc-search-dropdown-empty text-center py-4 text-muted"><i class="fa-regular fa-calendar-xmark fs-4 d-block mb-2 text-secondary"></i><span>Không tìm thấy sự kiện nào phù hợp.</span></div>';

        dropdown.innerHTML = `
   <div class="tc-search-dropdown-header">
    <span class="tc-search-dropdown-title"><i class="fa-solid fa-magnifying-glass text-danger me-1"></i>${title}</span>
    <span class="tc-search-dropdown-count badge bg-light text-dark border">${count} sự kiện</span>
   </div>
   <div class="tc-search-dropdown-list" role="listbox">${itemsHtml}</div>
   <div class="tc-search-dropdown-footer">
    <button type="button" class="tc-search-dropdown-view-all">Xem tất cả kết quả trên trang sự kiện <i class="fa-solid fa-arrow-right ms-1"></i></button>
   </div>`;

        dropdown.querySelector('.tc-search-dropdown-view-all')?.addEventListener('click', e => {
            e.preventDefault();
            closeDropdown();
            if (doc.body.dataset.publicPage === 'catalog') {
                syncCatalogFilters(selectedCat, keyword);
                doc.getElementById('events')?.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
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

    async function fetchAndShowEvents(catVal, kwVal) {
        if (!dropdown) return;
        const cat = (catVal || '').trim(),
            kw = (kwVal || '').trim();
        if (!cat && !kw) {
            closeDropdown();
            return;
        }

        dropdown.hidden = false;
        dropdown.innerHTML =
            '<div class="tc-search-dropdown-status text-center py-4 text-muted"><div class="spinner-border spinner-border-sm text-danger me-2" role="status"></div><span>Đang tìm kiếm sự kiện phù hợp…</span></div>';

        const rev = ++searchRevision;
        const q = buildSearchQuery({
            categoryId: cat,
            keyword: kw,
            pageSize: 6
        });

        try {
            const response = await root.fetch(base + '/api/events?' + q.toString(), {
                credentials: 'same-origin',
                cache: 'no-store',
                headers: {
                    Accept: 'application/json'
                }
            });
            const payload = await response.json();
            if (rev !== searchRevision) return;
            if (!response.ok) throw new Error('Không thể tải sự kiện');

            const catName = category?.selectedIndex > 0 ? category.options[category.selectedIndex].text : '';
            let title = 'Sự kiện liên quan';
            if (catName && kw) title = 'Sự kiện "' + kw + '" thuộc ' + catName;
            else if (catName) title = 'Sự kiện danh mục: ' + catName;
            else if (kw) title = 'Kết quả tìm kiếm cho: "' + kw + '"';

            renderDropdown(title, payload.total || (payload.data ? payload.data.length : 0), payload.data || [],
                cat, kw);
        } catch (_) {
            if (rev !== searchRevision) return;
            dropdown.innerHTML =
                '<div class="tc-search-dropdown-empty text-center py-3 text-muted"><i class="fa-solid fa-triangle-exclamation text-warning me-2"></i><span>Không thể tải sự kiện lúc này. Vui lòng thử lại.</span></div>';
        }
    }

    function debouncedFetch(cat, kw) {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => fetchAndShowEvents(cat, kw), 240);
    }

    if (category) {
        root.fetch(base + '/api/event-categories', {
            credentials: 'same-origin',
            cache: 'no-store',
            headers: {
                Accept: 'application/json'
            }
        }).then(async response => {
            const payload = await response.json();
            if (!response.ok) throw new Error('Danh mục chưa khả dụng');
            for (const value of payload.data) {
                const option = doc.createElement('option');
                option.value = value.categoryId;
                option.textContent = value.name;
                category.append(option);
            }
            category.value = doc.getElementById('category')?.value || new URLSearchParams(root.location
                .search).get('categoryId') || '';
        }).catch(() => {
            category.disabled = true;
            category.setAttribute('title', 'Chưa tải được danh mục. Bạn vẫn có thể tìm theo từ khóa.');
        });

        category.addEventListener('change', () => {
            const catVal = category.value,
                kwVal = (search?.value || '').trim();
            fetchAndShowEvents(catVal, kwVal);
            syncCatalogFilters(catVal, kwVal);
        });
    }

    if (search) {
        search.addEventListener('input', () => {
            debouncedFetch(category?.value || '', search.value.trim());
        });
        search.addEventListener('focus', () => {
            const catVal = category?.value || '',
                kwVal = (search.value || '').trim();
            if (catVal || kwVal) fetchAndShowEvents(catVal, kwVal);
        });
        search.addEventListener('keydown', e => {
            if (dropdown && !dropdown.hidden) {
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
            }
        });
    }

    doc.addEventListener('click', event => {
        if (searchForm && !searchForm.contains(event.target)) closeDropdown();
    });

    const logout = doc.getElementById('site-logout');
    if (logout) logout.addEventListener('click', async () => {
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

    if (searchForm) {
        searchForm.addEventListener('submit', event => {
            closeDropdown();
            if (doc.body.dataset.publicPage === 'catalog') {
                const filters = doc.getElementById('filters');
                if (filters) {
                    event.preventDefault();
                    const kw = (searchForm.querySelector('input[name=keyword]')?.value || '').trim(),
                        cat = searchForm.querySelector('select[name=categoryId]')?.value || '';
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
                    filters.dispatchEvent(new Event('submit', {
                        cancelable: true,
                        bubbles: true
                    }));
                    doc.getElementById('events')?.scrollIntoView({
                        behavior: 'smooth',
                        block: 'start'
                    });
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

// Hiển thị <time class="tc-local-time" datetime="ISO-8601"> theo giờ địa phương của người xem.
(function() {
    if (typeof document === "undefined") return;
    const format = () => {
        for (const node of document.querySelectorAll("time.tc-local-time[datetime]")) {
            const value = new Date(node.getAttribute("datetime"));
            if (!Number.isNaN(value.getTime())) node.textContent = value.toLocaleString("vi-VN", {
                dateStyle: "medium",
                timeStyle: "short"
            });
        }
    };
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", format);
    else format();
})();
