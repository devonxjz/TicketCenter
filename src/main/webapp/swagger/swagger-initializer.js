(function(global) {
    'use strict';

    const marker = '/swagger';
    const position = global.location.pathname.lastIndexOf(marker);
    const basePath = position >= 0 ? global.location.pathname.slice(0, position) : '';
    const mutationMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

    function operationFor(spec, pathname, method) {
        const relativePath = basePath ? pathname.slice(basePath.length) : pathname;
        for (const [path, item] of Object.entries(spec.paths || {})) {
            const pattern = path.split(/(\{[^}]+\})/).map(part => part.startsWith('{') ?
                '[^/]+' :
                part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('');
            if (new RegExp('^' + pattern + '$').test(relativePath)) return item[method.toLowerCase()];
        }
        return undefined;
    }

    function availabilityPlugin() {
        return {
            statePlugins: {
                spec: {
                    wrapSelectors: {
                        allowTryItOutFor: original => (state, path, method) => {
                            const status = state.getIn(['json', 'paths', path, method,
                                'x-implementation-status']);
                            return status === 'implemented' && original(state, path, method);
                        }
                    }
                }
            }
        };
    }

    function createOptions(sourceSpec) {
        const spec = Object.assign({}, sourceSpec, {
            servers: [{
                url: basePath || '/',
                description: 'Ứng dụng đang mở'
            }]
        });
        return {
            spec,
            dom_id: '#swagger-ui',
            deepLinking: true,
            filter: true,
            docExpansion: 'none',
            displayOperationId: true,
            displayRequestDuration: true,
            persistAuthorization: false,
            queryConfigEnabled: false,
            validatorUrl: null,
            withCredentials: true,
            plugins: [availabilityPlugin],
            async requestInterceptor(request) {
                const url = new URL(request.url, global.location.href);
                if (url.origin !== global.location.origin || url.username || url.password ||
                    (basePath && !url.pathname.startsWith(basePath + '/'))) {
                    throw new Error('Request must stay within this application origin and context.');
                }
                const method = (request.method || 'GET').toUpperCase();
                const operation = operationFor(spec, url.pathname, method);
                if (operation && operation['x-implementation-status'] !== 'implemented') {
                    throw new Error('API này chưa được triển khai (planned/legacy); chỉ dùng để đọc contract.');
                }
                request.credentials = 'same-origin';
                request.headers = request.headers || {};
                if (mutationMethods.has(method)) {
                    // The application's validation filter requires a media type even for empty POSTs (logout).
                    if (['POST', 'PUT', 'PATCH'].includes(method) && request.body == null &&
                        !Object.keys(request.headers).some(header => header.toLowerCase() === 'content-type')) {
                        request.headers['Content-Type'] = 'application/json';
                    }
                    // Fetch each time: login/reset or another tab can rotate the session token.
                    const response = await global.fetch(basePath + '/api/auth/csrf', {
                        credentials: 'same-origin',
                        cache: 'no-store',
                        redirect: 'error'
                    });
                    if (!response.ok) throw new Error('Không lấy được CSRF token; yêu cầu chưa được gửi.');
                    const payload = await response.json();
                    const token = payload && payload.data && payload.data.token;
                    if (typeof token !== 'string' || !token.trim()) throw new Error(
                        'CSRF response không hợp lệ.');
                    for (const header of Object.keys(request.headers)) {
                        if (header.toLowerCase() === 'x-csrf-token') delete request.headers[header];
                    }
                    request.headers['X-CSRF-Token'] = token;
                    // Idempotency-Key belongs to the caller; never invent or rotate it on replay.
                }
                return request;
            }
        };
    }

    async function boot() {
        const status = global.document.getElementById('load-status');
        try {
            const response = await global.fetch(basePath + '/api/openapi.json', {
                credentials: 'same-origin',
                cache: 'no-store',
                redirect: 'error'
            });
            if (!response.ok) throw new Error('Không tải được OpenAPI (' + response.status + ').');
            const spec = await response.json();
            const operations = Object.values(spec.paths || {}).flatMap(item => Object.entries(item)
                .filter(([method]) => /^(get|post|put|patch|delete|head|options|trace)$/.test(method))
                .map(([, operation]) => operation));
            const implemented = operations.filter(operation => operation['x-implementation-status'] ===
                'implemented').length;
            global.ui = global.SwaggerUIBundle(createOptions(spec));
            status.textContent = implemented + ' API đã triển khai / ' + operations.length +
                ' thao tác trong tài liệu. API chờ triển khai chỉ hiển thị contract.';
            status.dataset.state = 'ready';
        } catch (error) {
            status.textContent = 'Không thể mở tài liệu API. Kiểm tra ứng dụng và tải lại trang.';
            status.dataset.state = 'error';
            console.error('Swagger initialization failed:', error.message);
        }
    }

    global.TicketCenterSwagger = {
        createOptions,
        boot
    };
    if (global.document && global.SwaggerUIBundle) boot();
})(globalThis);
