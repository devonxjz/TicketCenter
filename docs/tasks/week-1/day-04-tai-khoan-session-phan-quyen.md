# Ngày 04 — Đăng ký, đăng nhập, phiên và bảo vệ HTTP

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ hai, 2026-09-28. **Ước lượng tạm của kế hoạch ban đầu:** 15 giờ công tổng của nhóm.

**Mục tiêu:** API tài khoản và session chạy được, seed admin an toàn, CSRF và quyền mặc định được bảo vệ.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §4, §6.1, §9.4, §11.3. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D03-T01…T04.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D04-T01 — Đăng ký và lưu mật khẩu

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/service/identity/AccountService.java; JAVA/repository/identity/UserRepository.java; JAVA/controller/identity/AuthServlet.java; TEST/identity/AccountTest.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST /auth/register {userName,fullName,email,password}; User ACTIVE nhưng emailVerifiedAt chưa có; phản hồi hạn chế dò tài khoản.

**Phụ thuộc của task:** D03-T01/T02/T03; schema User và principal auth.

**Cách thực hiện:**

- [ ] 1. Viết test email và userName chuẩn hóa duy nhất/NOT NULL theo C01; khóa quy ước chuẩn hóa từng trường, đăng ký đồng thời không tạo hai account.
- [ ] 2. Chọn thuật toán hash/password qua thư viện đã duyệt hoặc phương án chuẩn đã kiểm chứng; salt ngẫu nhiên riêng, so sánh an toàn, lưu tham số để nâng cấp sau.
- [ ] 3. Validate userName/fullName/email/password/độ dài trên server; mật khẩu không trim hoặc đổi chữ hoa; không log request body xác thực.
- [ ] 4. Tạo User và xử lý duplicate constraint đồng thời bằng kết quả nhất quán; registration không tự cấp ADMIN hay membership.
- [ ] 5. Kết nối đường yêu cầu gửi OTP ngày 5; trước khi có OTP vẫn đảm bảo user chưa xác minh không được giữ vé.

**Kiểm chứng bắt buộc:**

- [ ] Đăng ký đồng thời cùng email hoặc userName chỉ một User; response có userName, không lộ auth secret.
- [ ] DB không chứa mật khẩu rõ; response không chứa hash/authVersion nội bộ.

**Điều kiện hoàn thành:** Có tài khoản chưa xác minh với hash an toàn. Ghi case và kết quả trong `docs/evidence/day-04.md`.

## D04-T02 — Login/logout và vô hiệu phiên

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/service/identity/SessionService.java; JAVA/filter/AuthenticationFilter.java; TEST/identity/SessionIT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST /auth/login; POST /auth/logout; GET /me → user hiện tại và quyền được phép hiển thị.

**Phụ thuộc của task:** D04-T01 cho hash/account lookup; có thể viết session test trước.

**Cách thực hiện:**

- [ ] 1. Login thường tra email chuẩn hóa theo UI-03; mọi User có userName duy nhất. Seed có userName admin thật; cách đăng nhập demo admin/admin được tài liệu hóa riêng, không dùng alias để bỏ trường userName.
- [ ] 2. Login đúng tạo lại session ID, lưu userId/authVersion; không copy dữ liệu session cũ ngoài allowlist.
- [ ] 3. Filter kiểm tra User ACTIVE và authVersion hiện tại; user disabled hoặc version thay đổi làm phiên cũ mất hiệu lực.
- [ ] 4. Logout invalidates session; hỗ trợ nhiều phiên độc lập, không vô hiệu thiết bị khác chỉ vì một lần login.
- [ ] 5. Cookie HttpOnly, Secure khi HTTPS, SameSite phù hợp return VNPAY; kiểm tra cả môi trường local và proxy online.

**Kiểm chứng bắt buộc:**

- [ ] Sai password, disabled, email không tồn tại có response không tiết lộ chi tiết.
- [ ] Session fixation bị chặn; hai thiết bị login được; logout một phiên không phá phiên kia.

**Điều kiện hoàn thành:** Session server-side dùng cho tất cả endpoint sau này. Ghi case và kết quả trong `docs/evidence/day-04.md`.

## D04-T03 — CSRF, giới hạn payload và phân quyền mặc định

**Phụ trách đề xuất:** C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/filter/CsrfFilter.java; JAVA/filter/RequestValidationFilter.java; JAVA/service/identity/AuthorizationService.java; TEST/security/HttpSecurityIT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** GET /auth/csrf; X-CSRF-Token cho POST; authorization nhận actor từ session và đối tượng trong DB.

**Phụ thuộc của task:** D03-T04 và session contract D04-T02; tích hợp sau login.

**Cách thực hiện:**

- [ ] 1. Sinh CSRF token ngẫu nhiên gắn session, kiểm tra ở mọi user mutation, kể cả login/logout; không miễn trừ cả thư mục payment.
- [ ] 2. Giới hạn request size/content type; không phản chiếu raw input vào lỗi; đặt cache no-store cho phản hồi chứa dữ liệu nhạy cảm.
- [ ] 3. Viết guard owner/member/admin dùng lại; member check lấy trạng thái hiện tại chứ không chỉ role lưu trong session.
- [ ] 4. Áp dụng deny mặc định cho route cần auth; định nghĩa 403 cho thiếu role và 404 cho ID ngoài scope.
- [ ] 5. Tạo test user giả actorId/role/organizationId trong request; không được đổi session hoặc chọn DB principal.

**Kiểm chứng bắt buộc:**

- [ ] POST thiếu/sai CSRF bị chặn trước mutation; GET không đổi dữ liệu.
- [ ] Một manager không dùng UUID tổ chức khác để đọc/ghi; DISABLED bị chặn giữa phiên.

**Điều kiện hoàn thành:** Có các kiểm tra quyền nền áp dụng ngay khi viết nghiệp vụ. Ghi case và kết quả trong `docs/evidence/day-04.md`.

## D04-T04 — Seed admin và kiểm thử tài khoản qua HTTP

**Phụ trách đề xuất:** B+C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/config/AdminSeeder.java; TEST/acceptance/Day04IT.java; SQLTEST/day-04.sql; docs/backend/auth.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Seed idempotent: local cho phép demo theo cờ; public bắt buộc credential môi trường khác mặc định.

**Phụ thuộc của task:** D04-T01/T02/T03 và transaction nền.

**Cách thực hiện:**

- [ ] 1. Seed User userName=admin, email hợp lệ, verified ngay và platformRole=ADMIN; không qua OTP công khai.
- [ ] 2. Restart không ghi đè password/hash của admin; khóa/unique bảo vệ seed chạy trùng.
- [ ] 3. Public profile thiếu secret hoặc dùng mật khẩu demo mặc định phải fail startup rõ nhưng không in giá trị.
- [ ] 4. Viết HTTP test bằng cookie jar/client giữ phiên: csrf → register → login → me → logout → me 401.
- [ ] 5. Kiểm tra không có endpoint cấp admin và audit/auth log đã che email/secret phù hợp.

**Kiểm chứng bắt buộc:**

- [ ] Chạy seed hai lần vẫn một admin và hash không đổi.
- [ ] WAR startup public với cấu hình demo bị từ chối.

**Điều kiện hoàn thành:** Ngày 4 hoàn tất xác thực cơ bản; bước OTP ngày 5 bổ sung quyền xác minh. Ghi case và kết quả trong `docs/evidence/day-04.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day04IT` và `database/tests/day-04.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day04IT verify
```
