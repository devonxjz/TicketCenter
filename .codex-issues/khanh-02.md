> **Convention:** `dev` → `feat/khanh/khanh-02-qr-checkin` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Chỉ sửa code giải mã QR, dịch vụ soát vé, repository và cache trong `fulfillment/checkin/**`, JSP quét mã QR (UI-16) và test tương ứng. Tuyệt đối không can thiệp vào bộ sinh mã QR hay phát hành vé của Liêm (ở LIEM-05).

## 1. Mục tiêu và Nghiệp vụ

Xây dựng module Giải mã QR có chữ ký số (Signed QR Decoder) và Thực thi Điểm danh Soát vé Nguyên tử (Atomic Check-in):
- Phục vụ trực tiếp màn hình **UI-16 (Màn hình Camera Quét QR & Thông báo kết quả soát vé)**.
- Khi nhân viên soát vé quét mã QR từ điện thoại của khách hàng:
  - **Bước 1 - Xác thực Chữ ký số Mật mã:** Kiểm tra tính toàn vẹn của chuỗi QR envelope. Nếu chữ ký sai lệch hoặc bị làm giả, lập tức từ chối vé giả mạo.
  - **Bước 2 - Trích xuất Dữ liệu Định danh:** Đọc `ticketId` từ payload JSON hợp lệ. Trường `name` chỉ dùng để hiển thị chào mừng khách trên màn hình, không dùng để cấp quyền.
  - **Bước 3 - Kiểm tra Nghiệp vụ & Khóa Nguyên tử trong DB:**
    - Vé phải thuộc về đúng sự kiện đang mở soát vé.
    - Thời điểm quét phải nằm trong khung giờ soát vé hợp lệ (`[startTime - 60 phút, endTime)`).
    - Trạng thái vé phải là `ISSUED`. Nếu vé đã `CHECKED_IN` trước đó, từ chối và báo rõ thời gian vé đã vào cửa. Nếu vé đang `REFUND_PENDING` hoặc đã `REFUNDED`, từ chối ngay lập tức.
  - **Bước 4 - Cập nhật Nguyên tử:** Chuyển trạng thái vé sang `CHECKED_IN`, ghi nhận thời gian vào cổng và ID nhân viên soát vé vào nhật ký `dbo.tc_ticket_check_ins`.
