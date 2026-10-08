# Quy ước triển khai và nghiệm thu TicketsCenter

Tài liệu này đi cùng các task thành viên và task ngày tham khảo. Phạm vi hiện tại gồm JSP/frontend/backend/database/test/hồ sơ; [TEAM-CONTRACT](TEAM-CONTRACT.md) chốt ownership/contracts/migrations và [GIT-WORKFLOW](GIT-WORKFLOW.md) chốt GitFlow giản lược: feature chung cho tính năng/sửa lỗi, mặc định từ develop và PR vào develop, bản nghiệm thu qua PR vào main và tag; không dùng nhánh release hoặc loại nhánh sửa khẩn cấp riêng. Nguồn nghiệp vụ: [spec.md](../../spec.md) và [diagram chuẩn](../classdiagram/diagram.md). Lịch, 351 giờ công và phân vai A/B/C giữ từ kế hoạch cũ để truy vết; cần chốt lại trước triển khai, không phải tiến độ hoặc bảng phân công nhân sự thực tế. Các đường dẫn mã nguồn dưới đây là **đường dẫn dự kiến cần tạo khi triển khai**, chưa phải file đang tồn tại. Khi đổi tên phải cập nhật task và mọi nơi gọi trong cùng thay đổi.

## 1. Phạm vi và ranh giới

- Một ứng dụng Maven WAR, JDK 25, Tomcat 11.0.25, Servlet 6.1, namespace `jakarta.*`, JPA resource-local/Hibernate, SQL Server. Không thêm Spring. Khánh khóa phiên bản dependency tương thích stack đã chốt sau kiểm tra tích hợp, ghi quyết định để các thành viên cùng dùng.
- Route trang trả JSP/JSTL mặc định; Accept application/json rõ ràng trả DTO cùng query/quyền. JSON-only/status, CSV, QR và Return/IPN theo TEAM/API-MAP. Các task thành viên xây đủ24UI, Bootstrap/CSS/camera và kiểm thử; giới hạn backend-only chỉ còn ở lịch 21 ngày cũ.
- Giữ đúng 15 class nghiệp vụ và toàn bộ phương thức/quan hệ của diagram. Organization giữ cùng hồ sơ khi gửi/duyệt/từ chối. User.organizationRoles là map quyền hiện hành, lưu qua UserOrganizationRole; EventCategory là kiểu giá trị/danh mục. Refund trực tiếp giữ nghĩa vụ, kể cả 0đ, currentAttemptId và kết quả; RefundTransferLog giữ lịch sử thực hiện. Settlement giữ grossRevenue/totalRefund/totalCommission/paidAmount/pendingAmount, dữ liệu theo đơn ở SettlementOrderSnapshot, lịch sử chi ở SettlementTransferLog. TicketCheckInLog/AuditLog, OTP, redemption, outbox và bảng nối thuộc persistence; DTO/enum không tính thành class nghiệp vụ.
- Luồng: Filter → Servlet → Service → Model/Repository → SQL. Nhóm lớp theo nghiệp vụ trong từng tầng như SPEC §3. Chỉ tạo Service/Repository khi có use case cần; không tạo một bộ ba lớp cho từng bảng/SP.
- SP01–SP17 sở hữu đường ghi tương ứng. Java không dirty-write entity rồi gọi SP để lặp cùng thay đổi. Repository dùng `StoredProcedureQuery`, truy vấn View/UDF có bind parameters; clear/refresh persistence context sau SP.
- SQL Server thật là môi trường integration/concurrency. H2 hoặc mock không chứng minh được khóa, kiểu dữ liệu, filtered index, transaction hay quyền của SQL Server.

Các hợp đồng bắt buộc xuyên ngày:

