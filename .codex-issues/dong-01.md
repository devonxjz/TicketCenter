> **Convention:** `dev` → `feat/dong/dong-01-event-catalog-cache` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Chỉ sửa `event/controller/**`, `event/service/**`, `event/repository/**`, `event/dto/**`, `event/cache/**`, JSP public Event và test Event tương ứng. Cấm sửa model, config chung, database, `openapi.json`, feature domain khác.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng cổng tra cứu và chi tiết sự kiện công khai cho khách hàng:
- Phục vụ màn hình **UI-01 (Trang chủ & Danh mục sự kiện)** và phần đọc của **UI-02 (Trang chi tiết sự kiện & Sơ đồ Zone/Seat)**.
- Người dùng công khai (Public / Unauthenticated) và khách hàng có thể duyệt danh sách sự kiện, tìm kiếm theo từ khóa, lọc theo thể loại (`category`), khoảng thời gian tổ chức (`from`, `to`), và phân trang ổn định.
- Xem chi tiết sự kiện kèm danh sách các Khu vực vé (`Zone`) và trạng thái ghế ngồi (`Seat` - Trống / Đang giữ / Đã bán).
- Đảm bảo hiệu năng tải trang cao và giảm tải tối đa cho SQL Server thông qua cơ chế đệm bộ nhớ Cache-Aside với Redis.
- Bất biến nghiệp vụ: Công chúng **chỉ được phép nhìn thấy các sự kiện có trạng thái `PUBLISHED`**. Các sự kiện `DRAFT`, `PENDING_APPROVAL`, `REJECTED`, `CANCELLED` tuyệt đối không xuất hiện trên catalog công khai.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/events`
- **Mục đích:** Tìm kiếm và phân trang danh mục sự kiện công khai.
- **Quyền hạn:** Public.
- **Query Parameters:**
  - `keyword`: Tìm theo tên sự kiện, nghệ sĩ, địa điểm (tùy chọn, tối đa 100 ký tự).
  - `categoryId`: Lọc theo ID danh mục sự kiện (tùy chọn).
  - `from`: Ngày bắt đầu sự kiện (ISO-8601, ví dụ `2026-11-01T00:00:00Z`).
  - `to`: Ngày kết thúc sự kiện (ISO-8601).
  - `page`: Số trang (mặc định 1, số nguyên >= 1).
  - `pageSize`: Kích thước trang (mặc định 12, tối đa 50).
  - `sort`: Tiêu chí sắp xếp từ allowlist: `startTime_asc` (mặc định), `startTime_desc`, `price_asc`, `price_desc`.
- **Response Body (200 OK):**
```json
{
  "items": [
    {
      "eventId": 201,
      "title": "Đêm Nhạc Mùa Thu 2026",
      "category": "Âm nhạc",
      "venue": "Nhà Hát Hòa Bình, TP.HCM",
      "coverImageUrl": "https://res.cloudinary.com/ticketscenter/image/upload/v1/covers/event-201.jpg",
      "startTime": "2026-11-15T19:00:00Z",
      "endTime": "2026-11-15T22:30:00Z",
      "minPrice": "250000.00",
      "maxPrice": "1500000.00",
      "status": "PUBLISHED"
    }
  ],
  "total": 45,
  "page": 1,
  "pageSize": 12,
  "totalPages": 4
}
```

### 2.2. GET `/api/events/{eventId}`
- **Mục đích:** Xem chi tiết một sự kiện công khai.
- **Response Body (200 OK):**
```json
{
  "eventId": 201,
  "title": "Đêm Nhạc Mùa Thu 2026",
  "description": "Chương trình biểu diễn nghệ thuật đặc sắc quy tụ các nghệ sĩ hàng đầu.",
  "category": "Âm nhạc",
  "venue": "Nhà Hát Hòa Bình, TP.HCM",
  "organizationName": "Công ty Âm nhạc Trẻ Việt Nam",
  "coverImageUrl": "https://res.cloudinary.com/ticketscenter/image/upload/v1/covers/event-201.jpg",
  "startTime": "2026-11-15T19:00:00Z",
  "endTime": "2026-11-15T22:30:00Z",
  "saleStartTime": "2026-10-15T09:00:00Z",
  "saleEndTime": "2026-11-14T23:59:59Z",
  "isSaleActive": true,
  "status": "PUBLISHED"
}
```
- **Lỗi:** `404 Not Found` nếu sự kiện không tồn tại hoặc không ở trạng thái `PUBLISHED`.

### 2.3. GET `/api/events/{eventId}/zones`
- **Mục đích:** Lấy danh sách các khu vực vé (Zone) của sự kiện kèm sức chứa và số lượng còn lại.
- **Response Body (200 OK):**
```json
[
  {
    "zoneId": 10,
    "eventId": 201,
    "name": "Khu VIP Khán Đài A",
    "zoneType": "SEATED",
    "basePrice": "1500000.00",
    "totalCapacity": 100,
    "availableSeats": 42
  },
  {
    "zoneId": 11,
    "eventId": 201,
    "name": "Khu Standing Sân Khấu",
    "zoneType": "STANDING",
    "basePrice": "500000.00",
    "totalCapacity": 500,
    "availableSeats": 230
  }
]
```

### 2.4. GET `/api/zones/{zoneId}/seats`
- **Mục đích:** Lấy sơ đồ ghế ngồi chi tiết của một Zone loại có ghế (`SEATED`).
- **Response Body (200 OK):**
```json
[
  {
    "seatId": 501,
    "zoneId": 10,
    "rowName": "A",
    "seatNumber": 1,
    "seatLabel": "A-01",
    "status": "AVAILABLE"
  },
  {
    "seatId": 502,
    "zoneId": 10,
    "rowName": "A",
    "seatNumber": 2,
    "seatLabel": "A-02",
    "status": "HELD"
  },
  {
    "seatId": 503,
    "zoneId": 10,
    "rowName": "A",
    "seatNumber": 3,
    "seatLabel": "A-03",
    "status": "SOLD"
  }
]
```

### 2.5. GET `/api/event-categories`
- **Mục đích:** Danh sách thể loại sự kiện (Âm nhạc, Hội thảo, Thể thao...). Trả về mảng JSON.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Truy vấn Dữ liệu Nguồn thật từ SQL Server
- Sử dụng các View và Function canonical đã được tối ưu hóa:
  - View `dbo.v_events`: Chứa dữ liệu sự kiện đã chuẩn hóa. Luôn lọc điều kiện bắt buộc `status = 'PUBLISHED'`.
  - Function `dbo.fn_event_is_sale_active(event_id, SYSDATETIME())`: Tính toán xem sự kiện hiện có đang mở bán vé hay không.
  - View `dbo.v_zones` và Function `dbo.fn_zone_get_capacity(zone_id)`, `dbo.fn_zone_get_available(zone_id)`: Tính toán số lượng vé còn lại của từng Zone.
  - View `dbo.v_seats`: Đọc sơ đồ ghế của Zone.
- Phân trang ổn định (Deterministic Pagination):
  - Luôn sử dụng mệnh đề `ORDER BY <sort_column>, id ASC` kèm `OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY` để tránh trùng lặp hoặc bỏ sót dòng khi có nhiều dòng có cùng giá trị sắp xếp.
- Bảo vệ SQL Injection: Sử dụng 100% Bind Parameters qua JPA Criteria / PreparedStatements; kiểm tra whitelist đối với trường `sort`.

### 3.2. Chiến lược Đệm Bộ nhớ (Cache-Aside Strategy với Redis)
- **Quy tắc vàng:** SQL Server là nguồn chân lý (Source of Truth). Redis chỉ lưu trữ các bản chiếu DTO (Projections) phục vụ đọc nhanh. Tuyệt đối không cache JPA Managed Entities hoặc đối tượng có quan hệ Lazy-loading.
- **Cấu trúc Redis Key chuẩn hóa (Canonical Key Naming):**
  - Danh mục sự kiện: `tc:event:list:v1:<hash-filter>` (với `<hash-filter>` là chuỗi MD5 hoặc SHA-256 từ chuỗi canonical chuẩn hóa: `cat={categoryId}&kw={keyword}&p={page}&ps={pageSize}&s={sort}&from={from}&to={to}`).
  - Chi tiết sự kiện: `tc:event:detail:v1:<eventId>`
  - Danh sách Zone sự kiện: `tc:event:zones:v1:<eventId>`
  - Danh sách Ghế của Zone: `tc:zone:seats:v1:<zoneId>`
  - Danh mục categories: `tc:event:categories:v1`
- **Thời gian sống của Cache (Time-To-Live - TTL):**
  - Category list: 300 giây (5 phút).
  - Event detail: 120 giây (2 phút).
  - Zone list & Seat availability: 30–60 giây (đảm bảo tính tươi mới của trạng thái ghế).
- **Quy trình Cache-Aside:**
  ```text
  Client Request -> Đọc Redis Key
    -> CACHE HIT: Parse JSON DTO -> Trả kết quả ngay (Response time < 10ms).
    -> CACHE MISS: Truy vấn SQL Server qua View/Function
         -> Nếu không có trong DB: Trả 404 (hoặc cache rỗng TTL ngắn 10s để chống Cache Penetration).
         -> Nếu có dữ liệu: Lưu JSON DTO vào Redis với TTL quy định -> Trả kết quả.
  ```
- **Xử lý sự cố khi Redis Down (Fault-tolerance / Resilience):**
  - Bao bọc toàn bộ các thao tác Redis trong khối `try ... catch (JedisException | RedisConnectionException ex)`.
  - Khi Redis gặp sự cố, tự động ghi log `WARN`, **bỏ qua cache và fallback trực tiếp truy vấn SQL Server**. Tuyệt đối không để lỗi kết nối Redis làm sập trang web hoặc trả về dữ liệu rỗng/giả.

### 3.3. Ranh giới Trạng thái Ghế (Seat Availability Boundary)
- Lưu ý: Trạng thái ghế hiển thị từ `GET /api/zones/{zoneId}/seats` chỉ mang tính thông tin hiển thị giao diện cho người mua.
- Việc thực sự khóa ghế, giữ chỗ (Ticket Hold) được quyết định bởi transaction có khóa độc quyền của Liêm (ở issue LIEM-01).

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `EventCatalogItemDto`, `EventDetailDto`, `EventCategoryDto`, `ZoneSummaryDto`, `SeatStatusDto`, `EventFilterRequestDto`, `PaginatedResultDto<T>`.
   - Repositories: `EventReadRepository`, `ZoneReadRepository`, `SeatReadRepository` (sử dụng JPA EntityManager / native query đối với các View `dbo.v_events`, `dbo.v_zones`, `dbo.v_seats`).
   - Caching Service: `EventRedisCacheService` (xử lý sinh key chuẩn hóa, serialize/deserialize Jackson JSON, get/set có TTL, fallback an toàn).
   - Core Services: `EventCatalogService` (kết nối giữa Redis Cache và DB Repositories).
   - Servlets: `EventCatalogServlet` (`/api/events`), `EventDetailServlet` (`/api/events/{id}`), `EventZonesServlet` (`/api/events/{id}/zones`), `ZoneSeatsServlet` (`/api/zones/{id}/seats`), `EventCategoriesServlet` (`/api/event-categories`).
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/event/catalog.jsp`: UI-01 (Banner sự kiện nổi bật, bộ lọc thể loại, ô tìm kiếm từ khóa, lưới card sự kiện, thanh phân trang phân trang AJAX/HTML).
   - `src/main/webapp/WEB-INF/views/event/detail.jsp`: UI-02 (Ảnh cover, mô tả chi tiết, thời gian biểu diễn, danh sách các Zone và bảng giá, sơ đồ ghế trực quan).
