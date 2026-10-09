# Swagger UI — chạy và cập nhật

Owner: Vương, issue [VUONG-01 #28](https://github.com/devonxjz/TicketCenter/issues/28).

## Chạy ứng dụng

Dùng JDK 25 và Tomcat 11 theo stack dự án. Build WAR bằng `mvn -B -DskipTests package` sau khi chạy nhóm test phù hợp. Deploy `target/ticketscenter.war` vào môi trường kiểm thử đã cấu hình DB/Redis/SMTP của nhóm. Truy cập `<APP_BASE_URL>/swagger/` hoặc `<APP_BASE_URL>/swagger/index.html`; đặc tả tại `<APP_BASE_URL>/api/openapi.json`. APP_BASE_URL bao gồm context của WAR.

Các asset Swagger UI 5.33.1 được lưu trong WAR, không tải CDN khi mở trang. Nguồn, license và checksum tại [vendor/README.md](../../src/main/webapp/swagger/vendor/README.md).

UI tự lấy context đang mở; root deployment dùng `/`, WAR `ticketscenter` dùng `/ticketscenter`. Không nhập host API ngoài ứng dụng. Nếu không tải được tài liệu, kiểm tra endpoint OpenAPI, deployment và thông báo trên trang.

## Kiểm thử thủ công

1. Mở Health, thử `/health/live` trước để xác nhận UI gọi đúng URL. Readiness phản ánh trạng thái registry; 503 khi chưa sẵn sàng không có nghĩa Swagger lỗi.
2. Mở Authentication, đăng nhập bằng tài khoản kiểm thử của môi trường. Trình duyệt tự quản lý cookie HttpOnly; không nhập JSESSIONID vào Authorize. Dùng phiên trình duyệt riêng nếu cần kiểm thử nhiều vai trò.
3. Với API đã triển khai, chọn Try it out rồi Execute. Mỗi mutation lấy CSRF mới từ `/api/auth/csrf` của cùng phiên; không giữ token trong localStorage. UI từ chối gửi mutation nếu không lấy được token.
4. Với API yêu cầu Idempotency-Key, caller nhập khóa mới cho hành động mới và giữ nguyên khóa/payload khi replay. UI không tự tạo khóa hoặc đổi khóa. Đây là cơ chế chống lặp, không thay quyền truy cập.
5. Đăng xuất khi xong. Bodyless POST được bổ sung Content-Type để tương thích RequestValidationFilter.

Swagger hiển thị request cURL và có thể hiển thị token runtime của phiên kiểm thử. Không copy cURL chứa CSRF, cookie, mật khẩu, OTP hoặc response reset token vào Git, ảnh evidence hay kênh trao đổi. Không lưu Authorization trong UI.

`planned` và `legacy` chỉ để đọc hợp đồng; không có Try it out và interceptor cũng chặn thực thi. `implemented` nghĩa servlet tồn tại tại baseline ghi trong spec, không tự chứng minh SQL/SMTP hay toàn bộ nghiệp vụ đã được nghiệm thu.

## Cập nhật contract cùng feature

Owner cung cấp method/path, quyền, media type, DTO, status/error, idempotency và invalidation đã kiểm chứng. Vương cập nhật `src/main/resources/openapi.json`, giữ operationId duy nhất, đường dẫn UUID và local schema references. Contract dự kiến còn dùng `x-contract-status: owner-review-required`; chỉ chuyển sang `matched-to-source` và `implemented` sau khi đối chiếu source và evidence. Khi đổi route, cập nhật link và test cùng thay đổi. Callback IPN công khai phải verify chữ ký và trả protocol riêng.

Cập nhật [REDIS-REGISTRY.md](../references/REDIS-REGISTRY.md) cùng producer/consumer: namespace, scope, TTL, dữ liệu được phép lưu và invalidation sau commit. Auth namespace, QR và IPN đang có điểm cần thống nhất được ghi rõ trong registry.

## Kiểm tra cục bộ cho phần này

```powershell
# Chọn JDK 25 trong phiên terminal hiện tại nếu Java mặc định khác phiên bản.
$env:JAVA_HOME = 'C:\Users\Le Vuong\.jdks\openjdk-25'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -B '-Dtest=OpenApiContractTest,SwaggerUiAvailabilityTest,FeaturePackageStructureTest,AuthOtpServletsTest,IdentityWebFlowTest,UserPrincipalTest,SecurityContextTest,OtpCryptoHelperTest,AuthServiceTest#testRegisterInvalidEmail+testRegisterPasswordTooShort+testLoginNullOrBlankInputs+testCurrentUserDtoSanitization,EmailSenderTest#shouldValidateEmailMessageFields+fakeEmailSenderShouldRecordMessages+fakeEmailSenderShouldThrowWhenFailureSimulated' test
node --test src/test/js/swagger-initializer.test.cjs
mvn -B -DskipTests package
./src/test/scripts/swagger-smoke.ps1 -TomcatHome 'E:/apache-tomcat-11.0.25/apache-tomcat-11.0.25'
```

Node dùng module test có sẵn, không cần npm install. Nhóm test trên không mở DB hoặc gửi email. Full `mvn test` của baseline có test dùng DB và test gửi SMTP thật; chỉ chạy trong môi trường fixture đã được chủ môi trường chuẩn bị. Nhóm test này không thay yêu cầu CI toàn hệ thống xanh trước merge.

Script smoke dành cho PowerShell 7/Windows, nhận thư mục Tomcat 11 và JDK 25 trên máy. Mỗi lần chạy giải nén WAR vừa build vào thư mục mới trong `target/swagger-smoke`, tạo cấu hình server riêng với một connector loopback và port tự chọn, rồi chạy 10 test HTTP cho ROOT và `/ticketscenter`. Script không kế thừa ứng dụng/listener/resource của Tomcat đã cài. Nó dùng class/resource thật của WAR và ba filter Validation/CSRF/Session nhưng giới hạn servlet mapping, không khởi tạo persistence/seed/worker/SMTP. JVM được dừng trong `finally`, log giữ trong thư mục của lượt chạy. Đây là kiểm thử HTTP boundary; không thay full-app/SQL integration. Đừng chạy riêng file `swagger-http-smoke.test.cjs` vào môi trường đang có dữ liệu.

## Nhánh và bàn giao

Theo convention trong issue hiện hành: `dev` → `feat/vuong/vuong-01-swagger-redis-contract` → PR về `dev`; Conventional Commits, review đồng đội và CI xanh trước merge. Tài liệu GitFlow cũ còn dùng `develop/feature`, cần nhóm cập nhật thống nhất; không trộn hai quy ước.

Không sửa model, pom, persistence hay implementation của owner khác để làm cho contract trông đã hoàn tất. Evidence và các giới hạn kiểm thử tại [VUONG-01.md](../evidence/vuong/VUONG-01.md).
