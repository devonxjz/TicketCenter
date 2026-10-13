<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!-- Header & Navbar (Thanh tìm kiếm dài, cân đối, tiện dụng tối đa) -->
<nav class="navbar navbar-custom sticky-top">
    <div class="container d-flex align-items-center justify-content-between flex-nowrap">
        
        <!-- Logo Thương hiệu -->
        <a class="navbar-brand-custom flex-shrink-0" href="${pageContext.request.contextPath}/">
            TicketsCenter
        </a>

        <!-- Khung tìm kiếm chuẩn ban đầu -->
        <div class="search-wrapper d-none d-md-flex">
            <div class="location-box">
                <select class="location-select">
                    <option value="">Toàn quốc</option>
                    <option value="HN">Hà Nội</option>
                    <option value="HCM">TP. Hồ Chí Minh</option>
                    <option value="DN">Đà Nẵng</option>
                </select>
            </div>
            <input type="text" class="search-input" placeholder="Tìm kiếm sự kiện, nghệ sĩ, địa điểm biểu diễn...">
            <button class="search-btn">Tìm kiếm</button>
        </div>

        <!-- Cụm tài khoản -->
        <div class="d-flex align-items-center gap-2 flex-shrink-0">
            <c:choose>
                <c:when test="${not empty sessionScope.TC_CURRENT_USER}">
                    <div class="dropdown">
                        <button class="btn btn-outline-dark dropdown-toggle d-flex align-items-center gap-2 rounded-0 py-2 px-3 fw-semibold" type="button" data-bs-toggle="dropdown" aria-expanded="false">
                            <i class="fa-solid fa-circle-user fs-5 text-primary"></i>
                            <span class="d-none d-sm-inline">Tài khoản</span>
                        </button>
                        <ul class="dropdown-menu dropdown-menu-end shadow-sm rounded-0 border-secondary-subtle">
                            <c:if test="${sessionScope.TC_CURRENT_USER.isAdmin()}">
                                <li><a class="dropdown-item py-2 fw-bold text-danger" href="${pageContext.request.contextPath}/admin/dashboard"><i class="fa-solid fa-shield-halved me-2"></i>Bảng Quản trị Admin</a></li>
                                <li><hr class="dropdown-divider"></li>
                            </c:if>
                            <c:if test="${sessionScope.TC_CURRENT_USER.isManager()}">
                                <li><a class="dropdown-item py-2 fw-semibold text-primary" href="${pageContext.request.contextPath}/organizer/dashboard"><i class="fa-solid fa-building me-2"></i>Kênh Ban tổ chức</a></li>
                            </c:if>
                            <c:if test="${sessionScope.TC_CURRENT_USER.isStaff()}">
                                <li><a class="dropdown-item py-2 fw-semibold text-success" href="${pageContext.request.contextPath}/organizer/checkin-select"><i class="fa-solid fa-qrcode me-2"></i>Kênh Soát vé Check-in</a></li>
                                <li><hr class="dropdown-divider"></li>
                            </c:if>
                            <li><a class="dropdown-item py-2" href="${pageContext.request.contextPath}/profile"><i class="fa-solid fa-user me-2 text-muted"></i>Hồ sơ cá nhân</a></li>
                            <li><a class="dropdown-item py-2" href="${pageContext.request.contextPath}/me/orders"><i class="fa-solid fa-receipt me-2 text-muted"></i>Đơn hàng của tôi</a></li>
                            <li><a class="dropdown-item py-2" href="${pageContext.request.contextPath}/me/tickets"><i class="fa-solid fa-ticket me-2 text-muted"></i>Vé điện tử</a></li>
                            <c:if test="${sessionScope.TC_CURRENT_USER.isCustomerOnly()}">
                                <li><a class="dropdown-item py-2" href="${pageContext.request.contextPath}/organizer-request"><i class="fa-solid fa-handshake me-2 text-muted"></i>Đăng ký mở ban tổ chức</a></li>
                            </c:if>
                            <li><hr class="dropdown-divider"></li>
                            <li><a class="dropdown-item py-2 text-danger" href="${pageContext.request.contextPath}/auth/logout"><i class="fa-solid fa-right-from-bracket me-2"></i>Đăng xuất</a></li>
                        </ul>
                    </div>
                </c:when>
                <c:otherwise>
                    <a href="${pageContext.request.contextPath}/auth/login" class="btn-outline-custom d-none d-sm-inline-flex">Đăng nhập</a>
                    <a href="${pageContext.request.contextPath}/auth/register" class="btn-primary-custom">Đăng ký</a>
                </c:otherwise>
            </c:choose>
            <a href="${pageContext.request.contextPath}/me/tickets" class="btn-outline-custom p-0 d-inline-flex align-items-center justify-content-center" title="Vé & Đơn hàng của tôi" style="width: 42px; height: 40px;">
                <i class="fa-solid fa-ticket fs-6"></i>
            </a>
        </div>
    </div>
</nav>
