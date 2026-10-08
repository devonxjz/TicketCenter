# Ngày 03 — Model nền, JPA và transaction theo principal

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Chủ nhật, 2026-09-27. **Ước lượng tạm của kế hoạch ban đầu:** 18 giờ công tổng của nhóm.

**Mục tiêu:** Có đủ entity đúng mapping, lớp transaction thật và baseline quyền runtime; nghiệp vụ từng nhóm được bổ sung trong các ngày tương ứng.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §2–5, §7–8, §14.2, §14.10. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D02-T01…T04; phiên bản thư viện đã được duyệt.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D03-T01 — Ánh xạ 15 class nghiệp vụ và kiểm tra Model nền

**Phụ trách đề xuất:** A. **Giờ công:** 6h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/model/{identity,event,ticketing,order,fulfillment,settlement}/; JAVA/persistence/; TEST/model/ModelMappingTest.java; TEST/acceptance/Day03IT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Giữ toàn bộ API Model từ diagram; các phương thức nghiệp vụ được kiểm chứng ở ngày sở hữu, không để stub chạy như đã hoàn thành.

**Phụ thuộc của task:** D02-T01/T04; schema D02-T02 để nghiệm thu mapping.

**Cách thực hiện:**

- [ ] 1. Tạo đúng 15 class theo model-map và API diagram; enum/DTO/lookup, map role, log/snapshot thuộc kiểu giá trị hoặc persistence riêng. Không biến log hoàn/chi thành Model nghiệp vụ; tránh setter public cho trạng thái/tiền lịch sử.
- [ ] 2. Đưa kiểm tra bất biến đơn đối tượng vào Model: quantity, số tiền, thời gian, enum; dùng BigDecimal/Instant thay double/LocalDateTime không timezone.
- [ ] 3. Không tạo quan hệ cascade REMOVE cho dữ liệu lịch sử; mặc định fetch tiết kiệm và truy vấn chủ động khi tạo DTO.
- [ ] 4. Kiểm tra UUID/Instant/decimal/enum qua ghi đọc SQL thật; kiểm tra optimistic version ở đối tượng sửa cấu hình.
- [ ] 5. Ghi từng phương thức diagram vào checklist model-map với ngày test hành vi: hold ngày 8, paid ngày 11, used ngày 13, refund ngày 14–15, settlement ngày 17.

**Kiểm chứng bắt buộc:**

- [ ] Hibernate validate thành công; từng nhóm entity round-trip đúng.
- [ ] Không hoàn thành phương thức bằng return null/UnsupportedOperationException rồi tick đủ Model.

**Điều kiện hoàn thành:** Đủ cấu trúc 15 class nghiệp vụ và mapping kỹ thuật riêng, test bất biến nền; hành vi có lịch hoàn thành riêng. Ghi case và kết quả trong `docs/evidence/day-03.md`.

## D03-T02 — EntityManager lifecycle và TransactionManager

**Phụ trách đề xuất:** A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/config/PersistenceListener.java; JAVA/transaction/TransactionManager.java; src/main/resources/META-INF/persistence.xml; TEST/transaction/TransactionManagerIT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** TransactionManager nhận principal đã xác minh và một callback dùng cùng EntityManager; trả DTO sau commit, luôn close/rollback đúng.

**Phụ thuộc của task:** D01-T02/D02-T02; entity từ D03-T01 khi test JPA hoàn chỉnh.

**Cách thực hiện:**

- [ ] 1. Tạo factory theo cấu hình principal khi startup, đóng khi shutdown; không tạo factory mỗi request hoặc share EntityManager giữa thread.
- [ ] 2. Thực hiện begin → callback → flush/commit → close; exception rollback khi còn active, giữ nguyên lỗi nghiệp vụ đã ánh xạ.
- [ ] 3. Cấu hình pool tổng, timeout hữu hạn; mọi lời gọi Repository trong use case nhận cùng EntityManager.
- [ ] 4. Viết test SP thử trong database test theo quy tắc @@TRANCOUNT/savepoint/XACT_STATE; gọi từ JPA rồi cố tình ném lỗi để chứng minh rollback ngoài SP.
- [ ] 5. Test clear/refresh sau native/SP không dùng entity cache cũ; xác nhận transaction không mở xuyên external HTTP call.

