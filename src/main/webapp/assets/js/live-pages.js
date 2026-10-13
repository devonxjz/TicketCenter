/* Pages rendered only from server data: account profile, organizer overview and organizer coupons. */
(() => {
    const body = document.body, page = body.dataset.livePage, base = body.dataset.context || '';
    if (!page) return;
    const $ = id => document.getElementById(id);
    const node = (tag, text = '', cls = '') => { const e = document.createElement(tag); e.textContent = text; if (cls) e.className = cls; return e; };
    const money = value => /^-?\d+$/.test(String(value)) ? BigInt(value).toLocaleString('vi-VN') + ' đ' : '—';
    const date = value => value ? new Date(value).toLocaleString('vi-VN') : '—';
    const eventStatus = {DRAFT:'Bản nháp', PENDING_APPROVAL:'Chờ duyệt', REJECTED:'Bị từ chối', PUBLISHED:'Đã công khai', CANCELLED:'Đã hủy'};
    const roles = {MANAGER:'Quản lý tổ chức', CHECK_IN_STAFF:'Nhân viên soát vé'};

    function message(text, bad = false) { const e = $('live-status'); e.textContent = text; e.className = bad ? 'alert alert-danger' : 'text-muted'; e.hidden = !text; }
    async function api(path, options = {}) {
        const response = await fetch(base + path, {...options, credentials:'same-origin', headers:{Accept:'application/json', ...(options.headers || {})}});
        if (response.status === 401) { location.href = base + '/auth/login?redirect=' + encodeURIComponent(location.pathname + location.search); throw new Error('Phiên đăng nhập đã hết hạn.'); }
        let result; try { result = await response.json(); } catch (_) { throw new Error('Không thể đọc phản hồi máy chủ.'); }
        if (!response.ok) throw new Error(result.error?.message || 'Không thể xử lý yêu cầu.');
        return result;
    }
    async function post(path, payload) {
        const token = (await api('/api/auth/csrf')).data.token;
        return api(path, {method:'POST', headers:{'Content-Type':'application/json', 'X-CSRF-Token':token, 'Idempotency-Key':crypto.randomUUID()}, body:JSON.stringify(payload || {})});
    }
    async function organizations(select) {
        const items = (await api('/api/event-organizations')).data, wanted = new URLSearchParams(location.search).get('organizationId');
        select.replaceChildren();
        for (const item of items) { const option = node('option', item.name); option.value = item.organizationId; select.append(option); }
        if (!items.length) throw new Error('Bạn chưa quản lý tổ chức được phê duyệt nào.');
        if (wanted) {
            const selected = items.find(item => item.organizationId.toLowerCase() === wanted.toLowerCase());
            if (!selected) throw new Error('Bạn không có quyền quản lý tổ chức này.');
            select.value = selected.organizationId;
        }
        return select;
    }
    function row(cells) { const tr = document.createElement('tr'); for (const cell of cells) tr.append(cell instanceof Node ? cell : node('td', String(cell))); return tr; }
    function empty(tbody, columns, text) { const td = node('td', text, 'text-center text-muted py-4'); td.colSpan = columns; const tr = document.createElement('tr'); tr.append(td); tbody.replaceChildren(tr); }

    async function profile() {
        const [user, details] = (await Promise.all([api('/api/me'), api('/api/me/profile')])).map(result => result.data);
        function show(data) {
            $('profile-name').textContent = data.fullName || data.userName;
            $('profile-initials').textContent = (data.fullName || data.userName || '?').trim().split(/\s+/).map(part => part[0]).slice(-2).join('').toUpperCase();
            $('profile-full-name').value = data.fullName || '';
            $('profile-phone').value = data.phone || '';
        }
        show(details);
        $('profile-email').textContent = details.email;
        $('profile-username').textContent = details.userName;
        $('profile-role').textContent = user.admin ? 'Quản trị viên' : 'Khách hàng';
        $('profile-form').addEventListener('submit', async event => {
            event.preventDefault(); $('profile-saved').textContent = '';
            try {
                    show((await post('/api/me/profile', {fullName:$('profile-full-name').value, phone:$('profile-phone').value})).data);
                    message(''); $('profile-saved').textContent = 'Đã lưu.';
            } catch (error) { message(error.message, true); }
        });
        const memberships = (await api('/api/me/memberships')).data, tbody = $('profile-memberships');
        if (!memberships.length) empty(tbody, 3, 'Bạn chưa thuộc tổ chức nào.');
        else tbody.replaceChildren(...memberships.map(item => row([item.organizationName, roles[item.role] || item.role, item.isActive ? 'Đang hoạt động' : 'Đã vô hiệu hóa'])));
        globalThis.TicketUI?.paginateItems(tbody,{pageSize:10,label:'Phân trang tổ chức của bạn'});
        message('');
    }

    async function dashboard() {
        const select = await organizations($('organization'));
        let revision=0;
        async function load() {
            const current=++revision;
            message('Đang tải số liệu…');
            for(const id of ['metric-events','metric-pending','metric-orders','metric-revenue','metric-cash'])$(id).textContent='—';
            $('org-name').textContent = select.selectedOptions[0].textContent; $('metric-asof').textContent = '';
            $('daily').replaceChildren();$('events').replaceChildren();$('org-chart')?.replaceChildren();
            $('all-events').hidden = $('create-event').hidden = true;
            const org = select.value;
            try {
                const [overview, events] = await Promise.all([
                    api('/api/organizations/' + org + '/overview'),
                    api('/api/organizations/' + org + '/events?page=1&pageSize=6')]);
                if(current!==revision)return;
                const data = overview.data;
                $('org-name').textContent = select.selectedOptions[0].textContent;
                $('metric-events').textContent = String(data.eventCount);
                $('metric-pending').textContent = String(data.pendingEvents);
                $('metric-orders').textContent = String(data.paidOrders);
                $('metric-revenue').textContent = money(data.cumulativeGrossRevenue);
                $('metric-cash').textContent = money(data.cumulativeNetCashFlow);
                $('metric-asof').textContent = 'Cập nhật lúc ' + date(data.asOf) + ' · Hôm nay (UTC): doanh thu ' + money(data.todayCohortGrossRevenue) + ', dòng tiền ròng ' + money(data.todayCashFlow?.netCashFlow);
                const days = $('daily'), list = data.dailyMetrics || [];
                if ($('org-chart')) globalThis.TicketUI?.renderChart($('org-chart'), list);
                if (!list.length) empty(days, 3, 'Chưa có số liệu.');
                else days.replaceChildren(...list.map(day => row([day.date, money(day.cohortGrossRevenue), money(day.netCashFlow)])));
                const tbody = $('events');
                if (!events.data.length) empty(tbody, 5, 'Tổ chức chưa có sự kiện nào.');
                else tbody.replaceChildren(...events.data.map(event => {
                    const actions = node('td'), link = node('a', 'Xem / sửa', 'btn btn-sm btn-outline-dark rounded-0');
                    link.href = base + '/events/manage/editor?organizationId=' + org + '&eventId=' + event.eventId; actions.append(link);
                    const title = node('td'); title.append(node('strong', event.title, 'd-block text-dark'));
                    const status = node('td'); status.append(node('span', eventStatus[event.status] || event.status, 'status-pill ' + ({PUBLISHED:'status-success', PENDING_APPROVAL:'status-amber', REJECTED:'status-red', CANCELLED:'status-red'}[event.status] || 'status-subtle')));
                    return row([title, date(event.startTime), String(event.totalCapacity), status, actions]);
                }));
                $('all-events').href = base + '/events/manage?organizationId=' + org;
                $('create-event').href = base + '/events/manage/editor?organizationId=' + org;
                $('all-events').hidden = $('create-event').hidden = false;
                message('');
            } catch (error) { if (current === revision) message(error.message, true); }
        }
        select.addEventListener('change', () => void load());
        await load();
    }

    async function coupons() {
        const select = await organizations($('organization')), tbody = $('coupons');
        let revision = 0;
        async function load() {
            const current = ++revision;
            const org = select.value;
            tbody.replaceChildren();globalThis.TicketUI?.paginateItems(tbody,{pageSize:10});
            try {
                message('Đang tải mã giảm giá…');
                const items = (await api('/api/organizations/' + org + '/coupons')).coupons;
                if (current !== revision) return;
                if (!items.length) empty(tbody, 7, 'Tổ chức chưa có mã giảm giá nào.');
                else tbody.replaceChildren(...items.map(coupon => {
                    const actions = node('td'), toggle = node('button', coupon.active ? 'Tạm dừng' : 'Kích hoạt', 'btn btn-sm btn-outline-dark rounded-0');
                    toggle.type = 'button';
                    toggle.addEventListener('click', async () => {
                        toggle.disabled = true;
                        try { await post('/api/coupons/' + coupon.id + (coupon.active ? '/deactivate' : '/activate')); await load(); }
                        catch (error) { if (select.value === org) message(error.message, true); toggle.disabled = false; }
                    });
                    actions.append(toggle);
                    const value = coupon.discountType === 'PERCENTAGE' ? coupon.percentageValue + '%' : money(coupon.fixedAmount);
                    const status = node('td'); status.append(node('span', coupon.active ? 'Hoạt động' : 'Tạm dừng', 'status-pill ' + (coupon.active ? 'status-success' : 'status-subtle')));
                    return row([node('td', coupon.code, 'font-monospace fw-bold'), value, coupon.consumedUses + ' / ' + coupon.maxUses,
                        String(coupon.availableUses), date(coupon.validFrom) + ' – ' + date(coupon.validTo), status, actions]);
                }));
                globalThis.TicketUI?.paginateItems(tbody,{pageSize:10,label:'Phân trang mã giảm giá'});
                message('');
            } catch (error) { if (current === revision) message(error.message, true); }
        }
        const form = $('coupon-form'), type = $('coupon-type');
        const sync = () => {
            const percentage = type.value === 'PERCENTAGE';
            $('coupon-percent-group').hidden = !percentage; $('coupon-fixed-group').hidden = percentage;
            $('coupon-percent').disabled = !percentage; $('coupon-percent').required = percentage;
            $('coupon-fixed').disabled = percentage; $('coupon-fixed').required = !percentage;
        };
        type.addEventListener('change', sync); sync();
        let submitting = false;
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (submitting) return;
            const org = select.value, button = form.querySelector('[type=submit]');
            submitting = true; if (button) button.disabled = true;
            try {
                const percentage = type.value === 'PERCENTAGE';
                const payload = {code:$('coupon-code').value, discountType:type.value, maxUses:Number($('coupon-max-uses').value),
                    percentageValue:percentage ? Number($('coupon-percent').value) : null, fixedAmount:percentage ? null : Number($('coupon-fixed').value),
                    validFrom:new Date($('coupon-from').value).toISOString(), validTo:new Date($('coupon-to').value).toISOString()};
                if (new Date(payload.validTo) <= new Date(payload.validFrom)) throw new Error('Ngày kết thúc phải sau ngày bắt đầu.');
                await post('/api/organizations/' + org + '/coupons', payload); form.reset(); sync(); await load();
            } catch (error) { if (select.value === org) message(error instanceof RangeError ? 'Vui lòng chọn ngày hợp lệ.' : error.message, true); }
            finally { submitting = false; if (button) button.disabled = false; }
        });
        select.addEventListener('change', () => void load());
        await load();
    }

    ({profile, 'organizer-dashboard':dashboard, 'organizer-coupons':coupons})[page]?.().catch(error => message(error.message, true));
})();
