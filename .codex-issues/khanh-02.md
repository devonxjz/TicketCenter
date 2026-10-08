> **Convention:** `dev` → `feat/khanh/khanh-02-qr-checkin` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa decoder/check-in service/repository/cache, JSP UI-16 và test. Không sửa QR encoder/Ticket issuance của Liêm.

## Mục tiêu và endpoint

- `POST /api/check-ins`
- `GET /api/events/{eventId}/check-ins`

Giải mã QR, lấy Ticket ID và check-in nguyên tử dựa hoàn toàn vào DB.

## Giải mã QR bắt buộc

1. Đọc envelope `base64url(payload).base64url(signature)`.
2. Xác minh HMAC-SHA256 constant-time trước khi parse JSON.
3. Parse payload đúng schema `{id, name}`; reject field/type/size sai.
4. Lấy `id` UUID để query Ticket trong DB. `name` chỉ hiển thị, không tham gia quyết định.
5. Kiểm membership, Event, window, Ticket status và Ticket thuộc Event.

## Redis và transaction

- Request có `Idempotency-Key`; key `tc:idem:checkin:<actorId>:<key>` lưu request hash/result.
- Lock ngắn `tc:lock:ticket-checkin:<ticketId>` giảm request đồng thời, nhưng DB/SP04 vẫn là bảo vệ cuối.
- Transaction DB chuyển ACTIVE→USED và ghi TicketCheckInLog; mọi kết quả từ chối cần log theo contract nhưng không lưu raw QR.
- Sau commit invalidate Ticket detail/QR status, check-in history/report caches. Redis lỗi ở mutation check-in trả 503 nếu không chứng minh được idempotency từ DB.

## Test

- QR hợp lệ, signature sai, payload bị sửa, ID lạ, name bị sửa, wrong Event, outside window, refund pending và hai scan đồng thời.
- Manual Swagger dùng QR test do LIEM-05 sinh; kiểm đúng một success trong DB và Redis replay.

