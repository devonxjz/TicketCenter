> **Convention:** Checkout từ `dev`, tạo nhánh `feat/thai/thai-06-profile-layout`, commit theo Conventional Commits và mở PR về `dev`.
> **Merge gate:** Chỉ merge khi toàn bộ GitHub Actions xanh, test của task có bằng chứng và PR đã được người khác review.

## 1. Mục tiêu và Nghiệp vụ

Hoàn thiện trang thông tin cá nhân và thiết lập bộ khung giao diện dùng chung (Common Layout & Design Framework) cho toàn bộ hệ thống:
- Phục vụ trực tiếp màn hình **UI-24 (User Profile & Account Verification)**.
- Cung cấp thành phần Layout chuẩn mực: Header điều hướng thông minh theo vai trò (`PlatformRole` và `OrganizationRole`), Footer, Flash Message container, và thẻ meta CSRF tự động.
- Giúp các thành viên khác (Đông, Liêm, Khánh, Vương) chỉ cần kế thừa khung giao diện (`<jsp:include page="/WEB-INF/views/layout/header.jsp" />`) mà không cần tự cấu hình lại CSS, JS hay thanh điều hướng.
- Tuyệt đối phòng ngừa lỗi kinh điển `LazyInitializationException` bằng cách chỉ truyền DTO thuần (Plain Java Objects) sang JSP, không truyền Hibernate Entity.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/me/profile`
- **Mục đích:** Lấy thông tin cá nhân đầy đủ của người dùng đang đăng nhập.
- **Quyền hạn:** `AUTHENTICATED`.
- **Response Body (200 OK):**
```json
{
  "userId": 1024,
  "userName": "buyer_nguyen",
  "fullName": "Nguyễn Văn A",
  "email": "buyer.nguyen@example.com",
  "isEmailVerified": true,
  "emailVerifiedAt": "2026-10-09T01:15:00Z",
  "platformRole": "CUSTOMER",
  "status": "ACTIVE",
  "memberships": [
    {
      "organizationId": 45,
      "organizationName": "Công ty Âm nhạc Trẻ Việt Nam",
      "role": "MANAGER"
    }
  ]
}
```
- **Lưu ý bảo mật:** Tuyệt đối không trả về `passwordHash`, `authVersion`, `secretKey` hoặc các trường nhạy cảm trong JSON response.

### 2.2. GET `/profile` (HTML View)
- Trả về giao diện JSP của màn hình UI-24.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Thiết kế Bộ khung Layout Dùng chung (Modular Layout Architecture)
- Tổ chức các partial files trong thư mục `WEB-INF/views/layout/`:
  - `header.jsp`: Chứa thẻ `<head>`, link `common.css`, meta tags CSRF (`_csrf_token`, `_csrf_header`).
  - `navbar.jsp`: Thanh menu hiển thị động dựa trên thông tin session:
    - Nếu là `CUSTOMER`: Xem Vé của tôi, Yêu cầu tạo tổ chức, Hồ sơ.
    - Nếu là `MANAGER` của tổ chức: Thêm menu Quản lý sự kiện, Báo cáo doanh thu, Thành viên tổ chức.
    - Nếu là `CHECK_IN_STAFF`: Menu Quét vé sự kiện.
    - Nếu là `ADMIN`: Menu Duyệt sự kiện, Duyệt tổ chức, Quyết định hoàn tiền, Đối soát tài chính, Audit log.
  - `flash-messages.jsp`: Khu vực hiển thị thông báo alert tự động biến mất sau 5s (Success, Warning, Error).
  - `footer.jsp`: Thông tin bản quyền TicketsCenter và script nạp `common.js`.

### 3.2. Chống XSS & Phòng vệ Render Dữ liệu (XSS Prevention & Strict Escaping)
- Trên JSP: Sử dụng thẻ chuẩn JSTL Core `<c:out value="${...}" />` hoặc hàm thoát ký tự HTML để hiển thị dữ liệu do người dùng nhập (như `fullName`, `userName`, `description`).
- Tuyệt đối không sử dụng Java Scriptlet (`<% ... %>`) hay in chuỗi trực tiếp (`<%= ... %>`).

### 3.3. Bảo vệ Vòng đời Persistence & Lazy Graph
- Sau khi `EntityManager` hoặc JDBC connection đóng tại tầng Service, toàn bộ dữ liệu phải được chuyển đổi triệt để sang `UserProfileDto`.
- Không bao giờ truyền Entity JPA với các quan hệ `@OneToMany`, `@ManyToOne(fetch = LAZY)` vào `HttpServletRequest.setAttribute(...)`. Nếu JSP vô tình truy cập thuộc tính lazy sẽ gây crash trang với ngoại lệ `LazyInitializationException`.

### 3.4. Common Assets (`common.css` & `common.js`)
- `common.css`: Thiết lập biến CSS gốc (`--tc-primary: #4f46e5`, `--tc-bg: #f9fafb`), typography, bảng dữ liệu responsive, modal và badge trạng thái thống nhất toàn hệ thống.
- `common.js`:
  - Tự động gắn header `X-CSRF-Token` vào tất cả các yêu cầu `fetch()` hoặc `XMLHttpRequest` có phương thức thay đổi dữ liệu (POST/PUT/DELETE).
  - Cung cấp hàm tiện ích thông báo `window.TC.showToast(message, type)`.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `UserProfileDto`, `ProfileMembershipItemDto`.
   - Services: `ProfileService` (tập hợp thông tin user, trạng thái verify email, danh sách membership).
   - Servlets: `ProfileServlet` (xử lý cả GET API JSON và GET View HTML).
