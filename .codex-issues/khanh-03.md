> **Convention:** `dev` → `feat/khanh/khanh-03-refund-request` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa `fulfillment/refund/**`, JSP UI-08 và test. Không sửa Order/Ticket model hoặc legacy/canonical migration.

## Mục tiêu và endpoint

- `GET /api/orders/{orderId}/refundable-tickets`
- `POST /api/refund-requests`
- `GET /api/me/refund-requests`
- `GET /api/refund-requests/{refundId}`

Tạo nghĩa vụ Refund cho các Ticket cùng Order và chuyển Ticket sang `REFUND_PENDING`.

## Kỹ thuật

- Chỉ owner Order; Ticket cùng Order, chưa USED, trước startTime và chưa có Refund mở.
- Amount lấy tổng `Ticket.paidAmount` từ DB, không nhận từ client.
- Gọi `usp_RequestTicketRefund`/đường canonical hiện có. Đọc `vw_RefundOverview` và bảng canonical; không `persist(Refund)` trực tiếp vào legacy `tc_refunds`.
- Redis idempotency `tc:idem:refund-request:<actorId>:<key>` và lock ngắn theo sorted ticket IDs.
- Cache `tc:refund:detail:<id>`, `tc:user:refunds:<userId>:*`, `tc:order:refundable:<orderId>`; invalidate sau commit.
- Refund 0 đồng vẫn tồn tại trong DB; không tạo Payment/transfer giả.

## Test

- Vé khác Order, owner sai, USED, request mở, startTime boundary, concurrent request cùng Ticket và replay key.
- Manual Swagger tạo/list/detail, kiểm Ticket status, canonical Refund và Redis.

