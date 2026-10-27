'use strict';
/**
 * Quản lý & Xét duyệt yêu cầu hủy sự kiện (Manager Request & Admin Review).
 * Tuân thủ mô hình tư duy tuần tự:
 * 1. Root Guard & Config
 * 2. State & Constants
 * 3. Renderers & UI Sync
 * 4. Actions & API Flow (Load, Picker, Submit, Retry)
 * 5. Event Binding & Initialization
 */
(() => {
    // 1. ROOT GUARD & CONFIG
    const selectForm = document.getElementById('cancellation-select');
    if (!selectForm) return;

    const base = document.body.dataset.contextPath ?? document.body.dataset.context ?? '';
    const admin = document.body.dataset.cancellationAdmin === 'true';
    const prefix = admin ? '/api/admin/events/' : '/api/events/';
    const initialId = new URLSearchParams(location.search).get('id');
    const storageKey = 'tc:event-cancellation-review:pending';
    const pageSize = 6;
    const actions = ['request', 'approve', 'reject'];

    const $ = id => document.getElementById(id);

    // 2. STATE & CONSTANTS
    const requestStates = {
        NONE: 'Chưa có yêu cầu hủy',
        PENDING: 'Đang chờ duyệt',
        APPROVED: 'Đã duyệt hủy',
        REJECTED: 'Yêu cầu bị từ chối'
    };

    const eventStates = {
        PUBLISHED: 'Đã công khai',
        CANCELLED: 'Đã hủy',
        DRAFT: 'Bản nháp',
        PENDING_APPROVAL: 'Chờ duyệt',
        REJECTED: 'Bị từ chối'
    };

    const queueStates = {
        PENDING: 'Chờ duyệt',
        APPROVED: 'Đã duyệt',
        REJECTED: 'Từ chối'
    };

    let current = null;
    let busy = false;
    let pending = null;
    let revision = 0;
    let timer = null;
    let pickerPage = 1;
    let pickerRevision = 0;
    let pickerTotal = 0;

    try {
        const val = JSON.parse(sessionStorage.getItem(storageKey));
        if (val && typeof val.id === 'string' && typeof val.key === 'string' &&
            actions.includes(val.action) && typeof val.body === 'object') {
            pending = val;
        }
    } catch (_) {}

    function savePending() {
        try {
            if (pending) sessionStorage.setItem(storageKey, JSON.stringify(pending));
            else sessionStorage.removeItem(storageKey);
        } catch (_) {}
    }

    function stopTimer() {
        if (timer) clearTimeout(timer);
        timer = null;
    }

    // 3. RENDERERS & UI SYNC
    function message(text) {
        const el = $('cancellation-status');
        if (el) el.textContent = text;
    }

    function appendOption(select, value, text) {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = text;
        select.append(option);
    }

    function updatePagination() {
        window.TicketUI?.renderPagination($('cancellation-picker-page'), {
            page: pickerPage,
            total: pickerTotal,
            pageSize,
            previous: $('cancellation-picker-previous'),
            next: $('cancellation-picker-next'),
            disabled: busy,
            onPageChange: p => {
                pickerPage = p;
                picker();
            }
        });
    }

    function updateControls() {
        const waiting = current?.requestStatus === 'PENDING';
        const retry = Boolean(pending && current && pending.id === current.eventId);

        $('cancellation-request').hidden = !current || current.eventStatus !== 'PUBLISHED' || !['NONE', 'REJECTED'].includes(current.requestStatus);
        $('cancellation-approve').hidden = !admin || !waiting;
        $('cancellation-reject').hidden = !admin || !waiting;
        $('cancellation-retry').hidden = !retry;

        for (const act of actions) {
            $('cancellation-' + act).disabled = busy || Boolean(pending);
        }
        $('cancellation-retry').disabled = busy;
        $('cancellation-reason').readOnly = busy || Boolean(pending);
        $('cancellation-event-id').disabled = busy;
        $('cancellation-organization').disabled = busy;
        $('cancellation-picker-previous').disabled = busy || pickerPage <= 1;
        $('cancellation-picker-next').disabled = busy || pickerPage * pageSize >= pickerTotal;

        updatePagination();
        const submitBtn = $('cancellation-select')?.querySelector('button');
        if (submitBtn) submitBtn.disabled = busy;
    }

    function renderDetails(data) {
        current = data;
        $('cancellation-detail').hidden = false;
        $('cancellation-title').textContent = data.title;
        $('cancellation-state').textContent = `${requestStates[data.requestStatus] || data.requestStatus} · ` +
            (eventStates[data.eventStatus] || 'Đang đối chiếu');

        $('cancellation-request-reason').hidden = !data.reason;
        $('cancellation-request-reason').textContent = data.reason || '';
        $('cancellation-rejection').hidden = !data.rejectionReason;
        $('cancellation-rejection').textContent = `Lý do từ chối: ${data.rejectionReason || ''}`;

        $('cancellation-progress-panel').hidden = !data.progress;
        if (data.progress) {
            const p = data.progress;
            const total = Number(p.totalOrders);
            const processed = Number(p.processedOrders);
            $('cancellation-progress-bar').value = total === 0 ? 100 : (processed / total) * 100;
            $('cancellation-progress-counts').textContent = `${processed}/${total} đơn đã tạo hoặc tiếp nhận nghĩa vụ hoàn vé.`;
            $('cancellation-refund-counts').textContent =
                `Đã hoàn: ${p.successfulRefunds}; đang xử lý: ${p.pendingRefunds}; ` +
                `thất bại: ${p.failedRefunds}; cần đối soát: ${p.unknownRefunds}; ` +
                `vé đã check-in cần xem xét: ${p.usedTicketExceptions}.`;
        }
        updateControls();
    }

    // 4. ACTIONS & API FLOW
    async function api(path, options = {}, full = false) {
        const response = await fetch(base + path, {
            ...options,
            credentials: 'same-origin',
            headers: { Accept: 'application/json', ...options.headers }
        });
        let result;
        try {
            result = await response.json();
        } catch (_) {
            throw new Error('Không đọc được kết quả. Kiểm tra lại lệnh đã gửi.');
        }
        if (!response.ok) {
            const error = new Error(result.error?.message || 'Không thể xử lý yêu cầu hủy.');
            error.status = response.status;
            throw error;
        }
        return full ? result : result.data;
    }

    async function picker() {
        const rev = ++pickerRevision;
        $('cancellation-picker-previous').disabled = true;
        $('cancellation-picker-next').disabled = true;

        try {
            const path = admin
                ? '/api/admin/event-cancellations'
                : `/api/organizations/${$('cancellation-organization').value}/events`;
            const query = `?page=${pickerPage}&pageSize=${pageSize}` + (admin ? '' : '&status=PUBLISHED');
            const result = await api(path + query, {}, !admin);
            if (rev !== pickerRevision) return;

            const items = admin ? (result.items || []) : (result.data || []);
            const select = $('cancellation-event-id');
            pickerTotal = Number(result.total) || 0;
            select.replaceChildren();
            appendOption(select, '', admin ? 'Chọn yêu cầu hủy' : 'Chọn sự kiện');

            for (const item of items) {
                const state = item.requestStatus ? ' · ' + (queueStates[item.requestStatus] || item.requestStatus) : '';
                appendOption(select, item.eventId, item.title + state);
            }
            if (current) {
                if (!items.some(item => item.eventId === current.eventId)) {
                    appendOption(select, current.eventId, current.title);
                }
                select.value = current.eventId;
            }

            $('cancellation-picker-page').textContent = pickerTotal
                ? `Trang ${pickerPage} / ${Math.ceil(pickerTotal / pageSize)}`
                : 'Không có sự kiện';
            $('cancellation-picker-previous').disabled = pickerPage <= 1;
            $('cancellation-picker-next').disabled = pickerPage * pageSize >= pickerTotal;
            updatePagination();

            if (!items.length && !current) {
                message(admin ? 'Chưa có yêu cầu hủy nào.' : 'Tổ chức chưa có sự kiện đã công khai.');
            }
        } catch (error) {
            if (rev === pickerRevision) message(error.message);
        }
    }

    async function loadDetails(id, quiet = false) {
        stopTimer();
        const rev = ++revision;
        if (!quiet) message('Đang tải trạng thái từ hệ thống…');

        try {
            const data = await api(prefix + encodeURIComponent(id) + '/cancellation-request');
            if (rev !== revision) return false;
            renderDetails(data);
            if (!quiet) {
                message(pending
                    ? 'Có một lệnh chưa rõ kết quả. Chọn đúng sự kiện và kiểm tra lại lệnh đã gửi.'
                    : 'Đã tải trạng thái hiện tại.');
            }
            if (data.progress && (data.progress.status !== 'COMPLETED' || Number(data.progress.pendingRefunds) > 0) && !document.hidden) {
                timer = setTimeout(() => loadDetails(id, true), 5000);
            }
            return true;
        } catch (error) {
            if (rev !== revision) return false;
            message(error.message);
            current = null;
            $('cancellation-detail').hidden = true;
            updateControls();
            return false;
        }
    }

    async function submitAction(action) {
        if (busy || !current) return;
        if (!pending) {
            let body = {};
            if (action !== 'approve') {
                const reason = $('cancellation-reason').value.trim();
                if (reason.length < 10 || reason.length > 500) {
                    message('Nhập lý do từ 10 đến 500 ký tự.');
                    return;
                }
                body = { reason };
            }
            if (action === 'approve' && !window.confirm('Duyệt yêu cầu sẽ hủy sự kiện, dừng bán vé và chuẩn bị nghĩa vụ hoàn vé. Tiếp tục?')) {
                return;
            }
            pending = {
                id: current.eventId,
                key: crypto.randomUUID(),
                action,
                body
            };
            savePending();
        }

        if (pending.id !== current.eventId) {
            message('Chọn lại sự kiện có lệnh chưa rõ kết quả.');
            return;
        }

        busy = true;
        pickerRevision++;
        stopTimer();
        updateControls();
        message('Đang gửi yêu cầu…');

        const id = pending.id;
        try {
            const csrf = await api('/api/auth/csrf');
            const endpoint = pending.action === 'request' ? 'cancellation-request' : `cancellation-${pending.action}`;

            await api(prefix + id + '/' + endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': csrf.token,
                    'Idempotency-Key': pending.key
                },
                body: JSON.stringify(pending.body)
            });

            pending = null;
            savePending();
            $('cancellation-reason').value = '';
            const refreshed = await loadDetails(id);
            message(refreshed
                ? 'Đã ghi nhận yêu cầu. Trạng thái bên dưới được đọc từ hệ thống.'
                : 'Đã ghi nhận yêu cầu, chưa tải được trạng thái mới. ' + $('cancellation-status').textContent);
        } catch (error) {
            if ([400, 401, 403, 404, 409, 415].includes(error.status)) {
                pending = null;
                savePending();
                message(error.message);
            } else {
                message('Chưa xác định được kết quả. Kiểm tra lại lệnh đã gửi với cùng mã yêu cầu.');
            }
        } finally {
            busy = false;
            updateControls();
        }
    }

    // 5. EVENT BINDING & INITIALIZATION
    selectForm.addEventListener('submit', event => {
        event.preventDefault();
        if (busy) return;
        const id = $('cancellation-event-id').value.trim();
        if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) {
            message('Vui lòng chọn sự kiện trong danh sách.');
            return;
        }
        loadDetails(id);
    });

    $('cancellation-organization')?.addEventListener('change', () => {
        if (busy) return;
        pickerPage = 1;
        revision++;
        stopTimer();
        current = null;
        $('cancellation-detail').hidden = true;
        updateControls();
        picker();
    });

    $('cancellation-picker-previous')?.addEventListener('click', () => {
        if (!busy && pickerPage > 1) {
            pickerPage--;
            picker();
        }
    });

    $('cancellation-picker-next')?.addEventListener('click', () => {
        if (!busy && pickerPage * pageSize < pickerTotal) {
            pickerPage++;
            picker();
        }
    });

    for (const act of actions) {
        $('cancellation-' + act)?.addEventListener('click', () => submitAction(act));
    }
    $('cancellation-retry')?.addEventListener('click', () => submitAction(pending?.action));
    $('cancellation-action-form')?.addEventListener('submit', event => event.preventDefault());

    document.addEventListener('visibilitychange', () => {
        stopTimer();
        if (!document.hidden && current) loadDetails(current.eventId, true);
    });
    window.addEventListener('pagehide', stopTimer);

    async function initialize() {
        try {
            if (!admin) {
                const orgs = await api('/api/event-organizations');
                $('cancellation-organization-field').hidden = false;
                for (const org of orgs) {
                    appendOption($('cancellation-organization'), org.organizationId, org.name);
                }
                if (!orgs.length) {
                    message('Bạn chưa quản lý tổ chức được phê duyệt nào.');
                    return;
                }
            }
            const targetId = initialId || pending?.id;
            if (targetId) await loadDetails(targetId);
            await picker();
        } catch (error) {
            message(error.message);
        }
    }

    initialize();
    updateControls();
})();
