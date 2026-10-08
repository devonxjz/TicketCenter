# Ngày 05 — OTP email, đặt lại mật khẩu và nhà cung cấp tích hợp

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ ba, 2026-09-29. **Ước lượng tạm của kế hoạch ban đầu:** 15 giờ công tổng của nhóm.

**Mục tiêu:** Xác minh email và reset password dùng OTP an toàn; chứng minh gửi email và lựa chọn storage khả thi sớm.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §6.1, §8.4, §10, §11. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D04-T01…T04; cần nhà cung cấp/email sender và sandbox được chủ dự án cấp quyền.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D05-T01 — Phát OTP, resend và giới hạn tần suất

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/service/identity/OtpService.java; JAVA/repository/identity/OtpRepository.java; TEST/identity/OtpTest.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST /auth/otp/send {purpose}; purpose chỉ VERIFY_EMAIL hoặc RESET_PASSWORD, account xác định phù hợp từng luồng.

**Phụ thuộc của task:** D04-T01/T03, schema OTP; MailGateway contract D05-T03.

**Cách thực hiện:**

- [ ] 1. Sinh 6 chữ số bằng SecureRandom, giữ số 0 đầu; lưu HMAC với secret server, userId/purpose/expiry/attempt/consumedAt.
- [ ] 2. Khóa phạm vi user-purpose, resend sau ít nhất 60 giây, mã mới vô hiệu mã cũ cùng mục đích.
- [ ] 3. Giới hạn send/verify theo tài khoản và nguồn request với ngưỡng cấu hình được ghi; tránh dùng header forwarded IP chưa được proxy tin cậy.
- [ ] 4. Gửi qua MailGateway, không ghi mã rõ vào outbox/log; gửi chậm quá hạn phải bỏ mã và yêu cầu resend hợp lệ.
- [ ] 5. Tạo POST /auth/password/forgot nhận email và khởi tạo OTP RESET_PASSWORD; return message nhất quán cho tài khoản có/không tồn tại; ghi rate-limit test riêng.

**Kiểm chứng bắt buộc:**

- [ ] OTP dạng 000001 vẫn đủ 6 ký tự; ở giây 59 không resend, giây 60 được.
- [ ] Concurrent resend chỉ còn một mã hợp lệ; DB/log không có OTP rõ.

**Điều kiện hoàn thành:** Cơ chế phát OTP có hạn dùng 5 phút và chống gửi dồn. Ghi case và kết quả trong `docs/evidence/day-05.md`.

## D05-T02 — Verify OTP và quyền reset một lần

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/controller/identity/OtpServlet.java; JAVA/service/identity/PasswordResetService.java; TEST/identity/PasswordResetIT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST /auth/otp/verify; POST /auth/password/reset; reset token hạn ngắn, một lần, gắn user/purpose.

**Phụ thuộc của task:** D05-T01 cho OTP và D04-T02 cho authVersion/session.

**Cách thực hiện:**

- [ ] 1. Verify phải khóa OTP, so sánh HMAC, kiểm tra purpose/hạn/lần dùng; sai tăng counter trong transaction vẫn commit dù trả lỗi nghiệp vụ.
- [ ] 2. Đúng 5 lần sai vô hiệu mã; now >= expiresAt từ chối; thành công verify email cập nhật emailVerifiedAt và consume OTP cùng transaction.
- [ ] 3. OTP reset đúng tạo quyền reset một lần có expiry, lưu hash hoặc session state phù hợp; không chấp nhận OTP verify email để reset.
- [ ] 4. Reset lưu hash mới, consume quyền reset và tăng authVersion cùng transaction; không nhận userId tùy ý để đổi tài khoản khác.
- [ ] 5. Test hai request verify/reset đồng thời chỉ một thành công, mọi session cũ bị filter từ chối sau reset.

**Kiểm chứng bắt buộc:**

