// Page script for public/organizer-request.jsp; reads the context path and CSRF token from the layout meta tags.
const contextPath = document.querySelector('meta[name="context-path"]').content;
const csrfToken = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content') || '';

function showAlert(type, message) {
    const alertBox = document.getElementById('alertMessage');
    if (!alertBox) return;
    alertBox.className = 'alert alert-' + type + ' mb-4';
    alertBox.innerHTML = message;
    alertBox.classList.remove('d-none');
    alertBox.scrollIntoView({
        behavior: 'smooth',
        block: 'nearest'
    });
}

function clearAlert() {
    const alertBox = document.getElementById('alertMessage');
    if (!alertBox) return;
    alertBox.className = 'd-none alert mb-4';
    alertBox.innerHTML = '';
}

function getStatusBadge(status) {
    switch (status) {
        case 'PENDING_APPROVAL':
            return '<span class="badge bg-warning text-dark"><i class="fa-regular fa-clock me-1"></i>Chờ duyệt</span>';
        case 'APPROVED':
            return '<span class="badge bg-success"><i class="fa-solid fa-check me-1"></i>Đã phê duyệt</span>';
        case 'REJECTED':
            return '<span class="badge bg-danger"><i class="fa-solid fa-xmark me-1"></i>Bị từ chối</span>';
        default:
            return '<span class="badge bg-secondary">' + (status || 'Chưa xác định') + '</span>';
    }
}

