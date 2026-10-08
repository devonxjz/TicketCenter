# Ngày 12 — Worker, đối chiếu thanh toán và email bền vững

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ ba, 2026-10-06. **Ước lượng tạm của kế hoạch ban đầu:** 15 giờ công tổng của nhóm.

**Mục tiêu:** Restart không mất việc dọn Hold, đối chiếu Payment và gửi email; xử lý có lease, retry hữu hạn và log an toàn.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §8.4, §10, §11.2, §14 SP07/SP09. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D08 SP07, D10 gateway, D11 SP09/outbox, D05 email.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D12-T01 — Nhận việc outbox và vòng đời worker

**Phụ trách đề xuất:** B+A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D12_01_outbox_claim.sql; JAVA/job/OutboxWorker.java; JAVA/config/WorkerListener.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Outbox kỹ thuật với status/leaseUntil/attempts/nextAttemptAt/dedupKey; một worker trong ứng dụng, không hệ thống queue mới.

**Phụ thuộc của task:** Outbox schema D02, events D11, transaction/principal D03.

**Cách thực hiện:**

- [ ] 1. Viết claim batch bằng cập nhật có khóa/OUTPUT trong transaction ngắn, chỉ lấy pending đến hạn hoặc lease hết; không select rồi update không khóa.
- [ ] 2. Commit claim trước external I/O; worker ghi success/failure có kiểm tra lease owner để không ghi kết quả của lease đã mất.
- [ ] 3. Dùng scheduler có lifecycle startup/shutdown; shutdown dừng nhận việc, xử lý đang chạy có timeout; không thread rò qua redeploy.
- [ ] 4. Backoff có giới hạn và trạng thái cần vận hành khi hết retry; phân biệt retry vận chuyển với retry nghĩa vụ hoàn tiền mới.
- [ ] 5. Test hai worker tranh một batch và crash sau claim; lease hết cho nhận lại, khóa idempotency bảo vệ effect phía Service/SP.

**Kiểm chứng bắt buộc:**

- [ ] Hai worker không cùng sở hữu một lease còn hiệu lực.
- [ ] Crash sau effect trước ack không nhân ticket hoặc trả kho vì SP idempotent.

**Điều kiện hoàn thành:** Có worker kỹ thuật dùng lại cho email/compensation/cancellation. Ghi case và kết quả trong `docs/evidence/day-12.md`.

## D12-T02 — Dọn Hold và truy vấn Payment UNKNOWN

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/job/HoldExpiryJob.java; JAVA/job/PaymentReconciliationJob.java; JAVA/service/order/PaymentService.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Job gọi SP07 và gateway query→SP09; không tự UPDATE payment/hold qua JPA.

**Phụ thuộc của task:** D12-T01 scheduling, D08 SP07, D10 gateway, D11 SP09.

**Cách thực hiện:**

- [ ] 1. Quét Hold ACTIVE quá hạn theo batch/index, gọi SP07 từng giao dịch ngắn; request giữ/mua vẫn tự kiểm hạn không phụ thuộc job.
- [ ] 2. Quét Payment PENDING lâu/UNKNOWN, query cổng bằng txnRef, xác minh result như IPN trước SP09.
- [ ] 3. Timeout query giữ UNKNOWN/đang theo dõi, ghi next retry; không tạo Payment mới và không gia hạn Hold.
- [ ] 4. Thu xác nhận sau release đi bù trừ; retry failed compensation chỉ được vận hành cho phép, không bật cờ do worker redelivery.
- [ ] 5. Ghi metric số pending/oldest age/failures đã lọc; không polling để cố giữ Render thức.

**Kiểm chứng bắt buộc:**

- [ ] Tắt worker, Hold hết hạn vẫn không mua được; bật lại dọn đúng một lần.
- [ ] Query UNKNOWN→CAPTURED sau hạn tạo compensation, không ticket mới.

**Điều kiện hoàn thành:** Khôi phục kết quả thu tiền sau callback chậm/host ngủ. Ghi case và kết quả trong `docs/evidence/day-12.md`.

## D12-T03 — Email vé và dọn ảnh qua outbox

**Phụ trách đề xuất:** C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/job/EmailJob.java; JAVA/job/ImageCleanupJob.java; JAVA/integration/mail/; TEST/job/EmailJobIT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Email vé chứa thông tin/link owner-auth tới vé; email refusal/refund gắn khi ngày 14–15 hoàn thiện.

**Phụ thuộc của task:** D12-T01, D05 MailGateway, D07 storage và D11 event email.

**Cách thực hiện:**

- [ ] 1. Outbox payload chỉ ID/template/context tối thiểu, không OTP/raw QR/password; worker đọc data cần gửi sau commit.
- [ ] 2. Tạo khóa gửi theo sự kiện nghiệp vụ, retry provider lỗi transient; không cam kết exactly-once nếu nhà cung cấp không hỗ trợ dedup.
- [ ] 3. Email tiếng Việt, VND, giờ Asia/Ho_Chi_Minh; link owner login theo APP_BASE_URL, không công khai toàn Order.
- [ ] 4. Dọn ảnh chỉ khi không còn Event nào tham chiếu URL/key và qua grace period; recheck ngay trước delete; lỗi dọn không rollback thay ảnh.
- [ ] 5. Viết test gửi chậm, provider timeout, invalid recipient và restart; evidence không chứa người nhận thật.

**Kiểm chứng bắt buộc:**

- [ ] Email lỗi nhưng Order/Ticket vẫn PAID/ACTIVE.
- [ ] Ảnh đang dùng không bị xóa bởi job từ lần thay trước; OTP không đi queue vé.

**Điều kiện hoàn thành:** Vé tới email theo dõi được, storage cleanup không mất ảnh đang dùng. Ghi case và kết quả trong `docs/evidence/day-12.md`.

## D12-T04 — Diễn tập restart và tuần 2 giữa kỳ

**Phụ trách đề xuất:** C+B. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day12IT.java; TEST/recovery/WorkerRecoveryIT.java; docs/backend/operations.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Runbook tra Payment/outbox/hold khi lỗi; không thao tác sửa tiền trực tiếp trong SSMS.

**Phụ thuộc của task:** D12-T01/T02/T03; deployment local từ D01.

**Cách thực hiện:**

- [ ] 1. Dừng ứng dụng ở ba điểm: sau claim, sau provider response, sau commit nghiệp vụ trước response; restart theo runbook.
- [ ] 2. Assert số Ticket/redemption/Refund compensation không tăng trùng, Hold được release đúng.
- [ ] 3. Test DB mất kết nối/pool cạn/job timeout có giới hạn; health readiness 503 nhưng liveness không lộ nguyên nhân bí mật.
- [ ] 4. Ghi thao tác admin kiểm tra pending/UNKNOWN, cách đối chiếu trước retry và correlationId để tra log.
- [ ] 5. Chạy lại Day08IT–Day11IT vì worker chạm các đường ghi chung; ghi kết quả mới, không dùng kết quả cũ.

**Kiểm chứng bắt buộc:**

- [ ] Sau restart tiếp tục việc còn dở và không loop vô hạn.
- [ ] Lịch sử FAILED/UNKNOWN được giữ, không xóa để làm dashboard sạch.

**Điều kiện hoàn thành:** Nền vận hành đủ cho refund/cancel tuần 3. Ghi case và kết quả trong `docs/evidence/day-12.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day12IT` và `database/tests/day-12.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day12IT verify
```