- Đăng ký `{userName, fullName, email, password}`; email và userName chuẩn hóa đều duy nhất (C01). Login thường bằng email/mật khẩu theo UI-03; `admin` là userName thật của tài khoản seed, không phải alias thay cho trường userName. Thêm thành viên bằng userName tài khoản có sẵn, gọi User.assignRole/revokeRole và giữ lịch sử kỹ thuật.
- Mỗi Payment luôn thuộc một Order; một Order có 0..* lần thu. Payment.start(order, txnRef) chụp totalAmount bất biến. Order.acceptedPayment chỉ một CAPTURED của chính Order; PAID có tiền bắt buộc có acceptedPayment, PAID 0đ dùng markPaid(null, now) và không tạo Payment.
- Refund REQUESTED → APPROVED/REJECTED; beginAttempt(ID) đưa APPROVED/RETRYABLE sang PROCESSING. Kết quả SUCCEEDED → COMPLETED, FAILED → RETRYABLE, UNKNOWN → NEEDS_RECONCILIATION. Chỉ FAILED đã xác minh được thử ID mới trên cùng Refund; log attempt không phải nghĩa vụ mới. 0đ dùng completeWithoutTransfer, vẫn lưu Refund COMPLETED, không RefundTransferLog hoặc processedAt chuyển tiền giả.
- Settlement.recalculate chỉ DRAFT, confirm đóng băng các tổng. beginPayout/recordPayoutResult cập nhật pendingAmount/paidAmount và SettlementTransferLog trong cùng transaction; paid + pending <= netPayable, PAID chỉ khi paid = net và pending = 0. payoutId lặp giữ kết quả cũ, amount khác bị chặn; FAILED giữ log, thử lại ID mới.
- Route `/organization-requests` và `/refund-requests` là tên use case HTTP; ID tương ứng là organizationId/refundId của class hiện hành. Route `/payouts` trả DTO từ Settlement và log kỹ thuật, không tạo class nghiệp vụ chi trả.

## 2. Quy ước file

| Ký hiệu trong task | Đường dẫn đầy đủ tính từ gốc repo |
|---|---|
| `JAVA/` | `src/main/java/vn/ticketscenter/` |
| `TEST/` | `src/test/java/vn/ticketscenter/` |
| `SQL/` | `database/migrations/` |
| `SQLTEST/` | `database/tests/` |
| `SEED/` | `database/seeds/` |
| `EVIDENCE/` | `docs/evidence/` |

Bộ thành viên dùng tên 0010/0020/0030/0040/0050 và object/migration ngoại lệ theo TEAM-CONTRACT/manifest. Quy ước lịch ngày cũ giữ để truy vết, không dùng đồng thời tạo bản schema thứ hai: `D02_01_tables.sql`, `D02_02_constraints.sql`, `D06_01_organization.sql`. Mỗi ngày có thể nhiều file nếu phụ thuộc đòi hỏi; bảng → constraint/index → UDF/View theo phụ thuộc → SP/trigger → quyền → seed/test. Quyền của object mới được thêm cùng ngày tạo object. Không chỉnh migration đã áp dụng chung: tạo migration tiếp theo. File test ngày: `SQLTEST/day-NN.sql`, `TEST/acceptance/DayNNIT.java`; unit test đặt cạnh nhóm model/service tương ứng và kết thúc `Test`.

`docs/backend/` lưu quyết định mapping, API, khóa, cấu hình và runbook; `EVIDENCE/<ten>/<TASK-ID>.md` lưu bằng chứng thành viên; `EVIDENCE/day-NN.md` là bằng chứng lịch ngày tham khảo. Không copy bí mật, cookie, OTP, QR hoặc connection string có password vào bằng chứng.

## 3. Hợp đồng HTTP và dữ liệu

