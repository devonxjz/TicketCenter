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

Node dùng module test có sẵn, không cần npm install. Nhóm test trên không mở DB hoặc gửi email. Default `mvn test` hiện dùng ExternalTestCondition để bỏ qua các ca DB/SMTP có tên và lý do; opt-in phải chỉ đúng fixture riêng và marker. Xem [test isolation](platform-test-isolation.md). Nhóm test này không thay yêu cầu CI toàn hệ thống xanh trước merge.

Script smoke dành cho PowerShell 7/Windows, nhận thư mục Tomcat 11 và JDK 25 trên máy. Mỗi lần chạy giải nén WAR vừa build vào thư mục mới trong `target/swagger-smoke`, tạo cấu hình server riêng với một connector loopback và port tự chọn, rồi chạy 12 test HTTP cho ROOT và `/ticketscenter`. Script không kế thừa ứng dụng/listener/resource của Tomcat đã cài. Nó dùng class/resource thật của WAR và filter Validation/CSRF/Session; có thêm hai servlet đọc check-in và filter/named-dispatch của owner để kiểm401 khi không có phiên. Mapping vẫn giới hạn, không khởi tạo persistence/seed/worker/SMTP. JVM được dừng trong `finally`, log giữ trong thư mục của lượt chạy. Đây là kiểm thử HTTP boundary; không thay full-app/SQL integration. Đừng chạy riêng file `swagger-http-smoke.test.cjs` vào môi trường đang có dữ liệu.

## Phần tài chính đã tích hợp

Các route commission/settlement/payout, báo cáo/CSV/options và ADMIN audit/overview đã chuyển `implemented`/`matched-to-source`; DTO tiền là chuỗi VND nguyên, UUID canonical, UTC. `GET /api/organizations/{organizationId}/overview` đã triển khai: ADMIN hoặc MANAGER ACTIVE của đúng tổ chức, kiểm status/authVersion/membership SQL mỗi request; không nhận query/filter, trả KPI tổ chức và tiền nguyên VND theo UTC. Không có KPI toàn sàn hoặc cache tổng quan ADMIN. Tổ chức không tồn tại trả404 sau kiểm quyền; truy cập chéo tổ chức hoặc CHECK_IN_STAFF trả403. Xem [nghiệm thu tổng quan tổ chức](../evidence/vuong/VUONG-06-ORGANIZATION-OVERVIEW.md). Các trang `/finance/commission`, `/finance/settlements`, `/finance/reports`, `/finance/audit`, `/finance/overview` kiểm quyền hiện tại; hai trang sau chỉ dành cho ADMIN. Hỗ trợ Swagger cho tài chính không đồng nghĩa đã nghiệm thu toàn bộ #28 hoặc E2E toàn sàn của #33.

Reconcile payout nhận body `{}` với Idempotency-Key bằng payoutId, lấy amount/fingerprint từ reservation bền và chỉ lookup provider. Mọi reservation PENDING chặn payoutId mới cho đến khi có kết quả cuối. Provider production mặc định chưa sẵn sàng; `fixture-simulated` chỉ hoạt động với loopback DB `TC_Vuong_Test_*` có marker, không đại diện ngân hàng thật.

Sau khi build và nạp DB_URL/DB_NAME/DB_USER/DB_PASSWORD cùng DB_ADMIN_* từ cấu hình riêng ngoài build cache, dùng `./src/test/scripts/finance-http-smoke.ps1 -TomcatHome '<Tomcat11>' -SqlcmdPath '<sqlcmd>'`. Script giới hạn dedicated loopback fixture, seed UUID ngẫu nhiên, chạy 20 ca HTTP kiểm JDBC/servlets/JSP tài chính, báo cáo/CSV và Dashboard/Audit, rồi dừng JVM riêng khi xong. Nó dùng test-only session bridge với control token riêng; bridge không nằm trong WAR sản phẩm. Xem [nghiệm thu báo cáo](../evidence/vuong/VUONG-05-ACCEPTANCE.md) cho số ca và giới hạn. Chưa chứng minh login flow/full WAR/worker của owner khác.

Trang `/finance/reports` dành cho ADMIN hoặc MANAGER đang hoạt động. ADMIN có thể chọn toàn nền tảng; MANAGER chỉ chọn tổ chức mình quản lý. JSON và CSV dùng cùng metric, UUID tổ chức/sự kiện, UTC `[from,to)`. Tổng hợp/top sự kiện bao gồm toàn bộ kết quả theo bộ lọc, CSV không bị giới hạn bởi trang. Với Excel, dùng Data → From Text/CSV, UTF-8, comma, kiểu Text cho tiền/mã để không mất chữ số; không dùng Text Import Wizard cũ cho trường có newline. CSV trung hòa công thức bằng TAB trong ô quote theo quyết định đã duyệt. CI HTTP chung chạy Swagger/check-in boundary 12 ca; 20 ca finance/report/dashboard/audit/organization-overview HTTP cần fixture riêng như trên.

`GET /api/check-ins/events` và `GET /api/events/{eventId}/check-in-window` đã khớp source KHANH-01: data mảng sự kiện hoặc DTO khung giờ, membership MANAGER/CHECK_IN_STAFF, UUID và UTC; chức năng scan/refund còn planned. Danh sách API chờ theo owner và các điều kiện ghép nhóm tại [VUONG-01 đối chiếu hợp đồng](../evidence/vuong/VUONG-01-RECONCILIATION.md).

Dashboard/Audit: xem [nghiệm thu VUONG-06](../evidence/vuong/VUONG-06-ACCEPTANCE.md). Audit yêu cầu `from/to` UTC và có actor/targetTable/organization/event filters; dữ liệu chi tiết chỉ allowlist. Cache tổng quan kiểm quyền DB trước đọc và có TTL60/fallback; chưa nghiệm thu mọi hook ghi của owner.

## Nhánh và bàn giao

Phạm vi #29–#31 dùng `dev` → `feat/vuong/vuong-02-04-ready` → PR về `dev`, Conventional Commits và CI xanh trên cả commit PR lẫn commit dev sau merge. Người dùng đã cho phép merge các task hoàn thành mà không chờ review đồng đội; vẫn tuân thủ mọi yêu cầu GitHub thực sự áp dụng, không tạo approval giả hoặc thay branch protection. Các phần còn lại giữ tại [PR #42](https://github.com/devonxjz/TicketCenter/pull/42). Tài liệu GitFlow cũ còn dùng `develop/feature`, cần nhóm cập nhật thống nhất; không trộn hai quy ước.

Không sửa model, pom, persistence hay implementation của owner khác để làm cho contract trông đã hoàn tất. Evidence và các giới hạn kiểm thử tại [VUONG-READY-MERGE.md](../evidence/vuong/VUONG-READY-MERGE.md).
