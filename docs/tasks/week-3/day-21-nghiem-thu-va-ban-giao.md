# Ngày 21 — Nghiệm thu backend toàn luồng và bàn giao tái lập

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ năm, 2026-10-15. **Ước lượng tạm của kế hoạch ban đầu:** 16 giờ công tổng của nhóm.

**Mục tiêu:** Người khác dựng backend từ repo và chạy được các luồng chính, có bằng chứng chức năng/SQL/quyền/khôi phục đầy đủ.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §12, §13, §14.9–14.14; ngoại lệ frontend theo yêu cầu hiện tại. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** Tất cả task trước có bằng chứng; mục online/provider hoặc mapping chưa kiểm chứng phải được báo riêng, không che bằng mock.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D21-T01 — Bốn kịch bản end-to-end từ HTTP

**Phụ trách đề xuất:** C+A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day21IT.java; docs/backend/demo-scenarios.md; docs/evidence/day-21.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Chạy trên SQL Server thật với controller/service/SP; dữ liệu trạng thái được tạo qua API, chỉ seed danh mục/tài khoản bootstrap.

**Phụ thuộc của task:** D20 candidate; tất cả use case đã kiểm từng phần; mapping/provider blocker ghi riêng.

**Cách thực hiện:**

- [ ] 1. Kịch bản1: register→OTP→org request/admin approve→event upload/submit/publish→hold seated+standing→coupon→sandbox pay→ticket/QR→check-in.
- [ ] 2. Kịch bản2: mua→request partial refund→reject và request hợp lệ tiếp→approve→simulated success; kiểm paidAmount/kho/coupon.
- [ ] 3. Kịch bản3: zero order + paid order + pending hold + UNKNOWN payment→admin cancel→restart→worker dọn/refund/compensation→ngoại lệ USED.
- [ ] 4. Kịch bản4: Event kết thúc→blockers chưa xong bị chặn→giải quyết→recalc/confirm→partial payouts→paid; JSON/CSV cùng tổng.
- [ ] 5. Ghi request/response đã lọc, dòng SQL trước/sau và reference sandbox an toàn; test fake provider tách khỏi bằng chứng thật.

**Kiểm chứng bắt buộc:**

- [ ] Cả bốn flow thực hiện không cần chỉnh status trực tiếp DB.
- [ ] Sandbox/OTP/storage thật được ghi đạt riêng khi có evidence; mock chỉ chứng minh logic.

**Điều kiện hoàn thành:** Backend chính chạy xuyên tất cả tầng. Ghi case và kết quả trong `docs/evidence/day-21.md`.

## D21-T02 — Kiểm bộ SQL, transaction và security cuối

**Phụ trách đề xuất:** B. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** database/tests/inventory.sql; database/tests/acceptance.sql; docs/backend/sql-usage.md; docs/evidence/sql-acceptance.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** 20C,10V,17SP,10F,10TR,15IX,17TX và4R đúng danh mục, có nơi dùng và evidence.

**Phụ thuộc của task:** D20 candidate/index evidence, D19 security; chạy song song E2E trên DB test khác.

**Cách thực hiện:**

- [ ] 1. Chạy inventory theo tên và cột/quan hệ, đếm object ứng dụng đúng danh mục; đối chiếu coverage table từng dòng.
- [ ] 2. Chạy commit/rollback đã định nghĩa cho TX01–TX17; các ca concurrency trọng yếu dùng hai phiên thật, timeout và final-state assertions.
- [ ] 3. Chạy trigger multirow, quyền GRANT/REVOKE/DENY, direct SP principal sai và backend row scope một lần trên bản candidate.
- [ ] 4. Đối chiếu tiền test vector Java/SQL, snapshot và lịch sử cashflow; kiểm mọi caller UDF/View thực sự được dùng.
- [ ] 5. Lưu kết quả benchmark 15 IX đã đo và version/query/data; không chạy destructive benchmark lại trên DB bàn giao.

**Kiểm chứng bắt buộc:**

- [ ] Không SP tự commit outer JPA; không runtime db_owner/sysadmin.
- [ ] Không object 'có DDL nhưng không có ứng dụng dùng' được tick nghiệm thu.