- Chống gian lận quét trùng đồng thời (Double-scanning Race Condition): Đảm bảo 2 nhân viên ở 2 cửa khác nhau cùng quét 1 chiếc vé tại cùng 1 giây chỉ có đúng 1 người được duyệt vào cửa.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. POST `/api/check-ins`
- **Mục đích:** Thực hiện điểm danh vào cửa cho 1 vé qua chuỗi QR quét được.
- **Quyền hạn:** `MANAGER` hoặc `CHECK_IN_STAFF` của sự kiện.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "eventId": 201,
  "qrContent": "eyJpZCI6IjNmYTg1ZjY0LTU3MTctNDU2Mi1iM2ZjLTJjOTYzZjY2YWZhNiIsIm5hbWUiOiJOZ3V5ZW4gVmFuIEEifQ.q8P9z_K..."
}
```
- **Response Body (200 OK - Điểm danh thành công):**
```json
{
  "success": true,
  "status": "CHECKED_IN_SUCCESS",
  "ticketId": 10001,
  "ticketCode": "TC-9F8A-4B2C-8D1E",
  "customerName": "Nguyễn Văn A",
  "zoneName": "Khu VIP Khán Đài A",
  "seatLabel": "A-01",
  "checkedInAt": "2026-11-15T18:35:20Z",
  "message": "Soát vé thành công. Chào mừng khách hàng!"
}
```
- **Response Body (409 Conflict / 400 Bad Request - Từ chối vào cửa):**
  - Vé đã dùng:
    ```json
    {
      "success": false,
      "status": "ALREADY_CHECKED_IN",
      "checkedInAt": "2026-11-15T18:10:05Z",
      "message": "Vé này đã được điểm danh vào cửa lúc 18:10:05."
    }
    ```
  - Chữ ký giả mạo:
    ```json
    {
      "success": false,
      "status": "INVALID_SIGNATURE",
      "message": "Mã QR không hợp lệ hoặc đã bị chỉnh sửa bất hợp pháp."
    }
    ```
  - Vé đã hoàn tiền:
    ```json
    {
      "success": false,
      "status": "TICKET_REFUNDED",
      "message": "Vé này đã bị hủy do yêu cầu hoàn tiền."
    }
    ```

### 2.2. GET `/api/events/{eventId}/check-ins`
- **Mục đích:** Xem danh sách nhật ký các lượt soát vé gần nhất của sự kiện (để nhân viên theo dõi tiến độ).

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Quy trình Giải mã & Xác minh Chữ ký số Mật mã học (QR Decoding Protocol)
- **Thuật toán 5 bước nghiêm ngặt:**
  1. Kiểm tra chuỗi QR: Bắt buộc có đúng 1 dấu chấm `.` phân tách. Phần 1 là `base64UrlPayload`, phần 2 là `base64UrlSignature`.
  2. Giải mã Base64Url sang mảng byte: `payloadBytes` và `receivedSigBytes`.
  3. Tính toán chữ ký HMAC-SHA256 trên `payloadBytes` sử dụng `TC_TICKET_SIGNING_SECRET`:
     ```java
     Mac mac = Mac.getInstance("HmacSHA256");
     mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
     byte[] expectedSigBytes = mac.doFinal(payloadBytes);
     ```
  4. So sánh hằng số thời gian bằng `MessageDigest.isEqual(expectedSigBytes, receivedSigBytes)`. Nếu không khớp, dừng xử lý ngay lập tức (không parse JSON để tránh tấn công deserialization/CPU exhaustion).
  5. Parse JSON sang đối tượng Java: Schema bắt buộc `{"id": "<uuid>", "name": "<string>"}`. Lấy giá trị `id` để tra cứu trong DB.

### 3.2. Cập nhật Điểm danh Nguyên tử qua Stored Procedure
- Gọi Stored Procedure canonical `dbo.sp_ticket_check_in`:
  ```sql
  EXEC dbo.sp_ticket_check_in
       @ticket_uuid = :ticketUuid,
       @event_id = :eventId,
       @staff_user_id = :staffId,
       @checked_in_at = :now;
  ```
  - Trong nội tại SP:
    1. Sử dụng khóa dòng độc quyền: `SELECT id, status, event_id FROM dbo.tc_tickets WITH (UPDLOCK, ROWLOCK) WHERE uuid = @ticket_uuid`.
    2. Nếu không tìm thấy: Ném lỗi `TICKET_NOT_FOUND`.
    3. Nếu `event_id != @event_id`: Ném lỗi `TICKET_WRONG_EVENT`.
    4. Nếu `status == 'CHECKED_IN'`: Ném lỗi `TICKET_ALREADY_USED`.
    5. Nếu `status IN ('REFUND_PENDING', 'REFUNDED')`: Ném lỗi `TICKET_NOT_VALID_REFUNDED`.
    6. Nếu `status != 'ISSUED'`: Ném lỗi `TICKET_INVALID_STATUS`.
    7. Cập nhật `status = 'CHECKED_IN'`.
    8. Insert bản ghi vào `dbo.tc_ticket_check_ins`.
    9. Ghi vết kiểm toán vào `dbo.tc_audit_logs`.

### 3.3. Chống Tranh chấp Điểm danh (Distributed Lock & Idempotency)
- Sử dụng Redis Distributed Lock ngắn:
  - Key: `tc:lock:ticket-checkin:<ticketId>` (TTL: 5 giây).
  - Ngăn ngừa tình trạng 2 máy quét gửi 2 request trong cùng mili-giây gây xung đột DB.
- **Idempotency Key:** `tc:idem:checkin:<actorId>:<key>` (TTL: 3600s).
  - Nhân viên quét lại mã của cùng 1 vé trong phiên làm việc sẽ nhận lại kết quả cũ mà không làm sai lệch số liệu thống kê.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `CheckInScanRequestDto`, `CheckInScanResponseDto`, `RecentCheckInLogDto`.
   - Security Decoder: `SignedQrDecoder` (kiểm tra chữ ký HMAC-SHA256, parse JSON canonical).
   - Repositories: `TicketCheckInRepository` (gọi `dbo.sp_ticket_check_in`, truy vấn nhật ký soát vé).
   - Core Services: `TicketCheckInService`.
   - Servlets: `TicketCheckInServlet` (`/api/check-ins`), `EventCheckInLogsServlet` (`/api/events/{id}/check-ins`).
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/checkin/scanner.jsp`: Giao diện UI-16 tích hợp camera quét mã QR bằng thư viện JavaScript (như html5-qrcode), hộp thông báo kết quả to rõ (Màu Xanh lá cho Hợp lệ, Màu Đỏ cho Không hợp lệ / Đã sử dụng), phát âm thanh beep thông báo.
3. **Database Changes:**
   - Bảng `dbo.tc_tickets` cập nhật `status = 'CHECKED_IN'`.
   - Bảng `dbo.tc_ticket_check_ins` thêm 1 bản ghi mới (`ticket_id`, `staff_user_id`, `checked_in_at`).
