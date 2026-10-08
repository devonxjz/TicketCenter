# Ngày 01 — Chốt nền tảng, mô hình và hợp đồng backend

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ sáu, 2026-09-25. **Ước lượng tạm của kế hoạch ban đầu:** 16 giờ công tổng của nhóm.

**Mục tiêu:** Build được WAR tối thiểu, kết nối SQL Server và có hợp đồng chung để nhóm triển khai cùng một mô hình.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §1–5, §8, §11, §14.2, §14.10. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** Không có task trước. Cần JDK 25, Maven, Tomcat và quyền tạo database kiểm thử; diagram chuẩn đã có tại docs/classdiagram/diagram.md.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D01-T01 — Kiểm kê mô hình và khóa phạm vi

**Phụ trách đề xuất:** C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** docs/backend/model-map.md; docs/backend/decisions.md. Diagram hiện có là đầu vào chỉ đọc; cần đồng bộ thiết kế riêng nếu phát hiện bất đồng. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Đầu vào là SPEC/diagram chuẩn; đầu ra là bảng class → thuộc tính/phương thức → bảng SQL → ngày triển khai.

**Phụ thuộc của task:** Không có; dùng spec.md và diagram chuẩn hiện có.

**Cách thực hiện:**

- [ ] 1. Đọc spec.md và diagram chuẩn hiện có; kiểm kê 15 class, 25 quan hệ (10 ownership, 15 association) và API Model hiện hành.
- [ ] 2. Lập bảng đúng 15 class nghiệp vụ theo SPEC §5; phân biệt class nghiệp vụ với UserOrganizationRole, EventCategory, TicketCheckInLog/AuditLog, RefundTransferLog, SettlementOrderSnapshot/SettlementTransferLog, OTP/redemption/outbox/bảng nối. Chép đầy đủ phương thức từ diagram.
- [ ] 3. Ghi quyết định tạm cho câu hỏi còn mở: chuẩn hóa email/coupon, độ dài trường, giới hạn upload và request, pagination, clock. Đánh dấu quyết định kỹ thuật, không đổi nghiệp vụ.
- [ ] 4. Đối chiếu ba luồng mẫu: mua một ghế; mua vé đứng có coupon; thu muộn hoàn bù trừ. Ghi các lớp và trạng thái tham gia để phát hiện thiếu FK.
- [ ] 5. Ghi bất đồng giữa spec/diagram nếu có và xử lý đồng bộ trước triển khai phần liên quan; giữ thứ tự/phụ thuộc task, chốt lại lịch và giờ công theo năng lực thực tế.

**Kiểm chứng bắt buộc:**

- [ ] Đếm 15 class nghiệp vụ duy nhất; mọi lớp có task thực hiện trong COVERAGE.md.
- [ ] Mapping chỉ được nghiệm thu khi đã đối chiếu thuộc tính/phương thức/quan hệ, không chỉ đếm tên class.

**Điều kiện hoàn thành:** Người mới đọc model-map biết lớp nào cần tạo, lớp nào chỉ là DTO/persistence kỹ thuật. Ghi case và kết quả trong `docs/evidence/day-01.md`.

## D01-T02 — Dựng Maven WAR và smoke test Tomcat

**Phụ trách đề xuất:** A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** pom.xml; .gitignore; .env.example; src/main/webapp/WEB-INF/web.xml; JAVA/controller/HealthServlet.java; TEST/acceptance/Day01IT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** GET /health/live → 200 với trạng thái ứng dụng; không gọi DB nặng hoặc trả cấu hình.

**Phụ thuộc của task:** Dependency/tooling phải được duyệt; dùng SPEC §2, không phụ thuộc diagram chi tiết.

**Cách thực hiện:**

- [ ] 1. Liệt kê dependency/plugin cần dùng và phiên bản ứng viên trong decisions.md; xin chủ dự án chấp thuận trước khi thêm, kể cả thư viện test, JSON, pool và QR.
- [ ] 2. Rà pom.xml Java 25 hiện có (chưa WAR/dependency backend), kiểm tra tổ hợp JDK 25/Tomcat 11 bằng ứng dụng nhỏ; bổ sung packaging war, release 25, Servlet API provided. Không đóng gói container vào WAR.
- [ ] 3. Tạo HealthServlet trả JSON UTF-8 cố định; đặt finalName dự kiến ticketscenter để runbook có một đường WAR ổn định.
- [ ] 4. Cấu hình unit test và profile sqlserver-it chạy lớp *IT ở integration-test/verify; database thiếu phải khiến profile thất bại rõ.
- [ ] 5. Build, deploy WAR lên Tomcat local, gọi health, dừng/redeploy và gọi lại; ghi JDK/Maven/Tomcat thực dùng, không chỉ phiên bản mong muốn.

