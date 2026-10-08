# Ngày 19 — Rà quyền toàn hệ thống và chuẩn bị benchmark index

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ ba, 2026-10-13. **Ước lượng tạm của kế hoạch ban đầu:** 16 giờ công tổng của nhóm.

**Mục tiêu:** Chứng minh 4 role/login thực thi đúng quyền; kiểm thử tấn công vào API/SP; chuẩn bị dữ liệu và baseline 15 index.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §4, §6.1, §9.4, §11.3, §14.8/14.10/14.11. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D18 tất cả endpoint/SQL object; các grants đã được cập nhật hàng ngày.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D19-T01 — Ma trận GRANT/REVOKE/DENY và quyền hiệu lực

**Phụ trách đề xuất:** B. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** database/security/local-logins.sql; database/security/azure-users.sql; SQL/D19_01_security_review.sql; SQLTEST/day-19.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** R01–R04 + technical principals; chứng minh cho phép/từ chối bằng từng Login/User thật.

**Phụ thuộc của task:** D18 đầy đủ SQL object; baseline principals D03 và grants từng ngày.

**Cách thực hiện:**

- [ ] 1. Đối chiếu grants SPEC §14.10; check-in DENY financial views/UDF/bảng gốc, không SELECT V06; không một runtime nào là db_owner/sysadmin.
- [ ] 2. Buyer SP09 chỉ free branch, worker SP10/SP17 chỉ cancelled path; gọi trực tiếp với actor/status sai để chứng minh DB guard.
- [ ] 3. Minh họa GRANT quyền thử trên test User rồi REVOKE khi không còn nguồn cấp khác; DENY riêng check-in và thử inherited/public grants.
- [ ] 4. Kiểm ownership chain/module permission đủ cho SP chạy mà không cấp DML rộng; HTTP row authorization vẫn cần dù DB role đúng.
- [ ] 5. Azure script chỉ contained users/roles phù hợp, không chạy nguyên Login server script; ghi tách quyền DDL/migration.

**Kiểm chứng bắt buộc:**

- [ ] Có bằng chứng mỗi role ít nhất một allowed và denied, cùng các trường hợp SP có tiền nhạy cảm.
- [ ] User multi-role không gom mọi connection quyền cao cho mọi endpoint.

**Điều kiện hoàn thành:** R01–R04 đạt kiểm thử quyền thực tế cả SQL và HTTP. Ghi case và kết quả trong `docs/evidence/day-19.md`.

## D19-T02 — Security regression toàn API

**Phụ trách đề xuất:** A+C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/security/AuthorizationMatrixIT.java; TEST/acceptance/Day19IT.java; docs/backend/security-matrix.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Mọi route mutation có auth/CSRF/owner/member/admin; callback public có verifier, upload/CSV/log an toàn.

**Phụ thuộc của task:** D18 endpoint contracts; D19-T01 cho kiểm tích hợp quyền DB, HTTP cases chuẩn bị độc lập.

**Cách thực hiện:**

- [ ] 1. Duyệt endpoint contract từng dòng; test user khác, org khác, revoked member, disabled user và authVersion mới.
- [ ] 2. Fuzz có mục tiêu UUID sai, SQL-like strings, sort injection, JSON duplicate/type mismatch, amount/quantity overflow và payload quá lớn.
- [ ] 3. Test CSRF/session fixation, unsafe redirect ở login/return, forged IPN và buyer gửi CAPTURED/SUCCEEDED.
- [ ] 4. Kiểm log access/application không chứa body login/OTP/raw QR/share token/query secrets; response không stack trace/connection string.
- [ ] 5. Kiểm public config reject default admin và cookie/security headers trên HTTPS; sửa shared root cause rồi chạy các caller bị ảnh hưởng.

**Kiểm chứng bắt buộc:**

- [ ] Mỗi route có chủ sở hữu quyền rõ; không route debug/test-only được public WAR đóng gói.
- [ ] Các lỗ hổng tái hiện có regression đỏ→xanh, không chỉ đổi lỗi hiển thị.

