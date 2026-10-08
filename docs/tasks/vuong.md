# Nhiệm vụ của Vương — quản trị tài chính, báo cáo và tích hợp TicketsCenter

## Prompt khởi đầu — copy từ đây

Copy **nguyên nội dung trong khối bên dưới** vào AI có quyền đọc repository TicketsCenter. Prompt đã gắn đúng thành viên/file; không cần copy toàn bộ 76 task vào cửa sổ chat. Nếu AI không có quyền đọc repository, phải cung cấp các tài liệu được liệt kê trước khi triển khai.

```text
Tôi phụ trách phần của Vương trong dự án TicketsCenter.
Hãy triển khai nhiệm vụ của tôi theo docs/tasks/vuong.md.

Trước khi viết mã:
1. Đọc toàn bộ spec.md và docs/classdiagram/diagram.md, gồm thuộc tính,
   phương thức và quan hệ trong XML diagram.
2. Đọc toàn bộ docs/tasks/TEAM-CONTRACT.md từ đầu đến cuối, không bỏ mục
   hoặc chỉ đọc phần cá nhân; đọc đầy đủ docs/tasks/CONVENTIONS.md,
   API-MAP.md, COVERAGE.md và GIT-WORKFLOW.md.
3. Đọc toàn bộ docs/tasks/vuong.md. Đọc các phần nhiệm vụ thành viên khác
   cung cấp đầu vào hoặc nhận đầu ra liên quan; kiểm mã nguồn, cấu hình,
   trạng thái Git và bằng chứng thực tế trong repository.
4. Trước thay đổi đầu tiên, báo ngắn gọn phiên bản nguồn đã đọc, phạm vi
   sở hữu, hợp đồng liên miền, nhiệm vụ sẽ làm và tình trạng phụ thuộc.
   Tiếp tục công việc đã được giao nếu đủ đầu vào, không dừng để hỏi lại
   xác nhận cho các bước triển khai thông thường.

Bắt đầu bằng nhiệm vụ đầu tiên chưa hoàn thành và đủ phụ thuộc.
Nếu tôi chỉ định mã nhiệm vụ cụ thể trong tin nhắn tiếp theo, ưu tiên mã đó.
Chỉ sửa file thuộc phạm vi Vương; phần do người khác sở hữu phải phối hợp
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

Muốn giao một task cụ thể, thêm sau prompt: `Nhiệm vụ lần này: VUONG-NN.` Thay NN bằng mã thật trong file; không suy ra task hoàn thành từ lịch ngày, checkbox hoặc tên commit.

## Điều kiện bắt buộc trước khi triển khai

**Bắt buộc với cả thành viên và AI:** trước nhiệm vụ đầu tiên, phải đọc **toàn bộ [TEAM-CONTRACT.md](TEAM-CONTRACT.md), từ đầu đến cuối, không bỏ mục, không chỉ đọc phần của mình hoặc bản tóm tắt**. Đồng thời đọc đầy đủ [CONVENTIONS.md](CONVENTIONS.md), [API-MAP.md](API-MAP.md), [COVERAGE.md](COVERAGE.md) và [GIT-WORKFLOW.md](GIT-WORKFLOW.md), cùng spec/diagram được dẫn trong phần nguồn chuẩn.

- [ ] Đã đọc toàn bộ hợp đồng chung và tài liệu quy ước; hiểu ownership Model/Servlet/SQL, DTO/Service/API, quyền/principal, transaction/locking, outbox/SPI, migrations/fixture, Git và tiêu chí nghiệm thu.
- [ ] Ghi trong kế hoạch bắt đầu hoặc Draft PR: đường dẫn hợp đồng, commit SHA nguồn nếu có, task nhận, đầu vào/đầu ra liên miền và các ràng buộc áp dụng. Nếu hợp đồng còn chưa commit, ghi SHA nền và trạng thái working tree; không bịa phiên bản.
- [ ] Đối chiếu task với hợp đồng hiện hành và kiểm file/Service thuộc người nào. Nếu có mâu thuẫn hoặc thiếu hợp đồng bắt buộc, ghi rõ điểm thiếu và chủ cung cấp; chưa triển khai phần phụ thuộc đó.
- [ ] Khi tiếp tục phiên làm việc, kiểm hợp đồng có thay đổi từ phiên bản đã đọc không. Có thay đổi thì đọc lại toàn bộ hợp đồng mới và cập nhật caller/test bị ảnh hưởng trước khi tiếp tục phần liên quan.

Không bắt đầu viết mã cho nhiệm vụ khi chưa hoàn thành việc đọc và đối chiếu trên. Reviewer phải kiểm xác nhận này trước khi chấp nhận PR; một câu “đã đọc” không thay việc chứng minh đầu ra tuân hợp đồng.

> Dành cho thành viên hoặc AI: đọc nguồn chuẩn và hợp đồng chung, làm từng task chưa hoàn thành theo phụ thuộc; có thể dùng skill executing-plans khi môi trường có skill đó. Không cần cài skill để đọc hoặc triển khai kế hoạch này. Bộ nhiệm vụ là kế hoạch tương lai; các đường dẫn mã ứng dụng dưới đây cần tạo khi triển khai.

**Mục tiêu:** cung cấp quản trị dùng đúng nghiệp vụ của các thành viên, hoa hồng/đối soát/chi trả đúng tiền, báo cáo không nhân dòng; ghép được một WAR cùng database có đủ bằng chứng nghiệm thu và hồ sơ môn học.

**Kiến trúc:** một ứng dụng Servlet → Service → Model/Repository → JPA/SQL Server. AdminServlet chỉ điều phối HTTP; quyết định tổ chức/sự kiện/hoàn dùng Service của Đông/Thái. CommissionRule và Settlement chứa hành vi của diagram, SP14–SP16 sở hữu ghi tài chính; dịch vụ mô phỏng chạy ngoài transaction.

**Công nghệ:** theo spec: JDK25, Tomcat11.0.25/Servlet6.1, JSP4/JSTL3, Bootstrap, JPA/Hibernate, SQL Server local/Azure SQL; VNPAY Sandbox, hoàn/chi mô phỏng. Khánh khóa dependency/build; Vương không tự thay stack hoặc thêm framework.

**Nguồn:** [spec](../../spec.md) §4,5,6.11–12,8–12,14; [diagram](../classdiagram/diagram.md); [hợp đồng năm thành viên](TEAM-CONTRACT.md); [CONVENTIONS](CONVENTIONS.md); [API-MAP](API-MAP.md); [COVERAGE](COVERAGE.md). Task ngày17–21 chỉ là tham khảo backend, không là lịch đã hoàn thành. Ngày bắt đầu và hạn thực tế phải được nhóm xác nhận khi nhận việc.

## 1. Phạm vi và ràng buộc chung

- Sở hữu CommissionRule/Settlement; SettlementOrderSnapshot, SettlementTransferLog và AuditLog là persistence, không thêm lớp nghiệp vụ báo cáo/Payout/SettlementItem.
- Sở hữu UI17–23 và phần chọn tổ chức trong UI14 admin; AdminServlet `/admin/*`, ReportExportServlet `/reports/export`. UI19/20/21 gọi Service Đông/Thái, không gọi SP của họ trực tiếp từ controller.
- SQL: C16–C19; V03/V04/V08; SP14/SP15/SP16; F02/F05/F09/F10; TR03/TR04/TR09/TR10; IX07/IX12/IX13/IX14; TX14/TX15/TX16; DDL/grants R01–R04 và quyền kỹ thuật. Các SQL còn lại do người được ghi ở TEAM-CONTRACT triển khai, Vương chỉ ghép/catalog/review.
- Money/Decimal trong chữ ký diagram ánh xạ BigDecimal; HTTP chuỗi đồng nguyên, SQL decimal(19,0). Phí HALF_UP đến đồng, giới hạn remainingAmount; remaining=0 thì phí0. Không dùng double và không tự chọn tỷ lệ phí kinh doanh.
- Snapshot sau confirm bất biến; paid+pending<=net; chỉ paid=net và pending=0 mới PAID. `netPayable`/`availableToPay` dẫn xuất; Refund/Payment bù trừ tách khỏi doanh thu vé/hoa hồng.
- Kiểm current ADMIN/manager và quyền theo tổ chức ở Service/query; R04 không cho browser tự xác nhận thu/hoàn/chi. Giữ các format, API, quyền, transaction và evidence tại TEAM-CONTRACT. Không vẽ ảnh/sơ đồ còn thiếu; giữ placeholder để tác giả chỉnh bằng công cụ chuyên dụng.
- Các file DOCX thiết kế đang có giữ format gốc, không được coi là báo cáo DBMS sáu chương đã đạt. VUONG-17 tạo hồ sơ nộp riêng, lấy dữ liệu thực chạy và giữ các bản thiết kế.

## 2. Các điểm cần review và ca kiểm chứng

| Điều kiện dễ sai | Task và hành vi cần chứng minh |
|---|---|
| Một Order có nhiều Payment/Refund/attempt | VUONG-05/09: mỗi Order một dòng tài chính; thu bù trừ không thành doanh thu; log FAILED không nhân hoàn |
| Confirm đua kết quả thu/hoàn hoặc sửa snapshot | VUONG-07: cùng thứ tự khóa với Liêm/Thái, blocker và recalc cùng TX15, không chốt tổng cũ |
| Hai payout/same-id sau lỗi mạng | VUONG-08: một reservation/kết quả mỗi payoutId, amount khác409, tổng không vượt net |
| Manager sửa organizationId/filter/export | VUONG-09/10/15: xác minh phạm vi từ DB, JSON/JSP/CSV cùng quyền và dữ liệu |
| Restart/thiếu DB/quota hoặc schema lệch | VUONG-02/16: checksum và fail rõ, không skip test, dữ liệu bền vững và trạng thái chưa rõ không bị báo thất bại để thu lại |

## 3. File và hợp đồng bàn giao

Đường dẫn tính từ gốc repo. Vương chỉ sửa các file mình sở hữu; đề nghị chủ miền sửa file của họ và review PR phối hợp.

| Nhóm | File chính phải tạo |
|---|---|
| Model | `src/main/java/vn/ticketscenter/model/settlement/CommissionRule.java`, `Settlement.java`, các enum/kiểu giá trị cùng nhóm |
| Service | `service/settlement/CommissionService.java`, `CommissionPolicyValidator.java`, `SettlementService.java`; `service/report/ReportService.java`; `service/audit/AuditService.java` dưới package gốc |
| Repository | `repository/settlement/CommissionRepository.java`, `SettlementRepository.java`; `repository/report/ReportRepository.java`; `repository/audit/AuditRepository.java` |
| DTO | `dto/settlement/CommissionPolicyCommand.java`, `CommissionRuleDto.java`, `SettlementDto.java`, `SettlementOrderDto.java`, `PayoutCommand.java`, `PayoutDto.java`, `SettlementBlockerDto.java`; `dto/report/ReportFilter.java`, `ReportDto.java`, `OverviewDto.java`; `dto/audit/AuditFilter.java`, `AuditDto.java` |
| Controller/view | `controller/admin/AdminServlet.java`, `controller/report/ReportExportServlet.java`; `WEB-INF/views/admin/{overview,organizations,events,refunds,settlement,reports,audit}.jsp`; `WEB-INF/views/report/organization-report.jsp` |
| Schema | `database/migrations/0050_settlements_audit.sql`, `0100_cross_domain_keys.sql`, `0700_roles_grants.sql`; object files theo TEAM-CONTRACT |
| Chạy/kiểm kê | `database/README.md`, `database/migrations/manifest.csv`, `database/tests/inventory.sql`, `database/seeds/test-fixtures.sql`, `database/benchmarks/`; `docs/backend/{database-runbook,security-matrix,sql-usage,index-benchmark,deployment,acceptance}.md` |

### 3.1. DTO và chữ ký thống nhất

`ActorContext`, `Page<T>`, `PageRequest`, `ClockProvider`, `TransactionRunner` từ Khánh; model Event/Order/Refund từ Đông/Liêm/Thái. Không tạo bản sao. Các type mới bên dưới là DTO/enum kỹ thuật, không tính vào 15 lớp.

- `CommissionPolicyCommand(BigDecimal ratePercent, BigDecimal fixedFee, Instant effectiveFrom, Instant effectiveTo)`; nonnegative, thời hạn hợp lệ, không tự đặt cap phần trăm phí.
- `CommissionRuleDto(UUID id, UUID organizationId, BigDecimal ratePercent, BigDecimal fixedFee, Instant effectiveFrom, Instant effectiveTo, boolean applied)`.
- `SettlementDto(UUID id, UUID eventId, SettlementStatus status, BigDecimal grossRevenue, BigDecimal totalRefund, BigDecimal totalCommission, BigDecimal netPayable, BigDecimal paidAmount, BigDecimal pendingAmount, BigDecimal availableToPay, Instant confirmedAt, Page<SettlementOrderDto> orders)`; id nullable khi Event không có đơn hợp lệ, các tổng báo cáo bằng 0, không tạo entity giả.
- `SettlementOrderDto(UUID orderId, BigDecimal grossAmount, BigDecimal refundAmount, BigDecimal commissionAmount, BigDecimal netAmount)`; một dòng/Order.
- `PayoutCommand(UUID payoutId, BigDecimal amount, String reference)`; amount>0, payoutId ổn định; không có trường result/status từ browser.
- `PayoutDto(UUID payoutId, UUID settlementId, BigDecimal amount, TransferStatus status, String reference, Instant paidAt)`; TransferStatus kỹ thuật PENDING/SUCCEEDED/FAILED, result đầu vào Model vẫn PayoutResult SUCCEEDED/FAILED.
- `SettlementBlockerDto(String type, UUID objectId, String message, String href)`; projection F09, không entity blocker; href chỉ route nội bộ được phép.
- `ReportFilter(UUID organizationId, UUID eventId, ReportMetric metric, Instant fromUtc, Instant toUtc, PageRequest page)`; metric COHORT_REVENUE/CASH_FLOW/INVENTORY/CHECK_IN. Manager bị khóa organizationId đã xác minh; admin có thể chọn org hoặc toàn hệ thống. from/to phải cùng có hoặc cùng thiếu; thiếu dùng 30 ngày đến ClockProvider.now(), ghi rõ trên UI/CSV; khi truyền giá trị thì from<to, khoảng `[from,to)`. Lựa chọn mặc định 30 ngày là quy ước trình bày, không hạn chế quyền xem lịch sử.
- `ReportDto(ReportFilter filter, Map<String,BigDecimal> moneyTotals, Map<String,Long> counts, Page<ReportRowDto> rows, Instant updatedAt)`; ReportRowDto tách theo metric và ghi schema cột trong API contract, không dùng entity graph. Tổng lấy cả tập lọc, không chỉ trang đang xem.
- `OverviewDto(Map<String,Long> counts, Instant updatedAt)`; các key admin `pendingOrganizations,pendingEvents,pendingRefunds,pendingSettlements`; org `events,orders,tickets,checkedInTickets`.
- `AuditFilter(String aggregateType, UUID aggregateId, String action, Instant fromUtc, Instant toUtc, PageRequest page)`; `AuditDto(UUID id, UUID actorId, String action, String aggregateType, UUID aggregateId, String detail, Instant createdAt)`; actorId nullable hệ thống, detail đã lọc secret.

Các chữ ký TEAM giữ nguyên. Bổ sung:

```java
// Các chữ ký phải tạo trong service tương ứng; không phải code đã có.
CommissionRuleDto CommissionService.create(ActorContext actor, UUID organizationId, CommissionPolicyCommand command);
CommissionRuleDto CommissionService.editUnapplied(ActorContext actor, UUID ruleId, CommissionPolicyCommand command);
Page<CommissionRuleDto> CommissionService.list(ActorContext actor, UUID organizationId, PageRequest page);
SettlementDto SettlementService.get(ActorContext actor, UUID eventId, PageRequest page);
List<SettlementBlockerDto> SettlementService.blockers(ActorContext actor, UUID eventId);
SettlementDto SettlementService.recalculate(ActorContext actor, UUID eventId);
SettlementDto SettlementService.confirm(ActorContext actor, UUID settlementId);
PayoutDto SettlementService.payout(ActorContext actor, UUID settlementId, PayoutCommand command);
Page<PayoutDto> SettlementService.listPayouts(ActorContext actor, UUID settlementId, PageRequest page);
ReportDto ReportService.adminReport(ActorContext actor, ReportFilter filter);
OverviewDto ReportService.adminOverview(ActorContext actor);
void ReportService.export(ActorContext actor, ReportFilter filter, Writer csv);
Page<AuditDto> AuditService.list(ActorContext actor, AuditFilter filter);
AuditDto AuditService.get(ActorContext actor, UUID auditId);
```




Chữ ký viết theo dạng giao diện tài liệu, không chép nguyên `Type Class.method` làm khai báo Java. Chuẩn package gốc `src/main/java/vn/ticketscenter/` áp dụng mọi path viết ngắn ở bảng.

### 3.2. Schema báo cáo bàn giao

Vương tạo `dto/report/ReportRowDto.java` làm giao diện kỹ thuật có metric/eventId/organizationId, cùng các record cùng directory sau. ReportDto.rows chứa subtype theo metric, JSON row có discriminator `metric`; CSV dùng cột cùng schema. Không mở entity graph hoặc field tùy ý.

| Metric/subtype | Các fields hàng ngoài metric/eventId/organizationId | Keys tổng/count |
|---|---|---|
| COHORT_REVENUE / CohortRevenueRowDto | orderId UUID, orderCode String, paidAt Instant, grossAmount/refundAmount/commissionAmount/netAmount BigDecimal | moneyTotals grossRevenue,totalRefund,totalCommission,netRevenue; counts orders,tickets |
| CASH_FLOW / CashFlowRowDto | transactionId UUID (paymentId hoặc attemptId), orderId UUID, direction CAPTURE/REFUND, purpose TICKET/PAYMENT_COMPENSATION, occurredAt Instant, amount BigDecimal | ticketCaptured,compensationCaptured,ticketRefunded,compensationRefunded,netCashFlow; counts captures,refundTransfers |
| INVENTORY / InventoryRowDto | zoneId UUID, zoneName String, zoneType, capacity/held/sold/available long | moneyTotals rỗng; counts capacity,held,sold,available |
| CHECK_IN / CheckInReportRowDto | logId UUID, ticketId UUID nullable, result CheckInResult, scannedAt Instant | moneyTotals rỗng; counts successfulScans,refusedScans |

Cohort commission dùng rule Event đã chọn và remaining tại cutoff báo cáo; gross từ acceptedPayment/đơn0đ, không bù trừ. Báo cáo đối soát CONFIRMED đọc frozen snapshot; metric cohort/cashflow vẫn tuân filter transaction/time riêng. CashFlow purpose TICKET là nhãn báo cáo cho thu hợp lệ/CUSTOMER_REFUND, không thêm enum RefundPurpose. Cột CSV tiền chuỗi nguyên/timeUTC, header Việt được mapping cố định trong api-contract.

Ví dụ một CashFlowRowDto: `{"metric":"CASH_FLOW","transactionId":"80000000-0000-0000-0000-000000000001","orderId":"40000000-0000-0000-0000-000000000001","eventId":"10000000-0000-0000-0000-000000000001","organizationId":"90000000-0000-0000-0000-000000000001","direction":"REFUND","purpose":"TICKET","occurredAt":"2026-10-06T03:00:00Z","amount":"200000"}`; các ID phải được registry VUONG-02 khai báo cho scenario này trước chạy.

### 3.3. Phụ thuộc theo mốc

| Mốc | Vương cung cấp | Nhận từ thành viên khác |
|---|---|---|
| M0 | VUONG-01 models/policy/validator/Audit schema; VUONG-02 manifest/FK/fixture; VUONG-03 quyền nền | KHANH-01 build/tooling, KHANH-02 shared types/HTTP, KHANH-03 transaction, KHANH-04 User/schema, KHANH-06 auth interfaces; DONG-01, LIEM-01 và THAI-01 cung cấp hợp đồng/model/schema nền; dùng đúng ID thực tại các file liên quan |
| M1 | Initial policy cho SP01 và rule cho SP12, admin adapter UI19/20 | Đông approval/publish services; Khánh auth/layout |
| M2 | Catalog/grants từng object, report contracts và browser tooling | Liêm payment/ticket/outbox; Thái check-in |
| M3 | Finance/report/CSV/audit/UI17–23 | Liêm kết quả thu/compensation; Thái nghĩa vụ/kết quả hoàn/cancel |
| M4 | Benchmark/CI/packaging/acceptance/hồ sơ | Evidence/tests/scripts và phần thuyết minh mỗi miền |

`LIEM-01` và `THAI-01` là mốc hợp đồng/model sớm; không yêu cầu hoàn tất toàn file mới cho Vương bắt đầu. Các dependency ghi theo artifact cụ thể; triển khai fixture/models/test công thức được ngay khi nền có.

## 4. Danh sách nhiệm vụ

Quy ước đường dẫn viết ngắn trong các task: Java → `src/main/java/vn/ticketscenter/`; test Java → `src/test/java/vn/ticketscenter/`; SQL object → `database/migrations/`; JSP → `src/main/webapp/WEB-INF/views/`; assets → `src/main/webapp/assets/`. Tên file ở §3 là hợp đồng chính. Mỗi `SQLtestNN` là `database/tests/vuong/VUONG-NN.sql`; mỗi evidence là `docs/evidence/vuong/VUONG-NN.md`. Các profile kiểm thử dưới đây phải được Khánh/Vương tạo trước khi chạy; chưa có profile hoặc DB thì ghi BLOCKED, không ghi PASS.

### VUONG-01 — Hai Model tài chính và hợp đồng nền

**Mục tiêu/mốc:** M0 cung cấp CommissionRule, Settlement, DTO chính sách và validator để Đông có thể duyệt tổ chức cùng chính sách phí trong một transaction. **Nguồn:** spec §5, §6.11, §7, §14.3 và diagram.

**Tạo:** Model/DTO ở §3; `service/settlement/CommissionPolicyValidator.java`; `persistence/settlement/SettlementOrderSnapshotRecord.java`, `SettlementTransferLogRecord.java`; `persistence/audit/AuditLogRecord.java`; `persistence/settlement/MockPayoutProviderRecord.java`; `0050_settlements_audit.sql`. **Test:** `model/settlement/CommissionRuleTest.java`, `SettlementTest.java`, SQLtest01.

**Nhận:** kiểu chung/ClockProvider từ KHANH-01. **Bàn giao:** đầy đủ thuộc tính, phương thức và quan hệ của hai lớp theo diagram; CommissionPolicyCommand/validator cho Đông; C16–C19 và mapping snapshot/log cho các task tài chính.

- [ ] Lập bảng từng thuộc tính/phương thức diagram → file → ca kiểm chứng. Giữ `isEffectiveAt`, `calculateFee`, `recalculate`, `confirm`, `beginPayout`, `recordPayoutResult` đúng chữ ký.
- [ ] Money/Decimal dùng BigDecimal. Lưu trực tiếp grossRevenue/totalRefund/totalCommission/paidAmount/pendingAmount; netPayable/availableToPay là giá trị dẫn xuất.
- [ ] Tạo PK, unique Settlement/Event, snapshot(settlementId,orderId), payoutId; AuditLog có actorId nullable và source USER/SYSTEM. FK liên miền đưa vào VUONG-02. Tổng xuyên hàng phải được SP kiểm, không giả CHECK xuyên bảng.
- [ ] Validator chỉ kiểm dữ liệu/điều kiện chính sách; SP01 của Đông tạo rule, APPROVED và MANAGER ban đầu trong cùng transaction. Cung cấp interface/DTO biên dịch được từ M0; chức năng chưa triển khai phải báo rõ, không trả thành công giả.
- [ ] Test remaining=200001, rate=2.5, fixed=0 → fee=5000; remaining=20, rate=10, fixed=100 → fee=20; remaining=0 → fee=0. Từ chối tiền/tỷ lệ âm và null không hợp lệ.

**Kiểm chứng:** `mvn -B -Dtest=CommissionRuleTest,SettlementTest test`; SQLtest01 assert constraint/unique bằng SQL Server. **Đạt khi:** Model không còn stub, mapping đầy đủ, evidence01 có kết quả thật. Đông review chính sách, Liêm/Thái review tiền nguồn; chưa coi bước này là hoàn thành toàn luồng payout.

### VUONG-02 — Manifest, FK liên miền, fixture và runbook DB

**Mục tiêu/mốc:** M0 dựng được test DB từ năm schema; M4 tái lập bản bàn giao từ repository mới. **Nguồn:** spec §3, §8.2, §14.3, §14.11 và TEAM-CONTRACT.

**Tạo:** `database/README.md`, `database/migrations/manifest.csv`, `0100_cross_domain_keys.sql`, `database/seeds/test-fixtures.sql`, `support/TestFixtures.java`, `docs/backend/database-runbook.md`, `model-map.md`, `normalization.md`; `acceptance/VuongMigrationIT.java`, SQLtest02.

**Nhận:** KHANH-01/DONG-01/LIEM-01/THAI-01 cung cấp hợp đồng/schema nền; VUONG-01 cung cấp schema tài chính. Có thể lập registry và phân tích chuẩn hóa trước khi mọi schema hoàn thành.

- [ ] Catalog table/column/type/PK/FK/owner; phân biệt 15 lớp nghiệp vụ với persistence. EventCategory dùng lookup theo Đông. Ghi candidate keys, functional dependencies, 3NF và lý do lưu snapshot lịch sử.
- [ ] Ghép FK Order.acceptedPaymentId → Payment.id và Refund.currentAttemptId → RefundTransferLog.attemptId; SP phải kiểm Payment cùng Order/attempt cùng Refund. FK đơn không đủ chứng minh quan hệ tiền đúng.
- [ ] Đăng ký file/checksum/dependencies/owner. Chốt ngoại lệ `0190_V09.sql` trước F08, `0350_F03.sql` sau V02, `0390_SP07.sql` trước SP02. Đặt tên trước lần áp dụng đầu tiên để thứ tự tên và manifest cùng nhất quán.
- [ ] Fixture dùng UUID đầy đủ và clock chung; hai tổ chức, buyer/manager/check-in/admin, membership inactive, seated 2×3, standing capacity=3, giá 0, coupon quota=1/30%/hết hạn. Các ca nhiều payment/refund attempt có seed riêng trong test DB.
- [ ] Chạy DB mới → migrations → seed → inventory; chạy lại không nhân dữ liệu. Checksum lệch/lỗi migration phải fail và không ghi history đã áp dụng. Script reset có guard đúng DB test, không đụng DB demo.

**Kiểm chứng:** `mvn -B -Psqlserver-it -Dit.test=VuongMigrationIT verify`; lệnh sqlcmd với `-b` và cơ chế secret tại runbook. **Đạt khi:** từng thành viên dựng DB mới bằng cùng hướng dẫn và evidence02. Khánh review fixture helper; chủ miền review FK. Chưa kết luận Azure đạt từ kiểm chứng local.

### VUONG-03 — Bốn role/login và principal ứng dụng

**Mục tiêu/mốc:** M0 có quyền nền; M1–M3 bổ sung quyền tối thiểu khi SQL object được giao. **Nguồn:** spec §4, §14.10.

**Tạo:** `0700_roles_grants.sql`, `database/security/local-logins.sql`, `azure-users.sql`, `docs/backend/security-matrix.md`; `security/DatabasePrincipalsIT.java`, `AuthorizationMatrixIT.java`, SQLtest03. **Nhận:** TransactionRunner/PrincipalKind của Khánh; danh sách caller/quyền từ chủ SQL object.

- [ ] Tạo R01 tc_buyer/R02 tc_manager/R03 tc_checkin/R04 tc_platform_admin cùng bốn login/user local; secret từ môi trường, không password literal. Azure dùng contained user/role tương ứng và kiểm riêng.
- [ ] Migration/auth/worker dùng principal riêng. Worker chỉ các đường SP07/09/11/17 và nhánh canceled SP10 được spec cho phép; không thêm quyền payout. Task08 hoàn tất payout dưới principal ADMIN.
- [ ] R03 DENY tài chính V03/V04/V07/V08/F05/F10, không cấp V06 có paidAmount. R01 gọi SP09 chỉ nhánh đơn 0đ phải có guard SQL. Manager không được duyệt tổ chức hay chi tiền.
- [ ] Principal lấy từ use case/actor đã xác minh, không request và không cộng tất cả quyền người dùng vào connection. Mỗi query vẫn kiểm owner/tổ chức hiện tại.
- [ ] Thực chứng GRANT/REVOKE/DENY bằng login thật, ownership chain và quyền nguồn khác. Connection reuse phải xóa actor context; không runtime nào dùng db_owner/sysadmin.

**Ví dụ:** CHECK_IN_STAFF vào SP04 cùng tổ chức được xử lý; trực tiếp đọc V08/F10 bị từ chối. Buyer giả paymentId có tiền vào SP09 cũng bị từ chối. **Kiểm chứng:** hai IT và SQLtest03, evidence03. Khánh review pool/principal; chủ SP nhận ma trận guard.

### VUONG-04 — CommissionService, F02 và TR03

**Mục tiêu/mốc:** M1 có chính sách cho tổ chức/publish; chính sách đã áp dụng chỉ thay bằng rule mới. **Nguồn:** spec §6.11, F02/TR03 và diagram.

**Tạo:** CommissionService/Repository; `0200_F02.sql`, `0500_TR03.sql`, `0150_IX14.sql`; `model/settlement/CommissionParityTest.java`, `acceptance/VuongCommissionIT.java`, SQLtest04. **Phụ thuộc:** VUONG-01–03 và schema/publish của Đông.

**API:** GET/POST `/admin/organizations/{id}/commission-rules`, POST `/admin/commission-rules/{id}/edit`; ADMIN, CSRF với POST; create201/edit200/applied conflict409. Policy DTO ở §3.1; ratePercent SQL decimal(19,6), fixedFee decimal(19,0), thời gian UTC datetime2.

- [ ] Query/create/editUnapplied dùng current ADMIN, bind parameters; getEffective kiểm đúng org/rule/thời điểm server.
- [ ] F02 tính phí HALF_UP, cap remaining, 0 → 0; tham số null/âm trả NULL. Repository giữ ý nghĩa NULL; Java và SQL có parity test.
- [ ] TR03 set-based xử lý nhiều hàng; chặn sửa applied terms hoặc đổi tổ chức trái rule, khóa cùng SP12. Không để trigger tự tính Settlement.
- [ ] Cung cấp initial policy fixture cho Đông; 10% trong ví dụ là dữ liệu test, không business default.
- [ ] Test edit đua publish, rule khác org/hết hạn; update nhiều rule có một hàng sai rollback cả statement. remaining=30000, phí tính=50000 → phí thực=30000.

**Kiểm chứng:** unit parity, VuongCommissionIT, SQLtest04/evidence04. Đông review SP01/SP12 integration; UI22 được nối tại VUONG-13.

### VUONG-05 — V03/F09 và dữ liệu tài chính hiện hành

**Mục tiêu/mốc:** M3 một Order một dòng; blocker phản ánh nghĩa vụ/attempt hiện tại. **Nguồn:** spec §6.11, V03/F09.

**Tạo:** `0300_V03.sql`, `0200_F09.sql`; query SettlementRepository, DTO blocker/order; `acceptance/VuongFinancialReadIT.java`, SQLtest05. **Phụ thuộc:** schema/nguồn thu Liêm, hoàn/outbox Thái và VUONG-02.

**API:** GET `/admin/events/{id}/settlement`, `/admin/events/{id}/settlement-blockers`; ADMIN, readonly. Không Event →404; Event không đơn hợp lệ →200, tổng 0/id nullable, không entity giả.

- [ ] Tổng hợp accepted Payment của Order và CUSTOMER_REFUND đã hoàn thành theo vé. Pre-aggregate collections trước join; compensation không thành doanh thu vé/hoa hồng.
- [ ] Ghi aliases/types/mapping V03; phân biệt dữ liệu hiện hành với F05 có historical cutoff.
- [ ] F09 chặn Event chưa kết thúc theo lịch, Payment PENDING/UNKNOWN, nghĩa vụ hoàn chưa dứt và cancel job chưa hoàn tất. Refund COMPLETED có FAILED attempt cũ không còn blocker.
- [ ] Order gross600000/refund200000 cùng khoản compensation600000 → gross600000/refund200000, không gross1200000. FAILED log không tăng tiền hoàn.
- [ ] Dùng ClockProvider, scope ADMIN; blocker chỉ DTO type/objectId/message/href nội bộ. GET không sửa bất kỳ trạng thái nào.

**Kiểm chứng:** VuongFinancialReadIT, SQLtest05/evidence05. Liêm/Thái review nguồn tiền và blocker; VUONG-06/07 dùng cùng query.

### VUONG-06 — SP14/TX14 tính lại DRAFT

**Mục tiêu/mốc:** M3 snapshot từng Order và tổng cập nhật nguyên tử. **Nguồn:** spec §6.11, SP14/TX14.

**Tạo:** `0400_SP14.sql`, SettlementService/Repository phần recalculate; `acceptance/VuongRecalculateIT.java`, SQLtest06. **Phụ thuộc:** VUONG-04/05 và thứ tự khóa chung đã được Khánh/Liêm/Thái review.

**API:** POST `/admin/events/{id}/settlement/recalculate`, body `{}`; ADMIN+CSRF, 200 DRAFT/zero-report, 409 nếu đã CONFIRMED/PAID. Chỉ nhận eventId, không totals từ client.

- [ ] Khóa Event/Settlement theo hợp đồng; tối đa một Settlement/Event. Không đơn đủ điều kiện thì không tạo Settlement/snapshot.
- [ ] Đọc V03 một dòng/Order, tính gross/refund/fee F02/net, thay snapshot DRAFT và tổng cùng TX14; kiểm parity Model nhưng không dirty-write JPA lặp SP.
- [ ] SP14 tự đứng độc lập hoặc tham gia transaction ngoài với savepoint/XACT_STATE/THROW; không commit caller. Refresh/clear JPA sau SP.
- [ ] Gross600000/refund200000/rate10/fixed10000 → commission50000/net350000; tổng khớp snapshot.
- [ ] Gây lỗi sau insert item thứ hai phải giữ snapshot cũ; hai recalc không tạo hai Settlement. Payload totals giả →400.

**Kiểm chứng:** VuongRecalculateIT dùng hai connection, rollback sau mutation và outer transaction; SQLtest06/evidence06. VUONG-07 nhận đúng SP14/lock; UI22 ghi “dự tính” cho DRAFT.

### VUONG-07 — SP15/TX15 xác nhận và đóng băng

**Mục tiêu/mốc:** M3 blocker, recalc và confirm trong cùng transaction; sau confirm snapshot bất biến. **Nguồn:** spec §6.11, SP15/TX15, TR04/TR09.

**Tạo:** `0400_SP15.sql`, `0500_TR04.sql`, `0500_TR09.sql`; Service confirm; `acceptance/VuongConfirmIT.java`, SQLtest07. **Phụ thuộc:** VUONG-05/06, SP09 Liêm/SP11 Thái cùng thứ tự khóa.

**API:** POST `/admin/settlements/{id}/confirm`, `{}`; ADMIN+CSRF, 200 nếu xác nhận hoặc replay hợp lệ, 409 blocker. Net0 chuyển PAID, không transfer log giả.

- [ ] Khóa Event/Settlement rồi kiểm endTime và F09; gọi SP14 cùng transaction để tính lại ngay trước confirm. Event CANCELLED vẫn phải hết thời gian dự kiến.
- [ ] Confirm tổng/confirmedAt và snapshot cùng TX15; callback/hoàn đua phải bị tuần tự hóa hoặc confirm rollback, không chốt số cũ.
- [ ] TR04 chặn INSERT/UPDATE/DELETE snapshot đã đóng; TR09 khóa nội dung tài chính của header, vẫn cho SP16 thay paid/pending/status đúng invariant.
- [ ] Test PENDING/UNKNOWN Payment, nghĩa vụ hoàn unresolved/cancel outbox, resolved FAILED attempt, net0; confirm lặp không tính lại frozen totals.
- [ ] Test multirow trigger có một hàng cấm, rollback toàn statement; fault sau mutation và outer transaction không commit caller.

**Kiểm chứng:** VuongConfirmIT/SQLtest07/evidence07; race thật với Liêm/Thái. Snapshot và tổng trước/sau lỗi được đối chiếu trực tiếp.

### VUONG-08 — SP16/TX16 payout mô phỏng, V08/IX12

**Mục tiêu/mốc:** M3 chi ổn định theo payoutId, không vượt số dư, phục hồi sau lỗi mạng. **Nguồn:** spec §6.11, SP16/TX16/V08/IX12 và diagram.

**Tạo:** `0400_SP16.sql`, `0300_V08.sql`, `0150_IX12.sql`; Service payout, `integration/payout/PayoutSimulationAdapter.java`, `MockPayoutSimulationAdapter.java`; `acceptance/VuongPayoutIT.java`, SQLtest08. **Phụ thuộc:** VUONG-07 và ADMIN principal VUONG-03.

**API:** POST `/admin/settlements/{id}/payouts` nhận `{"payoutId":"70000000-0000-0000-0000-000000000001","amount":"100000","reference":"fixture-01"}`; GET cùng route đọc lịch sử. New201/replay200; amount khác cùng ID hoặc vượt số dư409; amount<=0/result từ browser400.

- [ ] SP16 với verifiedResult NULL chỉ giữ chỗ: log PENDING và tăng pending cùng TX; không thêm PENDING vào enum Model PayoutResult.
- [ ] Commit reservation → gọi adapter server-only ngoài TX → ADMIN transaction mới ghi SUCCEEDED/FAILED. Adapter `submit(UUID payoutId, BigDecimal amount, String reference):PayoutSimulationResult(PayoutResult result,String reference)` idempotent theo ID/amount. Tạo `MockPayoutProviderLedger` kỹ thuật trong0050 (payoutId PK/amount/result/reference/processedAt/createdAt/version), JPA repository `repository/settlement/MockPayoutProviderRepository.java`. Adapter dùng ADMIN transaction riêng ngắn sau reservation commit, trước TX16 kết quả; ledger bền qua restart, không memory-only, không browser setter và không gọi đây là chi thật.
- [ ] Crash sau reservation: POST lại cùng ID tiếp tục PENDING cũ; không reserve lần hai. Terminal replay không hồi quy. FAILED giữ log, lần thử mới dùng ID mới; GET không gọi submit.
- [ ] Net350000, pending100000 → paid0/pending100000/available250000; SUCCEEDED → paid100000/pending0/available250000. FAILED giải phóng pending. PAID khi paid=net/pending0; net0 không payout giả.
- [ ] Hai yêu cầu 250000/200000 đua trên net350000: tối đa một được giữ chỗ. V08 pre-aggregate log, không join snapshot nhân chi; lỗi sau log mutation rollback cả tổng.

**Kiểm chứng:** VuongPayoutIT hai connection/replay/crash/outerTX; SQLtest08/evidence08. Khánh review không có browser setter/kỹ thuật worker thêm quyền SP16; UI22 thể hiện paid/pending riêng.

### VUONG-09 — F05/F10/V04 và ReportService

**Mục tiêu/mốc:** M3 báo cáo doanh thu theo nhóm đơn và dòng tiền theo thời điểm phát sinh. **Nguồn:** spec §6.12, V04/F05/F10.

**Tạo:** `0200_F05.sql`, `0200_F10.sql`, `0300_V04.sql`, `0150_IX07.sql`; ReportService/Repository/DTO; `acceptance/VuongReportsIT.java`, SQLtest09. **Phụ thuộc:** VUONG-04/05/07, V01/V02 Đông, V05/V07 Thái, V06/V09 Liêm.

**API:** GET `/organizations/{id}/reports` do Đông delegate; GET `/admin/reports` do Vương. Manager đúng org hoặc ADMIN; staff403; filter §3.1, JSON/JSP cùng query; metric/time/sort sai400.

- [ ] F05 lọc Order.paidAt trong [from,to), hoàn thành công liên quan tính đến cutoff to; không dùng V03 current refund để sửa số báo cáo lịch sử. Compensation không doanh thu vé.
- [ ] F10 chọn Payment CAPTURED paidAt và RefundTransferLog SUCCEEDED processedAt trong kỳ; tách tiền vé/bù trừ/hoàn từng mục đích và netCashFlow. UNKNOWN/FAILED và refund0đ không dòng tiền.
- [ ] V04 không nhân aggregate. Báo cáo đối soát CONFIRMED dùng snapshot đóng băng; báo cáo DRAFT ghi dự tính. Event chưa có giao dịch trả 0, không tạo entity/log.
- [ ] Order trả600000 ngày01/10, hoàn200000 ngày06/10: cohort [01/10,05/10) không trừ hoàn ngày06; cashflow ngày06 ghi200000 dù Order mua ngoài kỳ.
- [ ] Late capture600000 rồi compensation600000 → cashflow0/doanh thu vé0. Kiểm ranh giới UTC to-exclusive, row scope, empty org và pagination ổn định.

**Kiểm chứng:** VuongReportsIT/SQLtest09/evidence09, đối chiếu từng dòng và tổng độc lập. Đông dùng organizationOverview/organizationReport cùng Service.

### VUONG-10 — CSV và JSP UI17/UI23 báo cáo

**Mục tiêu/mốc:** M3 HTML/JSON/CSV cùng quyền/filter/tổng; tiếng Việt mở Excel đúng. **Nguồn:** spec §6.12, UI17/UI23, §14.11.

**Tạo:** ReportExportServlet, `service/report/CsvWriter.java`, `report/organization-report.jsp`, `admin/reports.jsp`, `assets/js/report.js`; `service/report/CsvWriterTest.java`, `acceptance/VuongCsvIT.java`, `browser/VuongReportE2EIT.java`. **Phụ thuộc:** VUONG-09, layout/API client Khánh; browser profile VUONG-15.

**API:** GET `/reports/export?organizationId=...&eventId=...&metric=...&from=...&to=...`; text/csv UTF-8/BOM, attachment filename do server kiểm soát. `ReportService.export(actor,filter,Writer)` lấy toàn bộ tập lọc theo batch, không chỉ trang UI.

- [ ] Dùng cùng query/metric/schema cột §3.1 cho JSP/JSON/CSV, totals trên toàn tập lọc; batch sort ổn định không trùng/bỏ hàng.
- [ ] Escape quote/comma/newline. Trung hòa text không tin cậy có =,+,-,@ hoặc control/whitespace dẫn tới formula; tiền do server tạo vẫn có nghĩa số tiền.
- [ ] UI ghi rõ metric, from/to/timezone, cập nhật, VND; org selector chỉ hiện phạm vi cho phép. Có loading/error/empty/success/focus/keyboard/mobile theo prototype.
- [ ] Test tên `=HYPERLINK(...)`, tiếng Việt, quote/newline; cross-org export, client disconnect và memory khi nhiều hàng.
- [ ] CSV rowcount/tổng khớp query đầy đủ; không vẽ ảnh thiếu hoặc thay bố cục gốc.

**Kiểm chứng:** unit CSV + IT + browser profile khi đã có; evidence10. Đông review UI17/ngữ cảnh, Khánh review layout.

### VUONG-11 — Audit append-only và overview

**Mục tiêu/mốc:** M1 cung cấp audit consumer; M3 có UI18/UI23 truy vết sạch secret. **Nguồn:** UI18/UI23, TR10 và TR05.

**Tạo:** `0500_TR10.sql`, `0150_IX13.sql`; AuditService/Repository/DTO, overview query, `admin/overview.jsp`, `admin/audit.jsp`; `acceptance/VuongAuditIT.java`, SQLtest11. **Phụ thuộc:** VUONG-01, actor context Khánh, TR05 Đông và audit của các miền.

**API:** GET `/admin/overview`, `/admin/audit-logs`, `/admin/audit-logs/{id}`; ADMIN. organizationOverview chỉ trả counts được phép cho Đông UI10.

- [ ] TR10 chặn UPDATE/DELETE set-based, cho INSERT; rollback transaction insert audit bình thường, không recursive logging.
- [ ] Mỗi quyết định, role change, hủy, retry, payout có actor/time/action/object. TR05 duy nhất ghi EVENT_STATUS_CHANGED; Service ghi action khác để tránh trùng.
- [ ] SESSION_CONTEXT trên cùng connection: user/admin thiếu actor → lỗi cấu hình và rollback; worker actorId NULL/source SYSTEM; không lấy actorId request.
- [ ] Overview query current state: FAILED attempt cũ không thành pending refund. Queue link dẫn UI19–22.
- [ ] Test append-only/multirow/rollback, connection actorA tái dùng actorB; staff không finance audit. DTO/log không password/OTP/fullQR/token.

**Kiểm chứng:** VuongAuditIT/SQLtest11/evidence11. Khánh/Đông review context; UI23 dùng tab audit/report cùng layout.

### VUONG-12 — AdminServlet, UI19/UI20/UI21

**Mục tiêu/mốc:** M1 duyệt org/event; M3 điều phối hoàn/hủy qua Service đúng chủ miền. **Nguồn:** spec §4, UI19–21 và TEAM signatures.

**Tạo:** AdminServlet dispatch; `admin/organizations.jsp`, `admin/events.jsp`, `admin/refunds.jsp`, `assets/js/admin-decisions.js`; `acceptance/VuongAdminDelegationIT.java`, `browser/VuongAdminDecisionsE2EIT.java`. **Nhận:** auth/CSRF Khánh, Organization/Event Service Đông, Refund/Cancellation Service Thái; policy VUONG-04.

**Request mẫu:** approve org `{"initialPolicy":{"ratePercent":"10","fixedFee":"10000","effectiveFrom":"2026-10-06T00:00:00Z","effectiveTo":"2027-01-01T00:00:00Z"}}`; publish `{"commissionRuleId":"60000000-0000-0000-0000-000000000001"}`; reject `{"reason":"Thiếu liên hệ"}`; refund quyết định `{"decision":"APPROVE"}` hoặc `{"decision":"REJECT","rejectionReason":"..."}`. Trường duyệt tổ chức theo TEAM-CONTRACT §3.5 và DONG-04; AdminServlet ánh xạ `initialPolicy` vào CommissionPolicyCommand rồi gọi OrganizationService.approve của Đông. UUID rule phải có trong fixture registry trước chạy.

- [ ] GET list/detail HTML hoặc JSON theo helper; POST parse/validate/CSRF gọi đúng Service, 200/400/401/403/409 đúng contract. Không gọi Repository/SP miền khác trực tiếp.
- [ ] UI19 chọn policy, duyệt đúng organizationId đang chờ; không tạo OrganizationRequest class.
- [ ] UI20 chọn rule cùng org; hủy hiển thị tiến độ/USED exception, chỉ báo hoàn tất khi Service xác nhận.
- [ ] UI21 hiển thị amount/status/currentAttempt/history; 0đ không provider giả; UNKNOWN reconcile, chỉ RETRYABLE sau FAILED verified mới retry.
- [ ] Adapter unit kiểm delegation; nghiệm thu phải HTTP → Service → SQL thật. Test nonADMIN, CSRF, cross-ID, lặp quyết định không nhân obligation/attempt.

**Kiểm chứng:** IT/browser/evidence12; Đông/Thái review DTO/body. Vương sở hữu JSP/controller, nghiệp vụ giữ ở Đông/Thái.

### VUONG-13 — UI22 đối soát/chi trả

**Mục tiêu/mốc:** M3 quản trị hiểu số dư, blocker, snapshot và trạng thái pending. **Nguồn:** UI22/§6.11.

**Tạo:** `admin/settlement.jsp`, `assets/js/settlement.js`; financial subroutes AdminServlet; `acceptance/VuongFinanceHttpIT.java`, `browser/VuongSettlementE2EIT.java`. **Phụ thuộc:** VUONG-04–08, layout/auth/API client Khánh; browser checks chờ VUONG-15.

- [ ] Policy list/create/editUnapplied; chọn org/event; bảng từng Order và tổng gross/refund/commission/net/paid/pending/available.
- [ ] Recalculate/confirm POST+CSRF lấy Server DTO. Blocker có type/ID/link; Event chưa kết thúc hoặc Refund chưa rõ trả409 có giải thích.
- [ ] Giữ payoutId cùng amount qua lỗi mạng/replay; đọc history trước xử lý lại. Chỉ FAILED confirmed mới chọn lần thử mới; không cho sửa amount của ID cũ.
- [ ] Không gửi totalCommission/net/status/result từ form. Hiển thị VND/HCM, Event trống0/null Settlement, net0 PAID không transfer giả.
- [ ] Browser đối chiếu net350000/paid100000/available250000 với SQL; amount khác cùng ID409; snapshot readonly; kiểm keyboard/mobile/error focus.

**Kiểm chứng:** hai lớp IT/evidence13. Liêm/Thái review blocker/money; task16 dùng màn hình này trong demo.

### VUONG-14 — Catalog SQL và benchmark 15 index

**Mục tiêu/mốc:** M2 có caller catalog; M4 có đầy đủ bằng chứng hành vi/hiệu năng. **Nguồn:** spec §12, §14.1–11.

**Tạo:** `database/tests/inventory.sql`, `database/benchmarks/seed.sql`, `queries.sql`, `before-after.sql`; `docs/backend/sql-usage.md`, `index-benchmark.md`; `acceptance/VuongSqlCatalogIT.java`, SQLtest14. **Nhận:** từng chủ SQL object cung cấp scripts/query/test/evidence; không viết lại object người khác.

- [ ] Kiểm danh mục đúng tên/nghĩa: C20/V10/SP17/F10/TR10/IX15/TX17/R4. Unique bảo vệ nghiệp vụ không thay 15 index hiệu năng; không đếm system/duplicate object.
- [ ] Mapping migration → Repository/SP/View caller → endpoint/job → test → evidence; 15 Model có methods, 24 UI có Servlet/JSP/Service. Object có nhưng chưa caller vẫn chưa đạt.
- [ ] Mỗi index có dataset/seed/distribution/query params cố định; actual plan, STATISTICS IO/TIME trước/sau, warmup/repeat và logical reads/CPU/elapsed/storage/write cost.
- [ ] Kết quả hàng/tổng không đổi; không force index hoặc tự ghi phần trăm cải thiện chưa đo. Index chưa giúp thì sửa cùng owner và kiểm lại.
- [ ] Catalog cố ý thiếu object phải fail; tổng hợp rollback sau mutation/outerTX/hai connection cho SP và multirow cho cả 10 trigger.

**Kiểm chứng:** catalog IT/SQLtest14 và 15 báo cáo trước/sau, evidence14. Tất cả thành viên review rubric/mapping miền mình.

### VUONG-15 — CI, browser profile và nghiệm thu bản ghép

**Mục tiêu/mốc:** M0 chuẩn bị browser tooling; M2–M4 kiểm bản ghép qua build/unit/SQL/security/UI. **Nguồn:** spec §12 và TEAM test/PR.

**Tạo:** `.github/workflows/verify.yml`, `browser/BrowserTestSupport.java`, `acceptance/WholeProjectIT.java`, `browser/WholeProjectE2EIT.java`, `docs/backend/acceptance.md`. **Sửa có phối hợp:** pom.xml phần browser-it do Khánh nhận chính; gửi thay đổi targeted, không ghi đè dependency/plugin.

- [ ] Chọn/khóa công cụ browser sau compatibility smoke, ví dụ Playwright Java nếu phù hợp; ghi decisions. Profile triển khai WAR/Tomcat/test DB isolated, timeout/setup/teardown rõ; thiếu môi trường fail/BLOCKED.
- [ ] CI build/unit mỗi PR; SQL Server IT dùng job/runner test được nhóm chọn, secret private; thiếu DB không skip rồi xanh. Browser không dùng external demo DB.
- [ ] E2E thật: register/OTP → duyệt org → publish → Hold/coupon/pay → QR/check-in → refund/cancel → settlement/payout → CSV/audit. Mock gateway không được báo là VNPAY Sandbox thật.
- [ ] Negative HTTP/direct-SP: owner/org ID, revoke role/authVersion, CSRF, injection, CSV/upload; UI24 màn hình/loading/empty/error/keyboard/mobile.
- [ ] Recovery giữa commit/response, worker lease, UNKNOWN reconciliation; không double credit. Phần cần account thật ghi BLOCKED đúng task, không đổi thành test mock đã đạt.

**Lệnh:** `mvn -B verify`, `mvn -B -Psqlserver-it verify`, `mvn -B -Pbrowser-it verify`. **Đạt khi:** acceptance matrix có expected/actual/commit/evidence15; FAIL trả đúng owner để sửa. Không tự push/merge chỉ vì có CI trong task.

### VUONG-16 — Docker/runbook/restore

**Mục tiêu/mốc:** M4 fresh clone chạy WAR/SQL Server local; online có kiểm chứng riêng khi được cấp quyền. **Nguồn:** spec §11, §12, §14.11.

**Tạo:** `Dockerfile`, `.dockerignore`, `docs/backend/deployment.md`, `restore-runbook.md`, `database/backups/README.md`; `acceptance/DeploymentSmokeIT.java`. **Nhận:** build WAR/modules, migrations/principals/adapters. Root README/env schema/HealthServlet phối hợp Khánh.

- [ ] Kiểm tag JDK25/Tomcat11 thực và pin sau smoke; WAR không chứa container APIs ngoài provided scope. Build/start/readiness/new install có kết quả thật.
- [ ] Secret ngoài Git/image/buildargs; đo RSS/coldstart/pool/heap/thread theo quota thực. DB, các provider simulation ledger và ảnh bền vững qua redeploy, không đặt SQL Server trên host ứng dụng quota nhỏ.
- [ ] Backup local/script đầy đủ → restore DB test riêng → so catalog/rows/keys/orphan/finance/audit và worker resume; không restore đè DB demo.
- [ ] Khi được quyền online, tra tài liệu chính thức về quota/TLS/firewall/HTTPS/callback/Azure contained roles; kiểm query/locks riêng Azure. Thiếu account ghi BLOCKED online, local vẫn hoàn thiện.
- [ ] Restart mất session RAM thì đăng nhập lại; Hold/Order/Ticket vẫn DB; host sleep không tự chuyển pending Payment thành FAILED.

**Kiểm chứng:** fresh install, Docker smoke, restore; online evidence tách riêng trong evidence16. Tất cả thành viên dựng được bản giao từ runbook, không phải đoán password/path. Không tự tạo dịch vụ tính phí hoặc deploy từ kế hoạch này.

### VUONG-17 — Báo cáo môn học, demo và bàn giao

**Mục tiêu/mốc:** M4 gói tái lập và hồ sơ đúng rubric với bằng chứng thật. **Nguồn:** spec §12, §14.11–14; [kế hoạch DBMS](../baocao/KE-HOACH-BAO-CAO-DBMS.md).

**Tạo:** `docs/submission/README.md`, `report-outline.md`, `demo-script.md`, `contributions.md`, `acceptance-matrix.md` (tất cả dưới `docs/submission/`). Word/PDF/PPTX tạo khi triển khai hồ sơ. **Nhận:** VUONG-01–16 và phần thuyết minh/evidence của từng chủ miền.

- [ ] Sáu chương: bài toán/phạm vi; ERD/FD/3NF/tables; SQL/logic/caller; TX/concurrency/recovery/security; kiến trúc/application/screens; kết quả/giới hạn/hướng phát triển; references và phụ lục.
- [ ] Times New Roman13, line1.5, 50–100 trang nội dung ngoài bìa/TOC/phụ lục; caption/source. Giữ DOCX thiết kế gốc; ảnh thiếu giao tác giả cập nhật, không vẽ hoặc giả ảnh app.
- [ ] Slides<=15 trang; trình bày10–15 phút/Q&A5–10 phút; demo JSP → SQL thật. Gắn nhãn đúng Sandbox/hoàn và chi mô phỏng; mỗi thành viên hiểu phần mình.
- [ ] Gói source/SQL backup hoặc scripts đầy đủ/README/env/build/Tomcat/demo, tên nhóm/STT theo giảng viên xác nhận. Nộp trước24 giờ theo hạn thực tế; không tự bịa STT/hạn hoặc upload/publish.
- [ ] Fresh clone dựng gói lại; checksum source/WAR/manifest; không secrets/log raw. Acceptance matrix PASS/FAIL/BLOCKED gắn commit/test/evidence, không tick đóng góp chưa có.
- [ ] Giữ năm thành viên thực tế; xác nhận quy mô với giảng viên theo §14.12. Tác giả duyệt hồ sơ trước nộp; Vương tổng hợp, từng người chịu trách nhiệm nội dung miền mình.

**Đạt khi:** từng thành viên chạy demo/quyền/kho/tiền theo README, evidence17 và rubric mapping đầy đủ. Có đủ phạm vi không đồng nghĩa tự nhận 100% điểm.

## 5. Checklist kết thúc phần Vương

- [ ] Hai Model đủ thuộc tính/phương thức/quan hệ; không thêm lớp nghiệp vụ ngoài diagram.
- [ ] SQL sở hữu có caller/test/evidence; catalog toàn hệ thống đủ C20/V10/SP17/F10/TR10/IX15/TX17/R4.
- [ ] UI17–23 và phần admin UI14 dùng layout/JSP/Service chung, không trùng Servlet.
- [ ] Fee/settlement/payout, refund0đ, paid/pending, cohort/cashflow đúng; race/rollback/recovery chạy SQL Server thật.
- [ ] Quyền DB và scope HTTP đạt; CI/browser fail rõ khi thiếu DB; WAR/Docker/restore có bằng chứng.
- [ ] Từng owner giao PR/evidence/API/SQL/phần báo cáo; ảnh giữ cho tác giả cập nhật.

## 6. Lệnh giao việc cho AI

```text
Đọc spec.md, docs/classdiagram/diagram.md, docs/tasks/TEAM-CONTRACT.md và docs/tasks/vuong.md.
Kiểm tra repo; chọn task VUONG chưa có bằng chứng và đã có đầu vào phụ thuộc.
Chỉ triển khai file Vương sở hữu; giữ hợp đồng Service/Servlet/migration, gọi đúng module khác.
Chạy build/unit/SQLServerIT/browser phù hợp; lưu expected/actual/evidence gắn commit.
Không tự đổi nghiệp vụ, vẽ ảnh thiếu, dùng mock để báo tích hợp thật, push/merge/deploy/nộp hồ sơ.
Kết thúc ghi task/file/API/SQL/UI đã làm, kết quả và dependency còn thiếu/người nhận bàn giao.
```

## Quy trình Git bắt buộc cho Vương

Đọc toàn bộ [GIT-WORKFLOW](GIT-WORKFLOW.md) trước triển khai. Tính năng và sửa lỗi đều dùng feature theo task, mặc định từ develop đã cập nhật và kiểm, PR vào develop. Chỉ ngoại lệ sửa bản main đã bàn giao khi develop còn việc chưa nghiệm thu mới rẽ feature từ main, PR main rồi đồng bộ main → develop theo GIT-WORKFLOW §7. Develop là nhánh tích hợp; main giữ bản đã nghiệm thu qua PR develop → main và tag sau kiểm. Không dùng nhánh release riêng. Chỉ stage file thuộc nhiệm vụ. Hoàn thành mỗi đơn vị có kiểm chứng thì commit code/test/migration/tài liệu liên quan, ghi footer `Task-Id`, lệnh/kết quả và evidence. Task lớn có nhiều commit/PR con; commit nền không đồng nghĩa toàn task đã đạt. Không chờ hoàn thành cả module mới commit.

Bảng dưới là tên nhánh và subject khởi điểm đã gắn đúng chức năng. Khi chỉ sửa lỗi dùng `fix`, chỉ thêm test dùng `test`, chỉ tài liệu dùng `docs`; mô tả phải phản ánh nội dung thực. Subject dài được rút gọn rõ nghĩa theo khuyến nghị72 ký tự của nhóm, không đổi task ID. PR một task mặc định vào develop, ngoại lệ base main theo §7; reviewer theo người nhận đầu ra; tiền/quyền/transaction cần hai reviewer chuyên môn. Build/SQL/browser thiếu môi trường ghi BLOCKED, không đóng task từ commit hoặc mock.

| Task | Nhánh chức năng | Subject commit khi triển khai đầy đủ hành vi |
|---|---|---|
| VUONG-01 | `feature/vuong/vuong-01-finance-contracts` | `feat(settlement): define financial models and initial policy contracts` |
| VUONG-02 | `feature/vuong/vuong-02-migration-manifest` | `feat(db): assemble cross-domain keys fixtures and migrations` |
| VUONG-03 | `feature/vuong/vuong-03-database-principals` | `feat(db): enforce runtime principals and database permissions` |
| VUONG-04 | `feature/vuong/vuong-04-commission-policy` | `feat(settlement): manage commission policies and protect applied terms` |
| VUONG-05 | `feature/vuong/vuong-05-finance-projections` | `feat(settlement): query financial totals and current blockers` |
| VUONG-06 | `feature/vuong/vuong-06-recalculate-settlement` | `feat(settlement): recalculate draft order snapshots atomically` |
| VUONG-07 | `feature/vuong/vuong-07-confirm-settlement` | `feat(settlement): freeze verified settlement snapshots atomically` |
| VUONG-08 | `feature/vuong/vuong-08-record-payout` | `feat(settlement): reserve and record idempotent simulated payouts` |
| VUONG-09 | `feature/vuong/vuong-09-report-metrics` | `feat(report): separate cohort revenue from event cash flow` |
| VUONG-10 | `feature/vuong/vuong-10-report-export` | `feat(report): render scoped reports and safe CSV exports` |
| VUONG-11 | `feature/vuong/vuong-11-audit-overview` | `feat(audit): protect audit history and query current admin queues` |
| VUONG-12 | `feature/vuong/vuong-12-admin-pages` | `feat(core): delegate organization event and refund administration` |
| VUONG-13 | `feature/vuong/vuong-13-settlement-page` | `feat(settlement): render settlement blockers balances and payouts` |
| VUONG-14 | `feature/vuong/vuong-14-sql-benchmarks` | `test(db): verify SQL usage and benchmark all required indexes` |
| VUONG-15 | `feature/vuong/vuong-15-integration-checks` | `ci(build): validate the integrated WAR database and browser flows` |
| VUONG-16 | `feature/vuong/vuong-16-deployment-restore` | `build(core): package the WAR and document durable deployment` |
| VUONG-17 | `feature/vuong/vuong-17-submission-handoff` | `docs(report): assemble verified reports demo and project handoff` |

Trước commit: kiểm ownership/diff → chạy checks đúng task → `git diff --check` → stage đường dẫn cụ thể → review staged diff → commit theo mẫu chung. Báo cáo cuối của thành viên/AI phải ghi nhánh, commit, task, tests/evidence và dependency chưa ghép. Push/merge/deploy và thay cấu hình GitHub theo quyền được giao; bộ kế hoạch này không tự thực hiện các bước đó.
