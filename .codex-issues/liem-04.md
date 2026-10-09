> **Convention:** `dev` → `feat/liem/liem-04-vnpay-callback-reconcile` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa code xử lý VNPAY Return, IPN webhook, đối soát thanh toán trong `payment/webhook/**`, `payment/reconciliation/**`, `PaymentReconciliationJob` và test tương ứng. Tuyệt đối không can thiệp vào Refund worker của Khánh hay Event inventory của Đông.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng cổng tiếp nhận và xác thực kết quả thanh toán từ VNPAY qua hai kênh:
1. **Return URL (`GET /api/payments/vnpay/return`):** Trang đích chuyển hướng khách hàng sau khi thanh toán trên cổng VNPAY. **Chỉ dùng để hiển thị giao diện kết quả cho người dùng, tuyệt đối không cập nhật trạng thái tiền tệ tại endpoint này**.
2. **Instant Payment Notification - IPN Webhook (`GET /api/payments/vnpay/ipn`):** Giao tiếp trực tiếp giữa máy chủ VNPAY và máy chủ TicketsCenter (Server-to-Server). Đây là **nguồn căn cứ duy nhất** để quyết định đơn hàng đã được trả tiền thành công.
- Ràng buộc nghiệp vụ quan trọng:
  - Xác thực chữ ký số HMAC-SHA512 bằng kỹ thuật so sánh hằng số thời gian (`MessageDigest.isEqual`) để phòng chống tấn công Timing Attack.
  - Chống tấn công phát lại (Replay Attack): Lưu trữ và đối soát mã giao dịch đối tác (`vnp_TransactionNo`) trên Redis. Xử lý IPN trùng lặp trả về đúng định dạng mà VNPAY yêu cầu mà không trừ kho hoặc cộng tiền 2 lần.
  - Xử lý Thanh toán Muộn (Late Payment Anomaly): Nếu tiền về sau khi đơn hàng đã bị hủy/hết hạn, hệ thống vẫn ghi nhận thu tiền nhưng tự động sinh bản ghi Bồi hoàn (`Refund Compensation`) qua Stored Procedure, bảo vệ quyền lợi của khách hàng.
  - Luồng đối soát định kỳ (`PaymentReconciliationJob`): Quét các giao dịch bị treo (`PENDING`/`UNKNOWN`) để tự động tra soát và cập nhật kết quả cuối cùng.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/payments/vnpay/ipn`
- **Mục đích:** Webhook nhận thông báo thanh toán từ server VNPAY.
- **Quyền hạn:** Public (nhưng bắt buộc kiểm tra chữ ký HMAC-SHA512 với `VNPAY_HASH_SECRET`).
- **Query Parameters từ VNPAY gửi sang:**
  - `vnp_TmnCode`, `vnp_Amount`, `vnp_BankCode`, `vnp_BankTranNo`, `vnp_CardType`, `vnp_PayDate`, `vnp_OrderInfo`, `vnp_TransactionNo`, `vnp_ResponseCode`, `vnp_TransactionStatus`, `vnp_TxnRef`, `vnp_SecureHash`.
- **Response Body chuẩn VNPAY IPN Protocol (Bắt buộc định dạng JSON):**
  - Thành công:
    ```json
    {
      "RspCode": "00",
      "Message": "Confirm Success"
    }
    ```
  - Chữ ký không hợp lệ:
    ```json
    {
      "RspCode": "97",
      "Message": "Invalid Checksum"
    }
    ```
  - Không tìm thấy đơn hàng:
    ```json
    {
      "RspCode": "01",
      "Message": "Order Not Found"
    }
    ```
  - Đơn hàng đã được xác nhận trước đó (Idempotency):
    ```json
    {
      "RspCode": "02",
      "Message": "Order already confirmed"
    }
    ```
  - Số tiền thanh toán không khớp:
    ```json
    {
      "RspCode": "04",
      "Message": "Invalid Amount"
    }
    ```

### 2.2. GET `/api/payments/vnpay/return`
- **Mục đích:** Tiếp nhận khách hàng quay lại sau khi thanh toán trên cổng VNPAY.
- **Hành vi:** Đọc `vnp_TxnRef`, kiểm tra chữ ký hiển thị giao diện thành công / thất bại, sau đó chuyển hướng người dùng sang trang xem chi tiết vé hoặc đơn hàng. Không thực hiện update database tại đây!

### 2.3. GET `/api/orders/{orderId}/payment-status`
- **Mục đích:** Client Frontend gọi định kỳ (Polling) để kiểm tra xem webhook IPN đã cập nhật trạng thái đơn hàng thành công hay chưa.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Xác thực Chữ ký số An toàn Mật mã học
- Tách `vnp_SecureHash` ra khỏi danh sách tham số.
- Sắp xếp toàn bộ các tham số còn lại theo thứ tự bảng chữ cái (ASCII).
- Sinh mã băm HMAC-SHA512 với chuỗi secret của hệ thống.
- Sử dụng:
  ```java
  byte[] expectedHashBytes = expectedHash.getBytes(StandardCharsets.UTF_8);
  byte[] receivedHashBytes = receivedHash.getBytes(StandardCharsets.UTF_8);
  boolean isValid = MessageDigest.isEqual(expectedHashBytes, receivedHashBytes);
  ```
  Ngăn chặn kẻ tấn công đo thời gian phản hồi (Side-channel Timing Attack) để bẻ khóa secret.

### 3.2. Quy trình Cập nhật Thanh toán Nguyên tử (Atomic Settlement in DB)
- Khi IPN xác thực hợp lệ (`vnp_ResponseCode == "00"` và `vnp_TransactionStatus == "00"`):
  - Mở Database Transaction gọi Stored Procedure canonical:
    ```sql
    EXEC dbo.sp_payment_mark_captured
         @payment_id = :paymentId,
         @provider_txn_no = :vnpTransactionNo,
         @paid_at = :paidAt;
    ```
  - Trong nội tại SP:
    1. Kiểm tra trạng thái hiện tại của `dbo.tc_payments`. Nếu đã là `CAPTURED`, không xử lý tiếp (Idempotent).
    2. Cập nhật Payment sang `CAPTURED`.
    3. Tự động gọi `dbo.sp_order_mark_paid`: Chuyển đơn hàng sang `PAID`.
    4. Chuyển đổi toàn bộ ghế trong đơn hàng từ `HELD` sang `SOLD` (`dbo.sp_seat_mark_sold` hoặc `dbo.sp_zone_sell_held_standing`).
    5. Đánh dấu Coupon chuyển sang `CONSUMED` chính thức.
    6. Kích hoạt phát hành vé (`Ticket Issuance`).

### 3.3. Xử lý Thanh toán Bất thường & Bồi hoàn Tự động (Compensation Handling)
- Trường hợp IPN báo thành công nhưng đơn hàng trong hệ thống đã bị hủy do timeout trước đó, hoặc sự kiện bị hủy đột xuất:
  - SP phát hiện đơn hàng không ở trạng thái hợp lệ để `PAID`.
  - Thay vì bỏ qua (gây mất tiền của khách), SP tự động gọi:
    ```sql
    EXEC dbo.sp_refund_create_compensation
         @order_id = :orderId,
         @payment_id = :paymentId,
         @amount = :amount,
         @reason = 'LATE_PAYMENT_AUTOMATIC_REFUND';
    ```
  - Ghi nhận một yêu cầu hoàn tiền tự động trong `dbo.tc_refunds` để đội ngũ vận hành hoặc hoàn tiền tự động của Khánh xử lý.

### 3.4. Deduplication & Idempotency với Redis
- Redis Key: `tc:idem:vnpay:ipn:<txnRef>:<providerTxnNo>` (TTL: 7 ngày).
- Khi IPN đến:
  - Nếu key đã tồn tại: Đọc response JSON cũ (`{"RspCode":"00","Message":"Confirm Success"}`) và trả về ngay cho VNPAY.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - Webhook & Return Servlets: `VnPayIpnServlet` (`/api/payments/vnpay/ipn`), `VnPayReturnServlet` (`/api/payments/vnpay/return`).
   - Core Services: `PaymentWebhookService`, `PaymentReconciliationService`.
   - Jobs / Schedulers: `PaymentReconciliationJob` (quét giao dịch UNKNOWN mỗi 5 phút).
   - Repositories: Cập nhật `PaymentRepository` hỗ trợ `markCaptured`, `markFailed`, `findByTxnRef`.
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/payment/payment-result.jsp`: Màn hình hiển thị kết quả giao dịch đẹp mắt cho người mua sau khi trở về từ VNPAY (Mã đơn hàng, Số tiền, Trạng thái thành công/thất bại, nút Xem vé).
3. **Database Changes:**
   - `dbo.tc_payments` cập nhật `status = 'CAPTURED'`, `provider_txn_no`, `paid_at`.
   - `dbo.tc_orders` cập nhật `status = 'PAID'`.
   - `dbo.tc_seats` chuyển từ `'HELD'` sang `'SOLD'`.
