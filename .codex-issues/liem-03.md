> **Convention:** `dev` → `feat/liem/liem-03-vnpay-initiation` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa code khởi tạo thanh toán VNPAY trong `payment/controller/**`, `payment/service/**`, `payment/repository/**`, `payment/dto/**`, `payment/vnpay/**`, checkout JSP và test tương ứng. Cấm sửa IPN callback handler (thuộc LIEM-04), Refund hoặc secret config chung.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng cổng tích hợp khởi tạo giao dịch thanh toán trực tuyến qua cổng VNPAY Sandbox:
- Phục vụ màn hình **UI-04 (Chuyển hướng thanh toán VNPAY)**.
- Khi người dùng nhấn nút "Thanh toán qua VNPAY":
  - Kiểm tra tính hợp lệ của Đơn hàng: Phải thuộc về chính Buyer, đang ở trạng thái `PENDING_PAYMENT`, và phiên giữ vé/thời hạn thanh toán chưa hết hạn.
  - Không cho phép thanh toán nếu đơn hàng đã có một giao dịch thanh toán khác đang ở trạng thái `PENDING` hoặc `PROCESSING`.
  - Sinh mã tham chiếu giao dịch duy nhất (`vnp_TxnRef`) và lưu bản ghi Payment vào database.
  - Tạo chuỗi URL chuyển hướng sang cổng thanh toán VNPAY với chữ ký số HMAC-SHA512 chuẩn xác.
