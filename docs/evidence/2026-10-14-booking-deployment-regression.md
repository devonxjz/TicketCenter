# Sửa lỗi chọn ghế, bỏ qua checkout và combo box coupon

## Nguyên nhân

Sau lần triển khai giao diện/coupon, ứng dụng cổng 18082 chạy từ worktree `coupon-selector-theme`. Bản đó đã có giao diện coupon mới nhưng chưa chứa các sửa lỗi ghế và nút mua nhanh đang ở workspace chính. Đối chiếu mã phục vụ thực tế cho thấy `ticket-hold.js` vẫn có `payDirect`, tự tạo payment và chuyển sang VNPAY; ghế trống bị disabled khi số lượng yêu cầu là 0. Đã tái hiện trên trình duyệt: CAT 1A còn 13 ghế nhưng mọi checkbox ghế trống bị khóa.

Ngoài ra, checkout tải lại vô điều kiện khi window nhận focus. Native select có thể trả focus về window trước khi gửi sự kiện đổi lựa chọn; việc tải lại làm khóa và xây lại danh sách coupon. Kiểm thử tái hiện trường hợp này thất bại trước sửa, đạt sau sửa.

## Xử lý

- Triển khai lại từ workspace chính, giữ giao diện và endpoint coupon mới, đồng thời đưa đầy đủ sửa ghế và checkout vào ứng dụng.
- Ghế trống chọn trực tiếp được từ số lượng 0, tự đồng bộ số lượng; ghế đã bán vẫn khóa và giữ giới hạn 8 vé.
- “Thanh toán ngay” chỉ giữ vé và mở `/checkout?prepare=1&holdId=…`. Chỉ “Tiếp tục thanh toán” ở checkout mới gửi yêu cầu payment.
- Không tải lại checkout vì window focus khi native select đang được focus. Kiểm tra hết hạn và khi trang trở lại từ trạng thái ẩn vẫn giữ nguyên.
- Đổi phiên bản URL tài nguyên: `ticket-hold.js?v=seat-coupon-9` và `order-checkout.js?v=coupon-select-7`.

## Bằng chứng trên bản chạy chính

- Chọn trực tiếp CAT 1A/A1: checkbox được chọn, số lượng từ 0 thành 1, tổng 2.000.000 ₫.
- Bấm “Thanh toán ngay”: trình duyệt ở checkout của TicketsCenter, không chuyển VNPAY.
- Coupon `SUKIEN005` tải từ server; chọn mã giảm 200.000 ₫, tổng còn 1.800.000 ₫. Chọn “Không dùng mã” khôi phục 2.000.000 ₫, chọn lại mã vẫn áp dụng được.
- Order `041e7485-fa95-497c-9790-4c28a59de446` được đối chiếu SQL: PENDING_PAYMENT, giá 2.000.000, giảm 200.000, tổng 1.800.000, 0 payment và 0 vé phát hành. Không gọi VNPAY để kiểm tra lỗi này.
- Mã JavaScript thực tế trả về qua HTTP khớp workspace sau triển khai, gồm ticket-hold, order-checkout và site-shell; template sự kiện tham chiếu đúng phiên bản mới.
- Java: 1.044 test, 0 lỗi/thất bại, 151 bỏ qua theo cấu hình dịch vụ ngoài; build và readiness đạt.
- JavaScript: 234 test, 232 đạt, 2 bỏ qua, 0 thất bại. Riêng ghế/checkout: 80/80 đạt, gồm regression focus của native coupon select.

Log riêng: `.local/booking-restored-startup.log`, `booking-restored-all-js.log`, `booking-restored-deployment.json`, `booking-restored-checkout-db.json`, `coupon-native-focus-red.log` và `booking-regression-green.log`.

![Checkout áp dụng coupon](E:/TicketCenter/.local/booking-restored-coupon.png)