- Đường dẫn trong task là hợp đồng dự kiến dưới context path của WAR; `APP_BASE_URL` đã bao gồm context path. Ngày 1 ghi toàn bộ vào `docs/backend/api-contract.md`, mỗi ngày bổ sung request/response thực tế.
- UUID dưới dạng chuỗi; tiền VND dưới dạng chuỗi số nguyên, ví dụ `"300000"`; số lượng là số nguyên; thời điểm JSON ISO-8601 UTC, ví dụ `2026-09-25T03:00:00Z`. Java dùng `UUID`, `BigDecimal`, `Instant`; SQL dùng `uniqueidentifier`, `decimal(19,0)`, UTC `datetime2`.
- Dữ liệu thành công: `{"data": {...}}`; danh sách thêm `page`, `pageSize`, `total`. `page >= 1`, đề xuất `pageSize` mặc định 20 và tối đa 100. Đây là quyết định kỹ thuật được ghi nhận ngày 1.
- Lỗi: `{"error":{"code":"HOLD_EXPIRED","message":"Lượt giữ vé đã hết hạn","correlationId":"..."}}`. Mã ổn định để kiểm thử; không trả stack trace, SQL, thông tin nhà cung cấp hay bí mật.
- HTTP: 400 sai định dạng/validation; 401 chưa đăng nhập/phiên mất hiệu lực; 403 thiếu vai trò; 404 đối tượng không tồn tại hoặc ngoài phạm vi mà không được tiết lộ; 409 xung đột trạng thái/hết chỗ; 413 payload quá lớn; 429 giới hạn tần suất; 503 phụ thuộc tạm lỗi. IPN trả mã đúng giao thức VNPAY, không áp dụng envelope chung lên IPN.
- Mọi user mutation dùng POST và CSRF, kể cả logout. Đăng ký/login có bảo vệ CSRF phù hợp phiên chưa xác thực; endpoint `GET /auth/csrf` cung cấp token gắn session. Public IPN là ngoại lệ có chữ ký; return URL chỉ đọc trạng thái.
- Session xác định `actorId`; backend xác minh membership rồi chọn principal. Không tin `userId`, `role`, `organizationId`, `paidAmount`, `status=CAPTURED`, `now` hoặc `expiresAt` từ client để cấp quyền hay quyết định tiền/thời hạn.
- Filter kiểm tra trạng thái User/authVersion hiện tại; Service kiểm tra quyền theo đối tượng trong mỗi use case. DTO không serialize entity graph, passwordHash, OTP hash, auth secret hay QR ngoài đường riêng được phép.
- Danh sách phải có order ổn định kèm ID làm khóa phụ. Tên cột sắp xếp lấy từ allowlist; giá trị tìm kiếm luôn bind. Ghi rõ timezone/filter trên báo cáo.

## 4. Transaction, khóa và tính lặp an toàn

Ngày 1–3 lập `docs/backend/locking.md` trước khi viết nghiệp vụ. Thứ tự khóa phải được chứng minh dùng được cho tất cả luồng, đặc biệt giữ mới khi Hold cũ ở Event khác; không chỉ liệt kê thứ tự rồi mặc định đúng.

Một phương án để kiểm chứng: khóa các User liên quan theo ID khi thay Hold/membership; khám phá tập Event cần chạm rồi khóa Event theo ID; sau đó các bản ghi điều phối Order/Hold/Refund/Settlement và kho/coupon theo thứ tự đã thống nhất. Nếu khám phá lại thấy tập Event thay đổi thì rollback và thử lại hữu hạn từ đầu. Các đường không cần User tuyệt đối không lấy User sau Event. Cần phân tích callback, worker, publish, refund, cancel và trigger trước khi chấp nhận phương án. Đây là đầu vào thiết kế, không phải thuật toán đã được kiểm thử.

