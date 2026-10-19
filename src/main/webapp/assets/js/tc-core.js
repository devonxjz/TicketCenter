/**
 * Shared glue between JSP pages and the JSON API.
 * Pages load it first (defer) and use `TC.*` instead of their own fetch, CSRF and format helpers.
 *
 * 5-bước tuần tự: 1. Helpers -> 2. Trạng thái -> 3. Render -> 4. Hành động -> 5. Xuất bản
 */
(function (root) {
    'use strict';

    // 1. HẰNG SỐ & PURE HELPERS
    function meta(name) {
        const tags = root.document?.querySelectorAll(`meta[name="${name}"]`) || [];
        for (const tag of tags) {
            const value = tag.getAttribute('content')?.trim();
            if (value) return value;
        }
        return '';
    }

    const url = path => /^https?:/.test(path) ? path : TC.base + path;
    const readJson = response => response.json().catch(() => null);
    const newKey = () => root.crypto.randomUUID();

    function apiError(response, body) {
        const message = body?.error?.message || `Yêu cầu thất bại (${response.status})`;
        const error = new Error(message);
        error.status = response.status;
        error.code = body?.error?.code;
        return error;
    }

    function money(value) {
        if (value === null || value === undefined || value === '') return '—';
        try {
            return BigInt(value).toLocaleString('vi-VN') + ' ₫';
        } catch (_) {
            return String(value) + ' ₫';
        }
    }

    function dateTime(value) {
        if (!value) return '—';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('vi-VN', {dateStyle: 'medium', timeStyle: 'short'});
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) return '';
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // 2. TRẠNG THÁI (STATE)
    let cachedCsrf = '';

    // 3. RENDER / DOM BUILDERS
    /** Builds an element; text always goes through textContent. */
    function el(tag, props = {}, ...children) {
        const element = root.document.createElement(tag);
        const {text, className, dataset, ...rest} = props;
        if (text !== undefined) element.textContent = text;
        if (className) element.className = className;
        Object.assign(element.dataset, dataset);
        Object.assign(element, rest);
        element.append(...children);
        return element;
    }

    // 4. HÀNH ĐỘNG / API (ACTIONS)
    async function api(path, options = {}) {
        const response = await root.fetch(url(path), {
            ...options,
            credentials: 'same-origin',
            headers: {Accept: 'application/json', ...options.headers}
        });
        const body = await readJson(response);
        if (!response.ok) throw apiError(response, body);
        return body?.data;
    }

    async function csrf() {
        if (cachedCsrf) return cachedCsrf;
        cachedCsrf = meta('csrf-token');
        if (cachedCsrf) return cachedCsrf;
        const data = await api('/api/auth/csrf', {cache: 'no-store'});
        cachedCsrf = data?.token || '';
        return cachedCsrf;
    }

    /** JSON write with CSRF and Idempotency-Key; pass the same `key` to retry the same request. */
    async function send(method, path, payload, key = newKey()) {
        return api(path, {
            method,
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-Token': await csrf(),
                'Idempotency-Key': key
            },
            body: JSON.stringify(payload ?? {})
        });
    }

    // 5. SỰ KIỆN & KHỞI TẠO (INIT & EXPORT)
    const TC = {
        get base() { return meta('context-path'); },
        url, api, csrf, send, newKey, money, dateTime, escapeHtml, el
    };

    root.TC = TC;
    if (typeof module !== 'undefined' && module.exports) module.exports = TC;
})(typeof window !== 'undefined' ? window : globalThis);
