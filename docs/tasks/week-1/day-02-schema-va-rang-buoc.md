# Ngày 02 — Thiết kế và dựng toàn bộ schema SQL Server

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ bảy, 2026-09-26. **Ước lượng tạm của kế hoạch ban đầu:** 17 giờ công tổng của nhóm.

**Mục tiêu:** Database mới dựng được toàn bộ bảng, FK và 20 nhóm constraint; có từ điển dữ liệu và ca vi phạm.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §5, §7, §8.2, §14.3. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D01-T01 và D01-T03; mapping đầy đủ phụ thuộc diagram chuẩn.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D02-T01 — ERD, kiểu dữ liệu và chính sách xóa

**Phụ trách đề xuất:** B. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** docs/backend/erd.md; docs/backend/data-dictionary.md; docs/backend/model-map.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Ánh xạ 15 class nghiệp vụ + dữ liệu kỹ thuật theo spec §5.3/14.3; số bảng vật lý theo ERD, không suy từ số class. Mỗi cột có type/null/default/FK và ý nghĩa.

**Phụ thuộc của task:** D01-T01 và D01-T03; đối chiếu diagram chuẩn hiện có trước khi chốt mapping.

**Cách thực hiện:**

- [ ] 1. Đặt tên vật lý nhất quán tránh từ khóa User/Order, ghi mapping rõ để Java và SQL dùng cùng tên.
- [ ] 2. Liệt kê bảng nền identity/category/commission trước bảng Event và các quan hệ giao dịch; tạo FK sau bảng khi có vòng tham chiếu.
- [ ] 3. Ánh xạ UUID, decimal VND, UTC datetime2, enum chuỗi; xác định NOT NULL riêng, không dựa CHECK để cấm NULL.
- [ ] 4. Thiết kế User.platformRole và map organizationRoles qua UserOrganizationRole; Organization giữ requester/quyết định trên cùng record; Refund–Ticket có dấu hiệu đang mở, Refund.currentAttemptId + RefundTransferLog; Settlement giữ tổng + SettlementOrderSnapshot/SettlementTransferLog kỹ thuật; EventCategory là giá trị/lookup, OTP, redemption unique order và outbox có lease/idempotency. Ghi secret fields không được xuất DTO.
- [ ] 5. Ghi khóa ứng viên/phụ thuộc hàm và lý do snapshot giá/nhãn/paidAmount/tổng đối soát; mô tả 3NF và phần lưu dư chủ đích.

**Kiểm chứng bắt buộc:**

- [ ] Mỗi quan hệ diagram có FK hoặc bảng nối tương ứng.
- [ ] Không cascade delete lịch sử Order/Payment/Ticket/Refund/AuditLog.

**Điều kiện hoàn thành:** ERD và từ điển đủ để người khác tạo schema không đoán cột. Ghi case và kết quả trong `docs/evidence/day-02.md`.

## D02-T02 — Viết migration bảng và constraint C01–C20

**Phụ trách đề xuất:** B. **Giờ công:** 6h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D02_01_tables.sql; SQL/D02_02_constraints.sql; SQLTEST/day-02.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** DDL bao phủ đúng C01–C20 ở SPEC §14.3 cùng PK/FK và miền enum.

**Phụ thuộc của task:** D02-T01 đã thống nhất tên/cột/quan hệ.

**Cách thực hiện:**

- [ ] 1. Tạo bảng theo ERD, thêm PK/FK rõ tên; chỉ cascade cấu phần bản nháp khi đã có quy tắc xóa được kiểm tra.
- [ ] 2. Thêm đủ C01–C20; kiểm tra hai loại coupon loại trừ nhau, tiền không âm/quan hệ tổng, Refund purpose/payment/tập Ticket và lịch Event.
- [ ] 3. Thêm unique ngoài danh mục: orderCode, ticketCode, txnRef, Order/Hold, Settlement/Event, SettlementOrderSnapshot/Order, membership, seat label.
- [ ] 4. Tạo filtered unique cho một Hold ACTIVE/user và một request mở/ticket; không dùng GETDATE/SYSUTCDATETIME trong điều kiện index.
- [ ] 5. Viết SQL test từng nhóm với một bản ghi hợp lệ và một vi phạm, bắt đúng lỗi và THROW nếu dữ liệu sai lọt qua.

