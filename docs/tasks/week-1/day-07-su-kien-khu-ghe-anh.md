# Ngày 07 — Sự kiện, khu/ghế, ảnh bìa và công khai

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ năm, 2026-10-01. **Ước lượng tạm của kế hoạch ban đầu:** 18 giờ công tổng của nhóm.

**Mục tiêu:** Manager dựng sự kiện; admin công khai có phí; public tìm kiếm được và cấu trúc sau publish được khóa.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §6.3, §9 UI-01/02/12/13/20, §10 Ảnh, §14 SP12/V01/V02/F03/F07/TR01–03/TR05. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D06; storage được chọn ở D05-T03.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D07-T01 — CRUD bản nháp và sinh ghế

**Phụ trách đề xuất:** A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/service/event/EventService.java; JAVA/controller/event/EventServlet.java; JAVA/repository/event/EventRepository.java; TEST/event/EventTest.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST/GET /organizations/{id}/events; POST /events/{id}/edit, /zones, /submit; POST /zones/{id}/edit,/delete.

**Phụ thuộc của task:** D06 organization/membership/rule; D03 transaction.

**Cách thực hiện:**

- [ ] 1. Validate saleStart < saleEnd <= startTime < endTime, category tồn tại, tên/địa điểm/mô tả; actor manager đúng org.
- [ ] 2. Khu SEATED nhận hàng/ghế mỗi hàng, sinh A…Z, AA… theo quy ước được ghi; STANDING chỉ capacity, không tạo Seat.
- [ ] 3. Chặn nhãn trùng, số lượng âm/0 hoặc vượt giới hạn cấu hình an toàn; tạo cả khu/ghế trong một transaction.
- [ ] 4. POST /events/{id}/delete xóa thực bản nháp và cấu phần không có tham chiếu; giữ lịch sử khi có giao dịch. Submit kiểm tra ảnh và ít nhất một khu rồi PENDING_APPROVAL.
- [ ] 5. Cho sửa/resubmit REJECTED; sau PUBLISHED chỉ cho trường SPEC cho phép, giữ lịch/địa điểm có ảnh hưởng nghiệp vụ bất biến nếu chưa có chính sách thay đổi được chốt.

**Kiểm chứng bắt buộc:**

- [ ] Rollback khi sinh một ghế lỗi không để khu bán phần.
- [ ] Giá 0 hợp lệ; price âm, schedule sai, org khác bị chặn.

**Điều kiện hoàn thành:** API tạo dữ liệu Event/Zone/Seat đầy đủ và có xóa bản nháp hợp lệ. Ghi case và kết quả trong `docs/evidence/day-07.md`.

## D07-T02 — Upload ảnh bìa và thay ảnh an toàn

**Phụ trách đề xuất:** C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/integration/storage/ImageStorage.java; JAVA/controller/event/EventImageServlet.java; TEST/event/ImageUploadIT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST /events/{id}/cover multipart; chỉ manager đúng org; trả URL ảnh sau lưu thành công.

**Phụ thuộc của task:** D07-T01 Event owner và storage D05-T03.

**Cách thực hiện:**

- [ ] 1. Giới hạn byte, loại thật qua decode ảnh và kích thước pixel; không tin filename/Content-Type, không nhận HTML/SVG có nội dung thực thi nếu chưa xử lý an toàn.
- [ ] 2. Sinh storage key riêng, không dùng đường dẫn do client gửi; upload ngoài transaction, sau đó transaction cập nhật URL nếu quyền/trạng thái còn hợp lệ.
- [ ] 3. Nếu DB update thất bại, ghi kế hoạch dọn ảnh mới mồ côi; thay URL xong mới lên lịch dọn ảnh cũ.
- [ ] 4. Storage ngoài container; quyền credential chỉ namespace cần thiết; không lộ key trong DTO.
- [ ] 5. Test file giả đuôi png, quá dung lượng, decode bomb nhỏ-byte/lớn-pixel, mất storage và hai lần thay ảnh đồng thời.

**Kiểm chứng bắt buộc:**