**Kiểm chứng bắt buộc:**

- [ ] Rollback ngoài SP xóa cả thay đổi của SP; SP độc lập tự commit đúng.
- [ ] Exception không rò connection; lần gọi tiếp theo vẫn dùng được pool.

**Điều kiện hoàn thành:** Có boundary giao dịch duy nhất dùng lại cho các Service. Ghi case và kết quả trong `docs/evidence/day-03.md`.

## D03-T03 — Bốn principal runtime và principal kỹ thuật

**Phụ trách đề xuất:** B. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D03_01_security.sql; database/security/local-logins.sql; database/security/azure-users.sql; SQLTEST/day-03.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** R01–R04 có Login/User/Role local; auth/worker có quyền hẹp riêng; migration không dùng runtime.

**Phụ thuộc của task:** D02-T02; đồng bộ cấu hình principal với D03-T02.

**Cách thực hiện:**

- [ ] 1. Tạo role buyer, manager, check-in, admin theo SPEC; bí mật được provisioning an toàn, không hard-code trong script.
- [ ] 2. Viết baseline grants tối thiểu trên bảng hiện có; chưa cấp EXECUTE lên SP chưa tạo. Mỗi migration ngày sau bổ sung đúng quyền cùng object.
- [ ] 3. Tách auth principal chỉ truy cập dữ liệu xác thực và worker chỉ đường job; không dùng db_owner để chạy ứng dụng.
- [ ] 4. Backend chọn cấu hình theo use case sau authorization; mở transaction rồi không đổi principal giữa chừng.
- [ ] 5. Thử EXECUTE AS/REVERT và xác thực bằng login thật; ghi actor session context rồi xóa/ghi đè khi trả/mượn connection.

**Kiểm chứng bắt buộc:**

- [ ] Check-in không SELECT bảng tài chính; buyer không UPDATE payment trực tiếp.
- [ ] Connection tái sử dụng không mang actor người trước.

**Điều kiện hoàn thành:** Ứng dụng có đường chọn quyền thật, tránh đến cuối mới thay một DB user toàn quyền. Ghi case và kết quả trong `docs/evidence/day-03.md`.

## D03-T04 — Lỗi, thời gian và test mẫu xuyên các tầng

**Phụ trách đề xuất:** C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/exception/; JAVA/dto/; docs/backend/api-contract.md; docs/evidence/day-03.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Lỗi nghiệp vụ ổn định, correlationId an toàn; clock controllable cho test nhưng HTTP không đặt now.

**Phụ thuộc của task:** D03-T01/T02/T03 khi chạy kiểm thử xuyên tầng.

**Cách thực hiện:**

- [ ] 1. Tạo mapping lỗi validation/auth/conflict/system sang envelope ở CONVENTIONS; stack trace chỉ log đã lọc phía server.
- [ ] 2. Viết một test xuyên Servlet→Service→Repository→SQL rồi kiểm tra response và rollback; dùng endpoint test chỉ trong test deployment, không ship route gây lỗi.
- [ ] 3. Đặt Clock dùng trong Java vào cấu hình test, thống nhất SQL UTC; ghi cách tạo fixture đúng mốc thời gian.
- [ ] 4. Kiểm tra request malformed UUID, số quá lớn, decimal có phần lẻ không hợp lệ cho VND; lỗi có field/code rõ.
- [ ] 5. Ghi hướng dẫn thêm một IT mới, command Maven/sqlcmd và nơi lưu evidence để người tiếp theo lặp được.

**Kiểm chứng bắt buộc:**

- [ ] Response lỗi không chứa SQL/connection details; parser từ chối overflow trước truy vấn.
- [ ] Health liveness không cần DB; readiness kiểm tra DB nhẹ và fail 503 khi không kết nối được.

**Điều kiện hoàn thành:** Các ngày sau dùng cùng cách validation, transaction và đo kết quả. Ghi case và kết quả trong `docs/evidence/day-03.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day03IT` và `database/tests/day-03.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day03IT verify
```
