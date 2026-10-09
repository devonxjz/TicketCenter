> **Convention:** `dev` → `feat/khanh/khanh-05-refund-worker` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa luồng xử lý hoàn tiền nền trong `fulfillment/refund/worker/**`, `RefundJob`, `RefundGateway/SimulatedRefundGateway` và test tương ứng. Tuyệt đối không sửa luồng khởi tạo thanh toán VNPAY của Liêm.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng tiến trình chạy nền xử lý Chuyển tiền Hoàn trả (Refund Worker), ghi nhận các lần thử độc lập (Durable Attempts) và Đối soát an toàn (Reconciliation):
- Thực hiện nghĩa vụ hoàn tiền cho các yêu cầu đã được Admin phê duyệt (`APPROVED`) hoặc các yêu cầu bồi hoàn tự động từ hệ thống.
- **Nguyên tắc Chống Hoàn tiền Trùng (Double-Refund Prevention):**
  - Tuyệt đối không bao giờ hoàn tiền 2 lần cho cùng một chiếc vé hoặc đơn hàng, ngay cả khi Worker bị khởi động lại (Restart), bị sập nguồn (Crash) hoặc gặp sự cố Timeout mạng giữa chừng.
  - Sử dụng mô hình **Lần thử bền vững (Durable Attempt Pattern)**: Mỗi lần thực hiện chuyển tiền được cấp một mã `attemptId` duy nhất trong cơ sở dữ liệu.
- Phân tách rõ ràng giữa Database Transaction và Network Gateway:
  - Khóa và ghi nhận ý định chuyển tiền (`Claim Attempt`) trong DB Transaction 1 (Commit ngay).
  - Gọi dịch vụ cổng thanh toán / ngân hàng ngoài Transaction (I/O Out-of-Transaction).
  - Nhận kết quả và ghi nhận trạng thái cuối cùng trong DB Transaction 2.
- Xử lý hoàn tiền 0 đồng: Hoàn tất trực tiếp trong cơ sở dữ liệu, không gọi gateway và không tạo giao dịch giả.

## 2. Đặc tả Cổng Gateway & Hợp đồng Dịch vụ

### 2.1. Hợp đồng `RefundGateway`
```java
public interface RefundGateway {
    RefundTransferResult executeRefund(RefundTransferRequest request);
    RefundTransferStatus queryRefundStatus(String providerReference, String attemptId);
}
```
- **Các trạng thái kết quả trả về (`RefundGatewayStatus`):**
  - `SUCCEEDED`: Tiền đã chuyển thành công về tài khoản/thẻ của khách hàng.
  - `FAILED`: Giao dịch bị từ chối dứt khoát (Thẻ hết hạn, tài khoản đóng, số dư tài khoản hoàn không đủ).
  - `UNKNOWN`: Gặp sự cố Timeout mạng, Gateway không phản hồi, Socket đứt giữa chừng. Chưa rõ tiền đã trừ hay chưa!

### 2.2. Xử lý Trạng thái `UNKNOWN` & Quy tắc Đối soát (Reconciliation)
- Khi nhận trạng thái `UNKNOWN`:
  - **Cấm tuyệt đối việc thử lại ngay (No Auto Retry)**.
  - Đánh dấu lần thử là `UNKNOWN`.
  - Luồng đối soát định kỳ (`RefundReconciliationJob`) sẽ gọi hàm `queryRefundStatus` sang phía đối tác để kiểm tra trạng thái thực tế trước khi ra quyết định cuối cùng.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Vòng đời Xử lý Hoàn tiền qua Stored Procedures Canonical
- **Giai đoạn 1 - Nhận việc (Claim Work & Begin Attempt):**
  - Mở transaction ngắn gọi `dbo.sp_refund_begin_attempt`:
    ```sql
    EXEC dbo.sp_refund_begin_attempt
         @refund_id = :refundId,
         @new_attempt_id = :attemptId OUTPUT;
    ```
    - Nội tại SP: Cập nhật `tc_refunds.status = 'PROCESSING'`, tạo 1 dòng trong `dbo.tc_refund_attempts` với `status = 'PENDING'`, `attempt_number = attempt_number + 1`. Commit transaction để giải phóng kết nối DB.
- **Giai đoạn 2 - Gọi Cổng Hoàn tiền (Network I/O):**
  - Thực hiện gọi `RefundGateway.executeRefund(...)`. Có thể mất từ 2 đến 10 giây.
