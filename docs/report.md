# Báo cáo rà soát mã nguồn TicketsCenter

Ngày rà soát: 13/10/2026 · Nhánh: `refactor/db-canonical-consolidation` @ `f2f9930` (đi trước `main` 176 commit, trước `dev` 13 commit)

Báo cáo này thay thế hoàn toàn bản trước (rà trên `dev`). Nhiều kết luận của bản cũ đã lỗi thời; mục 3 liệt kê cái gì đã được xử lý và cái gì còn tồn.

## 0. Cập nhật sau rà soát (13/10/2026)

Các mục dưới đây đã được sửa trong cây làm việc (chưa commit). Phần còn lại của báo cáo mô tả trạng thái **trước** khi sửa.

| Mục | Đã làm |
|---|---|
| 4.1, 4.2 | Escape đầu ra bằng `fn:escapeXml` ở `payment-result.jsp`, `my-tickets.jsp`, `ticket-detail.jsp`, `order/my-orders.jsp`, `order/order-detail.jsp` |
| 4.3 | `LoginServlet.isLocalRedirect` chỉ nhận đường dẫn trong ứng dụng; JSON phản hồi được escape |
| 4.4 | Seeder demo chỉ chạy khi `demo.seed.enabled=true` và không bao giờ ở production |
| 4.7 | Chuỗi filter bảo mật khai báo thứ tự trong `web.xml`; `CouponAdminServlet` tự kiểm CSRF |
| 5.2 | Phản hồi 500 không còn chứa `getMessage()`; `HttpResponses.error` escape JSON |
| 5.4 | `/profile`, `/organizer/dashboard`, `/organizer/coupons` đọc dữ liệu thật qua API; các URL prototype khác chuyển hướng sang trang thật; xóa 13 JSP tĩnh, `app.js`, `data.js` |

Đợt sửa thứ hai:

| Mục | Đã làm |
|---|---|
| 5.1 | Giới hạn đăng nhập sai (5 lần/15 phút theo tài khoản + IP, HTTP 429); PBKDF2 600.000 vòng, hash cũ tự nâng cấp khi đăng nhập đúng |
| 5.3 | `GET/POST /api/me/profile` qua `usp_UpdateMyProfile` (migration 059, đã áp lên Azure); OpenAPI: 96 implemented, 4 planned |
| 5.5 | Pool mặc định 20 mỗi principal, ngân sách 60; hết ngân sách thì lỗi sau `connection-timeout` thay vì chờ vô hạn |

Đợt sửa thứ ba:

| Mục | Đã làm |
|---|---|
| 5.3 | `POST /api/zones/{id}/price` (zone edit giới hạn ở `basePrice`); `POST /api/admin/payments/{id}/compensation/retry` qua `usp_RetryPaymentCompensation` (migration 060, đã áp lên Azure) |
| 5.1 | `SecurityHeadersFilter`: `nosniff`, `X-Frame-Options`, `Referrer-Policy`, HSTS khi HTTPS, CSP giới hạn framing/base-uri/object |
| 5.6 | Redis trong compose: chỉ mở cổng trên loopback, mật khẩu tùy chọn qua `REDIS_PASSWORD`, `noeviction` |
| 6.3 | Xóa `VnPayConfig`, `PayoutStatus`, stub `RefundRepository` |
| 6.4 | Thêm `vw_AuditLogs`, `vw_EventCheckinCounts`; Java đọc qua tên mới. Tên cũ `vw_Vuong*` giữ nguyên vì nhánh khác còn dùng |
| 7 | Không đổi tên file migration (ledger Azure và CI tham chiếu theo tên); ghi thứ tự áp chuẩn vào `database/migrations/APPLY_ORDER.md` |

Chưa làm:
- 4.6 (hủy sự kiện): đã có nhánh `feat/khanh/khanh-06-event-cancellation` làm trọn phần này và migration `058_khanh06_event_cancellation.sql` đã nằm trên Azure, nên không làm trùng ở đây.
- 4.5 (rotate secret): phải làm ở phía nhà cung cấp.
- 6.2 (đưa phần ghi của `identity` về procedure): cần viết lại đăng ký, OTP, đặt lại mật khẩu và thành viên, trong khi toàn bộ test DB của `identity` không chạy được ở máy này.
- Đổi tên 20 trigger/constraint/procedure nội bộ mang tên `Vuong`, 5.7 và các mục P3.

