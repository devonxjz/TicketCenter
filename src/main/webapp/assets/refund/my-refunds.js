(() => {
    const dialog = document.querySelector('#dialog');
    const details = document.querySelector('#details');
    const base = document.querySelector('main').dataset.base;
    const states = {
        REQUESTED: 'Chờ duyệt',
        APPROVED: 'Đã duyệt, chờ chuyển tiền',
        PROCESSING: 'Đang chuyển tiền',
        COMPLETED: 'Đã hoàn tiền',
        REJECTED: 'Bị từ chối',
        RETRYABLE: 'Chuyển tiền thất bại, cần xử lý',
        NEEDS_RECONCILIATION: 'Đang xác minh giao dịch',
        ACTIVE: 'Có hiệu lực',
        USED: 'Đã sử dụng',
        REFUND_PENDING: 'Chờ hoàn tiền',
        REFUNDED: 'Đã hoàn tiền',
        INVALIDATED: 'Đã vô hiệu',
        PENDING: 'Đang chờ',
        SUCCEEDED: 'Chuyển tiền thành công',
        FAILED: 'Chuyển tiền thất bại',
        UNKNOWN: 'Đang xác minh giao dịch'
    };
    const statusText = status => states[status] || status;
    const localTime = value => {
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? value : date.toLocaleString('vi-VN', {
            dateStyle: 'medium',
            timeStyle: 'short'
        });
    };

    function progress(data) {
        const rejection = data.refund.rejectionReason ? 'Lý do từ chối: ' + data.refund.rejectionReason + '\n' : '';
        const tickets = data.tickets.map(ticket => ticket.seatLabel + ' · ' + statusText(ticket.status)).join('\n');
        const attempts = data.attempts.map(attempt =>
            'Lần ' + attempt.attemptNo + ': ' + statusText(attempt.status) + ' · ' + localTime(attempt
                .createdAt)).join('\n');
        return 'Trạng thái: ' + statusText(data.refund.status) + '\n' + rejection +
            '\nVé:\n' + tickets + '\n\nLịch sử chuyển tiền:\n' + (attempts || 'Chưa có lần chuyển tiền.');
    }

    document.querySelector('#close').onclick = () => dialog.close();
    document.querySelectorAll('button[data-id]').forEach(button => {
        button.onclick = async () => {
            details.textContent = 'Đang tải…';
            dialog.showModal();
            try {
                const response = await fetch(base + '/api/refund-requests/' + button.dataset.id, {
                    credentials: 'same-origin'
                });
                const body = await response.json();
                if (!response.ok) throw new Error(body.error?.message);
                details.textContent = progress(body.data);
            } catch (error) {
                details.textContent = error.message || 'Không thể tải tiến độ.';
            }
        };
    });
})();
