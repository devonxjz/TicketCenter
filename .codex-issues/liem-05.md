> **Convention:** `dev` → `feat/liem/liem-05-ticket-qr-issuance` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Chỉ sửa code phát hành vé và sinh mã QR trong `fulfillment/ticket/**`, Order/Ticket JSP (UI-05), Outbox email producer và test tương ứng. Tuyệt đối không sửa bộ giải mã check-in tại cổng của Khánh (ở KHANH-02).

## 1. Mục tiêu và Nghiệp vụ

Xây dựng quy trình phát hành Vé điện tử (E-Ticket Issuance) và tạo Mã QR có chữ ký số bảo mật (Cryptographically Signed QR Code):
- Phục vụ trực tiếp màn hình **UI-05 (Vé của tôi & Chi tiết vé điện tử)**.
- Ràng buộc phát hành vé bất biến:
  - **Mỗi quyền vào cổng là 1 vé độc lập:** Nếu trong đơn hàng khách mua 3 vé standing, hệ thống phải phát hành đúng 3 bản ghi `Ticket` riêng biệt với mã vé (`ticketCode`) và mã QR khác nhau.
  - **Bảo toàn số tiền:** Tổng `paidAmount` của các vé phát hành phải khớp chính xác với `totalAmount` của Đơn hàng sau khi đã trừ mã giảm giá.
  - **Chuẩn định dạng mã QR có chữ ký số chống làm giả (Tamper-proof Signed QR):**
    - Nội dung QR được mã hóa theo cấu trúc phong bì 2 phần phân tách bằng dấu chấm: `<Base64Url(Payload)>.<Base64Url(Signature)>`.
    - Payload là JSON chuẩn hóa UTF-8: `{"id":"<ticket-uuid>","name":"<customer-name>"}`.
    - Signature là chữ ký HMAC-SHA256 được tính toán từ Payload sử dụng khóa bí mật của hệ thống (`TC_TICKET_SIGNING_SECRET`).
    - Nhân viên soát vé khi quét QR chỉ cần xác minh chữ ký là có thể phát hiện ngay vé giả mạo, vé bị sửa đổi ID hoặc tên người xem mà không thể làm giả chữ ký.
