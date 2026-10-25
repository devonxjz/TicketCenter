(() => {
    'use strict';

    const base = location.pathname.replace(/\/(?:organizer\/checkin-scanner|check-ins\/scanner)\/?$/, '');
    const eventId = new URLSearchParams(location.search).get('eventId');
    const el = id => document.getElementById(id);
    const notice = el('notice');
    el('back').href = base + '/check-ins';

    let csrf, open = false,
        busy = false,
        pending = null;
    let camera = null,
        active = false,
        readFile = false,
        starting = false;

    function text(tag, value) {
        const item = document.createElement(tag);
        item.textContent = value;
        return item;
    }

    function time(value) {
        return value ? new Intl.DateTimeFormat('vi-VN', {
            dateStyle: 'short',
            timeStyle: 'medium',
            timeZone: 'Asia/Ho_Chi_Minh'
        }).format(new Date(value)) : '—';
    }

    function enabled() {
        const disabled = !open || busy || !!pending || readFile || starting;
        for (const id of ['code', 'submit', 'image']) el(id).disabled = disabled;
        el('camera').disabled = disabled || active;
        el('stop').disabled = !active;
    }

    async function api(url, options = {}) {
        const response = await fetch(base + url, {
            credentials: 'same-origin',
            cache: 'no-store',
            ...options,
            headers: {
                Accept: 'application/json',
                ...options.headers
            }
        });
        return {
            response,
            body: await response.json()
        };
    }

    function show(status, title, lines) {
        const area = el('result');
        area.className = 'panel ' + status;
        area.replaceChildren(text('h2', title), ...lines.map(line => text('p', line)));
    }

    function beep(success) {
        try {
            const Audio = window.AudioContext || window.webkitAudioContext;
            const context = new Audio(),
                oscillator = context.createOscillator(),
                gain = context.createGain();
            oscillator.connect(gain);
            gain.connect(context.destination);
            oscillator.frequency.value = success ? 880 : 220;
            gain.gain.value = .08;
            oscillator.start();
            oscillator.stop(context.currentTime + .18);
            oscillator.onended = () => context.close();
        } catch (_) {
            /* Audio feedback is optional. */ }
    }

    async function stop() {
        if (camera && active) {
            try {
                await camera.stop();
            } catch (_) {}
            active = false;
        }
        enabled();
    }

    async function history() {
        try {
            const {
                response,
                body
            } = await api('/api/events/' + encodeURIComponent(eventId) + '/check-ins?pageSize=20');
            if (!response.ok) throw Error(body.error?.message || 'Không thể tải nhật ký.');
            const rows = body.data,
                host = el('history');
            if (!rows.length) {
                host.replaceChildren(text('p', 'Chưa có lượt soát vé.'));
                return;
            }

            const labels = {
                SUCCESS: 'Vào cửa thành công',
                ALREADY_USED: 'Vé đã dùng',
                INVALID_CODE: 'Mã không hợp lệ',
                WRONG_EVENT: 'Sai sự kiện',
                OUTSIDE_WINDOW: 'Ngoài khung giờ',
                INACTIVE_TICKET: 'Vé không còn hiệu lực'
            };
            const table = document.createElement('table'),
                head = document.createElement('tr');
            for (const name of ['Thời gian (Việt Nam)', 'Kết quả', 'Khu / ghế', 'Nhân viên']) {
                head.append(text('th', name));
            }
            table.append(head);
            for (const row of rows) {
                const tr = document.createElement('tr');
                const values = [time(row.scannedAt), labels[row.result] || 'Không hợp lệ',
                    [row.zoneName, row.seatLabel].filter(Boolean).join(' / ') || '—', row.staffName || '—'
                ];
                for (const value of values) tr.append(text('td', value));
                table.append(tr);
            }
            host.replaceChildren(table);
        } catch (error) {
            el('history').replaceChildren(text('p', error.message));
        }
    }

    async function submit(raw, replay = false) {
        if (busy || (!open && !replay) || (pending && !replay)) return;
        const value = raw.trim();
        if (!value) return;
        // Keep the same body and key until an uncertain result has been resolved.
        if (!pending) {
            pending = {
                key: crypto.randomUUID(),
                body: {
                    eventId,
                    ...(value.includes('.') ? {
                        qrContent: value
                    } : {
                        ticketCode: value
                    })
                }
            };
        }
        busy = true;
        enabled();
        await stop();
        el('retry').hidden = true;
        el('next').hidden = true;
        notice.textContent = '';
        show('', 'Đang kiểm tra…', []);

        try {
            const {
                response,
                body
            } = await api('/api/check-ins', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': csrf,
                    'Idempotency-Key': pending.key
                },
                body: JSON.stringify(pending.body)
            });
            if (response.status >= 500) {
                throw Error('Chưa xác định được kết quả. Tra cứu lại bằng mã yêu cầu hiện tại.');
            }
            if (!body.data) {
                if (response.status === 401) {
                    open = false;
                    notice.textContent = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
                }
                throw Object.assign(Error(body.error?.message || 'Yêu cầu không hợp lệ.'), {
                    definite: true
                });
            }

            const result = body.data;
            show(result.success ? 'success' : 'failure', result.message, [
                result.customerName ? 'Khách: ' + result.customerName : '',
                result.zoneName ? 'Khu: ' + result.zoneName + (result.seatLabel ? ' · Ghế: ' + result
                    .seatLabel : '') : '',
                result.checkedInAt ? 'Thời gian vào cửa: ' + time(result.checkedInAt) : ''
            ].filter(Boolean));
            beep(result.success);
            pending = null;
            el('code').value = '';
            el('next').hidden = false;
            await history();
        } catch (error) {
            show(error.definite ? 'failure' : 'uncertain', error.definite ? error.message :
                'Cần xác minh kết quả',
                [error.definite ? '' : error.message].filter(Boolean));
            if (error.definite) {
                pending = null;
                el('next').hidden = false;
            } else {
                el('retry').hidden = false;
            }
        } finally {
            busy = false;
            enabled();
        }
    }

    function reader() {
        camera ??= new Html5Qrcode('reader', {
            formatsToSupport: [Html5QrcodeSupportedFormats.QR_CODE],
            verbose: false
        });
        return camera;
    }

    el('manual').addEventListener('submit', event => {
        event.preventDefault();
        if (!readFile && !starting) submit(el('code').value);
    });
    el('retry').addEventListener('click', () => {
        if (pending) submit(pending.body.qrContent || pending.body.ticketCode, true);
    });
    el('next').addEventListener('click', () => {
        el('next').hidden = true;
        show('', 'Sẵn sàng soát vé', ['Quét QR hoặc nhập mã để kiểm tra.']);
        el('code').focus();
    });
    el('camera').addEventListener('click', async () => {
        if (busy || pending || !open || readFile || starting) return;
        starting = true;
        enabled();
        try {
            await reader().start({
                    facingMode: 'environment'
                }, {
                    fps: 10,
                    qrbox: {
                        width: 220,
                        height: 220
                    }
                },
                decoded => {
                    if (!busy && !pending) submit(decoded);
                }, () => {});
            active = true;
        } catch (_) {
            notice.textContent =
                'Không mở được camera. Cho phép truy cập camera hoặc chọn ảnh QR / nhập mã vé.';
        } finally {
            starting = false;
            enabled();
        }
    });
    el('stop').addEventListener('click', stop);
    el('image').addEventListener('change', async () => {
        const file = el('image').files[0];
        if (!file || busy || pending || readFile || starting) return;
        readFile = true;
        enabled();
        try {
            await stop();
            const decoded = await reader().scanFile(file, true);
            readFile = false;
            await submit(decoded);
        } catch (_) {
            notice.textContent = 'Không đọc được QR từ ảnh. Hãy chọn ảnh rõ nét hoặc nhập mã vé.';
        } finally {
            readFile = false;
            el('image').value = '';
            enabled();
        }
    });
    el('reload').addEventListener('click', history);

    async function windowStatus() {
        try {
            const {
                response,
                body
            } = await api('/api/events/' + encodeURIComponent(eventId) + '/check-in-window');
            if (!response.ok) {
                open = false;
                notice.textContent = body.error?.message || 'Không thể mở soát vé.';
                await stop();
            } else {
                open = body.data.isOpen;
                el('window').textContent = body.data.message + ' · Đóng cổng: ' + time(body.data.windowEnd);
                if (!open) await stop();
            }
            enabled();
        } catch (_) {
            open = false;
            enabled();
            notice.textContent = 'Không xác minh được khung giờ soát vé. Vui lòng tải lại.';
        }
    }

    async function initialize() {
        try {
            const auth = await api('/api/auth/csrf');
            if (!auth.response.ok) throw Error('Không thể xác thực phiên làm việc.');
            csrf = auth.body.data.token;
            const events = await api('/api/check-ins/events');
            const event = events.body.data?.find(item => item.eventId.toLowerCase() === eventId?.toLowerCase());
            el('event-title').textContent = event?.title || 'Soát vé sự kiện';
            await windowStatus();
            await history();
        } catch (error) {
            notice.textContent = error.message;
        }
    }

    window.addEventListener('pagehide', () => {
        stop();
        pending = null;
    });
    initialize();
    setInterval(() => {
        if (!document.hidden && !busy) windowStatus();
    }, 15000);
})();
