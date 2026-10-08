> **Convention:** `dev` → `feat/vuong/vuong-04-settlement-confirm-payout` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Vương sở hữu confirm/payout UI-22 và adapter payout mô phỏng. Không sửa Refund gateway hoặc Payment.

## Mục tiêu và endpoint

- `POST /api/admin/settlements/{settlementId}/confirm`
- `POST /api/admin/settlements/{settlementId}/payouts`
- `GET /api/admin/settlements/{settlementId}/payouts`

Đóng băng Settlement và ghi chi trả idempotent, không vượt netPayable.

## Kỹ thuật

- Confirm gọi SP15: kiểm Event kết thúc, không còn Payment/Refund blocker, recalc mới nhất và freeze snapshot trong một transaction.
- Payout request nhận amount/reference, backend tạo payoutId. Redis `tc:idem:payout:<adminId>:<key>` và lock `tc:lock:settlement:<id>`.
- SP16/DB giữ `pendingAmount` trước khi gọi adapter. Gọi provider ngoài transaction; result cập nhật log/paid/pending trong transaction sau.
- Same payoutId/amount trả kết quả cũ; amount khác 409. Timeout sau side effect phải query theo payoutId, không tạo payout mới.
- Redis outage không được thực hiện payout nếu không chứng minh idempotency bằng DB unique payoutId.
- Sau commit invalidate settlement/report/dashboard caches.

## Test

- Confirm có blocker, confirm lặp, payout vượt số dư, concurrent payout, FAILED, timeout/restart, net=0.
- Manual Swagger confirm/payout/list; kiểm DB balances/log và Redis result.

