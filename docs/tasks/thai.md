# Nhiệm vụ của Thái — check-in, hoàn vé và hủy sự kiện

## Prompt khởi đầu — copy từ đây

Copy **nguyên nội dung trong khối bên dưới** vào AI có quyền đọc repository TicketsCenter. Prompt đã gắn đúng thành viên/file; không cần copy toàn bộ 76 task vào cửa sổ chat. Nếu AI không có quyền đọc repository, phải cung cấp các tài liệu được liệt kê trước khi triển khai.

```text
Tôi phụ trách phần của Thái trong dự án TicketsCenter.
Hãy triển khai nhiệm vụ của tôi theo docs/tasks/thai.md.

Trước khi viết mã:
1. Đọc toàn bộ spec.md và docs/classdiagram/diagram.md, gồm thuộc tính,
   phương thức và quan hệ trong XML diagram.
2. Đọc toàn bộ docs/tasks/TEAM-CONTRACT.md từ đầu đến cuối, không bỏ mục
   hoặc chỉ đọc phần cá nhân; đọc đầy đủ docs/tasks/CONVENTIONS.md,
   API-MAP.md, COVERAGE.md và GIT-WORKFLOW.md.
3. Đọc toàn bộ docs/tasks/thai.md. Đọc các phần nhiệm vụ thành viên khác
   cung cấp đầu vào hoặc nhận đầu ra liên quan; kiểm mã nguồn, cấu hình,
   trạng thái Git và bằng chứng thực tế trong repository.
4. Trước thay đổi đầu tiên, báo ngắn gọn phiên bản nguồn đã đọc, phạm vi
   sở hữu, hợp đồng liên miền, nhiệm vụ sẽ làm và tình trạng phụ thuộc.
   Tiếp tục công việc đã được giao nếu đủ đầu vào, không dừng để hỏi lại
   xác nhận cho các bước triển khai thông thường.

Bắt đầu bằng nhiệm vụ đầu tiên chưa hoàn thành và đủ phụ thuộc.
Nếu tôi chỉ định mã nhiệm vụ cụ thể trong tin nhắn tiếp theo, ưu tiên mã đó.
Chỉ sửa file thuộc phạm vi Thái; phần do người khác sở hữu phải phối hợp
qua hợp đồng hiện hành. Không tự đổi spec, DTO/Service/Servlet/SQL chung.
Nếu thiếu phụ thuộc, ghi rõ artifact/task/người cung cấp và làm phần độc lập;
không dùng stub thành công hoặc dữ liệu giả để che phần chưa triển khai.

Giữ format/bố cục prototype; không vẽ hoặc sinh ảnh/sơ đồ còn thiếu.
Tuân quyền hiện tại, owner/org scope, transaction, locking, idempotency,
outbox, migration và các bất biến tiền/kho trong tài liệu.
Chạy kiểm chứng phù hợp, lưu evidence expected/actual và môi trường thật.
Thiếu DB/provider/browser thì ghi BLOCKED đúng phần, không báo PASS giả.

Dùng feature/<ten>/<task-id>-<chuc-nang> cho tính năng và sửa lỗi, mặc định
từ develop theo bảng task cá nhân. PR task vào develop; bản nghiệm thu qua
PR develop → main rồi tag trên SHA main đã kiểm. Chỉ ngoại lệ sửa bản main
đã bàn giao khi develop còn phần chưa nghiệm thu mới rẽ feature từ main,
PR vào main rồi đồng bộ main → develop theo GIT-WORKFLOW mục 7. Không tạo nhánh release riêng; main/develop không nhận commit trực tiếp
sau bootstrap. Commit tiếng Anh theo GIT-WORKFLOW.md, scope miền và Task-Id;
không bịa số Issue. Nếu develop/baseline chưa có, ghi blocker khởi tạo.
Commit sau mỗi đơn vị công việc đã kiểm chứng; chỉ stage file của nhiệm vụ.
Không tự push, merge, deploy, nộp hồ sơ hoặc thay cấu hình GitHub.

Cuối mỗi đơn vị, báo task, nhánh/commit, file/API/SQL/UI thay đổi, lệnh và
kết quả kiểm chứng, evidence, phụ thuộc còn thiếu và bước tiếp theo.
```

Muốn giao một task cụ thể, thêm sau prompt: `Nhiệm vụ lần này: THAI-NN.` Thay NN bằng mã thật trong file; không suy ra task hoàn thành từ lịch ngày, checkbox hoặc tên commit.

## Điều kiện bắt buộc trước khi triển khai

**Bắt buộc với cả thành viên và AI:** trước nhiệm vụ đầu tiên, phải đọc **toàn bộ [TEAM-CONTRACT.md](TEAM-CONTRACT.md), từ đầu đến cuối, không bỏ mục, không chỉ đọc phần của mình hoặc bản tóm tắt**. Đồng thời đọc đầy đủ [CONVENTIONS.md](CONVENTIONS.md), [API-MAP.md](API-MAP.md), [COVERAGE.md](COVERAGE.md) và [GIT-WORKFLOW.md](GIT-WORKFLOW.md), cùng spec/diagram được dẫn trong phần nguồn chuẩn.

- [ ] Đã đọc toàn bộ hợp đồng chung và tài liệu quy ước; hiểu ownership Model/Servlet/SQL, DTO/Service/API, quyền/principal, transaction/locking, outbox/SPI, migrations/fixture, Git và tiêu chí nghiệm thu.
- [ ] Ghi trong kế hoạch bắt đầu hoặc Draft PR: đường dẫn hợp đồng, commit SHA nguồn nếu có, task nhận, đầu vào/đầu ra liên miền và các ràng buộc áp dụng. Nếu hợp đồng còn chưa commit, ghi SHA nền và trạng thái working tree; không bịa phiên bản.
- [ ] Đối chiếu task với hợp đồng hiện hành và kiểm file/Service thuộc người nào. Nếu có mâu thuẫn hoặc thiếu hợp đồng bắt buộc, ghi rõ điểm thiếu và chủ cung cấp; chưa triển khai phần phụ thuộc đó.
- [ ] Khi tiếp tục phiên làm việc, kiểm hợp đồng có thay đổi từ phiên bản đã đọc không. Có thay đổi thì đọc lại toàn bộ hợp đồng mới và cập nhật caller/test bị ảnh hưởng trước khi tiếp tục phần liên quan.

Không bắt đầu viết mã cho nhiệm vụ khi chưa hoàn thành việc đọc và đối chiếu trên. Reviewer phải kiểm xác nhận này trước khi chấp nhận PR; một câu “đã đọc” không thay việc chứng minh đầu ra tuân hợp đồng.

Ngày lập: 06/10/2026. **Kế hoạch tương lai, chưa triển khai/chưa nghiệm thu.** Thái là người nhận chính. Các đường dẫn mã nguồn và profile kiểm thử dưới đây là đầu ra phải tạo; repo hiện có đặc tả, diagram, Maven skeleton và prototype. Checkbox chỉ được đánh dấu khi có kết quả thật.

## 1. Mục tiêu và nguồn chuẩn

Hoàn thành Refund đúng toàn bộ diagram; UI-08/15/16; gửi/duyệt/từ chối/hoàn/reconcile/retry, bù trừ khoản thu và hủy Event theo lô. Phần này phải ghép được với vé của Liêm, kho/sự kiện của Đông, tài khoản của Khánh và đối soát của Vương.