Test sau khi sửa (`mvn -o -B test`): 978 test, 0 fail, 147 skip. Các JSP mới và JSP đã sửa chưa được biên dịch hay mở thử trên Tomcat; compose chưa được chạy thử (Docker không chạy trên máy này).

## 1. Kết luận nhanh

Phần lõi nghiệp vụ (giữ vé, đơn hàng, thanh toán, hoàn tiền, outbox, đối soát) đã được nối và chạy qua stored procedure/view; bộ test xanh. Tuy vậy còn **7 vấn đề cần xử lý trước khi coi là sẵn sàng triển khai**:

| # | Vấn đề | Mức | Mục |
|---|---|---|---|
| 1 | XSS phản chiếu ở trang kết quả thanh toán (không cần đăng nhập) | Cao | 4.1 |
| 2 | XSS lưu trữ ở trang vé (tên sự kiện/địa điểm in thẳng ra HTML) | Cao | 4.2 |
| 3 | Open redirect và chèn JSON ở đăng nhập | Cao | 4.3 |
| 4 | Tài khoản demo mật khẩu `123` được tạo/đặt lại mỗi lần khởi động, kể cả production | Cao | 4.4 |
| 5 | Secret cũ vẫn nằm trong lịch sử Git | Cao | 4.5 |
| 6 | Hủy sự kiện chưa hoạt động: worker rỗng, không có endpoint, dù procedure đã có trong DB | Cao | 4.6 |
| 7 | Thứ tự filter không được khai báo; CSRF/kiểm tra phiên của các route forward phụ thuộc thứ tự ngẫu nhiên | Cao (chưa chạy thử) | 4.7 |

Các phát hiện bảo mật 4.1–4.3 và 4.7 được xác định bằng **đọc mã**, chưa khai thác thử trên ứng dụng đang chạy.

## 2. Tổng quan và số liệu

- **Kiểu hệ thống**: monolith Java 25 / Servlet 6.1 / JSP / Tomcat 11, Hibernate + SQL Server (nghiệp vụ nằm trong procedure/view), Redis (cache, idempotency, lease), VNPAY Sandbox; hoàn tiền và chi trả là mô phỏng.
- **Quy mô**: 365 file Java chính (22.758 dòng), 191 file test (23.280 dòng), 46 migration + 2 file archive (16.417 dòng SQL), 61 JSP, 16 file JS.
- **CodeGraph**: index cũ đã lỗi thời (147 file, lập ngày 09/10); sau `codegraph sync` là **623 file, 12.302 node, 29.601 edge**.
- **Test** (`mvn -o -B test`): `BUILD SUCCESS`, **140 suite, 969 test, 0 fail, 0 error, 152 skip**. Toàn bộ 152 test bị skip là test cần SQL Server/Redis/SMTP thật (xem 8).
- **Tầng dữ liệu**: Java tham chiếu 98 đối tượng `dbo.*` (43 procedure, còn lại là view/function). Không còn `UPDATE`/`DELETE`/`MERGE` nhúng trong Java; ngoại lệ ghi ở 6.2.

## 3. So với báo cáo trước

