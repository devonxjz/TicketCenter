# Ngày 13 — Check-in vé và lịch sử quét theo tổ chức

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ tư, 2026-10-07. **Ước lượng tạm của kế hoạch ban đầu:** 15 giờ công tổng của nhóm.

**Mục tiêu:** Một vé chỉ vào cửa một lần; kết quả từ chối được lưu, nhân viên không thấy tài chính.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §4, §6.8, §14 SP04/V05/F07/TX04. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D11 Ticket; D06 membership; D07 F07; D12 job không bắt buộc cho API quét.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D13-T01 — SP04 kiểm quyền, giờ và trạng thái vé

**Phụ trách đề xuất:** B. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D13_01_checkin.sql; SQLTEST/day-13.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_CheckInTicket(eventId,actorId,ticketCode) → result/time/thông tin tối thiểu; ghi rejection nghiệp vụ rồi commit.

**Phụ thuộc của task:** D11 ticket, D06 active membership và D07 F07.

**Cách thực hiện:**

- [ ] 1. Kiểm active membership MANAGER/CHECK_IN_STAFF trong org của Event trước tìm/lộ ticket; thiếu quyền không ghi TicketCheckInLog nghiệp vụ.
- [ ] 2. Kiểm Event không CANCELLED và giờ [start−60 phút,end); khóa ticket tìm theo code, kiểm đúng Event và ACTIVE.
- [ ] 3. Thành công chuyển USED và insert TicketCheckInLog cùng transaction; USED/refund pending/refunded/invalidated đều từ chối.
- [ ] 4. Mã không tồn tại vẫn insert TicketCheckInLog với ticketId NULL; sai Event không lộ owner/paidAmount/ticketCode của Event khác.
- [ ] 5. TX04 lỗi insert history phải rollback USED; hai session quét chỉ một SUCCESS, session còn lại lưu ALREADY_USED.

**Kiểm chứng bắt buộc:**

- [ ] Đúng cửa mở được nhận, đúng endTime bị chặn.
- [ ] Sai mã được lưu lịch sử; thiếu quyền bị chặn trước TicketCheckInLog.

**Điều kiện hoàn thành:** SP04/TX04 nhất quán status và history. Ghi case và kết quả trong `docs/evidence/day-13.md`.

## D13-T02 — API quét và V05 lịch sử

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/service/fulfillment/CheckInService.java; JAVA/controller/fulfillment/CheckInServlet.java; JAVA/repository/fulfillment/CheckInRepository.java; SQL/D13_02_checkin_reads.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** GET /organizations/{id}/check-in-events; POST /check-ins {eventId,ticketCode}; GET /events/{id}/check-ins.

**Phụ thuộc của task:** SP04 contract D13-T01; V05 chuẩn bị từ schema, tích hợp sau SP04.

**Cách thực hiện:**

- [ ] 1. Camera tương lai và nhập mã tay cùng POST; backend validate code length/shape, actor lấy session.
- [ ] 2. Chọn principal check-in hoặc manager theo quyền xác minh; không serialize Ticket entity chứa paidAmount.
- [ ] 3. V05 LEFT JOIN Ticket để giữ lần mã lạ; paginate theo scannedAt/id và lọc organization/Event.
- [ ] 4. GET check-in-events/F07 trả thời gian/cờ hợp lệ tối thiểu, không báo cáo doanh thu.
- [ ] 5. Response quét không trả raw QR, password hoặc PII owner; log chỉ ticketId khi được phép và correlationId.

**Kiểm chứng bắt buộc:**

- [ ] Nhân viên A sửa eventId B bị 403/404; không đọc V06 chứa paidAmount.
- [ ] History có đúng cả success/reject và tổng số lần khác tổng vé thành công.

**Điều kiện hoàn thành:** Có backend quét QR dùng được qua HTTP. Ghi case và kết quả trong `docs/evidence/day-13.md`.

## D13-T03 — Mất response và quyền đổi giữa phiên

**Phụ trách đề xuất:** C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/fulfillment/CheckInSecurityIT.java; TEST/model/TicketTest.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Ticket.checkIn(event, now) chỉ từ ACTIVE; sau network error tra history/status được phép trước thao tác tiếp.

**Phụ thuộc của task:** D13-T01/T02 và D04 authVersion; Model Ticket D11.

**Cách thực hiện:**

- [ ] 1. Test Ticket.checkIn(event, now) hai lần ở Model và SP, không đưa DB/HTTP vào entity.
- [ ] 2. Mô phỏng response đầu mất sau commit; request sau báo đã dùng, hệ thống không diễn giải là có thêm người vào.
- [ ] 3. Membership bị vô hiệu hoặc manager đổi thành check-in trong phiên: request tiếp dùng quyền mới.
- [ ] 4. Thử input dài, control characters, UUID/code có ký tự đặc biệt; raw code không lọt error/access log.
- [ ] 5. Thử ngoài giờ, wrong Event, refund pending, invalidated và cancelled fixture; lý do đúng, không response generic SUCCESS.

**Kiểm chứng bắt buộc:**

- [ ] Hai request trong window khác nhau không vượt kiểm tra giờ vì cache.
- [ ] Không có dữ liệu tiền trong cả success và rejection DTO của nhân viên.

**Điều kiện hoàn thành:** Hành vi cổng vào rõ cả khi race/mạng lỗi. Ghi case và kết quả trong `docs/evidence/day-13.md`.

## D13-T04 — Kiểm chứng concurrency và quyền DB trực tiếp

**Phụ trách đề xuất:** B+C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day13IT.java; TEST/concurrency/CheckInConcurrencyIT.java; docs/evidence/day-13.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** SP04 được execute bởi manager/check-in; buyer không có quyền; V05 được lọc ở Repository.

**Phụ thuộc của task:** D13-T01/T02/T03; test race refund hoàn tất sau SP05 ngày 14.

**Cách thực hiện:**

- [ ] 1. Chạy hai connection barrier cùng ticket, assert một USED và hai history result đúng.
- [ ] 2. Gọi SP04 bằng buyer/login trực tiếp và actor giả để chắc chắn không chỉ Servlet bảo vệ.
- [ ] 3. Đối chiếu V05 trước/sau multirow fixture; mã lạ NULL không rơi mất vì INNER JOIN.
- [ ] 4. Test read transaction đóng trước response; lỗi DB rollback không để vé USED thiếu lịch sử.
- [ ] 5. Chuẩn bị case tranh chấp check-in/refund để ngày 14 chạy khi SP05 xuất hiện; ghi điểm barrier vào test hiện hữu.

**Kiểm chứng bắt buộc:**

- [ ] Day13IT và SQL TX04 pass; kiểm tra lock được nhả sau timeout/error.
- [ ] Tổng SUCCESS mỗi ticket tối đa 1.

**Điều kiện hoàn thành:** Check-in hoàn thiện backend, camera UI ngoài phạm vi. Ghi case và kết quả trong `docs/evidence/day-13.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day13IT` và `database/tests/day-13.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day13IT verify
```