Đọc [spec.md](../../spec.md) §4–8, §9 UI08/15/16/20/21, §12, §14; [diagram.md](../classdiagram/diagram.md); [TEAM-CONTRACT](TEAM-CONTRACT.md), [CONVENTIONS](CONVENTIONS.md), [API-MAP](API-MAP.md), [COVERAGE](COVERAGE.md); các file [Khánh](khanh.md), [Đông](dong.md), [Liêm](liem.md), [Vương](vuong.md). Giữ bố cục và page frame của [prototype](../web-demo/prototype/README.md), không vẽ/sinh ảnh hoặc sơ đồ thiếu.

Kiến trúc Filter → Servlet → Service → Model/Repository → JPA/SQL Server; một WAR. Công nghệ theo spec: JDK25, Tomcat11.0.25/Servlet6.1, JSP4/JSTL3, Bootstrap, JPA/Hibernate, SQL Server local/Azure. Khánh khóa dependency/tooling; không thêm Spring, SPA, JDBC DAO hoặc scheduler thứ hai.

## 2. Quyền sở hữu và yêu cầu chung

- Thái sở hữu **Refund**; RefundTicket, RefundTransferLog, TicketCheckInLog là persistence, không thêm RefundRequest/CheckIn làm lớp nghiệp vụ. Liêm sở hữu Ticket và đủ methods checkIn/markRefundPending/restoreAfterRejection/markRefunded. Đông sở hữu Event.cancel, Seat/Zone trả kho. Thái gọi đúng method/SP, không sao chép Model của họ.
- SQL: C15/C20; V05/V07; SP04/SP05/SP10/SP11/SP13/SP17; F04/F07; IX05/IX08/IX10; TX04/TX05/TX10/TX11/TX13/TX17. Không có trigger riêng thuộc Thái; phối hợp TR08 Liêm/TR05 Đông và F09/TR04/TR09 Vương.
- Servlet duy nhất: RefundRequestServlet `/refund-requests/*`, CheckInServlet `/check-ins`. Khánh MeServlet, Liêm OrderServlet, Đông Event/OrganizationServlet và Vương AdminServlet là caller của Service Thái. Không đăng ký wildcard của họ.
- Java UUID/BigDecimal/Instant; SQL uniqueidentifier/decimal(19,0)/UTC datetime2; HTTP tiền là chuỗi số nguyên, thời gian UTC ISO-8601. JSP/email tiếng Việt, VND, Asia/Ho_Chi_Minh; dùng layout/helpers chung, không scriptlet/SQL/entity lazy-load trong JSP.
- ActorContext/Page/PageRequest tại `dto/common/` của Khánh; ActorContext gồm actorId/platformRole/authVersion/emailVerified/type USER hoặc SYSTEM. Quyền hiện tại lấy DB, không actor/principal/role/now/amount/status từ browser.
- TransactionRunner.required(PrincipalKind,ActorContext,Function<EntityManager,T>) giữ cùng principal/EntityManager/connection. SP tự đứng độc lập hoặc tham gia transaction ngoài/savepoint, không commit caller. Lưu ý định → commit → gọi provider ngoài TX → TX ghi kết quả; không dirty-write JPA lặp mutation của SP.
- User POST+CSRF; GET readonly; lỗi theo TEAM 400/401/403/404/409/503. Business check-in bị từ chối phải lưu kết quả rồi commit; thiếu quyền bị từ chối trước TicketCheckInLog, audit bảo mật dùng đường riêng. Lỗi hệ thống rollback.
- Cùng attemptId dùng kết quả cũ; UNKNOWN chỉ query/reconcile ID hiện tại; FAILED đã xác minh và quyết định retry mới sinh ID mới. Transport redelivery không đồng nghĩa được thử hoàn lần mới.
- Hoàn thành công trả kho đúng allocation cũ, không mở vé lại hoặc giải phóng ghế đã thuộc lượt mua mới. Không trả coupon CONSUMED. Compensation không Ticket/kho/doanh thu vé/hoa hồng.

## 3. Hợp đồng file, DTO và Service

Các path viết ngắn sau đây dùng prefix Java `src/main/java/vn/ticketscenter/`, test `src/test/java/vn/ticketscenter/`, JSP `src/main/webapp/WEB-INF/views/`, assets `src/main/webapp/assets/`, SQL object `database/migrations/`. File đã tồn tại chuyển Create thành Modify và giữ thay đổi của người khác.

| Nhóm | File phải cung cấp |
|---|---|
| Model | `model/fulfillment/Refund.java`, `RefundStatus.java`, `RefundPurpose.java`, `RefundReason.java`, `RefundTransferResult.java`, `CheckInResult.java` |
| Persistence | `persistence/fulfillment/RefundTicketRecord.java`, `RefundTransferLogRecord.java`, `TicketCheckInLogRecord.java`, `MockRefundProviderRecord.java` |
| Service | `service/fulfillment/RefundService.java`, `RefundExecutionService.java`, `CancellationService.java`, `CheckInService.java` |
| Repository | `repository/fulfillment/RefundRepository.java`, `RefundTransferRepository.java`, `CancellationRepository.java`, `CheckInRepository.java` |
| Controller/JSP | `controller/fulfillment/RefundRequestServlet.java`, `CheckInServlet.java`; `fulfillment/refund-request.jsp`, `my-refunds.jsp`, `refund-detail.jsp`, `check-in-events.jsp`, `check-in.jsp` |
| Integration/job | `integration/payment/RefundProviderAdapter.java`, `MockRefundProviderAdapter.java`; `job/RefundJob.java`, `EventCancellationJob.java` |
| DDL/runbook | `0040_refunds_checkin.sql`; `docs/backend/thai-contract.md`, `refund-recovery.md`, `thai-handoff.md` |

### 3.1. Model đủ phương thức diagram

```java
// Chữ ký tài liệu: Money→BigDecimal, Identifier→UUID, Boolean→boolean.
static Refund requestForTickets(Order order, List<Ticket> tickets,
    RefundReason reasonType, String reason, Instant now);
static Refund createCompensation(Payment payment, String reason);
void adoptEventCancellation();
void approve();
void reject(String reason);
void beginAttempt(UUID attemptId);
void recordAttemptResult(UUID attemptId, RefundTransferResult result,
    String reference, Instant processedAt);
void completeWithoutTransfer();
```




Thuộc tính nghiệp vụ: tickets/payment/amount/purpose/reasonType/reason/rejectionReason/status/currentAttemptId/providerReference/processedAt. Payment nullable **chỉ khi amount=0**; đơn có tiền nhưng một vé paidAmount=0 vẫn có thể giữ acceptedPayment. CUSTOMER_REFUND có tập vé cùng Order; PAYMENT_COMPENSATION không vé và amount=Payment.amount. id/orderId/createdAt/version và dữ liệu log là persistence được ghi rõ, không dùng log làm nghĩa vụ thứ hai.

### 3.2. DTO ở `dto/fulfillment/`

Mỗi tên tương ứng một file Java; money=BigDecimal nội bộ/chuỗi HTTP, time=Instant, UUID không viết tắt, `?` là nullable. Không trả entity graph hoặc QR rõ.

