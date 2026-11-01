# TicketsCenter — Prototype tĩnh và 24 page frame

Nguồn chuẩn: [spec.md](../../../spec.md), cập nhật 05/10/2026, và [sơ đồ lớp](../../classdiagram/diagram.md). Bộ HTML này minh họa màn hình UI-01–UI-24 của mục 9; chưa phải ứng dụng Servlet/JPA/SQL Server đã triển khai.

Các dữ liệu, thông báo và chuyển trang là mẫu. Nút đăng nhập, OTP, giữ vé, phê duyệt, thanh toán, hoàn tiền, chi trả, camera và xuất CSV chưa chứng minh nghiệp vụ chạy ở backend; không gửi email hoặc chuyển tiền thật. VNPAY Sandbox là tích hợp dự kiến trong đặc tả. Chức năng và trạng thái còn thiếu được mô tả dưới đây, không được tính là đã nghiệm thu.

## Mô hình hiện hành và ranh giới dữ liệu

Có đúng 15 class nghiệp vụ: User, Organization, Event, Zone, Seat, TicketHold, TicketHoldItem, Order, OrderItem, Ticket, Payment, Coupon, CommissionRule, Refund, Settlement.

- EventCategory là kiểu giá trị/danh mục. User có một platformRole (`CUSTOMER`/`ADMIN`) và map organizationRoles theo từng Organization. Trạng thái/lịch sử liên kết nằm ở persistence, không tạo class Membership nghiệp vụ.
- Đăng ký yêu cầu userName duy nhất, email và mật khẩu. Đăng nhập thường dùng email/mật khẩu; quản trị local/demo dùng `admin / admin`. Mẫu đăng nhập chỉ chuyển trang và không xác thực tài khoản thật.
- UI-11 thêm tài khoản đã tồn tại bằng userName và cấp MANAGER/CHECK_IN_STAFF trực tiếp; không mời qua email. revokeRole bỏ quyền hiệu lực tại tổ chức, giữ lịch sử và User. Không loại/hạ quyền Manager đang hoạt động cuối cùng.
- Organization giữ cùng hồ sơ từ DRAFT → PENDING_APPROVAL → APPROVED/REJECTED. Duyệt cập nhật cùng Organization, tạo CommissionRule ban đầu và cấp MANAGER cho requester cùng giao dịch; không có OrganizationRequest nghiệp vụ hoặc Organization thứ hai.
- Mỗi Payment thuộc đúng một Order; Order có 0..* lần thu. acceptedPayment chỉ khoản CAPTURED của chính đơn được chọn để hoàn tất đơn. PENDING/UNKNOWN chặn lần thu mới và thay coupon; FAILED đã xác định mới thử lại trong hạn Hold. Đơn 0đ ghi Order.paidAt, phát hành vé mà không tạo Payment.
- Refund giữ nghĩa vụ, purpose, lý do/quyết định, amount và currentAttemptId kể cả chưa thực hiện hoặc hoàn 0đ. RefundTransferLog là lịch sử kỹ thuật. beginAttempt → PROCESSING; SUCCEEDED → COMPLETED, FAILED → RETRYABLE, UNKNOWN → NEEDS_RECONCILIATION. Hoàn 0đ completeWithoutTransfer, vẫn giữ Refund và không tạo lần chuyển tiền. Không có RefundRequest nghiệp vụ tách riêng.
- Settlement giữ snapshot grossRevenue/totalRefund/totalCommission, paidAmount/pendingAmount. SettlementOrderSnapshot và SettlementTransferLog là dữ liệu kỹ thuật; không có class SettlementItem/Payout nghiệp vụ. Chỉ DRAFT được tính lại; CONFIRMED đóng băng số liệu. paid + pending ≤ netPayable; PAID khi paid = net và pending = 0. netPayable = 0 không tạo lần chi giả.

[js/data.js](js/data.js) là fixture/DTO minh họa. Các trường categoryId, orgName, discountValue và nhãn trình bày hỗ trợ JavaScript mẫu, không bổ sung thuộc tính hay class cho sơ đồ chuẩn. Các fixture tiền/quét dùng thời điểm giả lập riêng; không đại diện dữ liệu đang vận hành. [js/app.js](js/app.js) chứa tương tác trình duyệt mẫu; các trang hiện tại cũng có script nội tuyến.

## Quy tắc màn hình cần thể hiện khi triển khai

Giữ vé cần đăng nhập/xác minh email; một Hold ACTIVE/người, tối đa 8 vé cùng Event, TTL 10 phút do máy chủ quyết định và không gia hạn khi tạo Order/áp mã. Ghế chọn trên trình duyệt chưa đồng nghĩa đã giữ. Mỗi Hold tạo tối đa một Order với giá/nhãn snapshot. Coupon tối đa một/đơn và trần 30%; hoàn sau thanh toán không trả lượt đã dùng.

Event có `saleStart < saleEnd <= startTime < endTime`. Chỉ Event PUBLISHED và trong cửa sổ bán mới mua được. DRAFT/REJECTED được sửa hồ sơ/lịch/cấu trúc; PENDING_APPROVAL khóa sửa. Sau công khai chỉ đổi giá niêm yết cho Hold mới, giữ cấu trúc ghế/khu và CommissionRule đã chọn.

