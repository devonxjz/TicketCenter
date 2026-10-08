# Kế hoạch triển khai fullstack — Liêm: mua vé, thanh toán và worker chung

## Prompt khởi đầu — copy từ đây

Copy **nguyên nội dung trong khối bên dưới** vào AI có quyền đọc repository TicketsCenter. Prompt đã gắn đúng thành viên/file; không cần copy toàn bộ 76 task vào cửa sổ chat. Nếu AI không có quyền đọc repository, phải cung cấp các tài liệu được liệt kê trước khi triển khai.

```text
Tôi phụ trách phần của Liêm trong dự án TicketsCenter.
Hãy triển khai nhiệm vụ của tôi theo docs/tasks/liem.md.

Trước khi viết mã:
1. Đọc toàn bộ spec.md và docs/classdiagram/diagram.md, gồm thuộc tính,
   phương thức và quan hệ trong XML diagram.
2. Đọc toàn bộ docs/tasks/TEAM-CONTRACT.md từ đầu đến cuối, không bỏ mục
   hoặc chỉ đọc phần cá nhân; đọc đầy đủ docs/tasks/CONVENTIONS.md,
   API-MAP.md, COVERAGE.md và GIT-WORKFLOW.md.
3. Đọc toàn bộ docs/tasks/liem.md. Đọc các phần nhiệm vụ thành viên khác
   cung cấp đầu vào hoặc nhận đầu ra liên quan; kiểm mã nguồn, cấu hình,
   trạng thái Git và bằng chứng thực tế trong repository.
4. Trước thay đổi đầu tiên, báo ngắn gọn phiên bản nguồn đã đọc, phạm vi
   sở hữu, hợp đồng liên miền, nhiệm vụ sẽ làm và tình trạng phụ thuộc.
   Tiếp tục công việc đã được giao nếu đủ đầu vào, không dừng để hỏi lại
   xác nhận cho các bước triển khai thông thường.

Bắt đầu bằng nhiệm vụ đầu tiên chưa hoàn thành và đủ phụ thuộc.
Nếu tôi chỉ định mã nhiệm vụ cụ thể trong tin nhắn tiếp theo, ưu tiên mã đó.
Chỉ sửa file thuộc phạm vi Liêm; phần do người khác sở hữu phải phối hợp
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

Muốn giao một task cụ thể, thêm sau prompt: `Nhiệm vụ lần này: LIEM-NN.` Thay NN bằng mã thật trong file; không suy ra task hoàn thành từ lịch ngày, checkbox hoặc tên commit.

## Điều kiện bắt buộc trước khi triển khai

**Bắt buộc với cả thành viên và AI:** trước nhiệm vụ đầu tiên, phải đọc **toàn bộ [TEAM-CONTRACT.md](TEAM-CONTRACT.md), từ đầu đến cuối, không bỏ mục, không chỉ đọc phần của mình hoặc bản tóm tắt**. Đồng thời đọc đầy đủ [CONVENTIONS.md](CONVENTIONS.md), [API-MAP.md](API-MAP.md), [COVERAGE.md](COVERAGE.md) và [GIT-WORKFLOW.md](GIT-WORKFLOW.md), cùng spec/diagram được dẫn trong phần nguồn chuẩn.

- [ ] Đã đọc toàn bộ hợp đồng chung và tài liệu quy ước; hiểu ownership Model/Servlet/SQL, DTO/Service/API, quyền/principal, transaction/locking, outbox/SPI, migrations/fixture, Git và tiêu chí nghiệm thu.
- [ ] Ghi trong kế hoạch bắt đầu hoặc Draft PR: đường dẫn hợp đồng, commit SHA nguồn nếu có, task nhận, đầu vào/đầu ra liên miền và các ràng buộc áp dụng. Nếu hợp đồng còn chưa commit, ghi SHA nền và trạng thái working tree; không bịa phiên bản.
- [ ] Đối chiếu task với hợp đồng hiện hành và kiểm file/Service thuộc người nào. Nếu có mâu thuẫn hoặc thiếu hợp đồng bắt buộc, ghi rõ điểm thiếu và chủ cung cấp; chưa triển khai phần phụ thuộc đó.
- [ ] Khi tiếp tục phiên làm việc, kiểm hợp đồng có thay đổi từ phiên bản đã đọc không. Có thay đổi thì đọc lại toàn bộ hợp đồng mới và cập nhật caller/test bị ảnh hưởng trước khi tiếp tục phần liên quan.

Không bắt đầu viết mã cho nhiệm vụ khi chưa hoàn thành việc đọc và đối chiếu trên. Reviewer phải kiểm xác nhận này trước khi chấp nhận PR; một câu “đã đọc” không thay việc chứng minh đầu ra tuân hợp đồng.

> **Hướng dẫn AI thực hiện:** dùng kỹ năng `superpowers:executing-plans`, làm từng nhiệm vụ `LIEM-01`…`LIEM-16` trong nhánh được giao. Đọc nguồn và task phụ thuộc trước; kiểm tra file thực tế trước khi Create/Modify. Đây là kế hoạch tương lai: chưa có mã, test hoặc bằng chứng đạt. Không tự triển khai cả năm file, tự push/merge/deploy hoặc đánh dấu checkbox từ mô tả.

**Mục tiêu (Goal):** hoàn thành phần mua vé từ giữ chỗ đến QR, quản lý coupon và nền worker bền vững trong **một** project chung, gồm backend, SQL Server, JSP/JavaScript và kiểm thử cho UI-04/05/06/07/14.

**Kiến trúc (Architecture):** Filter → Servlet → Service → Model/Repository JPA → SQL Server; SP sở hữu mutation của use case, Model vẫn có đủ hành vi diagram. Transaction kết thúc trước VNPAY/email; outbox ghi cùng transaction nghiệp vụ và một dispatcher/scheduler chung chạy handler của các thành viên.

**Công nghệ (Tech Stack):** JDK 25, Maven WAR, Tomcat 11.0.25/Servlet 6.1, JSP 4.0/JSTL 3.0, Bootstrap/CSS chung, JPA/Hibernate, SQL Server local/Azure SQL, VNPAY Sandbox, QR qua thư viện được Khánh khóa. Không Spring/SPA/DAO JDBC song song.

**Nguồn (Spec):** [spec.md](../../spec.md) §2–3, §5–8, §9 UI-04/05/06/07/14, §10–12, §14; [diagram XML](../classdiagram/diagram.md) phải đọc thuộc tính, phương thức và quan hệ; [TEAM-CONTRACT](TEAM-CONTRACT.md) khóa ownership/interfaces/migration/mốc; [CONVENTIONS](CONVENTIONS.md), [API-MAP](API-MAP.md), [COVERAGE](COVERAGE.md). Tham khảo [ngày 08](week-2/day-08-giu-ve-va-giai-phong.md), [09](week-2/day-09-don-hang-coupon.md), [10](week-2/day-10-vnpay-khoi-tao-xac-thuc.md), [11](week-2/day-11-ghi-nhan-thanh-toan-phat-hanh-ve.md), [12](week-2/day-12-outbox-worker-doi-soat-thanh-toan-email.md). Các file ngày cũ chỉ có backend; hợp đồng thành viên bổ sung đầy đủ UI, không dùng lịch ngày cũ làm tiến độ.

## Ràng buộc toàn cục (Global Constraints)

- Chủ triển khai Liêm; reviewer nhận đầu ra: Đông cho kho, Khánh cho session/MeServlet/email, Thái cho Ticket/Refund, Vương cho grants/catalog/benchmark và AdminServlet. File người khác chỉ sửa qua phối hợp và review của chủ.
- Đúng 15 Model: User, Organization, Event, Zone, Seat, TicketHold, TicketHoldItem, Order, OrderItem, Payment, Coupon, Ticket, CommissionRule, Refund, Settlement. Liêm sở hữu bảy lớp TicketHold/Item, Order/Item, Payment, Coupon, Ticket. Không thêm RefundRequest, CheckIn, Payout hoặc SettlementItem. Refund trực tiếp giữ nghĩa vụ/currentAttemptId; Settlement trực tiếp giữ snapshot tổng/paidAmount/pendingAmount; log/redemption/outbox là persistence.
- Java UUID/BigDecimal/Instant; Money và Decimal của diagram ánh xạ BigDecimal, không `double`. SQL uniqueidentifier/decimal(19,0)/UTC datetime2. Tiền HTTP là chuỗi đồng nguyên; UTC ISO-8601; UI/email tiếng Việt, VND, Asia/Ho_Chi_Minh. Không nhận `now`, `expiresAt`, giá, tiền, userId, role hoặc principal từ browser để quyết định nghiệp vụ.
- Một Hold ACTIVE/user, 1–8 vé cùng Event, TTL đúng 10 phút; `now >= expiresAt` là hết hạn. Cửa bán `[saleStart,saleEnd)`; Hold chưa hết nhưng Event qua saleEnd cũng không được chấp nhận thanh toán.
- Thao tác user POST+CSRF. Session ActorContext do Khánh tạo và kiểm tra trạng thái/authVersion hiện tại; manager/admin không tự có quyền mua/sửa đơn của người khác. IPN chỉ miễn session/CSRF khi xác thực chữ ký đúng giao thức.
- TransactionRunner và EntityManager của Khánh dùng một connection/use case. SP độc lập tự quản transaction, có transaction ngoài thì tham gia/savepoint, không commit caller; NOCOUNT/XACT_ABORT/TRY-CATCH/THROW theo hợp đồng. Không dirty-write rồi gọi SP lặp mutation. Clear/refresh context sau SP.
- Không gọi bên ngoài khi đang giữ khóa. UNKNOWN phải đối chiếu, không suy ra FAILED để tạo lần thu/hoàn khác. Redelivery worker không có nghĩa được tự bật `retryFailedCompensation`.
- Migrations theo TEAM §4.1; Liêm viết `database/migrations/0030_sales.sql` và object miền; Vương ghép manifest/cross-FK/grants. Không sửa migration đã áp dụng chung hoặc FK tới bảng chưa tồn tại. `V09 → F08` cần thứ tự đặc biệt được đăng ký trước lần áp dụng đầu tiên.
- JSP chỉ DTO/JSTL, không scriptlet/SQL/EntityManager. Dùng layout/assets chung, giữ page frame prototype và placeholder ảnh/sơ đồ còn thiếu; không tự vẽ/sinh ảnh. loading/empty/success/error, label/focus/keyboard, responsive phải có.
- Đường dẫn mã/test/SQL bên dưới là **đầu ra cần tạo**, không tuyên bố có sẵn. Chỉ tick khi bằng chứng gắn commit. Thiếu SQL Server/sandbox ghi BLOCKED phần tương ứng và tiếp tục task độc lập, không đổi sang mock rồi báo tích hợp đạt.

## Trọng tâm review (Review Focus)

| Điều kiện dễ bỏ sót | Hành vi và task kiểm chứng |
|---|---|
| Hold cũ hết hạn ở Event A, buyer giữ Event B trong hai tab | Dọn Order/coupon/kho cũ và giữ mới nguyên tử, khóa cross-Event ổn định, một ACTIVE; LIEM-03/04/15 |
| Coupon sửa/tắt/hết hạn sau khi RESERVED hợp lệ | Không hồi tố discount; lượt mới theo terms mới, PENDING/UNKNOWN khóa đổi mã; LIEM-06/07 |
| CAPTURED đến sau expiresAt/saleEnd/cancel, hoặc khoản thu thứ hai | Giữ lịch sử khoản thu, một Refund compensation/Payment, không Ticket/acceptedPayment mới; LIEM-10/15 |
| Mất response sau commit hoặc crash sau effect trước ack | Truy vấn trạng thái/idempotency/lease trước retry, không nhân vé/kho/nghĩa vụ; LIEM-11/12/15 |
| Người nhiều vai trò hoặc session bị thu hồi đang mở QR/coupon | Kiểm owner hoặc membership hiện tại từng request; QR không lộ vé khác/financial data cho check-in staff; LIEM-13/14/15 |

## Hợp đồng cung cấp từ M0

LIEM-01 phải cung cấp Model, DTO, Service interface và worker SPI **ngay đầu M0**, không chờ Auth/Event hoàn tất. Unit test dùng model fixtures/test doubles có nhãn rõ; chỉ compile stub không được nhận là nghiệp vụ đã xong. Model User/Event/Zone/Seat do Khánh/Đông cung cấp M0; Refund.createCompensation và schema do Thái cung cấp M0 để phá vòng phụ thuộc SP09.

### 1. Đủ thuộc tính/phương thức diagram

ID UUID và createdAt kỹ thuật thêm theo spec; association buyer/event/hold/orderItem/organization và composition Hold→HoldItem, Order→OrderItem, OrderItem→Ticket đúng XML; Payment Order bắt buộc, không thay bằng liên kết tùy ý. Item snapshot bất biến sau tạo, không cascade xóa lịch sử tài chính.

| File dưới `src/main/java/vn/ticketscenter/` | Thuộc tính và phương thức bắt buộc |
|---|---|
| `src/main/java/vn/ticketscenter/model/ticketing/TicketHold.java` | buyer, event, expiresAt, status; `boolean isExpired(Instant now)`, `boolean isActive(Instant now)`, `void release()`, `void consume(Instant now)` |
| `src/main/java/vn/ticketscenter/model/ticketing/TicketHoldItem.java` | zone, seat nullable, quantity, unitPrice; `BigDecimal lineTotal()` |
| `src/main/java/vn/ticketscenter/model/order/Order.java` | buyer/event/hold/coupon nullable/acceptedPayment nullable/orderCode/status/discountAmount; derived subtotalAmount/totalAmount, paidAt; `static Order fromHold(TicketHold hold, Instant now)`, `void applyCoupon(Coupon coupon, Instant now)`, `void removeCoupon()`, `void markPaid(Payment paymentOrNull, Instant now)`, `void cancel()`, `void expire(Instant now)` |
| `src/main/java/vn/ticketscenter/model/order/OrderItem.java` | zone, seat nullable, zoneNameSnapshot, seatLabelSnapshot nullable, quantity, unitPrice; `BigDecimal lineTotal()` |
| `src/main/java/vn/ticketscenter/model/order/Payment.java` | order bắt buộc, amount snapshot, status, txnRef, transactionNo nullable, paidAt; `static Payment start(Order order, String txnRef)`, `void markCaptured(String transactionNo, Instant paidAt)`, `void markFailed()`, `void markUnknown()` |
| `src/main/java/vn/ticketscenter/model/order/Coupon.java` | organization, code, discountType, fixedAmount nullable, percentage nullable, validFrom/validTo, isActive, maxUses; `boolean isActiveAt(Instant now)`, `BigDecimal calculateDiscount(BigDecimal subtotal)`, `void activate()`, `void deactivate()`, `void reviseTerms(CouponTerms terms, int allocatedUses)` |
| `src/main/java/vn/ticketscenter/model/fulfillment/Ticket.java` | orderItem, ticketCode, issuedAt, status, paidAmount; derived zone/seat; `void checkIn(Event event, Instant now)`, `void markRefundPending(Instant now)`, `void restoreAfterRejection()`, `void markRefunded()` |

CouponTerms = discountType/fixedAmount?/percentage?/validFrom/validTo/maxUses. Model không tự DB/HTTP/QR. Ticket.checkIn kiểm đúng Event/PUBLISHED/cửa `[startTime−60 phút,endTime)`/ACTIVE; markRefundPending chỉ trước start/chưa dùng; restoreAfterRejection không hồi sinh USED/REFUNDED và caller Thái chặn Event CANCELLED; markRefunded chỉ từ REFUND_PENDING. Thái sở hữu transaction SP04/05/10/11 và log, không viết lại Ticket. Order.markPaid chỉ CAPTURED của chính Order/số tiền khớp; null chỉ khi total=0; paidAt luôn có. CAPTURED không hồi quy FAILED/UNKNOWN, lịch sử Payment.amount không sửa.

### 2. Kiểu DTO/command và query

Mỗi kiểu ở một file `.java` cùng tên dưới `dto/ticketing/`, `dto/order/`, `dto/fulfillment/`, `dto/job/` tương ứng; enum là kiểu kỹ thuật. Ký hiệu `?` là nullable, UUID là UUID, money là BigDecimal nội bộ/chuỗi HTTP, time là Instant, list là List<T>. Không trả entity graph. Page/PageRequest/ActorContext dùng bản của Khánh trong `dto/common/`, không tạo bản song song.

| Kiểu | Toàn bộ trường |
|---|---|
| HoldCommand / HoldSelection | HoldCommand(eventId UUID, selections List<HoldSelection>); HoldSelection(zoneId UUID, seatId UUID?, quantity int) |
| HoldItemDto | id UUID, zoneId UUID, zoneName String, zoneType ZoneType, seatId UUID?, seatLabel String?, quantity int, unitPrice money, lineTotal money |
| HoldDto | id UUID, eventId UUID, eventTitle String, status HoldStatus, createdAt time, expiresAt time, saleEnd time, serverNow time, items List<HoldItemDto>, subtotalAmount money, orderId UUID? |
| OrderItemDto | id UUID, zoneId UUID, seatId UUID?, zoneNameSnapshot String, seatLabelSnapshot String?, quantity int, unitPrice money, lineTotal money |
| OrderDto | id UUID, orderCode String, eventId UUID, eventTitle String, organizationId UUID, status OrderStatus, createdAt time, paidAt time?, holdId UUID, holdExpiresAt time, saleEnd time, serverNow time, subtotalAmount money, discountAmount money, totalAmount money, couponId UUID?, couponCode String?, acceptedPaymentId UUID?, items List<OrderItemDto>, tickets List<TicketDto> |
| OrderFilter | status OrderStatus?, eventId UUID?, from time?, to time?; `[from,to)`, sort allowlist createdAt/paidAt, ID tie-break; không userId |
| CouponCommand | code String, discountType DiscountType, fixedAmount money?, percentage BigDecimal?, validFrom time, validTo time, maxUses int, isActive boolean; organizationId lấy route/DB, không đổi organization khi edit |
| CouponFilter | keyword String?, isActive Boolean?; sort code/validTo với id phụ |
| CouponDto | id UUID, organizationId UUID, code String, discountType, fixedAmount money?, percentage BigDecimal?, validFrom time, validTo time, isActive boolean, maxUses int, reservedUses int, consumedUses int, releasedUses int, remainingUses int |
| CouponEligibilityDto | couponId UUID?, couponCode String?, eligible boolean, reason String, remainingUses int, previewDiscount money?, serverNow time; lý do NOT_FOUND/WRONG_ORGANIZATION/INACTIVE/NOT_STARTED/EXPIRED/QUOTA_EXHAUSTED/ELIGIBLE |
| PaymentStartDto | orderId UUID, paymentId UUID?, txnRef String?, amount money, state enum REDIRECT/PENDING/UNKNOWN/COMPLETED_ZERO, paymentUrl String?, expiresAt time; UNKNOWN không URL thu mới |
| PaymentStatusDto | orderId UUID, orderStatus OrderStatus, paymentId UUID?, paymentStatus PaymentStatus?, amount money, compensationRefundId UUID?, compensationStatus RefundStatus?, ticketCount int, serverNow time; trạng thái nghiệp vụ xác nhận từ DB |
| TicketDto | id UUID, orderId UUID, orderItemId UUID, eventId UUID, organizationId UUID, eventTitle String, venueName String, venueAddress String, startTime time, endTime time, zoneNameSnapshot String, seatLabelSnapshot String?, status TicketStatus, issuedAt time, paidAmount money; **không** ticketCode/QR/token |
| PaymentIntent | orderId UUID, paymentId UUID, txnRef String, amount money, createdAt time, expiresAt time; chỉ backend |
| VerifiedPaymentResult | txnRef String, amount money, currency String, state enum CAPTURED/FAILED/UNKNOWN, transactionNo String?, paidAt time?, verifiedAt time; chỉ verifier integration tạo, CAPTURED bắt buộc transactionNo/paidAt; không deserialize browser |
| PaymentApplyDto | orderId UUID, paymentId UUID?, orderStatus OrderStatus, ticketIds List<UUID>, compensationRefundId UUID?, compensationStatus RefundStatus?; chỉ Service/handler/IPN mapping dùng |
| OutboxMessageDto | id UUID, type String, payloadVersion int, idempotencyKey String, payloadJson String, createdAt time, attempts int, nextAttemptAt time, leaseOwner String?, leaseUntil time?, leaseToken UUID? |
| JobResult | status enum SUCCEEDED/RETRY/FAILED, nextAttemptAt time?; RETRY bắt buộc nextAttemptAt; FAILED giữ lỗi đã lọc ở log theo correlationId/outboxId |

HoldService.getCurrent trả null khi không có Hold hiện hành, HTTP `{data:null}`; không tạo Hold giả. OrderDto.tickets có thể rỗng trước phát hành. Các DTO không chứa raw secret, nguyên QR, email/password/OTP. Khoảng lọc thiếu một biên được phép; hai biên phải from<to. Money đầu vào có phần lẻ/âm/vượt decimal(19,0) bị 400, không lặng lẽ làm tròn.

### 3. Service/adapter/SPI chính xác

Chữ ký trong TEAM có ưu tiên; bảng này giữ nguyên tên/type/argument của các chữ ký bàn giao và định nghĩa thêm use case. Service nằm `src/main/java/vn/ticketscenter/service/<nhóm>/<Tên>.java`; repository nhận EntityManager của caller, không tự lấy session hay mở transaction lồng độc lập.

```java
// service/ticketing/HoldService.java
HoldDto getCurrent(ActorContext actor);
HoldDto create(ActorContext actor, HoldCommand command);
HoldDto cancel(ActorContext actor, UUID holdId);
// service/order/OrderService.java và OrderQueryService.java
OrderDto create(ActorContext actor, UUID holdId);
OrderDto applyCoupon(ActorContext actor, UUID orderId, String couponCodeOrNull);
Page<OrderDto> listOwn(ActorContext actor, OrderFilter filter, PageRequest page);
OrderDto get(ActorContext actor, UUID orderId);
// service/order/CouponService.java
Page<CouponDto> list(ActorContext actor, UUID organizationId, CouponFilter filter, PageRequest page);
CouponDto create(ActorContext actor, UUID organizationId, CouponCommand command);
CouponDto edit(ActorContext actor, UUID couponId, CouponCommand command);
CouponDto setActive(ActorContext actor, UUID couponId, boolean active);
void deleteUnused(ActorContext actor, UUID couponId);
CouponEligibilityDto eligibility(ActorContext actor, UUID orderId, String couponCode);
// service/order/PaymentService.java
PaymentStartDto begin(ActorContext actor, UUID orderId);
PaymentStatusDto status(ActorContext actor, UUID orderId);
PaymentApplyDto applyVerified(VerifiedPaymentResult result); // system integration, không HTTP user body
// service/fulfillment/TicketQueryService.java và TicketService.java
Page<TicketDto> listOwn(ActorContext actor, PageRequest page);
TicketDto get(ActorContext actor, UUID ticketId);
byte[] qrPng(ActorContext actor, UUID ticketId);
// integration/payment/PaymentGateway.java
String createPaymentUrl(PaymentIntent intent);
VerifiedPaymentResult verifyCallback(Map<String, List<String>> parameters);
VerifiedPaymentResult query(PaymentIntent intent);
// job/JobHandler.java, job/OutboxPublisher.java
JobResult handle(OutboxMessageDto message);
UUID enqueue(EntityManager em, String type, int payloadVersion, String idempotencyKey, String payloadJson);
```

Danh sách prototype Java trên là method declarations trong class/interface tương ứng, không phải một file tổng hợp. applyVerified tra Payment bằng txnRef, kiểm identity/amount lại ở SP09; system actor/principal do hạ tầng tạo. PaymentService.begin luồng 0đ gọi SP09 trực tiếp với actor owner, không PaymentIntent/Gateway giả. Sau COMPLETED_ZERO, UI04 tới `/payments/vnpay/return?orderId=<id>` để render UI05 từ PaymentService.status kiểm owner; đây là lookup chỉ đọc cho đơn0đ, không giả callback VNPAY. PaymentIntent.expiresAt = min(Hold.expiresAt, Event.saleEnd), không tạo URL kéo dài cửa nhận thanh toán. QrEncoder ở `src/main/java/vn/ticketscenter/integration/qr/QrEncoder.java`: `byte[] encodePng(String ticketCode)`; nguồn code ngẫu nhiên an toàn tại Service, unique SQL, không mã hóa ID tuần tự.

Worker hook: `src/main/java/vn/ticketscenter/job/JobHandlerRegistry.java` cung cấp `void register(String type, int payloadVersion, JobHandler handler)` và `JobHandler resolve(String type, int payloadVersion)`; duplicate registration lỗi startup, unknown type/version thành FAILED cần vận hành, không mất payload. Liêm tạo OutboxWorker/WorkerListener/HoldExpiryJob/PaymentReconciliationJob; Khánh đăng ký EmailJob, Đông ImageCleanupJob, Thái RefundJob/EventCancellationJob vào registry hiện hữu. Họ không tạo scheduler thứ hai.

Repository worker tại `src/main/java/vn/ticketscenter/repository/job/OutboxRepository.java` cung cấp `List<OutboxMessageDto> claim(EntityManager em, String leaseOwner, Instant now, int batchSize, int leaseSeconds)` và `boolean acknowledge(EntityManager em, UUID messageId, String leaseOwner, UUID leaseToken, JobResult result, Instant now)`. Claim/ack dùng transaction riêng ngắn qua TransactionRunner, batch1–100 và leaseSeconds dương từ cấu hình server; token mới từng bản ghi. Enqueue cùng dedup key nhưng payload khác bị conflict, không lặng lẽ gửi payload của lần cũ.

Outbox types `EMAIL`, `HOLD_EXPIRE`, `PAYMENT_RECONCILE`, `PAYMENT_COMPENSATION`, `REFUND_TRANSFER`, `EVENT_CANCELLATION`, `IMAGE_CLEANUP`; mỗi handler chủ đăng ký version1 và schema cùng M0. Payload Liêm: HOLD_EXPIRE `{holdId}`, PAYMENT_RECONCILE `{paymentId}`, PAYMENT_COMPENSATION `{refundId,paymentId}`; không `retryFailed=true` trong redelivery payload. Thái chốt REFUND_TRANSFER/EVENT_CANCELLATION, Đông chốt IMAGE_CLEANUP. MailPayloadV1 Khánh sở hữu: schemaVersion=1, template TICKETS_ISSUED|REFUND_REJECTED|REFUND_COMPLETED, recipientUserId UUID, orderId UUID?, refundId UUID?, occurredAt Instant; type EMAIL, keys `ticket-issued:{orderId}`, `refund-rejected:{refundId}`, `refund-completed:{refundId}`. Enqueue bằng EntityManager hiện tại sau nghiệp vụ, trước commit; email vé chỉ ID/context tối thiểu, không QR/email address/OTP/password. OTP dùng đường gửi ngắn của Khánh. Liêm không viết EmailJob/RefundJob/ImageCleanupJob.

### 4. Route, response và quyền

Route tính dưới context path; Khánh parse path/representation/JSON/error/CSRF helpers. GET có page mặc định JSP, `Accept: application/json` trả cùng DTO/quyền; mutation JSON envelope TEAM. ID sai định dạng400, chưa login401, không quyền403 hoặc owner ngoài phạm vi404, conflict409; dependency503. UI không tự nhận giá/tiền/trạng thái từ querystring là thật.

| Chủ Servlet / route | Input → output thành công | Quyền/màn hình |
|---|---|---|
| HoldServlet POST `/holds` | HoldCommand → 201 HoldDto | ACTIVE verified buyer; gọi từ UI-02 Đông, tiếp UI-04 |
| HoldServlet POST `/holds/{id}/cancel` | `{}` → 200 HoldDto RELEASED, lặp kết quả cũ | Chủ Hold + CSRF, UI-04 |
| MeServlet Khánh GET `/me/hold` | không body → 200 HoldDto/null | HoldService.getCurrent, owner; UI-04 |
| OrderServlet POST `/orders` | `{holdId}` → 201 OrderDto mới, 200 khi lặp cùng Hold | Owner; UI-04 |
| OrderServlet GET `/orders/{id}` | id → OrderDto/JSP checkout khi PENDING_PAYMENT, detail khi đã chốt | Owner; UI-04/06 |
| OrderServlet POST `/orders/{id}/coupon` | `{couponCode:"..."}` hoặc `{couponCode:null}` → 200 OrderDto | Owner+CSRF, UI-04 |
| OrderServlet GET `/orders/{id}/coupon-eligibility?couponCode=...` | code → 200 CouponEligibilityDto | Owner, đọc F08 không giữ lượt |
| OrderServlet POST `/orders/{id}/payments` | `{}` → 200 PaymentStartDto | Owner+CSRF, UI-04; REDIRECT chỉ URL allowlist, UNKNOWN chỉ theo dõi |
| OrderServlet GET `/orders/{id}/payment-status` | id → 200 PaymentStatusDto JSON | Owner; UI-05 |
| VnpayServlet GET `/payments/vnpay/return` | query gateway hoặc orderId của đơn0đ → JSP payment/status.jsp | Chỉ đọc, session owner trước dữ liệu; chưa login về UI-03; không cập nhật tiền |
| VnpayServlet GET `/payments/vnpay/ipn` | raw multi-value query → response `{RspCode,Message}` theo tài liệu chính thức lúc triển khai | Không session/CSRF; chữ ký/merchant/txnRef/amount/currency/status; không envelope DTO |
| MeServlet Khánh GET `/me/orders`, `/me/tickets` | OrderFilter+PageRequest / PageRequest → Page<OrderDto>/Page<TicketDto> hoặc JSP | listOwn owner, UI-06; Liêm viết JSP, Khánh dispatch |
| TicketServlet GET `/tickets/{id}` | id → TicketDto hoặc JSP fulfillment/ticket.jsp | Owner, UI-07 |
| TicketServlet GET `/tickets/{id}/qr` | id → 200 image/png, Cache-Control private/no-store | Owner; không mở link cả Order; người mua tải/chia sẻ ảnh |
| OrganizationServlet Đông GET/POST `/organizations/{id}/coupons` | filter/page hoặc CouponCommand → Page<CouponDto>/201 CouponDto | MANAGER đúng org hoặc ADMIN; UI-14 |
| AdminServlet Vương GET/POST `/admin/organizations/{id}/coupons` | cùng DTO/service như route org | ADMIN, bộ chọn tổ chức Vương; cùng JSP UI-14 |
| CouponServlet POST `/coupons/{id}/edit`, `/activate`, `/deactivate`, `/delete` | edit CouponCommand; route khác `{}` → 200 CouponDto; delete 204 | MANAGER đúng org hoặc ADMIN, CSRF; delete chỉ chưa được tham chiếu |

Không đăng ký Servlet riêng cho `/me/*`, `/organizations/*`, `/admin/*` hay PaymentServlet `/orders/*`. Liêm bàn giao DTO/service/JSP path cho caller sở hữu mapping. R03 check-in staff không được V06 vì paidAmount; họ dùng DTO tối thiểu Thái, không mở QR owner endpoint bằng role staff.

### 5. SQL cần cung cấp

Schema `database/migrations/0030_sales.sql`: TicketHold/TicketHoldItem/[Order]/OrderItem/Payment/Coupon/Ticket/CouponRedemption/OutboxMessage; enum miền giá trị/NOT NULL/PK/FK/xóa lịch sử theo spec. Unique ngoài rubric: ACTIVE Hold(userId) filtered status; Order(holdId), orderCode/ticketCode/txnRef; một redemption/order (mỗi Order một dòng hiện hành, đổi mã cập nhật dòng trong transaction; audit append-only giữ transition cũ/mới qua hạ tầng Vương, V09 đếm trạng thái hiện hành); outbox(type,payloadVersion,idempotencyKey). Coupon code chuẩn hóa trim/uppercase, duy nhất `(organizationId,normalizedCode)`; maxUses count RESERVED+CONSUMED. Lease token/owner kiểm tra khi ack, không dùng đồng hồ trong filtered index.

| Object chính thức / file migration | Caller/task |
|---|---|
| C06 CK_Coupon_Discount_Limit; C07 CK_HoldItem_Seat_Quantity + CK_OrderItem_Seat_Quantity; C08 CK_Order_Amounts; C09 UQ_Payment_TxnRef + FK_Payment_Order; C11 CK_TicketHold_TimeRange; C12 CK_HoldItem_UnitPrice + CK_OrderItem_UnitPrice; C13 CK_Ticket_PaidAmount; C14 CK_Payment_PositiveAmount / `database/migrations/0030_sales.sql` | LIEM-02; acceptedPayment CAPTURED/same Order enforced SP09+mapping, cross FK manifest |
| V09 dbo.vw_CouponUsage / `database/migrations/0190_V09.sql`; F01 dbo.fn_CalculateCouponDiscount / `database/migrations/0200_F01.sql`; F08 dbo.fn_GetCouponEligibility / `database/migrations/0200_F08.sql`; TR07 dbo.trg_Coupon_ProtectUsageLimit / `database/migrations/0500_TR07.sql` | LIEM-06/07; V09 trước F08 là ngoại lệ thứ tự đăng ký Vương |
| SP07 dbo.usp_ReleaseTicketHold / `database/migrations/0390_SP07.sql` | LIEM-03; SP07 trước SP02 để dependency caller rõ |
| SP02 dbo.usp_CreateTicketHold / `database/migrations/0400_SP02.sql` | LIEM-04, TX02 |
| SP06 dbo.usp_CreateOrderFromHold / `database/migrations/0400_SP06.sql` | LIEM-05, TX06 |
| SP03 dbo.usp_ApplyOrderCoupon / `database/migrations/0400_SP03.sql` | LIEM-07, TX03 |
| SP08 dbo.usp_BeginOrderPayment / `database/migrations/0400_SP08.sql` | LIEM-08, TX08 |
| F06 dbo.fn_AllocateTicketPaidAmounts / `database/migrations/0200_F06.sql`; SP09 dbo.usp_ApplyPaymentResult / `database/migrations/0400_SP09.sql` | LIEM-10, TX09 |
| V06 dbo.vw_TicketDetails / `database/migrations/0300_V06.sql`; TR08 dbo.trg_Ticket_ProtectIssuedSnapshot / `database/migrations/0500_TR08.sql` | LIEM-13 |
| IX03 IX_Order_User_CreatedAt / `database/migrations/0150_IX03.sql`; IX04 IX_Payment_Status_CreatedAt / `database/migrations/0150_IX04.sql`; IX06 IX_TicketHold_Status_ExpiresAt / `database/migrations/0150_IX06.sql`; IX09 IX_CouponRedemption_Coupon_Status / `database/migrations/0150_IX09.sql`; IX11 IX_Ticket_OrderItem_Status / `database/migrations/0150_IX11.sql` | LIEM-02/12/13/16, key/include giữ đúng spec §14.8, Vương benchmark |

Chữ ký SP giữ **nguyên** spec §14.5: SP02(userId,eventId,selections); SP03(orderId,actorId,couponCodeOrNull); SP06(holdId,actorId); SP07(holdId,actorIdOrNull,reason); SP08(orderId,actorId); SP09(orderId,actorIdOrNull,paymentIdOrNull,verifiedResult,ticketCodes,compensationAttemptIdOrNull,retryFailedCompensation=false). LIEM-02 ghi kiểu SQL tham số/result set vào contract miền và catalog: selections JSON NVARCHAR(MAX) (zoneId/seatId/quantity), ticketCodes JSON array mã do backend tạo, verifiedResult JSON serialized VerifiedPaymentResult do integration kiểm; scalar UUID uniqueidentifier, reason/code nvarchar có giới hạn, retryFailedCompensation bit mặc định0. OPENJSON phải validation schema/trùng/null/giới hạn8, không âm thầm loại dòng lỗi. SP trả một result set canonical để repository tạo DTO; lỗi THROW được helper Khánh map stable code. Time dùng server UTC/test clock nội bộ, không cho HTTP truyền now.

F01(subtotal,discountType,percentage,fixedAmount) scalar decimal(19,0), invalid bắt buộc trả NULL và caller báo lỗi; floor đồng giảm/cap floor(30% subtotal). F06(orderId) TVF một dòng/quyền vào cửa với orderItemId,ticketOrdinal,unitPrice,discountAmount,paidAmount; phần dư lớn nhất tie `(orderItemId,ticketOrdinal)`: UUID canonical lowercase 36 ký tự so sánh lexical; Java String.compareTo và SQL CONVERT(char(36),orderItemId) COLLATE Latin1_General_100_BIN2, ordinal tăng. Không dùng thứ tự mặc định uniqueidentifier khác comparator Java. F08(couponId,organizationId,subtotal,nowUtc) TVF eligible/reason/remainingUses/previewDiscount, SQL clock do backend cấp. V06 không code/QR; V09 không tự trừ RESERVED theo đồng hồ trước SP07.

## Lệnh kiểm chứng và bằng chứng dùng cho mọi task

Khánh phải tạo unit/Surefire và Failsafe profile `sqlserver-it`, Vương phải tạo profile `browser-it`; đây là lệnh **mục tiêu tương lai**, repo hiện tại chưa có bằng chứng chạy. `mvn -B verify` build/unit không thay test SQL. Mỗi task dưới đây ghi ClassIT, script và expectation riêng; test thiếu DB phải fail, không skip xanh.

```text
mvn -B verify
mvn -B -Psqlserver-it -Dit.test=<ClassIT> verify
sqlcmd -S "$env:TC_SQL_HOST" -d "$env:TC_TEST_DATABASE" -E -b -i database/tests/liem/<TASK-ID>.sql
mvn -B -Pbrowser-it verify
```

`-E` chỉ dùng Windows integrated auth khi runbook đã cấu hình; auth khác theo runbook Khánh/Vương, không đặt password `-P` hoặc in secret. SQL assertion dùng THROW/mã thoát khác0 nếu sai, không PRINT-only. Mỗi `docs/evidence/liem/LIEM-nn.md` ghi commit, môi trường/version, fixture registry, source, test đỏ/xanh phù hợp, lệnh/exit code, expected/actual, DB trước/sau, race barrier hai connection, trace lọc, ảnh UI, blocker và người nhận. Không cookie/OTP/QR/raw khóa thanh toán. Task đóng chỉ khi reviewer kiểm chứng; trạng thái ban đầu tất cả chưa làm.

## LIEM-01 — Cấp Model, DTO và SPI cho cả nhóm từ đầu M0

**Mục tiêu/nguồn/mốc:** bảy Model đủ diagram và compile contract có thể dùng ngay; spec §5/7, TEAM §3.2–3.3; M0. **Phụ thuộc:** hợp đồng đã khóa; phối hợp KHANH-01/DONG-01/THAI-01/VUONG-01 đầu ra Model/type nền, không chờ các luồng hoàn chỉnh.

**Create:** bảy Model ở bảng §1; các DTO mỗi file đúng tên/nhóm §2; `src/main/java/vn/ticketscenter/job/JobHandler.java`, `JobHandlerRegistry.java`, `OutboxPublisher.java`; Service interface ở §3. **Modify:** `docs/backend/liem-contract.md` (tạo nếu chưa có) ghi enum/state/field/SQL result và caller. **Test:** `src/test/java/vn/ticketscenter/model/order/SalesModelTest.java`, `src/test/java/vn/ticketscenter/model/fulfillment/TicketTransitionTest.java`, `src/test/java/vn/ticketscenter/contract/LiemInterfacesTest.java`.

**Consumes:** Khánh ActorContext/Page/PageRequest/ClockProvider; Đông Event/Zone/Seat và Khánh User nền. **Produces:** đúng toàn bộ signature §1–3; DTO/schema mail/outbox contract gửi Khánh/Đông/Thái/Vương. Không endpoint nghiệp vụ đạt ở bước compile.

- [ ] Viết test trạng thái: Order.markPaid(null) với total>0 bị chặn, Payment sai Order bị chặn; Ticket ACTIVE→USED; ACTIVE→REFUND_PENDING→ACTIVE/REFUNDED; USED không refund.
- [ ] Chạy unit nhận FAIL đúng thiếu hành vi; viết Model/enum/factory theo diagram, snapshot/item bất biến, money nguyên và check time boundary.
- [ ] Cấp DTO/SPI/service declarations với nullability; compile caller contract. Stub tạm throw unsupported, không trả DTO thành công giả.
- [ ] Review Ticket methods với Thái: start−60 phút nhận, endTime từ chối, refund tại startTime từ chối, canceled không khôi phục. Kiểm CAPTURED không downgrade.
- [ ] Chạy `mvn -B -Dtest=SalesModelTest,TicketTransitionTest,LiemInterfacesTest test`: expected PASS và đủ named assertions, khi tooling Khánh sẵn sàng; ghi evidence `docs/evidence/liem/LIEM-01.md` và bàn giao ngay M0, tách stub compile với nghiệp vụ.

## LIEM-02 — Schema bán vé, khóa và hợp đồng SQL

**Mục tiêu/nguồn/mốc:** migration nền bảo vệ bất biến, caller biết kiểu/result/error; spec §5.3/8.2/14.2–3/14.8, M0. **Phụ thuộc:** LIEM-01; KHANH-04/DONG-02 schema identity/event, VUONG-02 manifest/fixture; THAI-01 chỉ để review cross-FK, không cần refund worker.

**Create:** `database/migrations/0030_sales.sql`, `database/migrations/0150_IX03.sql`, `database/migrations/0150_IX04.sql`, `database/migrations/0150_IX06.sql`, `database/migrations/0150_IX09.sql`, `database/migrations/0150_IX11.sql`; `src/main/java/vn/ticketscenter/persistence/order/CouponRedemption.java`, `src/main/java/vn/ticketscenter/persistence/job/OutboxMessage.java`. **Modify:** `docs/backend/liem-contract.md` kiểu SQL/result lỗi; gửi fragment FK cho chủ `database/migrations/0100_cross_domain_keys.sql`/manifest `database/README.md`, không viết bảng của Thái/Vương. **Test:** `database/tests/liem/LIEM-02.sql`, `src/test/java/vn/ticketscenter/acceptance/LiemSchemaIT.java`.

**Consumes:** UUID/money/time mappings, database object names/grants Vương. **Produces:** schema/constraints/index chính thức §5, FK internal đầy đủ; external FK deferred theo manifest, không có dangling link.

- [ ] Test đỏ inserts NULL/âm/seat quantity2/Payment amount0/txnRef trùng; implement C06/07/08/09/11/12/13/14 với CHECK xử lý NULL+NOT NULL và enum miền.
- [ ] Unique một ACTIVE Hold/user, Order/Hold, redemption/order, mã và outbox dedup; no cascade lịch sử. AcceptedPayment liên kết cùng Order/CAPTURED do SP09 enforce, không tuyên bố FK một cột tự kiểm trạng thái.
- [ ] Định nghĩa result sets: SP02/07 HoldDto data, SP03/06 OrderDto breakdown+items, SP08 PaymentIntent hoặc zero marker, SP09 PaymentApplyDto; ghi lỗi domain stable dùng helper Khánh, money scalar SQL và JSON đã validation.
- [ ] Chốt locking.md cùng Đông/Thái/Vương: user trước tập Event ID tăng, không lấy User sau Event; discovery lại nếu tập Event đổi; kho/coupon ID tăng, retry deadlock hữu hạn. Kiểm worker/callback không đảo thứ tự.
- [ ] Chạy `mvn -B -Psqlserver-it -Dit.test=LiemSchemaIT verify` và script `LIEM-02.sql`: expected schema/mapping đúng và inserts sai rollback. Bàn giao FK/key/include/3NF snapshot rationale, evidence `docs/evidence/liem/LIEM-02.md` cho Vương; fresh migration chưa có table Refund vẫn tạo sales thành công.

## LIEM-03 — SP07 giải phóng giữ vé nguyên tử

**Mục tiêu/nguồn/mốc:** trả ghế/quota/coupon một lần, không sửa Payment vì TTL; spec §6.4/8/14 SP07/TX07; M1 chuẩn bị M2. **Phụ thuộc:** LIEM-01/02, DONG-01 Seat/Zone mutations, KHANH-03 TransactionRunner và KHANH-06 actor/principal.

**Create:** `database/migrations/0390_SP07.sql`, `src/main/java/vn/ticketscenter/repository/ticketing/HoldRepository.java`, `src/main/java/vn/ticketscenter/service/ticketing/HoldService.java`, `src/main/java/vn/ticketscenter/controller/ticketing/HoldServlet.java`. **Modify:** `docs/backend/liem-contract.md`. **Test:** `database/tests/liem/LIEM-03.sql`, `src/test/java/vn/ticketscenter/acceptance/LiemReleaseIT.java`.

**Consumes:** `TicketHold.release`, Seat.release/Zone.releaseHeldStanding và TransactionRunner.required; **Produces:** `HoldService.cancel(actor,holdId):HoldDto`, SP07 signature §5; browser POST cancel owner+CSRF200/repeat200, other owner404, invalid state409; system reason chỉ principal worker.

- [ ] Test đỏ cancel seated/standing: ACTIVE→RELEASED, đúng assignment trả kho, Order chưa trả CANCELLED hoặc EXPIRED, RESERVED→RELEASED; CONSUMED không release.
- [ ] Implement reason enum BUYER_CANCEL/EXPIRED/EVENT_CANCELLED; actor null chỉ system context; expiry phải clock>=expiresAt hoặc cancelled đúng Event; không đặt Payment FAILED do hết hạn.
- [ ] Gây lỗi sau trả ghế trước release coupon: cả hai rollback, outer JPA/SP transaction không commit; hai connection buyer/worker race trả một lần.
- [ ] Timeout sau commit, GET current/order rồi cancel replay: kết quả cũ, counters không âm; lỗi owner/CSRF không mutation.
- [ ] Chạy `mvn -B -Psqlserver-it -Dit.test=LiemReleaseIT verify` + SQL `LIEM-03.sql`: expected TX07 atomic/idempotent. Evidence `docs/evidence/liem/LIEM-03.md`; bàn giao SP07 cho SP02 Liêm, SP17 Thái, worker.

## LIEM-04 — SP02 giữ 1–8 vé và dọn Hold cũ cross-Event

**Mục tiêu/nguồn/mốc:** all-or-nothing giữ với TTL10 phút, một ACTIVE/user; spec §6.4/14 SP02/TX02, ngày08; M2. **Phụ thuộc:** LIEM-02/03; DONG-01 model/schema và DONG tasks publish/catalog M1; KHANH auth verified M1 (contract từ KHANH-01).

**Create:** `database/migrations/0400_SP02.sql`. **Modify:** `src/main/java/vn/ticketscenter/repository/ticketing/HoldRepository.java`, `src/main/java/vn/ticketscenter/service/ticketing/HoldService.java`, `src/main/java/vn/ticketscenter/controller/ticketing/HoldServlet.java`, `docs/backend/liem-contract.md`. **Test:** `database/tests/liem/LIEM-04.sql`, `src/test/java/vn/ticketscenter/acceptance/LiemHoldIT.java`.

**Consumes:** SP07, User.isEligibleBuyer, Event.isSaleActive và kho Đông; **Produces:** `HoldService.create(actor,HoldCommand):HoldDto`, `getCurrent(actor):HoldDto`, POST holds201 / conflict409; MeServlet Khánh adapter GET me/hold200.

- [ ] Tạo test fixture buyer A/time03:00, Event/zone/seat TEAM; example ở cuối file phải ra expires03:10/unitPrice200000/no Order/Payment/Ticket.
- [ ] SP02 khóa buyer, tìm Hold cũ và tập Event, giải phóng expired cũ+coupon/Order trong **cùng** transaction trước giữ mới; ACTIVE chưa hết bị409, không âm thầm thay Hold.
- [ ] Validate tổng1/8 nhận; 0/9 từ chối; trùng ghế, seat sai zone/event, standing có seat/seated thiếu seat, quantity sai, user unverified/disabled, Event draft/canceled/outside sale đều không giữ item nào.
- [ ] Item cuối hết chỗ hoặc lỗi sau item đầu: rollback Hold/item/kho và cleanup expired cũ nếu cùng transaction. Hai buyer tranh seat, standing capacity3, hai tab cùng user/cross-Event: chỉ một kết quả hợp lệ, timeout barrier hữu hạn.
- [ ] Biên saleStart nhận, saleEnd và expiresAt từ chối; tắt worker vẫn chặn expired. Chạy `mvn -B -Psqlserver-it -Dit.test=LiemHoldIT verify` + SQL `LIEM-04.sql`: expected TX02/unique/TTL/cross-Event PASS. Evidence `docs/evidence/liem/LIEM-04.md`; bàn giao DTO cho Đông UI02 và Khánh MeServlet.

## LIEM-05 — SP06 một Order/Hold với snapshot bất biến

**Mục tiêu/nguồn/mốc:** một lần tạo đơn, không kéo dài TTL/reprice; spec §5/6.4/14 SP06/TX06, ngày09; M2. **Phụ thuộc:** LIEM-01/02/04, KHANH-02 HTTP helpers.

**Create:** `database/migrations/0400_SP06.sql`, `src/main/java/vn/ticketscenter/repository/order/OrderRepository.java`, `src/main/java/vn/ticketscenter/service/order/OrderService.java`, `src/main/java/vn/ticketscenter/service/order/OrderQueryService.java`, `src/main/java/vn/ticketscenter/controller/order/OrderServlet.java`. **Modify:** `docs/backend/liem-contract.md`. **Test:** `database/tests/liem/LIEM-05.sql`, `src/test/java/vn/ticketscenter/acceptance/LiemOrderIT.java`.

**Consumes:** Hold.isActive(now) và Order.fromHold/Item.lineTotal; **Produces:** create(actor,holdId):OrderDto, query get/listOwn signatures §3, POST orders201 mới/200 replay, owner404 ngoài phạm vi.

- [ ] Test tăng Zone.price sau Hold: OrderItem vẫn giữ unitPrice cũ và chụp zoneName/seatLabel lúc tạo Order; subtotal từ DB, discount0, total=subtotal; không browser money.
- [ ] Tạo một Order/code và tất cả item qua SP06, unique holdId; replay trả existing Order đúng quyền, không gọi gateway/tạo lần giữ mới.
- [ ] Hold expired/consumed và Event không bán không tạo mới; giải thích retry existing closed Order bằng query không hồi sinh trạng thái.
- [ ] Hai connection/two-tab nhận cùng orderId/code; lỗi Item thứ2 và outer rollback không để orphan Order/item, TTL vẫn03:10.
- [ ] Chạy `mvn -B -Psqlserver-it -Dit.test=LiemOrderIT verify` + SQL `LIEM-05.sql`: expected snapshot/TX06/Pagination owner PASS. Evidence `docs/evidence/liem/LIEM-05.md`, bàn giao OrderDto cho UI04/Khánh me/orders và PaymentService.

## LIEM-06 — Coupon Service, F01/F08/V09/TR07

**Mục tiêu/nguồn/mốc:** quản lý coupon đúng org, tiền floor/cap30%, lượt dùng không vượt quota; spec §6.5/6.7/14 C06/F01/F08/V09/TR07, ngày09; M2. **Phụ thuộc:** LIEM-01/02; KHANH-01 AuthorizationService, DONG-01 Organization; không cần thanh toán hoàn tất.

**Create:** `database/migrations/0190_V09.sql`, `database/migrations/0200_F01.sql`, `database/migrations/0200_F08.sql`, `database/migrations/0500_TR07.sql`; `src/main/java/vn/ticketscenter/repository/order/CouponRepository.java`, `src/main/java/vn/ticketscenter/service/order/CouponService.java`, `src/main/java/vn/ticketscenter/controller/order/CouponServlet.java`. **Modify:** `docs/backend/liem-contract.md`, gửi exception thứ tự V09→F08 cho Vương manifest. **Test:** `src/test/java/vn/ticketscenter/model/order/CouponMoneyTest.java`, `src/test/java/vn/ticketscenter/acceptance/LiemCouponIT.java`, `database/tests/liem/LIEM-06.sql`.

**Consumes:** AuthorizationService.requireOrganizationRole(actor,org,{MANAGER}) hoặc requireAdmin và model reviseTerms; **Produces:** list/create/edit đúng TEAM và setActive/deleteUnused/eligibility §3; caller OrganizationServlet Đông/AdminServlet Vương chỉ adapter. CouponServlet owns /coupons mutations.

- [ ] Test vectors Java/F01: subtotal500000 fixed200000→150000; percentage30→150000; subtotal1→0; subtotal0→0; invalid missing/wrong fields→NULL SQL/domain error Java, không coalesce0.
- [ ] Normalize trim/uppercase code, unique/org; same code khác org hợp lệ. Validation type-exclusive/positive/maxUses/time; sửa terms không đổi snapshot Order cũ.
- [ ] V09 left join coupon chưa dùng và count states; expired RESERVED vẫn count tới SP07. F08 kiểm org/active/[validFrom,validTo)/quota và preview qua F01, không giữ lượt.
- [ ] TR07 set-based multirow, maxUses=allocated nhận, <allocated reject cả statement; lock Coupon đồng bộ SP03. Delete chưa reference nhận204; có redemption bất kỳ trạng thái chặn409, dùng deactivate.
- [ ] User manager A/check-in B không edit B; admin chọn org bất kỳ, no unauth. Chạy `mvn -B -Dtest=CouponMoneyTest test`, `mvn -B -Psqlserver-it -Dit.test=LiemCouponIT verify` + SQL `LIEM-06.sql`: expected tiền/rights/trigger PASS. Evidence `docs/evidence/liem/LIEM-06.md`; bàn giao UI14 và quota race LIEM-07.

## LIEM-07 — SP03 giữ/đổi/bỏ coupon nguyên tử

**Mục tiêu/nguồn/mốc:** chốt discount từ server, đổi mã lỗi không mất lượt cũ; spec §6.5/14 SP03/TX03; M2. **Phụ thuộc:** LIEM-03/05/06 và Payment schema LIEM-02.

**Create:** `database/migrations/0400_SP03.sql`. **Modify:** `src/main/java/vn/ticketscenter/repository/order/OrderRepository.java`, `src/main/java/vn/ticketscenter/service/order/OrderService.java`, `src/main/java/vn/ticketscenter/controller/order/OrderServlet.java`, `docs/backend/liem-contract.md`. **Test:** `database/tests/liem/LIEM-07.sql`, `src/test/java/vn/ticketscenter/acceptance/LiemApplyCouponIT.java`.

**Consumes:** F01/F08/V09, Coupon locks and hold clock; **Produces:** applyCoupon(actor,orderId,codeOrNull):OrderDto, POST coupon200, invalid code400/domain rejection hoặc hết quota409, owner404, pending/unknown409; GET eligibility read-only.

- [ ] Test initial reserve, same code replay, remove→RELEASED, A→B đổi nguyên tử; giữ unique redemption/order và tổng counts không vượt maxUses.
- [ ] Same RESERVED hợp lệ không hồi tố khi terms/toggle/time đổi; lượt mới dùng terms hiện tại; không gia hạn Hold.
- [ ] PENDING/UNKNOWN bất kỳ lần thu đang theo dõi chặn đổi/bỏ; FAILED đã xác minh với Hold còn hạn cho thao tác; đã PAID/CONSUMED không trả lượt kể cả hoàn sau này.
- [ ] TX03 inject lỗi sau release mã cũ trước reserve mới: rollback code/discount/redemption; hai order quota1 chỉ một reserve; A→B/B→A lock theo ID; sửa maxUses đua reserve vẫn không vượt quota.
- [ ] Chạy `mvn -B -Psqlserver-it -Dit.test=LiemApplyCouponIT verify` + SQL `LIEM-07.sql`: expected atomic/outer rollback/race PASS. Evidence `docs/evidence/liem/LIEM-07.md`; bàn giao breakdown UI04/PaymentService, Thái lưu nguyên CONSUMED.

## LIEM-08 — SP08 khởi tạo Payment, dùng lại lần đang theo dõi

**Mục tiêu/nguồn/mốc:** mỗi Payment có Order/amount snapshot; commit trước gateway, không tạo thêm khi UNKNOWN; spec §6.6/14 SP08/TX08, ngày10; M2. **Phụ thuộc:** LIEM-05/07, KHANH-01 transaction/principal.

**Create:** `database/migrations/0400_SP08.sql`, `src/main/java/vn/ticketscenter/repository/order/PaymentRepository.java`, `src/main/java/vn/ticketscenter/service/order/PaymentService.java`. **Modify:** `src/main/java/vn/ticketscenter/controller/order/OrderServlet.java`, `docs/backend/liem-contract.md`. **Test:** `database/tests/liem/LIEM-08.sql`, `src/test/java/vn/ticketscenter/acceptance/LiemBeginPaymentIT.java`.

**Consumes:** Payment.start(Order,txnRef), hold/event/coupon state; **Produces:** PaymentIntent/SP08, begin(actor,orderId):PaymentStartDto route200; unknown yields UNKNOWN no new URL/Payment. Zero marker delegates LIEM-10, không báo COMPLETED_ZERO trước commit SP09.

- [ ] Test amount>0/order bắt buộc/txnRef unique; acceptedPayment vẫn null tới capture. Hai tab Pay chỉ một paymentId/txnRef PENDING.
- [ ] Còn PENDING dùng intent cũ, UNKNOWN chỉ query; FAILED xác minh cho attempt mới khi còn Hold/sale, giữ attempt cũ; không reset lịch sử.
- [ ] Kiểm owner, total server, saleEnd/expiresAt; total0 không Payment. Khi gateway fail tạo URL sau commit không rollback giả Payment đã lưu, UI theo dõi hoặc reconcile.
- [ ] TX08 inject sau insert Payment rollback đầy đủ; probe chứng minh connection/transaction đã đóng trước external call/timeout.
- [ ] Chạy `mvn -B -Psqlserver-it -Dit.test=LiemBeginPaymentIT verify` + SQL `LIEM-08.sql`: expected TX08/reuse/nozero PASS. Evidence `docs/evidence/liem/LIEM-08.md`; bàn giao intent immutable cho gateway/UI04.

## LIEM-09 — VNPAY Sandbox verifier và HTTP callback

**Mục tiêu/nguồn/mốc:** browser không xác nhận tiền, gateway verifier tạo kết quả trusted; spec §6.6/9.4/10, ngày10; M2. **Phụ thuộc:** LIEM-01/08; Khánh cấu hình an toàn; sandbox credential do chủ dự án cấp, không chặn unit verifier nếu thiếu.

**Create:** `src/main/java/vn/ticketscenter/integration/payment/PaymentGateway.java`, `VnpaySandboxGateway.java`, `src/main/java/vn/ticketscenter/controller/order/VnpayServlet.java`, `docs/backend/vnpay.md`. **Modify:** `src/main/java/vn/ticketscenter/service/order/PaymentService.java`, `src/main/java/vn/ticketscenter/controller/order/OrderServlet.java`. **Test:** `src/test/java/vn/ticketscenter/integration/payment/VnpaySignatureTest.java`, `src/test/java/vn/ticketscenter/acceptance/LiemVnpayAdapterIT.java`.

**Consumes:** PaymentIntent/VerifiedPaymentResult §2/3, runtime merchant/secret/return/IPN URL allowlist; **Produces:** gateway signatures §3, VnpayServlet `/payments/vnpay/*`, IPN mapping protocol. Apply sink LIEM-10 chưa có thì integration block, không trả success giả.

- [ ] Khi triển khai đọc tài liệu VNPAY chính thức đang hiệu lực, lưu URL/ngày/version về canonicalization/encoding/sign fields/amount multiplier/timezone/status và IPN RspCode/Message; không đoán số mã response từ kế hoạch.
- [ ] Test vector chữ ký: Unicode/space/+/%/đảo order params, duplicate/malformed params reject; thay amount/txnRef/signature/merchant/currency/status không mutation. Constant-time compare; secret test ngoài Git.
- [ ] Create URL sau SP08 commit, host return sandbox từ config allowlist, no client redirect URL. Query timeout hữu hạn→UNKNOWN; không FAILED do mạng.
- [ ] IPN chữ ký/semantic verified mới gọi applyVerified→SP09; replay trả protocol cũ đúng. Return GET chỉ đọc owner DB; `vnp_ResponseCode=00` giả không Order.PAID/Ticket/Refund.
- [ ] Chạy `mvn -B -Dtest=VnpaySignatureTest test` và `mvn -B -Psqlserver-it -Dit.test=LiemVnpayAdapterIT verify`: expected verifier/wiring PASS, bằng chứng gắn nhãn ADAPTER. Sandbox thật ở LIEM-15; credential thiếu ghi BLOCKED riêng. Evidence `docs/evidence/liem/LIEM-09.md`, bàn giao Khánh config/Vương runbook.

## LIEM-10 — F06 và SP09 phát hành/đơn0đ/bù trừ

**Mục tiêu/nguồn/mốc:** accepted capture phát hành nguyên tử, thu muộn/trùng chỉ một Refund compensation; spec §6.6–7/14 F06/SP09/TX09, ngày11; M2. **Phụ thuộc:** LIEM-03/05/07/08/09; THAI-01 Refund model/schema0040/createCompensation/beginAttempt và technical log M0; VUONG-01 FK/grants; không chờ refund UI/worker hoàn chỉnh.

**Create:** `database/migrations/0200_F06.sql`, `database/migrations/0400_SP09.sql`, `src/main/java/vn/ticketscenter/service/order/TicketPaidAmountAllocator.java`. **Modify:** `src/main/java/vn/ticketscenter/repository/order/PaymentRepository.java`, `src/main/java/vn/ticketscenter/service/order/PaymentService.java`, `docs/backend/liem-contract.md`. **Test:** `src/test/java/vn/ticketscenter/model/order/TicketAllocationTest.java`, `src/test/java/vn/ticketscenter/acceptance/LiemPaymentResultIT.java`, `database/tests/liem/LIEM-10.sql`.

**Consumes:** VerifiedPaymentResult từ gateway/system, F06, SP07 và `Refund.createCompensation(Payment payment, String reason)` theo diagram Thái, OutboxPublisher.enqueue; **Produces:** PaymentApplyDto; SP09 đúng signature §5, trusted IPN/query/0đ use case gọi. Không route nhận verifiedResult/ticketCodes/attemptId từ buyer.

- [ ] F06 mở standing quantity thành từng ordinal, discount proportional/floor, đồng lẻ largest remainder tie orderItemId+ordinal thống nhất Java/SQL; vectors200000+300000/discount100000→160000+240000, ba vé10/discount1 tie ổn định, toàn0 không chia0, mixed0; sum paid=total và không âm.
- [ ] SP09 khóa Event/Order/Hold/Payment/kho/coupon, DB đối chiếu identity/amount. Valid capture now còn sale/Hold: Payment CAPTURED/paidAt, Order PAID/acceptedPayment/paidAt, Hold CONSUMED, sold kho, redemption CONSUMED, N Ticket/code/random unique backend+email outbox trong một transaction.
- [ ] Thu sau expiresAt/saleEnd/Event canceled/Payment khác khi Order đã PAID: CAPTURED vẫn lưu, `Refund` PAYMENT_COMPENSATION amount toàn khoản thu/APPROVED gắn Payment/no Ticket. Thái THAI-01 cung cấp unique filtered Refund(paymentId) WHERE purpose=PAYMENT_COMPENSATION trong 0040; SP09 giữ khóa Payment/Refund khi tạo. Nếu Hold ACTIVE đã hết hạn hoặc Event CANCELLED, gọi SP07 với đúng reason EXPIRED/EVENT_CANCELLED trong cùng transaction để trả đúng assignment/RESERVED. Nếu chỉ qua saleEnd khi Hold chưa hết TTL, không giả reason hết hạn: không phát hành và để Hold được giải phóng qua điều kiện SP07 hợp lệ. Unique compensation/Payment; optional backend attempt ID beginAttempt hoặc giữ APPROVED cho Thái handler; không RefundRequest/bản Ticket mới.
- [ ] Callback cùng Payment lặp giữ kết quả cũ, FAILED/UNKNOWN không downgrade CAPTURED. Zero total owner+principal buyer/paymentId NULL được SP09 kiểm và PAID/paidAt/Ticket nhưng không Payment/gateway/RefundTransferLog giả.
- [ ] compensation retryFailedCompensation mặc định false; redelivery không new attempt. APPROVED bắt đầu lần đầu; RETRYABLE chỉ retry đã quyết định quyền/chứng cứ FAILED trên cùng Refund với attemptId mới; UNKNOWN chỉ reconcile currentAttemptId. Adapter Thái sau commit; SP09 không gọi gateway hoàn.
- [ ] Inject vé thứN/outbox fail rollback Payment/Order/hold/kho/redemption/Ticket/outbox; outer JPA transaction cùng connection. Hai IPN cùng txnRef một bộ vé; hai capture khác chỉ một acceptedPayment còn một compensation. Buyer direct SP09 payment có tiền bị DB reject, nhánh0đ đúng owner được nhận.
- [ ] Chạy `mvn -B -Dtest=TicketAllocationTest test`, `mvn -B -Psqlserver-it -Dit.test=LiemPaymentResultIT verify` + SQL `LIEM-10.sql`: expected F06/TX09/security/replay PASS. Evidence `docs/evidence/liem/LIEM-10.md`; bàn giao Ticket methods/compensation/refund IDs cho Thái, cashflow facts cho Vương.

Yêu cầu phối hợp THAI-11: SP09 bật retryFailedCompensation chỉ khi actorId là ADMIN hiện hành và nghĩa vụ RETRYABLE/current attempt FAILED đã xác minh. Thái Service chọn principal kỹ thuật sau kiểm ADMIN, giữ actor thật cho audit; worker SYSTEM redelivery không tự được thử ID mới. Thêm direct-SP negative test cho cờ retry ở sai ngữ cảnh.

## LIEM-11 — Một dispatcher outbox có lease và lifecycle

**Mục tiêu/nguồn/mốc:** các handler thành viên chạy bền vững, không nhân effect qua restart; spec §8.4/10/11.2, ngày12; M2. **Phụ thuộc:** LIEM-01/02/10, KHANH-01 transaction/config/system actor và Khánh mail hook; DONG-01/THAI-01 handler registrations contract.

**Create:** `src/main/java/vn/ticketscenter/job/OutboxWorker.java`, `src/main/java/vn/ticketscenter/config/WorkerListener.java`, `src/main/java/vn/ticketscenter/repository/job/OutboxRepository.java`, `database/migrations/0410_outbox_lease.sql` (SP kỹ thuật dbo.usp_ClaimOutbox và dbo.usp_AcknowledgeOutbox, không tính SP01–17). **Modify:** `src/main/java/vn/ticketscenter/job/OutboxPublisher.java`, `JobHandlerRegistry.java`, `docs/backend/operations.md` phần outbox. **Test:** `src/test/java/vn/ticketscenter/acceptance/LiemOutboxIT.java`, `database/tests/liem/LIEM-11.sql`.

**Consumes:** `JobHandler.handle(OutboxMessageDto):JobResult`, enqueue SPI; **Produces:** OutboxRepository.claim/acknowledge chữ ký §3 và SP kỹ thuật: usp_ClaimOutbox(leaseOwner nvarchar(100),nowUtc datetime2,batchSize int,leaseSeconds int) trả OutboxMessageDto rows; usp_AcknowledgeOutbox(messageId uniqueidentifier,leaseOwner nvarchar(100),leaseToken uniqueidentifier,resultStatus nvarchar(16),nextAttemptAt datetime2 nullable,nowUtc datetime2) trả applied bit. Tham số thời gian chỉ từ ClockProvider hệ thống; claim/ack lease core và registry chung, cấu hình batch/backoff/max attempts/timeouts qua config validated. Email handler Khánh, image Đông, refund/cancellation Thái đăng ký đúng types/version, không executor riêng.

- [ ] Test duplicate enqueue cùng type/version/key cùng transaction trả existing ID, rollback nghiệp vụ không outbox; event email chỉ IDs payload §3.
- [ ] Claim pending đến hạn/expired lease bằng locked UPDATE OUTPUT transaction ngắn; owner/token/until mới; commit rồi handle ngoài transaction. ACK/retry chỉ lease token hiện tại, stale worker không ghi đè.
- [ ] Retry có exponential backoff giới hạn và attempts tối đa cấu hình, FAILED giữ payload/lý do lọc; poison unknown handler/version không silent-success. Transport retry không tự authorise new refund attempt.
- [ ] Startup một dispatcher/scheduler, shutdown ngừng nhận, drain timeout/cancel an toàn, close resources; redeploy không thread rò. Hai worker test tranh batch một lease còn hiệu lực chỉ một owner.
- [ ] Crash sau claim, sau effect trước ACK, restart/lease expiry: SP idempotency không nhân effect; email có thể duplicate nếu provider không dedup, không hứa exactly-once. Chạy `mvn -B -Psqlserver-it -Dit.test=LiemOutboxIT verify` + SQL `LIEM-11.sql`: expected leases/lifecycle/durability PASS. Evidence `docs/evidence/liem/LIEM-11.md`; bàn giao cả ba handler chủ và Vương vận hành.

## LIEM-12 — Dọn Hold và reconcile Payment PENDING/UNKNOWN

**Mục tiêu/nguồn/mốc:** worker khôi phục nghiệp vụ sau host ngủ/restart; spec §8.4/10/11.2/14 IX04/IX06, ngày12; M2. **Phụ thuộc:** LIEM-03/09/10/11; Thái compensation handler qua THAI-01 contract, kết quả hoàn M3 riêng.

**Create:** `src/main/java/vn/ticketscenter/job/HoldExpiryJob.java`, `PaymentReconciliationJob.java`. **Modify:** `src/main/java/vn/ticketscenter/repository/order/PaymentRepository.java`, `src/main/java/vn/ticketscenter/repository/ticketing/HoldRepository.java`, `src/main/java/vn/ticketscenter/service/order/PaymentService.java`, `docs/backend/operations.md`. **Test:** `src/test/java/vn/ticketscenter/acceptance/LiemReconciliationIT.java`, `database/tests/liem/LIEM-12.sql`.

**Consumes:** ClockProvider, IX04/06, SP07, gateway.query→VerifiedPaymentResult→SP09; **Produces:** handlers HOLD_EXPIRE/PAYMENT_RECONCILE version1 và polling producer đăng ký scheduler chung; compensation enqueue cho Thái handler PAYMENT_COMPENSATION.

- [ ] Batch query status/time/keyset ID ổn định dùng index spec, transaction ngắn từng object; scheduler enqueue dedup hiện hành, không poll quá thường để giữ dịch vụ thức.
- [ ] Worker tắt vẫn HTTP giữ/mua kiểm expiry; bật lại SP07 trả một lần, không sửa Payment FAILED.
- [ ] Pending đủ tuổi/UNKNOWN query cổng, validate như IPN; query timeout giữ UNKNOWN/next attempt không thu lại hoặc gia hạn Hold; final capture after expiry→compensation/no Ticket.
- [ ] Crash sau provider result trước SP09, sau SP09 commit trước ACK; cùng txnRef/currentAttempt replay giữ facts; xác minh gateway call ngoài locks/pool finite timeout.
- [ ] Chạy `mvn -B -Psqlserver-it -Dit.test=LiemReconciliationIT verify` + SQL `LIEM-12.sql`: expected recover/index query/results PASS. Evidence `docs/evidence/liem/LIEM-12.md`; metrics pending count/oldest age/failures đã lọc cho Khánh health và Vương operations.

## LIEM-13 — Query lịch sử, vé, QR và snapshot guard

**Mục tiêu/nguồn/mốc:** owner đọc được vé thật và chia sẻ ảnh một vé, không rò QR/graph; spec §6.8/9 UI06–07/14 V06/TR08/IX03/IX11, ngày11; M2. **Phụ thuộc:** LIEM-01/05/10, KHANH-02 HTTP helpers, Thái Ticket transitions contract M0. Bàn giao query/DTO cho phần orders/tickets của KHANH-13 MeServlet ở M2; hai bên ghép adapter khi query có, không chờ MeServlet hoàn tất mới cung cấp query.

**Create:** `database/migrations/0300_V06.sql`, `database/migrations/0500_TR08.sql`, `src/main/java/vn/ticketscenter/repository/fulfillment/TicketRepository.java`, `src/main/java/vn/ticketscenter/service/fulfillment/TicketQueryService.java`, `TicketService.java`, `src/main/java/vn/ticketscenter/controller/fulfillment/TicketServlet.java`, `src/main/java/vn/ticketscenter/integration/qr/QrEncoder.java`; `src/main/webapp/WEB-INF/views/order/my-orders.jsp`, `src/main/webapp/WEB-INF/views/order/order-detail.jsp`, `src/main/webapp/WEB-INF/views/fulfillment/tickets.jsp`, `src/main/webapp/WEB-INF/views/fulfillment/ticket.jsp`; `src/main/webapp/assets/js/ticket-view.js`. **Modify:** `src/main/java/vn/ticketscenter/service/order/OrderQueryService.java`, gửi MeServlet adapter cho Khánh, `docs/backend/liem-contract.md`. **Test:** `src/test/java/vn/ticketscenter/acceptance/LiemTicketQueryIT.java`, `database/tests/liem/LIEM-13.sql` và browser cases `src/test/java/vn/ticketscenter/browser/LiemTicketBrowserIT.java`.

**Consumes:** TicketDto, OrderDto, Page signatures §3, layout Khánh, actual issued data và `RefundService.listForOrder(ActorContext actor, UUID orderId):List<RefundDto>` của Thái (THAI-01 khóa signature, task query Thái hoàn thiện M3); **Produces:** UI06 history grouped order→tickets, UI07 single-ticket/QR, V06/TR08 và owner routes §4. QR sharing chọn **tải/chia sẻ ảnh**, không thêm mandatory share-token lifecycle.

- [ ] Query V06 một dòng/Ticket, no check-in log nhân dòng, không ticketCode. listOwn/get owner trước QR code fetch; sort/page allowlist/ID; archived canceled Event vẫn lịch sử đọc được.
- [ ] QR encode random code từng Ticket, standing N mã khác nhau; image private/no-store, owner khác404, staff không bypass owner. Không log raw QR và không gửi QR trong list/email payload.
- [ ] TR08 set-based chặn orderItemId/ticketCode/issuedAt/paidAmount đổi; multirow có một snapshot sai rollback cả; status transitions từ Thái vẫn chạy, trigger không phát hành.
- [ ] JSP UI06 frame bộ lọc/list grouped/code/tổng/trạng thái/actions mở vé/hoàn; trạng thái compensation từ PaymentService.status; OrderServlet gọi `List<RefundDto> RefundService.listForOrder(ActorContext actor, UUID orderId)` do Thái cung cấp, kiểm owner từ DB, để render khối hoàn liên quan của UI06. RefundDto giữ đúng schema Thái, không tạo bản DTO ở Liêm; link lịch sử `/me/refund-requests` do Khánh gọi RefundService Thái; UI07 frame thông tin event/khu/ghế/QR/tải/chia sẻ/trạng thái; USED/REFUND_PENDING/REFUNDED/INVALIDATED không diễn đạt vào cửa được. Link UI08 Thái chỉ khi query đủ điều kiện server, không frontend tự quyết.
- [ ] Empty/loading/error focus/401/login/403/409/responsive keyboard; fetch JSON không trả HTML, JSP escaping chống XSS event labels. Chạy `mvn -B -Psqlserver-it -Dit.test=LiemTicketQueryIT verify` + SQL `LIEM-13.sql`, browser profile khi Vương tạo: expected owner/snapshot/UI06–07 PASS. Evidence `docs/evidence/liem/LIEM-13.md`; Khánh tích hợp MeServlet, Thái review refund links/transitions.

## LIEM-14 — UI04 checkout, UI05 trạng thái và UI14 coupon

**Mục tiêu/nguồn/mốc:** đủ JSP/JS dùng backend thật, theo prototype và layout chung; spec §9 UI04/05/14/9.3, M2. **Phụ thuộc:** LIEM-04…10/13; Khánh representation từ KHANH-02 và layout/api-client từ KHANH-11; hợp đồng M0 từ DONG-01/VUONG-01, OrganizationServlet do DONG-05 tạo và DONG-11 bổ sung coupon adapter, AdminServlet/coupon adapter do VUONG-12 phối hợp LIEM-06/14. Không coi hợp đồng M0 là Servlet đã triển khai.

**Create:** `src/main/webapp/WEB-INF/views/ticketing/checkout.jsp`, `src/main/webapp/WEB-INF/views/payment/status.jsp`, `src/main/webapp/WEB-INF/views/order/coupons.jsp`; `src/main/webapp/assets/js/checkout.js`, `src/main/webapp/assets/js/payment-status.js`, `src/main/webapp/assets/js/coupons.js`; `src/test/java/vn/ticketscenter/browser/LiemCheckoutBrowserIT.java`, `src/test/java/vn/ticketscenter/browser/LiemCouponBrowserIT.java`. **Modify:** `src/main/java/vn/ticketscenter/controller/order/OrderServlet.java`, `VnpayServlet.java`, `src/main/java/vn/ticketscenter/controller/order/CouponServlet.java`, `docs/backend/liem-contract.md`; caller MeServlet/OrganizationServlet/AdminServlet do chủ sửa. **Test:** `src/test/java/vn/ticketscenter/acceptance/LiemSalesHttpIT.java` và browser classes trên.

**Consumes:** service signatures §3 và routes §4, api-client CSRF/error helpers; **Produces:** UI04/05/14 fullstack, exact JSP paths gửi caller. HTML GET/JSON Accept dùng cùng DTO/rights.

- [ ] UI04 đúng frame: event+lựa chọn đã giữ, Hold countdown và saleEnd riêng dựa serverNow; coupon input/preview, subtotal/discount/total server, pay/cancel; countdown không gia hạn hoặc tự coi Order paid. Quantity/ghế sau hold không sửa cục bộ rồi báo giữ mới.
- [ ] Submit Order/coupon/pay/cancel JSON+CSRF; disable đang gửi, network timeout đọc me/hold/order/payment-status trước retry. REDIRECT URL từ server, zero COMPLETED_ZERO sau commit; UNKNOWN chỉ thông báo đang đối chiếu.
- [ ] UI05 Return/status frame confirmed/pending/failed/compensation, mã đơn và liên kết vé/lịch sử; polling hữu hạn/backoff, dừng khi final/page hidden; querystring success không override DB. Không đẩy browser result xuống SP09.
- [ ] UI14 frame scope org/tạo-sửa type terms/time/quota, usage RESERVED/CONSUMED/RELEASED, activate/deactivate/delete-unused; bộ chọn org admin do Vương cung cấp, manager đúng org. Cùng JSP không duplicate logic/service; edit conflict cập nhật server, không hạ quota trên frontend rồi nhận thành công.
- [ ] Test money forgery/client clock/expired Hold/cross-org/CSRF missing/404/503, labels/keyboard/focus/empty/loading/success/error, mobile desktop. `mvn -B -Psqlserver-it -Dit.test=LiemSalesHttpIT verify`, `mvn -B -Pbrowser-it verify`: expected UI04/05/14 dùng DB thật và auth thật PASS; fixture fake gateway phải nhãn ADAPTER. Evidence `docs/evidence/liem/LIEM-14.md` có ảnh đã che secret, giữ placeholder hình thiếu; bàn giao Khánh/Đông/Vương.

## LIEM-15 — Race, rollback, replay và sandbox mua thật

**Mục tiêu/nguồn/mốc:** chứng minh M2/M3 không bán/hoàn/giải phóng trùng qua boundary và restart; spec §8/12/14 TX02/03/06/07/08/09, ngày08–12; M2 rồi ghép M3. **Phụ thuộc:** LIEM-03…14; Thái check-in/refund/cancel handlers M2–M3 (contract THAI-01); Vương principal/grants/fixtures VUONG-01; Khánh auth/persistence M1.

**Create:** `src/test/java/vn/ticketscenter/acceptance/LiemSalesAcceptanceIT.java`, `src/test/java/vn/ticketscenter/concurrency/LiemSalesRaceIT.java`, `src/test/java/vn/ticketscenter/recovery/LiemWorkerRecoveryIT.java`, `database/tests/liem/LIEM-15.sql`. **Modify:** `docs/backend/locking.md`, `docs/backend/operations.md`, `docs/backend/vnpay.md` ca của Liêm. **Consumes:** endpoints/services/SPs/handlers thật; **Produces:** evidence M2/3 và lỗi có owner, không route mới.

- [ ] Mỗi TX sở hữu có standalone+outer JPA rollback sau mutation: TX02 item cuối; TX03 sau trả mã cũ; TX06 Item2; TX07 giữa kho/coupon; TX08 Payment insert; TX09 véN/outbox. Assert số dòng/amount/assignment cuối, không chỉ HTTP.
- [ ] Hai SQL connections/barrier/timeout hữu hạn: cùng seat, capacity3, same buyer cross-Event, coupon last-use+maxUses edit, same Hold→Order, same Order→Payment, duplicate IPN, SP07 vs SP09, SP13 cancel vs SP09, check-in/refund qua Ticket methods với Thái. Kết quả không vừa sold vừa release, không overquota/duplicate acceptedPayment/Ticket/Refund.
- [ ] Boundary clock saleStart/saleEnd/expiresAt/start−60/end/startTime, 0/1/8/9 tickets, whole-zero/mixed-money/decimal upper bound; direct SP09 buyer có tiền sai context reject, staff finance deny, revoked session/cross-owner/cross-org reject.
- [ ] Restart sau claim, sau provider response, sau commit trước response; lease expiry/replay SPs preserve counts. UNKNOWN reconcile cùng txnRef/currentAttempt, FAILED retry mới có quyền giữ history. Hoàn customer/compensation thành công không đổi Ticket.paidAmount hoặc trả CONSUMED coupon.
- [ ] Sandbox thật: cấu hình merchant+HTTPS Return/IPN được cấp, tạo Hold→Order→pay→capture→vé+QR, lấy trace cổng đã lọc/DB kiểm amount/inventory/coupon/tickets/outbox; thử late và callback/query lặp có evidence adapter riêng khi cổng không cung cấp thao tác. Không gọi fake capture là sandbox. Credential thiếu ghi BLOCKED sandbox, không tick toàn M2.
- [ ] Chạy `mvn -B -Psqlserver-it -Dit.test=LiemSalesAcceptanceIT,LiemSalesRaceIT,LiemWorkerRecoveryIT verify` + SQL `LIEM-15.sql` và browser profile: expected named cases PASS/FAIL/BLOCKED thật theo commit. Evidence `docs/evidence/liem/LIEM-15.md`; Đông/Thái/Vương/Khánh review khóa chung, điều kiện M2/M3 mở mốc ở TEAM.

## LIEM-16 — Ghép migration, benchmark và hồ sơ bàn giao

**Mục tiêu/nguồn/mốc:** người mới clone chạy được phần Liêm và có truy vết SQL/API/UI bằng chứng; spec §12/14.8/14.11–14, M4. **Phụ thuộc:** LIEM-01…15 và M4 tooling/manifest/browser profile của Vương, build/runbook Khánh (foundation KHANH-01/VUONG-01).

**Create:** `database/benchmarks/liem/IX03.sql`, `database/benchmarks/liem/IX04.sql`, `database/benchmarks/liem/IX06.sql`, `database/benchmarks/liem/IX09.sql`, `database/benchmarks/liem/IX11.sql`; `docs/backend/liem-handoff.md`. **Modify:** `docs/backend/liem-contract.md`, evidence `docs/evidence/liem/LIEM-16.md`; gửi fixture builders/cases cho chủ `database/seeds/test-fixtures.sql` và `src/test/java/vn/ticketscenter/support/TestFixtures.java`, catalog/report ghép Vương; không viết lại schema đối soát/hoàn. **Test:** `src/test/java/vn/ticketscenter/acceptance/LiemFreshInstallIT.java`, `database/tests/liem/LIEM-16.sql`.

**Consumes:** manifest/checksum/grants/fixture registry và build WAR; **Produces:** phần ERD/3NF/candidate key/snapshot rationale, API/SQL state machine, runbook pending/UNKNOWN/outbox/compensation, minh chứng năm UI cho hồ sơ/slide/demo Vương.

- [ ] Fresh database apply 0010→0020→0030→0040→0050→0100→IX→V09→F→V06→SP07→SP caller→TR→grants theo manifest đã đăng ký; verify FK cycles, no missing object và Hibernate validate. Nếu migrations đã shared, sửa bằng migration mới có thứ tự, không amend file cũ.
- [ ] Benchmark năm IX đúng key/include spec với data distribution buyer ít orders/active ít trong history/payment statuses/coupon released nhiều/OrderItem tickets. Cùng query/results baseline/after actual plan/logical reads/time/write cost; giữ môi trường/rowcount, không buộc optimizer seek bằng giả plan. Vương ghép đủ15IX.
- [ ] Catalog trace từ app: đủ C06/07/08/09/11/12/13/14,V06/V09,SP02/03/06/07/08/09,F01/06/08,TR07/08,IX03/04/06/09/11,TX02/03/06/07/08/09; direct SP tests quyền R01 và technical phối hợp Vương. Không bỏ SQL object vì JPA đã làm tương tự.
- [ ] Runbook lost-response/read status trước retry, unknown không thu lại, compensation/Thái handler reconciliation, lease/restart, email duplicate limitation; cấu hình bí mật/runtime, local/sandbox/online phân biệt. Không hướng dẫn UPDATE trực tiếp tiền/kho trong SSMS.
- [ ] Chạy `mvn -B verify`, `mvn -B -Psqlserver-it verify`, `mvn -B -Pbrowser-it verify`, SQL `LIEM-16.sql` và fresh WAR Tomcat smoke theo runbook: expected rebuild/migrations/24UI integration trong bản ghép; ghi phần Liêm/ngoài phạm vi thiếu thành BLOCKED đúng chủ. Evidence `docs/evidence/liem/LIEM-16.md`, reviewer Vương/Khánh; báo cáo chỉ trạng thái đã thực kiểm, không synthetic PASS.

## Ví dụ cho AI và người kiểm thử

Fixture TEAM base time `2026-10-06T03:00:00Z`, buyer A verified login và CSRF runtime; UUID đầy đủ từ registry, không copy ID viết tắt. Không truyền now/owner/principal.

```http
POST /holds
Content-Type: application/json
X-CSRF-Token: <token runtime>

{"eventId":"10000000-0000-0000-0000-000000000001","selections":[{"zoneId":"20000000-0000-0000-0000-000000000001","seatId":"30000000-0000-0000-0000-000000000001","quantity":1}]}
```

Expected201: Hold ACTIVE, unitPrice`"200000"`, quantity1, subtotal`"200000"`, expiresAt03:10 UTC, serverNow03:00; Seat HELD; chưa Order/Payment/Ticket. Buyer B đua cùng seat409 và không có partial item. Sau POST orders `{holdId:<id vừa nhận>}` nhận subtotal/total200000, cùng expiresAt; lặp trả cùng orderId. Coupon fixed200000 trên subtotal500000 chỉ giảm150000; hai đơn maxUses1 chỉ một RESERVED. PaymentStart REDIRECT phải dùng URL thật server; Return query success không mutate; IPN verified mới phát hành. Thu xác nhận lúc03:10 trở đi: một Refund compensation/no Ticket, dù provider paidAt03:09.

Expected QR chỉ owner GET `/tickets/{ticketId}/qr` image/png; user khác404; list/detail JSON không ticketCode. Tải/chia sẻ ảnh QR cho người đi cùng không đổi chủ Order và không mở cả đơn. Các câu expected này là assertion tương lai, không phải log nghiệm thu.

## Bàn giao và tự kiểm tra kế hoạch

- [ ] M0: LIEM-01 interfaces/DTO/Model/Ticket transitions/worker SPI gửi cả nhóm; LIEM-02 schema/locking/FK fragments. Không đợi Auth/Event hoàn chỉnh, không trì hoãn Refund compensation contract của Thái đến M3.
- [ ] M2: LIEM-03…14 backend/SQL/UI04–07/14; LIEM-15 evidence tiền/kho/race/sandbox; Thái nhận methods Ticket và compensation facts, Khánh nhận MeServlet/JSP/mail hook, Đông nhận hold+coup service, Vương nhận admin coupon/catalog.
- [ ] M3: Thái handlers vào registry chung, compensation/customer refund/settlement cashflow ghép theo TEAM; không tạo RefundRequest/SettlementItem hoặc scheduler riêng. Liêm sửa Ticket/Payment contract qua review chủ caller khi cần.
- [ ] M4: LIEM-16 migration/benchmark/fresh-run/evidence/thuyết minh; link nguồn còn đúng, prototype placeholders còn nguyên, sample UUID/expected không bị ghi thành actual.

**Lệnh giao AI mẫu:** “Đọc spec, XML diagram, TEAM-CONTRACT và liem.md; triển khai LIEM-04 trong nhánh được giao sau khi kiểm LIEM-02/03 và model nền của Khánh/Đông. Chỉ sửa các file task sở hữu, dùng đúng chữ ký/DTO, SQL Server thật cho race/rollback; cập nhật docs/evidence/liem/LIEM-04.md với commit/expected/actual/exit code và blocker. Không tự tick task khác, push/merge hoặc báo PASS khi profile/DB chưa chạy.”

## Quy trình Git bắt buộc cho Liêm

Đọc toàn bộ [GIT-WORKFLOW](GIT-WORKFLOW.md) trước triển khai. Tính năng và sửa lỗi đều dùng feature theo task, mặc định từ develop đã cập nhật và kiểm, PR vào develop. Chỉ ngoại lệ sửa bản main đã bàn giao khi develop còn việc chưa nghiệm thu mới rẽ feature từ main, PR main rồi đồng bộ main → develop theo GIT-WORKFLOW §7. Develop là nhánh tích hợp; main giữ bản đã nghiệm thu qua PR develop → main và tag sau kiểm. Không dùng nhánh release riêng. Chỉ stage file thuộc nhiệm vụ. Hoàn thành mỗi đơn vị có kiểm chứng thì commit code/test/migration/tài liệu liên quan, ghi footer `Task-Id`, lệnh/kết quả và evidence. Task lớn có nhiều commit/PR con; commit nền không đồng nghĩa toàn task đã đạt. Không chờ hoàn thành cả module mới commit.

Bảng dưới là tên nhánh và subject khởi điểm đã gắn đúng chức năng. Khi chỉ sửa lỗi dùng `fix`, chỉ thêm test dùng `test`, chỉ tài liệu dùng `docs`; mô tả phải phản ánh nội dung thực. Subject dài được rút gọn rõ nghĩa theo khuyến nghị72 ký tự của nhóm, không đổi task ID. PR một task mặc định vào develop, ngoại lệ base main theo §7; reviewer theo người nhận đầu ra; tiền/quyền/transaction cần hai reviewer chuyên môn. Build/SQL/browser thiếu môi trường ghi BLOCKED, không đóng task từ commit hoặc mock.

| Task | Nhánh chức năng | Subject commit khi triển khai đầy đủ hành vi |
|---|---|---|
| LIEM-01 | `feature/liem/liem-01-sales-contracts` | `feat(ticketing): define sales models DTOs and worker contracts` |
| LIEM-02 | `feature/liem/liem-02-sales-schema` | `feat(db): create sales schema and consistency constraints` |
| LIEM-03 | `feature/liem/liem-03-release-hold` | `feat(ticketing): release holds inventory and coupon reservations` |
| LIEM-04 | `feature/liem/liem-04-create-hold` | `feat(ticketing): reserve up to eight tickets atomically` |
| LIEM-05 | `feature/liem/liem-05-order-from-hold` | `feat(order): create one order with immutable hold snapshots` |
| LIEM-06 | `feature/liem/liem-06-coupon-policy` | `feat(coupon): manage coupon rules and availability projections` |
| LIEM-07 | `feature/liem/liem-07-coupon-redemption` | `feat(coupon): reserve replace and release order coupons atomically` |
| LIEM-08 | `feature/liem/liem-08-begin-payment` | `feat(payment): reuse pending payment attempts and freeze amounts` |
| LIEM-09 | `feature/liem/liem-09-vnpay-callbacks` | `feat(payment): verify sandbox callbacks and render payment status` |
| LIEM-10 | `feature/liem/liem-10-payment-result` | `feat(payment): apply payments issue tickets and create compensation` |
| LIEM-11 | `feature/liem/liem-11-outbox-dispatcher` | `feat(jobs): dispatch versioned jobs with leases and deduplication` |
| LIEM-12 | `feature/liem/liem-12-payment-reconciliation` | `feat(payment): expire holds and reconcile unresolved payments` |
| LIEM-13 | `feature/liem/liem-13-ticket-queries` | `feat(fulfillment): expose owner-scoped tickets QR and order history` |
| LIEM-14 | `feature/liem/liem-14-checkout-pages` | `feat(order): connect checkout payment and coupon JSP pages` |
| LIEM-15 | `feature/liem/liem-15-sales-acceptance` | `test(payment): verify payment races replay rollback and sandbox flows` |
| LIEM-16 | `feature/liem/liem-16-sales-handoff` | `feat(db): document sales benchmarks migrations and handoff` |

Trước commit: kiểm ownership/diff → chạy checks đúng task → `git diff --check` → stage đường dẫn cụ thể → review staged diff → commit theo mẫu chung. Báo cáo cuối của thành viên/AI phải ghi nhánh, commit, task, tests/evidence và dependency chưa ghép. Push/merge/deploy và thay cấu hình GitHub theo quyền được giao; bộ kế hoạch này không tự thực hiện các bước đó.