3. **Redis State:**
   - Các key dạng `tc:event:detail:v1:<id>` và `tc:event:list:v1:<hash>` xuất hiện trong Redis với dữ liệu JSON hợp lệ và TTL đếm lùi.
4. **Kiểm thử tự động:**
   - `EventRedisCacheServiceTest`: Kiểm tra tạo canonical key, serialize JSON, xử lý khi Redis ngắt kết nối.
   - `EventCatalogServiceTest`: Đảm bảo chỉ trả sự kiện PUBLISHED, kiểm tra cache hit không gọi Repository lần hai.
   - `EventCatalogWebFlowTest`: Kiểm tra phân trang và lọc dữ liệu.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Khai báo các DTOs**
  - Tạo `EventFilterRequestDto` với các trường lọc, kèm hàm tạo chuỗi chuẩn hóa `toCanonicalString()`.
  - Tạo các DTO trả về: `EventCatalogItemDto`, `ZoneSummaryDto`, `SeatStatusDto`.
- **Bước 2: Triển khai `EventReadRepository`**
  - Viết câu truy vấn JPQL hoặc Native SQL trên `dbo.v_events`:
    ```sql
    SELECT e FROM Event e WHERE e.status = 'PUBLISHED' 
      AND (:categoryId IS NULL OR e.category.id = :categoryId)
      AND (:keyword IS NULL OR LOWER(e.title) LIKE :keyword)
    ORDER BY e.startTime ASC
    ```
