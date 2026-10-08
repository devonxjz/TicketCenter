# Ngày 16 — Hủy sự kiện, hoàn tự động và tiếp tục sau restart

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ bảy, 2026-10-10. **Ước lượng tạm của kế hoạch ban đầu:** 18 giờ công tổng của nhóm.

**Mục tiêu:** Admin hủy làm ngừng bán/check-in ngay; các đơn được dọn/hoàn từng lô an toàn, có tiến độ và ngoại lệ.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §6.10, §8.4, §14 SP13/SP17/TX13/TX17. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D15 refund; D12 worker; D07 audit status; SP07/SP10 đã có.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D16-T01 — SP13 hủy và tạo công việc trong cùng transaction

**Phụ trách đề xuất:** B. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D16_01_cancel_event.sql; SQLTEST/day-16.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_CancelEvent(eventId,actorId) → CANCELLED/tiến độ; admin, trước startTime.

**Phụ thuộc của task:** D07 Event/TR05, D12 outbox; có thể cài/test trước refund worker chạy.

**Cách thực hiện:**

- [ ] 1. Khóa Event, kiểm admin/PUBLISHED/now<startTime; ghi CANCELLED và outbox công việc duy nhất cùng transaction.
- [ ] 2. TR05 ghi audit status; Service ghi lý do/quyết định riêng nếu có, không nhân EVENT_STATUS_CHANGED.
- [ ] 3. Lặp cancel trả trạng thái/tiến độ cũ, không tạo nhiều batch jobs.
- [ ] 4. Commit sớm: SP02/SP04/SP08/SP09 phải kiểm lại Event dưới khóa để ngay lập tức ngừng giữ/mua/check-in.
- [ ] 5. TX13 gây lỗi outbox insert sau Event change, cả hai rollback; không hoàn tất mọi Order trong transaction hủy ban đầu.

**Kiểm chứng bắt buộc:**

- [ ] Đúng startTime không cho cancel; manager không thể cancel.
- [ ] Sau commit CANCELLED, request mua/check-in bị chặn dù refund chưa xử lý.

**Điều kiện hoàn thành:** SP13/TX13 có hiệu lực hủy nguyên tử ngắn. Ghi case và kết quả trong `docs/evidence/day-16.md`.

## D16-T02 — SP17 xử lý từng Order bằng SP07/SP10

**Phụ trách đề xuất:** B. **Giờ công:** 6h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D16_02_cancelled_order.sql; JAVA/repository/fulfillment/RefundRepository.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_ProcessCancelledOrder(eventId,orderId) → progress/exceptions; chỉ worker, Event CANCELLED và Order đúng Event.

**Phụ thuộc của task:** D16-T01 cancelled state, D08 SP07, D14 SP10, D15 refund result.

**Cách thực hiện:**

- [ ] 1. Đơn chưa trả gọi SP07; Hold không Order sẽ do worker gọi SP07 riêng.
- [ ] 2. Khóa tập vé Order/Refund; với Refund REQUESTED gọi adoptEventCancellation, giữ lịch sử lý do và tự duyệt trên cùng record. Refund APPROVED/PROCESSING/NEEDS_RECONCILIATION/RETRYABLE giữ tập vé/amount và tiếp tục theo dõi.
- [ ] 3. Vé đủ điều kiện chưa có nghĩa vụ mở: tạo Refund CUSTOMER_REFUND/EVENT_CANCELLATION cho tập vé còn lại, gọi SP10 tự duyệt cùng transaction; không nghĩa vụ thứ hai cho vé đang mở.
- [ ] 4. Vé refunded bỏ qua; USED ghi ngoại lệ cho admin, không tự hoàn hoặc khôi phục; vé 0đ vẫn giữ Refund COMPLETED qua completeWithoutTransfer, không log chuyển tiền giả.
- [ ] 5. TX17 lỗi giữa nhiều vé rollback riêng Order này, không đảo các Order đã commit; SP con không commit riêng.

