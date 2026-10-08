> **Convention:** `dev` → `feat/liem/liem-03-vnpay-initiation` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa VNPAY initiation trong `payment/**`, Payment DTO/cache, checkout JSP/test. Không sửa config keys, callback result hoặc Refund.

## Mục tiêu và endpoint

- `POST /api/orders/{orderId}/payments`

Khởi tạo Payment/VNPAY Sandbox đúng chữ ký và không tạo nhiều lần thu do double-click hoặc timeout.

## Cách làm kỹ thuật

- Header `Idempotency-Key` bắt buộc. Redis key `tc:idem:vnpay:init:<actorId>:<key>` lưu request hash, paymentId, txnRef và response redirect URL với TTL ít nhất bằng Hold.
- Cùng key/payload trả cùng paymentId/URL; cùng key payload khác trả 409. Thêm lock ngắn `tc:lock:order-payment:<orderId>`.
- Transaction DB kiểm owner, Hold/Event còn hợp lệ, không có Payment PENDING/UNKNOWN, gọi đường SP08 để tạo ý định Payment và txnRef duy nhất; commit trước khi tạo URL.
- Ký tham số theo thứ tự canonical bằng HMAC cấu hình VNPAY; không log secret hoặc full signed URL có dữ liệu nhạy cảm.
- Đơn 0 đồng không gọi VNPAY và không tạo Payment giả; hoàn tất qua đường DB dành cho zero-payment.
- Redis mất sau DB commit phải tra Payment theo orderId/txnRef trước khi thử tạo lại.

## Test

- Chữ ký deterministic với clock/fixture cố định, double-click, Redis timeout, DB timeout sau commit, Payment PENDING/UNKNOWN.
- Manual Swagger khởi tạo có/không Coupon, replay key, key khác payload, zero-order; kiểm DB/Redis.

