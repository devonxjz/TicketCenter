# Ngày 14 — Yêu cầu hoàn vé và quyết định của admin

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ năm, 2026-10-08. **Ước lượng tạm của kế hoạch ban đầu:** 18 giờ công tổng của nhóm.

**Mục tiêu:** Chủ đơn chọn vé để hoàn; admin duyệt/từ chối; vé 0đ hoàn tất không tạo giao dịch tiền giả.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §6.9, §14 SP05/SP10/F04/V07/TX05/TX10. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D11 Ticket/paidAmount; D13 check-in; D12 outbox; xử lý provider refund có tiền nối ngày 15.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D14-T01 — F04, SP05 và một yêu cầu mở mỗi vé

**Phụ trách đề xuất:** B. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D14_01_refund_request.sql; SQLTEST/day-14.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** F04 refundable tickets; usp_RequestTicketRefund(orderId,actorId,ticketIds,reasonType,reason) → refundId/amount; buyer chỉ CUSTOMER_REQUEST.

**Phụ thuộc của task:** D11 ticket/paidAmount, D13 check-in và refund schema.

**Cách thực hiện:**

- [ ] 1. F04 trả Ticket ACTIVE, trước startTime, chưa request mở; endpoint kiểm owner trước đọc, SP kiểm lại sau khóa.
- [ ] 2. SP05 reject danh sách rỗng/trùng/khác Order, USED/inactive, đúng hoặc sau startTime; all-or-nothing.
- [ ] 3. Gọi Refund.requestForTickets: tạo Refund REQUESTED/CUSTOMER_REFUND, reasonType=CUSTOMER_REQUEST, amount=tổng paidAmount kể cả 0đ; payment=Order.acceptedPayment hoặc null chỉ 0đ. Bảng nối giữ dấu open; vé REFUND_PENDING cùng transaction.
- [ ] 4. Không tính lại giá/coupon hiện tại; không nhận amount hoặc reasonType EVENT_CANCELLATION từ buyer.
- [ ] 5. TX05 inject lỗi item cuối/bảng nối, rollback cả request/status; race hai yêu cầu cùng vé và race check-in chỉ một chuyển hợp lệ.

**Kiểm chứng bắt buộc:**

- [ ] Một vé không có hai open request; unique index và khóa đều được kiểm chứng.
- [ ] 200000+300000 giảm100000: hoàn vé thứ nhất yêu cầu160000 dù giá hiện tại đã đổi.

**Điều kiện hoàn thành:** SP05/TX05 chặn check-in ngay khi yêu cầu được gửi. Ghi case và kết quả trong `docs/evidence/day-14.md`.

## D14-T02 — SP10 duyệt/từ chối và hoàn 0đ

**Phụ trách đề xuất:** B+A. **Giờ công:** 6h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D14_02_refund_decision.sql; JAVA/model/fulfillment/Refund.java; JAVA/persistence/RefundTransferLog.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_DecideRefund(refundId,actorIdOrNull,decision,rejectionReasonOrNull,attemptIdOrNull,retryFailed=false); admin quyết Refund REQUESTED của khách, system chỉ EVENT_CANCELLATION hợp lệ qua SP17.

**Phụ thuộc của task:** D14-T01 request; D12 outbox; schema Payment/Refund đã có.

**Cách thực hiện:**

- [ ] 1. Khóa Event/Order/Refund/tập Ticket theo hợp đồng; Refund REQUESTED của khách chỉ admin quyết định, reject lưu rejectionReason/audit riêng.
- [ ] 2. Từ chối khôi phục ACTIVE chỉ khi Event chưa cancelled, đóng dấu open, outbox thông báo; không thay reason khách bằng lý do admin.
- [ ] 3. Duyệt cùng Refund → APPROVED; amount>0 gọi beginAttempt(attemptId backend sinh), đặt currentAttemptId/PROCESSING và ghi RefundTransferLog PENDING/outbox nguyên tử. Không tạo Refund thứ hai, chưa đánh Ticket REFUNDED; adapter chạy sau commit.
- [ ] 4. Refund 0đ gọi completeWithoutTransfer → COMPLETED, hoàn tất vé/trả kho/đóng open nguyên tử; vẫn giữ Refund, không Payment/RefundTransferLog hoặc processedAt chuyển tiền giả. Dùng cùng quy tắc kho với SP11.
- [ ] 5. Lặp quyết định trả cùng Refund/currentAttemptId, không attempt mới; trái quyết định conflict. retryFailed chỉ có quyền vận hành, Refund RETRYABLE và lần cũ FAILED đã xác minh; dùng attemptId mới trên cùng nghĩa vụ. PROCESSING/NEEDS_RECONCILIATION/COMPLETED không được thử mới.