- Trong một use case chỉ một chủ thể commit. SP đứng độc lập tự BEGIN/COMMIT; SP được JPA/SP khác gọi tham gia transaction hiện tại và dùng savepoint.
- `SET NOCOUNT ON`, `SET XACT_ABORT ON`; CATCH kiểm tra `XACT_STATE()` và ownership trước rollback; luôn THROW lỗi, không commit transaction của caller.
- Không giữ transaction trong lúc gọi VNPAY/email/storage. Lưu ý định → commit → gọi ngoài → transaction ghi kết quả. Outbox ghi cùng transaction với nghiệp vụ, worker nhận có lease và khóa idempotency.
- Idempotency dùng khóa nghiệp vụ của SPEC: organizationId, holdId, orderId, paymentId/txnRef, refundId + attemptId, eventId/settlementId, payoutId. Sau lỗi mạng/commit chưa rõ phải tra dữ liệu đã lưu trước retry; không mặc định thất bại.
- Tranh chấp chạy bằng hai connection thật với barrier/điểm đồng bộ và timeout hữu hạn. Test fixture làm đồng hồ có thể điều khiển ở Java; tham số thời gian kiểm thử của SQL không được trở thành lối để HTTP client đặt giờ.

## 5. Cách làm và kiểm chứng mỗi task

1. Đọc mục SPEC, hợp đồng và các task phụ thuộc; ghi người nhận và giờ bắt đầu.
2. Với logic phân nhánh, parser, quyền, API có hành vi hoặc bug: viết/nhận diện test thất bại, chạy để thấy lỗi đúng lý do; sửa nguyên nhân chung và đọc tất cả caller; chạy lại test. Configuration/glue đơn giản dùng smoke check phù hợp.
3. Cài đặt phần nhỏ đủ hoàn thành task; giữ quyền, validation và lịch sử tài chính. Không thêm dependency chưa được chủ dự án đồng ý. Test dùng công cụ đã thống nhất ngày 1.
4. Chạy các lệnh bên dưới và test chuyên biệt của ngày. Kiểm tra cả response lẫn dữ liệu sau transaction.
5. Ghi vào evidence: case, dữ liệu đầu, thao tác, kết quả kỳ vọng/thực tế, lệnh, mã thoát, commit được kiểm tra và đường log đã lọc. Chỉ đánh dấu checkbox khi có bằng chứng.

Các lệnh sau là **hợp đồng tooling phải tạo ngày 1–2**, chưa chạy được trong repo tại thời điểm lập kế hoạch:

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day08IT verify
sqlcmd -S "$TC_SQL_HOST" -d "$TC_TEST_DATABASE" -G -b -i database/tests/day-08.sql
mvn -B verify
```

`-G` chỉ dùng khi môi trường đã cấu hình xác thực Entra phù hợp. Với SQL Server local dùng integrated authentication được cấu hình, hoặc `-U "$TC_TEST_LOGIN"` và password nạp an toàn vào biến `SQLCMDPASSWORD`; không truyền password qua `-P`, không in biến. Người triển khai ghi đúng lệnh phù hợp môi trường vào runbook ngày 1. Profile `sqlserver-it` phải fail khi DB thiếu, không âm thầm skip rồi báo xanh. `mvn verify` mặc định build/unit; nghiệm thu cuối luôn chạy thêm profile integration.

**Test SQL thất bại bằng THROW, không chỉ PRINT:**

```sql
DECLARE @actual decimal(19,0) = dbo.fn_CalculateCouponDiscount(500000, 'FIXED_AMOUNT', NULL, 100000);
IF @actual IS NULL OR @actual <> 100000
    THROW 51001, N'F01: expected 100000 VND', 1;
