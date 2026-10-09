> **Convention:** `dev` → `feat/thai/thai-01-auth-session-csrf` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Chỉ sửa `identity/controller/**`, `identity/service/**`, `identity/repository/**`, `identity/dto/**`, `identity/security/**`, JSP views trong `WEB-INF/views/auth/**` và test tương ứng. Cấm sửa model, config chung, database, `openapi.json`, feature domain khác.

## 1. Mục tiêu và Nghiệp vụ

Hoàn thiện hạ tầng xác thực hạt nhân cho toàn bộ hệ thống TicketsCenter:
- Phục vụ màn hình **UI-03 (Login/Register)**.
- Quản lý phiên làm việc người dùng (HTTP Session) an toàn, chống Session Fixation và Session Hijacking.
- Cung cấp cơ chế chống giả mạo request liên trang (CSRF Protection) qua Double Submit Cookie / Anti-CSRF Token.
- Định hình hợp đồng người dùng hiện tại (`CurrentUserDto`, `UserPrincipal`, `SecurityContext`) làm tiêu chuẩn dùng chung cho tất cả các nhóm (Đông, Liêm, Khánh, Vương) trích xuất actor context mà không tự ý truy cập session lung tung.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/auth/csrf`
- **Mục đích:** Cung cấp CSRF token cho client trước khi thực hiện các HTTP mutation (POST/PUT/DELETE).
- **Quyền hạn:** Public.
- **Response Headers:** `Set-Cookie: TC-CSRF-TOKEN=<token>; Path=/; SameSite=Lax; HttpOnly=false` (cho phép script đọc để gắn vào Header) hoặc lưu trong session.
- **Response Body (200 OK):**
```json
{
  "token": "4a7b3c2d-9e1f-4a8b-b1c2-d3e4f5a6b7c8",
  "headerName": "X-CSRF-Token"
}
```

### 2.2. POST `/api/auth/register`
- **Mục đích:** Đăng ký tài khoản khách hàng mới (`PlatformRole.CUSTOMER`).
- **Headers:** `X-CSRF-Token: <token>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "userName": "buyer_nguyen",
  "fullName": "Nguyễn Văn A",
  "email": "buyer.nguyen@example.com",
  "password": "Password123!"
}
```
- **Validation Rules:**
  - `userName`: Không rỗng, 3–50 ký tự, regex `^[a-zA-Z0-9._-]+$`.
  - `fullName`: Không rỗng, tối đa 100 ký tự.
  - `email`: Định dạng email RFC 5322 hợp lệ, chuẩn hóa lowercase và trim trước khi kiểm tra.
  - `password`: Tối thiểu 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số.
- **Response Body (201 Created):**
```json
{
  "userId": 1024,
  "userName": "buyer_nguyen",
  "fullName": "Nguyễn Văn A",
  "email": "buyer.nguyen@example.com",
  "platformRole": "CUSTOMER",
  "status": "ACTIVE",
  "isEmailVerified": false
}
```
- **Lỗi:**
  - `400 Bad Request`: Payload không hợp lệ hoặc thiếu trường bắt buộc.
  - `409 Conflict`: `userName` hoặc `email` đã tồn tại trong hệ thống.

### 2.3. POST `/api/auth/login`
- **Mục đích:** Xác thực người dùng, thiết lập Session mới.
- **Request Body:**
```json
{
  "email": "buyer.nguyen@example.com",
  "password": "Password123!"
}
```
- **Response Body (200 OK):**
```json
{
  "userId": 1024,
  "userName": "buyer_nguyen",
  "fullName": "Nguyễn Văn A",
  "email": "buyer.nguyen@example.com",
  "platformRole": "CUSTOMER",
  "authVersion": 1
}
```
- **Response Headers:** `Set-Cookie: JSESSIONID=...; Path=/ticketscenter; HttpOnly; SameSite=Lax`.

### 2.4. POST `/api/auth/logout`
- **Mục đích:** Hủy bỏ session hiện tại.
- **Headers:** `X-CSRF-Token: <token>`.
- **Response Body (200 OK):**
```json
{
  "message": "Đăng xuất thành công."
}
```

### 2.5. GET `/api/me`
- **Mục đích:** Trả về thông tin người dùng đang đăng nhập trong session.
- **Response Body (200 OK):** Trả về `CurrentUserDto`.
- **Lỗi 401 Unauthorized:** Khi chưa đăng nhập hoặc session đã hết hạn / bị thu hồi `authVersion`.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Persistence & Cơ chế Database
- Tương tác với bảng `dbo.tc_users` thông qua `UserRepository`.
- Sử dụng BCrypt hashing với Cost Factor tối thiểu là 12 để băm mật khẩu (`passwordHash`). Không bao giờ lưu trữ plain password.
- Khi người dùng đăng ký:
  - Khởi tạo mặc định: `platform_role = 'CUSTOMER'`, `status = 'ACTIVE'`, `auth_version = 1`, `is_email_verified = 0`.
- Đối soát tính hợp lệ của tài khoản:
  - Đăng nhập chỉ cho phép khi `status = 'ACTIVE'`.
  - Nếu tài khoản có trạng thái `SUSPENDED` hoặc `INACTIVE`, trả về lỗi từ chối và ghi log cảnh báo.

### 3.2. Chống Session Fixation & Quản lý Session
- Khi `POST /api/auth/login` thành công:
  - Gọi `request.getSession(false)` để lấy session cũ (nếu có).
  - Sao chép các thuộc tính vô hại (như locale, flash message) sang session mới.
  - Gọi `session.invalidate()` để tiêu hủy Session ID cũ.
  - Gọi `request.getSession(true)` để Servlet container sinh Session ID mới ngẫu nhiên, an toàn.
  - Gắn `UserPrincipal` (chứa `userId`, `userName`, `platformRole`, `authVersion`) vào session mới.
- Cấu hình cookie session: `HttpOnly = true`, `Secure = true` (ở môi trường HTTPS), `SameSite = Lax`.

### 3.3. Cơ chế kiểm soát phiên theo `authVersion` (Stale Session Invalidation)
- Để đảm bảo khi người dùng đổi mật khẩu hoặc bị thu hồi quyền, các session đang đăng nhập ở các trình duyệt khác lập tức vô hiệu:
  - Trường `auth_version` (BIGINT/INT) trong bảng `dbo.tc_users` được tăng thêm 1 mỗi khi đổi mật khẩu / reset mật khẩu.
  - `SessionValidationFilter`: Chặn mỗi HTTP request đi vào hệ thống (đã có session). Filter đọc `authVersion` từ session và so sánh với `authVersion` hiện tại từ database hoặc local in-memory cache/Redis. Nếu `session.authVersion < db.authVersion`, tự động hủy session và trả về `401 Unauthorized`.

### 3.4. Bộ lọc CSRF (`CsrfFilter`)
- Áp dụng kỹ thuật Đồng bộ Token (Synchronizer Token Pattern).
- Bỏ qua các HTTP Safe Methods (`GET`, `HEAD`, `OPTIONS`, `TRACE`).
- Kiểm tra các mutation methods (`POST`, `PUT`, `PATCH`, `DELETE`):
  - Ngoại lệ an toàn (Exempt list): `/api/auth/login`, `/api/auth/register`, `/api/auth/password/forgot`, `/api/auth/otp/send`, `/api/auth/otp/verify` (các luồng pre-auth khi chưa có session).
  - Với các endpoint còn lại: Token gửi lên qua header `X-CSRF-Token` hoặc form field `_csrf` phải khớp tuyệt đối (sử dụng so sánh an toàn `MessageDigest.isEqual`) với token lưu trữ trong Session. Nếu sai hoặc thiếu, trả về `403 Forbidden` (`{"error": "INVALID_CSRF_TOKEN"}`).

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `RegisterRequestDto`, `LoginRequestDto`, `CurrentUserDto`.
   - Security Context & Principal: `UserPrincipal`, `SecurityContext`.
   - Filters & Listeners: `CsrfFilter`, `SessionValidationFilter`, `AuthSecurityListener`.
   - Repositories: `UserRepository` (tìm kiếm bằng email chuẩn hóa, lưu user, cập nhật authVersion).
   - Services: `AuthService` (đăng ký, xác thực, logout, tra cứu me).
   - Servlets: `RegisterServlet`, `LoginServlet`, `LogoutServlet`, `MeServlet`, `CsrfServlet`.
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/auth/login.jsp`: Form đăng nhập, link quên mật khẩu/đăng ký, hiển thị flash error.
   - `src/main/webapp/WEB-INF/views/auth/register.jsp`: Form đăng ký đầy đủ validation client-side và hiển thị lỗi server-side.