| Mục cũ | Trạng thái hiện tại |
|---|---|
| Secret commit trong `application.properties`, `taikhoan.txt` | **Đã gỡ khỏi cây làm việc** và thêm vào `.gitignore`; chỉ còn `application.properties.example` với placeholder. **Còn trong lịch sử** (4.5). |
| `OutboxWorker` không được khởi tạo, không giới hạn retry, gửi mail trong transaction | **Đã sửa**: chạy 10 giây/lần, lease bằng `usp_OutboxLease`, gửi ngoài transaction, tối đa 5 lần, có `OutboxEmailJob` và test tích hợp. |
| `PaymentReconciliationJob` không chạy, gọi gateway trong transaction | **Đã sửa**: nối qua `PaymentRuntime`, có `VnpayQueryClient`, gọi gateway ngoài transaction, có test. |
| `RefundJob` rỗng | **Đã sửa**: dùng `RefundProcessingWorker` + `sp_refund_begin_attempt`/`sp_refund_record_attempt_result`, có lease Redis và đối soát. |
| `HoldExpiryJob` là mã chết | **Đã xóa**. |
| `EventCancellationJob` rỗng, không có endpoint hủy | **Còn nguyên** (4.6). |
| Seed tài khoản demo vô điều kiện | **Còn nguyên** (4.4). |
| `GET/POST /api/me/profile` chưa có | **Còn nguyên** (5.3). |
| OpenAPI ghi "planned" cho endpoint đã có code | **Còn nguyên**: 53 implemented / 47 planned / 3 legacy, không đổi (5.3). |
| Trùng số migration | **Còn**, và thêm một cặp `030` (7). |
| Pool 500 kết nối | **Còn** là mặc định trong code (5.5). |
| `ImageCleanupJob` chưa có | Đã có dọn ảnh trong `EventRuntime` (`usp_EventImageClaimCleanup`); tên lớp cũ không còn liên quan. |
| Thiếu `TEAM-CONTRACT.md`, `api-contract.md` | **Còn thiếu**; vẫn có 6 và 3 tài liệu trỏ tới. |

## 4. Phát hiện mức cao

### 4.1 XSS phản chiếu ở trang kết quả thanh toán
`VnPayCallbackServlet.handleReturn` đưa `result.txnRef()` vào request rồi forward sang `WEB-INF/views/payment/payment-result.jsp`, nơi in `${not empty txnRef ? txnRef : 'N/A'}` và `${message}` không escape. `PaymentService.processReturn` trả lại nguyên `vnp_TxnRef` từ query string **kể cả khi chữ ký sai**. Một liên kết `/api/payments/vnpay/return?vnp_TxnRef=<script>…` vì thế chạy script trong origin của ứng dụng mà không cần đăng nhập.

Sửa: dùng `<c:out>`/`fn:escapeXml` cho mọi giá trị trong JSP này, và không hiển thị `txnRef` khi chữ ký không hợp lệ.

### 4.2 XSS lưu trữ ở trang vé
`ticket/my-tickets.jsp` và `ticket/ticket-detail.jsp` in thẳng `${t.eventTitle}`, `${t.venueName}`, `${ticket.venueAddress}`, `${t.zoneName}`, `${t.seatLabel}` (cả trong thuộc tính `title="…"`). Đây là dữ liệu do ban tổ chức nhập, hiển thị cho người mua. Toàn bộ JSP có 537 biểu thức EL nhưng chỉ 5 chỗ dùng `c:out`/`escapeXml`.

Chưa kiểm tra tầng nhập liệu có chặn ký tự HTML hay không; dù có, view vẫn nên escape. Các trang dựng HTML bằng JS (`admin/organizations/list.jsp`, `organization/members/list.jsp`) có hàm `escapeHtml` riêng.

### 4.3 Open redirect và chèn JSON ở đăng nhập
`LoginServlet.doPost` chấp nhận tham số `redirect` nếu nó không bắt đầu bằng `http`. Giá trị `//evil.example` hoặc `/\evil.example` qua được kiểm tra và được đưa vào `sendRedirect`. Cùng giá trị đó được nối thẳng vào chuỗi JSON `{"redirect":"…"}` không escape, nên dấu `"` trong tham số làm hỏng hoặc chèn trường vào phản hồi.

Kiểm tra quyền theo `contains("/admin")` cũng chỉ là so khớp chuỗi. Sửa: chỉ nhận đường dẫn bắt đầu bằng đúng một `/`, cùng context path, và serialize JSON bằng hàm escape sẵn có.

### 4.4 Tài khoản demo mật khẩu `123`
`PersistenceListener.contextInitialized` luôn gọi `DemoAccountSeeder.seed()`, không có cờ và không kiểm `app.env`. Mỗi lần khởi động, seeder:
- tạo `abcc@manager.com`, `abcc@staff.com`, `buyer@ticketscenter.vn` với mật khẩu `123`;
- nếu các email này đã tồn tại thì **đặt lại mật khẩu về `123`** và đánh dấu đã xác minh email;
- duyệt lại tổ chức "Vé Hay Event Center" và kích hoạt lại membership.

