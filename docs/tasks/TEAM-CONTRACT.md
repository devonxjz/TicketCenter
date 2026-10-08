# Hợp đồng phối hợp năm thành viên TicketsCenter

Ngày lập: 06/10/2026. Đồng bộ hợp đồng: 07/10/2026. Đây là kế hoạch triển khai tương lai, không phải xác nhận đã có mã hoặc đã nghiệm thu. Các đường dẫn mã nguồn, chữ ký Service và cấu hình kiểm thử dưới đây là đầu ra phải tạo. Không dùng ngày trong lịch 21 ngày cũ để suy ra tiến độ.

## 1. Nguồn chuẩn và cách sử dụng

1. Đọc [spec.md](../../spec.md) và [diagram.md](../classdiagram/diagram.md) trước khi nhận việc. Diagram là XML diagrams.net: đọc cả thuộc tính, phương thức và quan hệ, không chỉ tên lớp.
2. Đọc file thành viên: [Khánh](khanh.md), [Đông](dong.md), [Liêm](liem.md), [Thái](thai.md), [Vương](vuong.md), rồi hợp đồng này, [CONVENTIONS](CONVENTIONS.md), [API-MAP](API-MAP.md) và [COVERAGE](COVERAGE.md).
3. Spec/diagram quyết định nghiệp vụ. Hợp đồng này chốt cách phối hợp và các lựa chọn triển khai của năm người. Kế hoạch ngày cũ là nguồn tham khảo backend; khi khác phạm vi, dùng kế hoạch thành viên cho toàn dự án. Ghi bất đồng và cập nhật tất cả caller trước khi đổi hợp đồng, không tự đổi spec.
4. Khi giao cho AI hoặc đưa lên GitHub, giữ các file nguồn chuẩn, hợp đồng và năm bản nhiệm vụ trong cùng repository. Không giao một file nhiệm vụ tách khỏi các nguồn nó tham chiếu.
5. Mỗi nhiệm vụ có một người nhận chính, reviewer, đầu vào, đầu ra và bằng chứng. Mọi checkbox ban đầu chưa hoàn thành. Không tạo mã ứng dụng trong quá trình viết bộ kế hoạch này.

## 2. Mục tiêu chung và nền tảng

Hoàn thành một Maven WAR chạy trên Tomcat, đầy đủ 24 màn hình JSP, nghiệp vụ Servlet/Service/Model/JPA, SQL Server và tác vụ nền; có kiểm thử tích hợp, runbook, bản demo và hồ sơ môn học theo spec §12/§14.

- Theo spec: JDK 25, Tomcat 11.0.25/Servlet 6.1, JSP 4.0, JSTL 3.0, JPA/Hibernate, SQL Server local; Azure SQL cho môi trường online; Bootstrap và CSS chung; VNPAY Sandbox; hoàn/chi mô phỏng.
- Khánh kiểm tra tương thích và khóa phiên bản build/test/JSON/pool/JPA cùng nhóm trước khi module khác thêm dependency. Quyết định ghi tại `docs/backend/decisions.md`. Không tự chuyển nền tảng sang Spring, frontend SPA hoặc JDBC DAO song song.
- Package `vn.ticketscenter`; tầng ở ngoài, nhóm nghiệp vụ ở trong như spec §3. `com.ticketscenter` trong Maven groupId không bắt buộc là package Java.
- Model chứa hành vi của diagram; Servlet nhận đầu vào và trình bày; Service kiểm quyền/điều phối; Repository gọi SQL qua JPA. JSP dùng DTO/JSTL, không scriptlet, SQL hoặc EntityManager.
- Chỉ 15 lớp nghiệp vụ. DTO, enum, kiểu giá trị và các bản ghi persistence có thể được tạo nhưng phải phân biệt rõ; không thêm OrganizationRequest, RefundRequest, CheckIn, Payout hoặc SettlementItem làm lớp nghiệp vụ.
- Java dùng UUID, BigDecimal và Instant; SQL uniqueidentifier, decimal(19,0), UTC datetime2. HTTP tiền là chuỗi số nguyên; JSON thời gian UTC ISO-8601; giao diện Asia/Ho_Chi_Minh, tiếng Việt và VND.

## 3. Quyền sở hữu mã và màn hình

| Thành viên | Lớp nghiệp vụ do người đó định nghĩa và duy trì | Màn hình chính | Dữ liệu kỹ thuật chính |
|---|---|---|---|
| Khánh | User | UI-03, UI-24; layout và thành phần trạng thái chung | OTP/reset, session/authVersion; hạ tầng cấu hình/persistence/HTTP |
| Đông | Organization, Event, Zone, Seat | UI-01, UI-02, UI-09, UI-10, UI-11, UI-12, UI-13 | UserOrganizationRole, EventCategory, storage ảnh bìa |
| Liêm | TicketHold, TicketHoldItem, Order, OrderItem, Payment, Coupon, Ticket | UI-04, UI-05, UI-06, UI-07, UI-14 | CouponRedemption, OutboxMessage; QR; nền tảng worker/lease |
| Thái | Refund | UI-08, UI-15, UI-16 | RefundTicket, RefundTransferLog, TicketCheckInLog; worker hoàn/hủy |
| Vương | CommissionRule, Settlement | UI-17, UI-18, UI-19, UI-20, UI-21, UI-22, UI-23; bộ chọn tổ chức cho coupon admin UI-14 | SettlementOrderSnapshot, SettlementTransferLog, AuditLog; quyền DB, benchmark, triển khai/hồ sơ |