**Kiểm chứng bắt buộc:**

- [ ] mvn -B verify tạo target/ticketscenter.war.
- [ ] GET /health/live sau redeploy trả 200; nội dung không lộ biến môi trường.

**Điều kiện hoàn thành:** Có WAR chạy được và quy trình build tái lập; dependency chưa được duyệt phải ghi blocked. Ghi case và kết quả trong `docs/evidence/day-01.md`.

## D01-T03 — Chuẩn bị SQL Server và cách chạy script an toàn

**Phụ trách đề xuất:** B. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** docs/backend/database-runbook.md; database/README.md; TEST/acceptance/DatabaseConnectionIT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Có database dev/test/benchmark riêng và kết nối TLS thích hợp; chưa dùng tài khoản migration làm runtime.

**Phụ thuộc của task:** Quyền môi trường SQL test; có thể làm cùng D01-T02.

**Cách thực hiện:**

- [ ] 1. Xác minh SQL Server thật truy cập được từ Java, kiểu xác thực và quyền hiện có; chỉ tạo tài nguyên trong môi trường được chủ dự án cho phép.
- [ ] 2. Đặt tên rõ database dev, test và benchmark; ghi guard trước mọi script có khả năng xóa dữ liệu, không dùng database demo đang chạy để reset.
- [ ] 3. Chọn cách chạy migration tuần tự có bảng lịch sử/checksum hoặc công cụ đã được duyệt; ghi thứ tự, cách phát hiện file đã áp dụng và lỗi giữa migration.
- [ ] 4. Viết integration smoke SELECT 1 bằng driver dự kiến; kiểm tra sai host/password trả lỗi đã lọc, không log URL chứa bí mật.
- [ ] 5. Ghi lệnh sqlcmd phù hợp authentication local, cách nạp bí mật ngoài Git, timeout kết nối và cách mọi thành viên dựng test DB mới.

**Kiểm chứng bắt buộc:**

- [ ] Java mở kết nối và SELECT 1 thành công; host sai thất bại trong timeout.
- [ ] Chạy profile khi thiếu DB phải đỏ, không skipped thành xanh.

**Điều kiện hoàn thành:** Có runbook kết nối và môi trường test thật để ngày 2 viết schema. Ghi case và kết quả trong `docs/evidence/day-01.md`.

## D01-T04 — Chốt HTTP, principal và sơ đồ khóa

**Phụ trách đề xuất:** A+C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** docs/backend/api-contract.md; docs/backend/locking.md; docs/backend/security-matrix.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Thống nhất envelope, actor từ session, principal theo use case, lỗi và đường SP sở hữu ghi.

**Phụ thuộc của task:** D01-T01 cho phạm vi; D01-T02/T03 cho khả năng driver/principal, cập nhật sau spike.

**Cách thực hiện:**

- [ ] 1. Dùng CONVENTIONS.md để ghi các dạng UUID/tiền/thời gian/lỗi; lập danh sách endpoint từ các ngày 4–18 và ánh xạ tới UI-01…UI-24 của SPEC.
- [ ] 2. Lập ma trận buyer/manager/check-in/admin/auth-worker/migration với endpoint và SP; tài khoản kỹ thuật không phải role lựa chọn trên HTTP.
- [ ] 3. Vẽ thứ tự khóa dự kiến cho createHold, releaseHold, payment result, refund, cancel, settlement; xét Hold cũ ở Event khác và đổi coupon A→B/B→A.
- [ ] 4. Đặt ngân sách pool tổng cho ứng dụng; không tạo 5 connection tối thiểu cho mỗi principal. Định nghĩa connection được chọn một lần cho transaction.
- [ ] 5. Chốt danh sách biến môi trường có tên nhưng không giá trị thật; đăng ký các nhu cầu sandbox/email/storage để không chờ đến tuần 3.

**Kiểm chứng bắt buộc:**

- [ ] Review một request giả actorId/admin trong body phải không thể chọn principal.
- [ ] Mỗi đường ghi có một owner và một chiến lược tra kết quả sau mất mạng.

**Điều kiện hoàn thành:** Hợp đồng đủ để B viết SQL và A viết Java không tạo đường ghi trùng nhau. Ghi case và kết quả trong `docs/evidence/day-01.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day01IT` và `database/tests/day-01.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day01IT verify
```