`AdminSeeder` thì có cờ `admin.seed.enabled` và chặn mật khẩu yếu ở production; seeder demo không có gì tương tự. Sửa: chỉ chạy khi có cờ riêng và `app.env` không phải production.

### 4.5 Secret trong lịch sử Git
`src/main/resources/application.properties` có trong 3 commit (`0669775`, `6b5e5d3`, `df5a770`) và `taikhoan.txt` trong 2 commit (`a720a6e`, `9929074`), tất cả đều truy cập được từ `HEAD`. Bản cũ ghi nhận các file này chứa mật khẩu DB, khóa ký, secret VNPAY, mật khẩu SMTP, khóa Cloudinary và mật khẩu admin.

Gỡ file khỏi cây làm việc không vô hiệu hóa các giá trị đó. Tôi không có cách kiểm tra chúng đã được đổi chưa; nếu chưa thì cần rotate toàn bộ, sau đó mới cân nhắc `git filter-repo`.

### 4.6 Hủy sự kiện chưa hoạt động
- `EventCancellationRepository` vẫn là stub: `findWork` trả danh sách rỗng, `process` và `completeFinishedEvents` rỗng.
- `EventCancellationJob` vẫn được lên lịch 5 giây/lần, mỗi lần mở 2 transaction rỗng bằng principal `WORKER`.
- Không có route `POST /api/admin/events/{id}/cancel` hay `cancellation-progress`.
- Phía DB thì đã sẵn: `usp_CancelEvent` (cấp cho `tc_platform_admin`) và `usp_ProcessCancelledOrder` (cấp cho `tc_worker`, định nghĩa lại ở migration 050) không được Java gọi ở đâu.

Hệ quả: admin không thể hủy sự kiện, và nếu sự kiện bị hủy trực tiếp trong DB thì đơn/vé không được xử lý. Liên quan: `POST /api/admin/payments/{id}/compensation/retry` cũng chưa có route.

### 4.7 Thứ tự filter không được khai báo
Cả 13 filter đều dùng `@WebFilter`; `web.xml` không có `<filter-mapping>` nào. Theo đặc tả Servlet, thứ tự giữa các filter khai báo bằng annotation là không xác định. Mã nguồn đã tự ghi nhận điều này (`OrganizationApiRoutesFilter`: "Named forwarding can run before annotation-ordered REQUEST filters").

Vấn đề: nhiều filter định tuyến xử lý hoặc forward request ngay trong filter, bỏ qua phần còn lại của chuỗi. Nếu chúng chạy trước `CsrfFilter`, `SessionValidationFilter`, `RequestValidationFilter` hoặc `DatabaseActorFilter` thì các lớp bảo vệ đó không chạy cho request ấy.

| Filter định tuyến | Tự bảo vệ? |
|---|---|
| `OrganizationApiRoutesFilter` (báo cáo) | Có: tự kiểm phiên, tự gọi `withActor`; chỉ GET |
| `RefundOrderRoutesFilter` | Tự gọi `withActor`; chỉ GET |
| `EventManagementRoutesFilter` (có POST) | `EventManagementWeb` có tham chiếu CSRF; không tự đặt actor |
| `OrganizationMembersRoutesFilter` (có POST) | `OrganizationMembersServlet` có tham chiếu CSRF; không tự đặt actor |
| `CouponOrganizationRoutesFilter` (có POST) | **Không**: `CouponAdminServlet` không kiểm CSRF, không kiểm `authVersion`; chỉ yêu cầu header `Idempotency-Key` |
| `CheckInEventRoutesFilter`, `OrganizationReadsideRoutesFilter` | Chỉ GET; không tự đặt actor |

Header `Idempotency-Key` bắt buộc làm giảm rủi ro CSRF cho coupon (form chéo site không gửi được header tùy biến), nhưng giá trị header này không được dùng cho việc gì khác: `CouponAdminServlet` chỉ kiểm nó có tồn tại.

