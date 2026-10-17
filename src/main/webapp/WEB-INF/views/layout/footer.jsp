<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<footer class="tc-footer">
    <div class="container">
        <div class="row g-4">
            <div class="col-lg-5">
                <a class="navbar-brand-custom mb-3 d-inline-block"
                    href="${pageContext.request.contextPath}/">TicketsCenter</a>
                <p class="small text-muted pe-lg-5">Khám phá sự kiện, đặt vé và quản lý vé điện tử trong cùng một tài
                    khoản.</p>
            </div>
            <div class="col-6 col-lg-3">
                <h4 class="footer-title">Khách hàng</h4>
                <ul class="footer-list">
                    <li>
                        <a href="${pageContext.request.contextPath}/">Khám phá sự kiện</a>
                    </li>
                    <li>
                        <a href="${pageContext.request.contextPath}/me/orders">Đơn hàng của tôi</a>
                    </li>
                    <li>
                        <a href="${pageContext.request.contextPath}/me/tickets">Vé điện tử &amp; Mã QR</a>
                    </li>
                    <li>
                        <a href="${pageContext.request.contextPath}/my-refunds">Yêu cầu hoàn vé</a>
                    </li>
                </ul>
            </div>
            <div class="col-6 col-lg-4">
                <h4 class="footer-title">Tổ chức sự kiện</h4>
                <ul class="footer-list">
                    <li>
                        <a href="${pageContext.request.contextPath}/organizer/request">Đăng ký nhà tổ chức</a>
                    </li>
                    <li>
                        <a href="${pageContext.request.contextPath}/profile">Tài khoản của bạn</a>
                    </li>
                </ul>
            </div>
        </div>
        <div class="tc-footer-bottom">
            <span>&copy; 2026 TicketsCenter.</span>
            <nav aria-label="Tài khoản">
                <a href="${pageContext.request.contextPath}/profile">Thông tin tài khoản</a><a
                    href="${pageContext.request.contextPath}/auth/login">Đăng nhập</a>
            </nav>
        </div>
    </div>
</footer>