- Xử lý đơn hàng 0 đồng (Zero-amount Order - ví dụ áp dụng mã giảm giá 100%): **Tuyệt đối không gọi sang cổng VNPAY**, tự động hoàn tất đơn hàng qua luồng thanh toán nội bộ.
- Chống gửi đúp (Double-click / Retry) bằng cơ chế Distributed Lock và Idempotency trên Redis.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. POST `/api/orders/{orderId}/payments`
- **Mục đích:** Khởi tạo phiên thanh toán cho đơn hàng.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "paymentMethod": "VNPAY",
  "bankCode": "NCB"
}
```
- **Response Body (200 OK - Thanh toán thông thường):**
```json
{
  "paymentId": 7001,
  "orderId": 5001,
  "txnRef": "TXN_20261009_5001_8F3A",
  "amount": "2320000.00",
  "currency": "VND",
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=232000000&vnp_Command=pay&vnp_CreateDate=20261009183000&vnp_CurrCode=VND&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+don+hang+5001&vnp_OrderType=other&vnp_ReturnUrl=https%3A%2F%2Fticketscenter.vn%2Fpayment%2Fvnpay-return&vnp_TmnCode=TCENTER1&vnp_TxnRef=TXN_20261009_5001_8F3A&vnp_Version=2.1.0&vnp_SecureHash=a9b8c7...",
  "status": "PENDING"
}
```
- **Response Body (200 OK - Đơn hàng 0 đồng):**
```json
{
  "paymentId": 7002,
  "orderId": 5002,
  "amount": "0.00",
  "paymentUrl": null,
  "status": "COMPLETED",
  "message": "Đơn hàng miễn phí đã được hoàn tất tự động."
}
```
- **Lỗi:**
  - `400 Bad Request`: Đơn hàng đã hết hạn hoặc đã được thanh toán.
  - `403 Forbidden`: Người dùng không sở hữu đơn hàng.
  - `409 Conflict`: Đang có một phiên thanh toán khác cho đơn hàng này đang chờ xử lý.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Quy trình Transaction Tạo Ý định Thanh toán
- Gọi Stored Procedure canonical `dbo.sp_payment_start`:
  ```sql
  EXEC dbo.sp_payment_start
       @order_id = :orderId,
       @buyer_user_id = :actorId,
       @provider = 'VNPAY',
       @txn_ref = :txnRef,
       @new_payment_id = :paymentId OUTPUT;
  ```
  - Trong nội tại SP:
    1. Kiểm tra trạng thái đơn hàng: Phải là `PENDING_PAYMENT`.
    2. Kiểm tra xem có bản ghi Payment nào của đơn hàng đang `PENDING` không.
    3. Tạo bản ghi mới trong `dbo.tc_payments` với `status = 'PENDING'`, lưu `txn_ref`, `amount = order.total_amount`.
    4. Commit transaction cơ sở dữ liệu **trước khi** thực hiện logic tính toán URL và chữ ký.

### 3.2. Thuật toán Ký số VNPAY Canonical HMAC-SHA512
- **Quy tắc ký số bắt buộc của VNPAY:**
  1. Tập hợp tất cả các tham số có tiền tố `vnp_` (ngoại trừ `vnp_SecureHash` và `vnp_SecureHashType`).
  2. Loại bỏ các tham số có giá trị `null` hoặc rỗng (`""`).
  3. Sắp xếp danh sách tham số theo thứ tự bảng chữ cái tăng dần (Alphabetical / ASCII order) của tên tham số (Key).
  4. Nối các cặp `key=value` bằng ký tự `&` (ví dụ: `vnp_Amount=232000000&vnp_Command=pay&...`). Giá trị được URL-encode bằng UTF-8 / US-ASCII (thay thế khoảng trắng bằng `%20` hoặc `+` theo quy định chuẩn VNPAY 2.1.0).
  5. Nhân số tiền với 100 theo yêu cầu VNPAY: `vnp_Amount = amount.multiply(new BigDecimal(100)).toBigInteger().toString()`.
  6. Sử dụng khóa bí mật `VNPAY_HASH_SECRET` để sinh mã băm HMAC-SHA512 (chuỗi Hex in thường/hoa theo config).
  7. Nối thêm tham số `&vnp_SecureHash=<hash>` vào cuối URL.

### 3.3. Chống Tranh chấp Đơn hàng & Double Request với Redis Lock
- Sử dụng Distributed Lock ngắn hạn trên Redis:
  - Key: `tc:lock:order-payment:<orderId>` (TTL: 10 giây).
  - Sử dụng `SET tc:lock:order-payment:<orderId> <token> NX PX 10000`.
  - Ngăn ngừa tình trạng người dùng click nút thanh toán liên tục khiến sinh ra 2 `txnRef` khác nhau cho cùng một đơn hàng.
- **Idempotency Key:** `tc:idem:vnpay:init:<actorId>:<key>` (TTL 600s).
  - Replay cùng key và payload -> Trả lại URL thanh toán đã tạo trước đó.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `PaymentInitiateRequestDto`, `PaymentInitiateResponseDto`.
   - VNPAY Integration: `VnPayConfig`, `VnPayCryptoHelper` (triển khai thuật toán sắp xếp canonical params và HMAC-SHA512), `VnPayUrlBuilder`.
   - Repositories: `PaymentRepository` (gọi `dbo.sp_payment_start`, truy vấn `dbo.v_payments`).
   - Core Services: `PaymentInitiationService`.
   - Servlets: `PaymentInitiateServlet`.
2. **Giao diện JSP:**
   - Màn hình thanh toán UI-04 có nút "Xác nhận & Đi đến cổng VNPAY", tự động chuyển hướng (Redirect 302) hoặc hiển thị nút bấm chuyển trang an toàn.
3. **Database Changes:**
   - Tạo bản ghi mới trong `dbo.tc_payments` với `status = 'PENDING'`, `provider = 'VNPAY'`, mã `txn_ref` duy nhất.
4. **Kiểm thử tự động:**
   - `VnPayCryptoHelperTest`: Kiểm tra chữ ký số với bộ dữ liệu mẫu (fixtures) của VNPAY Sandbox bảo đảm kết quả băm khớp 100%.
   - `PaymentInitiationServiceTest`: Kiểm tra đơn hàng 0 đồng không gọi VNPAY, kiểm tra đơn hàng hết hạn bị từ chối.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Xây dựng `VnPayCryptoHelper`**
  - Triển khai hàm `hmacSHA512(String key, String data)`.
  - Triển khai hàm `buildCanonicalQueryString(Map<String, String> params)`.
- **Bước 2: Xây dựng `PaymentRepository`**
  - Viết lệnh gọi Stored Procedure `dbo.sp_payment_start` với JDBC.
- **Bước 3: Xây dựng `PaymentInitiationService`**
  - Kiểm tra Distributed Lock trên Redis.
  - Kiểm tra tổng tiền đơn hàng:
    - Nếu `totalAmount == 0`: Gọi trực tiếp SP hoàn tất đơn nội bộ, trả về `status = COMPLETED`.
    - Nếu `totalAmount > 0`: Sinh `txnRef`, gọi `sp_payment_start`, xây dựng URL VNPAY và trả về.
- **Bước 4: Xây dựng `PaymentInitiateServlet`**
  - Nhận yêu cầu POST, kiểm tra CSRF token, trả về JSON chứa `paymentUrl`.
- **Bước 5: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% ca test đều pass.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| PAY-01 | Khởi tạo thanh toán hợp lệ | Order hợp lệ, total = 500,000 | 200 OK, trả về URL có vnp_Amount=50000000 và chữ ký SecureHash |
| PAY-02 | Khởi tạo cho đơn hàng 0 đồng | Order áp mã coupon 100% (total = 0) | 200 OK, paymentUrl=null, status=COMPLETED |
| PAY-03 | Thanh toán đơn hàng của người khác | Buyer A truyền orderId của Buyer B | 403 Forbidden |
| PAY-04 | Double Click thanh toán | Gửi 2 request song song trong 100ms | 1 request tạo thành công, request thứ hai trả kết quả cũ qua Idempotency |
| PAY-05 | Kiểm tra chữ ký VNPAY | Chạy test so sánh với fixture sandbox | Chuỗi hash tạo ra khớp tuyệt đối với test vector |