function formatDate(instantStr) {
    if (!instantStr) return '-';
    try {
        const date = new Date(instantStr);
        return date.toLocaleString('vi-VN');
    } catch (e) {
        return instantStr;
    }
}
async function loadMyRequests() {
    const tbody = document.getElementById('requestsTableBody');
    if (!tbody) return;
    try {
        const response = await fetch(contextPath + '/api/me/organization-requests', {
            headers: {
                'Accept': 'application/json'
            }
        });
        if (response.status === 401) {
            tbody.innerHTML =
                '<tr><td colspan="5" class="text-center py-3 text-warning">Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.</td></tr>';
            return;
        }
        if (!response.ok) {
            tbody.innerHTML =
                '<tr><td colspan="5" class="text-center py-3 text-danger">Không thể tải danh sách yêu cầu.</td></tr>';
            return;
        }
        const result = await response.json();
        const list = result.data || [];
        if (list.length === 0) {
            tbody.innerHTML =
                '<tr><td colspan="5" class="text-center py-3 text-muted">Bạn chưa gửi yêu cầu tạo tổ chức nào.</td></tr>';
            return;
        }
        tbody.innerHTML = '';
        let hasPending = false;
        for (const item of list) {
            if (item.status === 'PENDING_APPROVAL') hasPending = true;
            const row = tbody.insertRow();
            row.insertCell().textContent = item.name || '';
            row.insertCell().textContent = item.contactEmail || '';
            row.insertCell().textContent = formatDate(item.submittedAt);
            row.insertCell().innerHTML = getStatusBadge(item.status);
            const reasonCell = row.insertCell();
            reasonCell.className = 'text-danger small';
            reasonCell.textContent = item.rejectionReason || '-';
        }
        const submitBtn = document.getElementById('btnSubmit');
        if (submitBtn && hasPending) {
            submitBtn.disabled = true;
            showAlert('warning',
                '<i class="fa-solid fa-triangle-exclamation me-2"></i>Bạn đang có một yêu cầu tạo tổ chức đang chờ xét duyệt. Vui lòng chờ phản hồi từ ban quản trị trước khi gửi yêu cầu mới.'
                );
        }
    } catch (err) {
        console.error('Error loading requests:', err);
        tbody.innerHTML =
            '<tr><td colspan="5" class="text-center py-3 text-danger">Lỗi kết nối khi tải danh sách.</td></tr>';
    }
}
async function submitOrg() {
    clearAlert();
    const btn = document.getElementById('btnSubmit');
    const spinner = document.getElementById('btnSpinner');
    const name = document.getElementById('orgName')?.value?.trim();
    const descriptionInput = document.getElementById('orgDescription')?.value?.trim() || '';
    const contactEmail = document.getElementById('contactEmail')?.value?.trim();
    const contactPhone = document.getElementById('contactPhone')?.value?.trim();
    const eventTitle = document.getElementById('eventTitle')?.value?.trim() || '';
    const eventDate = document.getElementById('eventDate')?.value?.trim() || '';
    const eventStartTime = document.getElementById('eventStartTime')?.value?.trim() || '';
    const eventEndTime = document.getElementById('eventEndTime')?.value?.trim() || '';
    const eventVenue = document.getElementById('eventVenue')?.value?.trim() || '';
    if (!name || name.length < 3 || name.length > 150) {
        showAlert('danger', 'Tên tổ chức phải từ 3 đến 150 ký tự.');
        return;
    }
    if (!contactEmail) {
        showAlert('danger', 'Vui lòng cung cấp email liên hệ.');
        return;
    }
    if (contactPhone && !/^0\d{9}$/.test(contactPhone)) {
        showAlert('danger', 'Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.');
        return;
    }
    let description = descriptionInput;
    const scheduleParts = [];
    if (eventTitle) scheduleParts.push('Sự kiện ' + eventTitle);
    if (eventDate) scheduleParts.push('Ngày ' + eventDate);
    if (eventStartTime || eventEndTime) {
        scheduleParts.push('Giờ ' + (eventStartTime || '') + (eventEndTime ? (' đến ' + eventEndTime) : ''));
    }
    if (eventVenue) scheduleParts.push('Địa điểm ' + eventVenue);
    if (scheduleParts.length > 0) {
        const scheduleText = '[Lịch trình dự kiến] ' + scheduleParts.join(' | ');
        description = description ? (description + '\n' + scheduleText) : scheduleText;
    }
    btn.disabled = true;
    spinner.classList.remove('d-none');
    try {
        const response = await fetch(contextPath + '/api/organization-requests', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-Token': csrfToken,
                'Accept': 'application/json'
            },
            body: JSON.stringify({
                name: name,
                description: description,
                contactEmail: contactEmail,
                contactPhone: contactPhone,
                eventTitle: eventTitle,
                eventDate: eventDate,
                eventStartTime: eventStartTime,
                eventEndTime: eventEndTime,
                eventVenue: eventVenue
            })
        });
        const data = await response.json();
        if (response.status === 201) {
            showAlert('success',
                '<i class="fa-solid fa-circle-check me-2"></i>Hồ sơ đăng ký tổ chức đã được gửi thành công. Trạng thái PENDING_APPROVAL. Ban quản trị sẽ sớm xem xét hồ sơ của bạn.'
                );
            document.getElementById('orgRequestForm')?.reset();
            await loadMyRequests();
        } else if (response.status === 409) {
            showAlert('warning', '<i class="fa-solid fa-circle-exclamation me-2"></i>' + (data?.error?.message ||
                'Bạn đang có một yêu cầu tạo tổ chức đang chờ xét duyệt.'));
            await loadMyRequests();
        } else if (response.status === 401) {
            showAlert('warning', 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.');
        } else {
            showAlert('danger', '<i class="fa-solid fa-triangle-exclamation me-2"></i>' + (data?.error?.message ||
                'Không thể gửi hồ sơ. Vui lòng kiểm tra lại thông tin.'));
            btn.disabled = false;
        }
    } catch (err) {
        console.error('Submit error:', err);
        showAlert('danger', 'Lỗi kết nối máy chủ. Vui lòng thử lại sau.');
        btn.disabled = false;
    } finally {
        spinner.classList.add('d-none');
    }
}
document.addEventListener('DOMContentLoaded', function() {
    if (document.getElementById('requestsTableBody')) {
        loadMyRequests();
    }
});
