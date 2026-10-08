# Ngày 17 — Đối soát, đóng băng số liệu và chi trả mô phỏng

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Chủ nhật, 2026-10-11. **Ước lượng tạm của kế hoạch ban đầu:** 20 giờ công tổng của nhóm.

**Mục tiêu:** Tính đúng doanh thu sau hoàn/hoa hồng, chốt khi hết blocker và chi trả không vượt netPayable.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §6.11, §14 SP14–16/F02/F09/V03/V08/TR04/TR09/TX14–16. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D15/D16 hoàn và cancel; D06 rule; D11 paid orders; ngày khối lượng cao cần chia SQL/tính tiền/API/test theo hợp đồng.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D17-T01 — V03, F02, F09 và tiền đối soát

**Phụ trách đề xuất:** B+A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D17_01_settlement_reads.sql; JAVA/model/settlement/; TEST/settlement/CommissionTest.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** V03 một dòng/Order; F02 commission; F09 blocker rows từ trạng thái hiện có, không domain class mới.

**Phụ thuộc của task:** D11 captured revenue, D15 refunds, D16 cancellation progress, D06 rule.

**Cách thực hiện:**

- [ ] 1. V03 lấy doanh thu vé từ Order PAID/acceptedPayment được chấp nhận (đơn 0đ không Payment), aggregate Refund vé COMPLETED riêng, loại thu/hoàn bù trừ; không join nhiều Payment với nhiều Ticket/Refund trực tiếp.
- [ ] 2. F02 remaining=0→0, còn lại min(remaining, HALF_UP(remaining×rate/100+fixedFee)); invalid/null trả NULL để caller reject.
- [ ] 3. F09 chặn trước endTime, PENDING/UNKNOWN payment, request mở, refund obligation chưa xong và cancel job còn dở.
- [ ] 4. Xác định blocker theo trạng thái Refund hiện hành và log kỹ thuật: log FAILED cũ của Refund đã COMPLETED không chặn mãi; REQUESTED/APPROVED/PROCESSING/RETRYABLE/NEEDS_RECONCILIATION còn phải giải quyết thì chặn.
- [ ] 5. Viết vector Java/SQL partial/full refund, zero, fixed fee lớn hơn remaining, nhiều rule hiệu lực nhưng Event luôn dùng rule đã lưu.

**Kiểm chứng bắt buộc:**

- [ ] Remaining=300000, rate=10%, fixed=1000 cho 31000 commission; remaining=0 cho 0.
- [ ] F09 Event cancelled vẫn chờ endTime; UNKNOWN không bị bỏ qua.

**Điều kiện hoàn thành:** Công thức và điều kiện chốt rõ, có test đối chiếu. Ghi case và kết quả trong `docs/evidence/day-17.md`.

## D17-T02 — SP14/SP15 và trigger snapshot

**Phụ trách đề xuất:** B. **Giờ công:** 6h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D17_02_settlement_write.sql; SQLTEST/day-17.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** SP14 recalculate DRAFT; SP15 confirm; TR04 bảo vệ SettlementOrderSnapshot kỹ thuật và TR09 bảo vệ Settlement tổng; TX14/TX15.

**Phụ thuộc của task:** D17-T01 V03/F02/F09; trigger header/item chuẩn bị trước SP confirm.

**Cách thực hiện:**

- [ ] 1. SP14 admin khóa Event/Settlement, upsert một Settlement/Event và một SettlementOrderSnapshot/Order; backend tính theo V03/F02, gọi Settlement.recalculate(grossRevenue,totalRefund,totalCommission), lưu tổng snapshot, zero eligible orders trả báo cáo 0 không tạo Settlement rỗng.
- [ ] 2. Chỉ DRAFT được tính lại; mỗi SettlementOrderSnapshot lưu gross/refund/commission/net, tổng khớp grossRevenue/totalRefund/totalCommission; netPayable dẫn xuất. paidAmount/pendingAmount chỉ cập nhật qua hành vi chi trả.
- [ ] 3. SP15 khóa chung với payment/refund, kiểm F09 dưới khóa, gọi lại SP14 rồi confirmedAt/CONFIRMED; net=0→PAID không SettlementTransferLog giả.
- [ ] 4. TR04 kiểm cả parent cũ/mới khi insert/update/delete SettlementOrderSnapshot; TR09 chặn đổi grossRevenue/totalRefund/totalCommission, Event/mốc snapshot đã CONFIRMED, xóa/lùi DRAFT; cho SP16 cập nhật paidAmount/pendingAmount và chuyển PAID hợp lệ.
- [ ] 5. TX14 lỗi một Item giữ snapshot DRAFT cũ; TX15 lỗi sau recalc rollback tiền lẫn status; confirm đua refund/Item edit phải bảo vệ snapshot.