```

Chữ ký tham số thực tế được khóa theo SPEC trong migration và cập nhật đồng bộ các caller. Đối với transaction, phải gây lỗi sau ít nhất một thay đổi, rồi kiểm tra rollback không để dữ liệu bán phần/khóa treo. Đối với trigger, thêm lệnh nhiều dòng có một dòng sai. Đối với quyền, thử cả HTTP và gọi SP trực tiếp bằng principal không được phép.

## 6. Mẫu đóng task và xử lý bị chặn

```text
Task: D08-T01
Người thực hiện / người kiểm tra: ghi tên thật khi nhận việc
Trạng thái: chưa làm | đang làm | bị chặn | đã kiểm chứng
Bằng chứng: docs/evidence/day-08.md, tên case và commit đã chạy
Đầu ra: file thực tế + endpoint/SP đã gọi được
Nếu bị chặn: phụ thuộc nào thiếu, ai xử lý, task độc lập nào tiếp tục được
```

Không tick hoàn thành chỉ vì SQL đã tạo được hoặc HTTP trả 200. Task tiền/quyền phải có ca bị từ chối, lặp và cạnh tranh liên quan. Task gọi dịch vụ ngoài phải tách bằng chứng adapter test và kết nối sandbox/nhà cung cấp thật.

## 7. Các rủi ro cần review xuyên suốt

| Điều kiện | Hành vi phải có | Ngày kiểm chứng |
|---|---|---|
| Timeout sau commit nhưng trước response | Tra trạng thái, không tạo lần thu/hoàn/đơn thứ hai | 8–12, 15–17, 21 |
| Mốc đúng expiresAt/startTime/endTime, sai timezone | `[start,end)`, server UTC quyết định | 5, 7–8, 13–18 |
| User nhiều tổ chức, membership/authVersion đổi giữa phiên | Quyền hiện tại và principal đúng use case, không rò chéo tổ chức | 4–6, 13, 19 |
| Giá/coupon/phí bị sửa khi đơn đang xử lý | Giữ snapshot, khóa coupon khi Payment PENDING/UNKNOWN | 7–11, 17 |
| Restart giữa công việc hủy/email/hoàn | Lease hết hạn được nhận lại, không nhân vé/kho/tiền | 12, 15–16, 20–21 |

## 8. Dữ liệu kiểm thử dùng chung

Tạo ít nhất hai tổ chức A/B; hai buyer xác minh và một buyer chưa xác minh; một User là manager A và check-in B; một manager cuối cùng; admin; một User DISABLED. Dùng UUID cố định riêng trong database kiểm thử; userName duy nhất cho mọi User, email miền ví dụ; password do môi trường test cấp. Không dùng dữ liệu người thật.

Mỗi tổ chức có Event DRAFT, PENDING_APPROVAL, PUBLISHED và CANCELLED; lịch kiểm thử tính theo mốc UTC được kiểm soát. Event bán có khu SEATED 2×3 ghế và STANDING capacity 3; khu có giá 200000, 300000 và 0. Coupon phần trăm 30%, fixed vượt 30%, hết hạn và maxUses=1. Chính sách phí trong fixture là dữ liệu kiểm thử có tên rõ, không phải mặc định kinh doanh.

SQL chỉ seed trạng thái phức tạp trực tiếp trong database test với quyền fixture; bài end-to-end ngày 21 phải tạo trạng thái qua API/SP thật. Ghi rõ file fixture và điều kiện ban đầu để người khác tái tạo.

## 9. Quản lý Git trong bản triển khai hiện hành

- Nhánh `feature/<ten>/<task-id>-<chuc-nang>` dùng chung cho tính năng và sửa lỗi; mặc định từ develop mới nhất đã kiểm, PR vào develop. Chỉ ngoại lệ sửa bản main đã bàn giao khi develop còn việc chưa nghiệm thu mới rẽ feature từ main, PR main rồi đồng bộ main → develop theo GIT-WORKFLOW §7.
- Main/develop không nhận commit trực tiếp sau bootstrap. Chốt phiên bản bằng PR develop → main, hai reviewer, kiểm SHA main và tag/gói bàn giao; không cần nhánh release riêng.
- Commit tiếng Anh `type(scope): description`, Task-Id/Validation/Evidence; Issue thật mới dùng Refs/Closes. Commit nhỏ theo đơn vị đã kiểm, stage đường dẫn sở hữu, không bịa PASS hoặc co-author.
- Nhánh chia sẻ cập nhật bằng merge; không rewrite/force nhánh chung. Tài liệu Word tham khảo không cấp quyền chạy reset, push, merge, tag remote hoặc deploy.
- Mọi điều kiện bootstrap/review/CI/sửa lỗi/dọn nhánh theo toàn bộ GIT-WORKFLOW; lịch backend cũ không ghi đè quy trình này.