| DTO/command | Fields và validation |
|---|---|
| RefundCommand | orderId UUID, ticketIds List<UUID> duy nhất 1–8, reasonType CUSTOMER_REQUEST, reason String?; không EVENT_CANCELLATION/amount/paymentId/userId |
| RefundFilter | status RefundStatus?, purpose RefundPurpose?, eventId UUID?, fromUtc time?, toUtc time?; cả hai mốc cùng có hoặc cùng thiếu; from<to; dùng createdAt và [from,to) |
| RefundDecisionCommand | decision APPROVE/REJECT, rejectionReason String?; REJECT bắt buộc lý do, APPROVE không nhận lý do từ chối |
| RefundTicketDto | ticketId UUID, eventId UUID, zoneName String, seatLabel String?, paidAmount money, status TicketStatus |
| RefundDto | id UUID, orderId UUID, eventId UUID, purpose, reasonType?, reason?, rejectionReason?, amount money, status, currentAttemptId UUID?, providerReference String?, processedAt time?, createdAt time, tickets List<RefundTicketDto> tối đa8 |
| RefundAttemptDto | attemptId UUID, refundId UUID, status PENDING/SUCCEEDED/FAILED/UNKNOWN, providerReference String?, createdAt time, processedAt time?; không amount/purpose nguồn thứ hai |
| RefundableTicketsDto | orderId UUID, eventId UUID, requestAllowed boolean, reasonCode String?, tickets List<RefundTicketDto>; chỉ vé đủ điều kiện, không tính lại giá |
| CheckInCommand | eventId UUID, ticketCode String bounded; validate syntax/size nhưng không phản chiếu QR vào lỗi/log |
| CheckInDto | result SUCCESS/INVALID_CODE/WRONG_EVENT/ALREADY_USED/OUTSIDE_WINDOW/INACTIVE_TICKET/EVENT_CANCELLED, ticketId UUID?, eventId UUID, zoneName String?, seatLabel String?, scannedAt time, message String; không tiền/QR/email |
| CheckInWindowDto | eventId UUID, opensAt time, closesAt time, serverNow time, allowed boolean, reasonCode String? |
| CheckInFilter | result CheckInResult?, fromUtc time?, toUtc time?; khoảng [from,to), sort allowlist scannedAt/id |
| CheckInLogDto | id UUID, eventId UUID, ticketId UUID?, actorId UUID, result CheckInResult, scannedAt time, zoneName String?, seatLabel String?; không raw QR |
| CancellationProgressDto | eventId UUID, eventStatus, processing boolean, processedOrders long, pendingOrders long, completedRefunds long, unresolvedRefunds long, exceptions long, updatedAt time; “đơn đã xử lý” khác “tiền đã hoàn” |
| RefundProviderResult | attemptId UUID, result RefundTransferResult, providerReference String?, processedAt time?; UNKNOWN/FAILED không bịa mốc chuyển tiền thành công |

Chốt giới hạn kỹ thuật trong `thai-contract.md`: reason/rejectionReason 1–1000 ký tự sau trim nếu bắt buộc, ticketCode tối đa512 ký tự/đúng format generator Liêm, body tối đa theo Khánh. Đây là giới hạn transport, không tự thêm chính sách hoàn/phí. Query HTTP dùng `from/to`, DTO dùng fromUtc/toUtc; PageRequest từ Khánh.

### 3.3. Service/adapter bàn giao từ M0

```java
// RefundService
RefundableTicketsDto refundableTickets(ActorContext actor, UUID orderId);
RefundDto request(ActorContext actor, RefundCommand command);
Page<RefundDto> listOwn(ActorContext actor, PageRequest page);
List<RefundDto> listForOrder(ActorContext actor, UUID orderId);
Page<RefundDto> listAdmin(ActorContext actor, RefundFilter filter, PageRequest page);
RefundDto get(ActorContext actor, UUID refundId);
Page<RefundAttemptDto> listAttempts(ActorContext actor, UUID refundId, PageRequest page);
RefundDto decide(ActorContext actor, UUID refundId, RefundDecisionCommand command);
RefundDto retry(ActorContext actor, UUID refundId);
RefundDto retryCompensation(ActorContext actor, UUID paymentId);
// RefundExecutionService: internal, không public HTTP/result setter.
RefundDto startCompensation(UUID refundId, UUID paymentId);
JobResult executeCurrent(UUID refundId);
// CancellationService
CancellationProgressDto cancel(ActorContext actor, UUID eventId);
CancellationProgressDto progress(ActorContext actor, UUID eventId);
JobResult processBatch(UUID eventId);
// CheckInService
Page<EventDto> listEvents(ActorContext actor, UUID organizationId, PageRequest page);
CheckInWindowDto window(ActorContext actor, UUID eventId);
CheckInDto checkIn(ActorContext actor, CheckInCommand command);
Page<CheckInLogDto> history(ActorContext actor, UUID eventId, CheckInFilter filter, PageRequest page);
// RefundProviderAdapter: server-only, I/O ngoài transaction.
RefundProviderResult submit(UUID attemptId, UUID paymentId, BigDecimal amount);
RefundProviderResult query(UUID attemptId);
```

Khánh `/me/refund-requests` gọi listOwn, JSP my-refunds.jsp. Liêm `/orders/{id}/refundable-tickets` và UI06 gọi refundableTickets/listForOrder, tự sửa OrderServlet. Đông gọi listEvents/window/history từ wildcard mình. Vương AdminServlet gọi listAdmin/get/decide/retry/retryCompensation/cancel/progress. Quyền kiểm ở Service/query cả JSON/HTML, không chỉ controller.

`startCompensation` dùng SYSTEM/WORKER_TECH được cấu hình. Thái gọi SP09 do Liêm sở hữu qua `RefundRepository.beginCompensation(EntityManager em, UUID refundId, UUID paymentId, UUID attemptId, boolean retryFailed)`; verified CAPTURED/Order/amount đọc DB, không giả trạng thái gateway từ payload. Nhánh admin retry compensation và worker dùng nguyên logic SP09, không tạo SP thứ hai hoặc PaymentService trùng.

### 3.4. HTTP và outbox

| Route/chủ adapter | Input → output/quyền |
|---|---|
| Liêm GET `/orders/{id}/refundable-tickets` | RefundableTicketsDto JSON hoặc JSP refund-request.jsp UI08, owner trước startTime |
| Thái POST `/refund-requests` | RefundCommand →201 RefundDto, owner+CSRF; lặp lại chính xác tập vé còn open có thể200 nghĩa vụ cũ, không tạo thứ hai; khác tập xung đột409 |
| Khánh GET `/me/refund-requests` | Page<RefundDto>/my-refunds.jsp, owner |
| Thái GET `/refund-requests/{id}`, `/{id}/attempts` | RefundDto/Page<RefundAttemptDto>; owner hoặc ADMIN, HTML/JSON detail hoặc JSON attempts |
| Thái GET `/check-ins?organizationId=...&eventId=...` | JSP chọn Event/UI15 hoặc quét/UI16; membership active MANAGER/CHECK_IN_STAFF cùng org |
| Thái POST `/check-ins` | CheckInCommand →200 CheckInDto kể cả từ chối nghiệp vụ;400 transport,401/403 quyền,503 lỗi hệ thống; CSRF |
| Đông GET `/organizations/{id}/check-in-events`, `/events/{id}/check-in-window`, `/events/{id}/check-ins` | Theo TEAM-CONTRACT §3.5: danh sách mặc định HTML UI15 dùng JSP Thái, `Accept: application/json` trả Page<EventDto>; window/history chỉ JSON CheckInWindowDto/Page<CheckInLogDto>, không forward JSP UI16; cùng scope membership |
| Vương GET `/admin/refund-requests`, `/{id}`; POST `/{id}/decision`, `/{id}/retry` | ADMIN, RefundDto/Page; decision/retry200, state409, POST CSRF |
| Vương POST `/admin/payments/{id}/compensation/retry` | ADMIN, body{}, retryCompensation200; chỉ RETRYABLE sau FAILED verified |
| Vương POST `/admin/events/{id}/cancel`, GET `/{id}/cancellation-progress` | ADMIN, cancel202 tiến độ, replay200; GET readonly; không chuyển tiền trong request |