**Điều kiện hoàn thành:** Bộ minh chứng DBMS backend đầy đủ có thể truy vết. Ghi case và kết quả trong `docs/evidence/day-21.md`.

## D21-T03 — Người mới dựng máy sạch và chạy demo

**Phụ trách đề xuất:** A+C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** README.md; database/README.md; docs/backend/deployment.md; docs/backend/operations.md; .env.example. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Runbook có phiên bản, config names, migration/grant/seed/build/deploy/test và tài khoản demo theo môi trường.

**Phụ thuộc của task:** D20 runbook/image; người dựng sạch dùng database riêng để không phá E2E.

**Cách thực hiện:**

- [ ] 1. Một thành viên không phải người viết setup làm theo README từ DB mới/checkout sạch; ghi mỗi bước thiếu và sửa tài liệu ngay.
- [ ] 2. README hiện có encoding UTF-16; nếu cần sửa, chuyển có chủ đích sang UTF-8 và giữ nội dung tên dự án, không mất dữ liệu khác.
- [ ] 3. Liệt kê tất cả env names, nguồn lấy secret, không giá trị; local demo admin chỉ được bật profile phù hợp.
- [ ] 4. Demo bằng HTTP client/curl/Postman collection đã lọc hoặc Java test client; cung cấp payload/expected response cụ thể cho từng flow.
- [ ] 5. Ghi backup/restore hoặc script tái tạo database trên môi trường được phép, recovery UNKNOWN/outbox, redeploy ảnh và session; không yêu cầu giao diện.

**Kiểm chứng bắt buộc:**

- [ ] Người mới tự build mvn -B verify, integration profile và chạy WAR/container thành công.
- [ ] Script/package/docs không chứa secret, cookie, OTP hoặc QR còn dùng được.

**Điều kiện hoàn thành:** Bàn giao tự chạy được, không cần hỏi lại tác giả từng bước. Ghi case và kết quả trong `docs/evidence/day-21.md`.

## D21-T04 — Đối chiếu hoàn thành và công bố phạm vi đạt

**Phụ trách đề xuất:** A+B+C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** docs/evidence/backend-acceptance.md; docs/backend/known-issues.md; docs/tasks/COVERAGE.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Bảng PASS/FAIL/BLOCKED theo requirement; frontend/báo cáo môn học đầy đủ không nằm trong 21 ngày backend.

**Phụ thuộc của task:** D21-T01/T02/T03 và toàn bộ evidence; mục thiếu giữ FAIL/BLOCKED.

**Cách thực hiện:**

- [ ] 1. Rà đủ 15 class nghiệp vụ/attributes/methods/relationships với diagram chuẩn hiện có; bảng kỹ thuật/enum kiểm riêng, không cộng thành class nghiệp vụ. Mapping chưa chạy vẫn chưa nghiệm thu.
- [ ] 2. Đánh dấu task theo bằng chứng, liên kết test/log/commit, không tick do hết ngày; thống kê việc còn chặn và người xử lý.
- [ ] 3. Xác nhận backend phục vụ 24 UI contracts nhưng không claim đã đạt UI responsive/camera/JSP; phần báo cáo 50–100 trang/slide là công việc riêng.
- [ ] 4. Không tuyên bố deployed Azure/Render hoặc email thật nếu thiếu credential/quyền; bàn giao artifact local đạt cùng blocker cụ thể.
- [ ] 5. Ghi kết quả cuối build/unit/integration/E2E/security/concurrency/recovery/benchmark và phạm vi; tạo commit/tag phát hành chỉ theo quyền Git được chủ dự án cho phép.

**Kiểm chứng bắt buộc:**

- [ ] Mọi PASS có evidence mới trên bản candidate; FAIL/BLOCKED còn nguyên rõ ràng.
- [ ] Không tuyên bố nghiệm thu toàn môn học khi frontend/hồ sơ môn học ngoài lịch này.

**Điều kiện hoàn thành:** Bộ backend và hướng dẫn bàn giao chi tiết, trạng thái trung thực. Ghi case và kết quả trong `docs/evidence/day-21.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day21IT` và `database/tests/day-21.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day21IT verify
```
