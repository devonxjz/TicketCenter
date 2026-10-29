<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!-- Header & Navbar (Thanh tìm kiếm dài, cân đối, tiện dụng tối đa) -->
<a href="#main-content" class="tc-skip-link">Đến nội dung chính</a>
<nav class="navbar navbar-custom sticky-top tc-header" data-site-header
    data-context="${pageContext.request.contextPath}" aria-label="Điều hướng chính">
    <div class="container d-flex align-items-center justify-content-between flex-nowrap">

        <!-- Logo Thương hiệu -->
        <a class="navbar-brand-custom flex-shrink-0" href="${pageContext.request.contextPath}/">
            TicketsCenter
        </a>

        <form class="search-wrapper tc-header-search" role="search" action="${pageContext.request.contextPath}/"
            method="get">
            <select id="header-category" name="categoryId" class="tc-header-category" aria-label="Danh mục sự kiện">
                <option value="">Tất cả danh mục</option>
            </select>
            <input type="search" name="keyword" class="search-input" maxlength="100"
                placeholder="Tìm sự kiện hoặc địa điểm..." aria-label="Tìm sự kiện hoặc địa điểm" autocomplete="off">
            <button class="search-btn" type="submit">Tìm kiếm</button>
            <div id="header-search-dropdown" class="tc-search-dropdown" hidden aria-label="Gợi ý sự kiện" role="region">
            </div>
        </form>

        <!-- Cụm tài khoản -->
        <div class="tc-header-account">
            <c:choose>
                <c:when test="${not empty sessionScope.TC_CURRENT_USER}">
                    <div class="dropdown">
                        <button
                            class="btn btn-outline-dark dropdown-toggle d-flex align-items-center gap-2 rounded-0 py-2 px-3 fw-semibold"
                            type="button" data-bs-toggle="dropdown" aria-expanded="false">
                            <i class="fa-solid fa-circle-user fs-5 text-danger"></i>
                            <span class="d-none d-sm-inline">Tài khoản</span>
                        </button>
                        <ul class="dropdown-menu dropdown-menu-end shadow-sm rounded-0 border-secondary-subtle">
                            <c:if test="${sessionScope.TC_CURRENT_USER.isAdmin()}">
                                <li>
                                    <a class="dropdown-item py-2 fw-bold text-danger"
                                        href="${pageContext.request.contextPath}/admin/dashboard"><i
                                            class="fa-solid fa-shield-halved me-2"></i>Quản lý hệ thống</a>
                                </li>
                                <li>
                                    <hr class="dropdown-divider">
                                </li>
                            </c:if>
                            <c:if
                                test="${!sessionScope.TC_CURRENT_USER.isAdmin() && sessionScope.TC_CURRENT_USER.isManager()}">
                                <li>
                                    <a class="dropdown-item py-2 fw-semibold text-danger"
                                        href="${pageContext.request.contextPath}/organizer/dashboard"><i
                                            class="fa-solid fa-building me-2"></i>Quản lý tổ chức</a>
                                </li>
                            </c:if>
                            <c:if
                                test="${!sessionScope.TC_CURRENT_USER.isAdmin() && sessionScope.TC_CURRENT_USER.organizationRole == 'CHECK_IN_STAFF'}">
                                <li>
                                    <a class="dropdown-item py-2 fw-semibold text-success"
                                        href="${pageContext.request.contextPath}/organizer/checkin-select"><i
                                            class="fa-solid fa-qrcode me-2"></i>Soát vé sự kiện</a>
                                </li>
                                <li>
                                    <hr class="dropdown-divider">
                                </li>
                            </c:if>
                            <li>
                                <a class="dropdown-item py-2" href="${pageContext.request.contextPath}/profile"><i
                                        class="fa-solid fa-user me-2 text-muted"></i>Hồ sơ cá nhân</a>
                            </li>
                            <li>
                                <a class="dropdown-item py-2" href="${pageContext.request.contextPath}/me/orders"><i
                                        class="fa-solid fa-receipt me-2 text-muted"></i>Đơn hàng của tôi</a>
                            </li>
                            <li>
                                <a class="dropdown-item py-2" href="${pageContext.request.contextPath}/me/tickets"><i
                                        class="fa-solid fa-ticket me-2 text-muted"></i>Vé điện tử</a>
                            </li>
                            <c:if test="${sessionScope.TC_CURRENT_USER.isCustomerOnly()}">
                                <li>
                                    <a class="dropdown-item py-2"
                                        href="${pageContext.request.contextPath}/organizer-request"><i
                                            class="fa-solid fa-handshake me-2 text-muted"></i>Đăng ký mở ban tổ chức</a>
                                </li>
                            </c:if>
                            <li>
                                <hr class="dropdown-divider">
                            </li>
                            <li>
                                <button id="site-logout" type="button" class="dropdown-item py-2 text-danger"><i
                                        class="fa-solid fa-right-from-bracket me-2"></i>Đăng xuất</button>
                            </li>
                        </ul>
                    </div>
                </c:when>
                <c:otherwise>
                    <a href="${pageContext.request.contextPath}/auth/login" class="btn-outline-custom">Đăng nhập</a>
                    <a href="${pageContext.request.contextPath}/auth/register"
                        class="btn-primary-custom d-none d-sm-inline-flex">Đăng ký</a>
                </c:otherwise>
            </c:choose>
            <a href="${pageContext.request.contextPath}/me/tickets"
                class="btn-outline-custom p-0 d-inline-flex align-items-center justify-content-center tc-icon-button"
                title="Vé & Đơn hàng của tôi">
                <i class="fa-solid fa-ticket fs-6"></i>
            </a>
        </div>
    </div>
</nav>

<p id="site-notice" role="alert" class="container tc-notice" hidden></p>