- Quyền riêng tư: Chỉ người sở hữu vé (`ticket.userId == actorId`) mới có quyền xem thông tin vé và tải ảnh mã QR.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/me/tickets`
- **Mục đích:** Khách hàng xem danh sách tất cả các vé của mình.
- **Quyền hạn:** `AUTHENTICATED`.
- **Query Params:** `status` (tùy chọn: `ISSUED`, `CHECKED_IN`, `REFUNDED`), `page`, `pageSize`.
- **Response Body (200 OK):**
```json
{
  "items": [
    {
      "ticketId": 10001,
      "ticketCode": "TC-9F8A-4B2C-8D1E",
      "orderId": 5001,
      "eventName": "Đêm Nhạc Mùa Thu 2026",
      "zoneName": "Khu VIP Khán Đài A",
      "seatLabel": "A-01",
      "paidAmount": "1160000.00",
      "status": "ISSUED",
      "eventStartTime": "2026-11-15T19:00:00Z",
      "venue": "Nhà Hát Hòa Bình, TP.HCM"
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

### 2.2. GET `/api/tickets/{ticketId}`
- **Mục đích:** Lấy thông tin chi tiết một chiếc vé cụ thể.
- **Lỗi 403 Forbidden:** Nếu vé không thuộc sở hữu của người đang đăng nhập.

### 2.3. GET `/api/tickets/{ticketId}/qr`
- **Mục đích:** Tải ảnh QR code của vé dưới định dạng ảnh PNG để người xem đưa cho nhân viên quét tại cổng.
- **Response Headers:** `Content-Type: image/png`, `Cache-Control: private, max-age=300`.
- **Response Body:** Mảng byte nhị phân của tệp ảnh PNG.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Thuật toán Phát hành Vé Idempotent trong DB
- Khi đơn hàng chuyển sang `PAID`, kích hoạt nghiệp vụ phát hành vé trong cùng transaction:
  - Duyệt qua từng `OrderItem`:
    - Với mỗi đơn vị số lượng (`quantity`):
      - Sinh mã `ticket_code` ngẫu nhiên dạng UUID v4: `UUID.randomUUID().toString()`.
      - Phân bổ số tiền đã trả: `paid_amount = line_total / quantity`.
      - Insert vào bảng `dbo.tc_tickets` với `status = 'ISSUED'`, `issued_at = SYSDATETIME()`.
  - Tạo bản ghi Outbox trong bảng `dbo.tc_outbox_emails` để chuẩn bị gửi email đính kèm vé cho khách hàng sau khi transaction commit thành công.

### 3.2. Thuật toán Ký và Sinh Ảnh QR bằng ZXing
- **Quy chuẩn Payload JSON Canonical:**
  ```json
  {"id":"3fa85f64-5717-4562-b3fc-2c963f66afa6","name":"Nguyen Van A"}
  ```
  Thứ tự các trường bắt buộc cố định: `id` đứng trước, `name` đứng sau. Không chứa khoảng trắng thừa.
- **Tính chữ ký HMAC-SHA256:**
  ```java
  Mac hmac = Mac.getInstance("HmacSHA256");
  hmac.init(new SecretKeySpec(signingSecretBytes, "HmacSHA256"));
  byte[] signatureBytes = hmac.doFinal(canonicalJsonBytes);
  ```
- **Tạo chuỗi Envelope chuẩn:**
  ```java
  String qrContent = Base64.getUrlEncoder().withoutPadding().encodeToString(canonicalJsonBytes)
                   + "." 
                   + Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
  ```
- **Sinh ảnh QR qua thư viện ZXing:**
  - Cấu hình: `BarcodeFormat.QR_CODE`, kích thước 300x300 pixels, mức sửa lỗi `ErrorCorrectionLevel.M`, UTF-8 charset.
  - Sử dụng `MatrixToImageWriter.writeToStream(bitMatrix, "PNG", byteArrayOutputStream)`.

### 3.3. Cơ chế Đệm Bộ nhớ (Caching) Ảnh QR trên Redis
- Sinh ảnh QR bằng CPU tốn chi phí xử lý. Do đó, áp dụng cache ảnh byte trên Redis:
  - Key: `tc:ticket:qr:v1:<ticketId>:<version>` (TTL: 3600s / 1 giờ).
  - Khi vé đổi trạng thái (ví dụ đã CHECKED_IN hoặc REFUNDED), tăng phiên bản hoặc xóa key ngay lập tức.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `TicketSummaryDto`, `TicketDetailDto`, `SignedQrPayloadDto`.
   - QR Generator & Signer: `TicketQrSigner`, `ZxingQrCodeGenerator`.
   - Repositories: `TicketRepository` (lưu vé, truy vấn qua `dbo.v_tickets`, `dbo.ticket_locations`).
   - Core Services: `TicketIssuanceService`, `TicketQueryService`.
   - Servlets: `MyTicketsServlet` (`/api/me/tickets`), `TicketDetailServlet` (`/api/tickets/{id}`), `TicketQrServlet` (`/api/tickets/{id}/qr`).
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/ticket/my-tickets.jsp`: Danh sách vé đã mua, thẻ card vé đẹp mắt có hình ảnh sự kiện, ghế ngồi.
   - `src/main/webapp/WEB-INF/views/ticket/ticket-detail.jsp`: Trang vé điện tử hiển thị ảnh mã QR to rõ ràng, thông tin hướng dẫn vào cửa, nút tải vé về điện thoại.
3. **Database Changes:**
   - Bảng `dbo.tc_tickets` ghi nhận các bản ghi vé tương ứng với đơn hàng.
4. **Kiểm thử tự động:**
   - `TicketQrSignerTest`: Kiểm tra ký và giải mã chữ ký số, kiểm tra việc giả mạo ID hoặc tên bị phát hiện và từ chối.
   - `TicketIssuanceTest`: Đơn hàng 3 vé standing sinh đúng 3 ticket riêng biệt có mã khác nhau.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Triển khai `TicketQrSigner`**
  - Viết hàm tạo chuỗi Envelope và hàm xác thực chữ ký số bằng `MessageDigest.isEqual`.
- **Bước 2: Triển khai `ZxingQrCodeGenerator`**
  - Tích hợp ZXing `QRCodeWriter` xuất ra `byte[]` định dạng PNG.
- **Bước 3: Xây dựng `TicketRepository`**
  - Truy vấn danh sách vé theo `userId` từ View `dbo.v_tickets`.
- **Bước 4: Xây dựng `TicketQueryService`**
  - Kiểm tra quyền sở hữu vé của Actor trước khi trả về dữ liệu hoặc ảnh QR.
  - Tích hợp cache ảnh QR trên Redis.
- **Bước 5: Xây dựng Servlets & Views**
  - `TicketQrServlet`: Set header `Content-Type: image/png` và ghi trực tiếp `byte[]` vào `response.getOutputStream()`.
  - Thiết kế màn hình xem vé UI-05 thân thiện cho người dùng di động.
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| TCK-01 | Khách xem danh sách vé của mình | Buyer đã đăng nhập | 200 OK, trả về danh sách các vé thuộc về buyer |
| TCK-02 | Xem vé của người khác | Buyer A truyền ticketId của Buyer B | 403 Forbidden |
| TCK-03 | Tải ảnh QR Code | Gọi GET `/api/tickets/{id}/qr` | 200 OK, Content-Type là image/png, quét ra đúng chuỗi envelope |
| TCK-04 | Kiểm tra chữ ký chống giả mạo | Thử đổi ID hoặc Name trong envelope | Chữ ký không khớp, xác minh thất bại |
| TCK-05 | Phát hành vé đúng số lượng | Order có 2 ghế và 3 vé đứng | DB tạo chính xác 5 bản ghi Ticket với 5 mã duy nhất |