**Kiểm chứng bắt buộc:**

- [ ] TX10 lỗi tạo RefundTransferLog rollback APPROVED/currentAttemptId; approve hai lần vẫn một Refund và một attempt hiện hành. 0đ lặp không trả kho hai lần.
- [ ] Reject đua Event cancel không khôi phục vé Event đã hủy; worker không tự duyệt Event chưa cancelled.

**Điều kiện hoàn thành:** SP10 có đầy đủ zero/reject/approve/retry guard, luồng tiền chờ ngày 15. Ghi case và kết quả trong `docs/evidence/day-14.md`.

## D14-T03 — API refund và V07 tổng hợp không nhân tiền

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D14_03_refund_reads.sql; JAVA/controller/fulfillment/RefundServlet.java; JAVA/service/fulfillment/RefundService.java; JAVA/repository/fulfillment/RefundRepository.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** GET /orders/{id}/refundable-tickets; POST /refund-requests; GET /me/refund-requests; GET/POST /admin/refund-requests/{id}/decision.

**Phụ thuộc của task:** Contract D14-T01/T02; V07 chuẩn bị song song từ schema.

**Cách thực hiện:**

- [ ] 1. DTO chọn ticketIds/reason, actor/session; admin decision APPROVE/REJECT mapping rõ sang SP10 và API-MAP.
- [ ] 2. V07 dbo.vw_RefundOverview có một dòng/Refund; aggregate tập Ticket và RefundTransferLog riêng rồi join, không nhân amount khi nhiều lần thử.
- [ ] 3. Trả refundId, amount, purpose, reasonType, reason, rejectionReason/status từ Refund, currentAttemptId và trạng thái log kỹ thuật; AuditLog giữ lịch sử quyết định.
- [ ] 4. Refund COMPLETED 0đ hiển thị hoàn tất từ nghĩa vụ có amount=0 dù không attempt; PROCESSING/NEEDS_RECONCILIATION/RETRYABLE không gọi là đã hoàn.
- [ ] 5. Kết nối email rejection với EmailJob và idempotency key; email lỗi không khôi phục ticket/request.

**Kiểm chứng bắt buộc:**

- [ ] User chỉ xem request của mình; manager không được duyệt dù Event của tổ chức.
- [ ] Hai log FAILED và một SUCCEEDED sau này vẫn một dòng/amount nghĩa vụ ở V07.

**Điều kiện hoàn thành:** API đủ cho khách/admin theo dõi hoàn tiền không cần UI. Ghi case và kết quả trong `docs/evidence/day-14.md`.

## D14-T04 — Nghiệm thu tuần 2 và race refund/check-in

**Phụ trách đề xuất:** C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day14IT.java; TEST/concurrency/RefundConcurrencyIT.java; docs/evidence/day-14.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Chuỗi từ mua vé đến quét hoặc gửi hoàn được kiểm tra qua HTTP/SQL thật.

**Phụ thuộc của task:** D14-T01/T02/T03; SP04 từ D13 để race thật.

**Cách thực hiện:**

- [ ] 1. Barrier SP04 và SP05 cùng ACTIVE ticket: kết quả cuối USED hoặc REFUND_PENDING, không cả hai use case thành công.
- [ ] 2. Test thời điểm start−1 tick/startTime, ticketIds cross-order và duplicate, reason quá dài.
- [ ] 3. Kiểm refund pending không check-in; rejected trở lại ACTIVE chỉ khi Event hợp lệ; zero approve trả kho đúng một lần.
- [ ] 4. Chạy lại flow coupon/paidAmount qua request và chứng minh coupon CONSUMED không được release.
- [ ] 5. Chốt backlog tuần 2; chuyển các Refund PROCESSING/APPROVED bù trừ chưa attempt vào fixture worker ngày 15, không tự đánh SUCCEEDED cho sạch.

**Kiểm chứng bắt buộc:**

- [ ] SP05/SP10, F04, V07 có caller thật và TX05/TX10 evidence.
- [ ] Ngày 14 kết thúc với refund có tiền đang chờ là trạng thái dự kiến, chưa tuyên bố đã hoàn tiền.

**Điều kiện hoàn thành:** Toàn bộ bán vé/check-in và phần yêu cầu/duyệt hoàn đã có. Ghi case và kết quả trong `docs/evidence/day-14.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day14IT` và `database/tests/day-14.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day14IT verify
```
