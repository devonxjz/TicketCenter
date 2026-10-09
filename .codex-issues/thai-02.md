> **Convention:** Checkout từ `dev`, tạo nhánh `feat/thai/thai-02-otp-password-reset`, commit theo Conventional Commits và mở PR về `dev`.
> **Merge gate:** Chỉ merge khi toàn bộ GitHub Actions xanh, test của task có bằng chứng và PR đã được người khác review.

## 1. Mục tiêu và Nghiệp vụ

Hoàn thiện toàn diện quy trình xác minh địa chỉ email người dùng qua mã OTP và cơ chế quên/đặt lại mật khẩu tự phục vụ:
- Phục vụ các màn hình **UI-03 (Forgot/Reset Password)** và **UI-24 (Email Verification Badge)**.
- Đảm bảo an toàn mật mã học cao nhất: Tuyệt đối không lưu bản rõ OTP, sử dụng HMAC-SHA256 băm kèm Secret Key của hệ thống.
- Ngăn chặn triệt để tấn công Enumeration (dò tìm tài khoản) và Brute-force OTP (giới hạn 5 lần nhập sai, cooldown 60s, TTL 5 phút).
- Tích hợp gửi mail thuần bằng Socket / SSLSocket trong Java Core (JDK 17+) không phụ thuộc thêm thư viện ngoài bên thứ ba.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. POST `/api/auth/otp/send`
- **Mục đích:** Gửi mã OTP xác minh email hoặc yêu cầu cấp OTP theo mục đích.
- **Request Body:**
```json
{
  "email": "buyer.nguyen@example.com",
  "purpose": "VERIFY_EMAIL"
}
```
- **Hợp lệ của `purpose`:** `VERIFY_EMAIL` hoặc `RESET_PASSWORD`.
- **Response Body (200 OK):**
```json
{
  "message": "Nếu email tồn tại trong hệ thống, mã xác nhận đã được gửi đi.",
  "cooldownSeconds": 60
}
```
- **Lưu ý chống enumeration:** Luôn trả 200 OK ngay cả khi email không tồn tại trong DB, tránh để kẻ tấn công kiểm tra email nào đã đăng ký.

### 2.2. POST `/api/auth/otp/verify`
- **Mục đích:** Kiểm tra mã OTP do người dùng nhập vào.
- **Request Body:**
```json
{
  "email": "buyer.nguyen@example.com",
  "otp": "839201",
  "purpose": "VERIFY_EMAIL"
}
```
- **Response Body khi `purpose == VERIFY_EMAIL` (200 OK):**
```json
{
  "success": true,
  "message": "Địa chỉ email đã được xác minh thành công.",
  "isEmailVerified": true
}
```
- **Response Body khi `purpose == RESET_PASSWORD` (200 OK):**
```json
{
  "success": true,
  "message": "Mã xác nhận hợp lệ.",
  "resetToken": "rst_a8f9c0e2b1d34c56e7f8091a2b3c4d5e"
}
```
- **Mã lỗi:**
  - `400 Bad Request`: Mã OTP sai, hết hạn, hoặc bị vô hiệu hóa do nhập sai quá 5 lần.

### 2.3. POST `/api/auth/password/forgot`
- **Mục đích:** Người dùng yêu cầu link / mã đặt lại mật khẩu khi bị quên.
- **Request Body:**
```json
{
  "email": "buyer.nguyen@example.com"
}
```
- **Response Body (200 OK):**
```json
{
  "message": "Nếu email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi đến hộp thư của bạn."
}
```

### 2.4. POST `/api/auth/password/reset`
- **Mục đích:** Đặt mật khẩu mới bằng `resetToken` một lần vừa được phát hành từ bước verify OTP.
- **Request Body:**
```json
{
  "email": "buyer.nguyen@example.com",
  "resetToken": "rst_a8f9c0e2b1d34c56e7f8091a2b3c4d5e",
  "newPassword": "NewSecurePassword123!"
}
```
- **Response Body (200 OK):**
```json
{
  "success": true,
  "message": "Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại với mật khẩu mới."
}
```

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Mô hình Dữ liệu OTP & Băm Mật mã học
- Tương tác với bảng `dbo.tc_otps` qua `OtpRepository`.
- Các trường quan trọng:
  - `secret_hash`: Chuỗi băm HMAC-SHA256 của `OTP + purpose + email` sử dụng hệ thống secret (`TC_OTP_HMAC_SECRET`).
  - `purpose`: Phân tách rạch ròi giữa `VERIFY_EMAIL` và `RESET_PASSWORD`. Mã tạo cho verify email không thể đem đi đổi mật khẩu.
  - `attempt_count`: Đếm số lần thử thất bại, tự động tăng trong DB.
  - `max_attempts`: Giá trị cố định = 5. Nếu `attempt_count >= max_attempts`, đánh dấu `invalidated_at = SYSDATETIME()`.
  - `consumed_at`: Thời điểm mã được sử dụng thành công (đơn năng 1 lần duy nhất).
  - `expires_at`: Mặc định 5 phút kể từ thời điểm tạo.
