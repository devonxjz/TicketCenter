# Nhiệm vụ triển khai của Khánh — tài khoản và nền tảng chung

## Prompt khởi đầu — copy từ đây

Copy **nguyên nội dung trong khối bên dưới** vào AI có quyền đọc repository TicketsCenter. Prompt đã gắn đúng thành viên/file; không cần copy toàn bộ 76 task vào cửa sổ chat. Nếu AI không có quyền đọc repository, phải cung cấp các tài liệu được liệt kê trước khi triển khai.

```text
Tôi phụ trách phần của Khánh trong dự án TicketsCenter.
Hãy triển khai nhiệm vụ của tôi theo docs/tasks/khanh.md.

Trước khi viết mã:
1. Đọc toàn bộ spec.md và docs/classdiagram/diagram.md, gồm thuộc tính,
   phương thức và quan hệ trong XML diagram.
2. Đọc toàn bộ docs/tasks/TEAM-CONTRACT.md từ đầu đến cuối, không bỏ mục
   hoặc chỉ đọc phần cá nhân; đọc đầy đủ docs/tasks/CONVENTIONS.md,
   API-MAP.md, COVERAGE.md và GIT-WORKFLOW.md.
3. Đọc toàn bộ docs/tasks/khanh.md. Đọc các phần nhiệm vụ thành viên khác
   cung cấp đầu vào hoặc nhận đầu ra liên quan; kiểm mã nguồn, cấu hình,
   trạng thái Git và bằng chứng thực tế trong repository.
4. Trước thay đổi đầu tiên, báo ngắn gọn phiên bản nguồn đã đọc, phạm vi
   sở hữu, hợp đồng liên miền, nhiệm vụ sẽ làm và tình trạng phụ thuộc.
   Tiếp tục công việc đã được giao nếu đủ đầu vào, không dừng để hỏi lại
   xác nhận cho các bước triển khai thông thường.

Bắt đầu bằng nhiệm vụ đầu tiên chưa hoàn thành và đủ phụ thuộc.
Nếu tôi chỉ định mã nhiệm vụ cụ thể trong tin nhắn tiếp theo, ưu tiên mã đó.
Chỉ sửa file thuộc phạm vi Khánh; phần do người khác sở hữu phải phối hợp
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

Muốn giao một task cụ thể, thêm sau prompt: `Nhiệm vụ lần này: KHANH-NN.` Thay NN bằng mã thật trong file; không suy ra task hoàn thành từ lịch ngày, checkbox hoặc tên commit.

## Điều kiện bắt buộc trước khi triển khai

**Bắt buộc với cả thành viên và AI:** trước nhiệm vụ đầu tiên, phải đọc **toàn bộ [TEAM-CONTRACT.md](TEAM-CONTRACT.md), từ đầu đến cuối, không bỏ mục, không chỉ đọc phần của mình hoặc bản tóm tắt**. Đồng thời đọc đầy đủ [CONVENTIONS.md](CONVENTIONS.md), [API-MAP.md](API-MAP.md), [COVERAGE.md](COVERAGE.md) và [GIT-WORKFLOW.md](GIT-WORKFLOW.md), cùng spec/diagram được dẫn trong phần nguồn chuẩn.

- [ ] Đã đọc toàn bộ hợp đồng chung và tài liệu quy ước; hiểu ownership Model/Servlet/SQL, DTO/Service/API, quyền/principal, transaction/locking, outbox/SPI, migrations/fixture, Git và tiêu chí nghiệm thu.
- [ ] Ghi trong kế hoạch bắt đầu hoặc Draft PR: đường dẫn hợp đồng, commit SHA nguồn nếu có, task nhận, đầu vào/đầu ra liên miền và các ràng buộc áp dụng. Nếu hợp đồng còn chưa commit, ghi SHA nền và trạng thái working tree; không bịa phiên bản.
- [ ] Đối chiếu task với hợp đồng hiện hành và kiểm file/Service thuộc người nào. Nếu có mâu thuẫn hoặc thiếu hợp đồng bắt buộc, ghi rõ điểm thiếu và chủ cung cấp; chưa triển khai phần phụ thuộc đó.
- [ ] Khi tiếp tục phiên làm việc, kiểm hợp đồng có thay đổi từ phiên bản đã đọc không. Có thay đổi thì đọc lại toàn bộ hợp đồng mới và cập nhật caller/test bị ảnh hưởng trước khi tiếp tục phần liên quan.

Không bắt đầu viết mã cho nhiệm vụ khi chưa hoàn thành việc đọc và đối chiếu trên. Reviewer phải kiểm xác nhận này trước khi chấp nhận PR; một câu “đã đọc” không thay việc chứng minh đầu ra tuân hợp đồng.

Ngày lập: 06/10/2026. **Trạng thái: kế hoạch tương lai, chưa triển khai và chưa nghiệm thu.** Repository hiện có `pom.xml` tối thiểu, đặc tả, diagram và prototype; chưa có backend/JSP/schema SQL để coi các lệnh dưới đây là đã chạy. Khánh là người nhận chính; mỗi task ghi reviewer/người nhận đầu ra riêng. Tất cả checkbox phải giữ chưa tích cho đến khi có bằng chứng thực thi.

## Goal — Mục tiêu

Hoàn thành User và đủ hành vi của diagram; đăng ký, đăng nhập, OTP, reset, phiên, quyền hiện hành; UI-03/UI-24; nền Maven WAR, JPA/transaction, HTTP/JSON/lỗi/CSRF, layout/assets/test helpers và gửi email. Đầu ra phải ghép được với bốn module còn lại thành toàn dự án. Khánh không nhận scheduler/dispatcher/outbox core của Liêm, schema membership của Đông, hay DDL role/login của Vương.

## Architecture — Kiến trúc

Filter → Servlet → Service → Model/Repository → SQL Server; Servlet trả JSP/JSTL hoặc JSON DTO. Model không biết HTTP/EntityManager; Repository nhận EntityManager của use case. Một transaction giữ nguyên principal/connection; gọi email sau commit, không giữ khóa trong lúc chờ provider. Không thêm Spring, SPA hoặc DAO JDBC song song; không thêm lớp nghiệp vụ ngoài 15 lớp chuẩn.

## Tech Stack — Công nghệ

JDK 25; Maven WAR; Tomcat 11.0.25, Servlet 6.1/JSP 4.0, JSTL 3.0; Jakarta Persistence/Hibernate; SQL Server local/Azure SQL; Bootstrap/CSS/Fetch. KHANH-01 kiểm tra tương thích rồi khóa dependency/plugin/JSON/formatter/test tooling trong `docs/backend/decisions.md`, không đoán phiên bản. Java Money/Decimal của diagram đều dùng **BigDecimal**; Money không có phần lẻ, SQL `decimal(19,0)`, không tạo wrapper Money trùng ở các module. UUID và Instant dùng chung; JSON tiền là chuỗi số nguyên, thời gian UTC; giao diện/email tiếng Việt, VND, Asia/Ho_Chi_Minh.

## Spec — Nguồn phải đọc

- [spec.md](../../spec.md): §2–5, §6.1/6.2/6.4, §7–12, §14.2/14.3 C01/14.10/14.11.
- [diagram.md](../classdiagram/diagram.md): `body-class-User`, `methods-class-User` và các quan hệ User–Organization/Order/TicketHold. Đọc XML thuộc tính/phương thức, không chỉ ảnh/tên lớp.
- [TEAM-CONTRACT](TEAM-CONTRACT.md), [CONVENTIONS](CONVENTIONS.md), [API-MAP](API-MAP.md), [COVERAGE](COVERAGE.md) và [nhiệm vụ Đông](dong.md), [Liêm](liem.md), [Thái](thai.md), [Vương](vuong.md).
- Truy vết kế hoạch cũ: [D01](week-1/day-01-nen-tang-va-hop-dong.md), [D02](week-1/day-02-schema-va-rang-buoc.md), [D03](week-1/day-03-model-jpa-transaction.md), [D04](week-1/day-04-tai-khoan-session-phan-quyen.md), [D05](week-1/day-05-otp-email-khoi-phuc-mat-khau.md), [D06](week-1/day-06-to-chuc-thanh-vien-hoa-hong.md), [D12](week-2/day-12-outbox-worker-doi-soat-thanh-toan-email.md), [D19](week-3/day-19-bao-mat-db-va-benchmark.md), [D21](week-3/day-21-nghiem-thu-va-ban-giao.md).
- Bố cục tham khảo: [auth.html](../web-demo/prototype/auth.html), [profile.html](../web-demo/prototype/profile.html), [UI-03](../web-demo/prototype/frames/UI-03-dang-nhap-otp.html), [UI-24](../web-demo/prototype/frames/UI-24-ho-so-tai-khoan.html), [CSS](../web-demo/prototype/css/style.css). Không dùng dữ liệu giả/handler prototype làm nghiệp vụ chạy thật.

Khi tài liệu ngày cũ ghi backend-only hoặc tên migration Dxx, bộ nhiệm vụ thành viên và TEAM-CONTRACT chốt phạm vi **cả frontend/backend/database/test**, migration `0010_identity.sql` và M0–M4. Ngày lịch cũ không là deadline hay tiến độ. Spec/diagram vẫn quyết định nghiệp vụ.

## Global Constraints — Ràng buộc chung

- Chỉ Khánh định nghĩa User; cung cấp cả `assignRole/revokeRole` cho Đông. UserOrganizationRole, V10, C02/TR06 do Đông sở hữu. Không tạo OrganizationRequest/RefundRequest thành Model.
- Không tin actorId/userId/role/principal/now/emailVerified/authVersion từ browser. Quyền lấy trạng thái User và membership hiện tại từ DB; một lần login không đóng băng quyền. ADMIN không mặc nhiên bỏ mọi guard của use case.
- Username/email chuẩn hóa duy nhất **ở DB và Service**, không chỉ kiểm tra trước INSERT. C01 gồm hai UNIQUE và NOT NULL; race phải dựa vào constraint.
- User mutation dùng POST + CSRF, kể cả trước login và logout. Chỉ VNPAY IPN đã xác minh chữ ký được ngoại lệ; không miễn toàn `/payments/*`.
- Không lưu/log OTP rõ, password, reset token, cookie, raw QR, connection secret. JSP escape nội dung, không scriptlet/SQL/entity lazy load. Không đặt HTTP debug endpoint đọc OTP.
- Mọi thay đổi nhạy cảm có test SQL Server thật khi cần; mock chỉ chứng minh adapter. Không dùng tài khoản DB toàn quyền để làm test xanh. Không tự reset database demo hoặc tạo resource/publish ngoài phạm vi được giao.
- Giữ bố cục prototype và thành phần chung, có loading/empty/success/error, label/focus/keyboard/responsive. Không vẽ/sinh ảnh hay sơ đồ còn thiếu; giữ placeholder để tác giả cập nhật.
- File có sẵn trong Create phải chuyển thành Modify sau kiểm kê, giữ thay đổi của người khác. Không đổi chữ ký chung âm thầm; cập nhật hợp đồng, chủ kiểu và mọi caller/test trong cùng thay đổi phối hợp.

## Review Focus — Điểm kiểm tra chính

C01/race; OTP 6 chữ số và biên 5 phút/60 giây/5 sai/purpose; counter sai vẫn commit; reset one-use + tăng authVersion nguyên tử; session fixation; quyền hiện tại/chéo tổ chức; connection không mang actor cũ; rollback sau SP; không giữ transaction qua I/O; mail thất bại không sửa Order/Refund; không scheduler thứ hai; HTML/JSON đúng Accept; không dùng stub/mock/placeholder để nhận nghiệm thu tích hợp.

## Hợp đồng xuất bản từ M0

Các đường dẫn dưới đây tính từ gốc repository, là đầu ra **phải tạo**. Dùng cùng tên ở mọi caller. Hợp đồng kỹ thuật bổ sung không thay phương thức Model/SQL trong spec.

| Thành phần Khánh sở hữu | Chữ ký/fields bắt buộc |
|---|---|
| `config/ClockProvider.java` | `Instant now()`; implementation dùng UTC, test clock thay qua cấu hình test, HTTP không nhận now |
| `dto/common/ActorContext.java` | Record `ActorContext(UUID actorId, PlatformRole platformRole, int authVersion, boolean emailVerified, ActorType type)`; `ActorType=USER/SYSTEM`; tạo từ auth DB hoặc danh tính hệ thống cấu hình, không deserialize request thành actor |
| `transaction/PrincipalKind.java` | `BUYER, MANAGER, CHECK_IN, PLATFORM_ADMIN, AUTH_TECH, WORKER_TECH`; Vương ánh xạ tới R01–R04/TECH, worker không giả buyer session |
| `transaction/TransactionRunner.java` | `<T> T required(PrincipalKind principal, ActorContext actor, Function<EntityManager,T> work)`; principal là quyết định server đã kiểm quyền; callback đồng bộ, EntityManager không thoát ra ngoài |
| `service/identity/AuthService.java` | `ActorContext requireCurrentUser(HttpServletRequest request)`; kiểm User ACTIVE/authVersion DB trên mỗi request cần login |
| `service/identity/AuthorizationService.java` | `void requireAdmin(ActorContext actor)`; `void requireOrganizationRole(ActorContext actor, UUID organizationId, Set<OrganizationRole> allowed)`; trong transaction đọc membership bằng EntityManager hiện tại, Service/SP ghi recheck dưới khóa để chống quyền bị thu hồi giữa kiểm tra và ghi |
| `dto/common/PageRequest.java`, `Page.java` | `PageRequest(int page,int pageSize,String sort)`; `Page<T>(List<T> items,int page,int pageSize,long total)`; 1-based/default20/max100, sort allowlist và ID tie-breaker |
| `dto/identity/UserDto.java` | `id,userName,fullName,email,phone?,status,emailVerified,platformRole`; không hash/authVersion/OTP/organization entity graph |
| `dto/identity/ProfileDto.java` | `user:UserDto,memberships:Page<MembershipDto>`; MembershipDto do Đông sở hữu |
| `dto/common/ApiError.java` | `code,message,correlationId`; server có thể thêm `fields` đã lọc cho validation; HTTP error envelope `error`, success `data` |
| `controller/common/HttpResponses.java` | `void data(HttpServletResponse,int,Object)`; `void error(HttpServletResponse,int,ApiError)`; `boolean wantsJson(HttpServletRequest)`; default HTML ở route có page, explicit Accept application/json trả JSON |
| `controller/common/RequestParsers.java` | `UUID uuid(String value,String field)`; `PageRequest page(HttpServletRequest,Set<String> allowedSorts)`; `BigDecimal money(String value,String field)`; lỗi400 trước query, không parse qua double |
| `integration/mail/MailSender.java` | `MailReceipt send(MailMessage message)`; `MailMessage(to,subject,textBody,htmlBody,idempotencyKey)` và `MailReceipt(providerReference,acceptedAt)`; timeout/thất bại dùng lỗi đã phân loại, không cập nhật nghiệp vụ |

Tiền nhận chuỗi canonical `0` hoặc số nguyên không âm không có dấu/space/exponent/phần lẻ, tối đa 19 chữ số; phép tính dùng BigDecimal exact. `Decimal` tỷ lệ/phí dùng BigDecimal với scale chủ miền khai báo theo DDL, không tự làm tròn bằng helper tiền; Liêm giữ công thức giảm/phân bổ, Vương giữ commission. Helpers chung chỉ validation/serialization.

### Đầu vào bắt buộc của module khác

| Chủ cung cấp | Consumes của Khánh; phạm vi DTO |
|---|---|
| Đông — DONG-01 và task membership | `MembershipAuthorizationRepository.findCurrent(EntityManager em, UUID userId, UUID organizationId): Optional<MembershipAccess>`; `MembershipAccess(organizationId,role,active,organizationStatus)`; query bind và quyền current. `Page<MembershipDto> MembershipService.listForUser(ActorContext actor, PageRequest page)`; MembershipDto có `organizationId,organizationName,userId,userName,role,active,organizationStatus`. `Page<OrganizationDto> OrganizationService.listOwnRequests(ActorContext actor, PageRequest page)`; sử dụng DTO chủ Đông, ít nhất `id,name,status,rejectionReason?` |
| Liêm — LIEM-01 và task query/worker | `Page<OrderDto> OrderQueryService.listOwn(ActorContext actor, OrderFilter filter, PageRequest page)`; `Page<TicketDto> TicketQueryService.listOwn(ActorContext actor, PageRequest page)`; `HoldDto HoldService.getCurrent(ActorContext actor)`; OrderFilter `status?,from?,to?`; projection tối thiểu Order `id,orderCode,eventId,status,totalAmount,createdAt,paidAt?`; Ticket `id,orderId,eventId,status,paidAmount`; Hold `id,eventId,status,expiresAt,serverNow,items`; mỗi item `zoneId,seatId?,quantity,unitPrice`. Không copy DTO hoặc tính tiền lại trong MeServlet |
| Thái — THAI-01 và task query | `Page<RefundDto> RefundService.listOwn(ActorContext actor, PageRequest page)`; projection tối thiểu `id,orderId,purpose,status,amount,currentAttemptId?,rejectionReason?`; dùng kiểu đầy đủ của Thái |
| Liêm — LIEM-01 và task outbox | `JobHandler.handle(OutboxMessageDto message): JobResult`; JobResult `status=SUCCEEDED/RETRY/FAILED,nextAttemptAt?`; OutboxMessageDto tối thiểu `id,type,payloadVersion,payloadJson,idempotencyKey,attempts,leaseOwner,leaseUntil`; `UUID OutboxPublisher.enqueue(EntityManager em,String type,int payloadVersion,String idempotencyKey,String payloadJson)` ghi trên connection của mutation, không tự commit |
| Vương — VUONG-01 và task bảo mật/CI | Manifest migrations/FK, fixture registry, mapping PrincipalKind–DB role/TECH, budget pool/grants; Vương sở hữu `database/README.md`, `database/seeds/test-fixtures.sql`, `support/TestFixtures.java`, profile browser-it, runbook/CI bản ghép |

Projection tối thiểu trên là yêu cầu dùng DTO chung từ chủ miền, không cho Khánh định nghĩa bản rút gọn cùng tên. M0 phải đồng bộ fields/nullable/enum và JSP đích với các file thành viên trước caller. Chưa có Service thật: contract test/adapter fake giúp compile, task tích hợp để chưa đạt.

### Danh mục route Khánh sở hữu

Tất cả route dưới context path của WAR, JSON mutation `Content-Type: application/json`; POST yêu cầu `X-CSRF-Token`. Servlet mapping duy nhất: AuthServlet `/auth/*`, MeServlet `/me/*`, HealthServlet `/health/*`. Không đăng ký route chủ khác.

| Route | Request → response/status/quyền |
|---|---|
| `GET /health/live`, `GET /health/ready` | Public, JSON `data.status=UP`; live200 không DB; ready200 hoặc503 kiểm kết nối nhẹ, không chi tiết cấu hình |
| `GET /auth?view=login\|register\|otp\|forgot\|reset&returnTo=...` | HTML UI-03; view allowlist; reset cần grant, returnTo chỉ path nội bộ hợp lệ; không mở redirect bên ngoài |
| `GET /auth/csrf` | Public, tạo phiên pre-auth;200 `data.csrfToken` runtime; no-store |
| `POST /auth/register` | `{userName,fullName,email,password}` →202 `data.message` chung; tạo CUSTOMER ACTIVE chưa verified nếu dữ liệu mới; duplicate chuẩn hóa trả cùng thông điệp hạn chế dò; dữ liệu sai400/rate429; không đăng nhập hoặc cấp ADMIN tự động |
| `POST /auth/login` | `{email,password}` →200 `data.user:UserDto`; local demo cho phép identifier `admin` chỉ tới User seed userName=admin bằng cờ demo rõ; public email thật. Credential sai/disabled cùng401, rate429 |
| `POST /auth/logout` | Session + CSRF →200 `data.loggedOut=true`; invalidate phiên hiện tại, gọi lặp với phiên pre-auth+CSRF vẫn200; không đăng xuất thiết bị khác |
| `POST /auth/otp/send` | `{purpose:"VERIFY_EMAIL"}` →202 `data.message,resendAfterSeconds:60`; user login lấy từ session, ACTIVE. `RESET_PASSWORD` đi forgot, không cho body userId đổi mục tiêu.59s429; email đã verified409; purpose sai400 |
| `POST /auth/password/forgot` | `{email}` →202 cùng `data.message` cho có/không account/disabled; phát RESET_PASSWORD khi hợp lệ, thiết lập flow session opaque, không trả userId/challengeId có thể dò. Global limit429 |
| `POST /auth/otp/verify` | `{purpose,code}`; VERIFY_EMAIL lấy user từ session; RESET_PASSWORD lấy flow pre-auth.200 `data.emailVerified=true` hoặc `data.resetAllowed=true`; grant reset chỉ session, không token trong JSON. Code sai/expired/used400, lock429; không sai purpose đổi account |
| `POST /auth/password/reset` | `{password}` + reset grant session →200 `data.message`; hết/used grant400; cập nhật hash/tăng authVersion/consume cùng transaction; sau đó phải login lại |
| `GET /me/profile`, `GET /me` (alias) | ACTIVE+session; HTML UI-24 hoặc200 JSON ProfileDto; membership page/pageSize dùng query hợp lệ;401 phiên không hợp lệ |
| `GET /me/memberships`, `/me/organization-requests` | Login;200 Page DTO từ Đông; scope actorId, không nhận userId query; mặc định HTML forward trang chủ miền, explicit JSON trả JSON |
| `GET /me/orders`, `/me/tickets`, `/me/refund-requests` | Login;200 Page DTO từ Liêm/Thái; owner từng query, JSON/HTML cùng scope/filter; dữ liệu rỗng200 Page items=[] |
| `GET /me/hold` | Login;200 HoldDto hoặc `data:null` khi không có ACTIVE hold theo contract Liêm; JSON route phục vụ UI-04, không tự render checkout từ MeServlet |

### Mốc và lệnh kiểm chứng

| Mốc | Task Khánh và gate |
|---|---|
| M0 | KHANH-01–04, phần nền06–07,11; WAR/health, DTO/Model/SQL C01, transaction/principal/helpers/UI layout có thể ghép; fake/stub không chứng minh nghiệp vụ |
| M1 | KHANH-05–10,12–13; register→OTP→login/profile và membership thật; buyer verified cho giữ vé, admin seed đúng |
| M2 | KHANH-13 tích hợp orders/tickets/hold,14 email vé; phối hợp mua→QR→check-in |
| M3 | KHANH-13 refund query,14 email hoàn/từ chối; đọc trạng thái thực sau worker/restart |
| M4 | KHANH-15; build mới/tái deploy, security/recovery/browser/SQL/evidence và thuyết minh phần Khánh bàn giao Vương |

KHANH-01 tạo Surefire unit `*Test`, Failsafe `sqlserver-it` `*IT` nhưng exclude `**/browser/**`; browser-it chỉ include `**/browser/**/*IT.java`, tránh SQL profile kéo browser hoặc browser profile chạy lại toàn SQL IT; Vương tạo browser-it và môi trường browser theo công cụ nhóm khóa. Lệnh bên dưới là **lệnh mục tiêu tương lai**, chưa chạy ở repo hiện tại. `mvn -B verify` mặc định unit/build; phải chạy thêm SQL/browser profiles khi nghiệm thu. Profile integration thiếu môi trường phải fail rõ, không âm thầm skip.

SQL local dùng `sqlcmd -S "$env:TC_SQL_HOST" -d "$env:TC_TEST_DATABASE" -E -b -i <file>` với DB test riêng/runbook. Nếu SQL auth, `-U` và `SQLCMDPASSWORD` nạp an toàn; không `-P`, không in secret. `-G` chỉ khi runbook Entra thực sự có. Test THROW khi assertion sai; không coi PRINT là pass. Base time `2026-10-06T03:00:00Z`, buyer A/B dùng registry TEAM; không tự gán UUID fixture mới. Test receipt/cookie/token là test runtime đã che, không là credentials giao người dùng.

## KHANH-01 — Build WAR và health/test tooling

**Mục tiêu/mốc:** M0, build WAR chạy Tomcat và một bộ tooling dùng chung. **Nguồn:** spec §2/3/11/12; TEAM §2/3.3/8; D01-T02/D03-T04. **Phụ thuộc:** không task code trước; phối hợp VUONG-01 khóa manifest/runtime/CI, không đợi nghiệp vụ module khác. **Reviewer/nhận:** Vương và cả đội.

**Create:** `src/main/java/vn/ticketscenter/controller/HealthServlet.java`; `src/main/java/vn/ticketscenter/config/ApplicationConfig.java`; `src/main/webapp/WEB-INF/web.xml`; `.env.example`; `docs/backend/decisions.md`; `docs/backend/build.md`.
**Modify:** `pom.xml`; `.gitignore`; `application.properties` (nếu dùng, chỉ giá trị không bí mật).
**Test:** `src/test/java/vn/ticketscenter/config/ApplicationConfigTest.java`; `src/test/java/vn/ticketscenter/acceptance/KhanhHealthIT.java`.

**Consumes/Produces:** khóa dependency/plugins thực sau spike; ApplicationConfig đọc JDK/Tomcat/DB/mail/base URL/timeouts từ môi trường có validation; HealthServlet dùng `HttpResponses.data` của02. Route live/ready theo bảng; không lộ version credential/cấu hình nội bộ. Readiness cần03, thiếu DB trả503.

- [ ] Kiểm kê `pom.xml`/source hiện có, ghi các phiên bản thực và dependency Servlet provided/JSTL implementation/Hibernate/JDBC driver/JSON/pool/test; khóa Maven plugins/release25/UTF-8/finalName`ticketscenter`/packagingWAR. Kiểm tra đúng namespace jakarta và không gói container vào WAR.
- [ ] Cấu hình Surefire/Failsafe tách `*Test/*IT`; sqlserver-it bắt buộc tham số test DB, fail khi thiếu. Thống nhất formatter/check trong verify, hướng dẫn thêm test mẫu. Phối hợp Vương cấu hình browser-it cùng pom, không ghi đè profile của nhau.
- [ ] Tạo live độc lập DB; ready probe nhẹ timeout hữu hạn, `Cache-Control:no-store`. Đặt schema validate, không hibernate create/update trên DB chung.
- [ ] Deploy/redeploy thật lên Tomcat, gọi cả health với DB có/mất; kiểm WAR không chứa `.env`, test-only routes hoặc Servlet API implementation.
- [ ] **Ca:** live200 `{"data":{"status":"UP"}}`; mất DB ready503/live200; thiếu config public startup fail thông báo không in giá trị; redeploy hai lần vẫn health200, không leak thread/listener.
- [ ] **Lệnh:** `mvn -B verify` → exit0 và `target/ticketscenter.war`; `mvn -B -Psqlserver-it -Dit.test=KhanhHealthIT verify` → báo cáo test có ca thực, không skipped. Profile thiếu DB phải exit khác0.
- [ ] **Bàn giao/evidence:** `docs/evidence/khanh/KHANH-01.md` ghi commit/tool versions/command/expected-actual/exit/deploy/cấu hình đã lọc; cả đội dùng được cùng build. Không tick chỉ vì Maven compile.

## KHANH-02 — Kiểu dữ liệu, HTTP và lỗi chung

**Mục tiêu/mốc:** M0, mọi module parse/serialize cùng hợp đồng. **Nguồn:** spec §5.2/6.7/8/9.4/14.2; diagram Money/Decimal/User; D03-T04. **Phụ thuộc:** KHANH-01; DONG-01/LIEM-01/THAI-01/VUONG-01 review DTO/enum tiền. **Reviewer/nhận:** cả đội, Vương kiểm tài chính.

**Create:** `src/main/java/vn/ticketscenter/config/ClockProvider.java`; `src/main/java/vn/ticketscenter/config/SystemClockProvider.java`; `src/main/java/vn/ticketscenter/controller/common/HttpResponses.java`; `src/main/java/vn/ticketscenter/controller/common/RequestParsers.java`; `src/main/java/vn/ticketscenter/dto/common/Page.java`; `src/main/java/vn/ticketscenter/dto/common/PageRequest.java`; `src/main/java/vn/ticketscenter/dto/common/ApiError.java`; `src/main/java/vn/ticketscenter/exception/BusinessException.java`; `src/main/java/vn/ticketscenter/filter/ErrorHandlingFilter.java`; `src/main/java/vn/ticketscenter/filter/CorrelationIdFilter.java`; `src/test/java/vn/ticketscenter/support/MutableClock.java`; `docs/backend/http.md`.
**Modify:** `docs/backend/decisions.md`; `src/main/java/vn/ticketscenter/controller/HealthServlet.java`.
**Test:** `src/test/java/vn/ticketscenter/controller/HttpContractTest.java`; `src/test/java/vn/ticketscenter/acceptance/KhanhHttpIT.java`.

**Consumes/Produces:** signatures/Page/ApiError/ClockProvider ở bảng nền; BusinessException mang `code,httpStatus` và message đã lọc. UUID/Instant/BigDecimal converters thuộc JSON config chung; tỷ lệ BigDecimal giữ precision theo chủ kiểu. Query sort chỉ allowlist theo module. Không tạo route nghiệp vụ; test servlet gây lỗi nằm test WAR riêng.

- [ ] Viết ca malformed UUID/page/sort/money trước; khóa field lỗi và mã ổn định cho400/401/403/404/409/413/429/503; lỗi hệ thống500 generic/correlationId, không SQL trace.
- [ ] Serialize envelope/nullable/Page không entity graph; money chuỗi nguyên kể cả0, Instant UTC; parser chặn negative/fraction/exponent/overflow và body không đúng JSON.
- [ ] `wantsJson` xét Accept rõ ràng ở GET có page; default HTML, không lấy Content-Type của GET thay Accept. IPN dùng response riêng, filter không bọc thành envelope.
- [ ] Correlation ID server sinh hoặc validate chuỗi bounded trước log/header; không raw CRLF/injection; Clock injectable chỉ backend/test.
- [ ] **Ca:** Page trống `{"data":{"items":[],"page":1,"pageSize":20,"total":0}}`; money`"200000"` giữ đúng; `"0"` hợp lệ; `"1.5"`, `"1e6"`,20digits/page0/pageSize101/sortSQL →400, không query; exception DB→500 đã lọc; HTML request không nhận JSON nhầm.
- [ ] **Lệnh:** `mvn -B -Dtest=HttpContractTest test`; `mvn -B -Psqlserver-it -Dit.test=KhanhHttpIT verify` → exit0, assertions format/error/filter hoạt động qua HTTP.
- [ ] **Bàn giao/evidence:** `docs/evidence/khanh/KHANH-02.md` + `docs/backend/http.md`; mọi caller compile cùng helper; không tự viết serializer/money wrapper riêng.

## KHANH-03 — JPA và transaction giữ principal/actor

**Mục tiêu/mốc:** M0, mọi Service dùng một connection và rollback đúng kể cả SP. **Nguồn:** spec §8/14.2/14.10; D03-T02/T03. **Phụ thuộc:** KHANH-01/02/04 schema identity; VUONG-02 fixture và VUONG-03 mapping/grants TECH; DONG-01/LIEM-01/THAI-01 models nền khi validate toàn bộ. **Reviewer/nhận:** Vương; các chủ SP.

**Create:** `src/main/java/vn/ticketscenter/config/PersistenceListener.java`; `src/main/java/vn/ticketscenter/config/PersistenceFactory.java`; `src/main/java/vn/ticketscenter/transaction/TransactionRunner.java`; `src/main/java/vn/ticketscenter/transaction/PrincipalKind.java`; `src/main/java/vn/ticketscenter/transaction/TransactionContext.java`; `src/main/java/vn/ticketscenter/transaction/ActorSessionContext.java`; `src/main/resources/META-INF/persistence.xml`; `src/test/java/vn/ticketscenter/support/SqlServerTestSupport.java`; `src/test/java/vn/ticketscenter/support/ConcurrentTestSupport.java`; `database/tests/khanh/KHANH-03.sql`; `docs/backend/persistence.md`; `docs/backend/locking.md`.
**Modify:** `src/main/java/vn/ticketscenter/config/ApplicationConfig.java`; `pom.xml` khi khóa pool/JPA đã kiểm.
**Test:** `src/test/java/vn/ticketscenter/acceptance/KhanhTransactionIT.java`.

**Consumes/Produces:** `TransactionRunner.required(...)` đúng bảng; ActorContext ở06; Vương cấp enum–principal config và grants. TransactionContext chỉ internal, cung cấp EntityManager của transaction hiện tại cho AuthorizationService; nested required cùng principal/actor tham gia hiện tại, không commit riêng; đổi principal/actor nested bị từ chối. Test SP riêng chỉ DB test, không cộng vào SP01–17 rubric.

- [ ] Factory lifecycle startup/shutdown, EntityManager mỗi use case, không share thread; tổng pool khởi đầu tối đa khoảng5 connections chung, không5 cho mỗi principal. Timeout/retry hữu hạn và đo với Vương.
- [ ] Begin→callback→flush/commit→close; exception rollback/close; nested cùng context join, rollback-only khi lỗi; kiểm connection/EntityManager thực không đổi giữa Repository/SP.
- [ ] Set/overwrite SESSION_CONTEXT actor khi mượn, clear khi trả cả success/error; cleanup thất bại discard connection. Không đặt readonly session context gây actor bị giữ qua pool.
- [ ] SP standalone quản transaction của nó; trong outer JPA dùng savepoint/ownership và không commit caller. Test gây lỗi sau UPDATE SP, kiểm @@TRANCOUNT/XACT_STATE và clear/refresh sau native/SP.
- [ ] **Ca:** update profile rồi test SP rồi ném lỗi→DB trước/sau giống nhau; standalone SP test commit được; nested đổi principal fail/rollback; hai thread BUYER A/B không nhận actor nhau; auth→worker reuse connection không actor cũ; lỗi callback không cạn pool; DB down timeout hữu hạn.
- [ ] **Lệnh:** `mvn -B -Psqlserver-it -Dit.test=KhanhTransactionIT verify`; SQL script `database/tests/khanh/KHANH-03.sql` qua sqlcmd theo runbook → exit0; phải có hai connection thật/barrier và lỗi sau mutation.
- [ ] **Bàn giao/evidence:** `docs/evidence/khanh/KHANH-03.md` ghi connection/transaction/principal observations đã lọc, rollback DB/khóa release, budget pool; Liêm/Thái/Đông/Vương gọi cùng runner, không mở transaction khác trong callback.

## KHANH-04 — User đủ diagram và schema identity/C01

**Mục tiêu/mốc:** M0, Model User dùng được bởi tất cả caller, schema identity tạo được trên DB mới. **Nguồn:** spec §4/5/7/8.2/14.3 C01; XML User; D02-T01/T02/D03-T01. **Phụ thuộc:** KHANH-01/02; DONG-01 cấp Organization, DONG-02 cấp mapping membership; Khánh tạo OrganizationRole; VUONG-02 nhận FK/catalog. Không cần03 để viết unit/DDL, cần03 để round-trip. **Reviewer/nhận:** Đông và Vương.

**Create:** `src/main/java/vn/ticketscenter/model/identity/User.java`; `src/main/java/vn/ticketscenter/model/identity/UserStatus.java`; `src/main/java/vn/ticketscenter/model/identity/PlatformRole.java`; `src/main/java/vn/ticketscenter/model/identity/OrganizationRole.java` (MANAGER/CHECK_IN_STAFF); `src/main/java/vn/ticketscenter/persistence/identity/OtpRecord.java`; `src/main/java/vn/ticketscenter/persistence/identity/ResetGrantRecord.java`; `src/main/java/vn/ticketscenter/model/identity/OtpPurpose.java`; `src/main/java/vn/ticketscenter/repository/identity/UserRepository.java`; `src/main/java/vn/ticketscenter/dto/identity/UserDto.java`; `database/migrations/0010_identity.sql`; `database/tests/khanh/KHANH-04.sql`; `docs/backend/identity-data.md`.
**Modify:** `src/main/resources/META-INF/persistence.xml` sau03; catalog/manifest chỉ gửi nội dung để Vương ghép `database/README.md`, không ghi đè file Vương.
**Test:** `src/test/java/vn/ticketscenter/model/identity/UserTest.java`; `src/test/java/vn/ticketscenter/acceptance/KhanhIdentityMappingIT.java`.

**Produces Model:** `void updateProfile(String fullName,String phone)`; `void verifyEmail()`; `void assignRole(Organization org,OrganizationRole role)`; `void revokeRole(Organization org)`; `boolean isEligibleBuyer()`. Phone nullable; Boolean diagram dùng boolean Java; emailVerified lưu một cột SQL bit NOT NULL DEFAULT0, verifyEmail chuyển true idempotent; thời điểm audit nếu cần là persistence, không cột trạng thái cạnh tranh thứ hai. `organizationRoles:Map<Organization,OrganizationRole>` chứa active hiện hành qua mapping do Đông cung cấp; assign thay role cùng tổ chức, revoke chỉ bỏ hiệu lực; không disable User. Guard manager cuối/transaction/C02/TR06 của Đông, không Model query DB. Repository nhận EntityManager; `Optional<User> findById(EntityManager,UUID)`; `Optional<User> findByNormalizedEmail(EntityManager,String)`; `Optional<User> findByNormalizedUserName(EntityManager,String)`; `void persist(EntityManager,User)`.

**DDL:** `[User]` UUID PK, email/normalizedEmail/userName/normalizedUserName NOT NULL, passwordHash/authVersion/fullName/status/platformRole/emailVerified bit/phone?/createdAt/optimistic version. `UQ_User_NormalizedEmail`, `UQ_User_NormalizedUserName` dùng normalized column collation xác định; status ACTIVE/DISABLED, platformRole CUSTOMER/ADMIN. OTP record id/userId/purpose/codeHmac/createdAt/expiresAt/lastSentAt/failedAttempts/consumedAt?/invalidatedAt?; unique filtered một record hiện hành/user-purpose (không dùng GETUTCDATE trong filter), purpose/check/count/FK User. ResetGrant id/userId/sessionBindingHash/createdAt/expiresAt/consumedAt?; lưu hash binding, không token rõ. Những khóa OTP/grant là kỹ thuật, không tự gọi C02+.

- [ ] Liệt kê mọi thuộc tính/phương thức/quan hệ User theo diagram, mapping audit/hash/version ẩn theo spec; không cascade REMOVE lịch sử, không setter public cho ADMIN/hash/verified từ UI.
- [ ] Khóa normalization ở05 và độ dài cột tương ứng; unique cả email/username, NOT NULL; ghi data dictionary/FK exports để module Order/Hold/Organization tham chiếu User.
- [ ] Cài đầy đủ phương thức; kiểm profile/name/phone, verify idempotent, eligibility = ACTIVE && verified, role map độc lập từng tổ chức. Tạo OTP/reset kỹ thuật cùng0010, không FK tới Organization chưa tạo.
- [ ] Review cùng Đông mapping map role chỉ một nguồn; nếu cần bảng nền chưa có, unit compile với model nền DONG-01, integration giữ blocked chứ không tạo Organization giả trong production.
- [ ] **Ca:** assign manager A/checkin B giữ2 role; revoke A cònB/UserACTIVE; verify lặp giữ trạng thái đã xác minh; User chưaverified/DISABLED không eligible; normalized email/username duplicate bị unique chặn; UUID/Instant/enum round-trip, OTP không plain code; lỗi sau insert rollback không bản ghi dở.
- [ ] **Lệnh:** `mvn -B -Dtest=UserTest test`; `mvn -B -Psqlserver-it -Dit.test=KhanhIdentityMappingIT verify`; `database/tests/khanh/KHANH-04.sql` → exit0/THROW khi sai; fresh DB Hibernate validate, không tự tạo schema.
- [ ] **Bàn giao/evidence:** `docs/evidence/khanh/KHANH-04.md`; Đông nhận đủ User methods, Vương nhận0010/khóa/FK/grant needs; chưa có evidence không tick C01.

## KHANH-05 — Đăng ký chuẩn hóa nguyên tử và hash mật khẩu

**Mục tiêu/mốc:** M1, tạo CUSTOMER chưaverified, duplicate/race không sinh tài khoản trùng. **Nguồn:** spec §4/6.1/14.3 C01/UI-03; User diagram; D04-T01. **Phụ thuộc:** KHANH-02/03/04/07; KHANH-08 gửi OTP sau commit; VUONG-03 auth TECH grants. **Reviewer/nhận:** Đông/Liêm dùng verified eligibility.

**Create:** `src/main/java/vn/ticketscenter/service/identity/AccountService.java`; `src/main/java/vn/ticketscenter/service/identity/IdentityNormalizer.java`; `src/main/java/vn/ticketscenter/service/identity/PasswordHasher.java`; `src/main/java/vn/ticketscenter/dto/identity/RegisterCommand.java`; `src/main/java/vn/ticketscenter/dto/identity/MessageDto.java`; `src/main/java/vn/ticketscenter/controller/identity/AuthServlet.java`; `database/tests/khanh/KHANH-05.sql`.
**Modify:** `src/main/java/vn/ticketscenter/repository/identity/UserRepository.java`; `docs/backend/identity-data.md`; `docs/backend/http.md`.
**Test:** `src/test/java/vn/ticketscenter/identity/RegistrationTest.java`; `src/test/java/vn/ticketscenter/acceptance/KhanhRegistrationIT.java`.

**Consumes/Produces:** `MessageDto AccountService.register(RegisterCommand command)`; command userName/fullName/email/password; MessageDto message. `String IdentityNormalizer.userName(String)`/`email(String)`; `String PasswordHasher.hash(char[] password)`/`boolean verify(char[],String encodedHash)`. Register route theo bảng; không nhận platformRole/status/verified/userId.

- [ ] Username trim + lowercase Locale.ROOT, ASCII `[a-z0-9_]{3,32}`; email trim + lowercase Locale.ROOT, tối đa254, validation email không rewrite dấu chấm/plus theo provider. Giữ cùng normalization cho lookup/add-member/seed. FullName trim/NFC 1–120; phone nullable tối đa20 với chữ số/+ và cách định dạng được document. Password không trim/casefold,10–128 ký tự cho đăng ký/reset thường, không phản chiếu vào lỗi.
- [ ] Chốt PasswordHasher trong decisions: dùng implementation đã kiểm, salt ngẫu nhiên riêng, encoded version/algorithm/iterations/salt/hash để nâng cấp; không hash nhanh SHA thuần. Kiểm tra chi phí/timeout login với tooling đã khóa, không viết cryptography tự tạo. Seed demo admin ngoại lệ chỉ task10, không nới đăng ký công khai.
- [ ] Validate trước transaction, insert USER CUSTOMER ACTIVE chưaverified; C01 quyết định duplicate, map duplicate code có/không existing cùng202 message, không trả userId/hash; unique violation rollback cả insert phụ.
- [ ] Kết nối gửi VERIFY_EMAIL theo08 saucommit và flow login/OTP; registration không tạo verified/session authenticated tự động. Thất bại email giữ User chưaverified, user login/resend được; không trả thành công gửi thư thật nếu provider chưa gọi.
- [ ] **Ca:** `{"userName":" Buyer_A ","fullName":"Người mua A","email":" Buyer.A@example.test ","password":"<test-runtime>"}` →202 generic, normalized `buyer_a`/`buyer.a@example.test`, emailVerifiedfalse/CUSTOMER. Hai connection cùng normalized userName khác email hoặc cùng email khác username chỉ1User;202 messages giống nhau. Unicode username/blank name/invalid email/short password400; body`role:ADMIN`400; DB không password rõ; lỗi sauinsert rollback hết.
- [ ] **Lệnh:** `mvn -B -Dtest=RegistrationTest test`; `mvn -B -Psqlserver-it -Dit.test=KhanhRegistrationIT verify`; SQL `database/tests/khanh/KHANH-05.sql` → exit0, số User constraint/race đúng; kiểm hash không ghi raw/hash vào evidence.
- [ ] **Bàn giao/evidence:** `docs/evidence/khanh/KHANH-05.md`; rule normalization/grants gửi Đông/Vương, Liêm kiểm user chưaverified giữ vé bị403; có unique thật, không chỉ pre-check.

## KHANH-06 — Session, ActorContext và quyền hiện hành

**Mục tiêu/mốc:** nền M0, hoàn thiện M1; actor thật và principal đúng use case. **Nguồn:** spec §4/6.1/8/14.10/UI-24; User.organizationRoles; D04-T02/T03. **Phụ thuộc:** KHANH-02/03/04/05; DONG-01 và query membership hiện hành; VUONG-03 mapping quyền. **Reviewer/nhận:** Đông/Vương và toàn bộ Service.

**Create:** `src/main/java/vn/ticketscenter/dto/common/ActorContext.java`; `src/main/java/vn/ticketscenter/dto/common/ActorType.java`; `src/main/java/vn/ticketscenter/service/identity/AuthService.java`; `src/main/java/vn/ticketscenter/service/identity/SessionService.java`; `src/main/java/vn/ticketscenter/service/identity/AuthorizationService.java`; `src/main/java/vn/ticketscenter/dto/identity/LoginCommand.java`; `src/main/java/vn/ticketscenter/filter/AuthenticationFilter.java`; `docs/backend/auth.md`.
**Modify:** `src/main/java/vn/ticketscenter/controller/identity/AuthServlet.java`; `src/main/java/vn/ticketscenter/repository/identity/UserRepository.java`; `src/main/webapp/WEB-INF/web.xml`.
**Test:** `src/test/java/vn/ticketscenter/identity/SessionTest.java`; `src/test/java/vn/ticketscenter/acceptance/KhanhSessionAuthorizationIT.java`.

**Consumes/Produces:** Actor/Auth/Authorization đúng bảng nền; `UserDto SessionService.login(HttpServletRequest request, LoginCommand command)`; `void SessionService.logout(HttpServletRequest request)`; LoginCommand có email/password. Dùng MembershipAuthorizationRepository/MembershipAccess của Đông và EntityManager hiện tại. Auth dùng AUTH_TECH; mua BUYER; quản lý MANAGER; check-in MANAGER/CHECK_IN theo role; quản trị PLATFORM_ADMIN. Guard trong transaction không mở transaction khác; ngoài transaction dùng read boundary ngắn. Routes login/logout/me theo danh mục, không HTTP cấp quyền.

- [ ] Login tra email chuẩn hóa, cùng phản hồi credential sai/disabled, throttling từ07; rotate session ID/CSRF. Chỉ mang dữ liệu allowlist như returnTo nội bộ; lưu userId/authVersion, không role map làm nguồn quyền.
- [ ] requireCurrentUser đọc User ACTIVE/version DB; đổi status/version tác động request tiếp theo401/no-store. Cho nhiều thiết bị, login mới không revoke phiên khác.
- [ ] requireAdmin kiểm platform role hiện hành; SYSTEM không mặc nhiên ADMIN. requireOrganizationRole kiểm membership active, Organization APPROVED và role allowed; query ngoài owner scope404/thiếu role403. Service/SP ghi recheck dưới khóa cùng transaction để chống thu hồi quyền giữa kiểm tra và ghi.
- [ ] Cookie HttpOnly/Secure trên HTTPS/SameSite phù hợp VNPAY Return/context path; trusted proxy explicit và inactivity timeout ghi trong auth.md. Logout invalidate một phiên; lặp với phiên pre-auth/CSRF hợp lệ vẫn200.
- [ ] **Ca:** login200/UserDto không secret; session ID đổi; hai cookie jars login được, logout một chỉ một401; reset09 cả hai phiên cũ401; disable→401. Manager A/check-in B không quản lý B; revoke→403 lần sau; forged actor/role/principal400; thiếu grants không fallback ADMIN.
- [ ] **Lệnh:** `mvn -B -Dtest=SessionTest test`; `mvn -B -Psqlserver-it -Dit.test=KhanhSessionAuthorizationIT verify` → exit0. HTTP và DB principal thật cùng Vương; race revoke–mutation cùng chủ use case, không chỉ mock guard.
- [ ] **Bàn giao/evidence:** `docs/evidence/khanh/KHANH-06.md`; mọi caller dùng actor/guard chung; Đông review map stale, Vương review role/TECH/pool actor.

## KHANH-07 — CSRF, validation và bảo vệ request

**Mục tiêu/mốc:** M0 nền, M1 áp dụng; request sai bị chặn trước mutation. **Nguồn:** spec §6.1/9.4/11/12; D04-T03; TEAM §5. **Phụ thuộc:** KHANH-01/02 và nền06; LIEM-01 chốt ngoại lệ IPN. **Reviewer/nhận:** Liêm/Vương.

**Create:** `src/main/java/vn/ticketscenter/filter/CsrfFilter.java`; `src/main/java/vn/ticketscenter/filter/RequestValidationFilter.java`; `src/main/java/vn/ticketscenter/filter/SecurityHeadersFilter.java`; `src/main/java/vn/ticketscenter/service/identity/AuthRateLimiter.java`; `src/main/java/vn/ticketscenter/controller/common/ReturnToValidator.java`; `src/test/java/vn/ticketscenter/support/HttpTestClient.java`.
**Modify:** `src/main/java/vn/ticketscenter/controller/identity/AuthServlet.java`; `src/main/java/vn/ticketscenter/config/ApplicationConfig.java`; `src/main/webapp/WEB-INF/web.xml`; `docs/backend/http.md`; `docs/backend/auth.md`.
**Test:** `src/test/java/vn/ticketscenter/security/RequestSecurityTest.java`; `src/test/java/vn/ticketscenter/acceptance/KhanhHttpSecurityIT.java`.

**Produces:** csrfToken session-bound từ GET /auth/csrf; header X-CSRF-Token cho POST; lỗi chung. `String ReturnToValidator.validate(String candidate)` trả path nội bộ allowlist hoặc `/events`, chặn URL external/schema-relative/backslash/double-decode/control chars. Limiter khóa ngưỡng account/source/TTL/bound; không thay counter OTP DB bằng memory. Không thêm route ngoài danh mục.

- [ ] Token SecureRandom pre-auth, check mọi user POST trước controller, rotate login/reset, so sánh constant-time; logout có CSRF. Ngoại lệ chỉ exact IPN path/method Liêm xác minh chữ ký, không miễn cả directory.
- [ ] Auth/profile JSON max64KiB và depth/string bounded; upload multipart có giới hạn riêng Đông, không áp filter JSON cho mọi route. Unsupported Content-Type415/body lớn413/malformed400.
- [ ] No-store auth/me; headers/cookie/trusted proxy đúng Tomcat. CSP nếu áp dụng khớp assets và camera/Fetch Thái; không log auth body/cookie/token.
- [ ] Ngưỡng bổ sung cấu hình/test: login/account5 lần sai/15phút, source20/15phút; OTP send account5/giờ, source20/giờ ngoài cooldown60s; verify/source30/5phút. Chỉ tin forwarded IP từ proxy trusted. Memory limiter cho một app; DB lock/counter vẫn quyết định OTP. Forgot giữ generic202 ở account limit chống dò; source limit429 giống nhau cho mọi email.
- [ ] **Ca:** thiếu/sai/chéo CSRF403/no DB change; token trước login bị từ chối sau rotate; GET không mutate. Body65537bytes413/JSON sai400/Content-Type sai415; returnTo`//evil.example`→default. IPN thiếu CSRF nhưng signature sai không mutation; boundary rate dùng clock test.
- [ ] **Lệnh:** `mvn -B -Dtest=RequestSecurityTest test`; `mvn -B -Psqlserver-it -Dit.test=KhanhHttpSecurityIT verify` → exit0; assert DB không đổi và response/log không secret.
- [ ] **Bàn giao/evidence:** `docs/evidence/khanh/KHANH-07.md`; cả đội nhận exact exemption/limits/helpers; public path không tự cấp actor SYSTEM.

## KHANH-08 — OTP gửi ngay và MailSender

**Mục tiêu/mốc:** M1, OTP6 chữ số/5phút/resend60s, gửi sau commit và không queue mã rõ. **Nguồn:** spec §6.1/8.3/10 Email/11; D05-T01/T03. **Phụ thuộc:** KHANH-02/03/04/06/07; provider/sender credentials được cấp; VUONG-03 auth TECH grants. Email nghiệp vụ thuộc14. **Reviewer/nhận:** Liêm/Thái.

**Create:** `src/main/java/vn/ticketscenter/service/identity/OtpService.java`; `src/main/java/vn/ticketscenter/repository/identity/OtpRepository.java`; `src/main/java/vn/ticketscenter/integration/mail/MailSender.java`; `src/main/java/vn/ticketscenter/integration/mail/ConfiguredMailSender.java`; `src/main/java/vn/ticketscenter/dto/mail/MailMessage.java`; `src/main/java/vn/ticketscenter/dto/mail/MailReceipt.java`; `src/main/java/vn/ticketscenter/dto/identity/OtpSendCommand.java`; `src/main/java/vn/ticketscenter/dto/identity/OtpSendResult.java`; `src/test/java/vn/ticketscenter/support/CapturingMailSender.java`; `database/tests/khanh/KHANH-08.sql`; `docs/backend/mail.md`.
**Modify:** `src/main/java/vn/ticketscenter/controller/identity/AuthServlet.java`; `src/main/java/vn/ticketscenter/config/ApplicationConfig.java`; `.env.example`.
**Test:** `src/test/java/vn/ticketscenter/identity/OtpSendTest.java`; `src/test/java/vn/ticketscenter/acceptance/KhanhOtpSendIT.java`.

**Produces:** `OtpSendResult OtpService.sendVerification(ActorContext actor)`; `MessageDto OtpService.beginPasswordReset(HttpServletRequest request,String email)`; OtpSendResult message/resendAfterSeconds; MailSender theo bảng nền. Repository `Optional<OtpRecord> lockCurrent(EntityManager,UUID,OtpPurpose)` và `void persist(EntityManager,OtpRecord)`; không OtpServlet riêng. VERIFY_EMAIL lấy actor session; RESET_PASSWORD từ forgot flow. Lock User→OTP theo thứ tự chung. OtpSendCommand chỉ purpose; routes send/forgot theo danh mục.

- [ ] Generate SecureRandom, giữ0 đầu thành6 ASCII digits; HMAC có domain purpose/userId/challengeId và OTP_HMAC_SECRET môi trường. DB chỉ HMAC; code chỉ memory ngắn tới sender, không log/outbox;09 compare constant-time.
- [ ] Lock User→OTP hiện hành; Clock/UTC, expiry now+5phút, ít nhất60s giữa issue. Invalidate cũ+insert mới nguyên tử, unique current bảo vệ race. Cooldown vẫn áp dụng khi provider lỗi, không send loop mù.
- [ ] Commit challenge trước I/O; recheck expiry/invalidated trước gửi khi chậm, bỏ expired/replaced code. Timeout bounded, không retry vượt TTL/scheduler. Mail lỗi giữ User/challenge chưa verified; resend trả503, forgot giữ generic tránh dò account; log lọc.
- [ ] Forgot202 giống nhau cho có/không/disabled; flow opaque bind session, không userId/challenge secret response. Validate sender/APP_BASE_URL; thiếu provider BLOCKED external, không fake receipt.
- [ ] **Ca:** random test000001 gửi đủ6 digits;03:00 issue/03:00:59 resend429/03:01 resend202; cũ invalidated/mới5phút. Hai thread một current; purpose độc lập. Provider quá TTL không gửi;503 không verified/User trùng; DB/outbox/log không OTP rõ.
- [ ] **Lệnh:** `mvn -B -Dtest=OtpSendTest test`; `mvn -B -Psqlserver-it -Dit.test=KhanhOtpSendIT verify`; SQL `database/tests/khanh/KHANH-08.sql` → exit0. Gửi mail thật tới hộp thư được phép, receipt/timestamp lọc; adapter không đủ provider PASS.
- [ ] **Bàn giao/evidence:** `docs/evidence/khanh/KHANH-08.md`; sender cho14/Liêm/Thái; PASS logic/BLOCKED external tách rõ, không credentials giả.

## KHANH-09 — Verify OTP và reset grant một lần

**Mục tiêu/mốc:** M1, đúng purpose, race một success, reset vô hiệu phiên cũ. **Nguồn:** spec §6.1/8.3/12; User.verifyEmail; D05-T02/T04. **Phụ thuộc:** KHANH-03/04/06/07/08. **Reviewer/nhận:** Vương/Liêm.

**Create:** `src/main/java/vn/ticketscenter/service/identity/PasswordResetService.java`; `src/main/java/vn/ticketscenter/repository/identity/ResetGrantRepository.java`; `src/main/java/vn/ticketscenter/dto/identity/OtpVerifyCommand.java`; `src/main/java/vn/ticketscenter/dto/identity/OtpVerifyResult.java`; `src/main/java/vn/ticketscenter/dto/identity/PasswordResetCommand.java`; `database/tests/khanh/KHANH-09.sql`.
**Modify:** `src/main/java/vn/ticketscenter/service/identity/OtpService.java`; `src/main/java/vn/ticketscenter/repository/identity/OtpRepository.java`; `src/main/java/vn/ticketscenter/controller/identity/AuthServlet.java`; `src/main/java/vn/ticketscenter/service/identity/SessionService.java`; `docs/backend/auth.md`.
**Test:** `src/test/java/vn/ticketscenter/identity/OtpVerificationTest.java`; `src/test/java/vn/ticketscenter/acceptance/KhanhPasswordResetIT.java`.

**Produces:** `OtpVerifyResult OtpService.verify(HttpServletRequest request,OtpVerifyCommand command)`; purpose/code→emailVerified?/resetAllowed?; `MessageDto PasswordResetService.reset(HttpServletRequest request,PasswordResetCommand command)` với password. Grant5phút từ verify, binding random hash DB, grantId chỉ server session, không JSON. VERIFY_EMAIL không cấp reset. Consume OTP+verifyEmail hoặc consume OTP+insert grant cùng transaction; reset consume grant+hash mới+authVersion+1 cùng transaction. Routes verify/reset theo bảng, không browser chọn account.

- [ ] Lock User→OTP/grant; kiểm code6ASCII digits/purpose/account/session binding/expiry/consumed/invalidated/fails; now>=expiry reject, đúng code sau5fails vẫn reject.
- [ ] Sai code tăng counter, lần5 invalidated. Callback transaction trả failed result sau commit rồi mapping HTTP; không throw làm rollback counter. Malformed/rate drop khác code đúng định dạng nhưng sai, document rõ.
- [ ] Grant đúng flow/session, rotate pre-auth session/CSRF khi bind grant; một grant một reset; validate password05/accountACTIVE, không userId/email/role body. Commit grant/hash/version trước invalidate reset session.
- [ ] Lost response sau commit: replay grant reject/new password login được; lỗi sau hash update rollback hash/version/grant. Không log password/OTP/token.
- [ ] **Ca:**03:04:59 valid/03:05:00 expired;4wrong counter4/5wrong invalidated/6correct reject. Purpose swap/used400; verifyEMAIL200/eligible; resetOTP200/resetAllowed không token. Hai verify/hai reset chỉ một success; old401/new200, hai phiên cũ401; inject lỗi sau mutation rollback toàn bộ.
- [ ] **Lệnh:** `mvn -B -Dtest=OtpVerificationTest test`; `mvn -B -Psqlserver-it -Dit.test=KhanhPasswordResetIT verify`; SQL `database/tests/khanh/KHANH-09.sql` → exit0, assert counter/version/grant bằng hai connections/boundary clock.
- [ ] **Bàn giao/evidence:** `docs/evidence/khanh/KHANH-09.md`; attempts/version/session trước-sau/hashChanged boolean không raw hash/HMAC; buyer verified bàn giao M1/M2.

## KHANH-10 — Seed admin idempotent và cấu hình auth public

**Mục tiêu/mốc:** M1, đúng một admin seed, không credentials giả/ghi đè password khi restart. **Nguồn:** spec §4 admin/11/12; D04-T04. **Phụ thuộc:** KHANH-03/04/05/06/07; VUONG-02 fixture/VUONG-03 provisioning TECH. **Reviewer/nhận:** Vương.

**Create:** `src/main/java/vn/ticketscenter/config/AdminSeeder.java`; `database/tests/khanh/KHANH-10.sql`; `docs/backend/admin-seed.md`.
**Modify:** `src/main/java/vn/ticketscenter/config/PersistenceListener.java`; `src/main/java/vn/ticketscenter/config/ApplicationConfig.java`; `src/main/java/vn/ticketscenter/service/identity/SessionService.java`; `.env.example`; `docs/backend/auth.md`.
**Test:** `src/test/java/vn/ticketscenter/config/AdminSeedTest.java`; `src/test/java/vn/ticketscenter/acceptance/KhanhAdminSeedIT.java`.

**Produces:** `void AdminSeeder.seed()` dùng provisioning TECH hẹp Vương review; User seed userName=admin/email hợp lệ/verified/ADMIN. Local explicit TC_DEMO_ADMIN_ENABLED cho admin/admin theo spec; public email/password env khác demo. CUSTOMER đăng ký không tự đổi ADMIN; không credential SQL/mail giả, không HTTP seed/create-admin route.

- [ ] Khóa LOCAL_DEMO/PUBLIC/Test; demo tắt public; missing secret/passwordpublic=admin fail startup có code không giá trị. `.env.example` chỉ biến/placeholder.
- [ ] Seed lookup normalized username/email+unique trong transaction, chỉ insert khi chưa có; verified/ADMIN ngay seed. Existing seed không ghi đè hash/password/profile; collision User CUSTOMER fail không escalation.
- [ ] Hai startup đồng thời một admin; local identifier admin chỉ tới seed khi cờ bật; public email thật/UI không hardcode credentials.
- [ ] **Ca:** seed hai lần count1/hash không đổi; race hai seed một; non-admin usernameadmin fail; public thiếu/default demo fail; localexplicit admin/admin200 và userNameadmin; publicaliasadmin401/emailenv200.
- [ ] **Lệnh:** `mvn -B -Dtest=AdminSeedTest test`; `mvn -B -Psqlserver-it -Dit.test=KhanhAdminSeedIT verify`; SQL `database/tests/khanh/KHANH-10.sql` → exit0; startup/restart/publicnegative evidence không password.
- [ ] **Bàn giao/evidence:** `docs/evidence/khanh/KHANH-10.md`; Vương nhận runbook/demo/online; không biến seedtest thành credentials phát hành.

## KHANH-11 — Layout, CSS và Fetch client dùng chung

**Mục tiêu/mốc:** M0 cung cấp khung/trạng thái chung; M1 các page ghép đúng prototype. **Nguồn:** spec §9.1/9.3/9.4, prototype CSS/auth/profile và TEAM §5.2. **Phụ thuộc:** KHANH-01/02/07, chủ từng miền khóa asset/layout contract. **Reviewer/nhận:** cả đội.

**Create:** `src/main/webapp/WEB-INF/views/layout/header.jspf`, `footer.jspf`, `notifications.jspf` cùng directory; `src/main/webapp/WEB-INF/views/common/error.jsp`; `src/main/webapp/assets/css/app.css`; `src/main/webapp/assets/js/api-client.js`, `ui-state.js`; `src/main/java/vn/ticketscenter/controller/common/ViewSupport.java`; `docs/frontend/shared-components.md`.
**Modify:** pom.xml phần JSTL/Bootstrap đã khóa; không copy dữ liệu/handler giả từ prototype vào app.
**Test:** `src/test/java/vn/ticketscenter/acceptance/KhanhLayoutIT.java`, `src/test/java/vn/ticketscenter/browser/KhanhSharedLayoutBrowserIT.java`.

**Produces:** `ViewSupport.forward(HttpServletRequest,HttpServletResponse,String viewPath,Object viewModel):void` chỉ allowlist WEB-INF; attributes `pageTitle,activeNav,currentUser,memberships,viewModel,notice`. `apiClient.get(path)`/`post(path,body)` trả Promise data; POST lấy CSRF runtime cùng session; lỗi chứa code/message/correlationId/status. uiState cung cấp loading/empty/success/error và focus/aria-live. Vương cấu hình Maven browser-it/Java browser support cùng tooling đã kiểm.

- [ ] Giữ font/màu/khoảng cách/header/footer/cards/forms prototype trong CSS chung. Nav lấy quyền hiện tại; chọn org chỉ đổi ngữ cảnh. Link/assets đúng context path WAR.
- [ ] JSP JSTL escape user text, không scriptlet/SQL/entity. Include/layout trong WEB-INF, không sửa 24 page frame hoặc vẽ ảnh thiếu.
- [ ] Fetch xử lý envelope/session/CSRF và 401→auth returnTo nội bộ,403→thông báo,409→refresh. Lỗi mạng mutation trả điều khiển cho caller tra trạng thái, không tự POST lại.
- [ ] Kiểm 320/375/768/1440, label/tab/focus/keyboard, empty/error/pagination. Bootstrap/CSP theo quyết định KHANH-01.
- [ ] Test tên chứa `<script>` hiển thị text, token rotate, timeout không resubmit, managerA/checkinB nav đúng và refresh khi revoke; mobile không tràn ngoài container bảng.

**Lệnh:** `mvn -B -Psqlserver-it -Dit.test=KhanhLayoutIT verify`; `mvn -B -Pbrowser-it -Dit.test=KhanhSharedLayoutBrowserIT verify` sau profile Vương. **Evidence:** `docs/evidence/khanh/KHANH-11.md`, viewport/keyboard/state thực; không coi assert HTML text là chứng minh visual.

## KHANH-12 — UI03 auth/OTP/forgot/reset

**Mục tiêu/mốc:** M1 năm khung auth và điều hướng dùng dữ liệu thật. **Nguồn:** UI03/§6.1/§9 và prototype. **Phụ thuộc:** KHANH-05–11; Đông membership cho đích theo role. **Reviewer/nhận:** Đông/Liêm.

**Create:** `src/main/webapp/WEB-INF/views/identity/auth.jsp`; `src/main/webapp/assets/js/auth.js`; `src/main/java/vn/ticketscenter/dto/identity/AuthViewModel.java`.
**Modify:** AuthServlet/app.css đã tạo.
**Test:** `src/test/java/vn/ticketscenter/acceptance/KhanhAuthPageIT.java`, `src/test/java/vn/ticketscenter/browser/KhanhAuthBrowserIT.java`.

**Consumes/Produces:** AuthViewModel(view,returnTo,resetAllowed,resendAfterSeconds,notice?); GET auth view allowlist, mutation JSON theo bảng. Register userName/fullName/email/password; confirmPassword kiểm client, không gửi field server chưa khai báo. OTP chuỗi sáu digits/purpose; reset grant chỉ server session, không query resetAllowed.

- [ ] Giữ card/tab/form gốc; trường userName theo spec; label/liên kết quay lại rõ. VERIFY_EMAIL/RESET_PASSWORD giữ purpose độc lập.
- [ ] OTP giữ số0 đầu, paste/backspace/tab/aria-label, không parseInt. Timer60s từ dữ liệu server; backend chốt expiry/cooldown; reload không tự gửi mã mới.
- [ ] Loading chống doubleclick, field message/focus/aria-live; returnTo nội bộ hoặc đích theo quyền hiện tại. User chưa verified vào giữ vé phải qua OTP, không bỏ guard Liêm.
- [ ] Forgot202 generic; reset hết hạn/used quay forgot, thành công về login. Không lưu password/OTP/localStorage; local demo hint chỉ khi cờ demo bật.
- [ ] Browser flow register→login→send/verify→safe destination,59/60s resend,5 sai,5min expired; forgot/reset/login mật khẩu mới; direct reset GET không grant không cho submit; mobile/keyboard lỗi đầy đủ.

**Lệnh:** AuthPageIT và `mvn -B -Pbrowser-it -Dit.test=KhanhAuthBrowserIT verify`; CapturingMailSender chỉ trong test, evidence mail provider thật KHANH-08 riêng. **Đạt khi:** `docs/evidence/khanh/KHANH-12.md` có năm view và DB/user state thực, caller verified/returnTo đúng.

## KHANH-13 — MeServlet, UI24 và query xuyên miền

**Mục tiêu/mốc:** M1 profile/membership; M2 orders/tickets/hold; M3 refunds. **Nguồn:** UI24/04/06/07/08/09, TEAM và API-MAP. **Phụ thuộc:** KHANH-02/03/06/07/11/12; Đông membership/request query; Liêm order/ticket/hold query; Thái refund query. **Reviewer/nhận:** Đông/Liêm/Thái.

**Create:** `src/main/java/vn/ticketscenter/controller/identity/MeServlet.java`, `src/main/java/vn/ticketscenter/service/identity/ProfileService.java`, `src/main/java/vn/ticketscenter/dto/identity/ProfileDto.java`; `src/main/webapp/WEB-INF/views/identity/profile.jsp`, `src/main/webapp/assets/js/profile.js`.
**Modify:** UserRepository/header/http contract.
**Test:** `src/test/java/vn/ticketscenter/identity/ProfileTest.java`, `src/test/java/vn/ticketscenter/acceptance/KhanhMeRoutesIT.java`, `src/test/java/vn/ticketscenter/browser/KhanhProfileBrowserIT.java`.

**Produces:** `UserDto ProfileService.get(ActorContext actor)`; ProfileDto(user,memberships) đúng bảng. UI24 hiển thị hồ sơ và trạng thái, không thêm POST/me/profile hoặc form chỉnh sửa ngoài spec/API. User.updateProfile đã cung cấp đầy đủ và kiểm unit/persistence tại KHANH-04. Không tạo MeRepository truy cập trực tiếp nghiệp vụ miền khác.

JSP forward: /me/profile và /me/memberships → identity/profile.jsp; /me/orders → order/my-orders.jsp (Liêm); /me/tickets → fulfillment/tickets.jsp (Liêm); /me/refund-requests → fulfillment/my-refunds.jsp (Thái); /me/organization-requests → identity/organization-requests.jsp (Đông). /me/hold chỉ JSON HoldDto/null; UI04 là GET/orders/{id} của Liêm, checkout khi PENDING_PAYMENT/detail khi đã chốt.

- [ ] Dispatch allowlist/path/method, query/OrderFilter đúng Liêm; unknown404/method405; GET page HTML/JSON cùng scope, Vary:Accept/no-store. DTO hoàn tất trước EntityManager close.
- [ ] Subroute gọi đúng Service/DTO chủ miền; owner từ actor/DB, không userId query hoặc đọc mọi hàng rồi lọc Java. Không trả empty giả khi module chưa triển khai.
- [ ] Profile verified/membership/status/nav giữ frame gốc; manager→UI10, check-in→UI15, admin→UI18; inactive không link vào khu vực trái quyền.
- [ ] Membership refresh từ DB khi mở/chọn org/sau403. User chưa verified vẫn xem profile/gửi hồ sơ org theo spec; chỉ guard verified đúng use case.
- [ ] Test buyerB không đơn/vé/refund buyerA, malformed page/status400, Page rỗng200, holdnull200, refund0đ không ghi giả transfer, revoked request sau bị chặn/menu refresh; HTML/JSON không trộn và DTO không secrets.

**Lệnh:** ProfileTest, KhanhMeRoutesIT; `mvn -B -Pbrowser-it -Dit.test=KhanhProfileBrowserIT verify`. **Evidence:** `docs/evidence/khanh/KHANH-13.md` tách M1/M2/M3, dependency thực và từng JSP owner. Adapter fake chỉ chứng minh adapter, không nghiệm thu scope SQL.

## KHANH-14 — EmailJob vé/kết quả hoàn

**Mục tiêu/mốc:** M2 email vé, M3 email từ chối/hoàn thành qua dispatcher Liêm. **Nguồn:** spec §6.8/6.9/8.4 và TEAM. **Phụ thuộc:** KHANH-02/03/08; Liêm outbox/publication; Thái refund producer/query; VUONG-03 quyền kỹ thuật. **Reviewer/nhận:** Liêm/Thái.

**Create:** `src/main/java/vn/ticketscenter/job/EmailJob.java`; `src/main/java/vn/ticketscenter/dto/mail/MailPayloadV1.java`, `MailTemplate.java` cùng directory; `src/main/java/vn/ticketscenter/service/mail/MailContentService.java`; `src/main/java/vn/ticketscenter/repository/mail/MailContentRepository.java`; `src/main/resources/mail/tickets-issued.html`, `refund-rejected.html`, `refund-completed.html`; `database/tests/khanh/KHANH-14.sql`.
**Modify:** ConfiguredMailSender/mail contract; registry bootstrap do Liêm nhận chính.
**Test:** `src/test/java/vn/ticketscenter/job/EmailJobTest.java`, `src/test/java/vn/ticketscenter/acceptance/KhanhEmailJobIT.java`.

**Produces:** EmailJob implements JobHandler.handle(OutboxMessageDto):JobResult. EMAIL/1 payload schemaVersion/template/recipientUserId/orderId?/refundId?/occurredAt; keys ticket-issued:{orderId},refund-rejected:{refundId},refund-completed:{refundId}. Tickets cần orderId; refund cần refundId và validated order/recipient DB; không email/rawQR/OTP/password/amount nguồn thứ hai.

`MailContentService.prepare(MailPayloadV1):MailMessage` đọc projection owner/Order/Refund trong WORKER_TECH transaction ngắn, đóng trước MailSender.send. TICKETS_ISSUED chỉ sau PAID/phát hành; refund chỉ REJECTED/COMPLETED, zeroRefund ghi đúng nội dung. Producer Liêm/Thái enqueue cùng nghiệp vụ, Khánh không enqueue lại trong handler.

- [ ] Validate version/template/owner/recipient; permanent invalid →FAILED và mã lọc; không retry vô hạn. Register EMAIL/1 duy nhất, không scheduler mới.
- [ ] Render tiếng Việt/VND/HCM/escape text; APP_BASE_URL link owner-auth orders/tickets, không QR/link fullOrder public. OTP dùng KHANH-08.
- [ ] Prepare DB → send ngoài TX → JobResult: accepted SUCCEEDED, transient RETRY/nextAttemptAt bounded, permanent FAILED. Liêm quản lease/ack, handler không sửa Order/Refund.
- [ ] Gửi mail là at-least-once; dùng idempotencyKey khi provider hỗ trợ, không hứa exactly-once khi SMTP không có dedup. Crash gửi thành công trước ack không được lặp nghiệp vụ tiền/kho.
- [ ] Test duplicate/redelivery/lease loss/send timeout/invalid version/crossowner/HTML escape/0đ; email lỗi không rollback PAID/COMPLETED. Mail provider thật và mock evidence tách rõ.

**Lệnh:** EmailJobTest/KhanhEmailJobIT và SQL KHANH-14. **Evidence:** `docs/evidence/khanh/KHANH-14.md`, payload đã lọc/receipt/quyền/caller thực; không đóng M3 khi refund producer còn fake.

## KHANH-15 — Nghiệm thu ghép và bàn giao

**Mục tiêu/mốc:** M4 auth/layout/mail/Me routes có bằng chứng tích hợp/recovery và hồ sơ miền. **Nguồn:** spec §12/14, TEAM; **phụ thuộc:** KHANH-01–14 và module thật Đông/Liêm/Thái/Vương. **Reviewer/nhận:** Vương và từng chủ Service được gọi.

**Create:** `src/test/java/vn/ticketscenter/acceptance/KhanhIdentityAcceptanceIT.java`, `KhanhMailRecoveryIT.java` cùng directory; `src/test/java/vn/ticketscenter/browser/KhanhIdentityJourneyBrowserIT.java`; `database/tests/khanh/KHANH-15.sql`; `docs/backend/khanh-handover.md`; `docs/baocao/sections/khanh-identity-platform.md`.
**Modify:** build/auth/persistence/mail docs, evidence từng task; gửi catalog/coverage/CI fragment để Vương review, không ghi đè SQL miền khác.

- [ ] Fresh clone/test DB → manifest/grants/seeds → verify → WAR health → CSRF/register/login/OTP/me/logout. Fixture direct SQL chỉ test; E2E dùng API/SP thật.
- [ ] UI03→Event Đông→Hold/Order/pay Liêm→Ticket/QR→check-in Thái; refund/0đ/mail/query current role. Evidence VNPAY/check-in/refund do chủ miền cung cấp, Khánh chứng minh adapter/auth/layout/mail.
- [ ] Kiểm hai thiết bị/reset/disabled/stale cookie/owner-org ID/forged principal/CSRF/body limits/OTP boundary/race. SQL auth principal không sysadmin/db_owner, logs/DTO không secrets.
- [ ] Stop/restart mail sau claim hoặc provider accepted trước ack, lease resume theo Liêm; mail không sửa tiền/kho, OTP hết hạn không gửi. Ghi at-least-once/unknown delivery đúng khả năng provider.
- [ ] DB down ready503/liveUP, timeout hữu hạn/pool recover/actor reuse không lẫn; redeploy không scheduler thứ hai.
- [ ] Unit/SQL/browser profiles chạy thật và lưu test counts/skips/expected/actual. Thiếu môi trường/provider ghi BLOCKED riêng; không PASS gộp.
- [ ] Thuyết minh User/Auth/OTP/C01/session/principals/mail/UI03/24, ERD/FD/API/evidence gửi Vương ghép hồ sơ; giữ ảnh thiếu cho tác giả.

**Lệnh:** `mvn -B verify`, `mvn -B -Psqlserver-it verify`, `mvn -B -Pbrowser-it verify`, SQL KHANH-15. **Đạt khi:** `docs/evidence/khanh/KHANH-15.md` có commit/env/fixture/commands/expected-actual/DB/log lọc/dependency; reviewer dựng lại từ handover được.

## Cách giao phần Khánh cho AI hoặc người mới

Đọc nguồn chuẩn và TEAM-CONTRACT, kiểm kê thực trạng rồi chỉ nhận task Khánh được giao, không triển khai tự động cả năm file. Có thể dùng `superpowers:executing-plans` nếu môi trường có skill; **không cần cài skill để đọc file Markdown trên GitHub hoặc để hiểu nhiệm vụ**. Tạo nhánh `feature/khanh/khanh-xx-chuc-nang` hoặc nhánh nhóm đã chỉ định; mỗi task thực hiện test hành vi có ý nghĩa → triển khai nhỏ → chạy lệnh → ghi evidence → xin review của người nhận đầu ra. Configuration/glue dùng smoke đúng phạm vi, không viết test rỗng để đủ số.

Nếu dependency thiếu, ghi task ID/chủ cung cấp/đầu vào thiếu và phần độc lập tiếp tục được. Không thay contract bằng stub200/emptylist để đánh dấu xong. Không tự thêm dependency/provider/resource/role/route ngoài quyết định nhóm; routine implementation trong phạm vi đã giao tiếp tục làm, không chờ hỏi lại mỗi bước.

## Checklist bàn giao cuối phần Khánh

- [ ] 15 task KHANH-01…KHANH-15 có reviewer/evidence/commit; C01, năm phương thức User, Auth/Me/Health routes, UI03/24 và handler EMAIL có testcase thực tương ứng.
- [ ] ActorContext/PrincipalKind/TransactionRunner/ClockProvider/Page/HTTPhelpers và current-membership repo compile cùng tất cả caller;Service/SP ghi recheck quyền ở đúng transaction.
- [ ] Liêm/Thái dùng MailPayloadV1/keys/SPI đã chốt; không OTP/raw QR/email address/secret trongoutbox; rollback nghiệp vụ rollback outbox; app chỉ mộtworker scheduler củaLiêm.
- [ ] Profile browser-it Vương, sqlserver-it/unit Khánh dựng thật; không suy lệnh trong kế hoạch là đã chạy; SQL Server/provider/browser vàonline evidence tách rõ.
- [ ] Link tương đối/file scopes/contracts khớp TEAM; không tạo ảnh thiếu/Model thừa/credentials giả; khôngđụng SQL/grants/migrations đã áp dụng của miền khác.
- [ ] Vương nhận  C01/schema/grantneeds/FK/User/maprolecontracts/fixturebuilders/phầnthuyếtminh/UIevidence; không coi hồ sơ/benchmarkSQLcủa cả đội là Khánh đã làm.

## Quy trình Git bắt buộc cho Khánh

Đọc toàn bộ [GIT-WORKFLOW](GIT-WORKFLOW.md) trước triển khai. Tính năng và sửa lỗi đều dùng feature theo task, mặc định từ develop đã cập nhật và kiểm, PR vào develop. Chỉ ngoại lệ sửa bản main đã bàn giao khi develop còn việc chưa nghiệm thu mới rẽ feature từ main, PR main rồi đồng bộ main → develop theo GIT-WORKFLOW §7. Develop là nhánh tích hợp; main giữ bản đã nghiệm thu qua PR develop → main và tag sau kiểm. Không dùng nhánh release riêng. Chỉ stage file thuộc nhiệm vụ. Hoàn thành mỗi đơn vị có kiểm chứng thì commit code/test/migration/tài liệu liên quan, ghi footer `Task-Id`, lệnh/kết quả và evidence. Task lớn có nhiều commit/PR con; commit nền không đồng nghĩa toàn task đã đạt. Không chờ hoàn thành cả module mới commit.

Bảng dưới là tên nhánh và subject khởi điểm đã gắn đúng chức năng. Khi chỉ sửa lỗi dùng `fix`, chỉ thêm test dùng `test`, chỉ tài liệu dùng `docs`; mô tả phải phản ánh nội dung thực. Subject dài được rút gọn rõ nghĩa theo khuyến nghị72 ký tự của nhóm, không đổi task ID. PR một task mặc định vào develop, ngoại lệ base main theo §7; reviewer theo người nhận đầu ra; tiền/quyền/transaction cần hai reviewer chuyên môn. Build/SQL/browser thiếu môi trường ghi BLOCKED, không đóng task từ commit hoặc mock.

| Task | Nhánh chức năng | Subject commit khi triển khai đầy đủ hành vi |
|---|---|---|
| KHANH-01 | `feature/khanh/khanh-01-war-foundation` | `build(core): configure the WAR build and health checks` |
| KHANH-02 | `feature/khanh/khanh-02-http-contract` | `feat(core): standardize HTTP responses and shared types` |
| KHANH-03 | `feature/khanh/khanh-03-jpa-transactions` | `feat(core): preserve principals and actors across transactions` |
| KHANH-04 | `feature/khanh/khanh-04-user-schema` | `feat(identity): implement User behavior and identity constraints` |
| KHANH-05 | `feature/khanh/khanh-05-registration` | `feat(identity): register normalized customer accounts atomically` |
| KHANH-06 | `feature/khanh/khanh-06-session-authorization` | `feat(identity): validate sessions and current organization roles` |
| KHANH-07 | `feature/khanh/khanh-07-request-security` | `feat(core): enforce CSRF and request validation` |
| KHANH-08 | `feature/khanh/khanh-08-email-otp` | `feat(identity): send purpose-bound email verification codes` |
| KHANH-09 | `feature/khanh/khanh-09-password-reset` | `feat(identity): consume reset grants and revoke prior sessions` |
| KHANH-10 | `feature/khanh/khanh-10-admin-seed` | `feat(identity): seed the demo administrator idempotently` |
| KHANH-11 | `feature/khanh/khanh-11-shared-layout` | `feat(core): provide shared JSP layout and API client` |
| KHANH-12 | `feature/khanh/khanh-12-auth-pages` | `feat(identity): connect authentication pages to real services` |
| KHANH-13 | `feature/khanh/khanh-13-me-routes` | `feat(identity): render profile and owner-scoped account queries` |
| KHANH-14 | `feature/khanh/khanh-14-email-handler` | `feat(jobs): deliver ticket and refund notifications via outbox` |
| KHANH-15 | `feature/khanh/khanh-15-auth-acceptance` | `test(identity): verify identity security and recovery scenarios` |

Trước commit: kiểm ownership/diff → chạy checks đúng task → `git diff --check` → stage đường dẫn cụ thể → review staged diff → commit theo mẫu chung. Báo cáo cuối của thành viên/AI phải ghi nhánh, commit, task, tests/evidence và dependency chưa ghép. Push/merge/deploy và thay cấu hình GitHub theo quyền được giao; bộ kế hoạch này không tự thực hiện các bước đó.