Tôi chưa xác định thứ tự thực tế trên Tomcat 11, nên đây là rủi ro về cấu hình chứ chưa phải lỗi đã tái hiện. Sửa: khai báo `<filter-mapping>` theo thứ tự cố định trong `web.xml` (actor → validation → session → CSRF → định tuyến) và thêm test khẳng định thứ tự.

## 5. Phát hiện mức trung bình

### 5.1 Đăng nhập và mật khẩu
- Không có giới hạn số lần đăng nhập sai; chỉ OTP và quên mật khẩu có cooldown (HTTP 429).
- `PasswordHasher` dùng PBKDF2-HMAC-SHA256 với 10.000 vòng, thấp hơn nhiều so với khuyến nghị hiện hành của OWASP (600.000).
- `RoleAuthorizationFilter` (`/admin/*`, `/organizer/*`) dựa vào `UserPrincipal` trong session, không kiểm lại `authVersion`; việc kiểm lại chỉ có ở `/api/*`. Người bị thu quyền vẫn mở được khung trang cho tới khi hết phiên, dù API sẽ từ chối.
- Không có header bảo mật chung (CSP, `X-Frame-Options`, HSTS); `X-Content-Type-Options` chỉ có ở 3 servlet. Cookie phiên đã được đặt `HttpOnly` và `Secure` có điều kiện trong `AuthSecurityListener`.

### 5.2 Lộ thông báo lỗi nội bộ
Các nhánh `catch (Exception)` trả `getMessage()` cho client ở mã 500: `CouponAdminServlet:77`, `OrganizationMembersServlet:90,175`, `MyMembershipsServlet:72`. `TicketApiServlet:69` còn nối `e.getMessage()` vào JSON không escape. Nên trả mã lỗi cố định và ghi chi tiết vào log.

### 5.3 Route còn thiếu và OpenAPI lệch mã
- **Chưa có route**: `GET/POST /api/me/profile` (`RequestValidationFilter` đã cho phép upload ở đường dẫn này nhưng không có servlet), `POST /api/zones/{id}/price`, hai route hủy sự kiện, route retry bù trừ.
- **OpenAPI**: 47 endpoint vẫn ghi `planned`, trong khi phần lớn đã có mã (holds, orders, payments, coupons, tickets, check-in, refund, members, publish/reject). Swagger đang mô tả sai trạng thái hệ thống.
- **Tài liệu**: `TEAM-CONTRACT.md` và `api-contract.md` được trỏ tới nhưng không tồn tại.

### 5.4 Trang prototype tĩnh vẫn được phục vụ
Các JSP sau không gọi API nào và chứa dữ liệu mẫu viết cứng:

| URL | View | Ghi chú |
|---|---|---|
| `/profile` | `public/profile.jsp` | Chưa có API hồ sơ |
| `/my-orders` | `public/my-orders.jsp` | Trang thật là `/me/orders` |
| `/tickets`, `/my-tickets` | `public/tickets.jsp` | Trang thật là `/me/tickets` |
| `/checkout`, `/payment-result` | `public/checkout.jsp`, `public/payment-result.jsp` | |
| `/organizer/dashboard`, `/organizer/coupons` | `organizer/*.jsp` | |

Người dùng vào các URL này sẽ thấy dữ liệu giả trông như thật.

Một nhóm view khác không còn truy cập được qua GET vì filter đã chuyển hướng trước khi tới servlet: `admin/dashboard.jsp`, `admin/settlements.jsp`, `admin/audit-logs.jsp`, `organizer/reports.jsp`, `organizer/checkin-select.jsp`, `organizer/checkin-scanner.jsp`. Ba JSP không được tham chiếu ở đâu: `admin/org-approval.jsp`, `admin/refund-approval.jsp`, `coupon/coupon-admin.jsp`. `assets/js/data.js` (1.011 dòng dữ liệu mẫu) không được trang nào nạp nhưng vẫn nằm trong WAR.

`AdminServlet` và `OrganizerServlet` chọn view bằng `path.contains("org")`, `contains("event")`…; cách này dễ khớp nhầm khi thêm URL mới.

