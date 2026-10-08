# Ngày 18 — Báo cáo, CSV và nhật ký quản trị

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ hai, 2026-10-12. **Ước lượng tạm của kế hoạch ban đầu:** 16 giờ công tổng của nhóm.

**Mục tiêu:** Báo cáo đúng phạm vi/timeline, CSV cùng bộ lọc và an toàn khi mở Excel; audit append-only.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §6.12, §9 UI-17/UI-18/UI-23/UI-24, §14 V04/F05/F10/TR10. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D17 financial views/settlement; V01–V03/V05–V10 đã được các ngày trước triển khai.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D18-T01 — V04 và báo cáo cohort/dòng tiền

**Phụ trách đề xuất:** B. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D18_01_reports.sql; SQLTEST/day-18.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** V04 EventSalesReport; F05 OrganizationRevenue; F10 OrganizationCashFlow; hai khái niệm thời gian tách rõ.

**Phụ thuộc của task:** D17 V03/F02/confirmed snapshots; D15 refund timestamps.

**Cách thực hiện:**

- [ ] 1. V04 một dòng/Event, Event không có đơn trả 0; confirmed settlement lấy snapshot, chưa confirmed ghi số dự tính.
- [ ] 2. F05 lọc Order PAID theo paidAt trong [from,to), chỉ trừ Refund của cohort qua RefundTransferLog SUCCEEDED với processedAt đến toUtc và amount lấy từ Refund cha; không dùng tổng refund hiện tại cho báo cáo quá khứ.
- [ ] 3. F10 lọc Payment CAPTURED theo paidAt và RefundTransferLog SUCCEEDED theo processedAt trong [from,to), lấy amount/purpose từ Refund cha; aggregate riêng rồi kết hợp. Refund 0đ không có dòng tiền; hoàn đơn mua ngoài kỳ vẫn tính.
- [ ] 4. Tách revenue vé/thu compensation/refund vé/refund compensation; timezone filter chuyển UTC một lần.
- [ ] 5. Tạo dataset mua tháng trước hoàn kỳ này, mua kỳ này hoàn kỳ sau, nhiều Payment và log attempt RefundTransferLog và tổ chức không dữ liệu.

**Kiểm chứng bắt buộc:**

- [ ] F05 và F10 khác nhau đúng kỳ vọng ở đơn ngoài kỳ hoàn trong kỳ.
- [ ] V04 không nhân tổng do join; full refund commission0.

**Điều kiện hoàn thành:** Các công thức báo cáo có query/fixture giải thích được. Ghi case và kết quả trong `docs/evidence/day-18.md`.

## D18-T02 — API báo cáo và export CSV

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/service/report/ReportService.java; JAVA/repository/report/ReportRepository.java; JAVA/controller/report/ReportServlet.java; JAVA/controller/report/CsvExportServlet.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** GET /organizations/{id}/reports; GET /admin/reports; GET /reports/export; cùng ReportFilter typed và truy vấn cho JSON/CSV.

**Phụ thuộc của task:** D18-T01 và views các ngày trước; CSV escaper test độc lập với SQL.

**Cách thực hiện:**

- [ ] 1. Parse filter Event/org/from/to/page/sort theo allowlist, validate from<to và range phù hợp; manager bị khóa org đã xác minh.
- [ ] 2. JSON/CSV gọi cùng ReportService/định nghĩa metric, không viết công thức riêng trong exporter.
- [ ] 3. CSV UTF-8 BOM theo nhu cầu Excel tiếng Việt, quote dấu phẩy/dấu nháy/newline đúng chuẩn, định dạng tiền nhất quán.
- [ ] 4. Trung hòa ô text từ user bắt đầu bằng =,+,-,@ hoặc tiền tố whitespace/control làm Excel nhận công thức; phân biệt số tiền server-generated với text untrusted.
- [ ] 5. Giới hạn export/batch pagination, close resource khi client disconnect; không kéo mọi entity vào RAM host nhỏ.

**Kiểm chứng bắt buộc:**

