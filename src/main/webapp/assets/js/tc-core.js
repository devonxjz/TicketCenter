/**
 * TC Core: Thư viện nền tảng dùng chung cho toàn bộ giao diện TicketCenter.
 * Cung cấp: Context Path, CSRF, Gọi API chuẩn, Idempotent Mutate, Format Tiền/Ngày và XSS Escape.
 */
(function(root) {
    'use strict';

    const TC = {
        get base() {
            return (typeof window !== 'undefined' && window.TC_CONTEXT_PATH) ||
                (typeof document !== 'undefined' && document.querySelector('meta[name="context-path"]')?.getAttribute('content')) ||
                (typeof document !== 'undefined' && document.body?.dataset?.context) ||
                (typeof location !== 'undefined' && location.pathname?.startsWith('/ticketscenter') ? '/ticketscenter' : '');
        },

        async csrf() {
            let token = (typeof document !== 'undefined' && document.querySelector('meta[name="csrf-token"]')?.getAttribute('content')) ||
                (typeof document !== 'undefined' && document.querySelector('input[name="_csrf"]')?.value);
            if (token && token.trim()) return token.trim();

            try {
                const resp = await fetch(TC.base + '/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
                if (resp.ok) {
                    const data = await resp.json();
                    if (data?.data?.token) return data.data.token;
                }
            } catch (_) {}
            return '';
        },

        async api(path, options = {}) {
            const url = path.startsWith('http') ? path : (path.startsWith(TC.base) ? path : TC.base + (path.startsWith('/') ? path : '/' + path));
            const res = await fetch(url, {
                ...options,
                credentials: 'same-origin',
                headers: {
                    'Accept': 'application/json',
                    ...(options.headers || {})
                }
            });
            let json = null;
            try { json = await res.json(); } catch (_) {}
            if (!res.ok) {
                const msg = json?.error?.message || json?.message || `Yêu cầu thất bại (${res.status})`;
                const err = new Error(msg);
                err.status = res.status;
                err.data = json;
                throw err;
            }
            return json;
        },

        async mutate(path, payload = {}, key = null) {
            const token = await TC.csrf();
            const idemp = key || ((typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : 'idemp-' + Date.now());
            const headers = {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Idempotency-Key': idemp
            };
            if (token) headers['X-CSRF-Token'] = token;

            const bodyData = payload ? { ...payload } : {};
            if (token) bodyData._csrf = token;

            return TC.api(path, {
                method: 'POST',
                headers,
                body: JSON.stringify(bodyData)
            });
        },

        money(value) {
            if (value === null || value === undefined) return '—';
            try {
                const num = BigInt(value);
                return num.toLocaleString('vi-VN') + ' đ';
            } catch (_) {
                return String(value) + ' đ';
            }
        },

        date(value) {
            if (!value) return '—';
            try {
                const d = new Date(value);
                return Number.isFinite(d.getTime()) ? d.toLocaleString('vi-VN') : String(value);
            } catch (_) {
                return String(value);
            }
        },

        escapeHtml(str) {
            if (str == null) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        },

        el(tag, text = '', cls = '') {
            const e = document.createElement(tag);
            if (text) e.textContent = text;
            if (cls) e.className = cls;
            return e;
        }
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = TC;
    }
    if (typeof root !== 'undefined') {
        root.TC = TC;
    }
})(typeof window !== 'undefined' ? window : globalThis);