**Điều kiện hoàn thành:** Boundary bảo mật đã rà đầy đủ, có danh sách failure được sửa. Ghi case và kết quả trong `docs/evidence/day-19.md`.

## D19-T03 — Dataset và baseline cho IX01–IX15

**Phụ trách đề xuất:** C+B. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** database/benchmarks/seed.sql; database/benchmarks/queries.sql; docs/backend/index-benchmark.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** 15 query ổn định tương ứng IX01–IX15, dữ liệu đủ lớn/phân bố rõ, baseline trên database benchmark riêng.

**Phụ thuộc của task:** D18 caller queries, D02 schema trên DB benchmark riêng; không phụ thuộc security test hoàn tất.

**Cách thực hiện:**

- [ ] 1. Tạo seed tái lập cho nhiều org/Event, lịch sử Order/Payment/Refund và RefundTransferLog/SettlementTransferLog lớn, ít active/pending và một org có nhiều bản ghi.
- [ ] 2. Viết 15 query đúng caller spec: catalog/kho/history/job/doanh thu/log hoàn/coupon/hàng chờ Refund/vé/log chi/audit/rule/Event nội bộ. IX08 dùng RefundTransferLog, IX10 dùng Refund; IX12 giữ tên IX_Payout_Settlement_Status nhưng nằm trên SettlementTransferLog.
- [ ] 3. Đo trước trên schema chưa có 15 performance indexes; unique/filtered unique bảo vệ nghiệp vụ vẫn giữ nguyên.
- [ ] 4. Thu actual plan, STATISTICS IO/TIME, count kết quả và số lần chạy; warm-up và lặp điều kiện tương đương, lưu median.
- [ ] 5. Không DROP index trên dev/demo; nếu benchmark có index sẵn cần database riêng hoặc xác nhận exact destructive target trước thao tác.

**Kiểm chứng bắt buộc:**

- [ ] Có baseline cho từng IX01–IX15 với SQL/tham số/data volume cụ thể.
- [ ] Dataset synthetic không chứa thông tin thật hoặc credential.

**Điều kiện hoàn thành:** Ngày20 đo sau có mốc so sánh hợp lệ, không đoán phần trăm cải thiện. Ghi case và kết quả trong `docs/evidence/day-19.md`.

## D19-T04 — Sửa thiếu coverage và rà actor context/pool

**Phụ trách đề xuất:** A. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** docs/backend/sql-usage.md; TEST/transaction/PrincipalIsolationIT.java; docs/evidence/day-19.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Khép gap từ D18; total pools trong ngân sách; session context không bị tái sử dụng sai.

**Phụ thuộc của task:** Gap list D18-T04; D19-T01/T02 cho quyền và connection tests.

**Cách thực hiện:**

- [ ] 1. Chạy inventory V/SP/F/TR và method map; thiếu caller thì thêm vào use case thật của SPEC, không tạo endpoint chỉ để tăng số lượng.
- [ ] 2. Luân phiên requests buyer/manager/check-in/admin trên connection tái sử dụng, assert database principal và actor audit.
- [ ] 3. Kiểm lifetime transaction, EntityManager close và pool tổng bằng metric; role config mới không tự tăng 5 connections mỗi role.
- [ ] 4. Review tất cả đường ghi dùng cùng locking.md, nhất là JPA CRUD membership/coupon/layout với trigger và SP.
- [ ] 5. Cập nhật evidence và ghi những mục cần fixture đặc biệt cho E2E ngày 21.

**Kiểm chứng bắt buộc:**

- [ ] Không principal/actor bleed; không View bị cho là tự có row security.
- [ ] Coverage thiếu còn lại được ghi thành lỗi cụ thể, không đánh đạt chung.

**Điều kiện hoàn thành:** Backend chuẩn bị cho đóng gói và stress/recovery. Ghi case và kết quả trong `docs/evidence/day-19.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day19IT` và `database/tests/day-19.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day19IT verify
```
