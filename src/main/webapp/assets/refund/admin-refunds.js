'use strict';
(() => {
  const $ = id => document.getElementById(id), context = document.body.dataset.context;
  const api = context + '/api/admin/refund-requests';
  const states = {
    REQUESTED: 'Chờ duyệt', APPROVED: 'Đã duyệt', PROCESSING: 'Đang xử lý', COMPLETED: 'Hoàn tất',
    REJECTED: 'Đã từ chối', RETRYABLE: 'Thất bại', NEEDS_RECONCILIATION: 'Cần đối soát', PENDING: 'Chờ chuyển',
    FAILED: 'Thất bại', UNKNOWN: 'Chưa rõ kết quả', SUCCEEDED: 'Thành công', ACTIVE: 'Còn hiệu lực',
    REFUND_PENDING: 'Chờ hoàn', REFUNDED: 'Đã hoàn', INVALIDATED: 'Mất hiệu lực', USED: 'Đã sử dụng'
  };
  const money = value => new Intl.NumberFormat('vi-VN').format(BigInt(value)) + ' ₫';
  const time = value => value
    ? new Intl.DateTimeFormat('vi-VN', {dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh'}).format(new Date(value))
    : '—';
  const node = (tag, value, className) => {
    const e = document.createElement(tag);
    e.textContent = value ?? '—';
    if (className) e.className = className;
    return e;
  };
  const cell = (row, value) => {
    const e = node('td', value);
    row.append(e);
    return e;
  };
  const feedback = (value, error = false) => {
    $('feedback').textContent = value;
    $('feedback').classList.toggle('error', error);
    $('reload').hidden = !error;
  };

  async function request(url, options = {}) {
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(url, {...options, credentials: 'same-origin', cache: 'no-store', signal: controller.signal});
      const text = await response.text();
      let body;
      try {
        body = JSON.parse(text);
      } catch {
        throw new Error('Máy chủ chưa trả kết quả rõ ràng. Hãy tải lại hồ sơ hoặc gửi lại cùng thao tác.');
      }
      if (!response.ok) {
        const error = new Error(body.error?.message || body.message || 'Không thể xử lý yêu cầu.');
        error.status = response.status;
        throw error;
      }
      return body.data;
    } catch (error) {
      if (error.name === 'AbortError' || error instanceof TypeError) {
        throw new Error('Kết nối gián đoạn, kết quả có thể đã được lưu. Hãy tải lại hồ sơ hoặc gửi lại cùng thao tác.');
      }
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  if (document.body.dataset.page === 'list') {
    const params = new URLSearchParams(location.search);
    let page = Math.max(1, Number(params.get('page')) || 1), busy = false;
    if ([...$('status').options].some(o => o.value === params.get('status'))) $('status').value = params.get('status');
    if (['20', '50', '100'].includes(params.get('pageSize'))) $('pageSize').value = params.get('pageSize');

    async function load() {
      if (busy) return;
      busy = true;
      $('pagination').replaceChildren();
      $('prev').disabled = $('next').disabled = true;
      feedback('Đang tải hồ sơ…');
      const query = new URLSearchParams({page: String(page), pageSize: $('pageSize').value});
      if ($('status').value) query.set('status', $('status').value);
      history.replaceState(null, '', '?' + query);
      try {
        const data = await request(api + '?' + query);
        $('rows').replaceChildren();
        for (const r of data.items) {
          const row = document.createElement('tr');
          cell(row, 'Hoàn ' + r.ticketCount + ' vé');
          const buyer = cell(row, r.buyer.fullName);
          buyer.append(node('span', r.buyer.email, 'secondary'));
          cell(row, r.eventName);
          cell(row, r.ticketCount);
          cell(row, money(r.totalAmount));
          cell(row, '').append(node('span', states[r.status] || r.status, 'badge'));
          cell(row, time(r.createdAt));
          const link = node('a', 'Xem hồ sơ');
          link.href = context + '/admin/refund-requests/' + encodeURIComponent(r.refundId);
          cell(row, '').append(link);
          $('rows').append(row);
        }
        feedback(data.total ? `${data.total} hồ sơ` : 'Không có hồ sơ phù hợp.');
        globalThis.TicketUI?.renderPagination($('pagination'), {
          page: data.page, total: data.total, pageSize: data.pageSize, previous: $('prev'), next: $('next'),
          onPageChange: target => {
            if (busy) return;
            page = target;
            load();
          }
        });
        $('prev').disabled = page <= 1;
        $('next').disabled = page * data.pageSize >= data.total;
      } catch (error) {
        $('rows').replaceChildren();
        feedback(error.message, true);
      } finally {
        busy = false;
      }
    }

    $('filter').addEventListener('submit', e => { e.preventDefault(); page = 1; load(); });
    $('prev').onclick = () => { page--; load(); };
    $('next').onclick = () => { page++; load(); };
    $('reload').onclick = load;
    load();
    return;
  }

  const id = document.body.dataset.refund, keys = new Map();
  let current, action, busy = false, loading = false;
  function buttons() {
    for (const key of ['approve', 'reject', 'retry']) $(key).disabled = busy || loading;
  }
  function fact(label, value) {
    $('facts').append(node('dt', label), node('dd', value));
  }

  async function load(keepFeedback = false) {
    if (loading) return;
    loading = true;
    buttons();
    if (!keepFeedback) feedback('Đang tải hồ sơ…');
    try {
      const data = await request(api + '/' + id), r = data.refund;
      current = r;
      $('detail').hidden = false;
      $('facts').replaceChildren();
      for (const [label, value] of [
        ['Người mua', r.buyer.fullName], ['Email', r.buyer.email], ['Sự kiện', r.eventName],
        ['Số tiền', money(r.totalAmount)], ['Trạng thái', states[r.status]], ['Yêu cầu lúc', time(r.createdAt)],
        ['Lý do hoàn', r.reason], ['Phương thức hoàn', data.repaymentMethod], ['Ghi chú quản trị', data.adminNote],
        ['Lý do từ chối', r.rejectionReason], ['Quyết định lúc', time(data.decidedAt)]
      ]) fact(label, value);
      $('tickets').replaceChildren();
      for (const t of data.tickets) {
        const row = document.createElement('tr');
        for (const v of ['Vé sự kiện', t.seatLabel, money(t.paidAmount), states[t.status] || t.status]) cell(row, v);
        $('tickets').append(row);
      }
      $('attempts').replaceChildren();
      $('noAttempts').hidden = data.attempts.length > 0;
      for (const a of data.attempts) {
        const row = document.createElement('tr');
        for (const v of [a.attemptNo, states[a.status] || a.status, time(a.createdAt), time(a.processedAt)]) cell(row, v);
        $('attempts').append(row);
      }
      globalThis.TicketUI?.paginateItems($('tickets'), {pageSize: 10, label: 'Phân trang vé hoàn'});
      globalThis.TicketUI?.paginateItems($('attempts'), {pageSize: 10, label: 'Phân trang lần chuyển tiền'});
      $('approve').hidden = $('reject').hidden = !r.canDecide;
      $('retry').hidden = !r.canRetry;
      $('stateHint').textContent = r.canDecide
        ? 'Phê duyệt sẽ đưa yêu cầu vào hàng đợi. Vé chỉ thành đã hoàn sau khi xác nhận chuyển tiền thành công. Từ chối khôi phục vé còn hợp lệ; vé của sự kiện đã hủy không được khôi phục.'
        : r.canRetry
          ? 'Lần trước đã xác nhận thất bại. Thử lại tạo lần chuyển mới cho cùng hồ sơ hoàn tiền.'
          : r.status === 'NEEDS_RECONCILIATION'
            ? 'Chưa rõ kết quả chuyển tiền. Cần đối soát trước khi thực hiện thao tác tiếp theo.'
            : 'Hồ sơ hiện không có thao tác quản trị khả dụng.';
      if (!keepFeedback) feedback('Hồ sơ đã được cập nhật.');
    } catch (error) {
      current = null;
      $('detail').hidden = true;
      feedback(error.message, true);
    } finally {
      loading = false;
      buttons();
    }
  }

  function open(value) {
    action = value;
    $('dialogError').textContent = '';
    const reject = value === 'REJECT', retry = value === 'RETRY';
    $('dialogTitle').textContent = reject ? 'Từ chối hoàn tiền' : retry ? 'Thử chuyển tiền lại' : 'Phê duyệt hoàn tiền';
    $('dialogHint').textContent = retry
      ? 'Chỉ tiếp tục khi lần trước đã thất bại rõ ràng. Hệ thống sẽ giữ nguyên mã hồ sơ và tạo mã lần chuyển mới.'
      : reject
        ? 'Nhập lý do rõ ràng để người mua hiểu quyết định.'
        : 'Xác nhận phê duyệt hồ sơ này và đưa vào hàng đợi hoàn tiền.';
    $('inputLabel').textContent = reject ? 'Lý do từ chối (bắt buộc)' : 'Ghi chú (không bắt buộc)';
    $('inputLabel').hidden = $('decisionText').hidden = retry;
    $('decisionText').required = reject;
    $('decisionText').maxLength = reject ? 1000 : 500;
    $('decisionText').value = '';
    $('confirm').disabled = false;
    $('decisionDialog').showModal();
  }

  $('approve').onclick = () => open('APPROVE');
  $('reject').onclick = () => open('REJECT');
  $('retry').onclick = () => open('RETRY');
  $('cancel').onclick = () => { if (!busy) $('decisionDialog').close(); };
  $('decisionDialog').addEventListener('cancel', e => { if (busy) e.preventDefault(); });
  $('decisionForm').addEventListener('submit', async e => {
    e.preventDefault();
    if (busy || !current) return;
    const value = $('decisionText').value.trim();
    const body = action === 'RETRY' ? {} : action === 'REJECT'
      ? {decision: action, rejectReason: value} : {decision: action, note: value || null};
    if (action === 'REJECT' && !value) {
      $('dialogError').textContent = 'Lý do từ chối không được để trống.';
      return;
    }
    const serialized = JSON.stringify(body), fingerprint = action + ':' + serialized;
    // Preserve the key for a repeated payload, including an uncertain timeout or dropped response.
    if (!keys.has(fingerprint)) keys.set(fingerprint, crypto.randomUUID());
    busy = true;
    buttons();
    $('confirm').disabled = $('cancel').disabled = $('decisionText').disabled = true;
    $('dialogError').textContent = 'Đang xử lý…';
    try {
      const result = await request(api + '/' + id + (action === 'RETRY' ? '/retry' : '/decision'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-Token': document.querySelector('meta[name="csrf-token"]').content,
          'Idempotency-Key': keys.get(fingerprint)
        },
        body: serialized
      });
      $('decisionDialog').close();
      feedback(result.message);
      await load(true);
    } catch (error) {
      $('dialogError').textContent = error.message;
      if (error.status === 409) await load(true);
    } finally {
      busy = false;
      $('confirm').disabled = $('cancel').disabled = $('decisionText').disabled = false;
      buttons();
    }
  });
  $('reload').onclick = () => load();
  load();
})();
