'use strict';
(() => {
    const dialog = document.querySelector('#dialog');
    if (!dialog) return;
    const details = document.querySelector('#details');

    const STATUS = {
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
    const statusText = status => STATUS[status] || status;

    function progressText({refund, tickets, attempts}) {
        const lines = ['Trạng thái: ' + statusText(refund.status)];
        if (refund.rejectionReason) lines.push('Lý do từ chối: ' + refund.rejectionReason);

        lines.push('', 'Vé:');
        for (const ticket of tickets) {
            lines.push(ticket.seatLabel + ' · ' + statusText(ticket.status));
        }

        lines.push('', 'Lịch sử chuyển tiền:');
        if (attempts.length === 0) lines.push('Chưa có lần chuyển tiền.');
        for (const attempt of attempts) {
            const when = TC.dateTime(attempt.createdAt);
            lines.push('Lần ' + attempt.attemptNo + ': ' + statusText(attempt.status) + ' · ' + when);
        }
        return lines.join('\n');
    }

    async function showProgress(refundId) {
        details.textContent = 'Đang tải…';
        dialog.showModal();
        try {
            const data = await TC.api('/api/refund-requests/' + encodeURIComponent(refundId));
            details.textContent = progressText(data);
        } catch (error) {
            details.textContent = error.message || 'Không thể tải tiến độ.';
        }
    }

    document.querySelector('#close').addEventListener('click', () => dialog.close());
    for (const button of document.querySelectorAll('button[data-id]')) {
        button.addEventListener('click', () => showProgress(button.dataset.id));
    }
})();
