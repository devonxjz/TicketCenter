# Ngày 11 — Ghi nhận thu tiền, phát hành vé và QR

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ hai, 2026-10-05. **Ước lượng tạm của kế hoạch ban đầu:** 19 giờ công tổng của nhóm.

**Mục tiêu:** Payment hợp lệ phát hành đúng số vé; thu muộn/trùng tạo bù trừ; hỗ trợ đơn 0đ và chia sẻ QR từng vé.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §6.6–6.8, §14 SP09/F06/V06/TR08/TX09. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D10 gateway/SP08; D08 hold; D09 money; schema Refund/outbox sẵn, xử lý hoàn bù trừ sẽ nối SP11 ngày 15.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D11-T01 — F06 phân bổ số tiền từng vé

**Phụ trách đề xuất:** B+A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D11_01_ticket_allocation.sql; JAVA/model/order/Order.java; TEST/order/TicketAllocationTest.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** fn_AllocateTicketPaidAmounts(orderId) → orderItemId, thứ tự vé, discount,paidAmount; tối đa 8 dòng.

**Phụ thuộc của task:** D09 Order/money; có thể làm song song adapter VNPAY cuối D10.

**Cách thực hiện:**

- [ ] 1. Mở rộng mỗi standing quantity thành từng quyền vào cửa, seat thành một dòng; sort ổn định theo orderItemId và ordinal.
- [ ] 2. Tính tỷ lệ discount theo giá gốc, lấy phần nguyên và phát đồng lẻ theo phần dư lớn nhất; tie-break giống Java.
- [ ] 3. Order toàn vé 0đ trả paidAmount=0, không chia cho 0; đầu vào/tổng sai phải bị caller chặn trước phát hành.
- [ ] 4. Viết chung test vector: 200000+300000 giảm 100000 → 160000+240000; ba vé cùng giá có đồng lẻ; mixed zero/nonzero.
- [ ] 5. Assert SUM paidAmount=Order.totalAmount và không âm; đổi thứ tự query không được thay phân bổ khi ID không đổi.

**Kiểm chứng bắt buộc:**

- [ ] Trường hợp 3 vé giá 1, discount=0 do trần floor trả tổng 3; case giá 10 mỗi vé, discount=1 phân bổ ổn định.
- [ ] Java/SQL dùng cùng rounding/tie-break và ra cùng số tiền.

**Điều kiện hoàn thành:** F06 đủ chính xác cho refund theo vé về sau. Ghi case và kết quả trong `docs/evidence/day-11.md`.

## D11-T02 — SP09/TX09 phát hành và bù trừ

**Phụ trách đề xuất:** B. **Giờ công:** 6h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D11_02_payment_result.sql; SQLTEST/day-11.sql; JAVA/repository/order/PaymentRepository.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_ApplyPaymentResult(orderId,actorIdOrNull,paymentIdOrNull,verifiedResult,ticketCodes,compensationAttemptIdOrNull,retryFailedCompensation=false); chỉ worker/integration ghi có tiền, buyer chỉ nhánh 0đ được DB kiểm tra.

**Phụ thuộc của task:** D10 verified gateway/SP08, D08 SP07, D11-T01; schema Refund/outbox D02.

**Cách thực hiện:**

- [ ] 1. Khóa Event/Order/Hold/Payment/kho theo locking.md; đối chiếu liên kết và số tiền dù integration đã xác minh chữ ký.
- [ ] 2. Thu hợp lệ còn hạn/còn bán: Payment CAPTURED/paidAt, Order PAID/acceptedPayment/paidAt, Hold CONSUMED, kho sold, coupon CONSUMED, insert từng Ticket từ F06 và outbox cùng transaction.
- [ ] 3. Thu muộn, Hold đã release, Event cancelled/qua saleEnd hoặc lần thu khác cho đơn đã PAID: lưu CAPTURED và một Refund PAYMENT_COMPENSATION/APPROVED gắn Payment, amount toàn khoản thu, không Ticket. Backend có thể beginAttempt bằng compensationAttemptId và lưu Refund PROCESSING và log PENDING, hoặc giữ APPROVED để worker tiếp tục; adapter sau commit. retryFailedCompensation mặc định false, chỉ thử ID mới trên cùng Refund sau FAILED đã xác minh.
- [ ] 4. Callback lặp cùng Payment đã xử lý trả kết quả cũ, không coi đó là thu trùng mới; kết quả FAILED cũ không hạ CAPTURED.
- [ ] 5. Nhánh 0đ kiểm principal buyer, owner, paymentId NULL, total=0 và Hold/Event hợp lệ; không Payment giả. ticketCodes do backend sinh đủ/unique, không nhận từ browser.

