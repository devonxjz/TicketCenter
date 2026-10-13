# Đối chiếu prototype và giao diện chạy thật — 13/10/2026

## Phạm vi và kết quả

Giữ thiết kế đỏ/trắng/đen, kiểu chữ, đường viền, cấu trúc bảng/panel và thanh điều hướng của prototype. Các trang triển khai là JSP/API, không phục vụ bản HTML prototype hoặc dùng danh sách sự kiện giả ở frontend. Prototype trong `docs/web-demo/prototype` chỉ là tài liệu thiết kế.

Chọn vé nằm trong trang chi tiết. Đường dẫn `/select-tickets` cũ chuyển tiếp cùng giao diện, giữ nguyên tham số sự kiện. Kho vé, lượt giữ, đếm thời gian, giá, mã giảm giá, đơn hàng và bắt đầu thanh toán tiếp tục dùng API. Chỉ máy chủ có thể xác nhận giữ vé/thanh toán thành công. Đăng nhập giữ đường quay lại sự kiện; đăng xuất dùng POST và CSRF.

## Đối chiếu 24 khung

| Khung | Trang chạy thật | Nguồn/chức năng |
|---|---|---|
| 01 Danh sách sự kiện | `/` | Catalog, tìm kiếm, danh mục, bộ lọc, phân trang API |
| 02 Chi tiết và chọn vé | `/event-detail?eventId=…` | Chi tiết, khu vé, ghế còn trống, Hold API |
| 03 Đăng nhập/OTP | `/auth/login`, `/auth/verify-email` | Identity API, không hiển thị tài khoản demo |
| 04 Giữ vé/thanh toán | `/checkout` | Hold, đơn hàng, coupon, payment initiation API |
| 05 Kết quả thanh toán | Trang kết quả payment | Trạng thái do máy chủ xác nhận, liên kết chi tiết đơn |
| 06 Đơn của tôi | `/me/orders`, `/orders/…` | Dữ liệu đơn theo tài khoản, lọc/phân trang |
| 07 Vé/QR | `/me/tickets`, `/tickets/…` | Vé theo quyền sở hữu, QR và tải QR thật |
| 08 Hoàn vé | `/my-refunds`, `/refund-requests` | Yêu cầu và trạng thái từ backend |
| 09 Yêu cầu tổ chức | `/organizer/request` | Nộp hồ sơ thật |
| 10 Tổng quan tổ chức | `/organizer/dashboard` | Phạm vi tổ chức và số liệu API |
| 11 Thành viên | `/organizer/members` | Thành viên/phân quyền backend |
| 12 Sự kiện tổ chức | `/organizer/events` | Sự kiện theo tổ chức, hành động theo quyền |
| 13 Biên tập sự kiện | `/organizer/event-editor` | Lưu sự kiện/khu/ghế/ảnh qua API |
| 14 Coupon | `/organizer/coupons` | Quản lý coupon API |
| 15 Chọn sự kiện check-in | `/check-ins` | Danh sách sự kiện được cấp quyền |
| 16 Quét vé | Trang scanner check-in | Đọc QR và đối chiếu API, không mô phỏng quét thành công |
| 17 Báo cáo tổ chức | `/finance/reports` | Phạm vi tổ chức, bộ lọc và xuất báo cáo |
| 18 Tổng quan quản trị | `/admin/dashboard`, `/finance/overview` | Số liệu và đường tới hàng chờ thật |
| 19 Duyệt tổ chức | `/admin/orgs/approval` | Danh sách/duyệt/từ chối API; xử lý đúng envelope `data` |
| 20 Duyệt sự kiện/hủy | `/admin/events/approval` | Quy trình sự kiện và yêu cầu hủy backend |
| 21 Duyệt hoàn vé | `/admin/refunds/approval` | Quyết định và hàng chờ backend |
| 22 Hoa hồng/đối soát | `/finance/commission`, `/finance/settlements` | Chính sách và trạng thái quyết toán thật |
| 23 Báo cáo/audit | `/finance/reports`, `/finance/audit` | Báo cáo và nhật ký API |
| 24 Hồ sơ | `/profile` | Hồ sơ tài khoản thật |

