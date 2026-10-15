# Đối chiếu bản sửa database theo báo cáo SQL Server

Ngày 11/10/2026. Nguồn: “Báo cáo sửa lỗi ticketcenter (SQL Server).md” của
TranLeThai, do người dùng cung cấp. SPEC và các contract đã tích hợp vẫn là
chuẩn nghiệp vụ; các đề xuất xóa mô hình trong báo cáo không tự động thay thế SPEC.

## Kết quả đã cài

Đã cài migration **031–039** trên database project **TicketCenterticke**.
031–038 được cài trong một giao dịch; 039 là giao dịch bổ sung. Mỗi file có
SHA-256 trong `tc_schema_migrations`; không sửa lịch sử migration 001–030.
Không xóa dữ liệu nghiệp vụ. Backfill seat/event ID và vô hiệu hóa OTP đã hết
hạn/bị thay thế giữ nguyên ID và lịch sử.

| Mục báo cáo | Xử lý và giới hạn |
| --- | --- |
| S1/S1b | Chặn manager ghi trực tiếp event, zone, seat, membership. Thêm proc kiểm tra manager đúng tổ chức, trạng thái/version và audit. Repository lưu event gọi proc mới; đường bảo trì giữ contract riêng. |
| S2 | Chặn auth chèn role, chặn sửa platform_role/status; vẫn cho cập nhật các trường xác thực cần thiết. Guard INSERT không cho đăng ký ADMIN. Giữ cột platform_role vì model hiện tại còn dùng. |
| S3 | RLS trên orders/users/organization requests và coupons; buyer/manager thiếu actor không thấy dữ liệu riêng. Buyer không được đọc coupon aggregate có code, bảng role và danh sách thành viên toàn tổ chức. Actor lấy từ session đã xác thực và xóa khi trả connection về pool. **Chưa đạt cô lập runtime nếu ứng dụng dùng dbo**, xem phần còn lại. |
| S4 | DENY ghi audit trực tiếp; proc/trigger vẫn ghi qua ownership chain. Publish/cancel đặt và khôi phục actor để trigger có người thực hiện. |
| S5/S6 | Gỡ quyền worker quyết định refund, gỡ quyền admin chạy settlement/payout legacy. System approval chỉ cho cancellation + APPROVE + worker/owner. Paid callback dùng whitelist worker/owner; buyer hoàn tất đơn miễn phí qua facade riêng. |
| C1 | Callback đọc lại order dưới khóa event → hold → order; callback trùng của payment được chấp nhận không tạo compensation/phát vé lại. |
| C2 | Receipt bền vững lưu kết quả gateway đã xác minh, kể cả kết quả bất thường; đưa nghĩa vụ về NEEDS_RECONCILIATION, chặn retry khi còn receipt chưa giải quyết. FAILED → SUCCEEDED cùng reference được ghi nhận. Kiểm tra giới hạn tiền khi tạo attempt. **Chưa thay thế việc ký/xác minh và idempotency phía gateway.** |
| C3 | Trigger DELETE xóa snapshot mirror tương ứng khi recalc xóa item; recalc ba lần và confirm thành công. Giữ bảng snapshot theo lựa chọn thay thế được báo cáo cho phép. |
| C4 | Gán accepted_payment_id khi phát vé. Capture thật thứ hai vẫn được lưu và tạo đúng một compensation. Không đặt unique CAPTURED/order vì sẽ làm mất thông tin tiền thực nhận; SPEC yêu cầu một payment được chấp nhận và bù các capture khác. Kiểm tra Azure: không có đơn PAID có tiền mà thiếu accepted_payment_id. |
| C5 | Projection worker cho hold hết hạn/payment cần đối soát; facade expire chạy có giới hạn batch, không result set thừa. Giữ facade mới của module hold nếu đã tồn tại. **Không tự đổi PENDING/UNKNOWN thành FAILED theo tuổi**, cần query gateway và xử lý dead-letter thật. |
| C6 | Áp max_discount_amount cùng trần SPEC; filtered unique OTP hiện hành. App đã HMAC OTP và giới hạn năm lần thử nên giữ luồng này. |
| K1 | Chuẩn hóa các proc được sửa; refresh SQL clock sau khi chờ khóa. Không tuyên bố mọi proc của toàn project đã được kiểm toán lại. |
| K2 | Giữ khóa event bảo vệ publish/cancel/inventory. Kiểm thử 1.000 yêu cầu với 16 luồng qua, không oversell. **Chưa chứng minh không lock escalation trên settlement lớn hoặc throughput go-live.** |
| K3 | Thêm đủ chín seek index báo cáo; unique provider reference, active refund/request, compensation/payment, successful check-in và live seat. Composite FK chặn item khác event/seat khác zone; CHECK trạng thái organization giữ cả DRAFT theo project. |
| K4 | Bỏ result set khóa thừa trong các proc được sửa. Cashflow dùng captured_at trong contract retained; không ghi đè projection finance canonical mới. |
| M1/A1 | Giữ các bảng/adapter đang được SPEC và module hiện hành dùng, gỡ trigger bảo vệ last-manager trùng. Hai hàm cửa sổ check-in cùng PUBLISHED và thời gian nửa mở. Installer dừng/rollback khi batch lỗi, kiểm checksum và catalog drift, không tiếp tục sau THROW. |

## Các việc còn lại, chưa được đánh dấu hoàn tất

