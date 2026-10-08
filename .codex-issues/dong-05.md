> **Convention:** `dev` → `feat/dong/dong-05-admin-event-review` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Đông chỉ sở hữu admin publish/reject Event và JSP UI-20. Không làm cancel/progress, phần đó thuộc Khánh.

## Mục tiêu

Admin xem Event chờ duyệt, publish với CommissionRule hợp lệ hoặc reject có lý do.

## Endpoint

- `GET /api/admin/events`
- `GET /api/admin/events/{eventId}`
- `POST /api/admin/events/{eventId}/publish`
- `POST /api/admin/events/{eventId}/reject`

## Cách làm kỹ thuật

- Chỉ `ADMIN`; kiểm role từ DB/current-user contract, không từ payload.
- Publish bắt buộc Event `PENDING_APPROVAL`, có ảnh, lịch hợp lệ, ít nhất một Zone và CommissionRule đang hiệu lực của cùng Organization.
- Gọi transaction/SP publish hiện có; không chép đường ghi song song bằng JPA.
- Mutation dùng `tc:idem:event-review:<adminId>:<key>`. Publish/reject lặp trả trạng thái hiện tại; key khác payload trả 409.
- Sau commit invalidate public Event list/detail, organizer list và admin pending count.
- UI-20 hiển thị nút cancel/progress bằng API contract của Khánh nhưng Đông không sửa code cancellation.

## Test và nghiệm thu

- Test non-admin 403, rule sai Organization, thiếu Zone/ảnh, publish/reject lặp và race publish với sửa layout.
- Manual Swagger đủ list/detail/publish/reject và kiểm DB, audit, Redis invalidation.
- Không hiển thị Event vừa reject trong public cache.

