# Ngày 08 — Giữ vé nguyên tử và giải phóng khi hủy/hết hạn

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ sáu, 2026-10-02. **Ước lượng tạm của kế hoạch ban đầu:** 17 giờ công tổng của nhóm.

**Mục tiêu:** Giữ được tối đa 8 vé cùng Event trong 10 phút, không oversell và chỉ một Hold ACTIVE mỗi user.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §6.4, §8.2–8.4, §14 SP02/SP07/TX02/TX07. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D07 Event/kho công khai; D03 transaction; phải review locking.md cho Hold cũ ở Event khác trước cài SP.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D08-T01 — SP07: giải phóng Hold đúng một lần

**Phụ trách đề xuất:** B. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D08_01_release_hold.sql; SQLTEST/day-08.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_ReleaseTicketHold(holdId,actorIdOrNull,reason) → trạng thái Hold/Order; buyer chỉ hủy Hold của mình, worker chỉ lý do hệ thống hợp lệ.

**Phụ thuộc của task:** D07 Event/kho; bảng Order/redemption D02; locking.md đã review.

**Cách thực hiện:**

- [ ] 1. Viết test với Hold ACTIVE gồm một ghế, quantity đứng và redemption RESERVED; sau release ghế AVAILABLE, standingHeld giảm, redemption RELEASED.
- [ ] 2. Khóa Event/Order/Hold/kho/Coupon theo hợp đồng chung, xác thực lý do: chủ hủy, hết hạn hoặc Event CANCELLED.
- [ ] 3. Chỉ ACTIVE được release; RELEASED trả kết quả cũ, CONSUMED không trả kho. Order chưa trả cập nhật CANCELLED/EXPIRED tương ứng.
- [ ] 4. Không chuyển Payment PENDING/UNKNOWN thành FAILED do thời hạn Hold; không trả redemption CONSUMED.
- [ ] 5. TX07 gây lỗi sau trả ghế trước trả coupon để rollback; cấp EXECUTE cho buyer/worker theo kiểm tra trong SP.

**Kiểm chứng bắt buộc:**

- [ ] Hai phiên worker/chủ đơn cùng release chỉ giảm standingHeld một lần.
- [ ] Release một Hold CONSUMED không tăng kho; lỗi giữa chừng khôi phục cả kho lẫn coupon.

**Điều kiện hoàn thành:** SP07 là đường ghi release dùng chung cho HTTP, expire worker, SP02 và SP17. Ghi case và kết quả trong `docs/evidence/day-08.md`.

## D08-T02 — SP02: giữ toàn bộ lựa chọn

**Phụ trách đề xuất:** B+A. **Giờ công:** 6h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D08_02_create_hold.sql; JAVA/model/ticketing/; JAVA/repository/ticketing/HoldRepository.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_CreateTicketHold(userId,eventId,selections) → holdId,expiresAt,items snapshot; thời gian do server/DB.

**Phụ thuộc của task:** D08-T01 vì dọn Hold cũ gọi SP07.

**Cách thực hiện:**

- [ ] 1. Parse selections JSON hoặc table parameter đã thử driver; reject rỗng, trùng ghế, quantity không nguyên, tổng ngoài 1–8, Zone/Seat sai Event hoặc sai loại.
- [ ] 2. Kiểm User ACTIVE/verified, Event PUBLISHED và trong sale window; khóa User và tập Event liên quan, phát hiện Hold cũ an toàn.
- [ ] 3. Nếu Hold cũ quá hạn gọi SP07 trong cùng transaction; nếu còn hạn báo xung đột và trả thông tin tối thiểu để chủ hủy chủ động.
- [ ] 4. Khóa kho theo thứ tự ID; SEATED tất cả phải AVAILABLE, STANDING held+sold+requested<=capacity; chụp giá Zone và đặt TTL đúng 10 phút.
- [ ] 5. Insert Hold/Items rồi cập nhật kho nguyên tử; không trả một phần nếu một item thiếu. Kiểm Model Seat.hold/TicketHold theo diagram trên dữ liệu không dirty-write trùng SP.

