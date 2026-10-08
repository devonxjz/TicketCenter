# Kế hoạch triển khai toàn dự án — Đông

## Prompt khởi đầu — copy từ đây

Copy **nguyên nội dung trong khối bên dưới** vào AI có quyền đọc repository TicketsCenter. Prompt đã gắn đúng thành viên/file; không cần copy toàn bộ 76 task vào cửa sổ chat. Nếu AI không có quyền đọc repository, phải cung cấp các tài liệu được liệt kê trước khi triển khai.

```text
Tôi phụ trách phần của Đông trong dự án TicketsCenter.
Hãy triển khai nhiệm vụ của tôi theo docs/tasks/dong.md.

Trước khi viết mã:
1. Đọc toàn bộ spec.md và docs/classdiagram/diagram.md, gồm thuộc tính,
   phương thức và quan hệ trong XML diagram.
2. Đọc toàn bộ docs/tasks/TEAM-CONTRACT.md từ đầu đến cuối, không bỏ mục
   hoặc chỉ đọc phần cá nhân; đọc đầy đủ docs/tasks/CONVENTIONS.md,
   API-MAP.md, COVERAGE.md và GIT-WORKFLOW.md.
3. Đọc toàn bộ docs/tasks/dong.md. Đọc các phần nhiệm vụ thành viên khác
   cung cấp đầu vào hoặc nhận đầu ra liên quan; kiểm mã nguồn, cấu hình,
   trạng thái Git và bằng chứng thực tế trong repository.
4. Trước thay đổi đầu tiên, báo ngắn gọn phiên bản nguồn đã đọc, phạm vi
   sở hữu, hợp đồng liên miền, nhiệm vụ sẽ làm và tình trạng phụ thuộc.
   Tiếp tục công việc đã được giao nếu đủ đầu vào, không dừng để hỏi lại
   xác nhận cho các bước triển khai thông thường.

Bắt đầu bằng nhiệm vụ đầu tiên chưa hoàn thành và đủ phụ thuộc.
Nếu tôi chỉ định mã nhiệm vụ cụ thể trong tin nhắn tiếp theo, ưu tiên mã đó.
Chỉ sửa file thuộc phạm vi Đông; phần do người khác sở hữu phải phối hợp
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

Muốn giao một task cụ thể, thêm sau prompt: `Nhiệm vụ lần này: DONG-NN.` Thay NN bằng mã thật trong file; không suy ra task hoàn thành từ lịch ngày, checkbox hoặc tên commit.

## Điều kiện bắt buộc trước khi triển khai

**Bắt buộc với cả thành viên và AI:** trước nhiệm vụ đầu tiên, phải đọc **toàn bộ [TEAM-CONTRACT.md](TEAM-CONTRACT.md), từ đầu đến cuối, không bỏ mục, không chỉ đọc phần của mình hoặc bản tóm tắt**. Đồng thời đọc đầy đủ [CONVENTIONS.md](CONVENTIONS.md), [API-MAP.md](API-MAP.md), [COVERAGE.md](COVERAGE.md) và [GIT-WORKFLOW.md](GIT-WORKFLOW.md), cùng spec/diagram được dẫn trong phần nguồn chuẩn.

- [ ] Đã đọc toàn bộ hợp đồng chung và tài liệu quy ước; hiểu ownership Model/Servlet/SQL, DTO/Service/API, quyền/principal, transaction/locking, outbox/SPI, migrations/fixture, Git và tiêu chí nghiệm thu.
- [ ] Ghi trong kế hoạch bắt đầu hoặc Draft PR: đường dẫn hợp đồng, commit SHA nguồn nếu có, task nhận, đầu vào/đầu ra liên miền và các ràng buộc áp dụng. Nếu hợp đồng còn chưa commit, ghi SHA nền và trạng thái working tree; không bịa phiên bản.
- [ ] Đối chiếu task với hợp đồng hiện hành và kiểm file/Service thuộc người nào. Nếu có mâu thuẫn hoặc thiếu hợp đồng bắt buộc, ghi rõ điểm thiếu và chủ cung cấp; chưa triển khai phần phụ thuộc đó.
- [ ] Khi tiếp tục phiên làm việc, kiểm hợp đồng có thay đổi từ phiên bản đã đọc không. Có thay đổi thì đọc lại toàn bộ hợp đồng mới và cập nhật caller/test bị ảnh hưởng trước khi tiếp tục phần liên quan.

Không bắt đầu viết mã cho nhiệm vụ khi chưa hoàn thành việc đọc và đối chiếu trên. Reviewer phải kiểm xác nhận này trước khi chấp nhận PR; một câu “đã đọc” không thay việc chứng minh đầu ra tuân hợp đồng.

> **Dành cho người triển khai và AI:** đọc nguồn và hợp đồng trước khi làm từng task được giao; có thể dùng `superpowers:executing-plans` nếu môi trường có skill đó, không cần cài skill để hiểu kế hoạch trên GitHub. File này là kế hoạch tương lai, không phải bằng chứng phần mềm đã chạy. Chỉ đánh dấu PASS khi có kết quả thực tế gắn với commit. Không tự triển khai cả năm kế hoạch, không sửa phần thuộc người khác để vượt blocker.

**Mục tiêu (Goal):** hoàn thiện tổ chức, thành viên, sự kiện, khu/ghế, danh mục và ảnh bìa; cung cấp UI-01/02/09/10/11/12/13 chạy bằng JSP–Servlet–JPA–SQL Server, đủ giao diện cho mua vé, check-in, hoàn/hủy và quản trị ghép vào WAR chung.

**Kiến trúc (Architecture):** Filter → Servlet → Service → Model/Repository → SQL Server. Model giữ hành vi diagram; Service kiểm quyền và điều phối transaction; Repository dùng JPA và SQL object thật. JSP nhận DTO/JSTL; ảnh được lưu ngoài container, I/O storage chạy ngoài transaction và dọn qua dispatcher chung.

**Công nghệ (Tech Stack):** JDK 25; Tomcat 11.0.25/Servlet 6.1; JSP 4.0/JSTL 3.0; JPA/Hibernate; SQL Server local/Azure SQL online; Bootstrap và CSS chung. Khánh khóa dependency/JSON/test trong `docs/backend/decisions.md`. Money/Decimal ánh xạ Java `BigDecimal`, không tạo wrapper Money song song; định danh `UUID`, thời gian `Instant`/UTC.

**Nguồn (Spec):** [spec.md](../../spec.md) §3–7, §8.2–8.4, §9 UI-01/02/09–13/19–20, §10, §12, §14; [diagram XML diagrams.net](../classdiagram/diagram.md), gồm thuộc tính, tất cả phương thức và quan hệ; [TEAM-CONTRACT](TEAM-CONTRACT.md); [CONVENTIONS](CONVENTIONS.md); [API-MAP](API-MAP.md); [COVERAGE](COVERAGE.md); nguồn truy vết [ngày 06](week-1/day-06-to-chuc-thanh-vien-hoa-hong.md), [ngày 07](week-1/day-07-su-kien-khu-ghe-anh.md). Tham khảo [prototype](../web-demo/prototype/README.md), giữ page frame/bố cục; không thiết kế lại, sinh ảnh hoặc vẽ bổ sung sơ đồ thiếu. Hợp đồng thành viên mở rộng lịch cũ từ backend sang toàn dự án, dùng M0–M4 thay ngày cũ.

## Ràng buộc chung (Global Constraints)

- Đông sở hữu đúng bốn lớp nghiệp vụ `Organization`, `Event`, `Zone`, `Seat`; toàn dự án vẫn đúng 15 lớp. `UserOrganizationRole` là persistence; `EventCategory` chọn **lookup** có id/code/name/active/displayOrder, kiểu tham chiếu danh mục; command/DTO/enum không là lớp nghiệp vụ. Không tạo `OrganizationRequest` hoặc Membership nghiệp vụ.
- Khánh sở hữu `User.assignRole/revokeRole`, auth/session/CSRF/ActorContext/AuthorizationService/TransactionRunner/layout. ActorContext do backend tạo gồm actorId/platformRole/authVersion/emailVerified/type USER hoặc SYSTEM; không nhận từ browser. PrincipalKind: BUYER/MANAGER/CHECK_IN/PLATFORM_ADMIN/AUTH_TECH/WORKER_TECH; giữ nguyên principal/connection trong một use case.
- Đông giữ mapping `OrganizationServlet /organizations/*`, `EventServlet /events/*`, `OrganizationRequestServlet /organization-requests`, `ZoneServlet /zones/*`, `EventCategoryServlet /event-categories`. Không thêm MembershipServlet/EventImageServlet trùng wildcard. Khánh giữ `/me/*`; Vương giữ `/admin/*` và JSP UI-19/20, gọi Service Đông. Thái giữ cancellation/check-in SP/Service/UI; Đông chỉ cung cấp Model và adapter wildcard. Coupon/Report subroute gọi Service Liêm/Vương, không chép logic.
- Mọi mutation người dùng dùng POST + CSRF; GET HTML mặc định ở route trang, `Accept: application/json` trả DTO JSON qua cùng quyền/query. Thành công dùng envelope `data`, lỗi dùng `error.code/message/correlationId`; 400 validation, 401 session, 403 quyền, 404 đối tượng không tồn tại/ngoài phạm vi cần che, 409 trạng thái/khóa kho, 413 ảnh quá lớn, 503 storage/phụ thuộc. Mutation JSON trả 200 hoặc 201 theo danh mục dưới; không trả HTML cho request JSON.
- Tiền HTTP là chuỗi số nguyên VND; SQL decimal(19,0), UUID uniqueidentifier, giờ UTC datetime2. Hiển thị Asia/Ho_Chi_Minh/tiếng Việt, pagination 1-based/default20/max100; sort allowlist và ID làm khóa phụ. Không serialize entity graph hoặc secret.
- `saleStart < saleEnd <= startTime < endTime`; mở bán `[saleStart,saleEnd)` và check-in `[startTime−60 phút,endTime)`, chỉ PUBLISHED. DRAFT/REJECTED được sửa hồ sơ/lịch/cấu trúc; PENDING_APPROVAL không sửa; PUBLISHED chỉ thay giá niêm yết trong phạm vi spec. Delete vật lý chỉ **DRAFT chưa có giao dịch/tham chiếu**, không xóa lịch sử. Sau publish/hủy sau publish khóa layout; giá mới chỉ áp dụng Hold mới, Hold/Order cũ giữ snapshot.
- SP01/SP12 sở hữu mutation TX01/TX12; Java không dirty-write rồi gọi SP cùng mutation. SP độc lập quản lý transaction, trong outer transaction tham gia/savepoint và không commit caller; THROW lỗi, không che rollback. Quyền kiểm trước HTTP và **kiểm lại trong transaction trên connection đang dùng** để chống membership bị revoke giữa hai bước.
- DDL theo `0020_organizations_events.sql`; FK nội miền ở file này, FK tới User/CommissionRule và liên miền được Đông cung cấp manifest cho Vương ghép `0100_cross_domain_keys.sql`. CommissionRule/AuditLog schema của Vương tại M0; SP01 tạo initial rule cùng hồ sơ và MANAGER trong một transaction, không gọi Service tạo phí có commit riêng. TR05 là nơi duy nhất ghi `EVENT_STATUS_CHANGED`; actor hệ thống NULL có nguồn SYSTEM rõ, không gán nhầm user cũ.
- `ImageCleanupJob` là handler `JobHandler.handle(OutboxMessageDto): JobResult` của Liêm, không tạo scheduler/executor thứ hai. Không giữ DB khóa trong upload/delete storage; credential không đưa vào JSP/JS/evidence. F07 thuộc Thái, Đông gọi CheckInService chứ không tạo lại function.
- Mọi file mã/SQL/test dưới đây là **đầu ra cần tạo hoặc sửa khi triển khai**, không phải xác nhận đã tồn tại. Tooling unit/Surefire/sqlserver-it do Khánh, browser-it/fixture/benchmark do Vương cần tạo; không báo đã chạy Maven hoặc SQL từ việc lập kế hoạch.

## Trọng tâm review (Review Focus)

1. Revoke quyền giữa kiểm HTTP và commit: connection mutation phải đọc membership hiện hành; bị revoke không ghi dữ liệu (DONG-05/06/08).
2. Publish đua sửa cấu trúc hoặc quy tắc phí: layout/rule/trạng thái phải nhất quán; audit lỗi rollback (DONG-09).
3. Thay ảnh đồng thời hoặc crash sau upload: URL đang dùng không bị dọn, ảnh mồ côi được dọn sau grace và restart (DONG-08).
4. Hai manager hạ quyền đồng thời, hoặc User DISABLED: tổ chức không mất MANAGER có User ACTIVE và membership active (DONG-05, phối hợp Khánh).
5. Ngồi/đứng, giá 0, mốc giờ bằng nhau và refund lặp: không âm kho, không nhân chỗ, không đổi snapshot giá, không public draft (DONG-01/07/10/12).

## Đường dẫn, giao diện và bàn giao nền

### Phụ thuộc ngoài theo mốc

| Mốc | Nhận từ ai/task | Đầu ra sử dụng |
|---|---|---|
| M0 | KHANH-01/02/03/04/06/11 | WAR/helpers; dto/common ActorContext/Page/PageRequest; model/identity User/OrganizationRole; auth/clock/transaction API TEAM, layout và test contract; `User.assignRole(Organization,OrganizationRole):void`, `revokeRole(Organization):void` |
| M0 | LIEM-01/02 | TicketHoldItem/Ticket đủ tham chiếu Seat; worker SPI/OutboxMessageDto/JobResult/payload registry; schema sales/outbox và locking contract |
| M0 | THAI-01 | Refund nền/cancel/check-in DTO và chữ ký TEAM, chủ F07; không phải chờ toàn bộ worker hoàn |
| M0 | VUONG-01/02/03 | CommissionRule/CommissionPolicyCommand/validator, AuditLog schema, manifest/grants/fixture registry; nhận FK proposal của Đông |
| M1 | DONG-03…10 + caller Khánh/Vương | Tổ chức duyệt có phí/MANAGER; Event publish được tạo bằng API; UI public/editor thật |
| M2/M3 | Liêm mua/coupon; Thái check-in/hoàn/hủy; Vương report | Adapter DONG-11, regression DONG-12; chưa có dependency thì ghi BLOCKED phần tích hợp, tiếp tục task độc lập |
| M4 | Vương CI/benchmark/runbook/hồ sơ | DONG-13 chuyển evidence/fixture/chương báo cáo cho bản ghép |

### DTO/command được Đông định nghĩa tại DONG-01

Mỗi kiểu sau là một file `.java` riêng tại prefix ghi trong bảng. `id`, FK là UUID; tiền BigDecimal khi Java/chuỗi số nguyên khi JSON; các mốc là Instant. Trường `?` nullable. Các giá trị trạng thái theo spec §7, không nhận status từ mutation command.

| Prefix chính xác | Kiểu và fields |
|---|---|
| `src/main/java/vn/ticketscenter/dto/identity/` | `OrganizationCommand(name,contactEmail,contactPhone?,description?)`; `OrganizationDto(id,name,contactEmail,contactPhone?,description?,requesterId?,requesterUserName?,status,rejectionReason?,createdAt,submittedAt?,decidedAt?)`; `RequestFilter(status?,keyword?)` |
| cùng prefix identity | `MembershipCommand(userName,role)`; `MembershipRoleCommand(role)`; `MemberFilter(keyword?,role?,active?)`; `MembershipDto(organizationId,organizationName,userId,userName,fullName,email,role,active,userStatus,organizationStatus)`; `MembershipAccess(organizationId,role,active,organizationStatus)` dùng kiểm quyền nội bộ, không trả browser |
| `src/main/java/vn/ticketscenter/dto/event/` | `EventCommand(title,description,categoryId,venueName,venueAddress,saleStart,saleEnd,startTime,endTime)`; `EventDto(id,organizationId,organizationName,title,description,categoryId,categoryName,venueName,venueAddress,coverImageUrl?,saleStart,saleEnd,startTime,endTime,status,rejectionReason?,commissionRuleId?,minPrice?,saleActive,serverNow)` |
| cùng prefix event | `EventFilter(keyword?,categoryId?,fromUtc?,toUtc?,status?)`; `ZoneCommand(name,type,price,rows?,seatsPerRow?,standingCapacity?)`; `ZonePriceCommand(price)`; `ZoneDto(id,eventId,name,type,price,capacity,held,sold,available)`; `SeatDto(id,zoneId,rowName,seatNumber,status)`; `EventCategoryDto(id,code,name,displayOrder)`; `CoverDto(eventId,coverImageUrl)` |
| `src/main/java/vn/ticketscenter/model/event/` | Kiểu giá trị `EventDetails(title,description,category,venueName,venueAddress,coverImageUrl?)`, `EventSchedule(saleStart,saleEnd,startTime,endTime)`; lookup reference `EventCategory(id,code,name,active,displayOrder)`; enum `EventStatus`, `ZoneType`, `SeatStatus` |
| `src/main/java/vn/ticketscenter/model/identity/` | Enum `OrganizationStatus` (DRAFT/PENDING_APPROVAL/APPROVED/REJECTED); **OrganizationRole/User/PlatformRole do Khánh sở hữu**, không khai báo lại |

EventDto projection public chỉ PUBLISHED, `rejectionReason`/`commissionRuleId` null; check-in DTO dùng cùng EventDto nhưng minPrice/commissionRuleId/rejectionReason null, không trả số liệu tài chính. OrganizationDto chỉ gửi applicant/manager/admin được phép; public EventDto chỉ dùng organizationName/id, không gửi contact/requester. `listForUser` trả membership active của chính session; list members có cả inactive theo filter.

**Giới hạn triển khai phải đồng bộ Java/DDL/UI** (lựa chọn kỹ thuật cho các trường spec chưa cho độ dài): trim/NFC tên, title và venueName 1–200 ký tự; venueAddress 1–500; contactEmail 1–254 chuẩn hóa theo helper Khánh; phone nullable 1–32 nếu có; Organization description nullable tối đa2000; Event description 1–20000 văn bản thuần, JSP escape; rejectionReason trim 1–2000; Zone name 1–100. Tiền nguyên 0…9999999999999999999, không nhận lẻ/âm. Rows/seatsPerRow 1…100 và tổng ghế tối đa10000/khu; standingCapacity 1…1000000. Giới hạn này phải ghi trong data dictionary khi triển khai; không gọi chúng là giá trị kinh doanh có sẵn trong spec.

### Chữ ký Service và Repository

Các chữ ký đã có TEAM giữ nguyên. `ActorContext`, `Page`, `PageRequest` từ `dto/common`; command/DTO từ catalog trên; `CommissionPolicyCommand` do Vương ở `dto/settlement`, không tạo bản sao. `actor` xác thực bắt buộc trừ các phương thức public được ghi rõ.

```java
// service/identity/OrganizationService.java
OrganizationDto submit(ActorContext actor, OrganizationCommand command);
OrganizationDto get(ActorContext actor, UUID organizationId);
OrganizationDto approve(ActorContext actor, UUID organizationId, CommissionPolicyCommand initialPolicy);
OrganizationDto reject(ActorContext actor, UUID organizationId, String reason);
Page<OrganizationDto> listRequests(ActorContext actor, RequestFilter filter, PageRequest page);
Page<OrganizationDto> listOwnRequests(ActorContext actor, PageRequest page);

// service/identity/MembershipService.java
Page<MembershipDto> listForUser(ActorContext actor, PageRequest page);
Page<MembershipDto> listMembers(ActorContext actor, UUID organizationId, MemberFilter filter, PageRequest page);
MembershipDto add(ActorContext actor, UUID organizationId, MembershipCommand command);
MembershipDto changeRole(ActorContext actor, UUID organizationId, UUID userId, MembershipRoleCommand command);
MembershipDto deactivate(ActorContext actor, UUID organizationId, UUID userId);
MembershipDto activate(ActorContext actor, UUID organizationId, UUID userId);

// repository/identity/MembershipAuthorizationRepository.java; Khánh gọi trên EM đang dùng
Optional<MembershipAccess> findCurrent(EntityManager em, UUID userId, UUID organizationId);

// service/event/EventService.java
EventDto create(ActorContext actor, UUID organizationId, EventCommand command);
EventDto edit(ActorContext actor, UUID eventId, EventCommand command);
void deleteDraft(ActorContext actor, UUID eventId);
EventDto submit(ActorContext actor, UUID eventId);
EventDto publish(ActorContext actor, UUID eventId, UUID commissionRuleId);
EventDto reject(ActorContext actor, UUID eventId, String reason);

// service/event/ZoneService.java
ZoneDto create(ActorContext actor, UUID eventId, ZoneCommand command);
ZoneDto edit(ActorContext actor, UUID zoneId, ZoneCommand command);
ZoneDto changePrice(ActorContext actor, UUID zoneId, ZonePriceCommand command);
void deleteDraft(ActorContext actor, UUID zoneId);

// service/event/EventQueryService.java
Page<EventDto> listPublic(EventFilter filter, PageRequest page);
EventDto getPublic(UUID eventId);
List<ZoneDto> listPublicZones(UUID eventId);
Page<SeatDto> listPublicSeats(UUID zoneId, PageRequest page);
Page<EventDto> listForOrganization(ActorContext actor, UUID organizationId, EventFilter filter, PageRequest page);
List<ZoneDto> listZones(ActorContext actor, UUID eventId);
Page<SeatDto> listSeats(ActorContext actor, UUID zoneId, PageRequest page);
Page<EventDto> listAdmin(ActorContext actor, EventFilter filter, PageRequest page);
EventDto get(ActorContext actor, UUID eventId);

// service/event/EventCategoryService.java
List<EventCategoryDto> listActive();
// service/event/EventCoverService.java
CoverDto replace(ActorContext actor, UUID eventId, InputStream content, long declaredSize);
```

`get` kiểm admin hoặc MANAGER đúng org; không dùng `getPublic` để đọc draft. `OrganizationService.get` cho requester của hồ sơ, MANAGER active đúng org hoặc ADMIN, ngoài phạm vi trả404. `listForOrganization/listZones/listSeats` MANAGER; quyền check-in dùng CheckInService Thái riêng. Zone `/edit` nhận ZoneCommand toàn bộ ở DRAFT/REJECTED; ở PUBLISHED chỉ nhận ZonePriceCommand `{price}` và gọi changePrice, field cấu trúc xuất hiện trả400/409, không âm thầm bỏ qua.

### Danh mục SQL/UI

| Phần | Đông phải tạo và có đường dùng |
|---|---|
| Constraint | C02 `UQ_UserOrganizationRole_User_Organization`; C03 `UQ_Seat_Zone_Row_Number`; C04 `CK_Event_TimeRange`; C05 `CK_Zone_Price_Quota`; C10 `DF_Organization_Status` + `CK_Organization_Requester` |
| View/function | V01 `dbo.vw_PublicEvents`; V02 `dbo.vw_ZoneInventory`; V10 `dbo.vw_OrganizationMembers`; F03 `dbo.fn_GetZoneAvailability` |
| SP/transaction | SP01 `dbo.usp_ApproveOrganization`/TX01; SP12 `dbo.usp_PublishEvent`/TX12 |
| Trigger | TR01 `dbo.trg_Zone_ProtectPublishedLayout`; TR02 `dbo.trg_Seat_ProtectPublishedLayout`; TR05 `dbo.trg_Event_AuditStatusChange`; TR06 `dbo.trg_UserOrganizationRole_ProtectLastManager` |
| Index | IX01 `IX_Event_Status_StartTime`; IX02 `IX_Seat_Zone_Status`; IX15 `IX_Event_Organization_Status_StartTime` |
| UI | UI-01 `views/event/list.jsp`; UI-02 `views/event/detail.jsp`; UI-09 `views/organization/request.jsp`; UI-10 `views/organization/dashboard.jsp`; UI-11 `views/organization/members.jsp`; UI-12 `views/organization/events.jsp`; UI-13 `views/event/editor.jsp` (mọi prefix `src/main/webapp/WEB-INF/`) |

### Quy tắc chứng minh áp dụng cho từng task

Reviewer được ghi theo nơi nhận đầu ra; người làm luôn là Đông. Mỗi task có `docs/evidence/dong/DONG-NN.md`: commit, môi trường/phiên bản, fixture IDs/base time, testcase, command/thao tác, expected/actual, exit code, dữ liệu DB trước/sau, log đã lọc, SQL object được ứng dụng gọi, dependency và PASS/FAIL/BLOCKED. Không token/cookie/password/QR thật. Test hai connection có barrier và timeout, không gọi tuần tự rồi kết luận race.

Các lệnh **dự kiến sau khi tooling được tạo**: unit `mvn -B verify`; một IT `mvn -B -Psqlserver-it -Dit.test=<ClassIT> verify`; toàn SQL `mvn -B -Psqlserver-it verify`; browser `mvn -B -Pbrowser-it verify`; SQL `sqlcmd -S "$env:TC_SQL_HOST" -d "$env:TC_TEST_DATABASE" -E -b -i database/tests/dong/DONG-NN.sql` khi local integrated auth đúng runbook. Nếu dùng login/Entra, theo runbook của Khánh/Vương, không truyền mật khẩu ở command line. Profile thiếu/DB thiếu/test skip ⇒ BLOCKED, không PASS. Mỗi task bên dưới nêu lớp/file ca riêng; dùng đúng lớp trong `-Dit.test` và đúng file SQL thay `NN`. Các command này chưa được chạy khi soạn kế hoạch.

## DONG-01 — Model và hợp đồng nền đủ cho các module khác

**Mốc/phụ thuộc:** M0; KHANH-01 build, KHANH-02 types/HTTP, KHANH-04 User/schema, LIEM-01, VUONG-01 để compile tham chiếu. **Mục tiêu:** bốn Model, DTO catalog và method contract có unit test; không chỉ tạo stub để đánh dấu nghiệp vụ hoàn thành. **Nguồn:** spec §4–7/§14.14; methods và 25 quan hệ XML diagram.

**Tạo:** `src/main/java/vn/ticketscenter/model/identity/Organization.java`, `src/main/java/vn/ticketscenter/model/identity/OrganizationStatus.java`; `src/main/java/vn/ticketscenter/model/event/Event.java`, `Zone.java`, `Seat.java`, `EventDetails.java`, `EventSchedule.java`, `EventCategory.java`, `EventStatus.java`, `ZoneType.java`, `SeatStatus.java` (các tên cuối nằm cùng `src/main/java/vn/ticketscenter/model/event/`); mỗi DTO catalog ở đường dẫn prefix đã chốt; `src/main/java/vn/ticketscenter/repository/identity/MembershipAuthorizationRepository.java`. Tạo API Service với chữ ký catalog ở `src/main/java/vn/ticketscenter/service/identity/OrganizationService.java`, `MembershipService.java` cùng prefix identity; `src/main/java/vn/ticketscenter/service/event/EventService.java`, `EventQueryService.java`, `ZoneService.java`, `EventCategoryService.java`, `EventCoverService.java` cùng prefix event. M0 cho phép khai báo/stub ném lỗi chưa triển khai để caller compile; task nghiệp vụ sau sửa cùng file, không trả thành công giả và không tính stub là nghiệp vụ hoàn thành. **Test:** `src/test/java/vn/ticketscenter/model/identity/OrganizationTest.java`, `src/test/java/vn/ticketscenter/model/event/EventTest.java`, `ZoneTest.java`, `SeatTest.java` cùng nhóm; `src/test/java/vn/ticketscenter/acceptance/DongContractIT.java`. **Bàn giao:** Khánh quyền, Liêm/Thái kho, Vương commission/caller.

**Consumes:** User và các chữ ký Khánh; TicketHoldItem/Ticket Liêm; CommissionRule Vương; findCurrent dùng EntityManager caller. **Produces:** catalog và chữ ký ở trên; đủ methods dưới đây, không đổi tên hoặc tham số diagram. Tiền dùng BigDecimal, Boolean/Integer dùng kiểu Java thống nhất Khánh; Identifier ánh xạ UUID.

```text
Organization: submitForApproval():void; approve():void; reject(String reason):void.
Event: updateDetails(EventDetails details):void; configureSchedule(EventSchedule schedule):void;
  addZone(Zone zone):void; removeZone(Zone zone):void; submitForApproval():void;
  publish(CommissionRule rule, Instant now):void; reject(String reason):void;
  cancel(Instant now):void; isSaleActive(Instant now):Boolean; isCheckInOpen(Instant now):Boolean.
Zone: changePrice(BigDecimal price):void; configureSeating(Integer rows,Integer seatsPerRow):void;
  setStandingCapacity(Integer capacity):void; holdStanding(Integer quantity):void;
  releaseHeldStanding(Integer quantity):void; sellHeldStanding(Integer quantity):void;
  returnSoldStanding(Integer quantity):void; getCapacity():Integer; getAvailable():Integer.
Seat: hold(TicketHoldItem item):void; release(TicketHoldItem item):void;
  markSold(TicketHoldItem item):void; returnToInventory(Ticket ticket):void.
```

Model không gọi DB/HTTP/storage hoặc tự transaction. Zone giữ đúng tên thuộc tính diagram `name/type/price/standingCapacity/standingHeld/standingSold`; ba trường đứng nullable cho SEATED. `held/sold/capacity/available` trong ZoneDto là projection trình bày, không đổi tên hoặc thêm nguồn kho của Model. Seat xác minh item/ticket đúng Zone/Seat/Event và đúng allocation; Service/SP chứng minh ownership/idempotency qua persistence, không thêm Hold/Refund workflow vào Model. Không thêm setter cho kết quả kho thay các methods. Event.cancel chỉ PUBLISHED và `now < startTime`, lặp CANCELLED không tác động kho; SP13/cancel outbox do Thái. Event–Zone, Zone–Seat là composition nhưng không mặc định cascade-delete tài chính; Event thuộc Organization/category/rule đúng quan hệ diagram.

**HTTP:** chưa đăng ký endpoint mới; DTO là hợp đồng cho các route task sau, browser không nhận allocation/mutation methods trực tiếp.

- [ ] Đọc XML, lập assertion đủ thuộc tính/phương thức; giữ requester nullable chỉ DRAFT, enum đúng spec và quan hệ hai loại khu.
- [ ] Viết/run test đỏ cho schedule sai; publish thiếu zone/rule sai org; Seat wrong item; quota underflow; triển khai methods rồi chạy unit xanh.
- [ ] `Zone(capacity3)` giữ2→available1, bán2→held0/sold2, hoàn1→sold1/available2; q0/âm/vượt kho phải lỗi và giữ nguyên trạng thái. Giá0 hợp lệ, âm/lẻ bị từ chối.
- [ ] Seated2×3 sinh A1…B3; AVAILABLE→HELD→SOLD→AVAILABLE với đúng item/ticket. Wrong seat/standing item hoặc return vé khác không đổi trạng thái. Lặp giải phóng/bán/hoàn được điều phối persistence ở Liêm/Thái, test không dùng method đơn lẻ để giả bằng chứng exactly-once.
- [ ] Test `saleStart` true, `saleEnd` false, `start−60 phút` check-in true, `endTime` false; cancel đúng start trả conflict, CANCELLED không bán/check-in.
- [ ] Contract test compile caller Khánh/Liêm/Thái/Vương và DTO fields; ghi chưa có schema/SQL thì integration BLOCKED; evidence DONG-01 và bàn giao M0.

## DONG-02 — Schema nền, constraint và danh mục

**Mốc/phụ thuộc:** M0; DONG-01, KHANH-04 User schema; VUONG-01 schema audit/fee và VUONG-02 manifest. **Mục tiêu:** dựng bảng thuộc Đông trên fresh DB theo thứ tự, constraint thực chặn dữ liệu sai. **Nguồn:** spec §5.3/§7/§14.2–14.3; TEAM migration.

**Tạo:** `database/migrations/0020_organizations_events.sql`, `database/seeds/event-categories.sql`, `database/tests/dong/DONG-02.sql`, `src/main/java/vn/ticketscenter/persistence/identity/UserOrganizationRole.java`, `src/test/java/vn/ticketscenter/acceptance/DongSchemaIT.java`, `docs/backend/dong-schema.md`. **Modify khi triển khai phối hợp:** đăng ký phần Đông trong `database/README.md`, cung cấp FK proposal cho chủ `database/migrations/0100_cross_domain_keys.sql` (Vương viết file đó), không sửa DDL Vương.

**Consumes/Produces:** Model/DTO DONG-01 → Organization/UserOrganizationRole/EventCategory/Event/Zone/Seat mapping. MembershipAuthorizationRepository.findCurrent trả Optional rỗng khi không có liên kết; có liên kết inactive vẫn trả active=false để AuthorizationService từ chối. Lookup id ổn định trong registry Vương, seed idempotent theo code; không tự tạo UUID mới mỗi startup. Không endpoint seed/reset.

- [ ] Tạo columns đúng catalog/model, NOT NULL và CHECK miền enum; UTC timestamps, FK nội miền Event→Organization/category, Zone→Event, Seat→Zone; cross User/requester/rule đăng ký 0100, không tham chiếu bảng chưa tồn tại. Event có technical `coverStorageKey` nullable cùng URL để dọn đúng namespace; không thêm lớp nghiệp vụ ảnh, không trả key trong DTO.
- [ ] C02 unique(userId,organizationId) kể cả inactive; C03 unique(zoneId,rowName,seatNumber); C04 lịch đúng; C05 STANDING capacity>0/held,sold>=0/tổng<=capacity, SEATED ba cột đứng NULL; C10 default DRAFT/requester bắt buộc sau gửi. Unique EventCategory.code là khóa kỹ thuật ngoài C01–20.
- [ ] Test SQL dùng THROW: C04 saleEnd=start hợp lệ, saleStart=saleEnd hoặc start=end sai; C05 capacity3/held1/sold2 hợp lệ, held2/sold2 hoặc NULL bắt buộc sai; C10 requester NULL ở DRAFT hợp lệ, PENDING/APPROVED/REJECTED sai; duplicate label/pair kể cả inactive sai.
- [ ] Gọi findCurrent bằng EntityManager do Khánh cấp, trả org/role/active/status đúng, không tài khoản/auth secret; Khánh kiểm User.status hiện hành trên cùng connection, không suy ra nó từ active membership.
- [ ] SQL lỗi nhiều dòng rollback cả statement; test outer transaction tạo rồi rollback không để record; smoke migration fresh/reapply theo manifest/checksum, seed không nhân category. Khóa schema đăng ký M0, chưa tạo SP gọi bảng chưa có.
- [ ] Chạy DongSchemaIT + file SQL khi môi trường sẵn; evidence DONG-02 chứa catalog C02/03/04/05/10, mapping/FK proposal và bàn giao Khánh/Vương/Liêm/Thái.

## DONG-03 — Hồ sơ tạo tổ chức và UI-09

**Mốc/phụ thuộc:** M1; DONG-01/02, KHANH-02 HTTP/validation, KHANH-03 transaction, KHANH-06 auth, KHANH-07 CSRF và KHANH-11 layout. **Mục tiêu:** session user gửi hồ sơ, theo dõi trạng thái trên cùng Organization; admin query/reject có Service cho Vương. **Nguồn:** spec §6.2/§7/§9 UI-09/19; D06-T01.

**Tạo:** `src/main/java/vn/ticketscenter/repository/identity/OrganizationRepository.java`, `src/main/java/vn/ticketscenter/controller/identity/OrganizationRequestServlet.java`, `src/main/webapp/WEB-INF/views/organization/request.jsp`, `src/main/webapp/WEB-INF/views/identity/organization-requests.jsp` (Khánh MeServlet forward lịch sử đến file view này do Đông viết), `src/main/webapp/assets/js/organization-request.js`, `src/test/java/vn/ticketscenter/acceptance/DongOrganizationRequestIT.java`, `src/test/java/vn/ticketscenter/browser/DongOrganizationRequestBrowserIT.java`. **Modify:** `src/main/java/vn/ticketscenter/service/identity/OrganizationService.java` API M0 thành nghiệp vụ thật. **Consumes:** AuthService.requireCurrentUser, TransactionRunner.required, ClockProvider.now; catalog commands. **Produces:** submit/get/listOwnRequests/listRequests/reject chữ ký chung; approve hoàn thiện DONG-04. Reviewer Khánh/Vương.

**HTTP/quyền:** `GET /organization-requests` → HTML UI-09 hoặc JSON Page hồ sơ own; `POST /organization-requests` JSON OrganizationCommand →201 OrganizationDto PENDING_APPROVAL. `GET /me/organization-requests?page=1&pageSize=20` do Khánh gọi listOwnRequests →200 Page JSON/HTML theo trang được Khánh định nghĩa. Vương adapter `GET /admin/organization-requests[/{id}]` gọi listRequests/get; `POST .../{id}/reject {"reason":"Thiếu thông tin liên hệ"}` →200 REJECTED. Session logged-in đủ gửi hồ sơ, không tự thêm emailVerified requirement; chỉ ADMIN quyết định/query mọi hồ sơ.

- [ ] Test đỏ requester lấy session, command không có applicantId/status/role/initial fee; thêm field giả thuộc quyền trả400, không thay người gửi.
- [ ] submit tạo DRAFT rồi submitForApproval cùng transaction/record; trả UUID và UTC; không tạo request table/Organization thứ hai. XSS title/description hiển thị escape; dài vượt giới hạn trả400 không ghi.
- [ ] Reject PENDING yêu cầu reason; lặp REJECTED cùng reason trả hiện trạng không audit trùng; reject kết quả đã APPROVED hoặc reason khác trả409. Hồ sơ REJECTED không có luồng gửi lại Organization; UI vẫn cho đọc lý do.
- [ ] Own filter actorId do server: buyer B không xem hồ sơ buyer A (404), chưa session401, buyer self-approve403, admin get thành công; pagination không lặp/bỏ row khi cùng createdAt, sort request allowlist `createdAtAsc/createdAtDesc` + id.
- [ ] UI-09 form có label/CSRF/loading/success/error và lịch sử empty/status/reason; mạng mất sau POST đọc listOwn trước thao tác tiếp, không retry mù và không tuyên bố submission idempotent chưa có khóa nghiệp vụ.
- [ ] Gây lỗi sau insert trước submit: rollback hồ sơ; CSRF sai403 không ghi. Chạy DongOrganizationRequestIT + browser khi profile có; evidence DONG-03 gồm DB/response/screenshot desktop/mobile; bàn giao Service cho Vương UI-19, listOwn cho Khánh MeServlet.

## DONG-04 — Duyệt tổ chức nguyên tử SP01/TX01

**Mốc/phụ thuộc:** M1; DONG-02/03, VUONG-01 CommissionRule/AuditLog/validator, Khánh User.assignRole và transaction. **Mục tiêu:** duyệt Organization hiện hữu cùng initial fee/MANAGER/audit; hai lần duyệt không tạo phí/quyền trùng. **Nguồn:** spec §6.2/§14 SP01/TX01; D06-T02.

**Tạo:** `database/migrations/0400_SP01.sql`, `database/tests/dong/DONG-04.sql`, `src/test/java/vn/ticketscenter/acceptance/DongApproveOrganizationIT.java`. **Modify:** OrganizationService/OrganizationRepository từ DONG-03. **Consumes:** `CommissionPolicyValidator.validate(CommissionPolicyCommand):void`; `User.assignRole(Organization,OrganizationRole):void` model contract Khánh, CommissionRule và grants Vương. **Produces:** exact `OrganizationService.approve(actor,organizationId,initialPolicy):OrganizationDto`; SP01 logical `(organizationId,actorId,initialCommissionPolicy)` chốt transport scalar tại M0: `@organizationId uniqueidentifier,@actorId uniqueidentifier,@ratePercent decimal(19,6),@fixedFee decimal(19,0),@effectiveFrom datetime2,@effectiveTo datetime2` → một result row organizationId/status/initialCommissionRuleId. Vương caller JSON initialPolicy có ratePercent/fixedFee/effectiveFrom/effectiveTo; applicant không được truyền phí.

**HTTP/quyền:** Vương `POST /admin/organization-requests/{id}/approve {"initialPolicy":{"ratePercent":"5.000000","fixedFee":"10000","effectiveFrom":"2026-10-01T00:00:00Z","effectiveTo":"2027-10-01T00:00:00Z"}}` →200 APPROVED cùng OrganizationDto; chỉ ADMIN và DB PLATFORM_ADMIN. Tên trường HTTP `initialPolicy` theo TEAM-CONTRACT §3.5; SP01 dùng scalar như hợp đồng phía trên. Đây là fixture chính sách, không mặc định kinh doanh. Đông không viết AdminServlet/JSP UI-19.

- [ ] Viết TX01 test đỏ, validate policy không âm/hiệu lực có thứ tự; thiếu policy400 không đổi PENDING. Khóa Organization/requester theo locking contract, recheck admin User ACTIVE/auth hiện hành trong DB, không tin actorId request; requester đã DISABLED trả409 trước mutation vì không thể tạo manager hoạt động đầu tiên.
- [ ] SP01 kiểm PENDING, tạo một initial rule thuộc đúng org, cập nhật APPROVED và map requester MANAGER active cùng transaction/audit. Mapping thỏa User.assignRole; Java không gọi assignRole để dirty-write trùng phần SP đã ghi, refresh/clear EntityManager sau SP.
- [ ] Đã APPROVED trả trạng thái/rule cũ kể cả retry payload thay đổi, không tái cấp role đã bị thay sau đó, không thêm rule hoặc audit duyệt; REJECTED409; ID lạ404. Model contract assignRole và persistence path được test, không tạo User thứ hai.
- [ ] Hai connections cùng approve cùng ID: cùng org/rule cuối, đúng một fee/map pair/audit quyết định. Chặn direct-SP buyer/check-in; nâng role ADMIN qua command400/403.
- [ ] Gây lỗi sau status APPROVED trước insert rule, sau rule trước map, sau map trước audit: toàn bộ rollback PENDING/no rule/no role; outer transaction gọi SP rồi rollback cũng xóa cả kết quả, SP không commit caller; không để khóa treo.
- [ ] Chạy DongApproveOrganizationIT + SQL DONG-04; evidence gồm real JPA→SP trace, TX01 database snapshots/race barrier/exit code; bàn giao Vương adapter UI-19, Đông DONG-05/06 và Liêm/Thái organization đã duyệt.

## DONG-05 — Membership/V10/TR06 và UI-11

**Mốc/phụ thuộc:** M1; DONG-02/04, KHANH-01 User/AuthorizationService. **Mục tiêu:** thêm tài khoản có sẵn bằng userName, đổi/kích hoạt/vô hiệu quyền hiện hành, bảo vệ manager cuối trên HTTP và SQL. **Nguồn:** spec §4/§6.2/§14 C02/V10/TR06; D06-T03/04.

**Tạo:** `src/main/java/vn/ticketscenter/repository/identity/MembershipRepository.java`, `src/main/java/vn/ticketscenter/controller/identity/OrganizationServlet.java`, `database/migrations/0300_V10.sql`, `database/migrations/0500_TR06.sql`, `database/tests/dong/DONG-05.sql`, `src/main/webapp/WEB-INF/views/organization/members.jsp`, `src/main/webapp/assets/js/organization-members.js`, `src/test/java/vn/ticketscenter/acceptance/DongMembershipIT.java`, `src/test/java/vn/ticketscenter/browser/DongMembershipBrowserIT.java`. **Modify:** `src/main/java/vn/ticketscenter/service/identity/MembershipService.java` và MembershipAuthorizationRepository từ DONG-01. **Consumes/Produces:** full MembershipService signatures, findCurrent; User.assignRole/revokeRole Khánh. Reviewer Khánh, Thái nhận membership cho check-in.

**HTTP MANAGER đúng org:** GET `/organizations/{id}/members?keyword=&role=&active=&page=1` →200 Page MembershipDto HTML UI-11/JSON; POST cùng route `{userName,role}` →201 new pair, 200 existing same active role; POST `/organizations/{id}/members/{userId}/role {role}` →200; POST `.../deactivate {}` hoặc `/activate {}` →200 hiện trạng. role chỉ MANAGER/CHECK_IN_STAFF. Khánh `/me/memberships` gọi listForUser cho UI-24 và picker, GET200 own active Page. Unknown account404 `USER_NOT_FOUND` cho manager được phép tìm; ngoài org404; thiếu role403; manager cuối409 `LAST_MANAGER`.

- [ ] Viết test đỏ quyền manager A/check-in B: quản lý A thành công, quản lý B403; role ADMIN400; userName chuẩn hóa theo Khánh, không nhận email để tạo User hoặc invitation.
- [ ] Khóa Organization trước role mutation, đọc lại actor membership/User hiện hành trong cùng EntityManager; gọi User.assignRole để cập nhật map và persist unique pair; revokeRole bỏ map, giữ record active=false. Reactivate dùng role đã lưu, không tạo row thứ hai, target User DISABLED409.
- [ ] V10 một dòng/pair có userName/email/name/status/active, không passwordHash/authVersion; query bind/scoped. listForUser chỉ actorId và active=true, organization APPROVED/User ACTIVE; listMembers hỗ trợ inactive history đúng org, sort `userNameAsc/userNameDesc` + userId.
- [ ] TR06 set-based check inserted/deleted cả org nguồn/đích; bảo vệ MANAGER có membership active=true và User ACTIVE, khóa org trong mọi luồng ghi. Phối hợp Khánh: mọi đường đổi User.status có ảnh hưởng phải khóa tất cả org của user theo ID và chặn mất manager cuối; không claim TR06 trên map một mình bảo vệ User.status update.
- [ ] Hai manager hạ quyền đồng thời chỉ một thành công, còn một ACTIVE manager; deactivate/role/activate lặp không thay lần hai; test multirow một vi phạm rollback toàn statement. Chèn lỗi sau map trước audit rollback quyền/lịch sử; revoke actor đua mutation bị từ chối trước commit.
- [ ] UI-11 hiển thị add by userName, role/active/error/empty/loading, disable nút theo khả năng nhưng backend kiểm lại; sau membership bị revoke tải lại picker và báo quyền hiện hành. CSRF403 không ghi; ngôn ngữ Việt/keyboard/mobile.
- [ ] Chạy DongMembershipIT, SQL DONG-05, browser khi sẵn; evidence DONG-05; bàn giao Khánh AuthorizationService/MeServlet và Thái listEvents/check-in, Liêm coupon, Vương report. Không tự sửa các caller ngoài ownership.

## DONG-06 — CRUD Event nháp và UI-12

**Mốc/phụ thuộc:** M1; DONG-01/02/04/05. **Mục tiêu:** tạo/sửa/xóa draft thật, đọc nội bộ không lộ chéo tổ chức; chuẩn bị submit/publish. **Nguồn:** spec §6.3/§9 UI-12/13; D07-T01.

**Tạo:** `src/main/java/vn/ticketscenter/repository/event/EventRepository.java`, `src/main/java/vn/ticketscenter/controller/event/EventServlet.java`, `src/main/webapp/WEB-INF/views/organization/events.jsp`, `src/main/webapp/assets/js/organization-events.js`, `src/test/java/vn/ticketscenter/acceptance/DongDraftEventIT.java`, `src/test/java/vn/ticketscenter/browser/DongEventListBrowserIT.java`. **Modify:** EventService API M0; OrganizationServlet dispatch `/events` và `/events/{eventId}`. **Consumes:** AuthorizationService.requireOrganizationRole(actor,org,Set.of(MANAGER)), TransactionRunner.required, catalog EventCommand; **Produces:** EventService.create/edit/deleteDraft; internal EventQuery listForOrganization/get có contract M0, hoàn thiện tại DONG-10. Basic query nội bộ để nghiệm thu UI-12 được triển khai ở task này trên cùng API, DONG-10 bổ sung query public/View, không chờ vòng phụ thuộc. Reviewer Khánh/Liêm.

**HTTP:** POST `/organizations/{id}/events` EventCommand →201 DRAFT EventDto; GET cùng route EventFilter/Page →200 UI-12/Page JSON; GET `/organizations/{id}/events/{eventId}` →200 editor/internal detail phải đối chiếu cả org path và event owner; POST `/events/{id}/edit` EventCommand →200; POST `/events/{id}/delete {}` →200 `{"data":{"id":"<UUID>","deleted":true}}`. MANAGER organization APPROVED; actor B truy cập event A404, check-in-only403. Publish/reject method do DONG-09; cancellation chỉ Model đã có, Service Thái.

- [ ] Test đỏ EventCommand không nhận orgId/status/commissionRuleId/coverImageUrl; title/category/venue/schedule theo catalog; danh mục inactive400, org chưa APPROVED409.
- [ ] create dùng orgId route đã authorize, gọi Event.updateDetails/configureSchedule; edit chỉ DRAFT/REJECTED, giữ rejection history/audit dù UI cho resubmit; PENDING/PUBLISHED/CANCELLED409, không nhận partial schema không rõ.
- [ ] delete chỉ DRAFT và không Hold/Order/Ticket/Refund/transaction reference: kiểm có khóa rồi xóa Seat/Zone/Event theo thứ tự hợp lệ, FK RESTRICT chặn race, không cascade tài chính; REJECTED không xóa vật lý; lần hai404 (không tạo tombstone giả).
- [ ] Chèn lỗi sau delete Seat trước Zone/Event: rollback đủ cấu trúc; edit lỗi C04 giữ dữ liệu cũ. Revoke actor sau auth trước mutation →403/404, không ghi. Manager A gửi organizationId B trong body400 và không đổi owner.
- [ ] UI-12 filter mọi trạng thái, rejectionReason, link editor/preview, create/delete có xác nhận và trạng thái success/error/empty/loading; sort `startTimeAsc/startTimeDesc/titleAsc` + id; draft khác org/public không xuất hiện.
- [ ] Chạy DongDraftEventIT + browser; evidence DONG-06 với create/edit/delete DB thật và permission negative; bàn giao DONG-07/08/09, Vương listAdmin/get cho UI-20.

## DONG-07 — Khu/ghế, giá và UI-13

**Mốc/phụ thuộc:** M1; DONG-01/02/06, LIEM-01 price snapshot contract. **Mục tiêu:** editor xây ngồi/đứng bằng transaction, layout đúng và thay giá không hồi tố. **Nguồn:** spec §6.3–6.4/§9 UI-13/C03/C05; D07-T01.

**Tạo:** `src/main/java/vn/ticketscenter/repository/event/ZoneRepository.java`, `src/main/java/vn/ticketscenter/controller/event/ZoneServlet.java`, `src/main/webapp/WEB-INF/views/event/editor.jsp`, `src/main/webapp/assets/js/event-editor.js`, `database/tests/dong/DONG-07.sql`, `src/test/java/vn/ticketscenter/acceptance/DongZoneLayoutIT.java`, `src/test/java/vn/ticketscenter/browser/DongEventEditorBrowserIT.java`. **Modify:** ZoneService API M0; EventServlet dispatch `/zones`; Event methods add/remove zone; EventQueryService basic listZones/listSeats nội bộ để editor chạy độc lập, DONG-10 bổ sung View/public query. **Consumes/Produces:** ZoneService signatures; listZones/listSeats DTO catalog; prices BigDecimal. Reviewer Liêm/Thái.

**HTTP MANAGER owner:** POST `/events/{id}/zones` ZoneCommand →201 ZoneDto; POST `/zones/{id}/edit` DRAFT/REJECTED ZoneCommand →200, PUBLISHED ZonePriceCommand →200; POST `/zones/{id}/delete {}` →200 deleted DTO chỉ DRAFT/no references. GET `/events/{id}/zones` public nếu PUBLISHED, manager/admin dùng same authenticated query cho nonpublic; GET `/zones/{id}/seats?page=1&pageSize=100` Page SeatDto quyền tương tự. STANDING response seat items rỗng, không dựng seat giả.

- [ ] Test đỏ SEATED2×3→6 Seat A1…B3; rows27→AA, deterministic label base26; max100×100 hợp lệ, rows0/101 hoặc vượt10000 reject400; STANDING capacity3→không Seat, available3; type/nullable fields trái loại400.
- [ ] Lock Event trước Zone/Seat theo locking contract; configureSeating/setStandingCapacity tạo/update cấu trúc atomic, nhãn unique trong khu. Đổi type/cấu trúc DRAFT/REJECTED chỉ khi no allocation/reference, rebuild không gây duplicate; PENDING409.
- [ ] Giá0 hợp lệ, -1/lẻ400; PUBLISHED nhận duy nhất price, không đổi name/type/capacity/rows; CANCELLED409; delete DRAFT/no references, không làm mất giá/nhãn snapshot lịch sử.
- [ ] Fault ghế thứ4 hoặc sau Zone insert → rollback không Zone/Seat bán phần. Multirow duplicate label C03 rollback toàn lệnh; Event publish đua layout được DONG-09 kiểm tiếp.
- [ ] Ca ghép Liêm: Hold A giá200000; đổi price250000; Hold A/Order Item vẫn200000, Hold mới250000; cả hai connection đua đổi giá/hold thấy một snapshot hợp lệ, không giá lai. Chưa có HoldService thì chỉ unit/query PASS, ca này BLOCKED tới DONG-12.
- [ ] UI-13 label và lỗi theo field, form ngồi/đứng, preview ghế bằng DOM từ DTO; không dùng ảnh rạp tĩnh thay dữ liệu; sau publish khóa controls cấu trúc, cho form price và ghi rõ tác động Hold mới. Có loading/empty/error/success/focus/responsive.
- [ ] Chạy DongZoneLayoutIT + SQL DONG-07 + browser; evidence DONG-07; bàn giao Zone/Seat APIs cho Liêm giữ/bán/trả và Thái hoàn, UI editor cho submit/publish.

## DONG-08 — Ảnh bìa, storage và handler dọn ảnh

**Mốc/phụ thuộc:** M1; DONG-06/07, KHANH-01 cấu hình secret, LIEM-01 JobHandler/outbox; Vương runbook namespace/provider. **Mục tiêu:** upload thật, DB đổi URL an toàn, ảnh sống qua redeploy; dọn ảnh cũ/mồ côi sau grace bằng worker chung. **Nguồn:** spec §8.3/§10/§12; D07-T02.

**Tạo:** `src/main/java/vn/ticketscenter/integration/storage/ImageStorage.java`, `StoredImage.java`, `ImageUpload.java` cùng storage package; `src/main/java/vn/ticketscenter/job/event/ImageCleanupJob.java`, `src/test/java/vn/ticketscenter/acceptance/DongImageUploadIT.java`, `src/test/java/vn/ticketscenter/job/event/ImageCleanupJobTest.java`, `docs/backend/dong-storage.md`. **Modify:** EventCoverService API M0; EventServlet `/cover`; event-editor.js và EventRepository. **Reviewer:** Liêm/Vương.

**Consumes:** `JobHandler.handle(OutboxMessageDto message):JobResult` Liêm, transaction/clock Khánh. **Produces:** `EventCoverService.replace(actor,eventId,InputStream,long):CoverDto`; `ImageStorage.upload(ImageUpload upload):StoredImage`, `delete(String key):void` (key không tồn tại = thành công); `ImageUpload(key String,content InputStream,byteCount long,mediaType String)`, `StoredImage(key String,publicUrl String,createdAt Instant)`. Key generated UUID trong namespace `event-covers/`, URL chỉ do provider/config trusted sinh; payload worker version1 `{eventId,key,reason,notBefore}` với reason ORPHAN/REPLACED, idempotencyKey `image-cleanup:orphan:<key>` hoặc `image-cleanup:replaced:<key>`, đăng ký registry Liêm. Hai loại intent riêng để orphan intent đã SUCCEEDED do ảnh đang dùng không chặn cleanup khi thay ảnh về sau. Grace mặc định kỹ thuật24h configurable, không phải cam kết spec; lease/attempt theo dispatcher chung.

**HTTP:** POST `/events/{id}/cover` multipart field `file`, CSRF header →200 CoverDto. MANAGER owner, chỉ DRAFT/REJECTED; quá5MiB413, file không PNG/JPEG/WebP decode hợp lệ400, pixels vượt16000000 hoặc width/height>8192 trả400, storage timeout503; thiếu session401, sai org404. Giới hạn bytes/pixel là cấu hình kỹ thuật chung, không tin filename/Content-Type/declaredSize và không nhận URL client.

- [ ] Viết test file png hợp lệ và file HTML giả png; đọc stream có byte cap, kiểm magic/decode/pixels, reject SVG/HTML; key không dùng filename/`../`; description/image preview escape; tuyệt đối không fetch URL nội bộ từ browser input.
- [ ] Recheck quyền/trạng thái trước upload; upload I/O ngoài DB transaction; transaction sau upload khóa Event, kiểm lại membership/status và cập nhật cover URL. Không gọi Event.updateDetails để vượt guard PUBLISHED; không storage I/O dưới khóa.
- [ ] Tạo cleanup intent cho ảnh mới trước I/O theo outbox/registry để crash vẫn truy vết được; upload xong nếu attach thành công, handler kiểm DB reference sẽ giữ ảnh; attach lỗi/rollback giữ intent dọn sau grace. Thay ảnh commit xong mới đăng ký cleanup ảnh cũ trong cùng transaction; no evidence để orphan vô hạn.
- [ ] Handler kiểm notBefore và mọi live reference (coverStorageKey/coverImageUrl) trước delete; Event đang dùng key ⇒ SUCCEEDED không xóa; key vắng ⇒ SUCCEEDED; provider timeout ⇒ RETRY theo bounded policy. Attach ảnh mới chỉ trong attempt upload gốc trước notBefore, không reattach key cũ/ảnh mồ côi sau grace; generate key bất biến/mỗi upload và server kiểm giới hạn này để tránh race check-reference/delete.
- [ ] Hai replace cùng event: URL cuối chỉ một ảnh đã commit, ảnh còn được dọn; revoke/publish giữa upload và attach ⇒ conflict/quyền và giữ URL cũ, ảnh mới được dọn; crash sau upload/restart và fail DB update không mất cover đang dùng. notBefore−1ms chưa delete, đúng notBefore mới eligible; orphan intent đã xử lý khi ảnh live rồi thay ảnh vẫn có REPLACED intent mới, delete lặp không lỗi.
- [ ] Provider chưa chọn: thử adapter local external directory để test cơ chế, ghi BLOCKED phần provider online; chọn provider bằng spike khả năng HTTPS/Java/namespace/credentials và khóa quyết định cùng Khánh/Vương. Redeploy WAR không xóa external storage; cấu hình không đưa secret vào DTO.
- [ ] Chạy DongImageUploadIT + handler unit + smoke provider thực khi cấu hình có; evidence DONG-08 tách adapter/mock/provider và ghi upload/read/delete, before/after URL, race/restart; bàn giao handler đăng ký Liêm, UI cover Đông và deployment Vương.

## DONG-09 — Submit/reject/publish, SP12/TX12 và bảo vệ layout/audit

**Mốc/phụ thuộc:** M1; DONG-06/07/08; VUONG-01 CommissionRule/AuditLog/TR03/grants; Khánh connection actor context. **Mục tiêu:** submit/publish chỉ khi đủ cấu trúc/ảnh/lịch/rule, khóa layout và audit nguyên tử. **Nguồn:** spec §6.3/§14 SP12/TR01/02/05/TX12; D07-T03. TR03 thuộc Vương, không viết lại.

**Tạo:** `database/migrations/0400_SP12.sql`, `database/migrations/0500_TR01.sql`, `0500_TR02.sql`, `0500_TR05.sql` cùng migrations; `database/tests/dong/DONG-09.sql`, `src/test/java/vn/ticketscenter/acceptance/DongPublishEventIT.java`. **Modify:** EventService/EventRepository; EventServlet submit adapter, editor UI trạng thái. **Consumes:** `CommissionService.getEffective(actor,orgId,ruleId,now):CommissionRuleDto`; ClockProvider/AuthorizationService; **Produces:** EventService.submit/publish/reject chữ ký catalog; `dbo.usp_PublishEvent(@eventId uniqueidentifier,@actorId uniqueidentifier,@commissionRuleId uniqueidentifier)` → một row eventId/status/commissionRuleId. Service lookup hỗ trợ UX, SP đọc lại rule có khóa và giờ DB UTC để quyết định.

**HTTP:** MANAGER POST `/events/{id}/submit {}` →200 PENDING_APPROVAL; ADMIN qua Vương POST `/admin/events/{id}/publish {"commissionRuleId":"<rule UUID>"}` →200 PUBLISHED; `/reject {"reason":"Cần bổ sung địa điểm"}` →200 REJECTED. Đông cung Service/DTO, Vương sở hữu UI-20/AdminServlet. Không manager tự publish/hủy.

- [ ] Test đỏ submit DRAFT/REJECTED cần valid schedule/cover/ít nhất một khu hợp lệ, gửi lặp PENDING trả hiện trạng, PUBLISHED/CANCELLED409; reject cần reason, PENDING→REJECTED, lặp cùng quyết định giữ kết quả/audit một lần, trái kết quả409.
- [ ] SP12 khóa Event/Zone/Seat và rule đúng thứ tự chung; admin hiện hành; PENDING only, đủ cấu trúc ngồi/đứng; effectiveFrom<=publishNow<effectiveTo, rule đúng organization. Đã PUBLISHED cùng rule trả cũ, request rule khác409 không chọn lại; rule thiếu/khác org404 hoặc409 có mã ổn định, không rò policy khác org.
- [ ] TR01/02 set-based inserted/deleted xét cha cũ/mới: sau PUBLISHED/CANCELLED khóa add/delete/move zone/seat, type/capacity/nhãn; allow price hợp lệ, quota/status mutation hợp lệ. Move draft→published và published→draft đều reject; trigger không tự giữ/bán/giải phóng.
- [ ] TR05 chỉ status thật đổi mới ghi một EVENT_STATUS_CHANGED/Event; multirow mỗi Event một audit, same-status no log; actor SESSION_CONTEXT backend ghi đè/xóa connection, unknown SYSTEM actor NULL/sourceSYSTEM, admin actor truy vết được. Thiếu actor ở thao tác user là lỗi nghiệp vụ/config để rollback, không gán actor giả.
- [ ] TX12 lỗi audit sau publish rollback status/rule; outer rollback cũng rollback audit. Hai connections publish vs edit layout/rule: một thứ tự serialize hợp lệ, không published layout sai/rule phí bị đổi; direct SQL multirow có một vi phạm rollback toàn lệnh.
- [ ] Test mốc rule effectiveFrom nhận/effectiveTo từ chối; price/status hợp lệ vẫn dùng được sau publish và sau cancellation ở mutation kho Thái; TR03 do Vương chặn sửa applied fee, test tích hợp không tạo trigger bản sao.
- [ ] Chạy DongPublishEventIT + SQL DONG-09; evidence DONG-09 gồm Java→SP12/TX12/TR01/02/05, context cleanup test, concurrency; bàn giao Vương UI-20 và public catalog DONG-10, Liêm/Thái event bán.

## DONG-10 — Query/View/F03, danh mục và UI-01/02

**Mốc/phụ thuộc:** M1; DONG-02/07/09; LIEM-01 Hold contract để UI-02 gọi khi M2; Thái F07 qua CheckInService. **Mục tiêu:** public chỉ thấy published, query nội bộ đúng scope, tồn khu đúng ngồi/đứng, search/page và chọn chỗ chạy bằng dữ liệu DB. **Nguồn:** spec §9 UI-01/02/12/15/§14 V01/V02/F03/IX01/02/15; D07-T04.

**Tạo:** `database/migrations/0300_V01.sql`, `0300_V02.sql` cùng migrations; `database/migrations/0350_F03.sql` (ngoại lệ thứ tự được đăng ký trước lần áp dụng đầu tiên vì F03 đọc V02), `database/tests/dong/DONG-10.sql`; `src/main/java/vn/ticketscenter/repository/event/EventQueryRepository.java`, `EventCategoryRepository.java` cùng repository/event; `src/main/java/vn/ticketscenter/controller/event/EventCategoryServlet.java`; `src/main/webapp/WEB-INF/views/event/list.jsp`, `detail.jsp` cùng views/event; `src/main/webapp/assets/js/public-events.js`, `event-detail.js`; `src/test/java/vn/ticketscenter/acceptance/DongEventQueryIT.java`, `src/test/java/vn/ticketscenter/browser/DongPublicEventsBrowserIT.java`. **Modify:** EventQueryService/EventCategoryService API M0; EventServlet/OrganizationServlet/ZoneServlet GET dispatch. **Consumes/Produces:** EventQueryService catalog signatures; F03 `dbo.fn_GetZoneAvailability(@zoneId uniqueidentifier)` returns capacity/held/sold/available một row, unknown empty; V01/V02 one-row grain. Reviewer Liêm/Vương.

**HTTP public:** GET `/events?keyword=&categoryId=&from=&to=&sort=startTimeAsc&page=1&pageSize=20` →200 UI-01/Page JSON; `from/to` UTC ISO-8601 ánh xạ EventFilter.fromUtc/toUtc, giữ tên query API-MAP. GET `/events/{id}` →200 UI-02/EventDto; GET `/events/{id}/zones` →200 List ZoneDto; GET `/zones/{id}/seats?...` →200 Page SeatDto. Unknown/nonpublished public404, public status filter DRAFT400. GET `/event-categories` →200 active List EventCategoryDto JSON (không trang riêng). Internal organization event routes MANAGER; `listAdmin/get` ADMIN callable qua Vương. `Accept:application/json` dùng cùng query HTML; default HTML chỉ route có JSP.

- [ ] V01 PUBLISHED only, minPrice của zone đủ điều kiện, không join nhân Event; V02 SEATED count Seat trạng thái, STANDING counters, zero-zone/empty seat đúng số0; F03 cùng công thức V02, đọc không khóa/chuyển trạng thái giữ vé.
- [ ] Repository bind keyword/category/from/to/org, filter thời điểm startTime trong `[fromUtc,toUtc)`, unknown category cho empty, reversed range400, page0/pageSize101400; sort `startTimeAsc/startTimeDesc/minPriceAsc/minPriceDesc/titleAsc` + id, null minPrice stable cho draft internal. `%`/`_` keyword escape như ký tự thường; sort injection400, không ghép tên cột từ input.
- [ ] Public draft/rejected/pending/cancelled404 dù biết UUID; internal manager đúng org thấy đủ trạng thái/reason, manager A không lấy eventB bằng nested route404; buyer orders history đọc qua Service Liêm, không mở public cancelled để phục vụ lịch sử.
- [ ] UI-01 filter/category/cards/min price/sale status/empty/loading/error, page stable; UI-02 ghế AVAILABLE selectable, HELD/SOLD disabled, standing quantity1…8, tổng chọn<=8, cùng Event; ghế chọn chỉ trạng thái client trước giữ. POST `/holds` gửi `{eventId,selections:[{zoneId,seatId?,quantity}]}` cho HoldServlet Liêm, nhận201 HoldDto rồi chuyển UI-04; 401 login, unverified403 dẫn UI-03,409 refresh tồn, mất mạng đọc `/me/hold` trước retry. Không cài HoldService/SP02 trong Đông.
- [ ] Fixture Event A khu SEATED2×3/standing3; giữ A1 và standing2 tại M2 ⇒ V02/F03 seat available5/standing available1; sold/refund biến động đúng ở DONG-12. Zone unknown F03 empty →HTTP404, không hàng giả capacity0.
- [ ] Chạy DongEventQueryIT + SQL DONG-10 + browser search/detail/mobile, ghi real query V01/V02/F03 và UI→HoldService nếu M2 đã có; evidence DONG-10. Bàn giao EventDto/listAdmin/get cho Vương, query/capacity cho Liêm/Thái, V02 cho ReportService.

## DONG-11 — UI-10 và adapter wildcard ghép coupon/report/check-in

**Mốc/phụ thuộc:** M1 hoàn thiện menu/list; M2/M3 nghiệm thu adapter khi Service Liêm/Thái/Vương có. DONG-03/05/06/10; KHANH-02 HTTP helpers, KHANH-06 auth, KHANH-11 layout và phần membership của KHANH-13 MeServlet ở M1. **Mục tiêu:** dashboard chọn membership hiện hành, dẫn đủ công việc, Organization/EventServlet dispatch đúng một owner cho mọi route. **Nguồn:** spec §4/§9 UI-10/11/12/13/14–17; TEAM §3.1–3.2.

**Tạo:** `src/main/webapp/WEB-INF/views/organization/dashboard.jsp`, `src/main/webapp/assets/js/organization-dashboard.js`, `src/test/java/vn/ticketscenter/acceptance/DongDelegatedRoutesIT.java`, `src/test/java/vn/ticketscenter/browser/DongOrganizationNavigationBrowserIT.java`. **Modify:** OrganizationServlet/EventServlet; JSP/JS Đông bổ sung shared navigation. **Consumes:** MembershipService.listForUser; OrganizationService.get; `ReportService.organizationOverview(actor,organizationId):OverviewDto`; `organizationReport(actor,organizationId,ReportFilter):ReportDto`; CouponService.list/create chữ ký TEAM; CheckInService.window/history/listEvents chữ ký TEAM. Các CouponFilter/Command/Dto Liêm, Overview/ReportFilter/Dto Vương, CheckInFilter/Log/WindowDto Thái sở hữu fields; không định nghĩa bản sao. **Produces:** adapters servlet gọi Service đúng owner, giữ HTTP chung. Reviewer Liêm/Thái/Vương.

**HTTP/quyền/đầu ra:** GET `/organizations/{id}` default UI-10, JSON OrganizationDto; GET `/organizations/{id}/overview` →200 OverviewDto (MANAGER); GET `/organizations/{id}/coupons` →200 UI-14 JSP của Liêm/Page CouponDto, POST cùng route CouponCommand →201 CouponDto; GET `/organizations/{id}/reports` →200 UI-17 JSP Vương/ReportDto cùng ReportFilter; GET `/organizations/{id}/check-in-events` →200 mặc định HTML UI-15 dùng JSP Thái, `Accept: application/json` trả Page<EventDto>; GET `/events/{id}/check-in-window` →200 CheckInWindowDto JSON; GET `/events/{id}/check-ins` →200 Page<CheckInLogDto> chỉ JSON, không forward JSP. UI-16 ở `/check-ins?organizationId=...&eventId=...` do Thái render và tải lịch sử qua JSON theo TEAM-CONTRACT §3.5. Coupon/report MANAGER đúng org; check-in MANAGER hoặc CHECK_IN_STAFF đúng org. `/check-ins` page/scanning và `/reports/export` giữ chủ Thái/Vương. Filter và HTTP validation chi tiết do file chủ Service quy định, adapter không thêm role/công thức riêng.

- [ ] Tạo dispatch test cho từng subroute với request/body/query/Accept/CSRF; unknown path404, sai method405, đường dẫn UUID sai400; không duplicate Servlet pattern giữa `/organizations/*`/`/events/*` và các owner khác.
- [ ] Dashboard sử dụng `/me/memberships` hiện hành, chọn org bằng URL/picker không cấp quyền; MANAGER A thấy menu thành viên/event/coupon/report, CHECK_IN B đi UI-15 chỉ chức năng được phép; switched org fetch dữ liệu đúng, không giữ KPI org cũ.
- [ ] Delegate gọi Service owner với ActorContext server; `eventId` check-in được Thái derive org và kiểm quyền; không query trực tiếp SP04/V05/F07, không tạo F07. Report query dùng công thức/filter Vương, không cộng số tiền tại JSP.
- [ ] Navigation UI-09→membership→UI-10→11/12/13; form/event controls theo status; tích hợp layout header/footer/notifications và api-client Khánh, tiếng Việt/loading/empty/error/success/focus/mobile, không sao chép prototype mock data.
- [ ] Revoke membership trong tab khác ⇒ request kế tiếp403/404 và xóa context không còn hiệu lực; backend tái kiểm dù UI vẫn hiện menu; direct JSON đúng envelope, default HTML đúng JSP không lộ entity.
- [ ] Chạy DongDelegatedRoutesIT và browser flow với Service/SQL thực; stub chỉ chứng minh adapter và phải ghi chưa đạt nghiệp vụ; evidence DONG-11 có UI-10 cùng route ownership table; bàn giao Khánh navigation, Liêm coupon, Thái check-in, Vương reports.

## DONG-12 — Hợp đồng kho, hoàn/hủy và các race xuyên module

**Mốc/phụ thuộc:** M2/M3; DONG-01/04/05/07/09/10; Liêm Hold/Order/Payment/Ticket SP02/07/09 thật; Thái Refund/Cancellation SP10/11/13/17 thật; Vương rule/TR03/AuditLog. **Mục tiêu:** chứng minh methods Đông và guard layout không chặn mutation kho hợp lệ, không nhân chỗ/tiền khi retry và cancel. **Nguồn:** spec §6.4/6.6/6.8–6.10/§8.3/§14 TX02/07/09/10/11/12/13/17; quyền sở hữu TEAM.

**Tạo:** `src/test/java/vn/ticketscenter/acceptance/DongInventoryContractIT.java`, `database/tests/dong/DONG-12.sql`, `docs/backend/dong-handoff.md`. **Modify:** chỉ Model/Service/Repository Đông nếu test tìm lỗi thuộc Đông; gửi owner sửa SP02/07/09/10/11/13/17, không chép SP sang Đông. **Consumes:** Zone/Seat/Event signatures DONG-01, fixture registry, Services owner; **Produces:** acceptance tests/evidence inventory và Event.cancel cho Liêm/Thái; reviewer Liêm/Thái.

**HTTP:** test API thật `/holds`, `/orders`, payment status, refund/cancel qua routes owner. Input hold mẫu TEAM: Event `10000000-0000-0000-0000-000000000001`, zone `20000000-0000-0000-0000-000000000001`, seat `30000000-0000-0000-0000-000000000001`, quantity1; buyerA session runtime. Kỳ vọng201 ACTIVE/unitPrice"200000"/Seat HELD/expiresAt03:10 UTC; buyerB race same seat409. Cancel admin gọi CancellationService Thái, không thêm cancel endpoint manager dưới EventServlet.

- [ ] Test hold seated và standing phối hợp: hold/sell/release/return methods tương ứng, wrong item/ticket/allocation reject; SQL inventory counters không âm, held+sold<=capacity; không dùng EntityManager dirty state cũ sau SP.
- [ ] Hai buyer giữ ghế cuối/standing quantity3 đồng thời: chỉ đủ capacity thành công; item sau lỗi rollback cả selection; hủy/expiry worker đua trả một lần; TR01/02 cho mutation price/inventory nhưng giữ layout bất biến.
- [ ] Snapshot price200000→250000 giữ cũ/Order cũ200000, Hold mới250000; publish/price/hold race theo khóa; phản hồi network bị mất đọc Hold/current state trước retry, không nhân kho.
- [ ] Refund 0đ và có tiền hoàn SUCCEEDED trả SOLD một lần; FAILED/UNKNOWN/REQUESTED không trả kho; reject khôi phục vé hợp lệ không đổi sold; Thái quyết định hoàn, Đông chỉ sửa returnSoldStanding/returnToInventory nếu cần.
- [ ] Event.cancel đúng trước start: CANCELLED ngay, mua/check-in bị chặn dù worker chưa hoàn; worker SP17 restart không nhân refund/trả kho; USED là ngoại lệ, không tự hoàn. Hủy tại đúng startTime409. Public query cancelled404, lịch sử Order/ticket qua Liêm vẫn xem được.
- [ ] Fault sau kho mutation trước audit/outbox/kết quả mỗi use case rollback theo SP chủ; outer transaction/connection và race publish vs layout/cancel vs payment được ghi barrier/timeout/thứ tự khóa. Không đổi ownership transaction để test xanh.
- [ ] Chạy DongInventoryContractIT + SQL DONG-12 trên DB thật; evidence DONG-12 ghi caller/SP/method/DB before-after và các task owner phụ thuộc; chưa có provider chỉ adapter evidence, không claim VNPAY thật. Bàn giao Liêm/Thái/Vương trước M3 gate.

## DONG-13 — Ba index, quyền miền, hồ sơ và nghiệm thu M4

**Mốc/phụ thuộc:** M4; DONG-01…12, VUONG-01 fixture/manifest + CI/benchmark/browser/runbook/grants, Khánh tooling. **Mục tiêu:** bản ghép đủ SQL/UI miền Đông, thực dùng object và fresh-clone chạy theo runbook; evidence/query plans/hồ sơ nộp tái tạo được. **Nguồn:** spec §12/§14.8–14.14, TEAM M4.

**Tạo:** `database/migrations/0150_IX01.sql`, `0150_IX02.sql`, `0150_IX15.sql` cùng migrations; `database/benchmarks/dong/IX01.sql`, `IX02.sql`, `IX15.sql` cùng benchmarks/dong; `database/seeds/dong-benchmark.sql`; `database/tests/dong/DONG-13.sql`; `src/test/java/vn/ticketscenter/acceptance/DongModuleAcceptanceIT.java`; `docs/backend/dong-sql-usage.md`; `docs/baocao/dong.md`; `docs/evidence/dong/DONG-13.md`. **Modify khi triển khai phối hợp:** catalog/manifest entry và DTO/route catalog của Đông trong `docs/backend/dong-handoff.md`; Vương ghép database README/0700 grants/hồ sơ, không Đông sửa file của Vương. Reviewer Vương/Khánh.

**Consumes/Produces:** query của DONG-05/10, Vương execution-plan/fixture runner; emits đúng IX01 `(status,startTime,id)` INCLUDE categoryId,organizationId,title,coverImageUrl; IX02 `(zoneId,status,id)` INCLUDE rowName,seatNumber; IX15 `(organizationId,status,startTime,id)` INCLUDE title,categoryId. R02 ca CRUD bản nháp/member/query đúng org, phối hợp R01 public/R03 check-in/R04 approve/publish/TECH worker, Vương sở hữu role/login DDL. Không endpoint benchmark/reset production.

- [ ] Seed benchmark riêng reproducible: nhiều org/Event phân bố status/time/category, seated nhiều ghế/status; query cùng params/result trước/sau, actual plans/STATISTICS IO,TIME/multiple runs/row counts/write cost/size. IX01 không claim tối ưu `%keyword%`; IX15 so với IX01 để chứng minh org filter; không ép INDEX hoặc cam kết phần trăm giảm trước đo.
- [ ] Test grants direct SQL và HTTP: buyer không SP01/12; check-in không CRUD/member/report; manager A không event/memberB; ADMIN đúng approve/publish; revoke/deny thật và actor/session context pool không bị lẫn. Không dùng db_owner/sysadmin làm principal ứng dụng.
- [ ] Fresh database chạy manifest/schema→FK→dependency V02→F03→SP/trigger→grants→seed; tạo tổ chức→approve→member→Event→zone/cover→submit→publish qua API thật; UI01/02/09/10/11/12/13 desktop/mobile; no placeholder/mock coi PASS. Build/WAR/DB startup/redeploy storage theo runbook Khánh/Vương.
- [ ] Chạy mục tiêu `mvn -B verify`, `mvn -B -Psqlserver-it verify`, `mvn -B -Pbrowser-it verify` và SQL DONG-13 bằng auth đã khóa; nếu thiếu SQL/provider/online config ghi chính xác BLOCKED, không skip để báo PASS. Test regression chỉ mở rộng khi lỗi/chưa chắc/đổi mới cần.
- [ ] Đối chiếu đủ C02/03/04/05/10, V01/02/10, SP01/12, F03, TR01/02/05/06, IX01/02/15, TX01/12 có real application trace/evidence; F07/TR03/SP13 thuộc Thái/Vương và các caller delegate đã nối. Không đếm persistence/DTO thành lớp nghiệp vụ thứ16.
- [ ] Phần báo cáo Đông giải thích ERD/table/category/map quyền, model methods, SQL/transactions/locks, UI/ảnh màn hình ứng dụng thật, benchmark/case/giới hạn; Vương ghép hồ sơ sáu chương50–100trang/slide/demo. Không tự sinh sơ đồ thiếu hoặc chụp prototype làm minh chứng chạy.
- [ ] Evidence DONG-13 ghi commit bản ghép, dependency versions, PASS/FAIL/BLOCKED từng task, risk/blocker còn lại, hướng dẫn chạy/fixture/source paths; bàn giao Vương acceptance/hồ sơ và Khánh build. Chỉ đóng module khi mọi gate cần thiết có bằng chứng, không dựa tên task hoặc số lượng SQL.

## Ma trận đóng phạm vi và lệnh khởi đầu cho AI

| Đầu ra | Task/gate |
|---|---|
| Organization methods + Event/Zone/Seat **đủ methods diagram**, MembershipAccess/DTOs | DONG-01 M0 |
| Persistence category/map và C02/03/04/05/10, cross-FK handoff | DONG-02 M0 |
| Organization request/reject/UI-09; SP01/TX01 approve+initial fee+manager | DONG-03/04 M1 |
| MembershipService/V10/TR06/UI-11, current authorization | DONG-05 M1 |
| Event draft thật/UI-12; layout/price/UI-13; storage/cleanup | DONG-06/07/08 M1 |
| Submit/publish/reject/SP12/TX12/TR01/02/05 | DONG-09 M1 |
| V01/V02/F03/EventQuery/category/UI-01/02 | DONG-10 M1, hold button M2 |
| UI-10/shared navigation/wildcard delegates | DONG-11 M1→M3 |
| Kho/price/refund/cancel/race integration | DONG-12 M2/M3 |
| IX01/02/15/grants ca miền/build/SQL/browser/hồ sơ | DONG-13 M4 |

> **Prompt nhận một task:** “Triển khai DONG-NN trong docs/tasks/dong.md. Đọc spec.md, XML docs/classdiagram/diagram.md, TEAM-CONTRACT.md và tất cả task dependency trước. Kiểm tra thực trạng file; tạo phần được giao theo exact signatures/paths, không đổi ownership. Viết test thất bại có ý nghĩa, triển khai, chạy unit/SQL Server/browser cần thiết khi tooling đủ. Ghi docs/evidence/dong/DONG-NN.md theo commit/expected/actual/exit code/DB trước-sau; báo PASS chỉ với bằng chứng và liệt kê BLOCKED/owner/dependency. Không tự sửa route/service/SP của người khác, commit/push/merge/deploy cần nằm trong ủy quyền phiên triển khai.”

- [ ] Khi giao việc thực tế, ghi Đông/người review và trạng thái từng task; mọi checkbox tài liệu này ban đầu chưa hoàn thành.
- [ ] Nếu đổi một contract, owner cập nhật TEAM/file mình/caller/tests/API-MAP/COVERAGE trong cùng thay đổi được nhóm duyệt; không tạo DTO giống nhau dưới tên khác.
- [ ] Lưu cùng spec/diagram/TEAM/năm file nhiệm vụ khi đưa AI/GitHub; các link nguồn đã có, path mã/test/SQL là yêu cầu phải tạo ở phiên triển khai.

## Quy trình Git bắt buộc cho Đông

Đọc toàn bộ [GIT-WORKFLOW](GIT-WORKFLOW.md) trước triển khai. Tính năng và sửa lỗi đều dùng feature theo task, mặc định từ develop đã cập nhật và kiểm, PR vào develop. Chỉ ngoại lệ sửa bản main đã bàn giao khi develop còn việc chưa nghiệm thu mới rẽ feature từ main, PR main rồi đồng bộ main → develop theo GIT-WORKFLOW §7. Develop là nhánh tích hợp; main giữ bản đã nghiệm thu qua PR develop → main và tag sau kiểm. Không dùng nhánh release riêng. Chỉ stage file thuộc nhiệm vụ. Hoàn thành mỗi đơn vị có kiểm chứng thì commit code/test/migration/tài liệu liên quan, ghi footer `Task-Id`, lệnh/kết quả và evidence. Task lớn có nhiều commit/PR con; commit nền không đồng nghĩa toàn task đã đạt. Không chờ hoàn thành cả module mới commit.

Bảng dưới là tên nhánh và subject khởi điểm đã gắn đúng chức năng. Khi chỉ sửa lỗi dùng `fix`, chỉ thêm test dùng `test`, chỉ tài liệu dùng `docs`; mô tả phải phản ánh nội dung thực. Subject dài được rút gọn rõ nghĩa theo khuyến nghị72 ký tự của nhóm, không đổi task ID. PR một task mặc định vào develop, ngoại lệ base main theo §7; reviewer theo người nhận đầu ra; tiền/quyền/transaction cần hai reviewer chuyên môn. Build/SQL/browser thiếu môi trường ghi BLOCKED, không đóng task từ commit hoặc mock.

| Task | Nhánh chức năng | Subject commit khi triển khai đầy đủ hành vi |
|---|---|---|
| DONG-01 | `feature/dong/dong-01-domain-contracts` | `feat(event): define organization and event domain contracts` |
| DONG-02 | `feature/dong/dong-02-event-schema` | `feat(db): create organization and event schema constraints` |
| DONG-03 | `feature/dong/dong-03-organization-request` | `feat(organization): submit organization profiles and request history` |
| DONG-04 | `feature/dong/dong-04-organization-approval` | `feat(organization): approve orgs with initial policy and manager` |
| DONG-05 | `feature/dong/dong-05-membership` | `feat(organization): manage current roles and protect the last manager` |
| DONG-06 | `feature/dong/dong-06-draft-events` | `feat(event): manage draft events and organization event lists` |
| DONG-07 | `feature/dong/dong-07-zone-layout` | `feat(event): configure zones seats and published price changes` |
| DONG-08 | `feature/dong/dong-08-cover-storage` | `feat(event): store event covers and clean unreferenced images` |
| DONG-09 | `feature/dong/dong-09-publish-event` | `feat(event): publish events with policy and layout guards` |
| DONG-10 | `feature/dong/dong-10-public-catalog` | `feat(event): query public events and zone availability` |
| DONG-11 | `feature/dong/dong-11-organization-dashboard` | `feat(organization): delegate organization reports coupons and check-in` |
| DONG-12 | `feature/dong/dong-12-inventory-integration` | `test(event): verify inventory transitions across sales and refunds` |
| DONG-13 | `feature/dong/dong-13-event-benchmarks` | `test(db): verify event indexes permissions and handoff` |

Trước commit: kiểm ownership/diff → chạy checks đúng task → `git diff --check` → stage đường dẫn cụ thể → review staged diff → commit theo mẫu chung. Báo cáo cuối của thành viên/AI phải ghi nhánh, commit, task, tests/evidence và dependency chưa ghép. Push/merge/deploy và thay cấu hình GitHub theo quyền được giao; bộ kế hoạch này không tự thực hiện các bước đó.
