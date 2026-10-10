# VUONG-02 — kiểm chứng phần chính sách độc lập

Issue: [#29](https://github.com/devonxjz/TicketCenter/issues/29). Nhánh: `feat/vuong/vuong-02-commission-rules`, base `dev` tại `cfd77c60ce539f145a6af7749ba050561be6dd3f`.

Đặc tả đã duyệt: [commission-rules-design](../../superpowers/specs/2026-10-09-vuong-02-commission-rules-design.md), commit `b1977fa`. Kế hoạch đã duyệt và thực thi Native: [commission-rules-plan](../../superpowers/plans/2026-10-09-vuong-02-commission-rules.md), commit `667d860`.

## Kết quả giai đoạn hiện tại

Đã có bốn lớp thuần Java trong `vn.ticketscenter.settlement.commission`: lỗi miền với mã ổn định, DTO tạo kiểm điều khoản, DTO projection kiểm giá trị/tính hiệu lực và policy quyền ADMIN/chặn edit.

- Tỷ lệ 0..100, scale 4 chính xác; loại bỏ zero dư, từ chối phần lẻ bị mất khi làm tròn. Zero với scale Integer.MIN_VALUE vẫn chuẩn hóa được, rate `1E+10000` bị từ chối theo miền giá trị.
- Phí nguyên đồng 0..Long.MAX_VALUE; thời gian bắt đầu nhỏ hơn kết thúc, hiệu lực theo `[from,to)` và giữ precision Instant.
- Principal bắt buộc có platform ADMIN. Guard quyền chạy trước guard count; count >0 chặn edit, count âm là lỗi trạng thái backend.
- Projection giữ UUID và long count, không chứa entity JPA hoặc lấy giờ hệ thống. `isEffectiveAt` yêu cầu thời gian tham chiếu do caller cung cấp.

Chưa có Repository/Service ghi DB, Servlet, JSP, cache Redis hoặc endpoint HTTP mới. Policy không thay transaction bảo vệ việc edit/publish Event đồng thời.

## Lần kiểm chứng ngày 09/10/2026

Chạy trong managed worktree mới, tách khỏi `target/` và preview #28; JDK 25, Maven 3.9.16, JUnit 5.12.0 của pom hiện có. Không đổi pom/model/persistence/database hoặc code owner khác.

| Nhóm | Số ca | Failures / Errors / Skipped | Kết quả |
|---|---:|---|---|
| Unit regression nền trước thay đổi | 43 | 0 / 0 / 0 | Exit 0 |
| CommissionRuleCreateDtoTest | 14 | 0 / 0 / 0 | Exit 0 |
| CommissionRulePolicyTest | 12 | 0 / 0 / 0 | Exit 0 |
| CommissionRuleDtoTest | 21 | 0 / 0 / 0 | Exit 0 |
| Regression cuối: 47 ca mới + 43 ca nền | 90 | 0 / 0 / 0 | Exit 0 |
| Đóng gói WAR sau regression | — | Tests đã chạy riêng phía trên | Exit 0 |
| Kiểm whitespace diff | — | — | Exit 0 |

Ba lần RED đều exit 1 ở `testCompile` vì lớp production chưa tồn tại: Task 1 thiếu CommissionRuleException/CommissionRuleCreateDto, Task 2 thiếu CommissionRulePolicy, Task 3 thiếu CommissionRuleDto. Đây là lỗi biên dịch khi viết test trước API, chưa phải assertion failure lúc chạy test. Sau khi triển khai, các lần GREEN chạy lần lượt 14, 26 và 47 ca, không failures/errors/skips.

Commit Task 1: `e130bb6`; Task 2: `b2d8952`; Task 3: `db8f522`.

## Review độc lập và quyết định bàn giao

Reviewer độc lập đọc toàn bộ range `cfd77c6..db8f522`, đặc tả, kế hoạch, diff và log kiểm chứng. Kết quả: không có Critical/Important/Minor; cả năm Review Focus được bao phủ đúng. Reviewer chỉ đọc, không sửa checkout và không chạy lại test; kết quả chạy test do implementer kiểm chứng trực tiếp trong phiên này. Không cần sửa implementation sau review.

Các quyết định được ghi trong ledger khi thực thi:

- Review sau commit Task 3 để package review có diff đầy đủ; vẫn review trước tích hợp. Nếu cần sửa sau review sẽ có commit bổ sung.
- Giữ HTTP parser/serialization/status mapping/JSP ở giai đoạn tích hợp như đã duyệt. Kết quả hiện tại chưa chứng minh hoạt động qua HTTP.
- Giữ DB mutation/lock/concurrent attachment/rollback ở giai đoạn tích hợp khi có schema và DB JDBC test riêng. Policy hiện tại chưa chứng minh bất biến khi edit và publish Event chạy đồng thời.
- Giữ idempotency/audit/Redis/approve integration ở giai đoạn tích hợp khi owner chốt contract. Chưa có bằng chứng chống mutation lặp hoặc invalidation cache trong runtime.

Ngày 09/10/2026, người dùng yêu cầu tiếp tục bàn giao theo quy trình: push feature branch và mở Draft PR vào `dev`. Không merge và không đóng #29. WAR và báo cáo Surefire được giữ trong `target/` của managed worktree; `target/` cũ ở checkout chính thuộc #28, không dùng làm bằng chứng build #29.

## Chuẩn bị Draft PR

Fetch xác nhận `origin/dev` vẫn tại `cfd77c60ce539f145a6af7749ba050561be6dd3f`, là tổ tiên của nhánh này; không cần merge cập nhật. Chạy lại nhóm regression 90 ca và package trên revision `38b25cd` trong worktree ngày 09/10/2026: 0 failures/errors/skips, cả hai lệnh exit 0. Commit bàn giao sau đó chỉ đổi hồ sơ này, không đổi implementation/test.

CI **chưa chạy và chưa đạt gate merge**. Workflow hiện tại gọi bare `mvn test`, bao gồm test SMTP thật sử dụng cấu hình chung, và các test DB chung. Commit bàn giao dùng `[skip ci]` để việc push/mở Draft PR không kích hoạt các tác động này. Theo [GitHub Docs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/skip-workflow-runs), chỉ dẫn này bỏ qua workflow `push`/`pull_request`; nó không thay thế checks bắt buộc.

Cần owner phối hợp sửa profile CI để unit test không dùng SMTP/DB thật, integration có DB và mail test riêng, đồng thời thu hồi/đổi credential SMTP đã nằm trong cấu hình theo dõi của repo. Các file identity/config/workflow không được sửa lẫn vào PR #29. Khi môi trường CI an toàn được chốt, cập nhật nhánh và thêm commit không có skip instruction để chạy checks thật trước khi Ready/merge.

Reviewers đề xuất theo ownership hiện tại: Thái (`devonxjz`) kiểm principal/ADMIN và contract duyệt tổ chức; Đông (`trungdong9919-svg`) kiểm linked event count và quy tắc bất biến rule khi publish. PR ghi `Refs #29`; Issue vẫn mở cho phần tích hợp.

## Lệnh tái kiểm chứng

```powershell
$env:JAVA_HOME = 'C:\Users\Le Vuong\.jdks\openjdk-25'
$env:PATH = "$env:JAVA_HOME\bin;E:\apache-maven-3.9.16\bin;$env:PATH"

mvn -B '-Dtest=CommissionRuleCreateDtoTest,CommissionRulePolicyTest,CommissionRuleDtoTest' test

mvn -B '-Dtest=CommissionRuleCreateDtoTest,CommissionRulePolicyTest,CommissionRuleDtoTest,FeaturePackageStructureTest,AuthOtpServletsTest,IdentityWebFlowTest,UserPrincipalTest,SecurityContextTest,OtpCryptoHelperTest,AuthServiceTest#testRegisterInvalidEmail+testRegisterPasswordTooShort+testLoginNullOrBlankInputs+testCurrentUserDtoSanitization,EmailSenderTest#shouldValidateEmailMessageFields+fakeEmailSenderShouldRecordMessages+fakeEmailSenderShouldThrowWhenFailureSimulated' test

mvn -B -DskipTests package
git diff --check
```

Lệnh package bỏ qua test vì regression đã chạy riêng; thành công package chỉ xác nhận build WAR, không xác nhận runtime HTTP/SQL. Test gọi các lớp thật, không dùng repository giả để tuyên bố mutation đã thành công.

## Phần nghiệm thu còn thiếu

#29 tiếp tục mở. Các điều kiện tích hợp trong mục 5 của đặc tả vẫn cần nhóm cung cấp/chốt:

1. Schema UUID/tc_commission_rules và view/UDF thống nhất với model đã khóa.
2. DB test JDBC riêng để kiểm create/edit/audit/rollback và tranh chấp edit với publish Event trong transaction thật.
3. Hợp đồng idempotency và lưu replay/fingerprint cho mutation.
4. Audit cùng transaction, cache invalidation sau commit và contract approve tổ chức với owner Thái.
5. Repository/Service/Servlet/JSP và nghiệm thu COM-01…COM-04 qua HTTP/SQL.

Không chạy bare `mvn test`/full integration: suite hiện có các ca dùng DB chung, sửa schema/dữ liệu và gửi email thật. Những ca DB/SMTP (JpaConnectionTest, các service/repository test DB và PasswordResetIT; EmailSenderTest#smtpEmailSenderShouldSendRealEmail) không được tính vào 90 ca đạt. Hồ sơ này không khẳng định toàn bộ suite xanh hoặc #29 đã nghiệm thu xong.