**Kiểm chứng bắt buộc:**

- [ ] Hai IPN cùng txnRef chỉ một bộ vé; hai Payment CAPTURED khác nhau chỉ một doanh thu vé, còn lại bù trừ.
- [ ] TX09 lỗi vé thứ N rollback cả Payment/Order/hold/kho/coupon/outbox; outer JPA rollback đúng.

**Điều kiện hoàn thành:** SP09 được IPN/query thật gọi và không có public endpoint tự đánh dấu success. Ghi case và kết quả trong `docs/evidence/day-11.md`.

## D11-T03 — Vé, QR và chia sẻ ảnh có kiểm soát

**Phụ trách đề xuất:** A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/service/fulfillment/TicketService.java; JAVA/controller/fulfillment/TicketServlet.java; JAVA/integration/qr/QrEncoder.java; SQL/D11_03_ticket_reads.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** V06/TR08; GET /me/tickets, /tickets/{id}, /tickets/{id}/qr; chọn chia sẻ ảnh QR theo SPEC để không cần thêm share-token lifecycle.

**Phụ thuộc của task:** D11-T01/T02 để có vé thật; library QR đã duyệt, V06/TR08 chuẩn bị trước.

**Cách thực hiện:**

- [ ] 1. Sinh ticketCode bằng nguồn ngẫu nhiên an toàn, unique DB; QrEncoder qua thư viện đã duyệt chỉ mã hóa code, không truy cập DB/HTTP trong entity.
- [ ] 2. V06 trả chi tiết snapshot và paidAmount nhưng không code/QR; query theo owner trước lấy dữ liệu nhạy cảm.
- [ ] 3. QR endpoint yêu cầu owner, trả ảnh cho đúng một ticket với cache policy phù hợp; người mua tải/chia sẻ ảnh ra ngoài, không mở link cả đơn.
- [ ] 4. TR08 chặn đổi orderItemId/code/issuedAt/paidAmount của ticket đã phát hành; cho status theo luồng nghiệp vụ, trigger không tự phát hành.
- [ ] 5. Sinh QR ở adapter/hiển thị từ ticketCode ngẫu nhiên; Kiểm Order.markPaid(payment, now) chọn CAPTURED cùng Order; markPaid(null, now) chỉ totalAmount=0, luôn paidAt. Email lên outbox trong transaction, gửi sau commit qua worker ngày 12.

**Kiểm chứng bắt buộc:**

- [ ] Vé standing quantity=3 có ba QR khác nhau.
- [ ] User khác không đọc ảnh/chi tiết; TR08 lệnh nhiều dòng sửa một snapshot phải rollback.

**Điều kiện hoàn thành:** Backend trả vé/ảnh QR dùng được bằng HTTP, không cần frontend. Ghi case và kết quả trong `docs/evidence/day-11.md`.

## D11-T04 — Ghép toàn bộ luồng và race hạn giữ

**Phụ trách đề xuất:** C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day11IT.java; TEST/concurrency/PaymentRaceIT.java; docs/evidence/day-11.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** IPN verified → Service → SP09 → ticket; return chỉ đọc; late compensation chưa tự xử lý tiền trước ngày 15.

**Phụ thuộc của task:** D11-T01/T02/T03; sandbox D10 và release SP07.

**Cách thực hiện:**

- [ ] 1. Chạy sandbox thành công đúng số tiền, lấy ticket count/paidAmount/inventory/coupon từ DB đối chiếu.
- [ ] 2. Chạy callback ngay sau expiresAt dù provider timestamp trước hạn: không ticket, có compensation.
- [ ] 3. Barrier giữa SP07 release và SP09 accept; không kết quả vừa bán vừa release; test Event cancellation fixture và callback.
- [ ] 4. Gọi SP09 trực tiếp buyer với paymentId có tiền/total>0/actor khác và xác nhận DB từ chối.
- [ ] 5. Kiểm outbox chỉ tồn tại khi transaction committed; failure email không đảo PAID. Nhánh 0đ phát hành đầy đủ mà không gọi gateway.

**Kiểm chứng bắt buộc:**

- [ ] Tổng ticket paidAmount bằng total ở mọi case; thu bù trừ không thành doanh thu.
- [ ] Chỉ đánh dấu thu thật sandbox khi có bằng chứng cổng; fake test ghi rõ.

**Điều kiện hoàn thành:** Mốc quan trọng: backend mua và nhận vé end-to-end hoạt động. Ghi case và kết quả trong `docs/evidence/day-11.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day11IT` và `database/tests/day-11.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day11IT verify
```