- [ ] OTP hết 5 phút, dùng lại, sai purpose, lần sai thứ 5 đều bị chặn.
- [ ] Reset thành công vô hiệu hai phiên đang mở; password cũ không đăng nhập lại được.

**Điều kiện hoàn thành:** Email verified và reset password có transaction/rate limit/one-use đầy đủ. Ghi case và kết quả trong `docs/evidence/day-05.md`.

## D05-T03 — Kết nối email thật và xác nhận storage

**Phụ trách đề xuất:** C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/integration/mail/MailGateway.java; JAVA/integration/mail/ConfiguredMailGateway.java; docs/backend/integrations.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** MailGateway nhận người nhận/nội dung có kiểm soát; provider response không được tự đổi trạng thái nghiệp vụ.

**Phụ thuộc của task:** D01-T04/approval provider; làm spike độc lập với OTP, bàn giao MailGateway cho D05-T01.

**Cách thực hiện:**

- [ ] 1. So sánh khả năng đăng ký, xác minh sender, HTTPS và quota với điều kiện tài khoản hiện có; chủ dự án chọn/duyệt dịch vụ và dependency trước khi cấu hình.
- [ ] 2. Thực hiện gửi email test tới hộp thư được phép; ghi thời gian nhận và lỗi đã lọc, không commit địa chỉ cá nhân hoặc API key.
- [ ] 3. Định nghĩa timeout/retry bounded cho email; OTP ưu tiên độ trễ và không gửi mã hết hạn, email vé sẽ dùng outbox ngày 12.
- [ ] 4. Chốt dịch vụ storage ngoài container, quyền upload/delete theo namespace ứng dụng; thử một ảnh test và đọc lại qua HTTPS.
- [ ] 5. Ghi biến cấu hình và thao tác khôi phục; nếu chưa có tài khoản thì adapter test vẫn kiểm tra logic nhưng mục kết nối thật giữ blocked.

**Kiểm chứng bắt buộc:**

- [ ] Mail thực tới hộp thư test và link ứng dụng đúng base URL.
- [ ] Ảnh test không phụ thuộc filesystem Tomcat; không có credential trong response/log.

**Điều kiện hoàn thành:** Loại sớm rủi ro provider khiến cuối tuần 3 không thể gửi OTP/upload. Ghi case và kết quả trong `docs/evidence/day-05.md`.

## D05-T04 — Kiểm thử auth đầy đủ và tài liệu sử dụng

**Phụ trách đề xuất:** B+C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day05IT.java; SQLTEST/day-05.sql; docs/backend/auth.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Chuỗi register → OTP → login; forgot → OTP reset → password mới; API chỉ trả dữ liệu cần thiết.

**Phụ thuộc của task:** D05-T01/T02/T03; provider thiếu thì tách logic test với integration thật.

**Cách thực hiện:**

- [ ] 1. Viết bảng case bằng Clock kiểm soát tại trước/đúng/sau hạn, 4/5 lần sai và 59/60 giây resend.
- [ ] 2. Test user không tồn tại, user disabled, account chưa verify, race login/reset và request body quá lớn.
- [ ] 3. Chứng minh verification thất bại vẫn lưu attempts/rate limit; không rollback counter vì exception mapping.
- [ ] 4. Truy vấn DB đối chiếu HMAC/expiry/consumed và authVersion mà không in hash/secret ra evidence.
- [ ] 5. Ghi HTTP mẫu request/response đã che bí mật và cách người QA nhận OTP test bằng adapter test chỉ trong môi trường test.

**Kiểm chứng bắt buộc:**

- [ ] mvn -B -Psqlserver-it -Dit.test=Day05IT verify pass trên SQL thật.
- [ ] Không có endpoint đọc OTP/debug secret được đóng gói trong profile public.

**Điều kiện hoàn thành:** Auth/OTP đủ dùng cho tổ chức và mua vé. Ghi case và kết quả trong `docs/evidence/day-05.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day05IT` và `database/tests/day-05.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day05IT verify
```
