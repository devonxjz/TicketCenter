'use strict';
(() => {
    const app = document.querySelector('#app');
    const base = app.dataset.base;
    const order = document.querySelector('#order');
    const tickets = document.querySelector('#tickets');
    const reason = document.querySelector('#reason');
    const message = document.querySelector('#message');
    const submit = document.querySelector('#submit');
    const dialog = document.querySelector('#confirm');
    const totalLabel = document.querySelector('#total');
    let choices = [],
        busy = false,
        pending = null,
        key = null,
        previousBody = null,
        load = 0;

    const money = value => BigInt(value).toLocaleString('vi-VN') + ' ₫';
    const selected = () => choices.filter(ticket =>
        tickets.querySelector('input[data-id="' + ticket.ticketId + '"]')?.checked);

    function total() {
        const list = selected();
        totalLabel.textContent = 'Số tiền dự kiến: ' + money(list.reduce((sum, ticket) => sum + BigInt(ticket
            .paidAmount), 0n));
        submit.disabled = busy || list.length === 0;
    }

    async function api(url, options = {}) {
        const response = await fetch(base + url, {
            credentials: 'same-origin',
            ...options
        });
        const body = await response.json();
        if (!response.ok) throw new Error(body.error?.message || 'Không thể thực hiện yêu cầu.');
        return body.data;
    }

    function renderTicket(ticket) {
        const row = document.createElement('div');
        row.className = 'ticket';
        const label = document.createElement('label');
        const input = document.createElement('input');
        input.type = 'checkbox';
        input.dataset.id = ticket.ticketId;
        input.disabled = !ticket.isRefundable;
        input.addEventListener('change', total);
        const text = document.createElement('span');
        text.textContent = ticket.seatLabel + ' · ' + money(ticket.paidAmount);
        label.append(input, text);
        row.append(label);
        if (ticket.unrefundableReason) {
            const hint = document.createElement('p');
            hint.className = 'muted';
            hint.textContent = ticket.unrefundableReason;
            row.append(hint);
        }
        tickets.append(row);
    }

    order.addEventListener('change', async () => {
        const generation = ++load;
        tickets.replaceChildren();
        choices = [];
        total();
        message.textContent = '';
        if (!order.value) return;
        message.textContent = 'Đang tải vé…';
        try {
            const rows = await api('/api/orders/' + encodeURIComponent(order.value) +
                '/refundable-tickets');
            if (generation !== load) return;
            choices = rows;
            message.textContent = rows.length ? '' : 'Đơn hàng không có vé.';
            rows.forEach(renderTicket);
            total();
        } catch (error) {
            if (generation === load) message.textContent = error.message;
        }
    });

    document.querySelector('#form').addEventListener('submit', event => {
        event.preventDefault();
        if (busy || !selected().length) return;
        pending = {
            orderId: order.value,
            ticketIds: selected().map(ticket => ticket.ticketId),
            reason: reason.value.trim()
        };
        if ([...pending.reason].length < 10) {
            message.textContent = 'Lý do cần ít nhất 10 ký tự.';
            return;
        }
        const body = JSON.stringify(pending);
        // Reuse the key when retrying the same request after an uncertain response.
        if (body !== previousBody) {
            key = crypto.randomUUID();
            previousBody = body;
        }
        document.querySelector('#confirmation').textContent = 'Bạn chọn ' + pending.ticketIds.length +
            ' vé. ' + totalLabel.textContent;
        dialog.showModal();
    });

    document.querySelector('#cancel').onclick = () => dialog.close();
    document.querySelector('#send').onclick = async () => {
        dialog.close();
        busy = true;
        order.disabled = true;
        reason.disabled = true;
        total();
        message.textContent = 'Đang gửi yêu cầu…';
        try {
            const result = await api('/api/refund-requests', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': app.dataset.csrf,
                    'Idempotency-Key': key
                },
                body: JSON.stringify(pending)
            });
            message.textContent = result.message + '\nSố tiền: ' + money(result.totalRefundAmount);
            pending = null;
            key = null;
            previousBody = null;
            order.disabled = false;
            order.dispatchEvent(new Event('change'));
            location.assign(base + '/my-refunds');
        } catch (error) {
            message.textContent = error.message + ' Bạn có thể bấm gửi lại cùng yêu cầu.';
        } finally {
            busy = false;
            order.disabled = false;
            reason.disabled = false;
            total();
        }
    };

    if (app.dataset.order && [...order.options].some(option => option.value === app.dataset.order)) {
        order.value = app.dataset.order;
        order.dispatchEvent(new Event('change'));
    }
})();