4. **Kiểm thử tự động:**
   - `VnPayIpnServletTest`: Kiểm tra chữ ký hợp lệ trả về RspCode "00", chữ ký sai trả về "97", sai số tiền trả về "04".
   - `PaymentReconciliationJobTest`: Kiểm tra đối soát tự động xử lý đơn thanh toán thành công bị trễ IPN.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Xây dựng Bộ xác thực IPN trong `VnPayCryptoHelper`**
  - Trích xuất toàn bộ params từ `HttpServletRequest`.
  - Sắp xếp và băm chữ ký, so sánh hằng số thời gian với `MessageDigest.isEqual`.
- **Bước 2: Xây dựng `PaymentWebhookService`**
  - Kiểm tra tính trùng lặp qua Redis.
  - Mở transaction thực thi `dbo.sp_payment_mark_captured` qua repository.
  - Xử lý các nhánh ngoại lệ: late payment, sai số tiền.
- **Bước 3: Xây dựng `VnPayIpnServlet`**
  - Trả về JSON envelope `{ "RspCode": "...", "Message": "..." }` kèm header `Content-Type: application/json`.
- **Bước 4: Xây dựng `VnPayReturnServlet`**
  - Kiểm tra chữ ký tham số, lấy chi tiết đơn hàng, chuyển hướng sang `payment-result.jsp`.
- **Bước 5: Xây dựng `PaymentReconciliationJob`**
  - Sử dụng ScheduledExecutor định kỳ 5 phút quét các Payment PENDING quá 15 phút.
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% ca test đều pass.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| IPN-01 | IPN hợp lệ thành công | vnp_ResponseCode=00, chữ ký đúng | RspCode "00", DB chuyển Order sang PAID, ghế thành SOLD |
| IPN-02 | Chữ ký giả mạo | vnp_SecureHash bị sửa đổi | RspCode "97" (Invalid Checksum), DB không đổi |
| IPN-03 | Sai lệch số tiền thanh toán | Đơn hàng 500k nhưng vnp_Amount báo 400k | RspCode "04" (Invalid Amount) |
| IPN-04 | IPN gửi lại nhiều lần (Replay) | VNPAY gửi lại cùng 1 gói tin IPN | RspCode "02" hoặc "00", DB không xử lý trùng |
| IPN-05 | Thanh toán muộn khi đơn đã hủy | Order đã CANCELLED trước khi tiền về | RspCode "00", DB tạo tự động bản ghi Bồi hoàn trong dbo.tc_refunds |
