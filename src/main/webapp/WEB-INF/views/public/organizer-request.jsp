<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="${sessionScope.TC_CSRF_TOKEN}">
    <title>Đăng Ký Trở Thành Nhà Tổ Chức | TicketsCenter</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
</head>

<body class="buyer-page bg-light min-h-screen d-flex flex-column justify-content-between">

    <!-- Header & Navbar -->
    <nav class="navbar navbar-custom sticky-top">
        <div class="container d-flex align-items-center justify-content-between">
            <a class="navbar-brand-custom" href="${pageContext.request.contextPath}/">
                TicketsCenter
            </a>
            <div class="d-flex align-items-center gap-3">
                <a href="${pageContext.request.contextPath}/" class="small fw-semibold text-muted text-decoration-none">
                    Quay lại trang chủ
                </a>
            </div>
        </div>
    </nav>

    <!-- Main Content -->
    <main class="container my-5">
        <div class="panel-box mx-auto bg-white p-4 rounded shadow-sm" style="max-width: 860px;">
            <div class="panel-header d-flex justify-content-between align-items-center mb-4 pb-3 border-bottom">
                <div>
                    <h1 class="h4 fw-bold mb-1 text-dark">Đăng Ký Đối Tác Ban Tổ Chức</h1>
                    <span class="small text-muted">Trở thành đối tác chính thức phát hành vé sự kiện trên nền
                        tảng TicketsCenter</span>
                </div>
                <span class="badge bg-secondary text-uppercase px-3 py-2">Hồ sơ ban tổ chức</span>
            </div>

            <div class="alert alert-info small mb-4" role="alert">
                <i class="fa-solid fa-circle-info me-2"></i>
                Sau khi hồ sơ được phê duyệt, tài khoản đăng ký sẽ trở thành Quản lý chính của tổ chức và có thể
                bắt đầu tạo chương trình sự kiện, cấu hình phân hạng vé và quản lý vận hành.
            </div>

            <div id="alertMessage" class="d-none alert mb-4" role="alert"></div>

            <c:choose>
                <c:when test="${empty sessionScope.TC_CURRENT_USER}">
                    <div class="text-center py-5 border rounded bg-light">
                        <i class="fa-solid fa-lock fa-2x text-muted mb-3"></i>
                        <h5 class="fw-bold">Yêu cầu đăng nhập</h5>
                        <p class="text-muted small mb-3">Bạn cần đăng nhập tài khoản để gửi hồ sơ và theo dõi
                            tiến độ xét duyệt tổ chức.</p>
                        <a href="${pageContext.request.contextPath}/auth/login?redirect=${pageContext.request.contextPath}/organizer-request"
                            class="btn btn-primary-custom px-4 py-2 fw-semibold">
                            Đăng nhập ngay
                        </a>
                    </div>
                </c:when>
                <c:otherwise>
                    <!-- Form Submit -->
                    <form id="orgRequestForm" onsubmit="event.preventDefault(); submitOrg();">
                        <h6 class="fw-bold mb-3 border-bottom pb-2 text-dark">1. Thông tin tổ chức</h6>
                        <div class="row g-3 mb-4">
                            <div class="col-md-12">
                                <label for="orgName" class="form-label fw-semibold small">Tên tổ chức / doanh
                                    nghiệp <span class="text-danger">*</span></label>
                                <input type="text" class="form-control" id="orgName" name="name" required minlength="3"
                                    maxlength="150" placeholder="VD Công ty TNHH Sự Kiện & Giải Trí Việt Nam">
                                <div class="form-text small">Tên chính thức hiển thị trên vé và trang sự kiện
                                    (3-150 ký tự).</div>
                            </div>
                            <div class="col-md-12">
                                <label for="orgDescription" class="form-label fw-semibold small">Mô tả giới
                                    thiệu</label>
                                <textarea class="form-control" id="orgDescription" name="description" rows="3"
                                    maxlength="2000"
                                    placeholder="Giới thiệu về năng lực tổ chức, các sự kiện tiêu biểu hoặc dự kiến thực hiện"></textarea>
                                <div class="form-text small">Tối đa 2000 ký tự.</div>
                            </div>
                        </div>

                        <h6 class="fw-bold mb-3 border-bottom pb-2 text-dark">2. Thông tin liên hệ</h6>
                        <div class="row g-3 mb-4">
                            <div class="col-md-6">
                                <label for="contactEmail" class="form-label fw-semibold small">Email liên hệ
                                    <span class="text-danger">*</span></label>
                                <input type="email" class="form-control" id="contactEmail" name="contactEmail"
                                    value="<c:out value='${sessionScope.TC_CURRENT_USER.email()}'/>" ${not empty
                                    sessionScope.TC_CURRENT_USER.email() ? 'readonly' : '' } required
                                    placeholder="contact@company.vn">
                                <div class="form-text small">Email lấy tự động từ tài khoản của bạn. Địa chỉ
                                    nhận thông báo và kết quả xét duyệt.</div>
                            </div>
                            <div class="col-md-6">
                                <label for="contactPhone" class="form-label fw-semibold small">Số điện thoại
                                    liên hệ</label>
                                <input type="tel" class="form-control" id="contactPhone" name="contactPhone"
                                    pattern="^0\d{9}$" placeholder="0901234567">
                                <div class="form-text small">Số di động 10 chữ số bắt đầu bằng 0 (tuỳ chọn).
                                </div>
                            </div>
                        </div>

                        <h6 class="fw-bold mb-3 border-bottom pb-2 text-dark">3. Thông tin kế hoạch sự kiện dự
                            kiến (Lịch trình)</h6>
                        <div class="row g-3 mb-4">
                            <div class="col-md-12">
                                <label for="eventTitle" class="form-label fw-semibold small">Tên sự kiện dự
                                    kiến</label>
                                <input type="text" class="form-control" id="eventTitle" name="eventTitle"
                                    maxlength="300" placeholder="VD Hội thảo Âm nhạc và Nghệ thuật Trẻ 2026">
                                <div class="form-text small">Tên sự kiện đầu tiên dự kiến phát hành vé trên nền
                                    tảng.</div>
                            </div>
                            <div class="col-md-4">
                                <label for="eventDate" class="form-label fw-semibold small">Ngày tổ chức</label>
                                <input type="date" class="form-control" id="eventDate" name="eventDate">
                                <div class="form-text small">Ngày diễn ra dự kiến.</div>
                            </div>
                            <div class="col-md-4">
                                <label for="eventStartTime" class="form-label fw-semibold small">Giờ bắt
                                    đầu</label>
                                <input type="time" class="form-control" id="eventStartTime" name="eventStartTime">
                                <div class="form-text small">Thời gian bắt đầu.</div>
                            </div>
                            <div class="col-md-4">
                                <label for="eventEndTime" class="form-label fw-semibold small">Giờ kết
                                    thúc</label>
                                <input type="time" class="form-control" id="eventEndTime" name="eventEndTime">
                                <div class="form-text small">Thời gian kết thúc.</div>
                            </div>
                            <div class="col-md-12">
                                <label for="eventVenue" class="form-label fw-semibold small">Địa điểm tổ chức dự
                                    kiến</label>
                                <input type="text" class="form-control" id="eventVenue" name="eventVenue"
                                    maxlength="300" placeholder="VD Trung tâm Hội nghị Quốc gia, Hà Nội">
                                <div class="form-text small">Địa điểm tổ chức dự kiến.</div>
                            </div>
                        </div>

                        <div class="d-flex gap-3 pt-2 mb-5">
                            <button type="submit" id="btnSubmit" class="btn btn-primary-custom px-4 py-2 fw-semibold">
                                <span id="btnSpinner" class="spinner-border spinner-border-sm me-2 d-none"
                                    role="status"></span>
                                Gửi hồ sơ đăng ký
                            </button>
                            <a href="${pageContext.request.contextPath}/" class="btn btn-outline-secondary px-4 py-2">
                                Hủy bỏ
                            </a>
                        </div>
                    </form>

                    <!-- Danh sách hồ sơ đã gửi -->
                    <div class="mt-4 pt-3 border-top">
                        <div class="d-flex justify-content-between align-items-center mb-3">
                            <h6 class="fw-bold mb-0 text-dark">
                                <i class="fa-solid fa-clock-rotate-left me-2 text-muted"></i>Lịch sử yêu cầu đã
                                gửi
                            </h6>
                            <button type="button" class="btn btn-sm btn-outline-secondary" onclick="loadMyRequests()">
                                <i class="fa-solid fa-arrows-rotate me-1"></i>Làm mới
                            </button>
                        </div>
                        <div id="requestsTableContainer" class="table-responsive">
                            <table class="table table-hover table-bordered align-middle small mb-0">
                                <thead class="table-light">
                                    <tr>
                                        <th>Tên tổ chức</th>
                                        <th>Email liên hệ</th>
                                        <th>Thời gian gửi</th>
                                        <th>Trạng thái</th>
                                        <th>Lý do từ chối / Phản hồi</th>
                                    </tr>
                                </thead>
                                <tbody id="requestsTableBody">
                                    <tr>
                                        <td colspan="5" class="text-center py-3 text-muted">Đang tải danh sách
                                            yêu cầu...</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>
    </main>

    <!-- Footer -->
    <footer class="mt-auto py-4 bg-white border-top">
        <div class="container text-center small text-muted">
            &copy; 2026 TicketsCenter. Đồng hành cùng nhà tổ chức sự kiện chuyên nghiệp.
        </div>
    </footer>

    <script>
        const contextPath = '${pageContext.request.contextPath}';
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
    </script>
</body>

</html>