OutboxPublisher của Liêm enqueue trên **EntityManager hiện tại trước commit**. Chỉ Liêm có dispatcher/lease/scheduler. Thái đăng ký JobHandler REFUND_TRANSFER/1 và EVENT_CANCELLATION/1:

- REFUND_TRANSFER payload `{schemaVersion:1,refundId,attemptId}`; key `refund-transfer:{refundId}:{attemptId}`. Handler kiểm currentAttemptId từ DB; message cũ không sinh attempt mới, không hồi quy trạng thái.
- EVENT_CANCELLATION payload `{schemaVersion:1,eventId}`; key `event-cancellation:{eventId}`. Cursor/progress lấy DB, payload không là nguồn tiền/kho. JobResult RETRY tiếp tục batch/current attempt, FAILED giữ evidence để vận hành.
- EMAIL dùng MailPayloadV1 Khánh: schemaVersion1, template REFUND_REJECTED/REFUND_COMPLETED, recipientUserId/orderId/refundId/occurredAt; key `refund-rejected:{id}` hoặc `refund-completed:{id}`. Không QR/email/password/OTP trong payload. Mail lỗi không sửa Refund/Ticket.
- Liêm PAYMENT_COMPENSATION handler gọi startCompensation rồi outbox REFUND_TRANSFER; không để hai handler cùng submit provider. Retry dispatcher chỉ gọi executeCurrent của cùng nghĩa vụ/attempt.

## 4. Các nhiệm vụ

Mỗi task có evidence `docs/evidence/thai/THAI-NN.md`: commit/env/fixture/commands/expected/actual/exit code/DB trước-sau/log đã lọc/PASS-FAIL-BLOCKED. SQL test tại `database/tests/thai/THAI-NN.sql`, dùng sqlcmd `-b` và auth runbook; không password command line. Unit `mvn -B -Dtest=<ClassTest> test`, SQL IT `mvn -B -Psqlserver-it -Dit.test=<ClassIT> verify`, browser `mvn -B -Pbrowser-it -Dit.test=<ClassIT> verify`. Các lớp bên dưới dùng prefix test đã ghi; thiếu profile/DB phải fail rõ. Không gọi tuần tự rồi kết luận concurrency: dùng hai connection/barrier/timeout hữu hạn. Reviewer ghi theo người nhận đầu ra.

### THAI-01 — Refund đủ diagram, DTO và schema nền

**Mục tiêu/mốc:** M0 cung cấp Model/schema/contract cho SP09 Liêm trước khi pipeline hoàn hoạt động. **Nguồn:** spec §5/7/8.2, C15/C20 và diagram Refund. **Nhận:** User/Event/Order/Payment/Ticket nền của Khánh/Đông/Liêm; Vương ghép FK.

**Tạo:** Model/persistence/DTO/Service interfaces §3, `0040_refunds_checkin.sql`; `model/fulfillment/RefundTest.java`, `acceptance/ThaiMappingIT.java`, SQLtest01. Interface/stub ở M0 phải báo chưa triển khai, không success giả.

- [ ] Tạo đủ tám phương thức diagram và toàn bộ thuộc tính/quan hệ. Test state REQUESTED→APPROVED→PROCESSING→NEEDS_RECONCILIATION/RETRYABLE/COMPLETED, REJECTED và zero complete.
- [ ] C15 kiểm amount không âm, payment nguồn đúng, CUSTOMER_REFUND có vé/COMPENSATION không vé; các điều kiện xuyên bảng ở SP/constraint tương đương, không giả CHECK đọc bảng khác. C20 tập vé cùng Order.
- [ ] Unique attemptId, FK currentAttempt đúng Refund do Vương ghép; RefundTicket unique open/ticket không time predicate; filtered unique Refund(paymentId) WHERE purpose=PAYMENT_COMPENSATION bảo đảm một nghĩa vụ/khoản thu.
- [ ] Log lần thực hiện không có amount/purpose nguồn thứ hai. Payment nullable chỉ amount0; processedAt/providerReference nullable đúng chuyển tiền; không cascade xóa lịch sử.
- [ ] Khóa DTO/signatures/payload §3 với caller, giữ BigDecimal; round-trip enum/Instant/UUID với Hibernate validate. M0 chưa yêu cầu frontend hoàn tất.

**Kiểm chứng:** RefundTest/ThaiMappingIT/SQLtest01; evidence01. **Bàn giao/reviewer:** Liêm dùng createCompensation/schema cho SP09, Khánh dùng query DTO, Vương nhận keys/FK/blocker.

### THAI-02 — F07, quyền và cửa sổ check-in

**Mục tiêu/mốc:** M1 có query thời gian/quyền cho Đông; M2 chọn Event đúng scope. **Nguồn:** spec §4/6.8, F07. **Phụ thuộc:** KHANH-03/06 và DONG-01/02 schema/query membership; THAI-01 DTO.

**Tạo:** `0200_F07.sql`, CheckInService/Repository query, `model/fulfillment/CheckInWindowTest.java`, `acceptance/ThaiCheckInWindowIT.java`, SQLtest02.

- [ ] F07 lấy Event và UTC server theo chữ ký spec, không receive now từ browser. Khoảng `[start−60min,end)`, CANCELLED không allowed.
- [ ] listEvents/window đọc current membership MANAGER/CHECK_IN_STAFF đúng org; không V06/finance, không show đơn/email/paidAmount.
- [ ] Clock fixture Event start06:00/end08:00 UTC:04:59:59 không,05:00 được,07:59:59 được,08:00 không. Đây là expected, không test log đã chạy.
- [ ] Role inactive/revoked hoặc org khác bị403/404; có nhiều membership không cộng quyền orgA sang orgB. Window trả opensAt/closesAt/serverNow/reasonCode để UI giải thích.

**Kiểm chứng:** WindowTest/WindowIT/SQLtest02/evidence02. **Bàn giao:** Đông delegate Event/OrganizationServlet; Khánh/Vương review authorization.

### THAI-03 — SP04/TX04 check-in nguyên tử

**Mục tiêu/mốc:** M2 một lần vào cửa và lịch sử từ chối đúng. **Nguồn:** spec §6.8, SP04/TX04. **Phụ thuộc:** THAI-01/02, LIEM-01 Ticket.checkIn và vé phát hành; locking chung.

**Tạo:** `0400_SP04.sql`, CheckInRepository/Service mutation, `acceptance/ThaiCheckInIT.java`, SQLtest03. **Sửa:** Ticket methods do Liêm sửa khi review, Thái không viết Model thứ hai.

