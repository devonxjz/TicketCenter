# Redis Key Registry — VUONG-01

Owner tích hợp: Vương. Nguồn: [#28](https://github.com/devonxjz/TicketCenter/issues/28) và các sub-issue hiện hành; đối chiếu producer tại `28ce056`, 11/10/2026. Đây là hợp đồng bàn giao: **hiện có** là đã thấy producer trong source; **dự kiến** cần owner triển khai và nghiệm thu; **cần phối hợp** là có mâu thuẫn giữa issue/code. Việc đăng ký key không chứng minh Redis runtime hoạt động.

## Quy tắc dùng chung

- SQL Server giữ quyền, trạng thái, số tiền và quyết định nghiệp vụ. Redis cache DTO/projection có TTL, không cache entity JPA/lazy graph.
- Cache mới dùng namespace của miền và phiên bản, ưu tiên `tc:<domain>:<purpose>:v<version>:<scope>`. Giữ nguyên tên key đã nêu trong sub-issue để tránh mỗi người tự đổi tên; chuyển tên/version cần producer và consumer cùng phối hợp.
- Scope gồm actor/organization/event khi dữ liệu có quyền riêng. Filter hash phải lấy từ tham số đã chuẩn hóa, có phân trang/order/date/timezone; kiểm quyền trước khi đọc cache hoặc xuất CSV.
- Không lưu password/hash, OTP rõ, cookie/session token, reset token, credential, khóa ký hoặc raw QR. Payload của idempotency phải được lọc dữ liệu nhạy cảm trước khi lưu kết quả.
- GET cache miss/Redis lỗi được fallback DB. Mutation quan trọng chỉ tiếp tục khi DB có bảo vệ chống lặp và transaction đã được kiểm chứng; nếu không, trả 503. Redis lock không thay thế khóa/constraint DB.
- Chỉ invalidate/refresh sau DB commit. Chỉ owner nghiệp vụ ghi/xóa namespace của mình; sự kiện liên miền thông báo qua contract/outbox. TTL không thay thế kiểm trạng thái ở mutation.

## Cache đọc

| Key | Owner | Giá trị / scope | TTL giây | Invalidate / trạng thái |
|---|---|---|---:|---|
| `auth:user:<userId>` | Thái | `status:authVersion` của User | 180 | `AuthService.evictUserCache`, revoke sessions, password reset; **hiện có** |
| `tc:user:auth-version:<userId>` | Thái | Theo bảng #28 | 86400 | **Cần phối hợp:** không có producer; không đổi key/TTL của auth đã merge trong PR VUONG-01 |
| `tc:event:list:v1:<filterHash>` | Đông | Public Event list, filter/page/order | 60 | Publish/edit/cancel, invalidate theo generation/index; dự kiến #8 |
| `tc:event:detail:v1:<eventId>` | Đông | Public Event projection | 120 | Edit/publish/reject/cancel; dự kiến #8 |
| `tc:event:zones:v1:<eventId>` | Đông | Zone projection | 60 | Inventory/pricing đổi; dự kiến #8/#10 |
| `tc:zone:seats:v1:<zoneId>` | Đông | Seat projection | 30 | Hold/cancel/issue/refund/check-in theo contract inventory; dự kiến #8 |
| `tc:event:categories:v1` | Đông | Danh mục | Chưa chốt | Owner xác nhận TTL trước ghép; dự kiến #8 |
| `tc:user:active-hold:<userId>` | Liêm | Chỉ holdId | 600, không quá expiry DB | Replace/cancel/expire/order; dự kiến #14 |
| `tc:order:summary:<orderId>` | Liêm | DTO đơn hàng; kiểm quyền buyer | Thời gian còn lại của Hold | Coupon/payment/expire/refund; dự kiến #15 |
| `tc:user:orders:v1:<actorId>:<page>` | Liêm | Order history, scope actor | Chưa chốt | Thêm filter/order vào scope nếu API có; payment/refund; dự kiến #19 |
| `tc:checkin:events:v1:<actorId>:<orgId>` | Khánh | Event metadata/bộ đếm, không có tiền; actor và org ở key | 60 | **Hiện có:** RedisCheckInEventCache; User/membership/lịch kiểm SQL mỗi request, hit không gia hạn TTL; invalidateEvent xóa actor-list theo org bằng SCAN. Event writer cần gọi hook sau commit |
| `tc:checkin:window:v1:<eventId>` | Khánh | Metadata khung giờ; trạng thái mở tính lại theo UTC máy chủ | 30 | **Hiện có:** RedisCheckInEventCache; SQL guard + UDF mỗi request, cache không cấp quyền. invalidateEvent xóa key; Event writer cần gọi hook sau commit |
| `tc:event:cancel-progress:<eventId>` | Khánh | Progress projection | 10 | Batch hoàn tất/retry; dự kiến #26 |
| `tc:commission:rules:v1:<organizationId>` | Vương | Rule DTO list; current ADMIN và org tồn tại được kiểm trước hit | 300 | **Hiện có:** CommissionRuleCache; create/edit invalidate sau SP commit. InitialPolicy từ Thái cần thông báo sau commit |
| `tc:finance:report:v1:<scope>:<generation>:<filterHash>` | Vương | scope `global` hoặc `org:<UUID>`; SHA256 actor/authVersion + metric/org/event/UTC/page/pageSize | 60 | **Hiện có:** ReportCache; current authority/event scope trước hit; mutation finance tăng global và org generation sau commit |
| `tc:finance:report:g:global` / `tc:finance:report:g:org:<UUID>` | Vương | Counter generation, không chứa DTO | Không TTL | **Hiện có:** ReportCache.key / invalidateAfterCommit; chỉ INCR, không wildcard DEL |
| `tc:settlement:summary:v1:<eventId>` / `tc:settlement:blockers:v1:<eventId>` | Vương | Summary/blockers DTO; current ADMIN và event tồn tại trước hit | 60 / 30 | **Hiện có:** SettlementReadCache; recalc/confirm/reserve/result invalidate sau SP commit. Payment/Refund owner cần thông báo sau commit |
| `tc:report:v1:<scope>:<filterHash>` | Vương | Namespace cũ dự kiến trong issue | Chưa bật | Implementation dùng namespace finance có generation và actor/authVersion ở trên; không có producer tên cũ |
| `tc:admin:overview:v1` | Vương | Admin KPI và bảy ngày UTC; current ADMIN trước hit | 60 | **Hiện có:** DashboardCache; finance writes invalidate sau commit. Org overview đọc SQL trực tiếp. User/organization/event/refund owner cần thông báo sau commit |
| `tc:ticket:detail:v1:<ticketId>` / `tc:user:tickets:v1:<actorId>:<filterHash>` | Liêm | Ticket projection | Chưa chốt | Issue/refund/check-in; chỉ mới được nhắc ở invalidation #23/#24 |
| `tc:refund:detail:v1:<refundId>` / `tc:admin:pending_refunds_count` | Khánh | Refund/admin projection | Chưa chốt | Request/decision/worker result; chỉ mới được nhắc ở invalidation #24 |

## Idempotency và lease

| Key | Owner | TTL giây | Nguồn / trạng thái |
|---|---|---:|---|
| `tc:idem:hold:<actorId>:<key>` | Liêm | 600 | #14, dự kiến |
| `tc:idem:order:<actorId>:<key>` | Liêm | 600 | #15, dự kiến |
| `tc:idem:vnpay:init:<actorId>:<key>` | Liêm | 600 | #16, dự kiến |
| `tc:idem:vnpay:ipn:<txnRef>:<providerTxnNo>` | Liêm | 604800 | #17 có providerTxnNo, khác bảng rút gọn #28; cần owner xác nhận canonical identity/replay |
| `tc:idem:checkin:<actorId>:<key>` | Khánh | 3600 | #22, dự kiến |
| `tc:idem:refund-request:<actorId>:<key>` | Khánh | 86400 | #23, dự kiến |
| `tc:idem:refund-decision:<adminId>:<key>` | Khánh | 86400 | #24, dự kiến |
| `tc:idem:refund-attempt:<attemptId>` | Khánh | 86400 | #28; khóa đúng attemptId, cần xác nhận với #25 |
| `tc:idem:payout:<adminId>:<key>` | Vương | Chưa bật | Payout hiện dedupe bền bằng payout UUID + SHA256 immutable terms ở SQL; chưa có Redis producer |
| `tc:lease:refund:<refundId>` | Khánh | 120 | #25, token lease riêng cho mỗi lần claim |
| `tc:lock:order-payment:<orderId>` | Liêm | 10 | #16, token owner; DB khóa Order giữ invariant |
| `tc:lock:ticket-checkin:<ticketId>` | Khánh | 5 | #22; DB quyết định kết quả soát vé |
| `tc:lock:refund-tickets:<sortedTicketIds>` | Khánh | 10 | #23; chuẩn hóa tập vé/sort trước hash, tránh A_B khác B_A |
| `tc:lock:settlement-payout:<settlementId>` | Vương | Chưa bật | SP16 giữ khóa SQL và paid + pending <= netPayable; mọi PENDING chặn ID mới, cùng ID replay/reconcile được phép |

Idempotency lưu fingerprint của operation + actor/scope + payload chuẩn hóa, trạng thái và kết quả đã lọc. Cùng key/cùng fingerprint trả kết quả đã lưu; khác fingerprint trả 409. Không tái thực thi chỉ vì Redis key hết TTL: tra identity nghiệp vụ đã lưu trong DB, đặc biệt Payment/Refund/Payout. Caller giữ key và payload khi replay; Swagger không tự tạo key mới.

Lease dùng atomic `SET NX EX/PX` với token duy nhất; renew/release phải so sánh token trong thao tác atomic, không `DEL` mù. Lease hết hạn hoặc process crash không được cho phép ghi trùng; DB transaction/unique identity và kiểm fencing/claim hiện tại cần được owner chứng minh.

## Những điểm cần owner xác nhận

Các producer Vương cập nhật ngày 10/10/2026 theo schema UUID/tc_* đã được duyệt. `finance.redis.enabled=false` tắt toàn bộ cache đọc tài chính. Redis outage fallback SQL; invalidation là best effort, nên cache cũ có thể tồn tại đến TTL nếu outage xảy ra đúng sau commit. Quyền hiện tại và mọi quyết định ghi vẫn kiểm SQL trực tiếp. CSV không đọc cache. Unit/SQL tests đã kiểm quyền trước cache và invalidate sau commit. FinanceRedisRuntimeTest đã chạy với Redis thật 7.4.2 biệt lập: hit, TTL300/60/30, generation invalidation, xóa overview và fallback khi shutdown server đều đạt. Test opt-in yêu cầu loopback/port riêng/fixture marker, không chạy mặc định. Thông báo invalidation của owner Payment/Refund/InitialPolicy và các writer KPI khác còn là gate ghép nhóm.

1. Auth namespace/TTL: hiện tại `auth:user`/180s; #28 đề xuất `tc:user:auth-version`/86400s. PR này chỉ ghi hiện trạng, không sửa identity.
2. QR: #18/#28 đề xuất cache PNG tại `tc:ticket:qr:v1:<ticketId>:<version>`/3600s, trong khi contract chung cấm raw QR. **Chưa cho phép producer lưu PNG/token QR** cho tới khi Liêm/Khánh thống nhất chính sách; giải mã/check-in vẫn phải kiểm DB, xóa cache không thu hồi được ảnh QR đã tải.
3. IPN: #17 có providerTxnNo; dedupe Redis phải đi cùng việc verify signature và identity DB của payment. Không dùng Redis để chấp nhận một IPN chưa xác minh.
4. Wildcard: `DEL prefix:*` không xóa theo pattern trong Redis. Dùng generation key hoặc index quản lý key; nếu dùng SCAN, phải giới hạn batch và phối hợp các writer. Không dùng KEYS ở production.
5. TTL ghi “Chưa chốt” và producer chỉ xuất hiện ở invalidation là hạng mục handoff cụ thể, không phải cache đã triển khai. Owner cập nhật registry cùng feature PR của mình qua Vương.

## Checklist bàn giao một namespace

Hai producer check-in trên đã đối chiếu [mã nguồn](../../src/main/java/vn/ticketscenter/fulfillment/checkin/cache/RedisCheckInEventCache.java), [service](../../src/main/java/vn/ticketscenter/fulfillment/checkin/service/CheckInEventService.java) và [bằng chứng owner ngày10/10](../evidence/khanh/KHANH-01-completion.md). Kiểm thử key/TTL/outage được chạy lại ở mức unit trong đợt đối chiếu ngày11/10; kết quả Redis/SQL thật của Khánh là bằng chứng lịch sử của owner, không được ghi thành một lần nghiệm thu mới của Vương. Event/payment/refund/identity writers phải ghép hook sau commit; chưa có bằng chứng toàn bộ các hook liên miền đã gọi.

- Owner xác nhận key/identity/version/TTL/value được lọc, cache scope có đủ quyền và filter.
- Ghi mutation nào invalidate, phía nào phát thông báo sau commit và bên nào xử lý.
- Test miss/hit/stale/unavailable, replay cùng payload, replay khác payload và cạnh tranh bằng SQL Server thật khi có mutation.
- Evidence chỉ ghi key đã ẩn identifier, TTL và trạng thái; không ghi value chứa dữ liệu cá nhân hoặc credential.