Chủ lớp phải cung cấp **đủ phương thức của diagram**, kể cả phương thức được module khác gọi: Khánh cung cấp User.assignRole/revokeRole; Đông cung cấp các chuyển kho của Zone/Seat và Event.cancel; Liêm cung cấp Ticket.checkIn/markRefundPending/restoreAfterRejection/markRefunded; Thái cung cấp Refund.createCompensation từ mốc nền. Người dùng lớp bổ sung test và yêu cầu sửa qua chủ lớp, không tự tạo bản thứ hai.

### 3.1. Servlet và ranh giới route

Các tên dưới đây là lựa chọn triển khai chung. Chủ Servlet giữ mapping và dispatch; chủ Service cung cấp nghiệp vụ. Không đăng ký hai Servlet cùng URL pattern; dùng `@WebServlet` và giữ `web.xml` cho cấu hình chung. Khánh cung cấp helpers parse path/HTTP, không xây framework router mới.

| Chủ | Servlet và URL pattern dự kiến |
|---|---|
| Khánh | `AuthServlet` `/auth/*`; `MeServlet` `/me/*`; `HealthServlet` `/health/*`; filters/bootstrap và web.xml |
| Đông | `EventServlet` `/events/*`; `OrganizationServlet` `/organizations/*`; `OrganizationRequestServlet` `/organization-requests`; `ZoneServlet` `/zones/*`; `EventCategoryServlet` `/event-categories` |
| Liêm | `HoldServlet` `/holds/*`; `OrderServlet` `/orders/*`; `TicketServlet` `/tickets/*`; `CouponServlet` `/coupons/*`; `VnpayServlet` `/payments/vnpay/*` |
| Thái | `RefundRequestServlet` `/refund-requests/*`; `CheckInServlet` `/check-ins` |
| Vương | `AdminServlet` `/admin/*`; `ReportExportServlet` `/reports/export` |

`MeServlet` gọi query Service của Đông/Liêm/Thái theo subroute. `OrganizationServlet` gọi CouponService của Liêm, ReportService của Vương và CheckInService của Thái cho các subroute tương ứng. `EventServlet` gọi CheckInService cho `/events/{id}/check-ins` và `/check-in-window`. `AdminServlet` gọi OrganizationService/EventService của Đông, RefundService/CancellationService của Thái, CouponService của Liêm và Service tài chính của Vương. Người sở hữu Servlet viết adapter gọi Service, không sao chép nghiệp vụ hoặc trực tiếp gọi SP của module khác.

UI-19/20/21 do Vương viết JSP và adapter AdminServlet; Đông/Thái cung cấp quyết định và query. UI-15 có trang `GET /check-ins?organizationId=...` để chọn sự kiện, UI-16 dùng cùng trang với eventId; POST `/check-ins` vẫn giữ hợp đồng kiểm tra vé. Đây là route trang bổ sung, không thay nghiệp vụ.

### 3.2. Chữ ký Service bàn giao

Chữ ký dưới đây là **đề xuất triển khai đã chốt cho bộ nhiệm vụ**, không phải phương thức thay thế Model. Các DTO/command là kiểu kỹ thuật, đặt tại `dto/<nhóm>/`; chủ Service tạo DTO cùng task nền. ActorContext do Khánh cấp từ session đã kiểm tra; browser không được tạo actor/role/principal. `UUID`, `Instant`, `BigDecimal`, `List<T>` và `Page<T>` thống nhất cả năm module. `Page<T>` chứa items/page/pageSize/total; các query đọc `PageRequest(page,pageSize,sort)` đã validation.

| Chủ | Giao diện cần có từ mốc nền |
|---|---|
| Khánh | `ActorContext AuthService.requireCurrentUser(HttpServletRequest request)`; `void AuthorizationService.requireAdmin(ActorContext actor)`; `void AuthorizationService.requireOrganizationRole(ActorContext actor, UUID organizationId, Set<OrganizationRole> allowed)`; `Instant ClockProvider.now()` |
| Đông | `Page<MembershipDto> MembershipService.listForUser(ActorContext actor, PageRequest page)`; `OrganizationDto OrganizationService.approve(ActorContext actor, UUID organizationId, CommissionPolicyCommand initialPolicy)`; `OrganizationDto OrganizationService.reject(ActorContext actor, UUID organizationId, String reason)`; `Page<OrganizationDto> OrganizationService.listRequests(ActorContext actor, RequestFilter filter, PageRequest page)`; `Page<OrganizationDto> OrganizationService.listOwnRequests(ActorContext actor, PageRequest page)`; `EventDto EventService.publish(ActorContext actor, UUID eventId, UUID commissionRuleId)`; `EventDto EventService.reject(ActorContext actor, UUID eventId, String reason)`; `Page<EventDto> EventQueryService.listAdmin(ActorContext actor, EventFilter filter, PageRequest page)`; `EventDto EventQueryService.get(ActorContext actor, UUID eventId)` |
| Liêm | `Page<OrderDto> OrderQueryService.listOwn(ActorContext actor, OrderFilter filter, PageRequest page)`; `Page<TicketDto> TicketQueryService.listOwn(ActorContext actor, PageRequest page)`; `HoldDto HoldService.getCurrent(ActorContext actor)`; `Page<CouponDto> CouponService.list(ActorContext actor, UUID organizationId, CouponFilter filter, PageRequest page)`; `CouponDto CouponService.create(ActorContext actor, UUID organizationId, CouponCommand command)`; `CouponDto CouponService.edit(ActorContext actor, UUID couponId, CouponCommand command)` |
| Thái | `Page<RefundDto> RefundService.listOwn(ActorContext actor, PageRequest page)`; `Page<RefundDto> RefundService.listAdmin(ActorContext actor, RefundFilter filter, PageRequest page)`; `RefundDto RefundService.get(ActorContext actor, UUID refundId)`; `RefundDto RefundService.decide(ActorContext actor, UUID refundId, RefundDecisionCommand command)`; `RefundDto RefundService.retry(ActorContext actor, UUID refundId)`; `CancellationProgressDto CancellationService.cancel(ActorContext actor, UUID eventId)`; `CancellationProgressDto CancellationService.progress(ActorContext actor, UUID eventId)`; `CheckInWindowDto CheckInService.window(ActorContext actor, UUID eventId)`; `Page<CheckInLogDto> CheckInService.history(ActorContext actor, UUID eventId, CheckInFilter filter, PageRequest page)`; `Page<EventDto> CheckInService.listEvents(ActorContext actor, UUID organizationId, PageRequest page)` |
| Vương | `OverviewDto ReportService.organizationOverview(ActorContext actor, UUID organizationId)`; `ReportDto ReportService.organizationReport(ActorContext actor, UUID organizationId, ReportFilter filter)`; `CommissionRuleDto CommissionService.getEffective(ActorContext actor, UUID organizationId, UUID ruleId, Instant now)`; `void CommissionPolicyValidator.validate(CommissionPolicyCommand command)` |