3. **Database Changes:**
   - Bảng `dbo.tc_users` được insert bản ghi mới với `password_hash` băm bằng BCrypt.
4. **Kiểm thử tự động:**
   - `AuthServiceTest`: Test unit kiểm tra đăng ký trùng lặp, sai mật khẩu, tài khoản suspended.
   - `IdentityWebFlowTest`: Test servlet integration bao phủ toàn bộ luồng CSRF, Login -> Đổi Session ID -> Me -> Logout.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs & Validation Helpers**
  - Tạo `RegisterRequestDto`, `LoginRequestDto`, `CurrentUserDto`.
  - Triển khai regex kiểm tra định dạng email và độ mạnh của password trong validator.
- **Bước 2: Xây dựng `UserRepository`**
  - Cung cấp phương thức `findByEmailNormalized(String email)`.
  - Cung cấp phương thức `findByUserName(String userName)`.
  - Cung cấp phương thức `save(User user)`.
- **Bước 3: Xây dựng `AuthService`**
  - `register(...)`: Chuẩn hóa email, check trùng lặp, hash password qua BCrypt, lưu User, trả về `CurrentUserDto`.
  - `login(...)`: Tìm user theo email chuẩn hóa. Nếu không thấy hoặc password không khớp (BCrypt.checkpw), ném `BadCredentialsException`. Kiểm tra trạng thái `ACTIVE`.