### 5.5 Pool kết nối
- `DatabaseConfig.getMaximumPoolSize()` mặc định 500 cho **mỗi** principal (tối đa 6 pool). `ConnectionBudgetDataSource` giới hạn tổng bằng một semaphore, nhưng ngân sách mặc định cũng là 500, vẫn cao hơn giới hạn phiên của các gói Azure SQL nhỏ.
- `BUDGET.acquire()` không có timeout: khi hết ngân sách, luồng request chờ vô hạn thay vì lỗi sau `connectionTimeout`.
- Mỗi lần mượn và trả kết nối đều chạy thêm một lệnh `sp_set_session_context`, tức 2 round-trip phụ cho mỗi transaction.

### 5.6 Khởi động và vận hành
- `PersistenceListener` bắt `RuntimeException` khi khởi tạo DB/seed, chỉ ghi log rồi tiếp tục. Ứng dụng lên ở trạng thái thiếu DB; chỉ `/health/ready` phản ánh điều đó.
- Thanh toán bắt buộc có Redis (`RedisPaymentIdempotency`); Redis ngừng thì không khởi tạo được thanh toán.
- `docker-compose.yml`: Redis mở cổng 6379 ra host, không mật khẩu, chính sách `allkeys-lru`. Khóa idempotency và lease nằm chung với cache nên có thể bị loại khi đầy bộ nhớ.
- `Dockerfile` ghim Tomcat 11.0.4, bản đã cũ; nên nâng lên bản 11.0.x mới nhất.
- Cổng hoàn tiền và chi trả luôn là `SimulatedRefundGateway`/`SimulatedPayoutAdapter`, không có cấu hình chuyển sang cổng thật.
- `RefundProcessingWorker` ghi cảnh báo khi lỗi nhưng không kèm exception, nên log không cho biết nguyên nhân.

### 5.7 IPN VNPAY
`processIpn` xác minh chữ ký, đối chiếu số tiền và có khóa theo đơn. Hai điểm lỏng: bỏ qua kiểm tra số tiền nếu thiếu `vnp_Amount`, và coi `vnp_TransactionStatus` vắng mặt là thành công. Cả hai chỉ xảy ra với request đã ký đúng nên rủi ro thấp. Ngoại lệ trong `processIpn` làm servlet trả 500 thay vì JSON `RspCode`.

## 6. Kiến trúc và chất lượng mã (từ đồ thị)

### 6.1 Phụ thuộc giữa module
Đồ thị cho thấy phụ thuộc hai chiều giữa gần như mọi cặp module (28 cặp). Đáng chú ý nhất:
- `config` phụ thuộc ngược vào `identity` (19 cặp file), `fulfillment` (7), `order`, `payment`, `event`, `settlement`, vì worker và `PersistenceListener` nằm trong `config`.
- `report` ↔ `settlement` (18/1), `dashboard` ↔ `settlement` (11/1), `event` ↔ `storage` (17/2).

Với một monolith thì chấp nhận được, nhưng việc khởi động worker nên chuyển về từng module (như `HoldRuntime`, `PaymentRuntime`, `EventRuntime` đã làm) để `config` không phải biết về nghiệp vụ.

### 6.2 Chỗ ghi dữ liệu chưa qua procedure
Nhánh này nhằm đưa logic ghi về procedure. Còn lại:
- `SimulatedPayoutAdapter` chạy `INSERT dbo.tc_vuong_simulated_transfers` và đọc bảng `tc_vuong_test_fixture` trực tiếp. Đây là chỗ duy nhất trong `src/main` còn truy cập thẳng bảng `tc_*`.
- Module `identity` vẫn ghi bằng JPA `persist`/`merge`: `UserRepository`, `OtpRepository`, `OrganizationRepository`, `OrganizationMembershipRepository`, `UserDAO` (đăng ký, OTP, đổi mật khẩu, thành viên). Duyệt/từ chối/gửi yêu cầu tổ chức thì đã qua `sp_organization_approve`, `sp_organization_reject`, `usp_OrganizationSubmit`.
- Hai seeder ghi trực tiếp bằng JPA.