- [ ] Kiểm membership/quyền trước TicketCheckInLog; khóa Event/Ticket theo hợp đồng và chuyển ACTIVE→USED có điều kiện cùng log SUCCESS.
- [ ] Mã hợp lệ format nhưng không tồn tại →INVALID_CODE/ticketIdNULL; sai Event/đã USED/ngoài giờ/REFUND_PENDING/REFUNDED/INVALIDATED/CANCELLED →business result, log và commit. Không exception khiến log mất.
- [ ] Thiếu quyền không TicketCheckInLog; malformed/oversize400 không query QR. Không raw ticketCode trong DB/log/DTO/evidence.
- [ ] Hai connection cùng Ticket: một SUCCESS, một ALREADY_USED có history; lỗi sau UPDATE trước log rollback Ticket về ACTIVE. SP chạy standalone và outerTX không commit caller.
- [ ] Chia sẻ ảnh QR vẫn đủ vào cửa, không yêu cầu người cầm vé đăng nhập; người kiểm tra phải có quyền. Không thêm chuyển quyền sở hữu.

**Kiểm chứng:** ThaiCheckInIT/SQLtest03/evidence03. **Reviewer:** Liêm trạng thái Ticket, Đông Event/kho, Vương principal R03.

### THAI-04 — V05/IX05 và lịch sử quét

**Mục tiêu/mốc:** M2 lịch sử phân trang không lộ QR hoặc tài chính. **Nguồn:** V05/IX05, UI16/17. **Phụ thuộc:** THAI-03.

**Tạo:** `0300_V05.sql`, `0150_IX05.sql`, history query; `acceptance/ThaiCheckInHistoryIT.java`, SQLtest04; `database/benchmarks/thai/IX05.sql`.

- [ ] V05 giữ cả từ chối/ticketIdNULL; LEFT JOIN đúng để không mất mã lạ. Fields chỉ ID/result/time/context tối thiểu.
- [ ] Filter Event/[from,to)/result, page1/default20/max100, scannedAt/id allowlist; total trên toàn tập lọc. Current membership áp dụng mỗi query.
- [ ] Test cùng timestamp, page cuối/empty, hai org, deleted/inactive membership; camera/manual thấy chung history. Invalid QR không biến thành email/paidAmount.
- [ ] Benchmark IX05 exact key/include spec với dataset/query params và actual plan/IO/TIME trước-sau; gửi Vương, không ghi trước mức cải thiện.

**Kiểm chứng:** HistoryIT/SQLtest04/evidence04. Đông nhận history delegate; Vương nhận check-in report/caller/benchmark.

### THAI-05 — JSP UI15/UI16 và camera/manual

**Mục tiêu/mốc:** M2 chọn Event, quét/nhập mã và đọc kết quả qua một endpoint. **Nguồn:** UI15/16 và prototype. **Phụ thuộc:** THAI-02–04, layout/API client Khánh, browser profile Vương.

**Tạo:** CheckInServlet, `fulfillment/check-in-events.jsp`, `check-in.jsp`, `assets/js/check-in.js`; `acceptance/ThaiCheckInHttpIT.java`, `browser/ThaiCheckInBrowserIT.java`.

- [ ] GET `/check-ins?organizationId=...` chọn Event; có eventId thì cùng scope render scanner. Nav/context giữ org/event đã kiểm, không authorize theo hidden input.
- [ ] Camera online HTTPS/getUserMedia: xin quyền, thông báo denied/no device, fallback nhập tay, stop tracks khi rời trang. Khánh khóa thư viện QR sau smoke, không tự thêm package chưa kiểm.
- [ ] Camera/manual POST cùng CheckInCommand/CSRF; debounce chỉ trải nghiệm, DB vẫn chốt một success. Mã không ghi console/history và không echo error.
- [ ] Hiển thị tên Event/window/kết quả rõ, quét tiếp/history, loading/empty/error, label/focus/keyboard/mobile theo frame gốc.
- [ ] Browser camera stub chỉ kiểm UI decode; HTTP/SQL thật xác nhận check-in. Thử manual, repeated scan, role revoked, camera denied và ngoài giờ.

**Kiểm chứng:** HTTPIT/browserIT/evidence05; nếu browser profile chưa có ghi BLOCKED phần browser. Reviewer Khánh layout, Liêm/Đông integration.

### THAI-06 — F04 và SP05/TX05 gửi yêu cầu hoàn

**Mục tiêu/mốc:** M3 chủ đơn chọn vé đúng điều kiện và tạo nghĩa vụ một lần. **Nguồn:** spec §6.9, F04/SP05/TX05. **Phụ thuộc:** THAI-01, Liêm Order/Ticket/paidAmount, Đông Event.

**Tạo:** `0200_F04.sql`, `0400_SP05.sql`; RefundService request/refundableTickets; `acceptance/ThaiRequestRefundIT.java`, SQLtest06.

- [ ] F04 kiểm ticket ACTIVE/un-used, Order chủ hiện tại, trước startTime, chưa open refund. RefundCommand chỉ reasonType CUSTOMER_REQUEST; tiền lấy tổng paidAmount đã lưu.
- [ ] Khóa Order/Event/Tickets ổn định, kiểm cùng Order/C20/all-or-nothing; insert Refund REQUESTED/tập vé/open marker và Ticket REFUND_PENDING/audit cùng TX05.
- [ ] Replay đúng tập open trả nghĩa vụ cũ, không tạo thứ hai; subset/overlap khác trả409. Unique open/ticket bảo vệ race, không chỉ Java pre-check.
- [ ] Ví dụ vé paid160000+240000 chọn vé đầu →amount160000 dù giá niêm yết/coupon hiện tại đã đổi; không lấy200000. Zero-ticket vẫn tạo REQUESTED amount0.
- [ ] Test sai chủ/crossOrder/duplicateIDs/USED/expired boundary/event cancel; request đua check-in chỉ một transition; lỗi sau nối vé thứ hai rollback tất cả.

**Kiểm chứng:** RequestRefundIT/SQLtest06/evidence06. Liêm review query adapter `/orders/{id}/refundable-tickets`; Vương nhận nghĩa vụ/blocker.

### THAI-07 — V07/IX10 và query lịch sử hoàn

**Mục tiêu/mốc:** M3 tài khoản/UI06/UI21 đọc một nghĩa vụ cùng current attempt, lịch sử phân trang riêng. **Nguồn:** V07/IX10, UI08/21. **Phụ thuộc:** THAI-06 và THAI-01 log schema.

**Tạo:** `0300_V07.sql`, `0150_IX10.sql`; listOwn/listForOrder/listAdmin/get/listAttempts; `acceptance/ThaiRefundReadIT.java`, SQLtest07; `database/benchmarks/thai/IX10.sql`.

- [ ] V07 một dòng/Refund, không join nhiều attempts làm nhân amount. CurrentAttemptId tách history; logs không chứa source amount trùng.
- [ ] listForOrder kiểm owner từ DB, ADMIN chỉ dùng route/quyền cho phép; listAdmin current ADMIN. Detail/list/attempts cùng scope HTML/JSON.
- [ ] Ticket list<=8, history attempts Page độc lập, total đúng; state/purpose/event/date filter/sort allowlist, không QR/payment secret.
- [ ] Test2FAILED+1UNKNOWN rồiSUCCEEDED vẫn một amount, zeroRefundNULLpayment/processedAt đúng, crossowner404, oldFAILED không nghĩa vụ pending mới.
- [ ] IX10 benchmark trước-sau; gửi Vương caller/F09 definitions, Khánh MeServlet DTO và Liêm UI06 dependency.