- [ ] Upload lỗi giữ URL cũ; redeploy vẫn đọc ảnh.
- [ ] Không thể dùng ../ hoặc URL nội bộ từ request làm đích upload.

**Điều kiện hoàn thành:** Backend ảnh hoạt động bằng HTTP, không cần form frontend. Ghi case và kết quả trong `docs/evidence/day-07.md`.

## D07-T03 — Publish và trigger bảo vệ

**Phụ trách đề xuất:** B. **Giờ công:** 6h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D07_01_event_rules.sql; SQLTEST/day-07.sql; JAVA/repository/event/EventRepository.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** SP12/TX12, TR01/TR02/TR03/TR05; POST /admin/events/{id}/publish hoặc /reject.

**Phụ thuộc của task:** D07-T01/T02/rule D06; trigger và SP có thể viết từ schema trước khi có upload thật.

**Cách thực hiện:**

- [ ] 1. SP12 kiểm admin, PENDING_APPROVAL, cấu trúc/lịch/ảnh và CommissionRule đúng org/đang hiệu lực; khóa trước kiểm tra.
- [ ] 2. Publish lưu rule một lần, idempotent không đổi rule khi gọi lại. Reject cần lý do để manager đọc/sửa/resubmit.
- [ ] 3. TR01/TR02 kiểm cả inserted/deleted và cha cũ/mới: khóa cấu trúc khu/ghế đã publish/cancelled, cho price và inventory status hợp lệ.
- [ ] 4. TR03 bảo vệ rate/fixed/org của rule đã dùng; TR05 ghi duy nhất EVENT_STATUS_CHANGED, actor session context được backend kiểm soát.
- [ ] 5. Test TX12 lỗi audit rollback publish; hai connection publish đua sửa layout/rule; thêm/sửa nhiều dòng có một vi phạm.

**Kiểm chứng bắt buộc:**

- [ ] Public event luôn có rule hợp lệ; đổi phí đã áp dụng bị chặn.
- [ ] Thay giá khu được, thay capacity/nhãn/parent sau publish bị từ chối.

**Điều kiện hoàn thành:** SP12 và bốn trigger có test rollback/multirow/concurrency. Ghi case và kết quả trong `docs/evidence/day-07.md`.

## D07-T04 — Tìm kiếm công khai và chốt tuần 1

**Phụ trách đề xuất:** C+A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D07_02_event_reads.sql; JAVA/repository/event/EventQueryRepository.java; TEST/acceptance/Day07IT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** V01/V02/F03/F07; GET /events, /events/{id}, /events/{id}/zones, /events/{id}/check-in-window.

**Phụ thuộc của task:** D07-T01/T03; V01/V02/F03/F07 chuẩn bị từ schema, nghiệm thu sau publish.

**Cách thực hiện:**

- [ ] 1. V01 chỉ PUBLISHED; V02 SEATED đếm ghế, STANDING dùng capacity/held/sold; F03 đọc cùng công thức, zone lạ trả tập rỗng.
- [ ] 2. F07 trả cửa mở start−60 phút, đóng endTime và cancelled; chưa thay kiểm tra quyền/trạng thái ticket.
- [ ] 3. GET /event-categories đọc danh mục seed; search tên/category/date, sort start hoặc min price theo allowlist, page ổn định có ID; filter thời gian UTC từ input Asia/Ho_Chi_Minh được chuyển rõ.
- [ ] 4. Endpoint nội bộ trả DRAFT/REJECTED đúng org, public không lộ chúng; cancelled vẫn xem được trong lịch sử chủ đơn về sau.
- [ ] 5. Chạy tuần 1: register/verify → organization approve → member → create/upload/submit/publish → public query; ghi backlog blockers trước ngày 8.

**Kiểm chứng bắt buộc:**

- [ ] Event không đơn/khu hết vé có số tồn đúng, không join nhân dòng.
- [ ] F07 mốc mở nhận và đúng end từ chối; SQL injection trong sort bị validation.

**Điều kiện hoàn thành:** Tuần 1 có backend quản trị sự kiện và catalog thật. Ghi case và kết quả trong `docs/evidence/day-07.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day07IT` và `database/tests/day-07.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day07IT verify
```
