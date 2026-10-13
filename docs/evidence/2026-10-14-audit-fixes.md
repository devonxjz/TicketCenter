# Kiểm tra và sửa lỗi sau khi đồng bộ giao diện

## Các lỗi đã sửa

- Lưu ảnh khi chưa cấu hình Cloudinary: thay URL mô phỏng bằng tệp ảnh được lưu thật và phục vụ qua ứng dụng. Ảnh còn tồn tại sau khởi động lại, gửi lại cùng yêu cầu không ghi đè nội dung khác, lỗi ghi đĩa được báo về người dùng. PNG, JPEG và WebP được kiểm tra trước khi lưu.
- Danh sách sự kiện: khôi phục từ khóa, danh mục, ngày và trang từ URL; nút quay lại/đi tới cập nhật cả biểu mẫu và kết quả. Tải lại trang thứ hai giữ nguyên trang, mỗi trang yêu cầu sáu sự kiện. Kiểm tra ngày nằm trong luồng xử lý lỗi, tránh lỗi JavaScript không được xử lý.
- Đăng nhập: khách hàng quay về đúng trang đăng ký tổ chức. Kiểm tra quyền theo đường dẫn đã giải mã, không hiểu nhầm từ khóa tìm kiếm là đường dẫn quản trị; nhân viên chỉ quay về khu vực soát vé phù hợp.
- Tổng quan tổ chức và mã giảm giá: bỏ phản hồi cũ khi đổi tổ chức, kể cả phản hồi lỗi. Xóa số liệu cũ khi bắt đầu tải. Chỉ yêu cầu ô số tiền của loại giảm giá đang chọn; kiểm tra thứ tự ngày và chặn gửi biểu mẫu lặp.
- Đồng bộ lại bốn tài khoản trình diễn với mật khẩu đã lưu trong `userdemo.txt`. File này tiếp tục nằm ngoài Git.

## Kết quả kiểm tra

- Java: 1.005 kiểm thử, không có lỗi hoặc thất bại; 147 kiểm thử cần dịch vụ ngoài được bỏ qua theo cấu hình.
- JavaScript sau khi ghép cập nhật dev: 168 kiểm thử, 155 đạt, 13 bỏ qua, không thất bại.
- Biên dịch 43 trang JSP: không có lỗi.
- Kiểm tra HTTP trên Azure SQL: đăng nhập bốn vai trò; bảy trang quản lý tổ chức, trang soát vé, tám trang quản trị, đơn hàng, vé, QR, hoàn vé, báo cáo và số liệu từ database đều đạt.
- Lưu bản nháp kiểm thử qua multipart với ảnh mặc định; tải ảnh lên qua cả API có phiên bản và API tương thích; kiểm tra gửi lại cùng yêu cầu, từ chối ảnh giả, từ chối khách hàng sửa ảnh; GET/HEAD/ETag đạt. Ảnh được xác minh sau khởi động lại; bản nháp kiểm thử được dọn qua API.
- Kiểm tra trực tiếp trên trình duyệt: tìm kiếm cho ra kết quả thật, quay lại/đi tới khôi phục kết quả, trang thứ hai giữ nguyên sau tải lại và hiển thị sáu thẻ sự kiện.

## Cấu hình ảnh

Migration `061_persistent_local_event_images.sql` đã được áp lên database đang dùng, có sao lưu định nghĩa thủ tục trước khi cập nhật. Migration chỉ bổ sung đường dẫn ảnh nội bộ có tên bất biến, giữ nguyên kiểm tra quyền và URL nhà cung cấp hiện có. Không có dòng sự kiện chứa URL Cloudinary mô phỏng trong database khi kiểm tra.

Khi dùng lưu trữ trên máy, giữ thư mục `event.image.directory` qua các lần triển khai và sao lưu cùng dữ liệu. Khi chạy nhiều máy chủ, cấu hình Cloudinary để các máy cùng truy cập được ảnh. `app.base.url` cần chứa đúng đường dẫn triển khai ứng dụng.

Các kiểm tra trên không thay thế kiểm thử với nhà cung cấp thanh toán hoặc email thật; bộ kiểm tra này không thực hiện giao dịch tài chính bên ngoài.
