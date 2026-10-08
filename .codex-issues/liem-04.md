> **Convention:** `dev` → `feat/liem/liem-04-vnpay-callback-reconcile` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa VNPAY return/IPN/status và PaymentReconciliationJob. Không sửa Refund worker của Khánh hoặc Event inventory.

## Mục tiêu và endpoint

- `GET /api/payments/vnpay/return`
- `GET /api/payments/vnpay/ipn`
- `GET /api/orders/{orderId}/payment-status`

Xác thực callback server-side, chống replay và ghi nhận kết quả thu tiền bằng DB transaction.

## Cách làm kỹ thuật

- Return URL chỉ hiển thị/tra trạng thái; không mutate Order/Payment.
- IPN xác minh signature constant-time, tmnCode, txnRef, amount, currency và response code trước khi ghi.
- Redis dedupe `tc:idem:vnpay:ipn:<txnRef>:<providerTxnNo>` lưu hash callback và protocol response. Callback lặp trả đúng response cũ.
- Dùng lock ngắn theo paymentId; SP09/đường DB là nơi duy nhất CAPTURE, consume Hold, chuyển inventory, consume Coupon và phát hành Ticket.
- Callback đến muộn, thu trùng, Event hủy hoặc hết sale window tạo đúng một Refund compensation trong DB; không gọi gateway refund trong transaction callback.
- `PaymentReconciliationJob` query PENDING/UNKNOWN ngoài transaction, sau đó áp verified result qua cùng SP09. Cache trạng thái chỉ refresh sau commit.

## Test

- Signature sai, amount sai, callback lặp, callback đồng thời, payment late, UNKNOWN rồi CAPTURED.
- Manual Swagger dùng bộ query đã ký của môi trường test; kiểm Payment/Order/Ticket/Refund DB và Redis dedupe.
- Không đưa VNPAY secret hoặc callback thật vào evidence.