- Sử dụng hàm so sánh an toàn `MessageDigest.isEqual()` để ngăn chặn tấn công Timing Attack.

### 3.2. Chống nghẽn kết nối DB trong quá trình gửi Email (I/O Out-of-Transaction)
- **Quy tắc bất biến:** Không bao giờ thực hiện cuộc gọi mạng Socket SMTP trong phạm vi một Database Transaction!
- **Quy trình chuẩn:**
  1. Transaction 1: Lưu OTP entity vào bảng `dbo.tc_otps` (trạng thái chờ), commit transaction để giải phóng DB connection về pool.
  2. I/O Network: Gọi `EmailSender.send(email, subject, body)` qua socket ngoài transaction.
  3. Nếu việc gửi mail bị lỗi kết nối (Timeout, Auth fail), ghi log lỗi nhưng không ảnh hưởng toàn vẹn DB.

### 3.3. Stateless Reset Token với Ràng buộc `authVersion`
- Sau khi xác thực OTP thành công cho `purpose = RESET_PASSWORD`, sinh ra `resetToken`:
  - Token có thể là chuỗi ký HMAC mã hóa chứa: `userId + authVersion + timestamp + expiration`.
  - Khi thực hiện `POST /api/auth/password/reset`: Kiểm tra tính toàn vẹn của token, và quan trọng nhất: so sánh `token.authVersion == user.authVersion`. Nếu user đã từng đổi mật khẩu hoặc reset trước đó, token cũ lập tức vô giá trị!
- Sau khi reset mật khẩu thành công:
  - Đổi `user.passwordHash = BCrypt.hash(newPassword)`.
  - Tăng `user.authVersion = user.authVersion + 1`.
  - Cả hai thao tác nằm trong cùng 1 Transaction nguyên tử (Atomic Transaction) để lập tức làm vô hiệu toàn bộ session và token cũ trên toàn bộ các thiết bị.

