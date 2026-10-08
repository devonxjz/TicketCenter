> **Convention:** `dev` → `feat/dong/dong-01-event-catalog-cache` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Chỉ sửa `event/controller|service|repository|dto|cache`, JSP public Event và test Event. Cấm sửa model, config chung, database, `openapi.json`, feature người khác.

## Mục tiêu

Xây UI-01 và phần đọc UI-02: danh sách/tìm kiếm Event, chi tiết Event, Zone và Seat. SQL Server là nguồn thật; Redis chỉ cache projection đã đọc từ DB.

## Endpoint

- `GET /api/events`
- `GET /api/events/{eventId}`
- `GET /api/events/{eventId}/zones`
- `GET /api/zones/{zoneId}/seats`
- `GET /api/event-categories`

## Cách làm kỹ thuật

- Query chỉ trả Event `PUBLISHED`; filter keyword/category/from/to, sort theo allowlist, phân trang ổn định kèm ID.
- Cache-aside với key versioned: `tc:event:list:v1:<hash-filter>`, `tc:event:detail:v1:<id>`, `tc:event:zones:v1:<id>`, `tc:zone:seats:v1:<id>`.
- Hash filter từ chuỗi canonical gồm actor scope, page, pageSize, sort và filter; không dùng raw query string làm key.
- TTL đề xuất 30–60 giây cho availability, 5 phút cho category. Cache JSON DTO, không cache entity.
- Cache miss đọc View/UDF hiện có trong DB bằng bind parameter. Redis lỗi thì fallback DB và không trả dữ liệu giả.
- Seat availability chỉ để hiển thị; giữ vé vẫn do transaction của Liêm quyết định.

## Test và nghiệm thu

- Unit test canonical cache key, filter/sort allowlist và mapping DTO.
- Integration test public chỉ thấy PUBLISHED, pagination ổn định, cache hit không query DB lần hai và cache miss trả đúng DB.
- Manual Swagger: tìm kiếm, trang rỗng, Event không công khai, ID sai, cache hit/miss; kiểm tra response và key Redis đã lọc.
- Không ghi raw SQL/filter người dùng vào log. `mvn -B test` và Actions phải xanh.

