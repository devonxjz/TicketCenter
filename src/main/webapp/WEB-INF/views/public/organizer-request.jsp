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
        <div class="panel-box mx-auto bg-white p-4 rounded shadow-sm tc-panel-narrow">
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

                    <!-- requests already sent -->
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

    <script defer src="${pageContext.request.contextPath}/assets/js/organization/organizer-request.js"></script>
</body>

</html>
