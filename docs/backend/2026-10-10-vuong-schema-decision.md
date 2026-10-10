# Quyết định schema cần chốt cho VUONG-EPIC

Ngày 10/10/2026, người dùng yêu cầu thực hiện toàn bộ #28–#33, không chờ review PR. #34 là epic. Review PR không chặn việc triển khai tiếp; hồ sơ này giải quyết hợp đồng dữ liệu còn thiếu, không phải yêu cầu chờ reviewer.

## Hiện trạng đã xác minh

| Nguồn | ID / tiền / bảng | Khả năng dùng |
|---|---|---|
| `database/schema.sql`, `functions.sql`, `views.sql`, `procedures.sql` | BIGINT; DECIMAL(19,2); bảng không prefix | Một họ SQL legacy; không tương thích locked JPA models |
| `BaseEntity`, settlement/order/payment models và SPEC | UUID; nguyên VND; `tc_*` | Hợp đồng chuẩn trong Java/spec, nhưng thiếu bản cài schema nền |
| Migration 026 | UUID/tc_*; thêm snapshot/log; giới hạn DB `TicketCenterticke` | Chỉ upgrade DB đã có tc_*; không cài được DB trống; còn alias/mapping không khớp |

Không có script tạo UUID base schema, grants hoặc các object tài chính canonical F02/F05/F09/F10, V03/V04/V08, SP14/SP15/SP16. `SettlementTransferLog` thiếu mapping cột status bắt buộc; payout mirror trigger và Payout model dùng tên cột khác nhau; audit model dùng entity_type/entity_id, migration dùng aggregate_type/aggregate_id. Không nối Java UUID vào procedure BIGINT rồi gọi là hoàn tất.

SQL Server local đang chạy, Windows integrated connection có quyền tạo database test riêng. Chưa tạo/sửa database nào trong đợt rà soát; không có database TicketCenter/test hiện hữu được tìm thấy. Không dùng DB hoặc SMTP chung để chạy test.

## Phương án đề xuất: UUID/tc_* là chuẩn

1. Giữ UUID và nguyên VND, không sửa model của owner khác. Định nghĩa bảng nền **chỉ cho DB test tách biệt** theo model/contract đã có; không dùng hbm2ddl để thay thế chứng minh SQL nghiệp vụ.
2. Thêm migration mới cho phần Vương; không sửa migration 026 đã chia sẻ. Nguồn tài chính chính là snapshot/transfer logs canonical, không dùng đồng thời Payout legacy và log để cộng tiền.
3. F02 `fn_CalculateCommission(remainingAmount, ratePercent, fixedFee)` trả phí HALF_UP nguyên VND, cap ở remaining; remaining=0 → 0. Gross/refund/commission tính theo từng accepted paid Order; compensation tách riêng.
4. V03 `vw_OrderFinancialSummary`, V04 `vw_EventSalesReport`, V08 `vw_SettlementPayoutBalance`; F05 cohort theo Order.paidAt, refund cutoff < toUtc; F10 cashflow theo Payment.paidAt/TransferLog.processedAt. Không thay lịch sử bằng số refund hiện tại.
5. SP14 `usp_RecalculateSettlement(eventId,actorId)` khóa Event/Settlement; chỉ DRAFT; không tạo settlement rỗng. SP15 `usp_ConfirmSettlement(settlementId,actorId)` endTime + zero-blocker, recalculate trong cùng transaction, đóng băng snapshot; net=0 → PAID không log chuyển tiền. SP16 `usp_RecordPayout(settlementId,payoutId,actorId,amount,reference,verifiedResult)` lưu reservation, giữ replay/fingerprint, paid+pending<=net; network adapter nằm ngoài transaction.
6. Chọn cột audit entity_type/entity_id đúng model hiện hữu; projection chỉ trả metadata allowlist, không raw JSON/secret. DDL append-only và grants là migration riêng được kiểm bằng principal thật.
7. Định nghĩa DTO UUID/tiền string nguyên/UTC và response envelope theo CONVENTIONS; ví dụ ID số/tiền `.00` trong issues được ghi rõ là ví dụ legacy. Report kiểm current actor và membership MANAGER của **org được yêu cầu** trước CSV BOM/header; không dùng role của một org bất kỳ từ session.
8. Payout adapter mô phỏng là cấu hình test có bằng chứng riêng; không trình bày là chuyển tiền ngân hàng thật. UNKNOWN giữ pending, cần đối soát; không tự retry ID mới hoặc ghi SUCCEEDED khi timeout.

## Phương án thay thế

Nếu nhóm đã có canonical schema khác hoặc đã chốt schema BIGINT legacy, cần tên repository/commit hoặc vị trí tài liệu schema chuẩn và mapping được owner xác nhận. Chọn BIGINT sẽ buộc đổi locked model/caller liên miền; không tự đổi hàng loạt model chỉ để khớp bộ SQL legacy.

## Phần có thể tiếp tục độc lập khi chờ chốt

- Tích hợp các phần Swagger/commission policy/CSV đã được duyệt vào nhánh epic local, bảo toàn PR #37/#40 và preview/worktree cũ.
- Cô lập nhóm unit/DB/SMTP và sửa summary CI chỉ báo PASS khi test thật đạt; không publish image/latest hoặc start WAR với config/shared workers trên feature push.
- Hoàn thiện tài liệu OpenAPI cho mã hiện có; planned endpoints vẫn planned đến khi có implementation.
- Test math/filter/redaction/adapter failure theo hợp đồng thuần Java đã rõ; không ghi các ca này thành bằng chứng DB/HTTP hoàn tất.

## Gate phải có để đóng tasks

Database SQL Server tách biệt với schema đã chọn; permission/rollback/concurrency/idempotency/tenancy tests thật; servlet/JSP và OpenAPI khớp cùng contract; Redis outage/invalidation; CSV app acceptance; smoke liên miền chỉ báo đạt khi endpoint owner có thật. Không đóng #28–#33 từ placeholder, stub hoặc mô phỏng provider.
