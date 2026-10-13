# Giao diện quản lý và dữ liệu trình diễn — 13/10/2026

Cập nhật này thay thế quy tắc lọc nhãn demo/test ở bản đối chiếu prototype trước đó. Quyền công khai và tồn kho tiếp tục do các view/procedure database quyết định; ứng dụng không suy đoán nguồn gốc dữ liệu từ tiêu đề hoặc mô tả.

## Thay đổi

- Giữ bảng màu đỏ, trắng, đen và kiểu panel phẳng của prototype trong `docs/web-demo/prototype`.
- Sidebar hệ thống và tổ chức dùng chung thiết kế. Menu hệ thống không dẫn sang quản lý tổ chức; sidebar tổ chức chỉ giữ tổng quan, sự kiện, khuyến mãi, thành viên, soát vé, báo cáo. Tạo sự kiện nằm ở hành động của trang sự kiện.
- Mọi vai trò đăng nhập về trang khám phá. Dropdown tài khoản thêm đúng cổng theo vai trò. Nhân viên bị chuyển về soát vé khi mở trang quản lý; API tài chính vẫn kiểm tra quyền từ database.
- Customer được mở hồ sơ đăng ký tổ chức tại cả đường dẫn hiện hành và đường dẫn cũ.
- Báo cáo hiển thị tên sự kiện lấy từ `vw_FinanceEvents`. Nhật ký lấy tên người thực hiện và tên sự kiện/tổ chức liên quan; bộ lọc không yêu cầu nhập UUID.
- Bỏ mã nội bộ khỏi danh sách tổ chức, thành viên, hoàn vé và các danh sách đơn/vé. Mã vào cửa trong chi tiết vé được giữ để dùng với QR và nhập tại điểm soát vé.
- Banner tự chuyển các ảnh của những sự kiện trong trang đang xem. Các ảnh/liên kết đều lấy từ API; có nút trước/sau, chọn slide và tạm dừng. Tự chuyển dừng khi hover, focus hoặc người dùng yêu cầu giảm chuyển động.
- Danh sách sự kiện công khai, tổ chức, thẩm định, soát vé và báo cáo giới hạn 6 sự kiện mỗi trang. Backend catalog giới hạn pageSize theo cùng quy tắc; tìm kiếm và danh mục vẫn giữ nguyên chức năng.
- Phân trang dùng nút mũi tên có tên truy cập, trạng thái vô hiệu ở đầu/cuối danh sách. Nút quét vé tiếp theo không bị nhận nhầm thành nút phân trang.
- Dashboard tổ chức có biểu đồ bảy ngày từ API và bảng số tiền chính xác; không dựng số liệu mẫu ở frontend. Các số tiền lớn dùng BigInt.
- Tách JavaScript thành viên khỏi JSP để tránh xung đột template literal với JSP EL, làm trang ngắt render khi đăng nhập.

Tham khảo cách trình bày ảnh, danh mục và thẻ sự kiện tại [Ticketbox](https://ticketbox.vn/) và cách tập trung các chỉ số vận hành tại [Eventbrite Analytics](https://www.eventbrite.com/features/analytics/). Các nguồn dữ liệu nghiệp vụ của dự án vẫn là SQL Server/API.

## Dữ liệu trình diễn

Đã tạo bốn tài khoản riêng: Organization Manager, Checkin Staff, Admin và Customer. Manager và Staff thuộc tổ chức KỊCH ĐOÀN THÀNH LỘC. File `userdemo.txt` trong thư mục dự án chứa thông tin đăng nhập, liên kết sự kiện, thời gian soát vé và hướng dẫn trình diễn; file được bỏ qua bởi Git và chỉ chủ máy/SYSTEM có quyền đọc.

Sự kiện `TicketsCenter — Trải nghiệm sân khấu [demo]` có khu đứng 40 vé, giá 0đ và ảnh có sẵn của cùng tổ chức. Hồ sơ, khu vé, gửi duyệt, công khai, giữ vé, tạo đơn và hoàn tất đơn 0đ đều được thực hiện qua API. Ảnh bìa cho đúng bản ghi demo mới tạo được chuẩn bị ở tầng database. Không sửa các sự kiện hiện có.

Customer có một đơn 0đ và ba vé: một vé đã dùng, một vé chờ hoàn, một vé còn hiệu lực để trình diễn QR. Quét hợp lệ và quét trùng đã được kiểm tra bằng Checkin Staff. Admin có một yêu cầu hoàn đang chờ để xem và quyết định. Thời gian check-in và điều kiện gửi hoàn mới tiếp tục do backend kiểm tra, không bỏ qua luật nghiệp vụ để demo.

## Kiểm chứng

- Maven: 990 ca; 0 failure/error, 147 integration tests ngoại vi được bỏ qua; 843 ca thực thi thành công. WAR đóng gói thành công.
- JavaScript: 161 ca; 148 passed, 13 skipped, 0 failed. Các HTTP fixture suites yêu cầu fixture riêng không được tính vào lần chạy unit này.
- HTTP bản chạy Azure: bốn tài khoản đăng nhập về trang mua vé; buyer views cho mọi vai trò; 7 trang tổ chức, 8 trang hệ thống và trang soát vé trả HTML hợp lệ. API dashboard có 7 ngày số liệu; bốn loại báo cáo đọc database và trả tên sự kiện; audit có tên người thực hiện. Customer/staff/manager bị từ chối các API vượt quyền.
- Dữ liệu catalog: 34 sự kiện ở thời điểm kiểm tra, pageSize 6 dù yêu cầu pageSize 50. Số lượng này có thể thay đổi theo database.
- Đơn hàng, ba chi tiết vé, ảnh QR và trang yêu cầu hoàn của customer đã được kiểm tra có dữ liệu thật.
- Trình duyệt: banner có ảnh/liên kết, chuyển trang từ trang 1 sang trang 2 vẫn có 6 thẻ; 1366px và 390px không tràn ngang. Mobile hiển thị nút đăng nhập.
- Jasper/Tomcat 11: biên dịch 43 đơn vị JSP không phải fragment `_…`, 0 lỗi.
- Không đưa mật khẩu/file cấu hình thật vào Git hoặc WAR.

## Giới hạn còn lại

Không còn nút hủy/tiến trình hủy trong trang thẩm định vì các URL đó chưa có API triển khai. Form tạo lệnh chi trả được ẩn khi chưa cấu hình nhà cung cấp; vẫn xem được quyết toán và lịch sử. Thanh toán có tiền, chuyển hoàn tiền, chi trả và upload ảnh qua nhà cung cấp cần cấu hình/nghiệm thu tích hợp riêng. Việc kiểm tra đơn 0đ không xác nhận các giao dịch có tiền đã chạy thực tế.

Bản giao diện mới được chạy ở `http://127.0.0.1:18083/ticketscenter/`, giữ phiên cũ trên cổng 18082. Nếu khởi động lại bằng runner chuẩn, cổng trở về cấu hình trong `application.properties`.
