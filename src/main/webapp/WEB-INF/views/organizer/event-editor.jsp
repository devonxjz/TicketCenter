<%@ page contentType="text/html;charset=UTF-8" language="java" %>
    <!DOCTYPE html>
    <html lang="vi">

    <head>
        <title>Hồ sơ và tồn kho sự kiện | TicketsCenter</title>
        <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/organizer-ui.css?v=20261014">
        <style>
            [hidden] {
                display: none !important
            }
        </style>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/event-images.css">
    </head>

    <body class="bg-light tc-organizer-page" data-context="${pageContext.request.contextPath}" data-dong-page="editor">
        <jsp:include page="/WEB-INF/views/layout/header.jsp" />
        <div class="dashboard-layout">
            <jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" />
            <main class="dashboard-content">
                <div class="d-flex flex-wrap gap-3 justify-content-between">
                    <div>
                        <h1 class="h3 fw-bold mb-1 text-dark">Biên tập chương trình &amp; hạng vé</h1>
                        <p class="small text-muted mb-4">Thiết lập nội dung, lịch mở bán và khu vực vé trước khi gửi
                            duyệt.</p>
                    </div><span id="draft-state" class="status-pill status-subtle align-self-start">Nháp mới</span>
                </div>
                <p id="status" role="status" aria-live="polite">Đang tải…</p>
                <p id="rejection" class="text-danger" style="white-space:pre-wrap"></p>
                <div class="row g-4 organizer-editor-grid">
                    <div class="col-lg-7">
                        <form id="event-form" method="post" class="panel-box">
                            <div class="panel-header">
                                <h2 class="panel-title">1. Thông tin chương trình</h2>
                            </div>
                            <div class="row g-3">
                                <div class="col-md-6"><label for="organization" class="filter-label">Tổ
                                        chức</label><select id="organization" class="form-select rounded-0"
                                        required></select></div>
                                <div class="col-md-6"><label for="categoryId" class="filter-label">Danh
                                        mục</label><select id="categoryId" class="form-select rounded-0"
                                        required></select></div>
                                <div class="col-12"><label for="title" class="filter-label">Tên sự kiện</label><input
                                        id="title" class="form-control rounded-0" required minlength="5"
                                        maxlength="200"></div>
                                <div class="col-12"><label for="description" class="filter-label">Mô tả</label><textarea
                                        id="description" class="form-control rounded-0" required maxlength="5000"
                                        rows="5"></textarea></div>
                                <div class="col-md-6"><label for="venueName" class="filter-label">Tên địa
                                        điểm</label><input id="venueName" class="form-control rounded-0" required
                                        maxlength="250"></div>
                                <div class="col-md-6"><label for="venueAddress" class="filter-label">Địa
                                        chỉ</label><input id="venueAddress" class="form-control rounded-0" required
                                        maxlength="500"></div>
                                <div class="col-md-6"><label for="saleStartTime" class="filter-label">Mở
                                        bán</label><input id="saleStartTime" type="datetime-local" step="0.001"
                                        class="form-control rounded-0" required></div>
                                <div class="col-md-6"><label for="saleEndTime" class="filter-label">Kết thúc
                                        bán</label><input id="saleEndTime" type="datetime-local" step="0.001"
                                        class="form-control rounded-0" required></div>
                                <div class="col-md-6"><label for="startTime" class="filter-label">Bắt đầu sự
                                        kiện</label><input id="startTime" type="datetime-local" step="0.001"
                                        class="form-control rounded-0" required></div>
                                <div class="col-md-6"><label for="endTime" class="filter-label">Kết thúc sự
                                        kiện</label><input id="endTime" type="datetime-local" step="0.001"
                                        class="form-control rounded-0" required></div>
                                <div class="col-12 text-muted small">Thời gian nhập theo giờ địa phương. Lịch phải thỏa:
                                    mở bán &lt; kết thúc bán ≤ bắt đầu &lt; kết thúc sự kiện.</div>
                                <div class="col-12 d-flex flex-wrap gap-2"><button id="save-event"
                                        class="btn-outline-custom" disabled>Lưu bản nháp</button><button type="button"
                                        id="submit-event" class="btn-primary-custom" hidden>Gửi duyệt sự
                                        kiện</button><button type="button" id="delete-event"
                                        class="btn btn-outline-danger" hidden>Xóa nháp</button></div>
                            </div>
                        </form>
                        <section id="media" hidden class="panel-box">
                            <div class="panel-header">
                                <h2 class="panel-title">Ảnh bìa chương trình</h2>
                            </div>
                            <p class="small text-muted">PNG/JPEG/WEBP tối đa 5 MB. Sự kiện mới dùng ảnh mặc định theo
                                thể loại nếu chưa chọn ảnh riêng.</p><img id="cover-preview" alt="Ảnh bìa sự kiện"
                                class="mb-3" style="max-width:100%;max-height:240px" hidden>
                            <div class="d-flex flex-wrap gap-3"><input type="file" id="cover-file"
                                    accept="image/png,image/jpeg,image/webp" class="form-control rounded-0"
                                    style="max-width:450px"><button type="button" id="upload-cover"
                                    class="btn btn-dark">Tải ảnh lên</button><progress id="upload-progress" value="0"
                                    max="100" aria-label="Tiến trình tải ảnh"></progress></div>
                        </section>
                    </div>
                    <div class="col-lg-5">
                        <section class="panel-box">
                            <div class="panel-header">
                                <h2 class="panel-title">2. Hạng vé &amp; khu vực chỗ ngồi</h2>
                            </div>
                            <p class="small text-muted organizer-unsaved-note">Lưu hồ sơ sự kiện để thiết lập khu vực vé
                                và sơ đồ ghế.</p>
                            <div id="inventory" hidden>
                                <p class="small text-muted">Sau công khai chỉ sửa giá. Chỗ đã giữ/bán hoặc có giao dịch
                                    được bảo vệ khi sửa/xóa.</p>
                                <form id="zone-form" method="post" class="organizer-zone-form border p-3 mb-4">
                                    <div class="row g-3">
                                        <div class="col-md-4"><label for="zone-name" class="filter-label">Tên
                                                khu</label><input id="zone-name" class="form-control rounded-0"
                                                maxlength="120" required></div>
                                        <div class="col-md-4"><label for="zoneType" class="filter-label">Loại
                                                khu</label><select id="zoneType" class="form-select rounded-0">
                                                <option value="SEATED">Có ghế ngồi</option>
                                                <option value="STANDING">Đứng tự do</option>
                                            </select></div>
                                        <div class="col-md-4"><label for="zone-price" class="filter-label">Giá VND (số
                                                nguyên)</label><input id="zone-price" class="form-control rounded-0"
                                                inputmode="numeric" pattern="[0-9]+" value="0" required></div>
                                        <div id="seat-fields" class="col-md-8 d-flex gap-3">
                                            <div><label for="rows" class="filter-label">Số hàng</label><input id="rows"
                                                    type="number" class="form-control rounded-0" min="1" max="1000"
                                                    value="4"></div>
                                            <div><label for="seatsPerRow" class="filter-label">Ghế mỗi
                                                    hàng</label><input id="seatsPerRow" type="number"
                                                    class="form-control rounded-0" min="1" max="1000" value="10"></div>
                                        </div>
                                        <div id="standing-fields" class="col-md-8" hidden><label for="capacity"
                                                class="filter-label">Sức chứa</label><input id="capacity" type="number"
                                                class="form-control rounded-0" min="1" max="10000000" value="100"></div>
                                        <div class="col-md-4 d-flex align-items-end"><button id="add-zone"
                                                class="btn btn-dark w-100">Thêm khu</button></div>
                                    </div>
                                </form>
                                <div id="zones"></div>
                            </div>
                        </section>
                    </div>
                </div>
            </main>
        </div>
        <jsp:include page="/WEB-INF/views/layout/footer.jsp" />
        <script defer src="${pageContext.request.contextPath}/assets/js/dong-events.js"></script>
    </body>

    </html>