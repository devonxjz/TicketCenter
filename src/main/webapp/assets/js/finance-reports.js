/* Finance read-side pages: safe text rendering and one filter contract for JSON/CSV. */
(function() {
    'use strict';
    const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;

    function periodBounds(kind, now = new Date()) {
        if (kind === 'all') return {
            from: '1970-01-01T00:00:00',
            to: '9999-12-31T23:59:59'
        };
        if (!['7', '30', '90'].includes(kind) || !Number.isFinite(now.getTime())) throw new Error(
            'Khoảng thời gian không hợp lệ.');
        const today = new Date(now);
        today.setUTCHours(0, 0, 0, 0);
        const start = new Date(today);
        start.setUTCDate(start.getUTCDate() - Number(kind) + 1);
        const end = new Date(today);
        end.setUTCDate(end.getUTCDate() + 1);
        return {
            from: start.toISOString().slice(0, 19),
            to: end.toISOString().slice(0, 19)
        };
    }

    function utc(value) {
        if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(value || ''))
            throw new Error('Nhập đầy đủ thời gian UTC.');
        const date = new Date(value + 'Z');
        if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 16) !== value.slice(0, 16))
            throw new Error('Thời gian UTC không hợp lệ.');
        return date.toISOString();
    }

    function query(values, pageType, page) {
        const from = utc(values.from),
            to = utc(values.to);
        if (from >= to) throw new Error('Thời gian kết thúc phải sau thời gian bắt đầu.');
        const size = values.pageSize || '20';
        if (!/^[0-9]+$/.test(size) || Number(size) < 1 || Number(size) > 100 || !Number.isInteger(page) || page < 1)
            throw new Error('Phân trang không hợp lệ.');
        const result = new URLSearchParams({
            from,
            to,
            page: String(page),
            pageSize: size
        });
        for (const name of pageType === 'audit' ? ['organizationId', 'eventId', 'actorId'] : ['organizationId',
                'eventId'
            ]) {
            if (values[name]) {
                if (!uuid.test(values[name])) throw new Error('Tổ chức hoặc sự kiện không hợp lệ.');
                result.set(name, values[name]);
            }
        }
        if (pageType === 'reports') {
            const metric = values.metric || 'COHORT_REVENUE';
            if (!['COHORT_REVENUE', 'CASH_FLOW', 'EVENT_SALES', 'SETTLEMENT_BALANCE'].includes(metric))
                throw new Error('Loại báo cáo không hợp lệ.');
            result.set('metric', metric);
        } else if (pageType === 'audit') {
            for (const name of ['action', 'entityType', 'entityId'])
                if (values[name]) result.set(name, values[name]);
        }
        return result;
    }

    function formatVnd(value) {
        if (value == null || !/^-?[0-9]+$/.test(String(value))) return '—';
        return new Intl.NumberFormat('vi-VN').format(BigInt(value)) + ' ₫';
    }
    async function fetchResponse(url, type, network = fetch) {
        const response = await network(url, {
            credentials: 'same-origin',
            cache: 'no-store',
            headers: {
                Accept: type
            }
        });
        if (!response.ok || !(response.headers.get('Content-Type') || '').toLowerCase().startsWith(type)) {
            const messages = {
                401: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
                403: 'Bạn không có quyền xem dữ liệu này.',
                404: 'Không tìm thấy dữ liệu trong phạm vi đã chọn.',
                400: 'Bộ lọc không hợp lệ. Kiểm tra lại thông tin.',
                503: 'Dữ liệu tạm thời không khả dụng. Vui lòng thử lại.'
            };
            let reference = '';
            if ((response.headers.get('Content-Type') || '').includes('application/json')) {
                try {
                    const payload = await response.json();
                    if (uuid.test(payload.error?.correlationId || '')) reference = ' Mã tra cứu: ' + payload
                        .error.correlationId;
                } catch (_) {
                    /* Fixed error only. */ }
            }
            throw new Error((messages[response.status] ||
                'Không thể tải dữ liệu. Vui lòng đăng nhập lại hoặc thử lại.') + reference);
        }
        return response;
    }
    const api = {
        query,
        periodBounds,
        formatVnd,
        fetchResponse
    };
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    if (typeof document === 'undefined') return;
    const app = document.getElementById('finance-readside');
    if (!app) return;
    const context = app.dataset.context || '',
        isAdmin = app.dataset.admin === 'true';
    const form = document.getElementById('filters'),
        org = document.getElementById('organization'),
        event = document.getElementById('event');
    const notice = document.getElementById('notice'),
        tbody = document.getElementById('rows'),
        head = document.getElementById('columns');
    const previous = document.getElementById('previous'),
        next = document.getElementById('next'),
        pagination = document.getElementById('pagination');
    const period = document.getElementById('report-period'),
        periodSummary = document.getElementById('report-period-summary');

    function showDates(show) {
        for (const name of ['from', 'to']) {
            const label = form?.elements?.[name]?.closest?.('label');
            if (label) label.hidden = !show;
        }
    }

    function periodDate(value) {
        const date = new Date(value);
        return Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat('vi-VN', {
            dateStyle: 'short',
            timeStyle: 'short',
            timeZone: 'UTC'
        }).format(date) : '—';
    }

    function setPeriod(kind) {
        if (kind !== 'custom') {
            const bounds = periodBounds(kind);
            for (const name of ['from', 'to'])
                if (form?.elements?.[name]) form.elements[name].value = bounds[name];
        }
        showDates(kind === 'custom');
    }
    let page = 1,
        sequence = 0,
        filterRevision = 0,
        scopeSequence = 0,
        loadingScope = false;
    const money = new Set(['grossRevenue', 'totalRefund', 'remainingAmount', 'totalCommission', 'netPayable',
        'ticketCaptured', 'compensationCaptured', 'ticketRefund', 'compensationRefund', 'netCashFlow',
        'paidAmount', 'pendingAmount', 'availableToPay'
    ]);
    const labels = {
        eventId: 'Mã sự kiện',
        organizationId: 'Mã tổ chức',
        eventTitle: 'Tên sự kiện',
        eventStatus: 'Trạng thái',
        endTime: 'Kết thúc (UTC)',
        paidOrderCount: 'Đơn đã thanh toán',
        ticketCount: 'Vé đã phát hành',
        validTicketCount: 'Vé hợp lệ',
        refundedTicketCount: 'Vé đã hoàn',
        ticketsCheckedInCount: 'Vé từng check-in',
        grossRevenue: 'Doanh thu vé',
        totalRefund: 'Hoàn tiền vé',
        remainingAmount: 'Doanh thu sau hoàn',
        totalCommission: 'Hoa hồng',
        netPayable: 'Phải trả tổ chức',
        ticketCaptured: 'Thu tiền vé',
        compensationCaptured: 'Thu thanh toán thừa',
        ticketRefund: 'Chi hoàn vé',
        compensationRefund: 'Chi hoàn thanh toán thừa',
        netCashFlow: 'Dòng tiền ròng',
        settlementId: 'Mã quyết toán',
        status: 'Trạng thái',
        settlementStatus: 'Quyết toán',
        isConfirmed: 'Bảng kê',
        paidAmount: 'Đã chi',
        pendingAmount: 'Chờ chi',
        availableToPay: 'Có thể chi',
        confirmedAt: 'Chốt lúc (UTC)',
        totalEvents: 'Số sự kiện trong báo cáo'
    };
    const columns = {
        COHORT_REVENUE: ['eventTitle', 'paidOrderCount', 'grossRevenue', 'totalRefund', 'remainingAmount',
            'totalCommission', 'netPayable'
        ],
        CASH_FLOW: ['eventTitle', 'ticketCaptured', 'compensationCaptured', 'ticketRefund',
            'compensationRefund', 'netCashFlow'
        ],
        EVENT_SALES: ['eventTitle', 'eventStatus', 'endTime', 'paidOrderCount', 'ticketCount',
            'validTicketCount', 'refundedTicketCount', 'ticketsCheckedInCount', 'grossRevenue',
            'totalRefund', 'totalCommission', 'netPayable', 'settlementStatus', 'isConfirmed'
        ],
        SETTLEMENT_BALANCE: ['eventTitle', 'status', 'grossRevenue', 'totalRefund', 'totalCommission',
            'netPayable', 'paidAmount', 'pendingAmount', 'availableToPay', 'confirmedAt'
        ]
    };
    const statusLabels = {
        DRAFT: 'Bản nháp',
        PENDING_APPROVAL: 'Chờ duyệt',
        PUBLISHED: 'Đã công khai',
        CANCELLED: 'Đã hủy',
        REJECTED: 'Đã từ chối',
        CONFIRMED: 'Đã chốt',
        PAID: 'Đã chi trả'
    };
    const basis = {
        ORDER_PAID_AT_WITH_REFUNDS_BEFORE_TO: 'Theo ngày đơn thanh toán; chỉ hoàn vé hoàn tất trước mốc Đến. Hoa hồng tính theo từng đơn.',
        PAYMENT_PAID_AT_AND_REFUND_PROCESSED_AT: 'Theo ngày thu/chi hoàn trong khoảng đã chọn; thanh toán thừa được tách riêng.',
        EVENT_END_TIME_CURRENT_OR_CONFIRMED_TOTALS: 'Lọc theo ngày kết thúc sự kiện; tiền hiện tại hoặc snapshot đã chốt. Số vé/check-in là số hiện tại, không phải phát sinh trong kỳ.'
    };

    function node(tag, text) {
        const n = document.createElement(tag);
        if (text != null) n.textContent = String(text);
        return n;
    }

    function values() {
        return Object.fromEntries(new FormData(form).entries());
    }

    function display(key, value) {
        if (value == null) return '—';
        if (money.has(key)) return formatVnd(value);
        if (key === 'isConfirmed') return value ? 'Đã chốt' : 'Ước tính';
        return statusLabels[value] || String(value);
    }

    function clear() {
        if (head) head.replaceChildren();
        if (tbody) tbody.replaceChildren();
        document.getElementById('summary')?.replaceChildren();
        document.getElementById('top-events')?.replaceChildren();
        if (previous) previous.disabled = true;
        if (next) next.disabled = true;
        if (pagination) pagination.textContent = '';
        const basisEl = document.getElementById('basis');
        if (basisEl) basisEl.textContent = '';
        if (periodSummary) periodSummary.textContent = '';
    }
    async function data(url) {
        return (await (await fetchResponse(context + url, 'application/json')).json()).data;
    }
    async function scope(selected) {
        const current = ++scopeSequence;
        loadingScope = true;
        if (event) {
            event.replaceChildren(new Option('Tất cả sự kiện', ''));
            event.disabled = true;
        }
        try {
            const result = await data('/api/reports/options' + (selected ? '?organizationId=' +
                encodeURIComponent(selected) : ''));
            if (current !== scopeSequence) return false;
            if (org) {
                org.replaceChildren();
                if (isAdmin) org.append(new Option('Tất cả tổ chức', ''));
                for (const x of result.organizations) org.append(new Option(x.name, x.id));
                org.value = selected || (isAdmin ? '' : result.organizations[0]?.id || '');
            }
            if (event) {
                for (const x of result.events) event.append(new Option(x.title, x.id));
                event.disabled = !org?.value;
            }
            return true;
        } finally {
            if (current === scopeSequence) loadingScope = false;
        }
    }

    function render(result) {
        const keys = columns[result.metric];
        if (!keys || !Array.isArray(result.rows)) throw Error('Dữ liệu báo cáo không hợp lệ.');
        if (head) {
            const tr = node('tr');
            for (const key of keys) {
                const th = node('th', labels[key]);
                th.scope = 'col';
                tr.append(th);
            }
            head.append(tr);
        }
        const emptyText = result.metric === 'SETTLEMENT_BALANCE' ?
            'Chưa có bảng quyết toán trong phạm vi đã chọn.' : 'Chưa có dữ liệu trong phạm vi đã chọn.';
        if (tbody) {
            for (const row of result.rows) {
                const tr = node('tr');
                for (const key of keys) tr.append(node('td', display(key, row[key])));
                tbody.append(tr);
            }
            if (!result.rows.length) {
                const td = node('td', emptyText);
                td.colSpan = keys.length;
                const tr = node('tr');
                tr.append(td);
                tbody.append(tr);
            }
        }
        const dl = node('dl');
        if (result.total === 0) {
            document.getElementById('summary')?.append(node('p', emptyText + (result.metric ===
                'SETTLEMENT_BALANCE' ? ' Chọn cách tính Doanh thu để xem tiền bán vé.' : '')));
        } else {
            for (const [key, value] of Object.entries(result.summary || {})) {
                const stat = node('div');
                stat.className = 'finance-stat';
                stat.append(node('dt', key === 'totalEvents' && result.metric === 'SETTLEMENT_BALANCE' ?
                    'Số bảng quyết toán' : labels[key] || key), node('dd', display(key, value)));
                dl.append(stat);
            }
            document.getElementById('summary')?.append(dl);
        }
        const top = document.getElementById('top-events');
        if (top && (result.topEvents || []).length) {
            top.append(node('h2', 'Sự kiện có doanh thu vé cao nhất theo bộ lọc'));
            const list = node('ol');
            for (const row of result.topEvents) list.append(node('li', (row.eventTitle || 'Sự kiện đã lưu trữ') +
                ' · ' + formatVnd(row.grossRevenue)));
            top.append(list);
        }
        if (previous) previous.disabled = page <= 1;
        if (next) next.disabled = page * result.pageSize >= result.total;
        if (pagination) globalThis.TicketUI?.renderPagination(pagination, {
            page,
            total: result.total,
            pageSize: result.pageSize,
            previous,
            next,
            onPageChange: load
        });
        const basisEl = document.getElementById('basis');
        if (basisEl) basisEl.textContent = basis[result.timeBasis] || '';
        if (periodSummary) {
            const kind = period?.value || 'all',
                label = kind === 'all' ? 'Toàn bộ thời gian' : kind === 'custom' ? 'Khoảng tùy chọn' : kind +
                ' ngày gần nhất';
            periodSummary.textContent = 'Phạm vi: ' + label + (kind === 'all' ?
                ' · Gồm lịch sử doanh số của tổ chức và sự kiện đã chọn.' : ' · Từ ' + periodDate(result.from) +
                ' đến trước ' + periodDate(result.to) + ' (UTC).');
        }
        if (notice) notice.textContent = result.metric === 'COHORT_REVENUE' && String(result.summary
            ?.grossRevenue) === '0' ? (Number(result.summary?.paidOrderCount) > 0 ?
                'Các đơn thanh toán trong phạm vi này không phát sinh doanh thu vé.' :
                'Không có đơn thanh toán phát sinh trong phạm vi đã chọn.') + ((period?.value || 'all') !== 'all' ?
                ' Chọn Toàn bộ thời gian để xem lịch sử.' : '') : '';
    }
    async function load(target) {
        const current = ++sequence;
        clear();
        if (notice) notice.textContent = 'Đang tải báo cáo…';
        try {
            if (loadingScope) throw Error('Đang tải danh sách sự kiện. Vui lòng thử lại.');
            const params = query(values(), 'reports', target),
                selected = params.get('organizationId');
            const route = isAdmin ? '/api/admin/reports' : '/api/organizations/' + encodeURIComponent(
                selected || '') + '/reports';
            const result = await data(route + '?' + params);
            if (current !== sequence) return;
            page = target;
            render(result);
        } catch (error) {
            if (current === sequence && notice) notice.textContent = error.message;
        }
    }

    function changed() {
        ++filterRevision;
        ++sequence;
        clear();
        if (notice) notice.textContent = 'Bộ lọc đã thay đổi. Nhấn Xem báo cáo hoặc tải CSV theo bộ lọc mới.';
    }
    form?.addEventListener('input', changed);
    form?.addEventListener('change', e => {
        if (e.target === form?.elements?.from || e.target === form?.elements?.to) {
            if (period) period.value = 'custom';
            showDates(true);
        }
        if (e.target !== org && e.target !== period) changed();
    });
    period?.addEventListener('change', () => {
        if (period.value === 'custom' && form?.elements?.from?.value === '1970-01-01T00:00:00') {
            const bounds = periodBounds('30');
            for (const name of ['from', 'to'])
                if (form.elements[name]) form.elements[name].value = bounds[name];
        }
        setPeriod(period.value);
        changed();
        if (period.value !== 'custom') void load(1);
    });
    form?.addEventListener('submit', e => {
        e.preventDefault();
        load(1);
    });
    org?.addEventListener('change', async () => {
        changed();
        const current = filterRevision;
        try {
            if (await scope(org?.value) && current === filterRevision) await load(1);
        } catch (error) {
            if (current === filterRevision && notice) notice.textContent = error.message;
        }
    });
    previous?.addEventListener('click', () => load(Math.max(1, page - 1)));
    next?.addEventListener('click', () => load(page + 1));
    const exportButton = document.getElementById('export');
    exportButton?.addEventListener('click', async () => {
        const current = filterRevision;
        if (exportButton) exportButton.disabled = true;
        if (notice) notice.textContent = 'Đang chuẩn bị CSV…';
        try {
            if (loadingScope) throw Error('Đang tải danh sách sự kiện. Vui lòng thử lại.');
            const params = query(values(), 'reports', 1);
            params.set('format', 'csv');
            params.set('scope', params.has('organizationId') ? 'ORGANIZATION' : 'PLATFORM');
            const response = await fetchResponse(context + '/api/reports/export?' + params, 'text/csv');
            const blob = await response.blob();
            if (current !== filterRevision) return;
            const url = URL.createObjectURL(blob),
                link = node('a');
            link.href = url;
            link.download = 'ticketscenter-' + params.get('metric').toLowerCase() + '.csv';
            document.body.append(link);
            link.click();
            link.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
            if (notice) notice.textContent = 'Đã tải CSV gồm toàn bộ dòng theo bộ lọc.';
        } catch (error) {
            if (current === filterRevision && notice) notice.textContent = error.message;
        } finally {
            if (exportButton) exportButton.disabled = false;
        }
    });
    (async () => {
        try {
            setPeriod(period?.value || 'all');
            await scope(app.dataset.organization || '');
            if (!isAdmin && org?.value) await scope(org.value);
            await load(1);
        } catch (error) {
            if (notice) notice.textContent = error.message;
        }
    })();
})();