- **Bước 3: Xây dựng `EventRedisCacheService`**
  - Kết nối Jedis / Redis Pool từ cấu hình hệ thống.
  - Viết các hàm: `getEventDetail(Long id)`, `putEventDetail(Long id, EventDetailDto dto, int ttl)`.
  - Triển khai thuật toán băm SHA-256 từ `filterDto.toCanonicalString()` để làm hash-key cho list catalog.
- **Bước 4: Xây dựng `EventCatalogService`**
  - Ghép luồng cache-aside: Kiểm tra cache -> Nếu rỗng gọi repository -> Lưu cache -> Trả về DTO.
- **Bước 5: Xây dựng Servlets**
  - `EventCatalogServlet`: Đọc params, gọi service, trả về JSON hoặc chuyển hướng forward sang `catalog.jsp`.
  - `EventDetailServlet`: Parse `eventId` từ URL path, gọi service lấy chi tiết, trả về JSON hoặc `detail.jsp`.
- **Bước 6: Xây dựng Giao diện JSP (UI-01 & UI-02)**
  - Tích hợp lưới responsive Bootstrap/CSS grid hiển thị danh sách sự kiện.
  - Vẽ sơ đồ ghế trực quan bằng SVG hoặc các ô grid với mã màu: Xanh lá (Trống), Vàng (Đang giữ), Xám (Đã bán).
- **Bước 7: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm toàn bộ unit test và integration test đều pass.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| CAT-01 | Tra cứu sự kiện PUBLISHED | Không có filter, page=1 | 200 OK, trả về danh sách các sự kiện PUBLISHED |
| CAT-02 | Sự kiện DRAFT/CANCELLED | Có sự kiện trạng thái DRAFT trong DB | Sự kiện này KHÔNG xuất hiện trong kết quả trả về |
| CAT-03 | Lọc theo category | categoryId=2 | Chỉ các sự kiện thuộc danh mục 2 được trả về |
| CAT-04 | Cache Hit | Gọi chi tiết sự kiện lần 2 | Dữ liệu trả về tức thì từ Redis, DB không bị truy vấn lần 2 |
| CAT-05 | Redis sự cố (Resilience) | Giả lập Redis bị tắt / timeout | Hệ thống tự động fallback đọc trực tiếp từ SQL Server, trả kết quả bình thường |
| CAT-06 | Xem chi tiết sự kiện không tồn tại | eventId=99999 | 404 Not Found |