2. **Giao diện JSP & Static Assets:**
   - `src/main/webapp/WEB-INF/views/layout/header.jsp`
   - `src/main/webapp/WEB-INF/views/layout/navbar.jsp`
   - `src/main/webapp/WEB-INF/views/layout/footer.jsp`
   - `src/main/webapp/WEB-INF/views/layout/flash-messages.jsp`
   - `src/main/webapp/WEB-INF/views/profile/view.jsp`: Màn hình UI-24 hiển thị thông tin cá nhân, huy hiệu trạng thái xác minh, danh sách tổ chức tham gia, nút đổi mật khẩu.
   - `src/main/webapp/assets/css/common.css`
   - `src/main/webapp/assets/js/common.js`
3. **Kiểm thử tự động:**
   - `ProfileServiceTest`: Kiểm tra chuyển đổi DTO an toàn, lọc bỏ trường nhạy cảm.
   - `ProfileServletTest`: Kiểm tra trả về JSON khi có header `Accept: application/json` và render JSP khi truy cập trình duyệt thông thường.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Tạo `UserProfileDto`**
  - Khai báo các thuộc tính hiển thị cần thiết, không chứa bất kỳ trường bảo mật nào.
- **Bước 2: Xây dựng `ProfileService`**
  - Đọc user theo `actorId` từ `UserRepository`.
  - Đọc danh sách membership hoạt động từ `OrganizationMembershipRepository`.
  - Ghép dữ liệu và trả về `UserProfileDto`.
- **Bước 3: Xây dựng Bộ khung JSP Layout**
  - Viết `navbar.jsp` sử dụng `<c:if test="${not empty sessionScope.TC_USER_PRINCIPAL}">` để render menu phù hợp.
- **Bước 4: Xây dựng `common.css` và `common.js`**
  - Tối ưu hóa UI/UX, hỗ trợ responsive trên màn hình điện thoại di động và máy tính bảng.
- **Bước 5: Xây dựng `ProfileServlet` và `view.jsp`**
  - Kết nối dữ liệu vào request attributes và forward đến `view.jsp`.
- **Bước 6: Chạy kiểm thử tự động**
  - Kiểm tra `mvn -B test` bảo đảm toàn bộ hệ thống hoạt động ổn định.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| PROF-01 | Xem hồ sơ khi đã đăng nhập | Session hợp lệ | 200 OK, trả về đầy đủ fullName, email, memberships; không lộ password hash |
| PROF-02 | Chưa đăng nhập truy cập profile | Không có session | Chuyển hướng về `/auth/login` (nếu là HTML) hoặc 401 Unauthorized (nếu là API) |
| PROF-03 | Phòng chống Lazy Exception | Entity có lazy relationships | JSP render hoàn tất mà không phát sinh LazyInitializationException |
| PROF-04 | Phòng chống XSS | Tên người dùng chứa `<script>alert(1)</script>` | Dữ liệu được escape thành `&lt;script&gt;` an toàn |
