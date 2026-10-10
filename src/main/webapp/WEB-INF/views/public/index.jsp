<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TicketsCenter — Mua Vé Sự Kiện & Giải Trí Trực Tuyến</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
</head>
<body>
    <div class="small text-center py-2 bg-light text-muted" role="note">Prototype tĩnh • Dữ liệu và thao tác mô phỏng • Chưa kết nối backend, email hoặc cổng thanh toán.</div>

    <!-- Header & Navbar -->
    <jsp:include page="/WEB-INF/views/layout/header.jsp" />

    <!-- Main Content Container -->
    <main class="container my-4">
        
        <!-- Hero Slider (Phẳng, sắc nét) -->
        <div id="heroCarousel" class="carousel slide hero-banner mt-2 mb-4" data-bs-ride="carousel">
            <div class="carousel-inner">
                <div class="carousel-item active">
                    <img src="https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?auto=format&fit=crop&w=1600&q=85" alt="Live Concert">
                </div>
                <div class="carousel-item">
                    <img src="https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1600&q=85" alt="Hội thảo">
                </div>
                <div class="carousel-item">
                    <img src="https://images.unsplash.com/photo-1554907984-15263bfd63bd?auto=format&fit=crop&w=1600&q=85" alt="Triển lãm Di sản">
                </div>
            </div>
            <button class="carousel-control-prev" type="button" data-bs-target="#heroCarousel" data-bs-slide="prev">
                <span class="carousel-control-prev-icon" style="filter: invert(1) grayscale(100); width: 24px; height: 24px;"></span>
            </button>
            <button class="carousel-control-next" type="button" data-bs-target="#heroCarousel" data-bs-slide="next">
                <span class="carousel-control-next-icon" style="filter: invert(1) grayscale(100); width: 24px; height: 24px;"></span>
            </button>
        </div>

        <!-- Category Navigation Tabs -->
        <div class="category-nav">
            <a href="#" class="active">Tất cả</a>
            <a href="#">Nhạc sống</a>
            <a href="#">Fan Meeting</a>
            <a href="#">Sân khấu & Nghệ thuật</a>
            <a href="#">Thể thao</a>
            <a href="#">Hội thảo & Cộng đồng</a>
            <a href="#">Khoá học</a>
            <a href="#">Nightlife</a>
            <a href="#">Tham quan & Triển lãm</a>
        </div>

        <!-- Section Title -->
        <div class="d-flex align-items-center justify-content-between mb-4">
            <h2 class="section-title mb-0">Sự kiện nổi bật</h2>
            <a href="#" class="small fw-bold text-dark text-decoration-none">
                Xem toàn bộ &rarr;
            </a>
        </div>

        <!-- Event Cards Grid (12 sự kiện phong phú, bố cục chặt chẽ, không bị trống) -->
        <div class="row g-4 mb-4">
            <!-- Card 1 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=650&q=80" alt="Chung kết Thể thao">
                        <span class="event-category-badge">Thể thao</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Chung Kết Quốc Gia Đấu Trường Mùa Đông 2026</h3>
                        <div class="event-meta">
                            07/11/2026 • Sân vận động Mỹ Đình, Hà Nội
                        </div>
                        <div class="event-price">Từ 250.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 2 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1470225620780-dba8ba36b745?auto=format&fit=crop&w=650&q=80" alt="Concert Mùa Thu">
                        <span class="event-category-badge">Nhạc sống</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Đại Nhạc Hội Live Concert: Giai Điệu Mùa Thu 2026</h3>
                        <div class="event-meta">
                            08/11/2026 • Trung tâm Hội nghị Quốc gia, Hà Nội
                        </div>
                        <div class="event-price">Từ 600.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 3 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1460723237483-7a6dc9d0b212?auto=format&fit=crop&w=650&q=80" alt="Nhạc kịch">
                        <span class="event-category-badge">Sân khấu</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Vở Nhạc Kịch: Huyền Thoại Đất Phương Nam</h3>
                        <div class="event-meta">
                            03/10/2026 • Nhà hát Thành phố, TP. Hồ Chí Minh
                        </div>
                        <div class="event-price">Từ 300.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 4 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1554907984-15263bfd63bd?auto=format&fit=crop&w=650&q=80" alt="Bảo tàng Phụ nữ VN">
                        <span class="event-category-badge">Tham quan</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Vé Tham Quan Bảo Tàng Phụ Nữ Việt Nam & Triển Lãm</h3>
                        <div class="event-meta">
                            Hàng ngày 08:30 – 17:30 • 36 Lý Thường Kiệt, Hà Nội
                        </div>
                        <div class="event-price">Từ 40.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 5 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=650&q=80" alt="Fan Meeting">
                        <span class="event-category-badge">Fan Meeting</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Fan Meeting: Giao Lưu & Ký Tặng Cùng Thần Tượng 2026</h3>
                        <div class="event-meta">
                            12/10/2026 • Nhà thi đấu Quân khu 7, TP.HCM
                        </div>
                        <div class="event-price">Từ 500.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 6 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1505373877841-8d25f7d46678?auto=format&fit=crop&w=650&q=80" alt="Tech Summit">
                        <span class="event-category-badge">Hội thảo</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Diễn Đàn Công Nghệ Đổi Mới Sáng Tạo TechSummit 2026</h3>
                        <div class="event-meta">
                            18/11/2026 • GEM Center, Quận 1, TP.HCM
                        </div>
                        <div class="event-price">Từ 350.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 7 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?auto=format&fit=crop&w=650&q=80" alt="Nightlife Jazz">
                        <span class="event-category-badge">Nightlife</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Đêm Nhạc Jazz Dưới Trăng: Moonlit Acoustic & Wine</h3>
                        <div class="event-meta">
                            Hàng tuần Thứ Bảy • Sunset Lounge InterContinental, Hà Nội
                        </div>
                        <div class="event-price">Từ 450.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 8 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=650&q=80" alt="Triển lãm Tranh">
                        <span class="event-category-badge">Triển lãm</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Triển Lãm Nghệ Thuật Đương Đại "Sắc Màu Di Sản"</h3>
                        <div class="event-meta">
                            25/09 – 25/10/2026 • Trung tâm VCCA, Hà Nội
                        </div>
                        <div class="event-price">Từ 80.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 9 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1452626038306-9aae5e071dd3?auto=format&fit=crop&w=650&q=80" alt="Marathon">
                        <span class="event-category-badge">Thể thao</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Giải Chạy Marathon Quốc Tế Di Sản Hà Nội 2026</h3>
                        <div class="event-meta">
                            15/11/2026 • Quảng trường Đông Kinh Nghĩa Thục, Hà Nội
                        </div>
                        <div class="event-price">Từ 350.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 10 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=650&q=80" alt="Lễ hội ẩm thực">
                        <span class="event-category-badge">Lễ hội</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Lễ Hội Ẩm Thực Đường Phố & Giao Lưu Văn Hóa 2026</h3>
                        <div class="event-meta">
                            28/10 – 30/10/2026 • Công viên 23/9, TP. Hồ Chí Minh
                        </div>
                        <div class="event-price">Từ 50.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 11 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1565193566173-7a0ee3dbe261?auto=format&fit=crop&w=650&q=80" alt="Workshop Gốm">
                        <span class="event-category-badge">Khóa học</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Workshop Trải Nghiệm Nghệ Thuật Gốm Thủ Công</h3>
                        <div class="event-meta">
                            Chủ Nhật hàng tuần • Làng gốm Bát Tràng, Hà Nội
                        </div>
                        <div class="event-price">Từ 150.000 đ</div>
                    </div>
                </a>
            </div>

            <!-- Card 12 -->
            <div class="col-12 col-sm-6 col-lg-3">
                <a href="${pageContext.request.contextPath}/event-detail" class="event-card">
                    <div class="event-image">
                        <img src="https://images.unsplash.com/photo-1465847899084-d164df4dedc6?auto=format&fit=crop&w=650&q=80" alt="Hòa nhạc thính phòng">
                        <span class="event-category-badge">Nhạc sống</span>
                    </div>
                    <div class="event-content">
                        <h3 class="event-title">Hòa Nhạc Giao Hưởng Mùa Đông: Bản Giao Hưởng Bất Diệt</h3>
                        <div class="event-meta">
                            24/12/2026 • Nhà hát Lớn TP. Hồ Chí Minh
                        </div>
                        <div class="event-price">Từ 400.000 đ</div>
                    </div>
                </a>
            </div>
        </div>

        <!-- Pagination -->
        <div class="pagination-container">
            <a href="#" class="page-link-custom">&lsaquo;</a>
            <a href="#" class="page-link-custom active">1</a>
            <a href="#" class="page-link-custom">2</a>
            <a href="#" class="page-link-custom">3</a>
            <a href="#" class="page-link-custom">&rsaquo;</a>
        </div>

    </main>

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
                        <li><a href="${pageContext.request.contextPath}/organizer-request">Đăng ký Nhà tổ chức</a></li>
                        <li><a href="${pageContext.request.contextPath}/organizer/dashboard">Cổng Ban tổ chức</a></li>
                        <li><a href="${pageContext.request.contextPath}/checkin/select">Kiểm soát vé vào cửa</a></li>
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
                            <li><a href="${pageContext.request.contextPath}/organizer-request">• UI-09: Đăng ký tổ chức</a></li>
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
                            <li><a href="${pageContext.request.contextPath}/checkin/select">• UI-15: Chọn sự kiện check-in</a></li>
                            <li><a href="${pageContext.request.contextPath}/checkin/scanner">• UI-16: Quét QR & Nhập mã</a></li>
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
</body>
</html>