**Kiểm chứng bắt buộc:**

- [ ] Một Order có request partial và các vé khác vẫn tạo đủ nghĩa vụ không trùng.
- [ ] Gọi SP17 trên Event chưa cancelled hoặc Order Event khác bị chặn ở DB.

**Điều kiện hoàn thành:** SP17/TX17 tái sử dụng đúng chủ sở hữu đường ghi. Ghi case và kết quả trong `docs/evidence/day-16.md`.

## D16-T03 — Worker theo lô và API tiến độ

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/job/EventCancellationJob.java; JAVA/controller/event/EventCancellationServlet.java; JAVA/service/event/EventCancellationService.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST /admin/events/{id}/cancel; GET /admin/events/{id}/cancellation-progress; số pending/completed/exception có nghĩa rõ.

**Phụ thuộc của task:** D16-T01/T02 và D12 scheduler; D15 RefundJob xử lý tiền sau commit.

**Cách thực hiện:**

- [ ] 1. Nhận cancel outbox, enumerate Hold không Order và Order theo ID ổn định/batch; mỗi Order một transaction SP17.
- [ ] 2. Lưu cursor/progress kỹ thuật đủ restart, nhưng kiểm lại công việc chưa hoàn thay vì bỏ qua do cursor nâng trước commit.
- [ ] 3. RefundJob ngày 15 hoàn actual simulated money sau commit SP17; progress tách nghĩa vụ tạo/duyệt, hoàn 0đ COMPLETED và hoàn có tiền SUCCEEDED; không coi tạo log PENDING là đã hoàn.
- [ ] 4. Admin đọc ngoại lệ USED/FAILED/UNKNOWN với ID xử lý; không expose buyer toàn hệ thống ra public event endpoint.
- [ ] 5. Chứng minh event history/đơn khách còn truy cập được dù public catalog loại cancelled.

**Kiểm chứng bắt buộc:**

- [ ] Restart ở giữa batch không bỏ sót Order hoặc tạo trùng refund.
- [ ] Progress không gọi completed khi vẫn còn UNKNOWN/exception cần admin.

**Điều kiện hoàn thành:** Quản trị theo dõi được hủy mà không cần màn hình. Ghi case và kết quả trong `docs/evidence/day-16.md`.

## D16-T04 — Race cancel/payment/refund và kiểm tổng

**Phụ trách đề xuất:** C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day16IT.java; TEST/concurrency/CancellationRaceIT.java; docs/evidence/day-16.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Dữ liệu test gồm Hold chưa Order, Order pending/paid/zero, pending refund, failed refund và USED.

**Phụ thuộc của task:** D16-T01/T02/T03, payment SP09 và refund SP10/SP11.

**Cách thực hiện:**

- [ ] 1. Cho callback payment đua cancel: hoặc phát hành trước rồi vào nghĩa vụ hủy, hoặc compensation; không vé active bán sau cancelled.
- [ ] 2. Cho reject request đua cancel: không khôi phục ACTIVE vé Event đã cancelled.
- [ ] 3. Chạy cancel hai lần, worker hai instance test, restart sau một Order; đối chiếu từng Ticket/Refund/log attempt/kho.
- [ ] 4. Đối chiếu tổng refund theo paidAmount, tách compensation; không trả coupon consumed.
- [ ] 5. Xác nhận Event cancelled sau publish vẫn bị TR01/TR02 khóa layout; giữ lịch sử chứ không xóa Event.

**Kiểm chứng bắt buộc:**

- [ ] TX13/TX17 rollback/retry pass; số open request mỗi ticket<=1.
- [ ] Không transaction dài bao trùm tất cả Order hoặc external refund.

**Điều kiện hoàn thành:** Hủy Event hoàn chỉnh gồm phục hồi và ngoại lệ. Ghi case và kết quả trong `docs/evidence/day-16.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day16IT` và `database/tests/day-16.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day16IT verify
```
