> **Convention:** `dev` → `feat/khanh/khanh-04-admin-refund-decision` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Khánh sở hữu admin Refund UI-21. Không sửa admin Event/Organization/Coupon/Finance.

## Mục tiêu và endpoint

- `GET /api/admin/refund-requests`
- `GET /api/admin/refund-requests/{refundId}`
- `POST /api/admin/refund-requests/{refundId}/decision`
- `POST /api/admin/refund-requests/{refundId}/retry`
- `POST /api/admin/payments/{paymentId}/compensation/retry`

## Kỹ thuật

- Chỉ ADMIN từ DB. Approve/reject gọi `usp_DecideRefund`/SP10 canonical; reject bắt buộc reason.
- Backend tạo attemptId; browser không được gửi trạng thái SUCCEEDED/FAILED/UNKNOWN.
- Retry chỉ Refund `RETRYABLE` sau FAILED đã xác minh; UNKNOWN phải reconciliation, không retry mù.
- Redis idempotency theo admin/refund/operation. Same key same payload trả kết quả cũ; payload khác 409.
- Sau commit invalidate Refund, Ticket, Order, admin pending count và report cache liên quan.
- Reject chỉ khôi phục Ticket ACTIVE nếu Event chưa cancelled và DB xác nhận điều kiện.

## Test

- Non-admin, approve/reject lặp, thiếu reason, retry sai status, concurrent decisions và rollback.
- Manual Swagger mọi decision; kiểm canonical DB, audit và Redis result.

