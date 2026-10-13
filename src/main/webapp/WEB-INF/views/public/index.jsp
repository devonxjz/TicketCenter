<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html><html lang="vi"><head><title>Khám phá sự kiện | TicketsCenter</title><jsp:include page="/WEB-INF/views/layout/meta-head.jsp"/><link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/public-events.css"></head>
<body data-public-page="catalog" data-context="${pageContext.request.contextPath}"><jsp:include page="/WEB-INF/views/layout/header.jsp"/>
<main id="main-content" class="container pb-5">
 <div class="tc-catalog-intro"><p class="tc-eyebrow">Âm nhạc · Sân khấu · Văn hóa · Thể thao</p><h1>Khám phá sự kiện</h1><p>Tìm chương trình bạn quan tâm, xem giá và tình trạng vé, chọn vị trí phù hợp trước khi đặt.</p></div>
 <section id="catalog-featured" class="tc-featured" aria-label="Sự kiện đang mở bán" hidden><div class="tc-featured-cover"><span class="tc-image-empty">Chưa có ảnh</span><img id="featured-image" hidden alt=""></div><div><p class="tc-eyebrow">Đang mở bán</p><h2 id="featured-title"></h2><p id="featured-time" class="tc-event-meta"></p><p id="featured-venue" class="tc-event-meta"></p><p id="featured-price" class="event-price"></p><a id="featured-link" class="btn-primary-custom">Xem sự kiện &amp; Chọn vé</a></div></section>
 <form id="filters" class="tc-catalog-filters" aria-label="Tìm kiếm sự kiện"><div class="row g-3">
  <div class="col-md-5"><label for="keyword" class="filter-label">Từ khóa</label><input id="keyword" name="keyword" type="search" class="form-control" maxlength="100" placeholder="Tên sự kiện hoặc địa điểm"></div>
  <div class="col-md-4"><label for="category" class="filter-label">Danh mục</label><select id="category" name="categoryId" class="form-select"><option value="">Tất cả danh mục</option></select><small id="category-help" class="text-muted"></small></div>
  <div class="col-md-3"><label for="sort" class="filter-label">Sắp xếp</label><select id="sort" name="sort" class="form-select"><option value="startTime_asc">Ngày diễn gần nhất</option><option value="startTime_desc">Ngày diễn muộn nhất</option><option value="price_asc">Giá tăng dần</option><option value="price_desc">Giá giảm dần</option></select></div>
  <div class="col-md-4"><label for="from" class="filter-label">Từ ngày</label><input id="from" name="from" type="datetime-local" class="form-control"></div>
  <div class="col-md-4"><label for="to" class="filter-label">Trước ngày</label><input id="to" name="to" type="datetime-local" class="form-control"></div>
  <div class="col-md-4 d-flex align-items-end"><button type="submit" class="btn-primary-custom w-100 justify-content-center">Tìm kiếm</button></div>
 </div></form>
 <p id="status" role="status" aria-live="polite">Đang tải sự kiện…</p><button id="retry-events" type="button" class="btn-outline-custom mb-4" hidden>Thử tải lại</button>
 <div id="events" class="row g-4"></div><nav class="d-flex justify-content-center align-items-center gap-3 mt-5" aria-label="Phân trang"><button id="previous" type="button" class="btn-outline-custom" disabled>Trang trước</button><span id="page-label" class="small"></span><button id="next" type="button" class="btn-outline-custom" disabled>Trang sau</button></nav>
 <noscript>Vui lòng bật JavaScript để tải dữ liệu sự kiện và đặt vé.</noscript>
</main><jsp:include page="/WEB-INF/views/layout/footer.jsp"/><script defer src="${pageContext.request.contextPath}/assets/js/public-events.js"></script></body></html>
