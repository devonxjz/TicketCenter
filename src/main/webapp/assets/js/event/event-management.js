'use strict';
/**
 * Event management and review (organiser and admin).
 * Laid out in order:
 * 1. Root Guard & Config
 * 2. Shared State, Constants & DOM Utilities
 * 3. Network & Mutation Engine (CSRF, Idempotency, Upload)
 * 4. Component Renderers & Page Controllers (List, Editor, Review)
 * 5. Event Binding & Page Dispatch
 */
(() => {
    // 1. ROOT GUARD & CONFIG
    const page = document.body.dataset.dongPage;
    if (!page) return;

    const base = document.body.dataset.contextPath ?? document.body.dataset.context ?? '';
    const $ = id => document.getElementById(id);

    // 2. SHARED STATE, CONSTANTS & DOM UTILITIES
    const labels = {
        DRAFT: 'Bản nháp',
        PENDING_APPROVAL: 'Chờ duyệt',
        REJECTED: 'Bị từ chối',
        PUBLISHED: 'Đã công khai',
        CANCELLED: 'Đã hủy'
    };

    const node = (tag, text = '', css = '') => {
        const el = document.createElement(tag);
        if (text) el.textContent = text;
        if (css) el.className = css;
        return el;
    };

    const lock = (element, disabled) => {
        if (!element) return;
        element.disabled = disabled;
        element.dataset.locked = String(disabled);
    };

    const money = value => BigInt(value || '0').toLocaleString('vi-VN') + ' đ';
    const date = value => value ? new Date(value).toLocaleString('vi-VN') : '';

    function localTime(value) {
        if (!value) return '';
        const d = new Date(value);
        return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 23);
    }

    function message(text, bad = false) {
        const el = $('status');
        if (!el) return;
        el.textContent = text;
        el.className = bad ? 'alert alert-danger' : 'text-muted';
    }

    function image(url, target) {
        target.hidden = true;
        if (!url) return;
        try {
            const parsed = new URL(url, location.origin);
            if (['http:', 'https:'].includes(parsed.protocol) && !parsed.username) {
                target.src = parsed.href;
                target.hidden = false;
            }
        } catch (_) {}
    }

    function button(text, work, css = 'btn btn-outline-dark btn-sm') {
        const b = node('button', text, css);
        b.type = 'button';
        b.addEventListener('click', () => action(b, work));
        return b;
    }

    // 3. NETWORK & MUTATION ENGINE
    async function api(path, options = {}) {
        const response = await fetch(base + path, {
            ...options,
            credentials: 'same-origin',
            headers: { Accept: 'application/json', ...(options.headers || {}) }
        });
        let result;
        try {
            result = await response.json();
        } catch (_) {
            throw new Error('Không thể đọc phản hồi máy chủ. Vui lòng thử lại.');
        }
        if (!response.ok) {
            const error = new Error(result.error?.message || 'Không thể xử lý yêu cầu.');
            error.status = response.status;
            error.code = result.error?.code;
            throw error;
        }
        return result;
    }

    async function token() {
        return (await api('/api/auth/csrf')).data.token;
    }

    async function intent(path, content) {
        const bytes = new TextEncoder().encode(path + '|' + content);
        const digest = await crypto.subtle.digest('SHA-256', bytes);
        const name = 'tc:dong:intent:' + Array.from(new Uint8Array(digest)).map(x => x.toString(16).padStart(2, '0')).join('');
        let key;
        try { key = sessionStorage.getItem(name); } catch (_) {}
        if (!key) {
            key = crypto.randomUUID();
            try { sessionStorage.setItem(name, key); } catch (_) {}
        }
        return {
            key,
            clear() {
                try { sessionStorage.removeItem(name); } catch (_) {}
            }
        };
    }

    async function mutate(path, payload = {}) {
        const body = JSON.stringify(payload);
        const operation = await intent(path, body);
        try {
            const result = await api(path, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': await token(),
                    'Idempotency-Key': operation.key
                },
                body
            });
            operation.clear();
            return result.data;
        } catch (e) {
            if ([400, 403, 404, 415].includes(e.status) || e.code === 'REQUEST_ABORTED') operation.clear();
            throw e;
        }
    }

    async function upload(path, file, version) {
        if (!file || file.size > 5242880) throw new Error('Chọn ảnh PNG/JPEG/WEBP tối đa 5 MB.');
        const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
        const operation = await intent(path, String(version) + '|' + Array.from(new Uint8Array(digest)).map(x => x.toString(16).padStart(2, '0')).join(''));
        const csrf = await token();
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            xhr.open('POST', base + path);
            xhr.setRequestHeader('X-CSRF-Token', csrf);
            xhr.setRequestHeader('Idempotency-Key', operation.key);
            xhr.upload.onprogress = e => {
                if (e.lengthComputable && $('upload-progress')) {
                    $('upload-progress').value = Math.round(e.loaded / e.total * 100);
                }
            };
            xhr.onerror = () => reject(new Error('Mất kết nối tải ảnh. Vui lòng thử lại.'));
            xhr.onload = () => {
                try {
                    const result = JSON.parse(xhr.responseText);
                    if (xhr.status >= 200 && xhr.status < 300) {
                        operation.clear();
                        resolve(result.data);
                    } else {
                        if ([400, 403, 404, 415].includes(xhr.status) || result.error?.code === 'REQUEST_ABORTED') {
                            operation.clear();
                        }
                        reject(new Error(result.error?.message || 'Không thể tải ảnh.'));
                    }
                } catch (_) {
                    reject(new Error('Phản hồi ảnh không hợp lệ.'));
                }
            };
            const data = new FormData();
            data.append('file', file);
            data.append('expectedVersion', String(version));
            xhr.send(data);
        });
    }

    async function action(btn, work) {
        const before = btn.disabled;
        btn.disabled = true;
        try {
            await work();
        } catch (e) {
            message(e.message, true);
        } finally {
            btn.disabled = btn.dataset.locked === undefined ? before : btn.dataset.locked === 'true';
        }
    }

    async function fetchOrganizations(select, preferred) {
        const items = (await api('/api/event-organizations')).data;
        select.replaceChildren();
        for (const item of items) {
            const option = node('option', item.name);
            option.value = item.organizationId;
            select.append(option);
        }
        if (preferred && items.some(item => item.organizationId.toLowerCase() === preferred.toLowerCase())) {
            select.value = preferred.toLowerCase();
        }
        if (!items.length) throw new Error('Bạn chưa quản lý tổ chức được phê duyệt nào.');
        if (preferred && !items.some(item => item.organizationId.toLowerCase() === preferred.toLowerCase())) {
            throw new Error('Bạn không có quyền với tổ chức trong đường dẫn.');
        }
        return select.value;
    }

    // 4. COMPONENT RENDERERS
    function renderZones(items, target, event, refresh, editing) {
        target.replaceChildren();
        for (const zone of items) {
            const panel = node('section', '', editing ? 'organizer-zone' : 'border rounded p-3 mb-3');
            panel.append(
                node('h3', zone.name + ' · ' + (zone.zoneType === 'SEATED' ? 'Khu ngồi' : 'Khu đứng'), 'h5'),
                node('p', money(zone.basePrice) + ' · Còn ' + zone.availableSeats + ' / ' + zone.totalCapacity + ' vé', 'text-muted')
            );

            const seatRows = node('div', '', 'd-grid gap-2');
            const rows = new Map();
            for (const seat of zone.seats || []) {
                if (!rows.has(seat.rowName)) {
                    const row = node('div', '', 'd-flex flex-wrap gap-2 align-items-center');
                    row.append(node('strong', 'Hàng ' + seat.rowName, 'small me-2'));
                    rows.set(seat.rowName, row);
                    seatRows.append(row);
                }
                const state = seat.status === 'AVAILABLE' ? 'Trống' : seat.status === 'HELD' ? 'Đang giữ' : 'Đã bán';
                const badgeColor = seat.status === 'AVAILABLE' ? 'bg-success' : seat.status === 'HELD' ? 'bg-warning text-dark' : 'bg-secondary';
                rows.get(seat.rowName).append(node('span', seat.seatLabel + ' · ' + state, 'badge p-2 ' + badgeColor));
            }

            if (editing && ['DRAFT', 'REJECTED', 'PUBLISHED'].includes(event.status)) {
                const price = node('input');
                price.type = 'text';
                price.inputMode = 'numeric';
                price.value = zone.basePrice;
                price.className = 'form-control';
                price.setAttribute('aria-label', 'Giá VND của ' + zone.name);

                const controls = node('div', '', 'd-flex flex-wrap gap-2 mb-3');
                controls.append(price, button('Lưu giá', async () => {
                    await mutate('/api/zones/' + zone.zoneId + '/edit', {
                        basePrice: price.value,
                        expectedVersion: String(event.version)
                    });
                    await refresh();
                }));

                if (['DRAFT', 'REJECTED'].includes(event.status)) {
                    if (zone.zoneType === 'STANDING') {
                        const capacity = node('input');
                        capacity.type = 'number';
                        capacity.min = '1';
                        capacity.max = '10000000';
                        capacity.value = String(zone.totalCapacity);
                        capacity.className = 'form-control';
                        capacity.setAttribute('aria-label', 'Sức chứa ' + zone.name);
                        controls.append(capacity, button('Lưu sức chứa', async () => {
                            await mutate('/api/zones/' + zone.zoneId + '/edit', {
                                basePrice: price.value,
                                capacity: capacity.value,
                                expectedVersion: String(event.version)
                            });
                            await refresh();
                        }));
                    } else {
                        const rowCount = node('input');
                        const per = node('input');
                        for (const input of [rowCount, per]) {
                            input.type = 'number';
                            input.min = '1';
                            input.max = '1000';
                            input.className = 'form-control';
                        }
                        rowCount.value = String(rows.size);
                        per.value = String(Math.max(0, ...(zone.seats || []).map(s => s.seatNumber)));
                        rowCount.setAttribute('aria-label', 'Số hàng ' + zone.name);
                        per.setAttribute('aria-label', 'Ghế mỗi hàng ' + zone.name);
                        controls.append(rowCount, per, button('Tạo lại ma trận', async () => {
                            if (!confirm('Tạo lại toàn bộ ghế chưa có giao dịch của khu này?')) return;
                            await mutate('/api/zones/' + zone.zoneId + '/edit', {
                                basePrice: price.value,
                                rows: rowCount.value,
                                seatsPerRow: per.value,
                                expectedVersion: String(event.version)
                            });
                            await refresh();
                        }));
                    }
                    controls.append(button('Xóa khu', async () => {
                        if (!confirm('Xóa khu vực này?')) return;
                        await mutate('/api/zones/' + zone.zoneId + '/delete', {
                            expectedVersion: String(event.version)
                        });
                        await refresh();
                    }, 'btn btn-outline-danger btn-sm'));
                }
                panel.append(controls);
            }

            panel.append(seatRows);
            target.append(panel);
        }
        if (!items.length) {
            target.append(node('p', 'Chưa có khu vực. Thêm khu ngồi hoặc khu đứng trước khi gửi duyệt.', 'text-muted'));
        }
    }

    // 5. PAGE CONTROLLERS

    // 5.1 organisation event list
    async function listPage() {
        let page = 1, revision = 0;
        await fetchOrganizations($('organization'), new URLSearchParams(location.search).get('organizationId'));

        async function load() {
            const current = ++revision;
            $('create-event').hidden = true;
            message('Đang tải sự kiện…');
            $('events').replaceChildren();
            $('page-label').replaceChildren();
            $('previous').disabled = $('next').disabled = true;

            const org = $('organization').value;
            const query = new URLSearchParams({ page: String(page), pageSize: '6' });
            if ($('filter-status').value) query.set('status', $('filter-status').value);

            const result = await api('/api/organizations/' + org + '/events?' + query);
            if (current !== revision) return;

            $('create-event').href = base + '/events/manage/editor?organizationId=' + org;
            $('create-event').hidden = false;

            for (const item of result.data) {
                const tr = document.createElement('tr');

                const coverCell = node('td');
                const thumbnail = node('img');
                thumbnail.alt = item.title;
                thumbnail.className = 'organizer-event-thumbnail';
                image(item.coverImageUrl, thumbnail);
                coverCell.append(thumbnail);
                if (thumbnail.hidden) coverCell.append(node('span', 'Chưa có ảnh', 'small text-muted'));

                const titleCell = node('td', '', 'organizer-event-title');
                titleCell.append(node('strong', item.title));

                const stateCell = node('td');
                const pillColor = { PUBLISHED: 'status-success', PENDING_APPROVAL: 'status-amber', REJECTED: 'status-red', CANCELLED: 'status-red' }[item.status] || 'status-subtle';
                stateCell.append(node('span', labels[item.status] || item.status, 'status-pill ' + pillColor));

                tr.append(coverCell, titleCell, stateCell, node('td', date(item.startTime)), node('td', String(item.totalCapacity)));

                const actionCell = node('td', '', 'organizer-event-actions');
                const actions = node('div');
                const link = node('a', 'Xem / sửa', 'btn btn-outline-dark btn-sm');
                link.href = base + '/events/manage/editor?organizationId=' + org + '&eventId=' + item.eventId;
                actions.append(link);

                if (['PUBLISHED', 'CANCELLED'].includes(item.status)) {
                    const cancelLink = node('a', item.status === 'CANCELLED' ? 'Tiến độ hủy' : 'Yêu cầu hủy', 'btn btn-outline-danger btn-sm');
                    cancelLink.href = base + '/events/cancellation?id=' + encodeURIComponent(item.eventId);
                    actions.append(cancelLink);
                }
                if (['DRAFT', 'REJECTED'].includes(item.status)) {
                    actions.append(button('Gửi duyệt', async () => {
                        await mutate('/api/events/' + item.eventId + '/submit', { expectedVersion: String(item.version) });
                        await load();
                    }));
                }
                if (item.status === 'DRAFT') {
                    actions.append(button('Xóa nháp', async () => {
                        if (!confirm('Xóa bản nháp này và các khu/ghế chưa có giao dịch?')) return;
                        await mutate('/api/events/' + item.eventId + '/delete', { expectedVersion: String(item.version) });
                        await load();
                    }, 'btn btn-outline-danger btn-sm'));
                }
                actionCell.append(actions);
                tr.append(actionCell);
                $('events').append(tr);
            }

            if (!result.data.length) {
                const empty = node('td', 'Chưa có sự kiện phù hợp. Tạo sự kiện mới hoặc thay đổi bộ lọc.', 'organizer-empty');
                empty.colSpan = 6;
                const row = node('tr');
                row.append(empty);
                $('events').append(row);
            }

            message(result.total ? 'Có ' + result.total + ' sự kiện.' : 'Chưa có sự kiện phù hợp.');
            globalThis.TicketUI?.renderPagination($('page-label'), {
                page,
                total: result.total,
                pageSize: result.pageSize,
                previous: $('previous'),
                next: $('next'),
                onPageChange: target => {
                    page = target;
                    load().catch(e => message(e.message, true));
                }
            });
            $('previous').disabled = page <= 1;
            $('next').disabled = page * result.pageSize >= result.total;
        }

        $('organization').addEventListener('change', () => { page = 1; load().catch(e => message(e.message, true)); });
        $('filters').addEventListener('submit', e => { e.preventDefault(); page = 1; load().catch(e => message(e.message, true)); });
        $('previous').addEventListener('click', () => { page--; load().catch(e => message(e.message, true)); });
        $('next').addEventListener('click', () => { page++; load().catch(e => message(e.message, true)); });
        await load();
    }

    // 5.2 event editor
    async function editorPage() {
        const params = new URLSearchParams(location.search);
        let eventId = params.get('eventId') || params.get('id');
        let currentEvent = null;

        await fetchOrganizations($('organization'), params.get('organizationId'));
        for (const cat of (await api('/api/event-categories')).data) {
            const opt = node('option', cat.name);
            opt.value = cat.categoryId;
            $('categoryId').append(opt);
        }

        async function load() {
            if (!eventId) {
                message('Điền hồ sơ và lưu nháp để thêm ảnh, khu và ghế.');
                $('inventory').hidden = $('media').hidden = true;
                lock($('save-event'), false);
                return;
            }
            currentEvent = (await api('/api/organizations/' + $('organization').value + '/events/' + eventId)).data;
            $('organization').value = currentEvent.organizationId;
            $('organization').disabled = true;

            for (const f of ['title', 'description', 'categoryId', 'venueName', 'venueAddress']) $(f).value = currentEvent[f] || '';
            for (const f of ['saleStartTime', 'saleEndTime', 'startTime', 'endTime']) $(f).value = localTime(currentEvent[f]);

            const editable = ['DRAFT', 'REJECTED'].includes(currentEvent.status);
            const published = currentEvent.status === 'PUBLISHED';

            $('draft-state').textContent = labels[currentEvent.status] || currentEvent.status;
            $('rejection').textContent = currentEvent.rejectionReason || '';

            for (const input of $('event-form').querySelectorAll('input,textarea,select')) {
                if (input.id !== 'organization') input.disabled = !(editable || (published && input.id === 'description'));
            }
            lock($('save-event'), !(editable || published));
            $('submit-event').hidden = !editable;
            $('delete-event').hidden = currentEvent.status !== 'DRAFT';
            $('inventory').hidden = $('media').hidden = false;

            image(currentEvent.coverImageUrl, $('cover-preview'));
            for (const input of $('zone-form').querySelectorAll('input,select,button')) lock(input, !editable);
            lock($('upload-cover'), !(editable || published));
            $('cover-file').disabled = !(editable || published);

            renderZones((await api('/api/events/' + eventId + '/layout')).data, $('zones'), currentEvent, load, true);
            message('Hồ sơ và tồn kho đã được cập nhật.');
        }

        $('event-form').addEventListener('submit', e => {
            e.preventDefault();
            action($('save-event'), async () => {
                const payload = { description: $('description').value };
                if (currentEvent?.status !== 'PUBLISHED') {
                    for (const name of ['title', 'categoryId', 'venueName', 'venueAddress']) payload[name] = $(name).value;
                    for (const name of ['saleStartTime', 'saleEndTime', 'startTime', 'endTime']) {
                        const val = $(name).value;
                        if (!val) throw new Error('Cần đầy đủ lịch sự kiện.');
                        payload[name] = new Date(val).toISOString();
                    }
                    if (!(payload.saleStartTime < payload.saleEndTime && payload.saleEndTime <= payload.startTime && payload.startTime < payload.endTime)) {
                        throw new Error('Lịch phải thỏa mở bán < kết thúc bán ≤ bắt đầu < kết thúc sự kiện.');
                    }
                    if (!eventId && new Date(payload.saleStartTime) <= new Date()) throw new Error('Thời gian mở bán phải ở tương lai.');
                }
                if (currentEvent) payload.expectedVersion = String(currentEvent.version);

                if (!eventId) {
                    const path = '/api/organizations/' + $('organization').value + '/events';
                    const operation = await intent(path, JSON.stringify(payload));
                    const form = new FormData();
                    for (const [name, val] of Object.entries(payload)) {
                        form.append(name === 'saleStartTime' ? 'saleStart' : name === 'saleEndTime' ? 'saleEnd' : name, val);
                    }
                    try {
                        const res = (await api(path, {
                            method: 'POST',
                            headers: { 'X-CSRF-Token': await token(), 'Idempotency-Key': operation.key },
                            body: form
                        })).data;
                        operation.clear();
                        location.replace(base + '/events/manage/editor?organizationId=' + res.organizationId + '&eventId=' + (res.eventId || res.id));
                        return;
                    } catch (err) {
                        if ([400, 403, 404, 415].includes(err.status) || err.code === 'REQUEST_ABORTED') operation.clear();
                        throw err;
                    }
                }
                await mutate('/api/events/' + eventId + '/edit', payload);
                await load();
            });
        });

        $('submit-event').addEventListener('click', () => action($('submit-event'), async () => {
            if (!currentEvent.coverImageUrl) throw new Error('Thêm ảnh bìa trước khi gửi duyệt.');
            if (!currentEvent.totalCapacity) throw new Error('Thêm ít nhất một khu vé trước khi gửi duyệt.');
            await mutate('/api/events/' + eventId + '/submit', { expectedVersion: String(currentEvent.version) });
            await load();
        }));

        $('delete-event').addEventListener('click', () => action($('delete-event'), async () => {
            if (!confirm('Xóa bản nháp này?')) return;
            await mutate('/api/events/' + eventId + '/delete', { expectedVersion: String(currentEvent.version) });
            location.assign(base + '/events/manage?organizationId=' + currentEvent.organizationId);
        }));

        $('zoneType').addEventListener('change', () => {
            $('seat-fields').hidden = $('zoneType').value !== 'SEATED';
            $('standing-fields').hidden = $('zoneType').value !== 'STANDING';
        });

        $('zone-form').addEventListener('submit', e => {
            e.preventDefault();
            action($('add-zone'), async () => {
                const payload = {
                    name: $('zone-name').value,
                    zoneType: $('zoneType').value,
                    basePrice: $('zone-price').value,
                    expectedVersion: String(currentEvent.version)
                };
                if (payload.zoneType === 'SEATED') {
                    payload.rows = $('rows').value;
                    payload.seatsPerRow = $('seatsPerRow').value;
                } else {
                    payload.capacity = $('capacity').value;
                }
                await mutate('/api/events/' + eventId + '/zones', payload);
                await load();
            });
        });

        let previewUrl;
        $('cover-file').addEventListener('change', () => {
            if (previewUrl) URL.revokeObjectURL(previewUrl);
            const file = $('cover-file').files[0];
            if (file) {
                previewUrl = URL.createObjectURL(file);
                $('cover-preview').src = previewUrl;
                $('cover-preview').hidden = false;
            }
        });

        $('upload-cover').addEventListener('click', () => action($('upload-cover'), async () => {
            if ($('upload-progress')) $('upload-progress').value = 0;
            await upload('/api/events/' + eventId + '/cover', $('cover-file').files[0], currentEvent.version);
            await load();
            message('Ảnh mới đã được lưu.');
        }));

        await load();
    }

    // 5.3 admin event review
    async function reviewPage() {
        let page = 1, selected = null, reviewRevision = 0, listRevision = 0, reviewId = null, reviewOpener = null;

        async function review(id, opener) {
            const current = ++reviewRevision;
            selected = null;
            reviewId = id;
            if (opener) reviewOpener = opener;

            $('review').hidden = false;
            $('review').setAttribute('aria-busy', 'true');
            $('review-title').textContent = 'Hồ sơ sự kiện';
            $('review-notice').textContent = 'Đang tải hồ sơ…';
            $('review-notice').className = 'text-muted';
            $('review-retry').hidden = true;

            for (const f of ['review-summary', 'review-schedule', 'review-description', 'review-rejection', 'rule-note']) $(f).textContent = '';
            $('review-cover').hidden = true;
            $('review-zones').replaceChildren();
            $('review-content').hidden = true;

            $('review-title').focus({ preventScroll: true });
            $('review').scrollIntoView({
                behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
                block: 'start'
            });

            lock($('publish'), true);
            lock($('reject'), true);
            $('rule').replaceChildren();
            $('reason').value = '';

            let detail, layout, policy;
            try {
                [detail, layout, policy] = await Promise.all([
                    api('/api/admin/events/' + id),
                    api('/api/admin/events/' + id + '/layout'),
                    api('/api/admin/events/' + id + '/commission-rules')
                ]);
            } catch (e) {
                if (current === reviewRevision) {
                    $('review-notice').textContent = e.message;
                    $('review-notice').className = 'alert alert-danger';
                    $('review-retry').hidden = false;
                    $('review').setAttribute('aria-busy', 'false');
                }
                return;
            }

            if (current !== reviewRevision) return;
            selected = detail.data;
            $('review-title').textContent = selected.title;
            $('review-summary').textContent = selected.organizationName + ' · ' + labels[selected.status] + ' · ' + date(selected.startTime);
            $('review-schedule').textContent = selected.venueName + ' · ' + selected.venueAddress +
                '\nMở bán: ' + date(selected.saleStartTime) + ' – ' + date(selected.saleEndTime) +
                '\nDiễn ra: ' + date(selected.startTime) + ' – ' + date(selected.endTime) +
                '\nTổng sức chứa: ' + selected.totalCapacity + ' vé';
            $('review-description').textContent = selected.description;
            $('review-rejection').textContent = selected.rejectionReason || '';
            image(selected.coverImageUrl, $('review-cover'));
            renderZones(layout.data, $('review-zones'), selected, async () => {}, false);

            const rules = policy.data || [];
            for (const r of rules) {
                const opt = node('option', r.ratePercent + '% + ' + money(r.fixedFee) + ' · ' + date(r.effectiveTo));
                opt.value = r.commissionRuleId;
                $('rule').append(opt);
            }

            lock($('publish'), selected.status !== 'PENDING_APPROVAL' || !rules.length || !selected.coverImageUrl);
            lock($('reject'), selected.status !== 'PENDING_APPROVAL');
            $('rule-note').textContent = !selected.coverImageUrl
                ? 'Hồ sơ cần ảnh bìa trước khi công khai.'
                : rules.length
                    ? 'Chính sách có hiệu lực hiện tại và tại ngày sự kiện.'
                    : 'Chưa có chính sách phù hợp. Cần thiết lập hoa hồng trước khi công khai.';

            $('review-content').hidden = false;
            $('review-notice').textContent = '';
            $('review').setAttribute('aria-busy', 'false');
        }

        async function load() {
            const current = ++listRevision;
            message('Đang tải hồ sơ…');
            $('page-label').replaceChildren();
            $('previous').disabled = $('next').disabled = true;

            const query = new URLSearchParams({ status: $('filter-status').value, page: String(page), pageSize: '6' });
            const result = await api('/api/admin/events?' + query);
            if (current !== listRevision) return;

            $('events').replaceChildren();
            for (const item of result.data) {
                const tr = document.createElement('tr');
                tr.append(node('td', item.title), node('td', item.organizationName), node('td', labels[item.status]), node('td', date(item.startTime)));
                const td = node('td');
                const open = button('Xem hồ sơ', () => review(item.eventId, open));
                td.append(open);
                tr.append(td);
                $('events').append(tr);
            }

            message(result.total ? 'Có ' + result.total + ' hồ sơ.' : 'Chưa có hồ sơ phù hợp.');
            globalThis.TicketUI?.renderPagination($('page-label'), {
                page,
                total: result.total,
                pageSize: result.pageSize,
                previous: $('previous'),
                next: $('next'),
                onPageChange: target => {
                    page = target;
                    load().catch(e => message(e.message, true));
                }
            });
            $('previous').disabled = page <= 1;
            $('next').disabled = page * result.pageSize >= result.total;
        }

        $('filters').addEventListener('submit', e => { e.preventDefault(); page = 1; load().catch(e => message(e.message, true)); });
        $('previous').addEventListener('click', () => { page--; load().catch(e => message(e.message, true)); });
        $('next').addEventListener('click', () => { page++; load().catch(e => message(e.message, true)); });

        $('review-close').addEventListener('click', () => {
            reviewRevision++;
            selected = null;
            reviewId = null;
            $('review').hidden = true;
            $('review').setAttribute('aria-busy', 'false');
            lock($('publish'), true);
            lock($('reject'), true);
            if (reviewOpener?.isConnected) reviewOpener.focus();
            else $('filter-status').focus();
        });

        $('review-retry').addEventListener('click', () => action($('review-retry'), async () => {
            if (reviewId) await review(reviewId);
        }));

        $('publish').addEventListener('click', () => action($('publish'), async () => {
            const chosen = selected;
            if (!chosen) return;
            await mutate('/api/admin/events/' + chosen.eventId + '/publish', {
                commissionRuleId: $('rule').value,
                expectedVersion: String(chosen.version)
            });
            if (selected?.eventId === chosen.eventId) await review(chosen.eventId);
            await load();
        }));

        $('reject').addEventListener('click', () => action($('reject'), async () => {
            const chosen = selected;
            if (!chosen) return;
            await mutate('/api/admin/events/' + chosen.eventId + '/reject', {
                reason: $('reason').value,
                expectedVersion: String(chosen.version)
            });
            if (selected?.eventId === chosen.eventId) await review(chosen.eventId);
            await load();
        }));

        await load();
        const initialId = new URLSearchParams(location.search).get('eventId');
        if (initialId) await review(initialId);
    }

    // 6. PAGE DISPATCH
    ({
        list: listPage,
        editor: editorPage,
        review: reviewPage
    }[page] || (() => {}))().catch(e => message(e.message, true));
})();