**Kiểm chứng:** RefundReadIT/SQLtest07/evidence07. Reviewer Khánh/Liêm/Vương.

### THAI-08 — SP10/TX10 quyết định, retry và zero refund

**Mục tiêu/mốc:** M3 ADMIN quyết định an toàn; hoàn0đ hoàn tất ngay không giao dịch chuyển tiền giả. **Nguồn:** §6.9/6.10, SP10/TX10. **Phụ thuộc:** THAI-06/07, Liêm Ticket methods và Đông trả kho; outbox SPI Liêm.

**Tạo:** `0400_SP10.sql`, decide/retry trong RefundService; `acceptance/ThaiRefundDecisionIT.java`, SQLtest08.

- [ ] REQUESTED approve/reject; reject cần lý do, gọi restoreAfterRejection chỉ Event chưa CANCELLED. Reject đua cancel không phục hồi vé Event đã hủy.
- [ ] Approve có tiền: APPROVED→beginAttempt backend UUID→PROCESSING/logPENDING/outboxREFUND_TRANSFER cùng TX10. Lặp approve không attempt mới; quyết định trái terminal409.
- [ ] Amount0: completeWithoutTransfer→COMPLETED/TicketREFUNDED/trả kho/audit/email, không log chuyển tiền/providerReference/processedAt giả. Coupon CONSUMED giữ nguyên.
- [ ] Retry chỉ RETRYABLE sau current logFAILED verified và ADMIN quyết định; UUID mới, nguyên amount/ticket set. UNKNOWN trả409 hướng reconcile current ID.
- [ ] EVENT_CANCELLATION tự duyệt chỉ SP17 và worker context đúng Event CANCELLED. Buyer không tự gửi loại này. Fault sau insert attempt rollback approve/log/outbox.

**Kiểm chứng:** DecisionIT/SQLtest08/evidence08, zero seated/standing/mixed-paid ticket và outerTX. Reviewer Vương UI21/principal, Liêm/Đông kho/trạng thái.

### THAI-09 — Adapter mô phỏng hoàn bền vững

**Mục tiêu/mốc:** M3 submit/query theo attemptId có thể phục hồi restart; không tiền thật. **Nguồn:** §6.9, §8.4/§14.9. **Phụ thuộc:** THAI-08, config/ClockProvider Khánh, deployment storage Vương.

**Tạo:** RefundProviderAdapter/MockRefundProviderAdapter, `dto/fulfillment/RefundProviderResult.java`; `integration/payment/MockRefundProviderAdapterTest.java`, `acceptance/ThaiRefundProviderRecoveryIT.java`; `docs/backend/refund-recovery.md`.

- [ ] Chốt submit/query §3; cùng attemptId/paymentId/amount trả cùng kết quả, khác payload conflict, timeouts UNKNOWN không FAILED mặc định.
- [ ] Mô phỏng SUCCEEDED/FAILED/UNKNOWN theo fixture/config server-only, không request setter. Chốt persistence kỹ thuật `MockRefundProviderLedger` trong 0040: attemptId PK, paymentId UUID, amount decimal(19,0), status/ref/provider processedAt/createdAt/version. Mapping `MockRefundProviderRecord` và repository JPA ở `repository/fulfillment/MockRefundProviderRepository.java`; đây là trạng thái nhà cung cấp mô phỏng, không Refund/attempt nguồn thứ hai. Mỗi submit/query dùng transaction riêng ngắn ngoài transaction nghiệp vụ trên WORKER_TECH, unique attemptId/kiểm amount bảo vệ replay. Test/demo DB namespace riêng; không memory-only hoặc đĩa container tạm cho restart evidence.
- [ ] UNKNOWN query cùng ID đến verified terminal; nếu chưa rõ giữ UNKNOWN, không submit attempt mới. amount0 không gọi adapter.
- [ ] I/O ngoài TX và timeout hữu hạn; log chỉ attemptID/reference đã lọc, không credentials/payment secrets. Phần mô phỏng ghi nhãn rõ.
- [ ] Restart sau provider thành công trước app ghi SQL rồi query lại →cùng SUCCEEDED, một lần hiệu lực; hai worker cùng ID không hai kết quả. Ledger không endpoint công khai; fixture UNKNOWN→terminal chỉ test/server config có kiểm soát, không sửa nghĩa vụ Refund trực tiếp.

**Kiểm chứng:** AdapterTest/RecoveryIT/evidence09. Khánh review config/timeout, Vương cấp quyền tối thiểu ledger/backup; không tạo web provider hoặc scheduler mới.

### THAI-10 — SP11/TX11 ghi kết quả, trả kho và IX08

**Mục tiêu/mốc:** M3 một thành công nghĩa vụ, một lần trả kho, giữ đúng trạng thái chưa rõ. **Nguồn:** SP11/TX11/IX08, §6.9. **Phụ thuộc:** THAI-08/09, Liêm Ticket và Đông allocation/kho; khóa cùng Vương settlement.

**Tạo:** `0400_SP11.sql`, `0150_IX08.sql`; RefundTransferRepository/result coordination; `acceptance/ThaiRefundResultIT.java`, SQLtest10; `database/benchmarks/thai/IX08.sql`.

- [ ] Chỉ WORKER/integration verified result với đúng refund/currentAttemptId. Terminal replay trả kết quả cũ, không hồi quy SUCCEEDED; stale attempt không ghi đè current.
- [ ] SUCCEEDED: RefundCOMPLETED/reference/processedAt, log, CUSTOMER ticketsREFUNDED/trả đúng kho/audit/email cùng TX11. PAYMENT_COMPENSATION chỉ nghĩa vụ/log, không tác động Ticket.
- [ ] FAILED→RETRYABLE, UNKNOWN→NEEDS_RECONCILIATION; vé vẫn REFUND_PENDING/kho chưa trả, không user retry mù. amount không lấy result payload.
- [ ] Không vượt Payment.amount xét các hoàn thành công; accepted source cùngOrder. Kho trả chỉ bán lại khi Event còn bán; không giải phóng allocation của buyer mới do callback cũ.
- [ ] Test duplicate/two workers/success sauUNKNOWN/fault sau trả ghế/outerTX; rollback trả cả log/ticket/kho. Race SP15 confirm phải tuần tự hóa, không snapshot sai.

**Kiểm chứng:** ResultIT/SQLtest10/evidence10 và IX08 benchmark. Reviewer Đông/Liêm kho, Vương blockers/finance.

### THAI-11 — RefundJob và phối hợp bù trừ SP09

**Mục tiêu/mốc:** M3 hoàn sau commit, reconcile/retry đúng; compensation dùng SP09 hiện hữu. **Nguồn:** §8.4, SP09/SP11. **Phụ thuộc:** LIEM worker SPI/SP09; THAI-08–10.

**Tạo:** RefundJob/RefundExecutionService; `job/RefundJobTest.java`, `acceptance/ThaiRefundWorkerIT.java`, SQLtest11; sửa thai-contract/recovery runbook.