`CommissionPolicyCommand` gồm ratePercent/fixedFee/effectiveFrom/effectiveTo; Vương sở hữu kiểu và validator. SP01 của Đông tạo CommissionRule ban đầu cùng Organization/map role trong **một** transaction; không gọi Service mở transaction riêng để tạo phí. Enum RefundDecision = APPROVE/REJECT; command có decision và rejectionReason nullable chỉ khi APPROVE. Retry được máy chủ kiểm tra theo trạng thái, không nhận trạng thái chuyển tiền từ browser.

Các Service khác nằm trong file thành viên. Nếu cần đổi một chữ ký trên: người sở hữu cập nhật hợp đồng, file mình và mọi caller/test trong cùng PR. Không tự viết DTO tương tự dưới tên khác ở module dùng nó.

### 3.3. Hạ tầng dùng chung

| Thành phần | Người nhận chính | Người phối hợp/đầu ra |
|---|---|---|
| Maven WAR, dependencies, JSON/errors/validation, ClockProvider, layout/assets và helpers test | Khánh | Tất cả dùng cùng helpers và layout; Vương kiểm build bản ghép |
| JPA factory, transaction runner, principal connection, actor session context | Khánh | Vương thiết kế quyền DB, mọi chủ SP review locking/commit |
| DDL mỗi miền và grants cho object miền | Chủ miền | Vương ghép thứ tự migration, FK liên miền và catalog; không viết lại DDL của người khác |
| Outbox schema, dispatcher/lease/retry và scheduler chung | Liêm | Handler email của Khánh, ảnh của Đông, hoàn/hủy của Thái; chỉ một dispatcher/scheduler |
| MailSender và gửi email/OTP, email vé/kết quả hoàn | Khánh | Liêm/Thái tạo payload nghiệp vụ sau commit theo schema mail thống nhất; worker gọi handler |
| Fixture registry, benchmark, CI, Docker/runbook, catalog SQL và hồ sơ nộp | Vương | Mỗi người cung cấp fixture/test/evidence miền và phần báo cáo của mình |

Khánh cung cấp `TransactionRunner.required(PrincipalKind principal, ActorContext actor, Function<EntityManager,T> work): T` (Java generic `<T>`); `PrincipalKind` và mapping kết nối do Khánh/Vương chốt, không lấy từ request. Worker dùng actor hệ thống riêng, không giả session buyer. SP tự đứng độc lập quản lý transaction; khi caller đã mở thì tham gia cùng connection/transaction, không commit caller. Lưu ý định → commit → I/O ngoài → transaction ghi kết quả. Java không dirty-write rồi gọi SP lặp cùng mutation.

Liêm cung cấp `JobHandler.handle(OutboxMessageDto message): JobResult`, `JobResult` gồm SUCCEEDED/RETRY/FAILED và nextAttemptAt nullable, cùng schema payload/version/idempotencyKey/lease. Khánh/Đông/Thái chỉ đăng ký handler của mình; không mở executor/scheduler thứ hai. Retry dispatcher không cấp quyền tự động thử **attempt hoàn mới**: UNKNOWN phải reconcile currentAttemptId, FAILED đã xác minh cần quyết định retry đúng spec.

### 3.4. Chi tiết kiểu chung, worker và bàn giao trang

Các quyết định sau áp dụng cả năm file, có hiệu lực trước caller:

- Money/Decimal của diagram đều ánh xạ **BigDecimal**, không tạo Money wrapper. `dto/common/ActorContext.java` gồm `UUID actorId, PlatformRole platformRole, int authVersion, boolean emailVerified, ActorType type`; ActorType USER/SYSTEM. `transaction/PrincipalKind.java` là BUYER/MANAGER/CHECK_IN/PLATFORM_ADMIN/AUTH_TECH/WORKER_TECH. Page/PageRequest ở dto/common, ClockProvider ở config.
- Đông cung cấp `repository/identity/MembershipAuthorizationRepository.findCurrent(EntityManager em,UUID userId,UUID organizationId):Optional<MembershipAccess>`; MembershipAccess ở dto/identity có organizationId/role/active/organizationStatus. Khánh AuthorizationService dùng EntityManager hiện tại; Service/SP ghi recheck dưới khóa để chống quyền bị thu hồi giữa kiểm tra và ghi.
- CommissionPolicyCommand ratePercent BigDecimal có DDL decimal(19,6), fixedFee decimal(19,0), effective times UTC datetime2. Initial policy của SP01 truyền scalar tương đương, cùng transaction approve/rule/manager. Rate của ví dụ không là default nghiệp vụ.
- User.updateProfile phải được Khánh triển khai và unit/persistence test theo diagram; UI24 hiện chỉ xem hồ sơ/OTP/membership theo spec, không thêm POST/me/profile hoặc form sửa.
- MeServlet HTML forwarding: /me/orders→order/my-orders.jsp; /me/tickets→fulfillment/tickets.jsp; /me/refund-requests→fulfillment/my-refunds.jsp; /me/organization-requests→identity/organization-requests.jsp; /me/profile và /me/memberships→identity/profile.jsp. JSP danh sách do chủ miền viết. /me/hold **JSON-only**; UI04 do GET/orders/{id} Liêm render checkout khi PENDING_PAYMENT/detail khi đã chốt. Payment status/coupon eligibility/check-in window/history/attempts là JSON query; QR image và Return/IPN giữ representation riêng.
- Service Thái bổ sung `RefundService.listForOrder(ActorContext actor,UUID orderId):List<RefundDto>` cho UI06; `refundableTickets(actor,orderId):RefundableTicketsDto` cho OrderServlet; `listAttempts(actor,refundId,PageRequest):Page<RefundAttemptDto>`; `retryCompensation(actor,paymentId):RefundDto`. Quyền/source schema theo thai.md; caller không tạo bản DTO khác.
- Internal `RefundExecutionService.startCompensation(UUID refundId,UUID paymentId):RefundDto` của Thái được Liêm PAYMENT_COMPENSATION handler gọi; Thái dùng SP09 **do Liêm sở hữu**, cùng hợp đồng và principal kỹ thuật đã kiểm. Không browser gọi internal execute/result hoặc worker tự retry FAILED.
- OutboxPublisher của Liêm: `enqueue(EntityManager em,String type,int payloadVersion,String idempotencyKey,String payloadJson):UUID`, cùng transaction nghiệp vụ. JobHandlerRegistry `register(String type,int payloadVersion,JobHandler handler):void` và `resolve(String type,int payloadVersion):JobHandler`; duplicate registration lỗi startup.
- OutboxMessageDto ở dto/job gồm id/type/payloadVersion/idempotencyKey/payloadJson/createdAt/attempts/nextAttemptAt/leaseOwner?/leaseUntil?/leaseToken?. JobResult(status SUCCEEDED/RETRY/FAILED,nextAttemptAt?) dùng chung; acknowledge phải kiểm token lease, không chỉ owner.
- Types/version1: EMAIL (Khánh), IMAGE_CLEANUP (Đông), HOLD_EXPIRE/PAYMENT_RECONCILE/PAYMENT_COMPENSATION (Liêm), REFUND_TRANSFER/EVENT_CANCELLATION (Thái). Liêm là chủ dispatcher/scheduler duy nhất. Payload tối thiểu/keys từng type ở file chủ, định nghĩa schema trước producer.
- MailPayloadV1 ở dto/mail (Khánh): schemaVersion1, template TICKETS_ISSUED/REFUND_REJECTED/REFUND_COMPLETED, recipientUserId/orderId?/refundId?/occurredAt. Keys ticket-issued:{orderId},refund-rejected:{refundId},refund-completed:{refundId}. Không rawOTP/emailaddress/QR/password. MailMessage(to,subject,textBody,htmlBody,idempotencyKey), MailReceipt(providerReference,acceptedAt). OTP gửi ngắn sau commit, không rawOTP outbox. Email at-least-once, không hứa provider SMTP exactly-once.
- IMAGE_CLEANUP payload schemaVersion1/eventId/key/reason ORPHAN|REPLACED/notBefore; key image-cleanup:orphan:{key} hoặc image-cleanup:replaced:{key}. Event.coverStorageKey kỹ thuật nullable, kiểm live reference trước xóa; lần orphan đã xử lý không chặn cleanup replaced.
- REFUND_TRANSFER payload schemaVersion1/refundId/attemptId, key refund-transfer:{refundId}:{attemptId}; EVENT_CANCELLATION payload schemaVersion1/eventId, key event-cancellation:{eventId}. Refund/payout adapter chỉ mô phỏng và lưu ledger kỹ thuật trong DB: MockRefundProviderLedger (0040 Thái) và MockPayoutProviderLedger (0050 Vương). Adapter thực hiện transaction riêng ngoài transaction nghiệp vụ, unique attemptId/payoutId và payload/amount kiểm replay. Vương cấp WORKER_TECH quyền hẹp ledger hoàn/ADMIN quyền ledger payout, không cấp browser hay DML tài chính rộng; backup/restore có ledger, state không vào Git. SQL role không được mở thêm SP16 cho worker.
- Tie-break phân bổ discount: canonical UUID lowercase lexical Java String.compareTo; SQL CONVERT(char(36),id) COLLATE Latin1_General_100_BIN2 rồi ordinal. Không dùng SQL uniqueidentifier order khi Java dùng lexical.
- Audit source USER/SYSTEM; worker actorId NULL/SYSTEM, user/admin thiếu actor rollback vì lỗi cấu hình. TR05 duy nhất ghi EVENT_STATUS_CHANGED. SESSION_CONTEXT phục vụ audit, không quyền.
- M0 giao theo artifact: KHANH-01 build,02 types/HTTP/clock,03 transaction,04 User/schema,06 auth interfaces, phần nền07 security và11 layout/API client; DONG-01 models/DTO/Service contract và02 schema; LIEM-01 contracts và02 schema; THAI-01 Refund/schema/DTO; VUONG-01 financial models/policy,02 manifest/fixture,03 grants. MeServlet do KHANH-13 cung cấp theo M1–M3; OrganizationServlet do DONG-05 tạo và DONG-11 bổ sung adapter; AdminServlet do VUONG-12 cung cấp. Hợp đồng M0 không đồng nghĩa các Servlet này đã triển khai. Có thể khai báo interface và tạo skeleton báo chưa triển khai để phá vòng phụ thuộc; không đợi toàn bộ task integration của người trước mới cung cấp type.
- Migration order ngoại lệ khóa trước áp dụng: 0190_V09.sql→0200_F08.sql; 0300_V02.sql→0350_F03.sql; 0390_SP07.sql→0400_SP02.sql. Manifest và tên phải cùng thứ tự dependency; không đổi tên migration đã dùng chung.

