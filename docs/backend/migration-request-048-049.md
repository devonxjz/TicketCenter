# Yêu cầu chạy migration 048 + 049 trên Azure SQL (TicketsCenter)

Trạng thái đo ngày 2026-10-13 (truy vấn chỉ đọc):

| Mục | Trạng thái |
|---|---|
| Migration đã áp gần nhất | `047_event_authority_lock_order.sql`, `048_azure_persistence_compatibility.sql` |
| `048_khanh05_refund_worker.sql` | **Chưa áp** (refund worker cần ledger/view/proc của file này) |
| `049_outbox_email_runtime.sql` | **Chưa áp** (`dbo.vw_OutboxEmailContext` chưa tồn tại) |
| `vw_PaymentsToReconcile`, `vw_ExpiredHolds` | Đã có |
| Dữ liệu | `tc_outbox` = 0 dòng, `tc_payments` = 0 dòng |

Cần người có quyền chạy theo thứ tự: `048_khanh05_refund_worker.sql` → `049_outbox_email_runtime.sql`,
rồi ghi vào `dbo.tc_schema_migrations` theo runner của dự án (`tools/acceptance/apply-sql-report.ps1`).
Lưu ý: DB đã có `048_azure_persistence_compatibility.sql` (không nằm trong repo); khác tên nên không xung đột version, nhưng nên xác nhận nội dung không chồng lấn.

Sau khi áp, chạy IT (chỉ trên DB fixture loopback dùng một lần, theo chính sách `ExternalTestCondition`):
`mvn test -Dtest=OutboxWorkerSqlServerIT` với `tc.tests.db.name=TC_Vuong_Test_*`.
IT này KHÔNG được trỏ vào DB Azure dùng chung: `OutboxWorker` sẽ claim cả các dòng outbox thật.
