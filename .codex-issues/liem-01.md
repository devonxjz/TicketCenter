> **Convention:** `dev` → `feat/liem/liem-01-ticket-hold` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa `ticketing/controller|service|repository|dto|cache`, Hold workers/JSP/test. Cấm sửa Event inventory, Refund, model/config/database/OpenAPI.

## Mục tiêu và endpoint

- `POST /api/holds`
- `GET /api/me/hold`
- `POST /api/holds/{holdId}/cancel`

Tạo/release Hold nguyên tử cho UI-02/UI-04: buyer đã xác minh, một ACTIVE Hold toàn hệ thống, 1–8 vé cùng Event, TTL 10 phút.

## Cách làm kỹ thuật

- `Idempotency-Key` bắt buộc. Redis key `tc:idem:hold:<actorId>:<key>` dùng `SET NX` với request hash canonical của event/selections.
- Redis chỉ chống gửi lặp và cache `tc:user:active-hold:<userId>`; DB/SP giữ Seat/standing inventory và quyết định conflict.
- Không dựa vào availability cache của Đông để giữ vé. Transaction DB khóa Event, Zone, Seat theo thứ tự ID; thiếu một chỗ rollback toàn bộ.
- Giá lấy từ DB lúc giữ và snapshot vào HoldItem. Client không được gửi giá/thời hạn.
- Sau commit invalidate active-hold, Event/Zone/Seat availability. HoldExpiryJob gọi đường release DB idempotent rồi invalidate cache.
- Redis lỗi ở mutation này trả 503 nếu không thể phục hồi kết quả bằng khóa DB/hold hiện hành.

## Test

- Concurrency hai buyer cùng Seat chỉ một thành công; standing không vượt capacity.
- Một buyer hai tab; key lặp cùng payload; key lặp payload khác; hết hạn đúng mốc.
- Manual Swagger tạo/xem/hủy Hold, 401, chưa verify, 409 hết chỗ; kiểm DB và Redis.

