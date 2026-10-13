'use strict';
(() => {
    const $ = id => document.getElementById(id), base = document.body.dataset.context || '';
    if (!$('cancel-event')) return;
    const storage = 'tc:khanh06:pending-cancellation';
    let selected = null, pending = null, timer = null, revision = 0, busy = false;
    try { const saved = JSON.parse(sessionStorage.getItem(storage));
        if (saved && typeof saved.eventId === 'string' && typeof saved.reason === 'string' && typeof saved.key === 'string') pending = saved;
    } catch (_) { }
    const status = text => { $('cancellation-status').textContent = text; };
    function save() { try { if (pending) sessionStorage.setItem(storage, JSON.stringify(pending)); else sessionStorage.removeItem(storage); } catch (_) { } }
    function stop() { if (timer) clearTimeout(timer); timer = null; }
    function controls() {
        $('cancel-event').disabled = busy || !selected || (pending ? pending.eventId !== selected.eventId : selected.status !== 'PUBLISHED');
        $('cancel-event').textContent = pending && pending.eventId === selected?.eventId ? 'Kiểm tra lại lệnh hủy' : 'Hủy sự kiện';
        $('cancellation-progress').disabled = busy || !selected;
        $('confirm-cancellation').disabled = busy;
    }
    async function api(path, options = {}) {
        const response = await fetch(base + path, {...options, credentials: 'same-origin', headers: {Accept: 'application/json', ...options.headers}});
        let result; try { result = await response.json(); } catch (_) { throw new Error('Không đọc được phản hồi. Hãy kiểm tra lại lệnh đã gửi.'); }
        if (!response.ok) { const e = new Error(result.error?.message || 'Không thể xử lý hủy sự kiện.'); e.status = response.status; throw e; }
        return result.data;
    }
    async function progress() {
        if (!selected || document.hidden) return;
        const id = selected.eventId, current = revision;
        try {
            const result = await api('/api/admin/events/' + id + '/cancellation-progress');
            if (current !== revision) return;
            $('cancellation-progress-panel').hidden = false;
            const percent = Math.max(0, Math.min(100, Number(result.progressPercentage)));
            $('cancellation-bar').value = percent;
            $('cancellation-counts').textContent = `${result.processedOrders}/${result.totalOrders} đơn đã tạo hoặc tiếp nhận nghĩa vụ hoàn (${percent}%).`;
            $('cancellation-results').textContent = `Đã hoàn: ${result.successfulRefunds}; đang hoàn: ${result.pendingRefunds}; thất bại: ${result.failedRefunds}; cần đối soát: ${result.unknownRefunds}; vé đã check-in cần quản trị xử lý: ${result.usedTicketExceptions}.`;
            status(result.status === 'COMPLETED' ? 'Đã xử lý hết danh sách đơn. Kết quả chuyển tiền được cập nhật riêng bên dưới.' : 'Hệ thống đang xử lý từng lô đơn hàng.');
            stop();
            if (result.status !== 'COMPLETED' || result.pendingRefunds > 0) timer = setTimeout(progress, 5000);
        } catch (e) {
            if (current !== revision) return;
            status(e.status === 404 ? 'Sự kiện chưa có tác vụ hủy.' : e.message);
            stop();
            if (e.status === 401 || e.status === 403) { selected = null; controls(); }
        }
    }
    async function submit() {
        if (busy || !selected) return;
        if (!pending) {
            const reason = $('cancellation-reason').value.trim();
            if (reason.length < 10 || reason.length > 500) { $('cancellation-error').textContent = 'Nhập lý do từ 10 đến 500 ký tự.'; return; }
            pending = {eventId: selected.eventId, reason, key: crypto.randomUUID()}; save();
        }
        if (pending.eventId !== selected.eventId) return;
        const eventId = pending.eventId;
        busy = true; controls(); $('cancellation-error').textContent = ''; status('Đang gửi lệnh hủy…');
        try {
            const csrf = await api('/api/auth/csrf');
            await api('/api/admin/events/' + pending.eventId + '/cancel', {method: 'POST', headers: {'Content-Type': 'application/json', 'X-CSRF-Token': csrf.token, 'Idempotency-Key': pending.key}, body: JSON.stringify({reason: pending.reason})});
            pending = null; save(); $('cancellation-dialog').close();
            if (selected?.eventId === eventId) {
                selected.status = 'CANCELLED';
                document.dispatchEvent(new CustomEvent('tc:event-review-cancelled', {detail: {eventId}}));
                await progress();
            }
        } catch (e) {
            if ([400, 401, 403, 404, 409, 415].includes(e.status)) { pending = null; save(); }
            const message = pending ? 'Chưa xác định được kết quả. Bấm kiểm tra lại để gửi cùng lệnh, không đổi lý do.' : e.message;
            status(message); $('cancellation-error').textContent = message;
            $('cancellation-reason').readOnly = !!pending;
        } finally { busy = false; controls(); }
    }
    document.addEventListener('tc:event-review-selected', event => {
        revision++; stop(); selected = event.detail; $('cancellation-progress-panel').hidden = true;
        status(pending && pending.eventId !== selected?.eventId ? 'Còn một lệnh chưa rõ kết quả. Chọn lại sự kiện đã gửi để kiểm tra.' : 'Chọn xem tiến trình để kiểm tra trạng thái hủy.');
        controls(); if (selected?.status === 'CANCELLED') progress();
    });
    $('cancel-event').addEventListener('click', () => {
        if (pending) { submit(); return; }
        $('cancellation-reason').readOnly = false; $('cancellation-reason').value = ''; $('cancellation-error').textContent = '';
        $('cancellation-dialog').showModal(); $('cancellation-reason').focus();
    });
    $('cancellation-form').addEventListener('submit', event => { event.preventDefault(); submit(); });
    $('close-cancellation').addEventListener('click', () => { if (!busy) $('cancellation-dialog').close(); });
    $('cancellation-dialog').addEventListener('cancel', event => { if (busy) event.preventDefault(); });
    $('cancellation-progress').addEventListener('click', progress);
    document.addEventListener('visibilitychange', () => { stop(); if (!document.hidden && selected?.status === 'CANCELLED') progress(); });
    window.addEventListener('pagehide', stop);
    controls();
})();