UI-05 trình bày kết quả đã xác thực ở máy chủ, không coi trang quay từ cổng là bằng chứng thu tiền. Thu muộn/trùng hoặc Event bị hủy tạo Refund PAYMENT_COMPENSATION gắn Payment, không có Ticket và không phát hành vé; doanh thu vé/hoa hồng loại khoản bù trừ.

UI-08/21 dùng tổng Ticket.paidAmount của vé ACTIVE cùng Order, trước startTime, chưa có nghĩa vụ mở. Gửi yêu cầu chuyển vé REFUND_PENDING. Duyệt chưa đồng nghĩa đã hoàn; chỉ thành công/hoàn 0đ mới hoàn tất vé và trả kho. UNKNOWN phải đối chiếu, không thử mù quáng. Hủy Event chỉ trước startTime; dừng bán/check-in và tiếp tục/tạo Refund cho vé đủ điều kiện theo từng Order, không trùng nghĩa vụ. Vé USED là ngoại lệ cần quản trị xử lý.

Check-in kiểm tra tổ chức/quyền, đúng Event, vé ACTIVE và khoảng `[startTime − 60 phút, endTime)`. Nhân viên chỉ xem check-in của tổ chức, không vào báo cáo tài chính/thành viên. Chia sẻ QR chỉ chia sẻ từng vé, không chuyển chủ Order.

Hoa hồng tính từng Order trên tiền còn lại sau hoàn thành công: còn lại 0 → phí 0; còn lại dương → min(còn lại, còn lại × tỷ lệ + phí cố định). Quy tắc phí trên màn hình là ví dụ cấu hình, không phải mức kinh doanh đã chốt. Xác nhận Settlement sau endTime, kể cả Event hủy, và hết Payment/Refund/công việc chưa xử lý. Báo cáo/CSV phải cùng bộ lọc/công thức/phạm vi; phân biệt tiền thu, doanh thu vé, tiền hoàn, số phải trả, đã chi và đang chờ.

## Danh mục 24 màn hình

