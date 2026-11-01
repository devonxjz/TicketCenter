'use strict';
(() => {
    const app = document.querySelector('#app');
    if (!app) return;
    const order = document.querySelector('#order');
    const tickets = document.querySelector('#tickets');
    const reason = document.querySelector('#reason');
    const message = document.querySelector('#message');
    const submit = document.querySelector('#submit');
    const totalLabel = document.querySelector('#total');
    const confirmDialog = document.querySelector('#confirm');
    const confirmation = document.querySelector('#confirmation');

    const state = {
        tickets: [],
        busy: false,
        loadId: 0,
        pending: null,
        key: null,
        lastBody: null
    };

    function checkedTickets() {
        return state.tickets.filter(ticket => {
            const input = tickets.querySelector('input[data-id="' + ticket.ticketId + '"]');
            return input?.checked;
        });
    }

    function renderTotal() {
        const chosen = checkedTickets();
        const sum = chosen.reduce((total, ticket) => total + BigInt(ticket.paidAmount), 0n);
        totalLabel.textContent = 'Số tiền dự kiến: ' + TC.money(sum);
        submit.disabled = state.busy || chosen.length === 0;
    }

    function ticketRow(ticket) {
        const input = TC.el('input', {
            type: 'checkbox',
            disabled: !ticket.isRefundable,
            dataset: {id: ticket.ticketId}
        });
        input.addEventListener('change', renderTotal);

        const text = TC.el('span', {text: ticket.seatLabel + ' · ' + TC.money(ticket.paidAmount)});
        const row = TC.el('div', {className: 'ticket'}, TC.el('label', {}, input, text));
        if (ticket.unrefundableReason) {
            row.append(TC.el('p', {className: 'muted', text: ticket.unrefundableReason}));
        }
        return row;
    }

    function setBusy(busy) {
        state.busy = busy;
        order.disabled = busy;
        reason.disabled = busy;
        renderTotal();
    }

    async function loadTickets() {
        const loadId = ++state.loadId;
        state.tickets = [];
        tickets.replaceChildren();
        message.textContent = '';
        renderTotal();
        if (!order.value) return;

        message.textContent = 'Đang tải vé…';
        try {
            const path = '/api/orders/' + encodeURIComponent(order.value) + '/refundable-tickets';
            const rows = await TC.api(path);
            if (loadId !== state.loadId) return;

            state.tickets = rows;
            tickets.append(...rows.map(ticketRow));
            message.textContent = rows.length ? '' : 'Đơn hàng không có vé.';
            renderTotal();
        } catch (error) {
            if (loadId === state.loadId) message.textContent = error.message;
        }
    }

    function review(event) {
        event.preventDefault();
        const chosen = checkedTickets();
        if (state.busy || chosen.length === 0) return;

        const request = {
            orderId: order.value,
            ticketIds: chosen.map(ticket => ticket.ticketId),
            reason: reason.value.trim()
        };
        if ([...request.reason].length < 10) {
            message.textContent = 'Lý do cần ít nhất 10 ký tự.';
            return;
        }

        // Retrying the same request after an uncertain response must reuse its Idempotency-Key.
        const body = JSON.stringify(request);
        if (body !== state.lastBody) {
            state.key = TC.newKey();
            state.lastBody = body;
        }
        state.pending = request;
        confirmation.textContent = 'Bạn chọn ' + chosen.length + ' vé. ' + totalLabel.textContent;
        confirmDialog.showModal();
    }

    async function sendRequest() {
        confirmDialog.close();
        setBusy(true);
        message.textContent = 'Đang gửi yêu cầu…';
        try {
            const result = await TC.send('POST', '/api/refund-requests', state.pending, state.key);
            message.textContent = result.message + '\nSố tiền: ' + TC.money(result.totalRefundAmount);
            location.assign(TC.url('/my-refunds'));
        } catch (error) {
            message.textContent = error.message + ' Bạn có thể bấm gửi lại cùng yêu cầu.';
        } finally {
            setBusy(false);
        }
    }

    order.addEventListener('change', loadTickets);
    document.querySelector('#form').addEventListener('submit', review);
    document.querySelector('#cancel').addEventListener('click', () => confirmDialog.close());
    document.querySelector('#send').addEventListener('click', sendRequest);

    const preselected = app.dataset.order;
    if (preselected && [...order.options].some(option => option.value === preselected)) {
        order.value = preselected;
        loadTickets();
    }
})();