- **Bước 4: Cài đặt `SecurityContext` & `UserPrincipal`**
  - Lưu trữ `UserPrincipal` vào `HttpSession` dưới key hằng số `TC_USER_PRINCIPAL`.
  - Xây dựng tiện ích tĩnh `SecurityContext.getCurrentUser(request)` để các servlet khác dễ dàng lấy thông tin mà không sợ NullPointerException.
- **Bước 5: Cài đặt `CsrfFilter` & `SessionValidationFilter`**
  - Đăng ký filter trong web container (qua `@WebFilter` hoặc cấu hình servlet).
  - Cung cấp endpoint `/api/auth/csrf` để web app SPA hoặc AJAX client gọi lấy token ban đầu.
- **Bước 6: Xây dựng Servlets**
  - `LoginServlet`: POST xác thực, tái sinh session id qua `request.changeSessionId()` hoặc `session.invalidate() + getSession(true)`.
  - `LogoutServlet`: Vô hiệu hóa session, xóa cookie nếu cần.
  - `MeServlet`: Đọc `SecurityContext`, trả về JSON thông tin người dùng.
- **Bước 7: Xây dựng Giao diện JSP**
  - Tạo trang login và register với form HTML semantic, hiển thị thông báo lỗi rõ ràng, nhúng hidden input CSRF token.
- **Bước 8: Viết Unit Test & Integration Test**
  - Viết test với Mockito / MockHttpSession / MockHttpServletRequest.
  - Đảm bảo 100% test pass qua lệnh `mvn -B test`.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Kịch bản kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| TC-01 | Đăng ký tài khoản hợp lệ | userName="user1", email="user1@test.com", pass="Pass123!" | 201 Created, DB lưu password đã hash, email chưa verify |
| TC-02 | Đăng ký trùng email | email đã có trong DB | 409 Conflict với mã lỗi DUPLICATE_EMAIL |
| TC-03 | Đăng nhập sai mật khẩu | email đúng, pass sai | 401 Unauthorized, thông báo chung tránh dò mật khẩu |
| TC-04 | Đăng nhập tài khoản bị khóa | user có status='SUSPENDED' | 403 Forbidden, không cấp session |
| TC-05 | Chống Session Fixation | Gọi login với JSESSIONID cũ | Session ID sau login khác hoàn toàn Session ID trước login |
| TC-06 | Thiếu CSRF Token | POST `/api/auth/logout` không có header `X-CSRF-Token` | 403 Forbidden |
| TC-07 | Stale Session Invalidation | User đổi mật khẩu làm tăng `authVersion` | Request tiếp theo với session cũ nhận 401 Unauthorized |