- **Giai đoạn 3 - Ghi nhận Kết quả (Record Result):**
  - Mở transaction mới gọi `dbo.sp_refund_record_attempt_result`:
    ```sql
    EXEC dbo.sp_refund_record_attempt_result
         @attempt_id = :attemptId,
         @status = :status, -- 'SUCCEEDED' | 'FAILED' | 'UNKNOWN'
         @provider_reference = :providerRef,
         @error_message = :errorMsg;
    ```
    - Nếu `@status == 'SUCCEEDED'`:
      1. Cập nhật attempt sang `SUCCEEDED`.
      2. Cập nhật `dbo.tc_refunds` sang `COMPLETED`, `completed_at = SYSDATETIME()`.
      3. Gọi `dbo.sp_ticket_mark_refunded`: Cập nhật toàn bộ vé liên quan sang trạng thái vĩnh viễn `REFUNDED`.
      4. Gọi `dbo.sp_seat_return_to_inventory` hoặc `dbo.sp_zone_return_sold_standing`: **Hoàn trả ghế/sức chứa vé lại vào kho bán của sự kiện** (nếu sự kiện vẫn chưa bắt đầu và chưa bị hủy).
    - Nếu `@status == 'FAILED'`:
      1. Cập nhật attempt sang `FAILED`.
      2. Cập nhật `tc_refunds.status = 'FAILED'`. Cho phép Admin xem xét để bấm Retry ở UI-21.
    - Nếu `@status == 'UNKNOWN'`:
      1. Cập nhật attempt sang `UNKNOWN`. Giữ nguyên `tc_refunds.status = 'PROCESSING'`.

### 3.2. Cơ chế Phân tán Lease trên Redis (Distributed Lease)
- Để tránh tình trạng nhiều Worker chạy song song cùng nhận 1 bản ghi Refund:
  - Redis Key: `tc:lease:refund:<refundId>` (TTL: 120 giây).
  - Worker phải lấy được lease qua `SET tc:lease:refund:<refundId> <workerInstanceId> NX EX 120`.
  - Nếu Redis gặp sự cố: Cơ chế khóa trạng thái `status = 'PROCESSING'` trong SQL Server là chốt chặn an toàn thứ hai ngăn ngừa xử lý trùng lặp.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - Worker & Schedulers: `RefundProcessingWorker`, `RefundReconciliationJob`.
   - Gateways: `RefundGateway` (interface), `SimulatedRefundGateway` (mô phỏng cho môi trường local/test), `VnPayRefundGateway` (nếu tích hợp VNPAY Refund API).
   - Repositories: `RefundWorkerRepository` (gọi các SP `sp_refund_begin_attempt`, `sp_refund_record_attempt_result`, `sp_refund_complete_without_transfer`).
   - DTOs: `RefundTransferRequest`, `RefundTransferResult`.
2. **Database Changes:**
   - Bảng `dbo.tc_refund_attempts` ghi lại chi tiết từng lần gọi cổng thanh toán (`attempt_id`, `status`, `provider_ref`, `error_message`, `created_at`).
   - Bảng `dbo.tc_refunds` chuyển sang `'COMPLETED'` hoặc `'FAILED'`.
   - Bảng `dbo.tc_tickets` chuyển sang `'REFUNDED'`.
   - Bảng `dbo.tc_seats` phục hồi về `'AVAILABLE'`.
3. **Kiểm thử tự động:**
   - `RefundWorkerTest`: Kiểm tra kịch bản Gateway trả về SUCCEEDED (tiền hoàn tất, ghế trả về kho), FAILED (cho phép admin retry), UNKNOWN (chuyển sang hàng đợi đối soát).
   - `WorkerCrashRecoveryTest`: Mô phỏng Worker bị crash giữa lúc đang gọi gateway, khi khởi động lại nhận diện đúng trạng thái và không hoàn tiền đúp.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Xây dựng `RefundGateway` & `SimulatedRefundGateway`**
  - Hỗ trợ các kịch bản test giả lập: Luôn thành công, luôn thất bại, hoặc timeout ném `SocketTimeoutException`.
- **Bước 2: Triển khai `RefundWorkerRepository`**
  - Viết các phương thức JDBC gọi `sp_refund_begin_attempt` và `sp_refund_record_attempt_result`.
- **Bước 3: Xây dựng `RefundProcessingWorker`**
  - Tìm các bản ghi `APPROVED` trong DB.
  - Sử dụng Redis Lease để giữ quyền xử lý.
  - Thực hiện quy trình 3 giai đoạn: Begin Attempt -> Gọi Gateway -> Ghi nhận kết quả.
- **Bước 4: Xây dựng `RefundReconciliationJob`**
  - Định kỳ 15 phút quét các attempt `UNKNOWN` để tra cứu trạng thái phía đối tác.
- **Bước 5: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| WRK-01 | Hoàn tiền thành công | Refund APPROVED, Gateway trả SUCCEEDED | Refund thành COMPLETED, vé thành REFUNDED, ghế trả về kho |
| WRK-02 | Cổng từ chối hoàn tiền | Gateway trả FAILED (thẻ hết hạn) | Attempt thành FAILED, Refund thành FAILED, admin có thể retry |
| WRK-03 | Lỗi mạng Timeout (UNKNOWN) | Gateway bị ngắt kết nối mạng | Attempt thành UNKNOWN, không hoàn lại tiền, chờ job đối soát |
| WRK-04 | Hoàn tiền đơn 0 đồng | Refund có số tiền 0 VND | Hoàn tất ngay trong DB không gọi Gateway |
| WRK-05 | Khởi động lại Worker (Crash Recovery) | Worker restart khi đang có attempt PENDING | Không sinh lệnh chuyển tiền thứ 2, kiểm tra trạng thái attempt trước |
