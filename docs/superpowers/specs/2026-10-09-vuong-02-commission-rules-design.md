# VUONG-02 — đặc tả phần chính sách hoa hồng độc lập

Issue: [#29 — Commission Rule administration](https://github.com/devonxjz/TicketCenter/issues/29). Owner: Vương (`l3vu0ng`). Nhánh: `feat/vuong/vuong-02-commission-rules`, từ `dev` tại `cfd77c6`.

Ngày 09/10/2026, người dùng duyệt hướng bắt đầu với DTO, validation, quyền ADMIN và quy tắc chặn sửa chính sách đã áp dụng. Tài liệu này cụ thể hóa hướng đó để duyệt trước khi lập kế hoạch triển khai. Schema API được owner xác nhận và môi trường DB test JDBC của nhóm hiện chưa sẵn sàng.

## 1. Mục tiêu và phạm vi

Chuẩn bị phần chính sách dùng chung cho API quản trị hoa hồng: dữ liệu đúng kiểu, kiểm quyền nhất quán, và quyết định được phép sửa từ số sự kiện liên kết do backend cung cấp. Kết quả của giai đoạn này là các lớp Java độc lập và unit test chạy được trên máy mà không cần DB/Redis/SMTP.

Phần này hỗ trợ UI-22/API của #29 ở giai đoạn tích hợp tiếp theo. Nó chưa tạo chính sách trong DB hoặc cung cấp endpoint HTTP, và không chứng minh tính bất biến khi hai transaction tranh chấp.

Mã nguồn nằm trong `src/main/java/vn/ticketscenter/settlement/commission/**`; test nằm trong package tương ứng dưới `src/test/java`. Giữ model `CommissionRule`, `UserPrincipal`, pom, persistence và code các owner khác theo ranh giới [#34](https://github.com/devonxjz/TicketCenter/issues/34).

## 2. Nguồn hợp đồng và quyết định

| Nội dung | Quyết định cho giai đoạn này | Căn cứ |
|---|---|---|
| ID tổ chức/chính sách | `UUID`; dạng chuỗi ở HTTP sau này | BaseEntity/model hiện có và CONVENTIONS |
| Tỷ lệ | `BigDecimal`, 0 đến 100 inclusive, chuẩn hóa scale 4 bằng phép chuyển chính xác | Issue #29 và cột rate_percent của model |
| Phí cố định | `long`, từ 0 đến Long.MAX_VALUE, đơn vị đồng | Model hiện có và hướng tiền nguyên đồng đã duyệt |
| Thời gian | `Instant`, bắt đầu nhỏ hơn kết thúc | Issue #29, không dùng timezone máy |
| Hiệu lực | `effectiveFrom <= now < effectiveTo` | Issue #29 và function hiện có |
| Quyền | `UserPrincipal.isAdmin()` là điều kiện bắt buộc | Contract identity đã merge |
| Chính sách đã dùng | Chặn mọi edit khi linked event count lớn hơn 0 | Điều kiện COM-03 của #29 và hướng đã duyệt |

Ví dụ ID số và tiền `8000.00` trong issue không được sao chép thành kiểu dữ liệu mới. DTO Java dùng UUID/long phù hợp model đã khóa. Khi ghép HTTP, số tiền được xuất dạng chuỗi nguyên đồng; parser cần đối chiếu quy tắc đầu vào trước khi expose API.

SPEC/TR03 cho phép điều chỉnh khoảng hiệu lực của một rule đã dùng, trong khi #29 chặn toàn bộ edit. Giai đoạn này áp dụng điều kiện chặn toàn bộ edit đã được duyệt. Đường sửa thời gian riêng chỉ được bổ sung sau khi hợp đồng quản trị được nhóm chốt.

## 3. Thành phần và trách nhiệm

### 3.1 CommissionRuleCreateDto

Immutable record trong `settlement/commission/dto` với bốn trường:

- `BigDecimal ratePercent`.
- `long fixedFee`.
- `Instant effectiveFrom`.
- `Instant effectiveTo`.

Constructor kiểm tra tỷ lệ/thời gian bắt buộc, tỷ lệ nằm trong [0, 100], phí không âm, và start < end. Tỷ lệ có quá bốn chữ số thập phân có nghĩa bị từ chối; các số 0 dư được phép loại bỏ chính xác. Không làm tròn giá trị người dùng nhập. Không tự đặt ngày kết thúc, lấy giờ máy hoặc sửa khoảng hiệu lực.

Đây là DTO giá trị đã kiểm tra; actor và organizationId thuộc ngữ cảnh lệnh của service/HTTP ở giai đoạn sau. Giai đoạn này không thêm JSON parser hoặc ràng buộc HTTP vào DTO.

### 3.2 CommissionRuleDto

Immutable projection trong `settlement/commission/dto` với `UUID ruleId`, `UUID organizationId`, bốn trường điều khoản như DTO tạo, và `long associatedEventsCount`.

Constructor yêu cầu ID đầy đủ, điều khoản hợp lệ và count không âm. Dùng cùng quy tắc validation để DTO phản hồi không biểu diễn một rule sai miền giá trị. Projection không chứa entity JPA, session, password hoặc thông tin quyền của caller.

Phương thức `isEffectiveAt(Instant now)` dùng khoảng nửa kín [from, to); now bắt buộc. Giai đoạn HTTP sau sẽ lấy now từ clock phía server và tính `isEffectiveNow` lúc trả response. Cache lưu điều khoản, không lưu cờ thời gian có thể trở nên cũ.

### 3.3 CommissionRulePolicy

Lớp thuần trong `settlement/commission/service` cung cấp:

- `requireAdmin(UserPrincipal actor)`: actor null bị từ chối với `AUTHENTICATION_REQUIRED`; actor có role CUSTOMER bị từ chối với `ADMIN_REQUIRED`; ADMIN được phép.
- `requireEditable(UserPrincipal actor, long associatedEventsCount)`: kiểm quyền trước, sau đó count. Count > 0 bị từ chối với `RULE_ALREADY_ATTACHED_TO_EVENTS`; count = 0 được phép. Count âm là lỗi trạng thái đầu vào nội bộ và ném `IllegalStateException`.

MANAGER/CHECK_IN_STAFF là quyền tổ chức, không nâng platform role CUSTOMER thành ADMIN. Policy dùng principal hiện có, không nhận role từ payload.

Policy không đọc entity lazy hoặc truy vấn persistence. Số sự kiện liên kết phải được adapter backend lấy từ DB trong transaction bảo vệ mutation ở giai đoạn tích hợp. Cờ hoặc count do client gửi không được dùng để cho phép edit.

### 3.4 Lỗi nghiệp vụ

Một `CommissionRuleException` trong package miền giữ mã ổn định và thông điệp ngắn, không giữ payload hoặc credential. Validation dùng mã `COMMISSION_RULE_INVALID`.

| Mã | Ý nghĩa | HTTP mapping khi triển khai API |
|---|---|---:|
| COMMISSION_RULE_INVALID | Thiếu hoặc sai điều khoản do caller nhập | 400 |
| AUTHENTICATION_REQUIRED | Chưa có principal | 401 |
| ADMIN_REQUIRED | Principal không phải platform ADMIN | 403 |
| RULE_ALREADY_ATTACHED_TO_EVENTS | Rule đã có ít nhất một Event tham chiếu | 409 |

Exception không phụ thuộc Servlet API. HTTP mapping thuộc controller ở giai đoạn sau, dùng envelope chuẩn `error.code/message/correlationId`; unit test hiện tại kiểm mã lỗi Java, không tuyên bố endpoint đã trả các HTTP status này.

## 4. Luồng và kiểm chứng

DTO tạo kiểm điều khoản ngay khi xây dựng. Policy quyết định quyền từ principal backend; policy edit kiểm quyền trước khi kiểm count để hạn chế tiết lộ trạng thái cho người thiếu quyền. DTO phản hồi giữ dữ liệu giá trị và tính hiệu lực từ Instant được truyền vào.

Unit test gọi trực tiếp các lớp thật, không mock repository/DB để tuyên bố nghiệp vụ ghi đã thành công.

| Nhóm | Ca bắt buộc |
|---|---|
| Tỷ lệ | 0 và 100 hợp lệ; tỷ lệ âm, >100, null và phần lẻ không biểu diễn chính xác ở scale 4 bị chặn; 5.00000 chuẩn hóa chính xác thành 5.0000 |
| Phí | 0, 10000 và Long.MAX_VALUE hợp lệ; giá trị âm bị chặn |
| Thời gian | from < to hợp lệ; bằng nhau, đảo thứ tự và trường null bị chặn |
| Quyền | ADMIN được phép; CUSTOMER bị ADMIN_REQUIRED; actor null bị AUTHENTICATION_REQUIRED |
| Edit | Count 0 được phép; count 1 và nhiều sự kiện bị RULE_ALREADY_ATTACHED_TO_EVENTS; count âm bị lỗi trạng thái nội bộ |
| Thứ tự guard | Caller thiếu quyền bị từ chối kể cả count > 0 hoặc count âm |
| Projection | ID/count/điều khoản được kiểm tra; thời điểm trước start, đúng start, giữa khoảng, đúng end và sau end cho kết quả đúng |

Áp dụng chu trình test đỏ → triển khai → test xanh. Nhóm regression cục bộ dùng các unit test identity không truy cập DB/gửi SMTP thật đã xác định trong phiên #28. Evidence ghi tên test, lệnh, số ca/mã thoát và commit, phân biệt unit test với integration.

## 5. Điều kiện để tích hợp phần còn lại của #29

1. Schema UUID/tc_commission_rules và view/UDF phù hợp model được owner xác nhận; SQL legacy hiện tại dùng BIGINT/commission_rules và chưa thể dùng làm adapter cho model này.
2. Có DB test JDBC riêng cho create/edit/audit, quyền DB và rollback. Kiểm bất biến phải nằm trong transaction có khóa/constraint/trigger thích hợp, bao gồm ca publish Event đồng thời với edit; kiểm count trước rồi update riêng không đủ bằng chứng.
3. Owner chốt idempotency key, nơi lưu fingerprint/kết quả, replay cùng payload và 409 khi payload khác. Không dùng cache đọc 300s làm bằng chứng chống lặp mutation.
4. Chốt đường audit cùng transaction và invalidation sau commit cho `tc:commission:rules:v1:<organizationId>` TTL 300s; luồng approve tổ chức của Thái thông báo theo contract, không sửa package identity trong feature này.
5. Khi các điều kiện trên sẵn sàng, triển khai CommissionRuleRepository/Service, AdminCommissionRulesServlet, JSP và các ca COM-01…COM-04 qua HTTP/SQL thật.

Giai đoạn hiện tại giữ #29 mở. Kết quả đủ để tiếp tục tích hợp là các kiểu dữ liệu/policy được kiểm thử, không phải nghiệm thu toàn bộ màn hình quản trị hoặc tính an toàn của transaction tài chính.