Các truy vấn nối chuỗi (`ORDER BY`, tên view, danh sách cột trong `ReportRepository`, `EventCatalogRepository`, `FinanceRepository`, `AuditRepository`, `AdminRefundRepository`) đều lấy phần động từ hằng số hoặc bảng tra cố định; giá trị người dùng đi qua tham số. Không thấy SQL injection.

### 6.3 Mã chết
Đã đối chiếu đồ thị với grep:

| Đối tượng | Tình trạng |
|---|---|
| `config/VnPayConfig`, `settlement/model/PayoutStatus` | Không được tham chiếu ở đâu, kể cả test |
| `fulfillment/repository/RefundRepository` | Stub; chỉ được truyền vào `RefundJob` rồi bỏ qua |
| `event/repository/EventCancellationRepository` | Stub (4.6) |
| `order/service/CouponPolicy`, `settlement/commission/service/CommissionRulePolicy`, `CommissionRuleDto` | Chỉ test tham chiếu |
| `event/image/EventImageMigration` | Công cụ dòng lệnh (dùng `System.out`), chỉ test tham chiếu; nên chuyển ra khỏi WAR |
| `UserDAO` | Chỉ hai seeder dùng; trùng chức năng với `UserRepository` |
| Entity `AuditLog`, `OrganizationRequest`, `TicketCheckInLog`, `CouponRedemption` | Chỉ khai báo trong `persistence.xml`, không có mã dùng; vẫn bị `hbm2ddl=validate` kiểm khi khởi động |

Đồ thị ban đầu báo 76 file không có tham chiếu vào; phần lớn là servlet/filter do container nạp hoặc DTO mà CodeGraph không phân giải được, nên chỉ các mục trên được giữ lại.

### 6.4 Khả năng đọc
- 41 file có dòng dài hơn 200 ký tự (dài nhất 598 ký tự ở `audit/service/AuditMetadata`), tập trung ở `report`, `settlement`, `audit`, `dashboard`, `fulfillment/refund`. Mã kiểu nén một dòng này khó review và debug.
- Phương thức quá dài: `AdminOrganizationApprovalServlet.doPost` (196 dòng), `OrganizationRequestService.approveRequest` (117), `OrganizationRequestServlet.doPost`/`doGet` (113/112), `OrderRepository.findOrderDetail` (104), `LoginServlet.doPost` (100).
- 69 chỗ `catch (Exception|Throwable)`; hơn 30 khối catch rỗng. Phần lớn bọc thao tác cache Redis là có chủ đích, nhưng không ghi log nên sự cố Redis sẽ không để lại dấu vết.
- Tên đối tượng DB không thống nhất: `usp_*` lẫn `sp_*`, `vw_*` lẫn `v_*`, và tên gắn với người (`vw_VuongAuditLogs`, `vw_VuongEventCheckinCounts`, `tc_vuong_*`).

## 7. Cơ sở dữ liệu và migration

- 46 migration: `001`–`008`, rồi nhảy tới `027`–`057` (không có `009`–`026`; `026` và một bản `027` nằm trong `archive/`).
- **Trùng số**: `027` (2 file), `028` (2), `029` (3), `030` (2), `043` (2), `045` (2). Thứ tự áp dụng trong mỗi nhóm phụ thuộc thứ tự tên file.
- Migration `052` giữ lại các đối tượng có tên trong `SPEC.md` dù chưa dùng (ví dụ `usp_CancelEvent`), khớp với 4.6.
- Quyền tối thiểu: chưa có bằng chứng mới về việc chạy với 6 login hạn chế thật. Các test liên quan nằm trong nhóm bị skip.

## 8. Test và CI

