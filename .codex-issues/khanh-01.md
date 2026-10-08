> **Convention:** `dev` → `feat/khanh/khanh-01-checkin-events` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa `fulfillment/checkin/**`, JSP UI-15, DTO/cache/test. Không sửa Event của Đông, Ticket/QR encoder của Liêm hoặc model/config/database/OpenAPI.

## Mục tiêu và endpoint

- `GET /api/check-ins`
- `GET /api/organizations/{organizationId}/check-in-events`
- `GET /api/events/{eventId}/check-in-window`

Cho Manager/CHECK_IN_STAFF chọn đúng Organization và Event trước khi quét.

## Kỹ thuật

- Membership và Event status luôn xác minh từ DB; organizationId chỉ là filter.
- GET dùng cache-aside: `tc:checkin:events:v1:<actorId>:<orgId>`, `tc:checkin:window:v1:<eventId>`; TTL ngắn và không chứa revenue.
- Cache miss đọc DB/F07; cache chỉ chứa Event DTO. Redis lỗi fallback DB.
- Khoảng hợp lệ `[startTime - 60 phút, endTime)`; CANCELLED luôn chặn.
- Khi membership/Event thay đổi, owner feature tương ứng gửi invalidation contract; không sửa repository của Đông/Thái.

## Test

- Manager/staff đúng org; membership inactive; org khác; Event cancelled; boundary thời gian.
- Manual Swagger list/window, 401/403, cache hit/miss và đối chiếu DB.