1. Cấu hình hiện tại dùng login **dbo/db_owner**, các credential theo principal
   chưa được cung cấp; `DatabaseConfig` còn fallback về credential chung.
   `FinanceRuntime` có factory connection ADMIN riêng. RLS/DENY được kiểm bằng
   các database user giới hạn có thật nhưng **dbo bypass RLS và DENY**. Cần cấp
   secret cho sáu tài khoản hiện có, cấu hình pool đúng principal, bỏ fallback,
   rồi chạy nghiệm thu HTTP theo từng role. Không đổi mật khẩu các tài khoản
   dùng chung để tạo cảm giác hoàn thành bước này.
2. Worker querydr VNPay, xác minh chữ ký, retry/idempotency gateway, giải quyết
   receipt reconciliation và chính sách dead-letter/outbox archive cần luồng
   ứng dụng và cấu hình nhà cung cấp. Không suy diễn thất bại từ timeout.
3. Thiết kế settlement lớn tránh escalation và đo `sys.dm_tran_locks` cần
   nghiệm thu tải riêng. Thử 1.000 hold là 1.000 yêu cầu với 16 luồng, không phải
   1.000 kết nối SQL cùng lúc.
4. Gom mô hình legacy/canonical, ledger kép, maker-checker payout, computed
   normalized email, tối ưu toàn bộ trigger/index INCLUDE, sửa projection sự
   kiện đã kết thúc và bỏ adapter là thay đổi thiết kế/migration tiếp theo.
   Maker-checker hai admin không phù hợp SPEC đang seed một admin. Không xóa
   bảng check-in/refund/payout/snapshot đang phục vụ module hiện hành.

Do các gate trên còn mở, đây là **bản sửa database và regression**, không phải
chứng nhận toàn bộ báo cáo đã hoàn tất hoặc ứng dụng sẵn sàng nhận tiền thật.

## Bằng chứng kiểm tra

- Tái hiện C1 trên contract cũ: compensation count thực tế 1, kỳ vọng 0.
- Tái hiện C3 trên contract cũ: recalc lần hai vi phạm
  `UQ_SettlementOrderSnapshots_SettlementOrder`.
- SQL Server độc lập: `SqlReportSafetyIT` **16/16**, check-in regression
  **14/14**; tổng **30**, không failure/error/skip. Bao gồm hai callback đồng
  thời, capture thêm, refund success muộn/conflict, recalc ba lần + confirm,
  role negatives, RLS, actor pool, coupon và 1.000 hold không oversell.
- Safe default suite sau sửa test gate: **625 tests, 0 failure, 0 error,
  80 skipped** do môi trường ngoài chưa bật. Skip không tính là kiểm thử SQL.
- Azure chỉ đọc: **375 assertions** qua (checksum, RLS, effective permission,
  FK/backfill và ID vé/OTP trước sửa). Số dòng hiện tại đếm bằng COUNT_BIG.
  Snapshot đầu dùng sys.partitions.rows là ước lượng; không coi đây là bằng
  chứng so sánh số dòng chính xác cho mọi bảng.
- Workflow `sql-report-safety.yml` dựng SQL Server disposable, cài contract,
  chạy 30 SQL tests và từ chối báo cáo có skips. Không dùng Azure hoặc deploy.

## Sự cố bài kiểm thử cũ và xử lý

Trong lần kiểm tra này, bài OrganizationRepository integration cũ chưa có
DB tag đã nối cấu hình chung dù tắt DB IO, tạo bốn requester và bốn tổ chức
thử trống. Đã xác định đúng ID, thời gian, email/username/name/hash fixture;
giao dịch dọn chỉ cho phép các dòng khớp hoàn toàn và không có tham chiếu
nghiệp vụ. Đã xác nhận không còn bốn requester thử này trên Azure.
Thêm DB tag, điều kiện opt-in và regression bảo đảm bài này bị skip trước khi
truy cập database. Không xóa tài khoản/dữ liệu khác.

## Bàn giao và thao tác

Backup cục bộ (ignored, không đưa dữ liệu/secrets lên Git):

- `docs/handoff/database-backups/sql-report-20261011-215314/before.json`:
  module definitions, quyền, migration ledger, constraint, ID vé/OTP trước 031.
- `docs/handoff/database-backups/sql-report-20261011-220105/before.json`:
  trước 039. Đây là snapshot đối tượng/quyền/ID, **không phải full DB backup**.
- Bằng chứng riêng: `target/sql-report-proof.xml`,
  `target/sql-report-azure-verification.json`, `target/sql-report-row-counts.json`.

PowerShell 7: `tools/acceptance/apply-sql-report.ps1` mặc định chỉ preflight;
`-Apply` yêu cầu bằng chứng 16 SQL tests thực chạy và baseline catalog đã
kiểm tra, lưu snapshot mới rồi cài trong transaction. Từ chối checksum thay
đổi hoặc dữ liệu trùng, không xóa/đoán dữ liệu để vượt kiểm tra.
`tools/acceptance/verify-sql-report.ps1` chỉ đọc; baseline khác dùng `-Baseline`.
Không chạy các file RLS riêng lẻ sau khi policy đã bind function. Luôn kiểm
ledger; sửa tiếp phải thêm migration mới, không sửa các file đã cài.
Checksum kiểm tra chấp nhận khác biệt CRLF/LF do Git checkout; thay đổi nội dung
khác vẫn bị từ chối. Ledger đã cài giữ nguyên; lần cài mới dùng hash chuẩn LF.
