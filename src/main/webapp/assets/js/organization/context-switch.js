// Page script for organization/context-switch.jsp; reads the context path from the layout meta tag.
(function() {
    'use strict';
    const STORAGE_KEY = 'tc_current_organization_id';
    const contextPath = document.querySelector('meta[name="context-path"]').content;

    function getSelect() {
        return document.getElementById('tcOrgSelect');
    }
    async function fetchMemberships() {
        const select = getSelect();
        if (!select) return;
        try {
            const resp = await fetch(contextPath + '/api/me/memberships', {
                method: 'GET',
                headers: {
                    'Accept': 'application/json'
                },
                credentials: 'same-origin',
                cache: 'no-store'
            });
            if (!resp.ok) {
                if (resp.status === 401) {
                    select.innerHTML = '<option value="" disabled selected>Chưa đăng nhập</option>';
                    return;
                }
                throw new Error('Lỗi tải danh sách tổ chức (mã ' + resp.status + ')');
            }
            const json = await resp.json();
            const list = Array.isArray(json.data) ? json.data : (json.data?.memberships || json.data?.items || (
                Array.isArray(json) ? json : []));
            if (!list || list.length === 0) {
                select.innerHTML = '<option value="" disabled selected>Chưa tham gia tổ chức nào</option>';
                window.dispatchEvent(new CustomEvent('tc:org-changed', {
                    detail: {
                        memberships: [],
                        currentOrgId: null,
                        organizationId: null
                    }
                }));
                return;
            }
            const urlParams = new URLSearchParams(window.location.search);
            const preferredOrgId = urlParams.get('organizationId') || urlParams.get('orgId') || localStorage
                .getItem(STORAGE_KEY);
            select.innerHTML = '';
            let selectedItem = null;
            list.forEach(function(item) {
                const orgId = item.organizationId || item.id;
                const orgName = item.organizationName || item.name || 'Tổ chức';
                const role = item.role || '';
                const roleLabel = role === 'MANAGER' ? 'Quản lý' : (role === 'CHECK_IN_STAFF' ?
                    'Soát vé' : role);
                const isActive = item.isActive !== undefined ? item.isActive : (item.active !==
                    undefined ? item.active : true);
                const opt = document.createElement('option');
                opt.value = orgId;
                opt.textContent = orgName + (roleLabel ? ' (' + roleLabel + ')' : '') + (!isActive ?
                    ' [Tạm khóa]' : '');
                opt.dataset.role = role;
                opt.dataset.orgName = orgName;
                opt.dataset.active = String(isActive);
                if (!isActive) {
                    opt.disabled = true;
                }
                if (preferredOrgId && orgId.toLowerCase() === preferredOrgId.toLowerCase() &&
                    isActive) {
                    opt.selected = true;
                    selectedItem = item;
                }
                select.appendChild(opt);
            });
            if (!selectedItem) {
                const firstActive = list.find(item => item.isActive !== false);
                if (firstActive) {
                    select.value = firstActive.organizationId || firstActive.id;
                    selectedItem = firstActive;
                } else if (list.length > 0) {
                    select.value = list[0].organizationId || list[0].id;
                    selectedItem = list[0];
                }
            }
            const currentOrgId = select.value;
            if (currentOrgId) {
                localStorage.setItem(STORAGE_KEY, currentOrgId);
            }
            window.tcCurrentOrgId = currentOrgId;
            window.tcMemberships = list;
            window.dispatchEvent(new CustomEvent('tc:org-changed', {
                detail: {
                    organizationId: currentOrgId,
                    organization: selectedItem,
                    memberships: list,
                    role: select.selectedOptions[0]?.dataset.role,
                    orgName: select.selectedOptions[0]?.dataset.orgName
                }
            }));
        } catch (err) {
            console.error('Error fetching memberships:', err);
            select.innerHTML = '<option value="" disabled selected>Lỗi nạp tổ chức</option>';
        }
    }

    function initOrgSwitcher() {
        const select = getSelect();
        if (!select) return;
        select.addEventListener('change', function() {
            const orgId = this.value;
            localStorage.setItem(STORAGE_KEY, orgId);
            window.tcCurrentOrgId = orgId;
            try {
                const url = new URL(window.location);
                if (url.searchParams.has('organizationId')) {
                    url.searchParams.set('organizationId', orgId);
                    window.history.replaceState({}, '', url);
                } else if (url.searchParams.has('orgId')) {
                    url.searchParams.set('orgId', orgId);
                    window.history.replaceState({}, '', url);
                }
            } catch (e) {}
            window.dispatchEvent(new CustomEvent('tc:org-changed', {
                detail: {
                    organizationId: orgId,
                    role: this.selectedOptions[0]?.dataset.role,
                    orgName: this.selectedOptions[0]?.dataset.orgName
                }
            }));
        });
        const refreshBtn = document.getElementById('tcOrgRefreshBtn');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => fetchMemberships());
        }
        fetchMemberships();
    }
    window.TcOrgContext = {
        getCurrentOrgId: () => getSelect()?.value || localStorage.getItem(STORAGE_KEY),
        refresh: fetchMemberships
    };
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initOrgSwitcher);
    } else {
        initOrgSwitcher();
    }
})();