### 3.5. Hợp đồng HTTP dùng chung cho duyệt tổ chức và check-in

Bảng này chốt tên trường và representation cho các adapter liên miền; API-MAP và ví dụ trong file thành viên phải đồng bộ theo bảng. Vương giữ AdminServlet/UI-19–21, Đông giữ OrganizationServlet/EventServlet, Thái giữ CheckInServlet/UI-15–16 và Service check-in.

| Route / chủ adapter | Input hoặc representation | Service / đầu ra |
|---|---|---|
| POST `/admin/organization-requests/{id}/approve` — Vương | JSON `{"initialPolicy":{"ratePercent":"5.000000","fixedFee":"10000","effectiveFrom":"2026-10-01T00:00:00Z","effectiveTo":"2027-10-01T00:00:00Z"}}`; ADMIN + CSRF | `OrganizationService.approve(actor, organizationId, initialPolicy)` của Đông; 200 OrganizationDto APPROVED, trả cùng Organization khi duyệt lặp hợp lệ |
| GET `/check-ins?organizationId=...&eventId=...` — Thái | Trang HTML; không có eventId hiển thị UI-15, có eventId hiển thị UI-16 sau kiểm tra phạm vi | `CheckInService.listEvents/window/history`; JSP `fulfillment/check-in-events.jsp` hoặc `fulfillment/check-in.jsp` |
| GET `/organizations/{id}/check-in-events` — Đông | Mặc định HTML UI-15 dùng JSP của Thái; `Accept: application/json` trả JSON | `CheckInService.listEvents(actor, organizationId, page)`; Page<EventDto> trong envelope chung |
| GET `/events/{id}/check-in-window` — Đông | Chỉ JSON, không forward JSP | `CheckInService.window(actor, eventId)`; CheckInWindowDto trong envelope chung |
| GET `/events/{id}/check-ins` — Đông | Chỉ JSON, không forward JSP; UI-16 tải lịch sử từ route này | `CheckInService.history(actor, eventId, filter, page)`; Page<CheckInLogDto> trong envelope chung |

Tên trường HTTP duyệt tổ chức duy nhất là `initialPolicy`; các trường con ánh xạ CommissionPolicyCommand của Vương. `initialCommissionPolicy` trong đặc tả SP01 là tên tham số logic của SQL, không phải trường JSON; Repository truyền các scalar `ratePercent`, `fixedFee`, `effectiveFrom`, `effectiveTo` theo DONG-04. Các con số trong ví dụ là fixture, không là chính sách phí mặc định.

Check-in yêu cầu membership đang hoạt động MANAGER/CHECK_IN_STAFF đúng tổ chức; Service kiểm lại quyền và Event cho cả HTML/JSON. Quy tắc POST `/check-ins`, trạng thái lỗi và envelope giữ theo THAI-03/API-MAP; việc chọn representation không thay logic check-in.

## 4. Danh mục SQL và người nhận chính

Tên/chữ ký/semantics SQL theo spec §14; mã dưới đây phân người sở hữu, không giảm danh mục. SQL caller qua JPA phải thực chạy. Vương ghi catalog/grants/benchmarks toàn bộ, các chủ miền viết object và test. Với class dùng chung, SQL mutation được sở hữu theo use case/SP, không theo người sở hữu class.

| Nhóm | Khánh | Đông | Liêm | Thái | Vương |
|---|---|---|---|---|---|
| Constraint | C01 | C02,C03,C04,C05,C10 | C06,C07,C08,C09,C11,C12,C13,C14 | C15,C20 | C16,C17,C18,C19 |
| View | — | V01,V02,V10 | V06,V09 | V05,V07 | V03,V04,V08 |
| SP | — | SP01,SP12 | SP02,SP03,SP06,SP07,SP08,SP09 | SP04,SP05,SP10,SP11,SP13,SP17 | SP14,SP15,SP16 |
| Function | — | F03 | F01,F06,F08 | F04,F07 | F02,F05,F09,F10 |
| Trigger | — | TR01,TR02,TR05,TR06 | TR07,TR08 | — | TR03,TR04,TR09,TR10 |
| Index | — | IX01,IX02,IX15 | IX03,IX04,IX06,IX09,IX11 | IX05,IX08,IX10 | IX07,IX12,IX13,IX14 |
| Transaction | — | TX01,TX12 | TX02,TX03,TX06,TX07,TX08,TX09 | TX04,TX05,TX10,TX11,TX13,TX17 | TX14,TX15,TX16 |
| Role/Login | Auth/technical phối hợp | R02 ca miền | R01 ca miền | R03 ca miền | R01–R04 DDL/grants và TECH, negative security tests toàn hệ thống |