**Kiểm chứng bắt buộc:**

- [ ] Hai buyer tranh một ghế chỉ một thắng; hai request capacity 3 cùng đòi 2 không bán quá 3.
- [ ] Hai thiết bị cùng user chọn hai Event không có hai ACTIVE; rollback item cuối trả nguyên trạng item đầu.

**Điều kiện hoàn thành:** SP02/TX02 có test concurrency thật, filtered index là chốt bảo vệ thêm. Ghi case và kết quả trong `docs/evidence/day-08.md`.

## D08-T03 — HTTP giữ/hủy/xem Hold hiện hành

**Phụ trách đề xuất:** A. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/service/ticketing/HoldService.java; JAVA/controller/ticketing/HoldServlet.java; TEST/ticketing/HoldTest.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST /holds {eventId,selections}; GET /me/hold; POST /holds/{id}/cancel.

**Phụ thuộc của task:** Hợp đồng SP02/SP07 để viết Java; D08-T01/T02 để chạy tích hợp.

**Cách thực hiện:**

- [ ] 1. Controller validate shape/UUID trước Service; không nhận giá, actorId hay expiresAt từ client.
- [ ] 2. Service dùng buyer principal sau kiểm verified; gọi SP02/SP07 cùng transaction boundary đã có.
- [ ] 3. DTO trả serverNow/expiresAt, giá giữ và trạng thái; mọi lần đọc/tiếp tục use case kiểm hiệu lực thật.
- [ ] 4. Lỗi 409 trả mã hết chỗ/đã có Hold; không lộ thông tin buyer đang giữ ghế khác.
- [ ] 5. Mất response sau create: client tra GET /me/hold, backend kiểm Hold hiện hành/lịch sử thay vì tự tạo lượt mới; ghi cách đối chiếu lựa chọn.

**Kiểm chứng bắt buộc:**

- [ ] Reload và GET không gia hạn; người chưa verify bị chặn trước giữ kho.
- [ ] Hủy Hold user khác trả 404; body expiresAt xa tương lai không ảnh hưởng TTL.

**Điều kiện hoàn thành:** Có API giữ vé đầy đủ mà không cần sơ đồ ghế frontend. Ghi case và kết quả trong `docs/evidence/day-08.md`.

## D08-T04 — TX02/TX07 và mốc hết hạn

**Phụ trách đề xuất:** C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day08IT.java; TEST/concurrency/HoldConcurrencyIT.java; docs/evidence/day-08.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Hai connection, barrier, timeout; assert inventory + Hold + Order/redemption cùng lúc.

**Phụ thuộc của task:** D08-T01/T02/T03; concurrency harness D02/D03.

**Cách thực hiện:**

- [ ] 1. Chạy case ở expiresAt−1 tick, đúng expiresAt và sau expiresAt; now>=expiresAt phải hết hạn.
- [ ] 2. Cho một Hold cũ Event A quá hạn, user giữ Event B; kiểm dọn A và giữ B nguyên tử, lỗi B rollback cả transaction theo thiết kế.
- [ ] 3. Thử duplicate selections cùng ghế và quantity overflow, Event vừa hết bán/cancelled fixture.
- [ ] 4. Gây lỗi thật sau mutation bằng fixture constraint/trigger chỉ ở test DB; không thêm endpoint production để inject lỗi.
- [ ] 5. Lưu timeline hai session, thời điểm chờ, kết quả thắng/thua và dữ liệu cuối; bỏ khóa test và xác nhận request sau vẫn chạy.

**Kiểm chứng bắt buộc:**

- [ ] Không âm held/sold, không ghế vừa AVAILABLE vừa thuộc Hold ACTIVE hiệu lực.
- [ ] Bộ test có lỗi sau mutation, không chỉ input validation đầu SP.

**Điều kiện hoàn thành:** Giữ và giải phóng đạt invariant trước khi thêm Order. Ghi case và kết quả trong `docs/evidence/day-08.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day08IT` và `database/tests/day-08.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day08IT verify
```