4. **Kiểm thử tự động:**
   - `SignedQrDecoderTest`: Kiểm tra giải mã thành công QR chuẩn, từ chối khi chữ ký bị thay đổi 1 bit, từ chối khi JSON thiếu trường id.
   - `TicketCheckInServiceTest`: Kiểm tra vé đã dùng bị từ chối, vé sai sự kiện bị từ chối, vé hợp lệ check-in thành công.
   - `CheckInConcurrencyTest`: 2 luồng quét cùng 1 vé chỉ có 1 luồng báo thành công.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Xây dựng `SignedQrDecoder`**
  - Tách chuỗi payload và signature, kiểm tra HMAC-SHA256 với `MessageDigest.isEqual`.
- **Bước 2: Triển khai `TicketCheckInRepository`**
  - Thực thi Stored Procedure `dbo.sp_ticket_check_in` với JDBC CallableStatement.
- **Bước 3: Xây dựng `TicketCheckInService`**
  - Kiểm tra khung giờ soát vé của Event qua `CheckInEventService`.
  - Giải mã QR qua `SignedQrDecoder`.
  - Thực thi cập nhật DB qua repository.
  - Xóa cache chi tiết vé trên Redis: `DEL tc:ticket:qr:v1:<ticketId>:*`.
- **Bước 4: Xây dựng Servlets**
  - Định tuyến POST `/api/check-ins`, trả về envelope JSON với HTTP status code phù hợp.
- **Bước 5: Xây dựng Giao diện JSP (UI-16)**
  - Tích hợp HTML5 Camera QR scanner và hiển thị thông tin vé vừa quét.
- **Bước 6: Chạy kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| SCN-01 | Quét vé hợp lệ | QR hợp lệ, vé ISSUED, trong khung giờ | 200 OK, status=CHECKED_IN_SUCCESS, DB ghi nhận check-in |
| SCN-02 | Chữ ký QR giả mạo | Sửa 1 ký tự trong signature | 400 Bad Request, status=INVALID_SIGNATURE |
| SCN-03 | Quét lại vé đã check-in trước đó | Vé đã CHECKED_IN lúc 18:00 | 409 Conflict, status=ALREADY_CHECKED_IN kèm thời gian đã dùng |
| SCN-04 | Quét vé thuộc sự kiện khác | QR của sự kiện 102 nhưng quét tại sự kiện 201 | 400 Bad Request, status=WRONG_EVENT |
| SCN-05 | Quét 2 lần đồng thời (Concurrency) | 2 máy quét cùng bắn 1 mã tại cùng 1 thời điểm | Đúng 1 máy nhận thành công, máy kia nhận ALREADY_CHECKED_IN |