Schema kỹ thuật cần unique/FK ngoài C01–C20 vẫn bắt buộc: một ACTIVE Hold/user, Order/Hold, open Refund/Ticket, Settlement/Event, snapshot(settlementId,orderId), redemption/order, ticketCode/orderCode/txnRef/attemptId/payoutId. Không coi 15 index hiệu năng thay thế các unique bảo vệ nghiệp vụ. EventCategory chốt lookup(id/code/name/active/displayOrder) do Đông tạo; catalog đếm đúng bảng thực. Refund PAYMENT_COMPENSATION có unique filtered theo paymentId; mỗi khoản CAPTURED chỉ một nghĩa vụ bù trừ.

### 4.1. Quy ước migration cho công việc thành viên

Đường dẫn `database/migrations/`; script chạy thứ tự tên tăng dần. Đây là quy ước cho bộ nhiệm vụ mới; tên Dxx trong lịch cũ giữ để truy vết. Các file chưa tạo, chủ file phải đăng ký trong `database/README.md` trước khi áp dụng chung.

| Nhóm file | Chủ và nội dung |
|---|---|
| `0010_identity.sql` | Khánh: User/OTP; khóa miền C01 |
| `0020_organizations_events.sql` | Đông: Organization/map role/category/Event/Zone/Seat; khóa miền |
| `0030_sales.sql` | Liêm: Hold/Item/Order/Item/Payment/Coupon/Ticket/redemption/outbox; khóa miền |
| `0040_refunds_checkin.sql` | Thái: Refund/RefundTicket/RefundTransferLog/TicketCheckInLog; khóa miền |
| `0050_settlements_audit.sql` | Vương: CommissionRule/Settlement/snapshot/transfer/AuditLog; khóa miền |
| `0100_cross_domain_keys.sql` | Vương ghép FK liên miền đã được chủ miền cung cấp, gồm các chu kỳ Order.acceptedPayment/Payment, Refund.currentAttempt/log. Bảng nền trước FK; không tạo FK tới bảng chưa tồn tại |
| `0150_IXnn.sql` | Chủ IX theo bảng trên; key/include theo spec; benchmark bằng script riêng |
| `0200_Fnn.sql`, `0300_Vnn.sql` | Chủ F/V; V/F phụ thuộc nhau phải đổi thứ tự file và đăng ký trước lần áp dụng đầu tiên |
| `0400_SPnn.sql`, `0500_TRnn.sql` | Chủ SP/TR; SP con phải có trước caller nếu dependency runtime/build yêu cầu, ghi rõ trong manifest |
| `0700_roles_grants.sql` | Vương: role/login/user/module permissions, bí mật từ môi trường |

Các tên `nn` là mẫu đặt tên, phải thay bằng mã thực (ví dụ `0400_SP02.sql`) khi tạo file. Không sửa migration đã dùng chung; thêm migration kế tiếp và cập nhật catalog. Vương quản lý manifest/checksum/lệnh chạy, không chạy reset lên dữ liệu demo đang sử dụng.

## 5. Format code, API và giao diện

### 5.1. Code và lỗi

- UTF-8, Java 4 spaces; class PascalCase, method/field camelCase, constant UPPER_SNAKE_CASE; SQL giữ tên chính thức spec. Khánh khóa formatter và lệnh `mvn -B verify` để mọi người dùng giống nhau.
- File nhỏ theo trách nhiệm, không tạo Service/Repository riêng cho mỗi bảng chỉ để đủ tầng. Validation client hỗ trợ trải nghiệm; backend và SQL quyết định tiền, quyền, kho và thời gian.
- HTTP thành công `{"data":{...}}`; Page `{"data":{"items":[],"page":1,"pageSize":20,"total":0}}`. UUID chuỗi, tiền `"200000"`, thời điểm `"2026-10-06T03:00:00Z"`. Pagination 1-based, default20/max100, sort allowlist và ID tie-breaker.
- Lỗi `{"error":{"code":"HOLD_EXPIRED","message":"Lượt giữ vé đã hết hạn","correlationId":"..."}}`. 400 validation; 401 session; 403 thiếu quyền; 404 không tồn tại/ngoài phạm vi cần che; 409 state/stock; 413 size; 429 rate; 503 dependency. VNPAY IPN giữ response giao thức, không envelope chung.
- User mutation POST + CSRF, kể cả auth/logout; IPN ngoại lệ chữ ký/principal kỹ thuật. Return chỉ trình bày/truy vấn trạng thái. Không route cho browser tự CAPTURED/SUCCEEDED.
- Query bind parameters, sort allowlist. Không trả entity graph hoặc secret. Log correlationId và thông tin đã lọc; SESSION_CONTEXT actor ghi đè/xóa khi mượn/trả connection.

### 5.2. JSP và JSON trên cùng route

`GET /events` và `/events/{id}` mặc định trả JSP theo spec9.4. Các list/detail có page UI mặc định trả HTML; `Accept: application/json` rõ ràng trả DTO JSON theo cùng quyền/query. Khánh cung cấp helper chọn representation; chủ Servlet ghi lựa chọn vào API-MAP. Không trả HTML cho request JSON hoặc dùng Content-Type request làm tín hiệu duy nhất của GET. Các mutation trong kế hoạch thành viên dùng JSON; form JSP submit qua JavaScript và CSRF; payment URL do server trả rồi browser chuyển hướng. Đây là lựa chọn kỹ thuật thống nhất từ các lựa chọn cho phép trong spec.

Trang VNPAY Return là HTML trạng thái, status endpoint riêng là JSON, IPN protocol; QR image endpoint kiểm owner; CSV dùng cùng filter/quyền/công thức của JSON/HTML. UI-03 chia sẻ khung auth cho register/login/OTP/forgot/reset; không gộp endpoint reset với OTP xác minh email.

