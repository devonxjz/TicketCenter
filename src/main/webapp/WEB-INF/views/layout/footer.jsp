<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!-- Footer -->
<footer>
    <div class="container">
        <div class="row g-4">
            <div class="col-lg-4">
                <a class="navbar-brand-custom mb-3 d-inline-block" href="${pageContext.request.contextPath}/">
                    TicketsCenter
                </a>
                <p class="small text-muted pe-lg-4">
                    Hệ thống phân phối vé sự kiện và giải pháp kiểm soát ra vào chuyên nghiệp. Cung cấp nền tảng bán vé hiện đại, an toàn và minh bạch cho ban tổ chức và khán giả.
                </p>
            </div>

            <div class="col-6 col-lg-2">
                <h4 class="footer-title">Khám phá</h4>
                <ul class="footer-list">
                    <li><a href="${pageContext.request.contextPath}/">Nhạc sống</a></li>
                    <li><a href="${pageContext.request.contextPath}/">Sân khấu kịch</a></li>
                    <li><a href="${pageContext.request.contextPath}/">Giải đấu Thể thao</a></li>
                    <li><a href="${pageContext.request.contextPath}/">Bảo tàng & Di sản</a></li>
                </ul>
            </div>

            <div class="col-6 col-lg-3">
                <h4 class="footer-title">Khách hàng</h4>
                <ul class="footer-list">
                    <li><a href="${pageContext.request.contextPath}/my-orders">Đơn hàng của tôi</a></li>
                    <li><a href="${pageContext.request.contextPath}/tickets">Vé điện tử & Mã QR</a></li>
                    <li><a href="${pageContext.request.contextPath}/refund-request">Chính sách hoàn vé</a></li>
                    <li><a href="${pageContext.request.contextPath}/profile">Thông tin tài khoản</a></li>
                </ul>
            </div>

            <div class="col-lg-3">
                <h4 class="footer-title">Đối tác & Quản lý</h4>
                <ul class="footer-list">
                    <li><a href="${pageContext.request.contextPath}/organizer/request">Đăng ký Nhà tổ chức</a></li>
                    <li><a href="${pageContext.request.contextPath}/organizer/dashboard">Cổng Ban tổ chức</a></li>
                    <li><a href="${pageContext.request.contextPath}/checkin-select">Kiểm soát vé vào cửa</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/dashboard">Cổng Quản trị hệ thống</a></li>
                </ul>
            </div>
        </div>

        <!-- Khối tổng quan 24 Page Frames (Khôi phục lại chính xác theo yêu cầu tại 1 vị trí duy nhất này) -->
        <div class="mt-5 pt-4 border-top">
            <h6 class="small text-uppercase fw-bold text-dark mb-3">SƠ ĐỒ 24 PAGE FRAMES (SPEC.MD):</h6>
            <div class="row g-3 small">
                <div class="col-6 col-md-3">
                    <strong class="d-block text-dark mb-1">Khách & Người mua:</strong>
                    <ul class="list-unstyled text-muted mb-0">
                        <li><a href="${pageContext.request.contextPath}/" class="text-danger fw-semibold">• UI-01: Trang chủ & Tìm kiếm</a></li>
                        <li><a href="${pageContext.request.contextPath}/event-detail">• UI-02: Chi tiết & Chọn ghế</a></li>
                        <li><a href="${pageContext.request.contextPath}/auth/login">• UI-03: Đăng nhập & OTP</a></li>
                        <li><a href="${pageContext.request.contextPath}/checkout">• UI-04: Giữ vé 10p & Thanh toán</a></li>
                        <li><a href="${pageContext.request.contextPath}/payment-result">• UI-05: Kết quả thanh toán</a></li>
                        <li><a href="${pageContext.request.contextPath}/my-orders">• UI-06: Đơn của tôi</a></li>
                        <li><a href="${pageContext.request.contextPath}/tickets">• UI-07: Vé & Mã QR</a></li>
                        <li><a href="${pageContext.request.contextPath}/refund-request">• UI-08: Yêu cầu hoàn vé</a></li>
                        <li><a href="${pageContext.request.contextPath}/profile">• UI-24: Hồ sơ tài khoản</a></li>
                    </ul>
                </div>
                <div class="col-6 col-md-3">
                    <strong class="d-block text-dark mb-1">Đơn vị Tổ chức:</strong>
                    <ul class="list-unstyled text-muted mb-0">
                        <li><a href="${pageContext.request.contextPath}/organizer/request">• UI-09: Đăng ký tổ chức</a></li>
                        <li><a href="${pageContext.request.contextPath}/organizer/dashboard">• UI-10: Tổng quan tổ chức</a></li>
                        <li><a href="${pageContext.request.contextPath}/organizer/members">• UI-11: Quản lý thành viên</a></li>
                        <li><a href="${pageContext.request.contextPath}/organizer/events">• UI-12: Sự kiện tổ chức</a></li>
                        <li><a href="${pageContext.request.contextPath}/organizer/event-editor">• UI-13: Biên tập sự kiện & ghế</a></li>
                        <li><a href="${pageContext.request.contextPath}/organizer/coupons">• UI-14: Mã giảm giá (Cap 30%)</a></li>
                        <li><a href="${pageContext.request.contextPath}/organizer/reports">• UI-17: Báo cáo & Xuất CSV</a></li>
                    </ul>
                </div>
                <div class="col-6 col-md-3">
                    <strong class="d-block text-dark mb-1">Soát vé tại cửa:</strong>
                    <ul class="list-unstyled text-muted mb-0">
                        <li><a href="${pageContext.request.contextPath}/checkin-select">• UI-15: Chọn sự kiện check-in</a></li>
                        <li><a href="${pageContext.request.contextPath}/checkin-scanner">• UI-16: Quét QR & Nhập mã</a></li>
                    </ul>
                </div>
                <div class="col-6 col-md-3">
                    <strong class="d-block text-dark mb-1">Quản trị viên:</strong>
                    <ul class="list-unstyled text-muted mb-0">
                        <li><a href="${pageContext.request.contextPath}/admin/dashboard">• UI-18: Tổng quan quản trị</a></li>
                        <li><a href="${pageContext.request.contextPath}/admin/org-approval">• UI-19: Duyệt tổ chức</a></li>
                        <li><a href="${pageContext.request.contextPath}/admin/event-approval">• UI-20: Duyệt / Hủy sự kiện</a></li>
                        <li><a href="${pageContext.request.contextPath}/admin/refund-approval">• UI-21: Duyệt hoàn vé</a></li>
                        <li><a href="${pageContext.request.contextPath}/admin/settlements">• UI-22: Quyết toán hoa hồng</a></li>
                        <li><a href="${pageContext.request.contextPath}/admin/audit-logs">• UI-23: Báo cáo & Nhật ký Audit</a></li>
                    </ul>
                </div>
            </div>
        </div>

        <div class="d-flex flex-column flex-md-row justify-content-between align-items-center pt-4 mt-4 border-top small text-muted">
            <div>
                &copy; 2026 TicketsCenter. Bản quyền thuộc về TicketsCenter Việt Nam.
            </div>
            <div class="d-flex gap-3 mt-2 mt-md-0">
                <a href="#" class="text-muted">Điều khoản sử dụng</a>
                <span>•</span>
                <a href="#" class="text-muted">Chính sách bảo mật</a>
            </div>
        </div>
    </div>
</footer>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