Đây là đối chiếu mã nguồn, hợp đồng API và thiết kế chung; không phải chứng nhận mọi thao tác production đã chạy thực tế. Các trang có phân quyền cần kiểm tra chấp nhận với tài khoản thật của từng vai trò.

## Các lỗi tích hợp đã sửa

- Đường quay lại chọn vé bị mất khi POST đăng nhập; liên kết auth không mã hóa tham số redirect.
- Đơn/vé chuyển người chưa đăng nhập tới `/login` không tồn tại.
- JavaScript template literal trong JSP duyệt tổ chức gây lỗi biên dịch/500.
- Trang duyệt tổ chức đọc sai lớp `data`, làm danh sách rỗng và báo thất bại sau quyết định thành công.
- Tên tổ chức/thông báo có thể chèn markup hoặc tham số JavaScript; dùng escape và thuộc tính dữ liệu.
- Số lượng vé không nguyên gây lỗi BigInt trong tóm tắt lựa chọn.
- Bootstrap bị nạp lặp trên các trang dùng layout chung.
- Phê duyệt tổ chức tự tạo sự kiện với lịch/địa điểm mặc định: real mode giờ chỉ duyệt tổ chức và chính sách; BTC tạo sự kiện với thông tin thực tế trong trình biên tập.

## Dữ liệu và giới hạn production

Trong real mode, máy chủ loại các sự kiện có nhãn demo/test/giả lập ở tiêu đề hoặc mô tả. Cùng điều kiện áp dụng cho tổng số/danh sách/chi tiết/khu/ghế và đọc công khai cũ. Khoảng cache cũ không được phép đưa bản ghi đó trở lại. Hold options, tạo lượt giữ và lượt giữ hiện có của các sự kiện bị chặn trả 404; không sửa tồn kho hoặc xóa dữ liệu Azure. Đọc lượt giữ giải phóng kết nối trước khi kiểm tra để tránh chiếm hai kết nối đồng thời.

Kiểm tra GET Azure qua bản chạy mới trả **0 sự kiện** sau bộ lọc. Đây là dữ liệu hiện tại, không dùng dữ liệu lấp chỗ. Bộ lọc nhãn chỉ là cơ chế loại bản ghi biết là giả: bản ghi không có nhãn vẫn cần xác minh nguồn gốc. Nó không xác nhận các đơn hàng/lịch sử tài chính đã trở thành dữ liệu thật. Không xóa nhãn để biến dữ liệu mẫu thành dữ liệu production.

Thanh toán/hoàn tiền/chi trả production còn cần cấu hình và kiểm chứng nhà cung cấp thật. Các kiểm tra này không chuyển tiền, gửi mail, tạo đơn/giữ vé, duyệt tổ chức hoặc sửa Azure. Không bật lại bộ xử lý tài chính giả lập để làm nút có vẻ hoạt động.

## Kiểm chứng

- Java: 989 ca, 0 lỗi/failed; 147 ca tích hợp ngoài được bỏ qua; 842 ca thực thi thành công.
- JavaScript: 158 ca, 145 passed, 13 skipped, 0 failed.
- Jasper/Tomcat 11: biên dịch 42 đơn vị JSP không phải fragment `_…`, 0 lỗi; các trang tài chính biên dịch cùng include thực tế.
- Trình duyệt: catalog API, trang chi tiết, liên kết mở booking cùng trang, trạng thái cần đăng nhập; chiều rộng 390px không tràn ngang. Quan sát chi tiết trước khi chặn nhãn không được coi là chứng cứ nguồn dữ liệu thật.
- Rà soát độc lập giao diện và thay đổi máy chủ; hai lỗi hợp đồng đăng nhập/quyết định tổ chức đã được sửa và kiểm thử hồi quy.
- Không đưa `application.properties`, mật khẩu hoặc file `.local` vào Git/WAR.