### 3.4. Trình gửi Mail Socket Thuần (`SmtpEmailSender`)
- Xây dựng lớp client SMTP qua `java.net.Socket` và `javax.net.ssl.SSLSocketFactory`.
- Hỗ trợ STARTTLS trên cổng 587 hoặc SSL/TLS trực tiếp trên cổng 465.
- Thiết lập socket timeout nghiêm ngặt (`connectTimeout = 5000ms`, `readTimeout = 5000ms`) để tránh treo luồng HTTP của Tomcat.
- Trong môi trường Unit Test / CI: Tự động nạp `FakeEmailSender` để lưu email vào bộ nhớ in-memory kiểm tra nội dung mà không gửi ra Internet.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `OtpSendRequestDto`, `OtpVerifyRequestDto`, `OtpVerifyResponseDto`, `ForgotPasswordRequestDto`, `PasswordResetRequestDto`.
   - Integration Mail: `EmailSender` (interface), `EmailMessage`, `EmailDeliveryException`, `SmtpEmailSender` (socket client thuần), `FakeEmailSender` (dành cho test).
   - Repositories: `OtpRepository` (lưu OTP, đếm failed attempts nguyên tử, tìm OTP khả dụng), cập nhật `UserRepository` (phương thức update mật khẩu kèm tăng authVersion, xác minh email).
   - Crypto Helpers: `OtpCryptoHelper` (HMAC-SHA256, sinh mã 6 số CSPRNG `SecureRandom`, token signing & verify).
   - Services: `OtpService`, `PasswordResetService`.
   - Servlets: `OtpSendServlet`, `OtpVerifyServlet`, `ForgotPasswordServlet`, `PasswordResetServlet`.
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/auth/verify-email.jsp`: Giao diện nhập mã OTP 6 ô hoặc 1 ô số, đếm ngược cooldown 60s, nút gửi lại.
   - `src/main/webapp/WEB-INF/views/auth/forgot-password.jsp`: Nhập email để nhận OTP / hướng dẫn đặt lại mật khẩu.
   - `src/main/webapp/WEB-INF/views/auth/reset-password.jsp`: Nhập mật khẩu mới kèm thanh đo độ mạnh mật khẩu và confirm password.
3. **Database Changes:**
   - Bảng `dbo.tc_otps` ghi nhận các lượt cấp mã OTP, số lần nhập sai, thời gian tiêu thụ.
   - Bảng `dbo.tc_users` cập nhật `is_email_verified = 1`, `email_verified_at = SYSDATETIME()` khi verify email; cập nhật `password_hash` và tăng `auth_version` khi reset mật khẩu.
4. **Kiểm thử tự động:**
   - `OtpCryptoHelperTest`, `OtpRepositoryTest`, `OtpServiceTest`, `PasswordResetServiceTest`, `EmailSenderTest`, `AuthOtpServletsTest`.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Triển khai `OtpCryptoHelper`**
  - Dùng `java.security.SecureRandom` sinh 6 chữ số ngẫu nhiên: `String.format("%06d", secureRandom.nextInt(1000000))`.
  - Triển khai phương thức băm HMAC-SHA256 và so sánh hằng số thời gian bằng `MessageDigest.isEqual`.
- **Bước 2: Xây dựng `SmtpEmailSender`**
  - Giao tiếp dòng lệnh SMTP chuẩn RFC 5321 (`EHLO`, `AUTH LOGIN`, `MAIL FROM`, `RCPT TO`, `DATA`, `QUIT`).
  - Thiết lập timeout 5 giây.
- **Bước 3: Xây dựng `OtpRepository`**
  - Triển khai câu truy vấn cập nhật thất bại nguyên tử:
    ```sql
    UPDATE dbo.tc_otps
    SET attempt_count = attempt_count + 1,
        invalidated_at = CASE WHEN attempt_count + 1 >= 5 THEN SYSDATETIME() ELSE invalidated_at END
    WHERE id = ? AND consumed_at IS NULL AND invalidated_at IS NULL;
    ```
- **Bước 4: Triển khai `OtpService`**
  - `sendOtp(email, purpose)`: Kiểm tra cooldown 60s. Vô hiệu hóa các OTP còn hiệu lực cũ của cùng email + purpose. Sinh mã OTP, băm HMAC, lưu vào DB, sau đó ra ngoài transaction gọi email sender.
  - `verifyOtp(email, otpRaw, purpose)`: Tra cứu bản ghi OTP khả dụng. So sánh hash. Nếu sai: tăng attempt count. Nếu đúng: cập nhật `consumed_at = SYSDATETIME()`.
- **Bước 5: Triển khai `PasswordResetService`**
  - Verify OTP cho mục đích RESET_PASSWORD, cấp phát `resetToken`.
  - `resetPassword(email, resetToken, newPassword)`: Kiểm tra token, kiểm tra authVersion hiện tại của user, băm password mới, cập nhật user, tăng `authVersion`.
- **Bước 6: Xây dựng các Servlets và Views JSP**
  - Viết servlets, kết nối view JSP, xử lý hiển thị lỗi trực quan và logic đếm lùi JavaScript phía client.
- **Bước 7: Viết kiểm thử toàn diện**
  - Chạy `mvn -B test` để bảo đảm 100% ca test xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Đầu vào | Kết quả mong đợi |
|---|---|---|---|
| OTP-01 | Gửi OTP thành công | email="buyer@test.com", purpose=VERIFY_EMAIL | Mã lưu trong DB dưới dạng HMAC hash, email gửi qua sender, trả về 200 OK |
| OTP-02 | Gửi lại trước 60 giây | Gửi lại sau 30 giây | 429 Too Many Requests hoặc 400 Bad Request kèm thông báo cooldown |
| OTP-03 | Nhập sai OTP 5 lần | Nhập mã sai liên tiếp 5 lần | Mã OTP chuyển sang `invalidated_at != NULL`, lần nhập thứ 6 bị chặn hoàn toàn |
| OTP-04 | Dùng sai purpose | Dùng mã của VERIFY_EMAIL cho RESET_PASSWORD | 400 Bad Request, báo lỗi mục đích mã không khớp |
| OTP-05 | Reset mật khẩu thành công | resetToken hợp lệ, mật khẩu mới mạnh | Mật khẩu mới được cập nhật, `authVersion` tăng 1, session cũ bị văng ra |
| OTP-06 | Reset mật khẩu với token cũ | resetToken đã dùng 1 lần trước đó | 400 Bad Request, từ chối thực hiện |