- **152 test bị skip** đều cần hạ tầng ngoài. Nhiều nhất: `AdminRefundDecisionSqlServerTest` (18), `RefundWorkerSqlServerTest` (18), `TicketCheckInSqlServerTest` (14), `RefundRequestSqlServerTest` (14), `ReadsideDatabaseIntegrationTest` (13), `FinanceTeamSchemaIntegrationTest` (11), `PaymentCrossDomainSqlTest` (9). Kết quả xanh ở trên vì thế **không** chứng minh procedure và phân quyền DB đúng.
- **Độ phủ theo đồ thị**: 127/365 file chính không được file test nào tham chiếu. Theo module: `fulfillment` 35/85, `order` 15/34, `identity` 15/63, `event` 13/48, `settlement` 10/29, `ticketing` 9/22, `payment` 6/17.
- Không có test nào cho thứ tự filter (4.7) hay cho việc escape đầu ra JSP (4.1, 4.2).
- **CI**: `02-unit-integration-tests.yml` có job SQL Server và Redis nhưng là opt-in (`run_db_tests`). `06-deploy-production.yml` chỉ là mô phỏng, tự ghi "No application service started". Các workflow `khanh02`–`khanh05` và `sql-report-safety` kích hoạt theo nhánh feature cũ và PR vào `dev`, không chạy cho nhánh hiện tại. `npm audit … || true` không bao giờ làm hỏng build.

## 9. Việc nên làm, theo thứ tự

| Ưu tiên | Việc | Mục |
|---|---|---|
| P0 | Escape đầu ra ở `payment-result.jsp`, `my-tickets.jsp`, `ticket-detail.jsp`; rà 537 biểu thức EL còn lại | 4.1, 4.2 |
| P0 | Siết kiểm tra `redirect` và escape JSON ở `LoginServlet` | 4.3 |
| P0 | Đặt seeder demo sau cờ và chặn ở production | 4.4 |
| P0 | Xác nhận đã rotate các secret từng commit; nếu chưa thì rotate | 4.5 |
| P0 | Khai báo thứ tự filter trong `web.xml`; thêm kiểm CSRF và `authVersion` cho `CouponAdminServlet` | 4.7 |
| P1 | Hiện thực `EventCancellationRepository` qua `usp_ProcessCancelledOrder`, thêm endpoint hủy và tiến độ; tạm tắt job rỗng cho tới lúc đó | 4.6 |
| P1 | Giới hạn đăng nhập sai; tăng số vòng PBKDF2 kèm cơ chế băm lại khi đăng nhập | 5.1 |
| P1 | Bỏ `getMessage()` khỏi phản hồi 500 | 5.2 |
| P1 | Gỡ hoặc nối API cho các trang prototype; xóa JSP mồ côi và `data.js` | 5.4 |
| P1 | Chạy nhóm test SQL Server/Redis mặc định trong CI cho PR vào `main`/`dev` | 8 |
| P2 | Cập nhật `openapi.json`; hiện thực `/api/me/profile` | 5.3 |
| P2 | Hạ mặc định pool, thêm timeout cho semaphore ngân sách | 5.5 |
| P2 | Đưa ghi của `identity` và `SimulatedPayoutAdapter` về procedure | 6.2 |
| P2 | Xóa mã chết ở 6.3; đổi tên đối tượng DB gắn tên người | 6.3, 6.4 |
| P2 | Đánh lại số các migration trùng trước khi hợp nhất vào `main` | 7 |
| P3 | Tách dòng dài và phương thức dài; ghi log ở các catch rỗng | 6.4 |

## 10. Phương pháp và giới hạn

- **CodeGraph**: `codegraph sync` rồi `status`, `query`, `callers`; truy vấn trực tiếp `.codegraph/codegraph.db` để tính tham chiếu vào từng file, ma trận phụ thuộc module, phương thức dài nhất, file không có test tham chiếu. Mọi ứng viên mã chết đều được kiểm lại bằng grep.
- **Đọc mã**: toàn bộ filter bảo mật và định tuyến, `PersistenceListener`, các worker, hai seeder, `LoginServlet`, `SecurityContext`, `VnPayCallbackServlet`, `PaymentService.processIpn/processReturn`, tầng persistence, các JSP in dữ liệu động.
- **Chạy**: `mvn -o -B test` (kết quả ở mục 2). Phân tích `openapi.json` bằng script.
- **Không làm**: không chạy ứng dụng, không kết nối SQL Server/Redis/VNPAY/SMTP thật, không thử khai thác các lỗ hổng ở mục 4, không đọc nội dung secret trong lịch sử Git, không rà từng dòng của 365 file. Các module `report`, `settlement`, `audit`, `dashboard` chỉ được rà ở mức truy vấn SQL và cấu trúc.
