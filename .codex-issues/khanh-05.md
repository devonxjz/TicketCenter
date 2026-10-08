> **Convention:** `dev` → `feat/khanh/khanh-05-refund-worker` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa RefundJob, RefundRepository, RefundGateway/SimulatedRefundGateway và test. Không sửa Payment/VNPAY của Liêm.

## Mục tiêu

Thực hiện hoặc đối chiếu Refund có tiền bằng durable attempt, không hoàn hai lần khi worker restart hoặc timeout.

## Kỹ thuật

- DB `RefundTransferLog` và `currentAttemptId` là lịch sử thật. Redis chỉ lease/dedupe: `tc:lease:refund:<refundId>`, `tc:idem:refund-attempt:<attemptId>`.
- Claim work trong transaction ngắn, commit intent/attempt trước khi gọi gateway.
- Gọi submit/query ngoài DB transaction. Sau kết quả, SP11/đường canonical ghi SUCCEEDED/FAILED/UNKNOWN, cập nhật Ticket/inventory và audit trong transaction.
- Timeout sau side effect không tạo attempt mới; lần sau query provider theo providerReference/attemptId.
- SUCCEEDED chỉ áp một lần; FAILED mới được admin yêu cầu attemptId mới; UNKNOWN không retry tự động.
- Hoàn 0 đồng hoàn tất DB không gọi gateway và không tạo transfer log giả.
- Redis outage không được dẫn tới submit trùng; nếu DB không chứng minh lease/attempt hiện hành thì worker bỏ qua và cảnh báo.

## Test

- Success, failed, unknown, timeout trước/sau side effect, worker đồng thời, restart và replay result.
- Manual Swagger chỉ dùng endpoint admin để khởi tạo; không có endpoint browser đặt provider result. Kiểm DB/Redis/log đã lọc.