JSP dưới `src/main/webapp/WEB-INF/views/<nhóm>/`; assets dưới `assets/`. Khánh cung cấp `layout/header.jspf`, `layout/footer.jspf`, `layout/notifications.jspf`, `assets/css/app.css`, `assets/js/api-client.js`. Mọi người giữ bố cục prototype làm tham khảo, cùng font/màu/khoảng cách chung; không tự thiết kế lại mỗi màn hình. Không vẽ hoặc sinh ảnh/sơ đồ còn thiếu: giữ placeholder, tác giả cập nhật bằng công cụ chuyên dụng. Ảnh upload thực là chức năng phần mềm riêng, vẫn phải triển khai.

Mỗi UI có loading/empty/success/error, label/focus/keyboard, responsive và thông báo tiếng Việt. Ghế mới chọn chưa có nghĩa đã giữ. Khi lỗi mạng ở mutation tiền/kho/check-in, đọc trạng thái thật trước retry.

## 6. Fixture và ví dụ dùng chung

Vương tạo `database/seeds/test-fixtures.sql` và `src/test/java/vn/ticketscenter/support/TestFixtures.java`; chủ miền cung cấp builder dữ liệu. Các UUID mẫu phải tồn tại trong test fixture; không dùng dữ liệu cá nhân hoặc token/cookie sống. Base time test `2026-10-06T03:00:00Z`, điều khiển ClockProvider và SQL test clock nội bộ; HTTP không nhận now.

| Đối tượng | ID mẫu/kịch bản |
|---|---|
| Buyer A verified, buyer B verified, buyer chưa verified | `00000000-0000-0000-0000-000000000001`, `00000000-0000-0000-0000-000000000002`, `00000000-0000-0000-0000-000000000003`; userName buyer_a/buyer_b/buyer_unverified |
| Tổ chức A/B, manager A đồng thời check-in B, Manager cuối, admin, User DISABLED | ID cố định trong fixture registry; chỉ registry được gán ID cho ví dụ mới, không tự đoán trong test |
| Event A published đang bán | `10000000-0000-0000-0000-000000000001`; saleStart03:00 hôm trước, saleEnd05:00, start06:00, end08:00 ngày06/10 UTC |
| Khu ngồi A giá200000, khu đứng A giá300000/capacity3 | `20000000-0000-0000-0000-000000000001`, `20000000-0000-0000-0000-000000000002`; khu ngồi2×3 |
| Ghế A1 | `30000000-0000-0000-0000-000000000001` |
| Coupon/giá0 và event DRAFT/PENDING_APPROVAL/CANCELLED | Registry bổ sung mã phần trăm30%, fixed vượt30%, hết hạn, quota1; event/zone0 cho free-order/refund; không làm hỏng fixture của ca khác |

Dùng UUID đầy đủ từ registry trong mọi request/test, không dùng ký hiệu viết tắt. Khởi tạo dữ liệu trạng thái phức tạp trực tiếp chỉ trong test DB; end-to-end tạo bằng API/SP thật.

Ví dụ POST `/holds` dùng buyer A đã login/verified, CSRF runtime:

```json
{"eventId":"10000000-0000-0000-0000-000000000001","selections":[{"zoneId":"20000000-0000-0000-0000-000000000001","seatId":"30000000-0000-0000-0000-000000000001","quantity":1}]}
```

Expected: HTTP201; một Hold ACTIVE, một item quantity1/unitPrice`"200000"`, expiresAt03:10 UTC, Seat HELD, serverNow03:00; không Order/Payment/Ticket ở bước giữ. Buyer B đua cùng ghế nhận409, không có Hold/item/kho dở dang. Hủy lặp trả kho một lần. Đây là expected test, không phải log thực thi.

## 7. Mốc phụ thuộc và cách chia năm phiên triển khai

Năm **phiên soạn nhiệm vụ** tạo năm file thành viên; người triển khai sau này làm song song theo mốc dưới đây, không đợi hoàn thành toàn bộ file của người trước. Không dùng thứ tự phiên soạn để áp đặt thứ tự người lập trình.

| Mốc | Đầu ra phải ghép và kiểm chứng | Điều kiện mở mốc tiếp |
|---|---|---|
| M0 nền chung | Khánh build WAR/helpers/layout/auth foundation; mỗi chủ cung cấp Model/DTO/schema sớm; Vương manifest/fixture/quyền; Liêm worker SPI; Đông/Thái/Vương cung cấp lớp được bên khác dùng | WAR health, SQL connection, model signatures/cross-FK và contract tests có thể chạy; DTO/query/service stubs chỉ để compile, không báo hoàn thành nghiệp vụ |
| M1 tài khoản và danh mục | Register/login/OTP, tổ chức approve+initial fee+role, Event/khu/ghế publish; UI01–03,09–13,19–20,24 cơ bản | Buyer verified, event bán thật được tạo qua API và xem JSP |
| M2 mua và vào cửa | Hold/order/coupon/VNPAY/free flow/Ticket/QR/check-in; UI04–07,14–16 | Mua→QR→check-in, các ca race/rollback/repeat; payment sandbox evidence tách adapter tests |
| M3 hoàn và tài chính | Refund/cancel worker/reconcile, fee/settlement/payout, reports/CSV/audit; mọi24UI | Hoàn0đ/cótiền, hủy restart, blocker/freeze, payouts đồng thời, quyền chéo tổ chức |
| M4 bàn giao | Fresh-clone migrations/seeds/build/WAR/local/Docker; online khi được cấp cấu hình; benchmark15IX, hồ sơ6chương/50–100trang nội dung, slide/demo/evidence | Có ma trận nghiệm thu PASS/FAIL/BLOCKED theo commit; không nhận placeholder/mock làm bằng chứng tích hợp thật |