**Kiểm chứng bắt buộc:**

- [ ] Một Event chỉ một Settlement, một Order một SettlementOrderSnapshot; confirmed lần nữa trả snapshot cũ.
- [ ] Không thể chuyển Item từ confirmed sang draft khác để né trigger.

**Điều kiện hoàn thành:** SP14/SP15/TR04/TR09 có concurrency và trigger multirow. Ghi case và kết quả trong `docs/evidence/day-17.md`.

## D17-T03 — SP16, V08 và API payout

**Phụ trách đề xuất:** A+B. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D17_03_payout.sql; JAVA/service/settlement/SettlementService.java; JAVA/controller/settlement/SettlementServlet.java; JAVA/integration/payment/SimulatedPayoutGateway.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST /admin/events/{id}/settlement/recalculate; POST /admin/settlements/{id}/confirm,/payouts; V08 balance. SP16 usp_RecordPayout(settlementId,payoutId,actorId,amount,reference,verifiedResult) cập nhật Settlement và SettlementTransferLog nguyên tử.

**Phụ thuộc của task:** Schema Settlement/SettlementTransferLog D02; D17-T02 confirmed lifecycle cho tích hợp; V08/SP16 có thể phát triển song song theo contract.

**Cách thực hiện:**

- [ ] 1. SP16 khóa Settlement/lịch sử payoutId; chỉ CONFIRMED tạo lần mới. Gọi beginPayout(payoutId,amount) tăng pendingAmount và ghi SettlementTransferLog PENDING nguyên tử; paidAmount+pendingAmount<=netPayable.
- [ ] 2. ID lặp trả/cập nhật đúng log, amount khác bị từ chối. Kết quả cuối gọi recordPayoutResult với amount đã lưu, giảm pendingAmount và tăng paidAmount khi SUCCEEDED; FAILED giữ log và giải phóng pending đúng một lần.
- [ ] 3. Chỉ paidAmount=netPayable và pendingAmount=0 chuyển PAID; failed retry dùng payoutId mới sau xác nhận thất bại.
- [ ] 4. Adapter mô phỏng qua admin backend, không public status setter; external-style I/O tách transaction, kiểm reference/outcome từ adapter.
- [ ] 5. V08 tổng hợp SettlementTransferLog riêng, đối chiếu paidAmount/pendingAmount trên Settlement; remaining=net-paid, availableToPay=net-paid-pending. Không log trả 0, không join log với snapshot gây nhân tiền; API giải thích blocker qua F09.

**Kiểm chứng bắt buộc:**

- [ ] Hai payouts đồng thời không vượt net sau trừ pending; pending không hiển thị đã trả.
- [ ] Net0 không tạo SettlementTransferLog; buyer/manager không được chi trả.

**Điều kiện hoàn thành:** Có chi trả mô phỏng, idempotency và số dư chính xác. Ghi case và kết quả trong `docs/evidence/day-17.md`.

## D17-T04 — Chốt test tài chính và snapshot

**Phụ trách đề xuất:** C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day17IT.java; TEST/concurrency/SettlementConcurrencyIT.java; docs/evidence/day-17.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** TX14/TX15/TX16 với lỗi sau mutation và hai phiên thật; Model methods trong diagram hoàn tất.

**Phụ thuộc của task:** D17-T01/T02/T03; transaction/concurrency harness.

**Cách thực hiện:**

- [ ] 1. Tạo nhiều Order partial/full refund + compensation; lập bảng kỳ vọng bằng tay cho gross/refund/commission/net.
- [ ] 2. Confirm trước end hoặc còn request/UNKNOWN bị chặn; sau giải quyết được; thay rule mới không đổi Event/Settlement lịch sử.
- [ ] 3. Multirow sửa header/items và payout duplicate/overspend thử cả SQL trực tiếp lẫn HTTP.
- [ ] 4. Crash sau payout success trước response rồi tra cùng payoutId; không ghi nhận lần chi thứ hai.
- [ ] 5. Đối chiếu tổng SettlementOrderSnapshot=Settlement và tổng log với paid/pending, paid+pending<=net; test CommissionRule/Settlement.recalculate/confirm/beginPayout/recordPayoutResult theo diagram. Snapshot/log là persistence, không class nghiệp vụ bổ sung.

**Kiểm chứng bắt buộc:**

- [ ] Có evidence cho SP14/SP15/SP16/F02/F09/V03/V08/TR04/TR09.
- [ ] Không gọi 'đã chi trả' khi chỉ PENDING hoặc có phần còn lại.

**Điều kiện hoàn thành:** Toàn bộ vòng đời tài chính backend hoàn tất. Ghi case và kết quả trong `docs/evidence/day-17.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day17IT` và `database/tests/day-17.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day17IT verify
```
