// Page script for auth/login.jsp; reads the context path from the layout meta tag.
const contextPath = document.querySelector('meta[name="context-path"]').content;
let csrfToken = document.getElementById('login-csrf').value;
document.getElementById('loginForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    const alertBox = document.getElementById('alertBox');
    alertBox.classList.add('d-none');
    const btn = document.getElementById('btnLogin');
    btn.disabled = true;
    btn.innerText = 'Đang đăng nhập...';
    const identifier = document.getElementById('identifier').value.trim();
    const password = document.getElementById('password').value;
    if (!csrfToken) {
        try {
            const cResp = await fetch(contextPath + '/api/auth/csrf');
            const cData = await cResp.json();
            if (cData && cData.data && cData.data.token) {
                csrfToken = cData.data.token;
                document.getElementById('login-csrf').value = csrfToken;
            }
        } catch (e) {}
    }
    try {
        const reqHeaders = {
            'Content-Type': 'application/json'
        };
        if (csrfToken) {
            reqHeaders['X-CSRF-Token'] = csrfToken;
        }
        const loginApi = new URL(document.getElementById('loginForm').action, window.location.href);
        loginApi.pathname = contextPath + '/api/auth/login';
        const resp = await fetch(loginApi.pathname + loginApi.search, {
            method: 'POST',
            headers: reqHeaders,
            body: JSON.stringify({
                identifier: identifier,
                password: password
            })
        });
        const res = await resp.json();
        if (resp.ok && res.data && res.data.authenticated) {
            const redirectTarget = res.data?.redirect || (contextPath + '/');
            window.location.href = redirectTarget;
        } else {
            alertBox.className = 'alert alert-danger';
            alertBox.innerText = (res.error && res.error.message) || 'Email hoặc mật khẩu không chính xác';
            alertBox.classList.remove('d-none');
            btn.disabled = false;
            btn.innerText = 'Đăng nhập';
        }
    } catch (err) {
        document.getElementById('loginForm').submit();
    }
});