Chu kỳ chéo được phá bằng đầu ra nền: Refund model/schema/DTO của Thái và CommissionRule của Vương cung cấp tại M0; Liêm tạo SP09 sau khi schema này có; Thái chỉ hoàn thiện worker/hoàn sau payment contract; Vương chốt đối soát sau payment/refund, không trì hoãn initial policy đến cuối.

## 8. Kiểm thử, PR và hướng dẫn AI

- Repository chung dùng GitFlow giản lược: `feature/<ten>/<task-id-lowercase>-<chuc-nang>` dùng cho tính năng và sửa lỗi, mặc định rẽ từ develop, PR về develop; bản nghiệm thu qua PR develop → main rồi tag trên SHA main đã kiểm. Không tạo nhánh release riêng. Main/develop chỉ nhận PR sau bootstrap. Ngoại lệ sửa bản main đã bàn giao khi develop còn phần chưa nghiệm thu: cùng nhánh feature từ main, PR main rồi PR main → develop, không có loại nhánh sửa khẩn cấp riêng; theo [GIT-WORKFLOW](GIT-WORKFLOW.md). Mỗi đơn vị đã kiểm phải commit Conventional Commits có Task-Id; PR theo task và reviewer phù hợp. Không tự push/merge/deploy từ việc đọc kế hoạch; người nhận làm trong nhánh được giao, không sửa phần không thuộc phạm vi.
- Mỗi task: viết ca thất bại có ý nghĩa cho behavior → triển khai → chạy ca và regression liên quan. Configuration/glue dùng smoke checks phù hợp. Không tạo test chỉ lặp lại code hoặc bỏ DB test khi thiếu SQL Server.
- Khánh tạo Maven unit/Surefire và integration/Failsafe profile `sqlserver-it`: unit `*Test`, IT `*IT`; module IT ở `src/test/java/vn/ticketscenter/acceptance/<Ten><TinhNang>IT.java`. Vương cấu hình profile `browser-it` cho browser E2E sau khi lựa chọn công cụ kiểm tra và được nhóm khóa.
- Lệnh mục tiêu: `mvn -B verify`; `mvn -B -Psqlserver-it -Dit.test=<ClassIT> verify`; `mvn -B -Psqlserver-it verify`; `mvn -B -Pbrowser-it verify`. Các profile này **cần tạo**, chưa chạy được trong repo hiện tại. SQL test chạy `sqlcmd ... -b -i database/tests/<ten>/<task-id>.sql` theo authentication trong runbook; không in mật khẩu hoặc dùng password trong command line.
- Cả SP01–SP17 có ca transaction với lỗi **sau mutation**, outer transaction/connection và hai phiên cạnh tranh thật. Trigger10 ca nhiều dòng; Index15 query/plan/reads/time trước-sau và write cost; Role4 có GRANT/REVOKE/DENY và HTTP/direct-SP sai quyền. Mock chỉ chứng minh adapter, không chứng minh SQL Server khóa/principal hoặc sandbox thật.
- Evidence `docs/evidence/<ten>/<TASK-ID>.md`: commit, môi trường, fixture, test case, thao tác/lệnh, expected/actual, exit code, DB trước/sau, log đã lọc, phụ thuộc. Chỉ tick khi có bằng chứng.
- PR ghi task ID, file/API/SQL/UI thay đổi, phụ thuộc/caller, cách chạy, evidence và risk thật. Reviewer ưu tiên người nhận đầu ra: Khánh↔Đông quyền; Đông↔Liêm kho; Liêm↔Thái vé/hoàn; Thái↔Vương tài chính; Vương↔Khánh bản build/quyền.
- AI phải đọc nguồn và dependency task, kiểm tra thực trạng repo, phân biệt file có sẵn với file phải tạo; dùng đúng chữ ký hợp đồng; không tự thêm class nghiệp vụ/route/setter kết quả tiền; không tick từ mô tả. Khi blocker: ghi thiếu gì, chủ cung cấp, task độc lập tiếp tục; không trả200 giả hoặc sửa requirement để test xanh.
- Mỗi người cung cấp phần thuyết minh/ERD/API/SQL/evidence của mình; Vương ghép hồ sơ môn học. Nhóm5 người giữ theo phân công thực tế; quy mô3–4 trong spec14.12 cần xác nhận với giảng viên khi nộp, không tự loại thành viên.

## 9. Mẫu nhiệm vụ thống nhất

Mỗi file thành viên có mục tiêu/phạm vi, nguồn, file sở hữu, phụ thuộc, danh mục SQL/API/UI, task ID, ví dụ và checklist nghiệm thu. Mỗi task ghi: mục tiêu đo được; source spec/diagram; mốc/phụ thuộc task ID; Create/Modify/Test bằng đường dẫn cụ thể; Consumes/Produces với chữ ký và DTO; route/body/status/quyền; bước checkable; expected success/negative/boundary/race/retry/rollback phù hợp; lệnh/check chứng minh; evidence và người nhận bàn giao. Không dùng câu chung “xử lý đầy đủ lỗi” làm yêu cầu duy nhất.

Các DTO fields hoặc chữ ký SP chưa có trong spec phải được task sở hữu định nghĩa rõ trước caller. Không viết full code thay người triển khai; code block dùng cho hợp đồng, input/output và assertion quan trọng. Mỗi file có lệnh bắt đầu cho AI chỉ rõ phải triển khai phần được giao và báo bằng chứng, không thực thi cả năm kế hoạch tự động.