- [ ] Register REFUND_TRANSFER/1 duy nhất; load nghĩa vụ/current attempt dưới TX ngắn, commit rồi provider submit/query, TX khác SP11. Payload không nguồn quyền/amount.
- [ ] Redelivery PROCESSING cùng attempt: adapter idempotent; NEEDS_RECONCILIATION query cùng ID; RETRYABLE không tự beginAttempt; terminalack SUCCEEDED không mutation.
- [ ] Compensation APPROVED: Liêm PAYMENT_COMPENSATION gọi startCompensation; Thái gọi SP09 với verified capture từ DB và stable backend attemptID, enqueue REFUND_TRANSFER cùng TX. Không hai handler submit cùng đường.
- [ ] Admin retryCompensation(paymentId) xác minh payment/refund RETRYABLE và current ADMIN. Service chọn WORKER_TECH cho nhánh SP09 retryFailed=true, giữ actor ADMIN thật để audit; SP09 kiểm actorId/current ADMIN khi bật retryFailed, không chấp nhận worker SYSTEM tự bật cờ. Refund retry(refundId) dispatch đúng purpose. Authorization/ghi dùng ranh giới transaction đã review với Khánh/Liêm, không đổi principal giữa nested transaction.
- [ ] Lease mất sau I/O: ack theo token Liêm, trạng thái SQL vẫn idempotent; poison/stale/version payload xử lý rõ, không tạo obligation mới hoặc retry vô hạn.

**Kiểm chứng:** WorkerTest/WorkerIT/SQLtest11/evidence11; crash sau DBcommit/provider success/lease timeout và late/duplicate captured. Reviewer Liêm SPI/SP09, Khánh EMAIL, Vương quyền.

### THAI-12 — SP13/TX13 khởi động hủy Event

**Mục tiêu/mốc:** M3 hủy có hiệu lực ngay, xử lý hoàn theo lô sau commit. **Nguồn:** §6.10/SP13/TX13. **Phụ thuộc:** DONG Event.cancel/TR05, LIEM outbox/kho, THAI-01.

**Tạo:** `0400_SP13.sql`, CancellationService/Repository; `acceptance/ThaiCancelEventIT.java`, SQLtest12.

- [ ] ADMIN current, PUBLISHED và now<startTime; Event.cancel+CANCELLED+EVENT_CANCELLATION outbox+TR05 audit cùng transaction.
- [ ] Không xử lý mọi Order/provider trong TX13; cancel mới202 với progress, replay200 và không outbox thứ hai. Từ chối trạng thái không phù hợp409.
- [ ] BuyerHold/payment/checkin kiểm CANCELLED ngay sau commit. Provider captured muộn vào SP09 compensation; không Ticket mới.
- [ ] Fault sau status trước outbox rollback cả Event/audit; outerTX không commit caller. Cancel đua publish/startTime/hold/checkin đều có kiểm dưới khóa.

**Kiểm chứng:** CancelEventIT/SQLtest12/evidence12. Đông review Model/TR05; Vương adapter/UI20, Liêm payment/hold integration.

### THAI-13 — SP17/TX17 từng Order và cancellation worker

**Mục tiêu/mốc:** M3 restart tiếp tục hủy mà không nhân nghĩa vụ/trả kho. **Nguồn:** §6.10, SP17/TX17. **Phụ thuộc:** THAI-08/10/11/12, Liêm SP07/SP09, Đông kho; Job SPI.

**Tạo:** `0400_SP17.sql`, EventCancellationJob, processBatch/progress; `acceptance/ThaiCancelledOrderIT.java`, SQLtest13.

- [ ] Event CANCELLED/Order cùngEvent, mỗi TX17 một Order. Chưa trả gọi SP07; Hold không Order được worker dọn qua SP07 riêng. Child SP tham gia TX, không commit riêng.
- [ ] PAID: khóa vé; adopt REQUESTED Refund có sẵn, giữ lịch sử lý do, chuyển EVENT_CANCELLATION và tự duyệt SP10; đã APPROVED/PROCESSING/UNKNOWN tiếp tục nguyên tập/amount/attempt.
- [ ] Chỉ tạo nghĩa vụ cho vé còn đủ điều kiện, không đã hoàn/open. USED ghi exception cho quản trị; không silent refund/restore. Không thay Ticket.paidAmount/coupon consumed.
- [ ] Cursor/progress bền trên DB/outbox; batch hữu hạn cấu hình, RETRY tiếp tục; một Order lỗi không mất Order đã xong. Snapshot counters không báo tiền hoàn khi mới approve.
- [ ] Test nhiều đơn, lỗi giữa tập vé rollback Order hiện tại, crash rồi replay, callback đua cancel, request đua reject/adopt, UNKNOWN/FAILED/zero/mixed-ticket/USED cases.

**Kiểm chứng:** CancelledOrderIT/SQLtest13/evidence13 hai connection và fault sau mutation. Reviewer Liêm/Đông consistency, Vương F09/progress, Khánh email.

### THAI-14 — UI08 và API tài khoản/Admin handoff

**Mục tiêu/mốc:** M3 khách đọc điều kiện, gửi và xem kết quả; UI21 dùng DTO chuẩn. **Nguồn:** UI08/21/§6.9. **Phụ thuộc:** THAI-06–11, layout Khánh, browser Vương.

**Tạo:** RefundRequestServlet; `fulfillment/refund-request.jsp`, `my-refunds.jsp`, `refund-detail.jsp`, `assets/js/refund-request.js`; `acceptance/ThaiRefundHttpIT.java`, `browser/ThaiRefundBrowserIT.java`. MeServlet/OrderServlet/AdminServlet do chủ sở hữu sửa adapter.

- [ ] Hiển thị vé đủ điều kiện/paidAmount, reason form, amount server/trạng thái/quyết định/lịch sửattempt; UI không tính lại giảm giá hoặc nhận giá querystring.
- [ ] POST+CSRF/check owner, tiền/vé đọc lại DB; UX submit lặp không nghĩa vụ thứ hai, lỗi409 refresh điều kiện. Không nút browser markSuccess/provider outcome.
- [ ] CREATED REQUESTED ghi “chờ duyệt”; PROCESSING/UNKNOWN/RETRYABLE/COMPLETED/REJECTED ghi đúng. Refund0đ hoàn tất không nói đã chuyển khoản.
- [ ] JSON detail/attempts và JSP cùng scope; mốc HCM, VND, loading/error/empty/keyboard/mobile theo format prototype.
- [ ] Liêm UI06 listForOrder/refundable adapter; Khánh listOwn/my-refunds.jsp; Vương UI21/query/decide/retry, test cùng DTO/version. Email sau reject/completed dùng outbox đã commit.

**Kiểm chứng:** HTTPIT/browserIT/evidence14, IDOR/CSRF/time boundary/repeatedsubmit/zero; Khánh/Liêm/Vương review phần caller.

### THAI-15 — Nghiệm thu race/recovery, quyền và bàn giao

**Mục tiêu/mốc:** M4 phần Thái có bằng chứng tích hợp thật, đủ SQL/UI và hồ sơ miền. **Nguồn:** spec §12/14.9–14.14. **Phụ thuộc:** THAI-01–14 và bản ghép các miền.

**Tạo:** `acceptance/ThaiWholeFlowIT.java`, `docs/backend/thai-handoff.md`, SQLtest15; cập nhật evidence/caller matrix và benchmark IX05/08/10. Gửi phần ERD/FD/3NF/SQL/API/UI/thuyết minh/demo cho Vương.