**Kiểm chứng bắt buộc:**

- [ ] C01–C20 đều có cặp pass/reject; thêm null, sai enum, FK không tồn tại.
- [ ] Payment.amount và SettlementTransferLog.amount phải >0; Refund.amount >=0, vẫn lưu nghĩa vụ 0đ không log chuyển; Ticket/Zone giá 0 hợp lệ. C15/C20 kiểm cả payment null cho 0đ và bù trừ có Payment.

**Điều kiện hoàn thành:** Script chạy được trên DB mới và chặn dữ liệu sai ngay tại DB. Ghi case và kết quả trong `docs/evidence/day-02.md`.

## D02-T03 — Tạo seed và harness integration dùng chung

**Phụ trách đề xuất:** C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SEED/test-fixtures.sql; TEST/acceptance/Day02IT.java; docs/backend/test-data.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Fixture theo CONVENTIONS §8, ID cố định trong test DB; không seed credential thật.

**Phụ thuộc của task:** D02-T02 để chạy fixture; có thể viết case theo data dictionary trước.

**Cách thực hiện:**

- [ ] 1. Tạo hai tổ chức/người dùng với role chéo; ghi ID và lịch tương đối để các test không hết hạn sau một ngày.
- [ ] 2. Tạo helper test mở connection riêng và fixture theo từng case; không dùng một transaction chung cho hai thread concurrency.
- [ ] 3. Đưa password test qua môi trường, tạo hash qua code seed; không viết hash/password online vào fixture.
- [ ] 4. Đọc số dòng/constraint từ sys catalog và assert danh mục; không chỉ đếm tổng object hệ thống.
- [ ] 5. Test dựng schema mới lần đầu, chạy migration runner lần hai không thực thi lại file đã áp dụng; sửa checksum file đã chạy phải báo lỗi.

**Kiểm chứng bắt buộc:**

- [ ] Seed tái tạo được, không nhân User/Organization khi chạy lại theo quy ước.
- [ ] Integration kiểm tra UTF-8 tiếng Việt, BigDecimal lớn và UTC round-trip.

**Điều kiện hoàn thành:** Nhóm có dữ liệu chung để chạy test mà không cần frontend. Ghi case và kết quả trong `docs/evidence/day-02.md`.

## D02-T04 — Rà quyền schema và chuẩn bị mapping JPA

**Phụ trách đề xuất:** A. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** docs/backend/model-map.md; docs/backend/security-matrix.md; TEST/acceptance/SchemaMappingIT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Mapping từng entity → table/column/version/converter; các grant chưa có object sẽ triển khai khi object xuất hiện.

**Phụ thuộc của task:** D02-T01/T02 và thử nghiệm driver D01-T03.

**Cách thực hiện:**

- [ ] 1. Đọc mọi FK và nullable để xác định quan hệ JPA bắt buộc/tùy chọn; TicketCheckInLog.ticketId nullable; Refund.payment null chỉ 0đ; Payment.order bắt buộc; Order.acceptedPayment CAPTURED cùng Order, nullable khi chưa PAID hoặc PAID 0đ; OrderItem.tickets có thể rỗng trước paid.
- [ ] 2. Chỉ ra aggregate nào có cập nhật cạnh tranh cần @Version; SP phải cập nhật version tương ứng nếu entity có version.
- [ ] 3. Ghi kiểu enum/UUID/Instant phải kiểm chứng với driver, quan hệ fetch theo use case và DTO cần trả.
- [ ] 4. Kiểm tra runtime sẽ không cần quyền DDL hoặc xóa lịch sử; ghi những đường JPA CRUD được phép theo trạng thái.
- [ ] 5. Review rollback của migration và kế hoạch forward-fix; không đưa lệnh DROP database dùng chung vào script tiện lợi.

**Kiểm chứng bắt buộc:**

- [ ] Đối chiếu đủ 15 class nghiệp vụ với diagram hiện có và mapping persistence với spec §5.3; chưa chạy kiểm chứng thì chưa nghiệm thu.
- [ ] Schema có thể dùng với Hibernate validate mà không tự create/update.

**Điều kiện hoàn thành:** Mapping được A và B thống nhất trước khi viết entity. Ghi case và kết quả trong `docs/evidence/day-02.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day02IT` và `database/tests/day-02.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day02IT verify
```