- [ ] Cùng filter JSON và CSV có tổng/count giống nhau.
- [ ] Tên Event '=HYPERLINK(...)', dấu nháy/newline/tiếng Việt không thực thi công thức hoặc lệch cột.

**Điều kiện hoàn thành:** Báo cáo/extract sẵn dùng không cần dashboard frontend. Ghi case và kết quả trong `docs/evidence/day-18.md`.

## D18-T03 — Audit append-only và endpoint tổng quan/hồ sơ

**Phụ trách đề xuất:** B+A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D18_02_audit.sql; JAVA/controller/audit/AuditServlet.java; JAVA/repository/audit/AuditRepository.java; JAVA/controller/identity/ProfileServlet.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** TR10 append-only; GET /admin/audit-logs; GET /admin/overview; GET /me/profile; GET /organizations/{id}/overview.

**Phụ thuộc của task:** D07 audit status, decisions D06/D14/D17; TR10 có thể viết theo schema.

**Cách thực hiện:**

- [ ] 1. TR10 chặn UPDATE/DELETE nhiều dòng AuditLog, cho INSERT và rollback transaction đã insert; không tự log vào chính bảng audit.
- [ ] 2. API audit phân trang/filter aggregate/action/time, admin-only; chi tiết decision đọc đúng action, không toàn bộ entity payload.
- [ ] 3. Tổng quan trả count công việc chờ tổ chức/Event/refund/settlement; organization overview chỉ số trong phạm vi.
- [ ] 4. Profile trả userName/fullName/email/verified/platformRole/organizationRoles được phép; không endpoint sửa credential mới ngoài flow đã có.
- [ ] 5. Rà mọi thao tác nhạy cảm: approve/reject, fee, cancel, refund retry, payout, role changes có actor/time/object/action, TR05 status không ghi đôi.

**Kiểm chứng bắt buộc:**

- [ ] Audit tamper trực tiếp bằng runtime bị chặn; rollback transaction không bị TR10 cản.
- [ ] Check-in không xem audit tài chính; profile không lộ passwordHash/OTP.

**Điều kiện hoàn thành:** Bộ API đọc tương ứng UI-10/18/23/24 và TR10 đầy đủ. Ghi case và kết quả trong `docs/evidence/day-18.md`.

## D18-T04 — Kiểm kê lời gọi toàn bộ SQL và Model

**Phụ trách đề xuất:** C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day18IT.java; database/tests/inventory.sql; docs/backend/sql-usage.md; docs/backend/model-map.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Mỗi V01–V10/SP01–SP17/F01–F10/TR01–TR10 có object, caller, test và evidence; IX kiểm ngày 19–20.

**Phụ thuộc của task:** D18-T01/T02/T03; tất cả Model/SQL đã phân công trước đó.

**Cách thực hiện:**

- [ ] 1. Inventory query sys catalog theo tên chính xác, không chỉ COUNT object; đối chiếu COVERAGE.md.
- [ ] 2. Viết sql-usage một dòng mỗi SQL object: migration, Repository/SP/View gọi nó, endpoint/job và test thực thi.
- [ ] 3. Kiểm mỗi method của 15 Model từ diagram có implementation và test use case; không chấp nhận method stub vì SP đã làm thay.
- [ ] 4. Chạy truy vấn rỗng/cross-org/historical date và đối chiếu toàn bộ V/UDF path từ ứng dụng.
- [ ] 5. Ghi thiếu sót cụ thể để sửa ngày 19–20; diagram chuẩn đã có; ghi cụ thể thuộc tính/phương thức/quan hệ chưa kiểm chứng, không coi đếm tên class là đạt.

**Kiểm chứng bắt buộc:**

- [ ] Inventory phát hiện object thiếu/caller thiếu và fail check tương ứng.
- [ ] Không tính SQL object tạo nhưng chưa bao giờ được ứng dụng gọi là hoàn thành.

**Điều kiện hoàn thành:** Phát hiện lỗ hổng phạm vi trước hai ngày kiểm thử cuối. Ghi case và kết quả trong `docs/evidence/day-18.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day18IT` và `database/tests/day-18.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day18IT verify
```
