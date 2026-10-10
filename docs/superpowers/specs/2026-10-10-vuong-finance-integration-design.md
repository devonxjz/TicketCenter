# VUONG-EPIC — thiết kế triển khai toàn phần Vương

Người dùng yêu cầu thực hiện toàn bộ #28–#33, không chờ review PR ngày 10/10/2026. Quyết định schema trong `docs/backend/2026-10-10-vuong-schema-decision.md` đã được người dùng chốt: UUID/tc_*, tiền nguyên VND, cho phép bổ sung schema test và migration tài chính. Thực thi Native với các phần độc lập giao song song; không chờ review PR giữa tasks. Không merge PR vào dev, sửa nghiệp vụ owner khác, deploy hoặc chuyển tiền thật.

## Mục tiêu và ranh giới

Hoàn thiện phần Vương có thể triển khai từ các contract đã chốt: commission CRUD; settlement recalculate/blockers/confirm; payout reservation/result/replay; organization/admin report cùng CSV; dashboard/audit; Swagger/Redis registry và CI. Java 25/Servlet 6.1/Tomcat 11/JPA resource-local, SQL Server thật; không Spring/dependency mới. Giữ model/pom/persistence.xml và migration 026; bổ sung SQL mới, không dùng legacy BIGINT. Base integration origin/dev 8d5828a, đã ghép groundwork #28/#29/#32 local, bảo toàn PR37/40.

## Dữ liệu và giao dịch

Các thao tác ghi finance thực hiện SP với backend actor UUID. Repositories JDBC gọi SP và bound SELECT, không dirty-write model. `FinanceDatabase.open()` trả Connection do caller đóng; production dùng cấu hình DatabaseConfig của ADMIN; request không nhận credentials/SQL/principal name. SP và lớp authorization đều kiểm current ACTIVE/authVersion/admin; manager report phải có membership active MANAGER của đúng organization. CSV kiểm quyền/filter/event scope trước BOM/header.

Commission giữ contract đã duyệt #29: rate scale4 trong [0,100], fixedFee nguyên không âm, [from,to), event reference khóa mọi edit. Rule mutation transaction lưu idempotency key/fingerprint + audit; xung đột cùng key khác terms →409. Fee từng accepted paid Order: gross minus completed CUSTOMER_REFUND, loại PAYMENT_COMPENSATION; HALF_UP VND cap ở remaining, remaining0 fee0. Không tổng hợp double/long rồi làm tròn.

Recalculate chỉ DRAFT, một Settlement/event, unique snapshot/order; không có paid order thì báo0 không tạo header. Blockers gồm order payment chưa giải quyết, PENDING/UNKNOWN capture, refund chưa final/reconciled và cancellation work chưa hoàn tất. Confirm endTime đã qua kể cả CANCELLED; recalculate+blocker+freeze trong một transaction, confirm replay không đổi. Net0 PAID không transfer log.

Payout UUID ổn định từ idempotency key, fingerprint gồm amount và bank terms; reserve pending atomic trước adapter ngoài transaction. Same ID/terms trả stored status, không gọi transfer lần nữa. UNKNOWN giữ reservation, không retry bằng ID mới; chỉ FAILED verified mới release pending/new ID. Final result một lần, paid+pending<=net, PAID iff paid=net/pending0. Production mặc định provider unavailable →503/giữ pending theo trạng thái thực; mô phỏng chỉ cấu hình explicit, dữ liệu adapter lưu bền để replay/crash test, không trả success giả khi provider chưa tích hợp.

## API và giao diện

Prefix /api thống nhất với application đã có. DTO UUID strings, money strings nguyên, UTC Instant, {data:...}, paginated page/pageSize/total; errors fixed code/message/correlationId không SQL/bank secret. Mutation POST có session+CSRF+Idempotency-Key. Một shared helper finance xử lý validation/serialization/error, không sửa shared identity logic.

Commission `/api/admin/commission-rules`, settlement `/api/admin/events/{eventId}/settlement`, `/settlement-blockers`, `/settlement/recalculate`, confirm `/api/admin/settlements/{id}/confirm`, payouts `/payouts`. Reports `/api/organizations/{id}/reports`, `/api/admin/reports`, `/api/reports/export`. Overview `/api/admin/overview`; audit `/api/admin/audit-logs` và detail. JSP mới trong miền commission/settlement/report/audit/dashboard để không override controllers/layout mẫu của owner khác; form chuẩn escaping và CSRF.

Report metric explicit COHORT_REVENUE vs CASH_FLOW; UTC half-open [from,to), event phải đúng org, filters shared JSON/CSV. COHORT theo Order.paidAt và refund final cutoff; CASH_FLOW theo Payment.paidAt và refund transfer processedAt, compensation riêng. All event rows gồm zero-order; paid snapshots labeled confirmed vs estimated. Export tuần tự ResultSet/iterator, không collect toàn dataset; cursor/connection đóng khi IOException. Tenant authorization luôn chạy trước đọc cache. Redis outage fallback DB, TTL/key/filter scope theo registry; invalidation sau commit, không dùng Redis làm authority tiền/permission/replay.

Audit append-only DB trigger/grants; projection allowlist metadata typed IDs/state/money/time, không raw metadataJson hoặc secret, pagination stable created_at/id. Dashboard current counts + today UTC cash/fees đúng metric; không thay hardcoded prototype số liệu làm contract.

## Kiểm chứng và điều kiện đóng

Unit TDD cho math/filter/validation/redaction/provider failure; SQL Server riêng cho rounding/rollback/immutable snapshots/replay/reservation concurrency/permissions/tenant A-B. Test fixtures không emails thật, không DB chung. CI default unit không external IO; DB/SMTP explicit opt-in + disposable target; summaries truthful; publish/deploy chỉ explicit allowed trigger, feature push không publish latest/start shared WAR.

Test schema chỉ fixture cho database tên TC_Vuong_Test_* do phiên tạo; migration production fail khi base schema thiếu, không drop/recreate dữ liệu. Không sửa database hiện có. SMTP thật không chạy; gateway simulation labeling rõ. Không đóng #28/#33 E2E owner/catalog hoặc CSV Excel acceptance khi chưa có kết quả thật. Triển khai đầy đủ phần sở hữu không đồng nghĩa toàn 34 issues đã được ghép/kiểm.