| Mã màn hình | Tên màn hình trong spec.md | File HTML độc lập | Nội dung minh họa/luồng dự kiến |
|---|---|---|---|
| **UI-01** | Danh sách & Tìm kiếm sự kiện | [`index.html`](index.html) | Bộ lọc danh mục, banner và thẻ sự kiện công khai |
| **UI-02** | Chi tiết sự kiện & Chọn vé | [`frames/UI-02-chi-tiet-chon-ve.html`](frames/UI-02-chi-tiet-chon-ve.html) | Sơ đồ rạp hát thực tế (Stage, Hàng A-B VIP, C-D Thường, Khu đứng GA, giỏ vé cố định) |
| **UI-03** | Đăng ký, Đăng nhập, OTP 6 số | [`frames/UI-03-dang-nhap-otp.html`](frames/UI-03-dang-nhap-otp.html) | Đăng ký có userName duy nhất; đăng nhập email; OTP và khôi phục mật khẩu là luồng mô tả |
| **UI-04** | Lượt giữ vé & Thanh toán | [`frames/UI-04-giu-ve-thanh-toan.html`](frames/UI-04-giu-ve-thanh-toan.html) | Đồng hồ đếm ngược 10 phút, kiểm tra coupon &le; 30% trần, cổng VNPAY / đơn 0đ |
| **UI-05** | Kết quả thanh toán VNPAY | [`frames/UI-05-ket-qua-thanh-toan.html`](frames/UI-05-ket-qua-thanh-toan.html) | Hóa đơn giao dịch điện tử, trạng thái CAPTURED, UNKNOWN, bù trừ |
| **UI-06** | Đơn hàng của tôi | [`frames/UI-06-don-cua-toi.html`](frames/UI-06-don-cua-toi.html) | Lịch sử mua vé, mã đơn hàng, đường dẫn xem vé và yêu cầu hoàn tiền |
| **UI-07** | Vé điện tử & Mã QR | [`frames/UI-07-ve-ma-qr.html`](frames/UI-07-ve-ma-qr.html) | Thẻ vé máy bay/sự kiện thực tế có răng cưa perforation, mã QR vector sắc nét, chia sẻ |
| **UI-08** | Yêu cầu hoàn vé | [`frames/UI-08-yeu-cau-hoan-ve.html`](frames/UI-08-yeu-cau-hoan-ve.html) | Chọn vé chưa dùng, tính paidAmount, lý do khách gửi (CUSTOMER_REQUEST) |
| **UI-09** | Đăng ký tạo tổ chức | [`frames/UI-09-yeu-cau-tao-to-chuc.html`](frames/UI-09-yeu-cau-tao-to-chuc.html) | Form nộp hồ sơ xin làm ban tổ chức sự kiện |
| **UI-10** | Bảng điều khiển Tổ chức | [`frames/UI-10-tong-quan-to-chuc.html`](frames/UI-10-tong-quan-to-chuc.html) | Giao diện SaaS chuyên nghiệp kiểu Stripe (sidebar trái, biểu đồ KPI, sự kiện) |
| **UI-11** | Quản lý thành viên | [`frames/UI-11-thanh-vien-to-chuc.html`](frames/UI-11-thanh-vien-to-chuc.html) | Thêm trực tiếp bằng userName; map organizationRoles; bảo vệ Manager cuối |
| **UI-12** | Danh sách sự kiện tổ chức | [`frames/UI-12-danh-sach-su-kien-to-chuc.html`](frames/UI-12-danh-sach-su-kien-to-chuc.html) | Trạng thái DRAFT, PENDING_APPROVAL, PUBLISHED, hiển thị lý do nếu bị từ chối |
| **UI-13** | Biên tập sự kiện, khu & ghế | [`frames/UI-13-bien-tap-su-kien-ghe.html`](frames/UI-13-bien-tap-su-kien-ghe.html) | Ràng buộc `saleStart < saleEnd <= startTime < endTime`, tạo số hàng ghế |
| **UI-14** | Quản lý mã giảm giá | [`frames/UI-14-ma-giam-gia.html`](frames/UI-14-ma-giam-gia.html) | Quản lý mã coupon của tổ chức, kiểm tra hạn mức `maxUses` |
| **UI-15** | Chọn sự kiện soát vé | [`frames/UI-15-chon-su-kien-checkin.html`](frames/UI-15-chon-su-kien-checkin.html) | Giới hạn theo khung giờ hợp lệ `[startTime - 60 phút, endTime)` |
| **UI-16** | Cổng kiểm soát vé (Scanner) | [`frames/UI-16-quet-ma-ve.html`](frames/UI-16-quet-ma-ve.html) | Giao diện mobile-first, viewfinder camera có tia laser, phản hồi hợp lệ/đã dùng |
| **UI-17** | Báo cáo doanh thu & CSV | [`frames/UI-17-bao-cao-to-chuc.html`](frames/UI-17-bao-cao-to-chuc.html) | Doanh thu, hoàn tiền, hoa hồng, thực nhận (Net Payable) & nút xuất CSV |
| **UI-18** | Tổng quan Quản trị viên | [`frames/UI-18-tong-quan-quan-tri.html`](frames/UI-18-tong-quan-quan-tri.html) | Bảng điều hành Admin nền tảng, triage hàng đợi phê duyệt |
| **UI-19** | Duyệt yêu cầu tổ chức | [`frames/UI-19-duyet-yeu-cau-to-chuc.html`](frames/UI-19-duyet-yeu-cau-to-chuc.html) | Duyệt cùng Organization, tạo CommissionRule và cấp MANAGER cùng giao dịch dự kiến |
| **UI-20** | Duyệt & Hủy sự kiện | [`frames/UI-20-duyet-huy-su-kien.html`](frames/UI-20-duyet-huy-su-kien.html) | Công khai sự kiện hoặc hủy khẩn cấp (tạo/tiếp tục Refund cho vé đủ điều kiện, giữ ngoại lệ USED) |
| **UI-21** | Xét duyệt hoàn tiền | [`frames/UI-21-duyet-hoan-ve.html`](frames/UI-21-duyet-hoan-ve.html) | Xét lý do hoàn của khách, duyệt hoàn tiền mô phỏng, cập nhật trả kho |
| **UI-22** | Hoa hồng & Quyết toán | [`frames/UI-22-chinh-sach-hoa-hong-doi-soat.html`](frames/UI-22-chinh-sach-hoa-hong-doi-soat.html) | Công thức chuẩn mục 6.11, snapshot tổng, paidAmount/pendingAmount; lịch sử chi kỹ thuật |
| **UI-23** | Báo cáo hệ thống & Audit | [`frames/UI-23-bao-cao-he-thong-audit.html`](frames/UI-23-bao-cao-he-thong-audit.html) | Nhật ký AuditLog truy vết actor, action, target entity, timestamp |
| **UI-24** | Hồ sơ tài khoản người dùng | [`frames/UI-24-ho-so-tai-khoan.html`](frames/UI-24-ho-so-tai-khoan.html) | Thông tin cá nhân, trạng thái xác thực email và danh sách tổ chức tham gia |


## Xem, đồng bộ và kiểm tra

Mở [index.html](index.html), dùng ma trận điều hướng 24 màn hình. Mỗi HTML ở thư mục này là nguồn của bản tương ứng trong [frames](frames/); các bản độc lập có cùng nội dung/form/script và đổi đường dẫn tương đối để hoạt động trong thư mục con.

Sau khi sửa trang nguồn, chạy từ thư mục dự án:

```text
node docs/web-demo/prototype/sync_frames.js
node docs/web-demo/prototype/verify_frames.js
```

`generate_individual_frames.js` dùng chung cơ chế với `sync_frames.js`; có thể chạy từ bất kỳ thư mục nào. Kiểm tra xác nhận đủ 24 cặp có nội dung đồng nhất, nhãn mock và đường dẫn nội bộ tồn tại. Đây là kiểm tra tài liệu/prototype, không thay unit/integration test, kiểm thử quyền/cạnh tranh hay nghiệm thu SQL Server ở spec.md.
