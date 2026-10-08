# Ngày 10 — Khởi tạo VNPAY và xác thực kết quả cổng

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Chủ nhật, 2026-10-04. **Ước lượng tạm của kế hoạch ban đầu:** 16 giờ công tổng của nhóm.

**Mục tiêu:** Tạo Payment an toàn, sinh URL sandbox, xác thực callback/query; browser không tự báo trả tiền thành công.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §6.6, §9.4, §10 VNPAY, §14 SP08/TX08. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D09; credential VNPAY Sandbox do chủ dự án cung cấp; đọc tài liệu chính thức khi thực hiện vì giao thức có thể thay đổi.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D10-T01 — SP08 đóng băng lần thanh toán

**Phụ trách đề xuất:** B. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D10_01_begin_payment.sql; JAVA/repository/order/PaymentRepository.java; SQLTEST/day-10.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_BeginOrderPayment(orderId,actorId) → paymentId/txnRef/amount hoặc chỉ dẫn đơn 0đ.

**Phụ thuộc của task:** D09 Order/coupon và D08 Hold.

**Cách thực hiện:**

- [ ] 1. Khóa Event/Order/Hold, kiểm owner, cửa bán/hold còn hiệu lực và số tiền tính trong DB.
- [ ] 2. Còn PENDING/UNKNOWN trả lần hiện tại để theo dõi; chỉ FAILED đã xác định cho tạo lần mới khi Hold còn hạn.
- [ ] 3. Gọi Payment.start(order, txnRef) với txnRef unique, lưu Payment PENDING có order bắt buộc và amount snapshot bất biến từ totalAmount; zero total không tạo Payment, chuyển use case hoàn tất miễn phí của ngày 11.
- [ ] 4. Không gọi VNPAY từ SP; commit trước khi tạo/chuyển URL phía integration.
- [ ] 5. TX08 gây lỗi sau insert Payment, rollback không để payment dở; hai caller đồng thời chỉ một lần đang xử lý.

**Kiểm chứng bắt buộc:**

- [ ] Refresh/nhấn pay hai lần dùng cùng paymentId/txnRef.
- [ ] Hold expired hoặc tổng 0 không tạo Payment amount=0; nhiều attempt cùng Order giữ lịch sử, acceptedPayment chưa được chọn đến khi SP09 chấp nhận CAPTURED.

**Điều kiện hoàn thành:** SP08 sở hữu khởi tạo, amount/coupon không đổi trong lần chờ. Ghi case và kết quả trong `docs/evidence/day-10.md`.

## D10-T02 — Adapter VNPAY ký request và kiểm tra chữ ký

**Phụ trách đề xuất:** A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/integration/payment/PaymentGateway.java; JAVA/integration/payment/VnpaySandboxGateway.java; TEST/integration/payment/VnpaySignatureTest.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Gateway tạo URL/query và trả kết quả đã xác thực cho Service; không sửa Order trực tiếp.

**Phụ thuộc của task:** D01 nhu cầu sandbox và credential; test chữ ký không chờ SP08.

**Cách thực hiện:**

- [ ] 1. Đọc tài liệu chính thức về canonicalization, encoding, sign fields, số tiền/đơn vị, timezone và response codes; lưu phiên bản/link ngày tra.
- [ ] 2. Viết test vector từ tài liệu với merchant test; dùng secret test ngoài Git, so sánh chữ ký an toàn.
- [ ] 3. Build URL từ cấu hình allowlist host sandbox/base URL, không dùng returnUrl tùy ý của client.
- [ ] 4. Verify chữ ký và merchant, txnRef, amount, currency, transaction status; reject duplicate/malformed parameters có thể gây diễn giải khác nhau.
- [ ] 5. Query giao dịch dùng timeout hữu hạn; network timeout là UNKNOWN, không đoán FAILED.

**Kiểm chứng bắt buộc:**

- [ ] Thay một chữ số amount/txnRef hoặc signature bị từ chối.
- [ ] Unicode, khoảng trắng, ký tự +/% và thứ tự tham số không làm ký sai theo tài liệu.

**Điều kiện hoàn thành:** Adapter xác minh trả data typed để ngày 11 ghi kết quả. Ghi case và kết quả trong `docs/evidence/day-10.md`.

## D10-T03 — Controller payment/return/IPN và boundary quyền

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/controller/order/PaymentServlet.java; JAVA/controller/order/VnpayCallbackServlet.java; JAVA/service/order/PaymentService.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST /orders/{id}/payments; GET /payments/vnpay/return; GET /payments/vnpay/ipn; GET /orders/{id}/payment-status.

**Phụ thuộc của task:** Contract D10-T01/T02; result sink SP09 nghiệm thu đầy đủ ở D11.

**Cách thực hiện:**

- [ ] 1. User endpoint dùng auth/CSRF/owner, gọi SP08 rồi gateway sau commit; URL chỉ được trả sau Payment đã lưu.
- [ ] 2. Return chỉ đọc trạng thái theo chủ đơn/session; query string báo success không cập nhật database.
- [ ] 3. IPN không dùng cookie/CSRF nhưng bắt buộc signature; xác minh đầy đủ trước gọi use case ApplyPaymentResult của ngày 11.
- [ ] 4. Trong ngày 10 kiểm wiring bằng gateway/service test double tại test; không bật xử lý IPN thành công production trước khi SP09 sẵn sàng.
- [ ] 5. Không nhận verifiedResult/principal kỹ thuật từ public body; callback chỉ đi qua kết quả verifier hợp lệ.

**Kiểm chứng bắt buộc:**

- [ ] Browser gọi return giả không chuyển PAID.
- [ ] IPN giả không đổi Payment, không tạo ticket; response IPN theo protocol đã tra.

**Điều kiện hoàn thành:** HTTP routes và cơ chế xác thực kết quả sẵn sàng ghép SP09. Ghi case và kết quả trong `docs/evidence/day-10.md`.

## D10-T04 — Sandbox smoke và lỗi không rõ kết quả

**Phụ trách đề xuất:** C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day10IT.java; docs/backend/vnpay.md; docs/evidence/day-10.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Sandbox handshake thật nếu credential có; các kết quả tiền cuối cùng nghiệm thu ngày 11.

**Phụ thuộc của task:** D10-T01/T02/T03 và quyền sandbox; ngày 10 chưa nghiệm thu phát hành.

**Cách thực hiện:**

- [ ] 1. Tạo Payment qua API thật và mở/gọi URL sandbox phù hợp để xác nhận merchant/return/IPN cấu hình đúng.
- [ ] 2. Test callback đến hai lần, callback sai số tiền và timeout query ở integration fake; không gọi fake là kết nối thật.
- [ ] 3. Kiểm transaction/DB locks đã kết thúc trước external HTTP; đo pool khi gateway timeout.
- [ ] 4. Ghi cách chuẩn bị HTTPS callback và recovery khi host ngủ; đăng ký sandbox chỉ khi chủ dự án cho phép.
- [ ] 5. Credential thiếu: ghi phần sandbox blocked, vẫn hoàn thiện signature test/SQL; không hard-code khóa hoặc tự mô phỏng status thành PAID.

**Kiểm chứng bắt buộc:**

- [ ] TX08 pass với hai session; service không giữ connection trong timeout gateway.
- [ ] Evidence phân biệt rõ sandbox thật với local adapter test.

**Điều kiện hoàn thành:** Hợp đồng thanh toán hoàn chỉnh để ngày 11 phát hành. Ghi case và kết quả trong `docs/evidence/day-10.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day10IT` và `database/tests/day-10.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day10IT verify
```