- [ ] Fresh DB + migrations/fixtures/run WAR: mua→QR→quét; mua→request→reject/approve→refund; cancel nhiều đơn→recovery; compensation→reconcile; settlement blockers/confirm nhìn cùng tiền.
- [ ] Mỗi SP04/05/10/11/13/17 có valid/negative/boundary, lỗi sau mutation, outerTX và hai connection thật. Không dùng mock SQL để chứng minh khóa.
- [ ] Role hiện tại/revoke/IDOR/currentorg/directSP: check-in staff không finance; buyer không tự approve/UNKNOWNretry; worker không cancel admin hoặc mới retryFAILED.
- [ ] Coverage Refund đủ tám methods, C15/C20/V05/V07/F04/F07/IX05/08/10/TX04/05/10/11/13/17, UI08/15/16; log/money/QR đã lọc, không tick chưa có evidence.
- [ ] Runbook pending obligations/attempts/poison lease/USED exception và durable simulator ledger; đưa expected/actual/commit/PASS-FAIL-BLOCKED, không tự sửa SQL production bằng tay.
- [ ] Chạy unit/build/SQL/browse profiles phù hợp và hoàn thành Git task/PR theo quy trình chung. Tác giả tự cập nhật ảnh thiếu, Thái không vẽ thêm.

**Kiểm chứng:** WholeFlowIT + toàn test miền; evidence15/caller/benchmark và contribution task map. Vương/Khánh review bản ghép; Liêm/Đông review Ticket/kho.

## 5. Checklist cuối và lệnh giao cho AI

- [ ] Refund đủ thuộc tính/quan hệ/tám phương thức; DTO/interfaces thống nhất mọi caller.
- [ ] Check-in một SUCCESS, các business refusals lưu lịch sử; thiếu quyền không TicketCheckInLog; không raw QR/finance.
- [ ] Một obligation, một current attempt; UNKNOWN reconcile cùngID, FAILED retry rõ; zeroRefund không provider/log/mốc tiền giả.
- [ ] Hủy áp dụng ngay, từng Order tái lập, adopt request và USED exceptions đúng; compensation không vé/doanh thu/fee.
- [ ] SQL/HTTP/JSP/UI/tests/evidence/benchmark/runbook có kết quả thật; Git/PR/task traceability đúng quy trình.

```text
Đọc spec.md, docs/classdiagram/diagram.md, TEAM-CONTRACT.md, GIT-WORKFLOW.md và thai.md.
Kiểm repo, chọn THAI task đủ đầu vào; chỉ sửa file sở hữu, caller khác phối hợp đúng chủ.
Giữ API/DTO/SQL/model methods; không tạo scheduler/RefundRequest/CheckIn nghiệp vụ mới.
Kiểm quyền, race, rollback sau mutation, zero/UNKNOWN/replay/cancel/recovery phù hợp.
Lưu evidence theo commit, commit đơn vị đã kiểm theo Git workflow; không tự push/merge/deploy.
Báo task/file/API/SQL/UI/commands/actual/dependency; không claim mock/placeholder là tích hợp thật.
```

## Quy trình Git bắt buộc cho Thái

Đọc toàn bộ [GIT-WORKFLOW](GIT-WORKFLOW.md) trước triển khai. Tính năng và sửa lỗi đều dùng feature theo task, mặc định từ develop đã cập nhật và kiểm, PR vào develop. Chỉ ngoại lệ sửa bản main đã bàn giao khi develop còn việc chưa nghiệm thu mới rẽ feature từ main, PR main rồi đồng bộ main → develop theo GIT-WORKFLOW §7. Develop là nhánh tích hợp; main giữ bản đã nghiệm thu qua PR develop → main và tag sau kiểm. Không dùng nhánh release riêng. Chỉ stage file thuộc nhiệm vụ. Hoàn thành mỗi đơn vị có kiểm chứng thì commit code/test/migration/tài liệu liên quan, ghi footer `Task-Id`, lệnh/kết quả và evidence. Task lớn có nhiều commit/PR con; commit nền không đồng nghĩa toàn task đã đạt. Không chờ hoàn thành cả module mới commit.

Bảng dưới là tên nhánh và subject khởi điểm đã gắn đúng chức năng. Khi chỉ sửa lỗi dùng `fix`, chỉ thêm test dùng `test`, chỉ tài liệu dùng `docs`; mô tả phải phản ánh nội dung thực. Subject dài được rút gọn rõ nghĩa theo khuyến nghị72 ký tự của nhóm, không đổi task ID. PR một task mặc định vào develop, ngoại lệ base main theo §7; reviewer theo người nhận đầu ra; tiền/quyền/transaction cần hai reviewer chuyên môn. Build/SQL/browser thiếu môi trường ghi BLOCKED, không đóng task từ commit hoặc mock.

| Task | Nhánh chức năng | Subject commit khi triển khai đầy đủ hành vi |
|---|---|---|
| THAI-01 | `feature/thai/thai-01-refund-contracts` | `feat(fulfillment): define refund models DTOs and constraints` |
| THAI-02 | `feature/thai/thai-02-check-in-window` | `feat(fulfillment): validate current roles and check-in windows` |
| THAI-03 | `feature/thai/thai-03-check-in-ticket` | `feat(fulfillment): check in tickets and record outcomes atomically` |
| THAI-04 | `feature/thai/thai-04-check-in-history` | `feat(fulfillment): query scoped scan history and benchmark IX05` |
| THAI-05 | `feature/thai/thai-05-check-in-pages` | `feat(fulfillment): connect camera and manual check-in pages` |
| THAI-06 | `feature/thai/thai-06-request-refund` | `feat(fulfillment): request owner-scoped ticket refunds atomically` |
| THAI-07 | `feature/thai/thai-07-refund-queries` | `feat(fulfillment): query refund obligations and attempt history` |
| THAI-08 | `feature/thai/thai-08-refund-decision` | `feat(fulfillment): decide refunds and complete zero-value obligations` |
| THAI-09 | `feature/thai/thai-09-refund-provider` | `feat(fulfillment): simulate durable idempotent refund transfers` |
| THAI-10 | `feature/thai/thai-10-refund-result` | `feat(fulfillment): apply verified refund results atomically` |
| THAI-11 | `feature/thai/thai-11-refund-worker` | `feat(jobs): reconcile current refund attempts via shared dispatcher` |
| THAI-12 | `feature/thai/thai-12-cancel-event` | `feat(event): cancel published events and enqueue cleanup atomically` |
| THAI-13 | `feature/thai/thai-13-cancellation-worker` | `feat(jobs): process cancelled orders with restart-safe progress` |
| THAI-14 | `feature/thai/thai-14-refund-pages` | `feat(fulfillment): connect refund requests history and admin handoff` |
| THAI-15 | `feature/thai/thai-15-refund-acceptance` | `test(fulfillment): verify refund recovery permissions and integration` |

Trước commit: kiểm ownership/diff → chạy checks đúng task → `git diff --check` → stage đường dẫn cụ thể → review staged diff → commit theo mẫu chung. Báo cáo cuối của thành viên/AI phải ghi nhánh, commit, task, tests/evidence và dependency chưa ghép. Push/merge/deploy và thay cấu hình GitHub theo quyền được giao; bộ kế hoạch này không tự thực hiện các bước đó.
