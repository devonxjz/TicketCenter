> **Convention:** `dev` → `feat/liem/liem-05-ticket-qr-issuance` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa ticket issuance/query/QR trong `fulfillment/ticket/**`, Order/Ticket JSP, outbox producer/test. Không sửa check-in decoder của Khánh.

## Mục tiêu và endpoint

- `GET /api/me/tickets`
- `GET /api/tickets/{ticketId}`
- `GET /api/tickets/{ticketId}/qr`

Phát hành một Ticket cho mỗi quyền vào cửa và sinh QR từ payload logic JSON `{id, name}`.

## Định dạng QR bắt buộc

- JSON canonical UTF-8: `{"id":"<ticket-uuid>","name":"<display-name>"}` với thứ tự field cố định.
- `id` là UUID Ticket trong DB; `name` chỉ hiển thị, không dùng cấp quyền.
- Ký HMAC-SHA256 trên bytes JSON bằng application secret, sau đó encode envelope `base64url(payload).base64url(signature)` vào QR.
- QR sinh bằng ZXing đã có. Không log raw payload/envelope/signature.

## Cache và dữ liệu

- DB giữ Ticket, paidAmount, status và issuedAt. Redis cache DTO/PNG bytes bằng `tc:ticket:detail:v1:<id>` và `tc:ticket:qr:v1:<id>:<version>` với TTL hữu hạn.
- Chỉ owner đọc Ticket/QR. Cache key phải có ticket/version; mọi thay đổi status từ check-in/refund phải invalidate.
- Phát hành idempotent theo OrderItem/sequence; IPN lặp không tạo thêm Ticket/code/outbox.
- Email/outbox ghi cùng transaction phát hành; gửi mail sau commit.

## Test

- Quantity N phát hành N Ticket, tổng paidAmount bằng Order total, QR decode lại đúng JSON và HMAC.
- Tamper payload/name/id hoặc signature phải bị decoder test từ chối.
- Manual Swagger xem Ticket/QR, owner sai, cache hit/miss; đối chiếu DB và Redis.

