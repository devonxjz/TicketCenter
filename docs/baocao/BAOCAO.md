> Đồng bộ ngày 06/10/2026 với [đặc tả chuẩn](../../spec.md) và [sơ đồ 15 lớp nghiệp vụ](../classdiagram/diagram.md). Nội dung dưới đây mô tả yêu cầu và thiết kế; chưa phải kết quả triển khai.

# MỤC LỤC

## CHƯƠNG 1: KHẢO SÁT HIỆN TRẠNG

- 1.1. Tổng quan về bài toán bán vé sự kiện
  - 1.1.1. Đặc điểm hoạt động tổ chức sự kiện và phân phối vé
  - 1.1.2. Các bên tham gia và nhu cầu sử dụng hệ thống
- 1.2. Khảo sát quy trình nghiệp vụ bán vé sự kiện
  - 1.2.1. Quản lý đơn vị tổ chức và thông tin sự kiện
  - 1.2.2. Quản lý khu vực, chỗ ngồi và số lượng vé
  - 1.2.3. Đặt vé, áp dụng khuyến mãi và thanh toán
  - 1.2.4. Phát hành vé và kiểm soát vào cửa
  - 1.2.5. Xử lý yêu cầu hoàn vé và hủy sự kiện
  - 1.2.6. Quản lý doanh thu, hoa hồng, đối soát và chi trả
- 1.3. Khảo sát dữ liệu và thông tin quản lý
  - 1.3.1. Thông tin người mua và đơn vị tổ chức
  - 1.3.2. Thông tin sự kiện, khu vực và vé
  - 1.3.3. Thông tin đơn hàng, thanh toán và hoàn tiền
  - 1.3.4. Thông tin kiểm soát vào cửa và báo cáo
- 1.4. Định hướng xây dựng hệ thống TicketsCenter
  - 1.4.1. Mục tiêu của hệ thống
  - 1.4.2. Phạm vi chức năng và nhóm người sử dụng
  - 1.4.3. Giới hạn của đồ án và phạm vi mô phỏng

## CHƯƠNG 2: DANH SÁCH YÊU CẦU

- 2.1. Phân tích yêu cầu
  - 2.1.1. Yêu cầu chức năng
    - 2.1.1.1. Yêu cầu chức năng nghiệp vụ
      - 2.1.1.1.1. Lưu trữ
      - 2.1.1.1.2. Tra cứu
      - 2.1.1.1.3. Tính toán
      - 2.1.1.1.4. Kết xuất
    - 2.1.1.2. Yêu cầu chức năng hệ thống
      - 2.1.1.2.1. Môi trường hoạt động
      - 2.1.1.2.2. Phân quyền
  - 2.1.2. Yêu cầu phi chức năng
    - 2.1.2.1. Liên quan đến người dùng
      - 2.1.2.1.1. Tính tiến hóa
      - 2.1.2.1.2. Tính tiện dụng
      - 2.1.2.1.3. Tính hiệu quả
      - 2.1.2.1.4. Tính tương thích
    - 2.1.2.2. Liên quan đến chuyên viên tin học
      - 2.1.2.2.1. Tính tái sử dụng
- 2.2. Quy trình tác nghiệp
  - 2.2.1. Quy trình đăng ký, xác minh email và quản lý tài khoản
  - 2.2.2. Quy trình đăng ký và xét duyệt tổ chức
  - 2.2.3. Quy trình quản lý thành viên và phân quyền trong tổ chức
  - 2.2.4. Quy trình tạo sự kiện, thiết lập khu vé và xét duyệt công khai
  - 2.2.5. Quy trình quản lý mã giảm giá
  - 2.2.6. Quy trình tìm kiếm sự kiện, chọn chỗ và giữ vé
  - 2.2.7. Quy trình lập đơn, áp dụng mã giảm giá, xử lý thanh toán và phát hành vé
  - 2.2.8. Quy trình hủy lượt giữ và xử lý lượt giữ hết hạn
  - 2.2.9. Quy trình tra cứu đơn hàng, nhận và chia sẻ mã QR vé
  - 2.2.10. Quy trình kiểm tra vé và xác nhận vào cửa
  - 2.2.11. Quy trình yêu cầu, xét duyệt và xử lý hoàn vé
  - 2.2.12. Quy trình hủy sự kiện và hoàn vé tự động
  - 2.2.13. Quy trình thiết lập chính sách hoa hồng, đối soát và chi trả cho tổ chức
  - 2.2.14. Quy trình thống kê hoạt động và xuất báo cáo

## CHƯƠNG 3: MÔ HÌNH HÓA YÊU CẦU

- 3.1. Nhận diện tác nhân và chức năng của hệ thống
- 3.2. Danh sách các chức năng và mô tả chung
- 3.3. Lược đồ Use case
  - 3.3.1. Lược đồ 3.1 - Use case tổng quát hệ thống TicketsCenter
  - 3.3.2. Lược đồ 3.2 - Use case quản lý tài khoản và xác minh email
  - 3.3.3. Lược đồ 3.3 - Use case đăng ký, xét duyệt và quản lý tổ chức
  - 3.3.4. Lược đồ 3.4 - Use case quản lý sự kiện, khu vực và ghế
  - 3.3.5. Lược đồ 3.5 - Use case tra cứu sự kiện, giữ vé và tạo đơn hàng
  - 3.3.6. Lược đồ 3.6 - Use case quản lý và sử dụng mã giảm giá
  - 3.3.7. Lược đồ 3.7 - Use case thanh toán và phát hành vé
  - 3.3.8. Lược đồ 3.8 - Use case quản lý vé và kiểm soát vào cửa
  - 3.3.9. Lược đồ 3.9 - Use case hoàn vé, hoàn tiền và hủy sự kiện
  - 3.3.10. Lược đồ 3.10 - Use case quản lý hoa hồng, đối soát và chi trả
  - 3.3.11. Lược đồ 3.11 - Use case báo cáo, xuất dữ liệu và tra cứu nhật ký hoạt động
- 3.4. Đặc tả Use case
  - 3.4.1. Đăng ký tài khoản
  - 3.4.2. Xác minh email bằng OTP
  - 3.4.3. Đăng nhập
  - 3.4.4. Đặt lại mật khẩu bằng OTP
  - 3.4.5. Cập nhật hồ sơ cá nhân
  - 3.4.6. Tìm kiếm và lọc sự kiện
  - 3.4.7. Xem chi tiết sự kiện, giá vé và tình trạng chỗ
  - 3.4.8. Chọn chỗ và giữ vé
  - 3.4.9. Hủy lượt giữ vé
  - 3.4.10. Tạo đơn mua vé
  - 3.4.11. Áp dụng và bỏ mã giảm giá
  - 3.4.12. Thanh toán qua VNPAY Sandbox
  - 3.4.13. Xem đơn mua vé và vé điện tử
  - 3.4.14. Chia sẻ mã QR của vé
  - 3.4.15. Gửi yêu cầu hoàn vé và xem kết quả
  - 3.4.16. Gửi yêu cầu tạo tổ chức và xem kết quả
  - 3.4.17. Duyệt hoặc từ chối yêu cầu tạo tổ chức
  - 3.4.18. Thêm thành viên vào tổ chức
  - 3.4.19. Thay đổi vai trò và vô hiệu hóa thành viên
  - 3.4.20. Tạo và chỉnh sửa sự kiện
  - 3.4.21. Thiết lập khu ngồi, ghế và khu đứng
  - 3.4.22. Cập nhật giá vé theo khu
  - 3.4.23. Gửi sự kiện để duyệt
  - 3.4.24. Duyệt công khai hoặc từ chối sự kiện
  - 3.4.25. Quản lý mã giảm giá của tổ chức
  - 3.4.26. Kiểm tra vé vào cửa bằng QR hoặc mã nhập tay
  - 3.4.27. Xem lịch sử kiểm tra vé
  - 3.4.28. Duyệt hoặc từ chối yêu cầu hoàn vé
  - 3.4.29. Xử lý hoàn tiền mô phỏng
  - 3.4.30. Hủy sự kiện
  - 3.4.31. Thiết lập chính sách hoa hồng và phí
  - 3.4.32. Lập và tính lại đối soát sự kiện
  - 3.4.33. Xác nhận đối soát sự kiện
  - 3.4.34. Ghi nhận chi trả mô phỏng cho tổ chức
  - 3.4.35. Xem và lọc báo cáo
  - 3.4.36. Xuất báo cáo CSV
  - 3.4.37. Xem nhật ký thao tác hệ thống

# CHƯƠNG 1: KHẢO SÁT HIỆN TRẠNG

Chương này trình bày khái quát bài toán quản lý bán vé sự kiện, các bên tham gia, những quy trình nghiệp vụ chính và các nhóm dữ liệu cần quản lý. Phạm vi khảo sát được xác định từ đặc tả hệ thống TicketsCenter và sơ đồ lớp chính thức của dự án. Đây là cơ sở để xác định yêu cầu ở Chương 2 và mô hình hóa các chức năng ở Chương 3.

## 1.1. Tổng quan về bài toán bán vé sự kiện

### 1.1.1. Đặc điểm hoạt động tổ chức sự kiện và phân phối vé

Hoạt động bán vé sự kiện là một chuỗi nghiệp vụ liên tục, bắt đầu từ việc hình thành đơn vị tổ chức, tạo sự kiện và thiết lập cấu trúc vé; tiếp theo là công khai sự kiện, tiếp nhận nhu cầu mua vé, giữ chỗ, thanh toán và phát hành vé; cuối cùng là kiểm soát vé tại địa điểm tổ chức, xử lý hoàn tiền và đối soát doanh thu. Mỗi giai đoạn tạo ra dữ liệu và trạng thái có liên quan chặt chẽ với nhau. Vì vậy, hệ thống phải duy trì được tính nhất quán xuyên suốt vòng đời của sự kiện và giao dịch.

Một sự kiện thuộc về một đơn vị tổ chức và được phân vào một danh mục. Thông tin cơ bản của sự kiện gồm tên, mô tả, địa điểm, thời gian mở bán, thời gian kết thúc bán, thời gian bắt đầu, thời gian kết thúc và ảnh bìa. Trước khi được công khai, sự kiện phải trải qua quy trình tạo bản nháp, gửi duyệt và nhận quyết định từ quản trị nền tảng. Sự kiện bị từ chối có thể được chỉnh sửa và gửi duyệt lại. Chỉ sự kiện đã được công khai và đang trong thời gian mở bán mới được phép phát sinh lượt giữ vé và đơn mua vé.

TicketsCenter hỗ trợ hai hình thức phân phối vé theo khu vực. Đối với khu ngồi, hệ thống quản lý từng ghế theo hàng và số ghế; mỗi ghế có trạng thái riêng và chỉ có thể thuộc một lượt giữ hợp lệ tại một thời điểm. Đối với khu đứng, hệ thống không tạo từng ghế mà quản lý sức chứa, số lượng đang được giữ và số lượng đã bán. Hai hình thức này cùng sử dụng giá được thiết lập tại khu vực, nhưng cách kiểm soát số lượng còn lại khác nhau.

Quá trình mua vé có yếu tố thời gian và cạnh tranh dữ liệu. Người mua phải giữ ghế hoặc số lượng vé trước khi tạo đơn. Lượt giữ chỉ tồn tại trong mười phút, không được gia hạn khi tải lại trang hoặc áp dụng mã giảm giá, và mỗi tài khoản chỉ có tối đa một lượt giữ đang hoạt động trên toàn hệ thống. Một lượt giữ hoặc một đơn được mua tối đa tám vé của cùng một sự kiện. Khi nhiều người cùng chọn một ghế hoặc cùng mua phần sức chứa còn lại của khu đứng, hệ thống phải bảo đảm không bán vượt số lượng thực tế.

Theo thiết kế, thanh toán dùng VNPAY Sandbox. Return URL chỉ hiển thị; IPN hoặc truy vấn đã xác thực mới xác nhận khoản thu. Đơn chỉ hoàn tất khi Event PUBLISHED, thời điểm hiện tại còn trong cửa sổ bán và Hold còn hiệu lực; khoản CAPTURED được chọn vào Order.acceptedPayment trước khi phát hành vé. Một vé là một quyền vào cửa, có mã riêng để tạo QR. Thu muộn, thu trùng và kết quả chưa rõ được theo dõi để tránh phát hành hoặc hoàn nhiều lần.

Sau khi vé được phát hành, hệ thống tiếp tục quản lý việc kiểm tra vé, yêu cầu hoàn vé, hủy sự kiện, đối soát và chi trả. Như vậy, đối tượng được quản lý không chỉ là vé mà còn gồm quan hệ giữa người dùng, tổ chức, sự kiện, khu vực, lượt giữ, đơn hàng, thanh toán, hoàn tiền và các khoản phải trả cho đơn vị tổ chức.

### 1.1.2. Các bên tham gia và nhu cầu sử dụng hệ thống

Các bên tham gia hệ thống được xác định theo vai trò và phạm vi dữ liệu mà họ được phép thao tác. Một người dùng có thể vừa là người mua, vừa tham gia một hoặc nhiều tổ chức với vai trò khác nhau. Quyền trong tổ chức được xác định qua tư cách thành viên, không chỉ dựa vào tổ chức đang được chọn trên giao diện.

| Bên tham gia | Nhu cầu sử dụng chính |
|---|---|
| Khách chưa đăng nhập | Xem danh sách sự kiện đã công khai, tìm kiếm sự kiện, xem giá vé và tình trạng chỗ, đăng ký tài khoản. |
| Người dùng chưa xác minh email | Đăng nhập, xem sự kiện và thực hiện xác minh email; chưa được giữ hoặc mua vé. |
| Người mua đã xác minh | Giữ vé, tạo đơn, áp dụng mã giảm giá, thanh toán, xem đơn và vé, chia sẻ mã QR, yêu cầu hoàn vé và gửi yêu cầu tạo tổ chức. |
| Quản lý tổ chức | Quản lý thành viên, sự kiện, khu vực, ghế, giá vé, mã giảm giá, báo cáo và hoạt động kiểm soát vé trong phạm vi tổ chức. |
| Nhân viên kiểm soát vé | Chọn sự kiện thuộc tổ chức, quét hoặc nhập mã vé, xem kết quả kiểm tra và lịch sử phù hợp với quyền được cấp. |
| Quản trị nền tảng | Duyệt yêu cầu tạo tổ chức, duyệt sự kiện, xử lý yêu cầu hoàn vé, hủy sự kiện, quản lý chính sách hoa hồng, đối soát, chi trả và nhật ký thao tác. |

Người quản lý tổ chức có thể thêm thành viên đã tồn tại trong hệ thống bằng `userName` duy nhất, thay đổi vai trò hoặc vô hiệu hóa quyền theo tổ chức. Quyền đang hiệu lực nằm trong `User.organizationRoles: Map<Organization, OrganizationRole>`. Hệ thống không cho phép hạ quyền hoặc vô hiệu hóa người quản lý đang hoạt động cuối cùng. Nhân viên kiểm soát vé chỉ thực hiện check-in cho các sự kiện thuộc tổ chức của mình và không được truy cập chức năng quản lý doanh thu hoặc thành viên.

Quản trị nền tảng có phạm vi rộng hơn các vai trò còn lại nhưng vẫn phải thao tác thông qua các quy trình và trạng thái đã xác định. Việc duyệt tổ chức, công khai sự kiện, duyệt hoàn vé, hủy sự kiện và xác nhận đối soát đều phải được ghi nhận rõ ràng để phục vụ kiểm tra lịch sử và truy vết thao tác.

## 1.2. Khảo sát quy trình nghiệp vụ bán vé sự kiện

### 1.2.1. Quản lý đơn vị tổ chức và thông tin sự kiện

Người mua đã đăng nhập, tài khoản hoạt động và đã xác minh email có thể gửi yêu cầu tạo tổ chức. Hồ sơ gồm tên, email liên hệ, số điện thoại liên hệ và mô tả, được lưu trực tiếp trên `Organization` ở trạng thái `DRAFT`; `submitForApproval()` chuyển cùng hồ sơ sang `PENDING_APPROVAL`. Quản trị nền tảng duyệt hoặc từ chối kèm lý do trên chính hồ sơ này.

Khi được duyệt, chính `Organization` chuyển sang `APPROVED`; hệ thống tạo chính sách phí ban đầu và gọi `User.assignRole(organization, MANAGER)` cho người gửi trong cùng giao dịch. Xử lý lặp trả kết quả cũ, không tạo Organization khác hoặc quyền trùng. Tổ chức chỉ được hoạt động sau khi duyệt; người quản lý có thể thêm thành viên, thay đổi vai trò hoặc thu hồi quyền trong phạm vi tổ chức.

Sự kiện được tạo dưới dạng bản nháp và luôn thuộc đúng một tổ chức cùng một danh mục. Người quản lý nhập thông tin mô tả, địa điểm, khoảng thời gian bán vé, thời gian diễn ra và ảnh bìa. Trước khi gửi duyệt, sự kiện phải đáp ứng thứ tự thời gian: thời điểm mở bán trước thời điểm kết thúc bán, thời điểm kết thúc bán không sau thời điểm bắt đầu sự kiện, và thời điểm bắt đầu trước thời điểm kết thúc sự kiện.

Sau khi hoàn thiện, tổ chức gửi sự kiện để quản trị nền tảng xét duyệt. Quản trị có thể công khai hoặc từ chối kèm lý do. Nếu bị từ chối, tổ chức được sửa thông tin và gửi lại. Khi sự kiện đã công khai, cấu trúc chỗ ngồi và sức chứa được khóa để tránh làm thay đổi quyền lợi của các lượt giữ và đơn hàng đã phát sinh.

### 1.2.2. Quản lý khu vực, chỗ ngồi và số lượng vé

Mỗi sự kiện gồm một hoặc nhiều khu vực bán vé. Khu vực lưu tên, loại khu và giá vé. Loại khu quyết định cách quản lý chỗ: khu ngồi quản lý danh sách ghế cụ thể, còn khu đứng quản lý theo sức chứa.

Đối với khu ngồi, người quản lý khai báo tên khu, số hàng và số ghế trong mỗi hàng. Hệ thống tạo các ghế với tên hàng và số ghế, đồng thời bảo đảm nhãn ghế không trùng trong cùng một khu. Mỗi ghế có thể chuyển qua các trạng thái còn trống, đang được giữ và đã bán. Khi lượt giữ bị hủy hoặc hết hạn trước thanh toán, ghế phải được trả về trạng thái có thể bán.

Đối với khu đứng, hệ thống không tạo đối tượng ghế cho từng người. Số vé có thể bán được tính từ sức chứa, số lượng đang giữ và số lượng đã bán. Mọi thao tác giữ, giải phóng hoặc xác nhận bán phải bảo đảm tổng số lượng không vượt quá sức chứa và không trở thành số âm.

Giá vé được thiết lập tại khu vực. Sau khi sự kiện công khai, người quản lý vẫn có thể thay đổi giá niêm yết nhưng không được thay đổi loại khu, thêm hoặc xóa ghế, thay đổi số hàng hoặc thay đổi sức chứa. Giá của một lượt giữ được chụp tại thời điểm giữ thành công; do đó, việc thay đổi giá niêm yết chỉ tác động đến lượt giữ mới, không làm thay đổi giá của lượt giữ còn hiệu lực hoặc đơn đã hình thành.

### 1.2.3. Đặt vé, áp dụng khuyến mãi và thanh toán

Người dùng có thể tìm sự kiện theo tên, lọc theo danh mục hoặc khoảng ngày, sắp xếp theo thời gian bắt đầu hoặc giá khu thấp nhất và xem thông tin chi tiết. Danh sách công khai chỉ hiển thị các sự kiện đã được duyệt; sự kiện đã hủy không tiếp tục xuất hiện như một sự kiện còn có thể mua vé.

Để giữ vé, người dùng phải đăng nhập và đã xác minh email. Một lượt giữ chỉ chứa vé của cùng một sự kiện nhưng có thể kết hợp khu ngồi và khu đứng. Với khu ngồi, mỗi mục giữ gắn với một ghế và có số lượng bằng một. Với khu đứng, mục giữ không gắn ghế và có thể có số lượng lớn hơn một. Toàn bộ lựa chọn phải được giữ thành công trong một giao dịch; nếu có một chỗ không còn hợp lệ thì hệ thống không âm thầm giữ phần còn lại.

Mỗi tài khoản chỉ được có một lượt giữ đang hoạt động. Nếu muốn chọn lượt khác, người dùng phải chủ động hủy lượt hiện tại. Lượt giữ có hiệu lực mười phút tính từ thời điểm giữ thành công, tối đa tám vé và không được gia hạn khi tạo đơn hay áp dụng mã giảm giá. Thời gian trên máy chủ là căn cứ quyết định hiệu lực, còn đồng hồ trên trình duyệt chỉ có chức năng hiển thị.

Từ một lượt giữ, hệ thống tạo tối đa một đơn hàng. Giá của từng mục giữ được chuyển sang mục đơn hàng; tên khu và nhãn ghế cũng được lưu lại tại thời điểm mua để bảo toàn dữ liệu lịch sử. Đơn hàng ghi nhận tiền vé trước giảm, số tiền giảm và tổng tiền phải trả.

Mã giảm giá thuộc về một tổ chức và chỉ áp dụng cho sự kiện của chính tổ chức đó. Mỗi đơn sử dụng tối đa một mã. Mức giảm theo phần trăm phải lớn hơn không và không vượt quá 30%; với mã giảm số tiền cố định, số tiền giảm thực tế cũng không vượt quá 30% giá trị trước giảm. Hệ thống kiểm tra thời hạn, trạng thái hoạt động, tổ chức phát hành và giới hạn số lượt sử dụng trước khi áp dụng mã. Lượt sử dụng đang giữ cùng lượt đã dùng không được vượt quá giới hạn của mã.

Khi bắt đầu thanh toán, hệ thống kiểm tra chủ đơn, Event/cửa sổ bán, Hold và totalAmount máy chủ. Payment.start(order, txnRef) tạo lần thu có Payment.order bắt buộc, txnRef duy nhất và amount bất biến. Khi IPN/query đã xác thực đến mà Event vẫn PUBLISHED, saleStart <= now < saleEnd và Hold còn hạn, hệ thống ghi CAPTURED/paidAt, đặt Order.acceptedPayment/PAID/paidAt, consume Hold, bán kho, dùng coupon và phát hành vé nguyên tử.

Nếu khoản thu được xác nhận sau khi lượt giữ hết hạn hoặc đã giải phóng, khi thu trùng, sự kiện đã hủy hoặc đã qua `saleEnd`, hệ thống ghi `Payment` là `CAPTURED` nhưng không phát hành thêm vé. Hệ thống tạo đúng một nghĩa vụ `Refund` có `purpose = PAYMENT_COMPENSATION`, gắn khoản Payment đó và không có tập vé. Kết quả chưa rõ phải được truy vấn lại, không tự coi là thất bại để tạo lần thu mới. Đơn 0đ vẫn phải thỏa điều kiện Hold, Event và cửa sổ mở bán; hệ thống hoàn tất, ghi `Order.paidAt`, phát hành vé mà không tạo Payment giả.

### 1.2.4. Phát hành vé và kiểm soát vào cửa

Sau khi đơn được hoàn tất, hệ thống phát hành một vé cho mỗi quyền vào cửa. Nếu một mục đơn hàng khu đứng có số lượng nhiều hơn một, hệ thống tạo số vé tương ứng; mỗi vé có mã riêng và giá trị thực trả được phân bổ từ tổng tiền của đơn. Vé được hiển thị trong khu vực “Vé của tôi”, đồng thời thông tin vé và liên kết xem mã QR được gửi qua email.

Người mua có thể chia sẻ mã QR cho người đi cùng. Người cầm vé không bắt buộc phải có tài khoản, nhưng người mua vẫn là người quản lý đơn hàng và là người có quyền gửi yêu cầu hoàn vé. Hệ thống không chuyển quyền sở hữu đơn hàng hoặc tài khoản khi mã QR được chia sẻ.

Khi kiểm soát vào cửa, nhân viên phải chọn sự kiện trước rồi mới quét QR bằng camera hoặc nhập mã vé thủ công. Hệ thống kiểm tra tư cách thành viên đang hoạt động của nhân viên, sự kiện của vé, trạng thái vé và thời gian check-in. Khoảng thời gian hợp lệ được tính từ 60 phút trước giờ bắt đầu đến trước thời điểm kết thúc sự kiện. Sự kiện đã hủy và vé đang chờ hoàn, đã hoàn, đã vô hiệu hóa hoặc đã sử dụng đều không được chấp nhận.

Mỗi lần kiểm tra tạo một kết quả để phục vụ theo dõi. Kết quả có thể phản ánh vé hợp lệ, mã không tồn tại, sai sự kiện, vé đã sử dụng, ngoài thời gian cho phép hoặc vé không còn hoạt động. Trong trường hợp hai yêu cầu kiểm tra cùng xảy ra, chỉ một lần được phép thành công và chuyển vé sang trạng thái đã sử dụng.

### 1.2.5. Xử lý yêu cầu hoàn vé và hủy sự kiện

Người mua chỉ được yêu cầu hoàn đối với vé thuộc đơn hàng của mình. Các vé trong cùng một yêu cầu phải thuộc cùng một đơn, chưa được sử dụng và yêu cầu phải được gửi trước thời điểm bắt đầu sự kiện. Một vé chỉ có tối đa một yêu cầu hoàn đang mở. Số tiền đề nghị hoàn bằng tổng số tiền thực trả đã lưu trên các vé được chọn.

Khi yêu cầu hợp lệ được gửi, hệ thống tạo `Refund` ở trạng thái `REQUESTED`, chuyển vé sang `REFUND_PENDING` và chặn check-in trong cùng giao dịch. Quản trị từ chối kèm lý do thì Refund chuyển `REJECTED`, vé được khôi phục `ACTIVE` chỉ khi sự kiện chưa hủy. Duyệt chuyển cùng Refund sang `APPROVED`; hoàn có tiền bắt đầu lần thực hiện và chỉ khi kết quả thành công mới chuyển Refund sang `COMPLETED`, vé sang `REFUNDED` và trả kho. Với số tiền bằng 0, hệ thống hoàn tất cùng nghĩa vụ bằng `completeWithoutTransfer()`, cập nhật vé/trả kho mà không tạo lần chuyển tiền giả.

Kết quả `FAILED` đã xác minh đưa Refund sang `RETRYABLE`; kết quả `UNKNOWN` đưa sang `NEEDS_RECONCILIATION`. Vé vẫn chờ hoàn, không tự khôi phục. Chỉ thất bại đã xác minh mới cho phép lần thử mới với `attemptId` mới; kết quả chưa rõ phải đối chiếu trước. Cùng ID xử lý lặp trả kết quả cũ và không hoàn tiền hoặc trả kho hai lần. Lượt coupon đã dùng không được trả lại sau hoàn một phần hay toàn bộ.

Đối với sự kiện đã công khai, chỉ quản trị nền tảng được hủy trước `startTime`. Hệ thống ngừng bán, chặn giữ mới/check-in và giải phóng Hold còn hiệu lực cùng lượt coupon đang giữ. Theo từng Order, Refund `REQUESTED` của khách được dùng lại bằng `adoptEventCancellation()`, giữ lịch sử lý do và tự duyệt với `reasonType = EVENT_CANCELLATION`. Nghĩa vụ đã duyệt/đang xử lý giữ nguyên tập vé và amount; chỉ tạo nghĩa vụ mới cho vé đủ điều kiện còn lại. Không hoàn trùng vé đã hoàn; vé `USED` được ghi ngoại lệ cho quản trị và không tự động hoàn hoặc khôi phục.

### 1.2.6. Quản lý doanh thu, hoa hồng, đối soát và chi trả

Mỗi tổ chức có chính sách hoa hồng do quản trị nền tảng thiết lập. Khi sự kiện được công khai, hệ thống chọn quy tắc đang có hiệu lực và gắn cố định quy tắc đó với sự kiện. Tỷ lệ phần trăm và phí cố định của quy tắc đã gắn không được thay đổi; nếu cần áp dụng chính sách mới, hệ thống tạo một quy tắc khác cho các sự kiện về sau.

Sau `endTime` dự kiến, kể cả sự kiện đã hủy, hệ thống có thể lập Settlement nháp và tính lại số liệu. Mỗi Event tối đa một Settlement; mỗi Order đủ điều kiện xuất hiện đúng một lần trong snapshot theo đơn. Chỉ xác nhận khi không còn Payment/Refund liên quan đang chờ hoặc chưa rõ kết quả. Chi tiết theo đơn là dữ liệu persistence phục vụ truy vết, không phải lớp nghiệp vụ bổ sung.

Hoa hồng được tính trên phần tiền còn lại sau hoàn. Nếu toàn bộ tiền của đơn đã được hoàn thì hoa hồng bằng không. Các khoản thu trùng, khoản thu đến muộn và hoàn tiền bù trừ không được tính là doanh thu vé hoặc cơ sở tính hoa hồng. Trước khi xác nhận, bản đối soát có thể được tính lại; sau khi xác nhận, các số tiền được đóng băng và không bị ảnh hưởng bởi chính sách phí mới.

Việc chi trả cho tổ chức được mô phỏng và do `Settlement` trực tiếp quản lý. `paidAmount` giữ số đã chi thành công, `pendingAmount` giữ số đang chờ; `availableToPay = netPayable - paidAmount - pendingAmount`. Mỗi lần chi có `payoutId` ổn định, số tiền và mã tham chiếu, được lưu lịch sử bằng `SettlementTransferLog` kỹ thuật. Tổng `paidAmount + pendingAmount` không vượt `netPayable`. Chỉ khi `paidAmount = netPayable` và `pendingAmount = 0` mới chuyển `PAID`; số phải trả bằng 0 được hoàn tất mà không tạo lần chi giả.

Hệ thống cung cấp báo cáo cho quản trị nền tảng trên phạm vi toàn hệ thống và cho quản lý trên phạm vi tổ chức của mình. Báo cáo có thể lọc theo sự kiện và thời gian, đồng thời phân biệt tiền đã thu, doanh thu vé, tiền hoàn, hoa hồng, số vé theo khu, kết quả check-in và tiền còn phải trả. Dữ liệu xuất CSV phải sử dụng cùng công thức và bộ lọc với dữ liệu hiển thị trên giao diện.

## 1.3. Khảo sát dữ liệu và thông tin quản lý

### 1.3.1. Thông tin người mua và đơn vị tổ chức

Thông tin người dùng nằm trong `User`, gồm `userName` duy nhất, email, họ tên, điện thoại, trạng thái, xác minh email, `platformRole` và `organizationRoles`. Mật khẩu đã băm, mốc thời gian và phiên bản xác thực vẫn được lưu ở persistence dù sơ đồ tổng quan ẩn chúng. Email dùng đăng ký, đăng nhập và xác minh; `userName` dùng tìm tài khoản khi thêm thành viên. Hồ sơ thông thường chỉ cập nhật họ tên và điện thoại.

Thông tin xin tạo và xét duyệt tổ chức được quản lý trực tiếp bởi `Organization`, gồm tên, liên hệ, mô tả, `requester`, `status` và `rejectionReason`. Cùng hồ sơ đi từ `DRAFT` qua `PENDING_APPROVAL` đến `APPROVED` hoặc `REJECTED`; người duyệt và thời điểm quyết định được lưu để truy vết trong persistence/audit. Không có hồ sơ yêu cầu nghiệp vụ riêng và chưa có ca gửi lại Organization bị từ chối.

Quan hệ quyền hiệu lực giữa User và Organization được biểu diễn bằng `User.organizationRoles: Map<Organization, OrganizationRole>`, với `MANAGER` hoặc `CHECK_IN_STAFF`. Một User có thể thuộc nhiều tổ chức; mỗi cặp chỉ có một quyền thành viên. Có thể chuẩn hóa map bằng bảng kỹ thuật `UserOrganizationRole` để lưu role, trạng thái hoạt động và lịch sử; bảng này không tạo lớp nghiệp vụ mới. `assignRole` cấp/đổi quyền, `revokeRole` bỏ quyền hiệu lực khỏi map mà không vô hiệu hóa tài khoản User.

### 1.3.2. Thông tin sự kiện, khu vực và vé

Nhóm lớp nghiệp vụ sự kiện gồm `Event`, `Zone` và `Seat`. `EventCategory` là kiểu giá trị hoặc tham chiếu danh mục, không phải lớp nghiệp vụ bổ sung. Event lưu nội dung, địa điểm, lịch, trạng thái duyệt, lý do từ chối, ảnh bìa và tổ chức cùng CommissionRule đã chọn. Zone lưu loại khu, giá, sức chứa đứng, số đang giữ/đã bán. Seat lưu hàng, số ghế và trạng thái phân bổ hiện tại.

Trước khi mua, lựa chọn của người dùng được lưu trong `TicketHold` và `TicketHoldItem`. Lượt giữ lưu người tạo, sự kiện, thời điểm bắt đầu, thời điểm hết hạn và trạng thái. Mỗi mục giữ lưu khu vực, ghế nếu là khu ngồi, số lượng và đơn giá đã được chụp tại thời điểm giữ.

Sau khi đơn được thanh toán hợp lệ, quyền vào cửa được lưu trong `Ticket`. Vé có mã vé duy nhất, trạng thái, thời điểm phát hành và số tiền thực trả. Việc lưu số tiền thực trả trên từng vé giúp hệ thống xử lý hoàn tiền theo đúng số tiền đã phân bổ thay vì tính lại từ giá hoặc mã giảm giá hiện tại.

### 1.3.3. Thông tin đơn hàng, thanh toán và hoàn tiền

`Order` lưu người mua, sự kiện, Hold nguồn, mã đơn, trạng thái, coupon, `discountAmount` và `acceptedPayment`; `subtotalAmount` và `totalAmount` là dẫn xuất từ các mục. `paidAt` bắt buộc khi Order `PAID`, kể cả 0đ. Mỗi đơn gồm `OrderItem` có tên khu/nhãn ghế snapshot, số lượng và đơn giá bất biến, giúp giữ lịch sử khi giá niêm yết hoặc thông tin khu thay đổi.

Mỗi lần thu được quản lý bằng `Payment`, bắt buộc có `Payment.order` thuộc đúng một Order; Order có 0..* Payment, kể cả lần `FAILED`, `UNKNOWN` hoặc khoản `CAPTURED` cần bù trừ. `Payment.start(order, txnRef)` chụp `Order.totalAmount` vào `amount` bất biến; `txnRef` duy nhất. Trạng thái gồm `PENDING`, `CAPTURED`, `FAILED`, `UNKNOWN`; `Payment.paidAt` bắt buộc khi CAPTURED. `Order.acceptedPayment [0..1]` chọn đúng một Payment CAPTURED của chính đơn để hoàn tất đơn có tiền; đơn 0đ không có Payment.

Thông tin mã giảm giá được lưu trong `Coupon`, gồm mã, loại giảm, số tiền cố định hoặc tỷ lệ phần trăm, khoảng hiệu lực, trạng thái hoạt động và giới hạn số lượt sử dụng. Việc giữ, tiêu thụ hoặc trả lượt dùng mã là cơ chế persistence kỹ thuật; không bổ sung một lớp nghiệp vụ độc lập vào sơ đồ chính.

`Refund` trực tiếp quản lý nghĩa vụ hoàn từ yêu cầu đến kết quả, gồm Order/tập Ticket, `payment`, `amount`, `purpose`, `reasonType`, `reason`, `rejectionReason`, `status`, `currentAttemptId`, `providerReference` và `processedAt`. Hoàn vé có `purpose = CUSTOMER_REFUND`, `reasonType = CUSTOMER_REQUEST` hoặc `EVENT_CANCELLATION`, amount bằng tổng Ticket.paidAmount và payment là acceptedPayment, có thể trống khi hoàn 0đ. Bù trừ có `purpose = PAYMENT_COMPENSATION`, gắn Payment đã thu, amount dương và không có Ticket. Nghĩa vụ vẫn tồn tại khi chưa xử lý hoặc bằng 0; lịch sử từng lần thực hiện nằm trong bản ghi kỹ thuật `RefundTransferLog`.

### 1.3.4. Thông tin kiểm soát vào cửa và báo cáo

`Ticket.checkIn(event, now)` kiểm tra điều kiện và chuyển Ticket từ `ACTIVE` sang `USED`. Mỗi lần quét/nhập tay được lưu trong `TicketCheckInLog` kỹ thuật, gồm vé nhận diện nếu có, sự kiện, actor, thời điểm và kết quả. Nhật ký phân biệt mã sai, sai sự kiện, đã dùng, ngoài giờ và vé không hoạt động; yêu cầu thiếu quyền bị từ chối trước check-in và ghi audit phù hợp. Nhật ký không phải lớp nghiệp vụ riêng.

`CommissionRule` quản lý tỷ lệ, phí cố định và hiệu lực; `Settlement` trực tiếp giữ snapshot `grossRevenue`, `totalRefund`, `totalCommission`, trạng thái và tiến độ chi `paidAmount`/`pendingAmount`. `netPayable` và `availableToPay` là dẫn xuất. `SettlementOrderSnapshot` lưu chi tiết từng Order và `SettlementTransferLog` lưu các lần chi để truy vết; cả hai là bản ghi persistence kỹ thuật, không phải lớp nghiệp vụ hay collection bổ sung trong diagram. Xác nhận đối soát đóng băng chi tiết theo đơn và các tổng doanh thu/hoàn/phí; tổng đã chi/đang chờ chỉ cập nhật qua hành vi chi trả.

Các thao tác quan trọng được ghi nhận bằng `AuditLog`, gồm người thực hiện, hành động, loại đối tượng, mã đối tượng, nội dung chi tiết và thời điểm phát sinh. Dữ liệu nhật ký phục vụ truy vết các quyết định và thao tác quản trị nhưng không thay thế dữ liệu trạng thái của các đối tượng nghiệp vụ.

Báo cáo không được xem là một nguồn dữ liệu độc lập mà được tổng hợp từ đơn hàng, vé, thanh toán, hoàn tiền, đối soát và check-in. Người quản lý chỉ được xem dữ liệu thuộc tổ chức của mình, trong khi quản trị nền tảng được xem phạm vi toàn hệ thống. Giao diện và tệp CSV phải dùng chung phạm vi truy cập, bộ lọc và công thức tính toán.

## 1.4. Định hướng xây dựng hệ thống TicketsCenter

### 1.4.1. Mục tiêu của hệ thống

TicketsCenter được định hướng là một hệ thống quản lý bán vé sự kiện thống nhất, hỗ trợ toàn bộ chuỗi nghiệp vụ từ đăng ký tài khoản, hình thành tổ chức, tạo và duyệt sự kiện đến giữ vé, thanh toán, phát hành vé, check-in, hoàn tiền và đối soát. Hệ thống cần duy trì đúng trạng thái của chỗ ngồi, số lượng vé, đơn hàng và giao dịch trong các tình huống có nhiều yêu cầu xảy ra đồng thời.

Mục tiêu tiếp theo là phân định rõ quyền của người mua, quản lý tổ chức, nhân viên kiểm soát vé và quản trị nền tảng. Mọi thao tác phải được kiểm tra theo đối tượng và phạm vi tổ chức, tránh việc chỉ dựa vào dữ liệu được gửi từ giao diện. Các nghiệp vụ tài chính và thay đổi trạng thái quan trọng phải có lịch sử để phục vụ tra cứu và đối chiếu.

Hệ thống cũng hướng đến khả năng sử dụng thuận tiện trên trình duyệt. Tìm kiếm, chọn chỗ, theo dõi thời hạn Hold/cửa sổ bán, xem QR và quét vé cần phù hợp máy tính và điện thoại. Giao diện dùng tiếng Việt, VND, nhập/hiển thị theo Asia/Ho_Chi_Minh và lưu thời điểm UTC để xử lý nhất quán.

### 1.4.2. Phạm vi chức năng và nhóm người sử dụng

Phạm vi chức năng của TicketsCenter gồm các nhóm chính sau:

- Quản lý tài khoản, xác minh email, đăng nhập, khôi phục mật khẩu và hồ sơ người dùng.
- Gửi, xét duyệt yêu cầu tạo tổ chức và quản lý thành viên theo vai trò.
- Phân loại sự kiện theo danh mục; quản lý sự kiện, khu vực, ghế, sức chứa và giá vé.
- Tìm kiếm sự kiện, giữ vé, tạo đơn, áp dụng mã giảm giá và thanh toán.
- Phát hành vé điện tử, hiển thị hoặc chia sẻ QR và kiểm soát vào cửa.
- Tiếp nhận, xét duyệt và xử lý yêu cầu hoàn vé; xử lý hủy sự kiện.
- Thiết lập chính sách hoa hồng, lập đối soát và ghi nhận chi trả.
- Tổng hợp báo cáo, xuất CSV và lưu nhật ký thao tác.

Hệ thống được thiết kế dưới dạng một ứng dụng web thống nhất theo MVC, sử dụng Java Servlet, JSP/JSTL, Model/Service, JPA/Hibernate và Microsoft SQL Server. Mô hình nghiệp vụ chuẩn có đúng 15 lớp: `User`, `Organization`, `Event`, `Zone`, `Seat`, `TicketHold`, `TicketHoldItem`, `Order`, `OrderItem`, `Payment`, `Coupon`, `Ticket`, `Refund`, `CommissionRule`, `Settlement`. Enum, kiểu giá trị, DTO, bảng tra cứu và bản ghi kỹ thuật hỗ trợ lưu trữ không làm tăng số lớp nghiệp vụ. Ứng dụng thống nhất này tách trách nhiệm giao diện, điều phối và persistence.

### 1.4.3. Giới hạn của đồ án và phạm vi mô phỏng

TicketsCenter là đồ án phục vụ học tập và portfolio. Bản triển khai trực tuyến được định hướng để minh họa luồng nghiệp vụ qua HTTPS, không phải cam kết vận hành một nền tảng bán vé thương mại liên tục. Môi trường trực tuyến sử dụng tài nguyên miễn phí nên có thể phát sinh thời gian khởi động lại hoặc tạm ngừng khi hết hạn mức.

Thanh toán được thực hiện trong môi trường VNPAY Sandbox, không phát sinh giao dịch tiền thật. Hoàn tiền và chi trả cho tổ chức được mô phỏng để thể hiện đầy đủ trạng thái và quy tắc nghiệp vụ. Nhà cung cấp email và lưu trữ ảnh chưa được chốt; việc lựa chọn được thực hiện ở giai đoạn tích hợp dựa trên khả năng tương thích với Java, HTTPS và hạn mức phù hợp.

Phạm vi hệ thống không bao gồm chuyển quyền sở hữu đơn hàng hoặc tài khoản khi chia sẻ vé. Tổ chức thêm trực tiếp thành viên đã có tài khoản bằng `userName` duy nhất, chưa có luồng lời mời riêng. Coupon luôn thuộc một tổ chức, không có mã chung toàn nền tảng. Nhân viên check-in có quyền trên mọi sự kiện của tổ chức mình, chưa có phân công riêng theo sự kiện.

Tại ngày đồng bộ 06/10/2026, dự án chưa triển khai lớp Java nghiệp vụ, Servlet, JSP hoặc script SQL. [Prototype giao diện](../web-demo/prototype/README.md) gồm 24 khung UI dùng dữ liệu mô phỏng để trình diễn, chưa tích hợp database, VNPAY hay email thực tế. Các công nghệ và hành vi trong báo cáo là yêu cầu/thiết kế cần triển khai, chưa phải minh chứng nghiệm thu.

Các giới hạn trên xác định rõ phạm vi hiện thực của đồ án nhưng không làm thay đổi những quy tắc cốt lõi về giữ chỗ, thanh toán, phát hành vé, hoàn tiền, kiểm soát vào cửa và đối soát đã được mô hình hóa trong hệ thống.

# CHƯƠNG 2: DANH SÁCH YÊU CẦU

## 2.1. Phân tích yêu cầu

Phần này xác định các yêu cầu mà hệ thống TicketsCenter phải đáp ứng. Yêu cầu chức năng mô tả dữ liệu cần lưu trữ, thông tin cần tra cứu, các phép tính nghiệp vụ, kết quả cần xuất ra, môi trường hoạt động và phạm vi quyền của từng nhóm người dùng. Yêu cầu phi chức năng xác định những đặc tính cần có để hệ thống có thể sử dụng, bảo trì và triển khai phù hợp với phạm vi đồ án.

### 2.1.1. Yêu cầu chức năng

#### 2.1.1.1. Yêu cầu chức năng nghiệp vụ

##### 2.1.1.1.1. Lưu trữ

Hệ thống phải lưu trữ đầy đủ thông tin của các đối tượng nghiệp vụ và duy trì được quan hệ giữa chúng trong suốt quá trình vận hành. Dữ liệu đã phát sinh giao dịch phải được giữ lại để phục vụ tra cứu, hoàn tiền, đối soát và kiểm tra lịch sử; không xóa dây chuyền các đơn hàng, thanh toán, vé, giao dịch hoàn tiền hoặc nhật ký đã hình thành.

Hệ thống lưu trữ các nhóm thông tin sau:

- **Thông tin tài khoản người dùng:** `userName` duy nhất, email chuẩn hóa không trùng, mật khẩu đã băm, họ tên, điện thoại, trạng thái, quyền nền tảng, map quyền tổ chức, thời điểm xác minh, mốc tạo và phiên bản xác thực.
- **Thông tin xác minh và khôi phục tài khoản:** mục đích của mã OTP, giá trị mã đã được bảo vệ, thời hạn, số lần nhập sai và trạng thái đã sử dụng hoặc bị vô hiệu hóa. Đây là dữ liệu persistence hỗ trợ xác thực, không phải một lớp nghiệp vụ độc lập trong sơ đồ chính.
- **Thông tin hồ sơ tổ chức và xét duyệt:** cùng Organization lưu requester, tên, email/điện thoại liên hệ, mô tả, trạng thái DRAFT/PENDING_APPROVAL/APPROVED/REJECTED và lý do từ chối; persistence/audit giữ người duyệt, thời điểm gửi/quyết định. Duyệt không tạo Organization khác.
- **Thông tin quyền theo tổ chức:** `User.organizationRoles` giữ quyền hiệu lực MANAGER/CHECK_IN_STAFF; `UserOrganizationRole` kỹ thuật có thể lưu liên kết, active và lịch sử. Mỗi cặp User–Organization duy nhất; thu hồi quyền không vô hiệu hóa User và không loại Manager cuối cùng.
- **Thông tin danh mục và sự kiện:** danh mục, tổ chức sở hữu, tiêu đề, mô tả, địa điểm, thời gian mở và kết thúc bán, thời gian bắt đầu và kết thúc sự kiện, trạng thái, lý do từ chối, ảnh bìa, thời điểm tạo và quy tắc hoa hồng được gắn khi công khai.
- **Thông tin khu vực và ghế:** tên khu, loại khu, giá vé, sức chứa khu đứng, số lượng đang giữ, số lượng đã bán, tên hàng, số ghế và trạng thái của từng ghế. Nhãn ghế phải duy nhất trong phạm vi một khu.
- **Thông tin giữ vé:** người tạo lượt giữ, sự kiện, thời điểm tạo, thời điểm hết hạn, trạng thái và các mục giữ. Mỗi mục giữ phải lưu khu vực, ghế nếu là khu ngồi, số lượng và đơn giá tại thời điểm giữ.
- **Thông tin đơn hàng:** buyer, event, hold, orderCode, status, coupon, discountAmount, acceptedPayment và các OrderItem snapshot. Subtotal/total là dẫn xuất; `Order.paidAt` bắt buộc khi PAID kể cả 0đ; hoàn vé giữ lịch sử PAID.
- **Thông tin thanh toán:** mỗi Payment bắt buộc gắn một Order, amount chụp bất biến từ totalAmount, txnRef duy nhất, status, transactionNo và paidAt khi CAPTURED. Chỉ acceptedPayment CAPTURED của chính Order được chọn để hoàn tất đơn có tiền; giữ toàn bộ lịch sử lần thu khác.
- **Thông tin mã giảm giá:** tổ chức phát hành, mã, loại giảm, số tiền cố định hoặc tỷ lệ phần trăm, thời gian hiệu lực, trạng thái hoạt động và giới hạn lượt sử dụng. Hệ thống phải lưu trạng thái giữ, sử dụng hoặc giải phóng lượt mã theo đơn bằng cơ chế persistence kỹ thuật.
- **Thông tin vé:** mục đơn hàng phát hành vé, mã vé duy nhất, trạng thái, thời điểm phát hành và số tiền thực trả được phân bổ cho từng vé.
- **Thông tin kiểm soát vào cửa:** Ticket giữ trạng thái đã dùng; TicketCheckInLog kỹ thuật lưu vé nếu nhận diện được, event, actor, kết quả, phương thức và thời điểm; các lần bị từ chối phù hợp vẫn được lưu, không ghi nguyên mã QR.
- **Thông tin nghĩa vụ hoàn:** cùng Refund lưu Order/tập Ticket, payment, amount, purpose, lý do khách/quyết định, reasonType và status; một vé tối đa một nghĩa vụ đang mở. CUSTOMER_REFUND gắn vé cùng Order, payment có thể trống khi amount = 0; PAYMENT_COMPENSATION có Payment, amount > 0 và không có vé.
- **Thông tin tiến độ và lịch sử hoàn:** Refund giữ currentAttemptId, providerReference, processedAt; RefundTransferLog kỹ thuật giữ attemptId và kết quả mỗi lần. Trạng thái Refund gồm REQUESTED, APPROVED, PROCESSING, NEEDS_RECONCILIATION, RETRYABLE, REJECTED, COMPLETED. Hoàn 0đ vẫn có Refund COMPLETED nhưng không tạo Payment/nhật ký chuyển tiền hoặc processedAt chuyển tiền giả.
- **Thông tin hoa hồng và đối soát:** CommissionRule theo tổ chức, tỷ lệ, phí cố định, hiệu lực; Settlement theo Event lưu snapshot grossRevenue/totalRefund/totalCommission, paidAmount/pendingAmount và status. SettlementOrderSnapshot kỹ thuật giữ mỗi Order đúng một lần; netPayable và availableToPay là dẫn xuất. Snapshot doanh thu/hoàn/phí bất biến sau confirm.
- **Thông tin lịch sử chi trả:** SettlementTransferLog kỹ thuật lưu payoutId ổn định, settlement, số tiền, reference và kết quả từng lần; Settlement trực tiếp cập nhật paidAmount/pendingAmount, giữ paidAmount + pendingAmount <= netPayable và không ghi lặp cùng ID.
- **Thông tin nhật ký:** người thực hiện, hành động, loại đối tượng, mã đối tượng, nội dung chi tiết và thời điểm phát sinh.

Các thời điểm nghiệp vụ được lưu UTC/Instant và hiển thị theo `Asia/Ho_Chi_Minh`. Money là số VND nguyên; phần trăm và tính toán trung gian dùng số thập phân chính xác, không dùng số thực dấu phẩy động. Trạng thái lưu bằng tên enum thay vì số thứ tự; giá/nhãn snapshot và paidAmount của vé không được tính lại theo cấu hình hiện tại.

##### 2.1.1.1.2. Tra cứu

Hệ thống phải cung cấp khả năng tra cứu đúng với vai trò và phạm vi dữ liệu của người đang sử dụng. Kết quả tra cứu phải có phân trang khi danh sách có thể tăng lớn và không được làm lộ dữ liệu của người dùng hoặc tổ chức khác.

Các chức năng tra cứu chính gồm:

- Khách chưa đăng nhập và người dùng có thể xem danh sách sự kiện đã công khai, tìm theo tên, lọc theo danh mục hoặc khoảng ngày và sắp xếp theo thời gian bắt đầu hoặc giá khu thấp nhất.
- Người dùng có thể xem chi tiết sự kiện, địa điểm, thời gian, ảnh bìa, các khu vực, giá vé và tình trạng chỗ còn có thể bán.
- Người dùng đã đăng nhập có thể xem trạng thái xác minh email và thông tin hồ sơ của tài khoản.
- Người mua có thể xem lượt giữ đang hoạt động, thời gian còn lại, đơn hàng của mình, các lần thanh toán, mã giảm giá đang áp dụng, vé đã phát hành, mã QR và trạng thái yêu cầu hoàn.
- Người gửi yêu cầu tạo tổ chức có thể xem nội dung yêu cầu, trạng thái xét duyệt và lý do từ chối trên cùng Organization; sau duyệt, hồ sơ đó trở thành tổ chức hoạt động.
- Quản lý tổ chức có thể xem danh sách thành viên, vai trò và trạng thái; danh sách sự kiện, khu vực, ghế, mã giảm giá, đơn hàng, vé, tiền thu, tiền hoàn, hoa hồng, đối soát và kết quả check-in trong phạm vi tổ chức.
- Nhân viên kiểm soát vé có thể chọn sự kiện thuộc tổ chức của mình, tra cứu kết quả kiểm tra vé và xem lịch sử phù hợp với quyền được cấp.
- Quản trị nền tảng có thể xem các yêu cầu tạo tổ chức, sự kiện chờ duyệt, yêu cầu hoàn vé, giao dịch thanh toán hoặc hoàn tiền chưa rõ kết quả, chính sách hoa hồng, đối soát, chi trả và nhật ký thao tác trên toàn hệ thống.
- Báo cáo phải hỗ trợ lọc theo sự kiện và khoảng thời gian. Dữ liệu hiển thị trên giao diện và dữ liệu xuất CSV phải sử dụng cùng điều kiện lọc, phạm vi quyền và công thức tính.

Khi tra cứu một sự kiện đã hủy, hệ thống không hiển thị sự kiện đó như một sự kiện còn có thể mua vé, nhưng người mua vẫn phải xem được lịch sử đơn hàng và vé liên quan. Tương tự, việc một đối tượng đã chuyển sang trạng thái kết thúc không được làm mất dữ liệu cần cho lịch sử giao dịch và đối soát.

##### 2.1.1.1.3. Tính toán

Hệ thống phải thực hiện các phép tính nghiệp vụ ở phía máy chủ. Giá trị do trình duyệt gửi lên chỉ được xem là dữ liệu tham khảo; máy chủ phải kiểm tra lại giá, số lượng, mức giảm, thời hạn và trạng thái trước khi ghi nhận kết quả.

Các phép tính và kiểm tra số lượng gồm:

- Tính số ghế còn trống của khu ngồi từ trạng thái của các ghế thuộc khu.
- Tính số lượng còn lại của khu đứng từ sức chứa trừ số lượng đang giữ và số lượng đã bán.
- Tính tổng số vé trong một lượt giữ hoặc đơn hàng; tổng số lượng không được vượt quá tám vé.
- Kiểm tra một tài khoản chỉ có tối đa một lượt giữ đang hoạt động và xác định lượt giữ hết hạn khi thời gian hiện tại lớn hơn hoặc bằng thời điểm hết hạn.
- Kiểm tra toàn bộ lựa chọn có thể giữ trong cùng một giao dịch; nếu một ghế hoặc một phần số lượng khu đứng không còn hợp lệ thì không giữ một phần còn lại.

Giá trị đơn hàng được tính theo các công thức:

```text
tiền trước giảm = tổng (số lượng × đơn giá của từng mục đơn hàng)
tổng tiền giảm  = mức giảm hợp lệ của mã giảm giá
tổng thanh toán = tiền trước giảm - tổng tiền giảm
```

Mã giảm giá theo phần trăm phải có tỷ lệ lớn hơn không và không vượt quá 30%. Với mã giảm số tiền cố định, mức giảm thực tế bằng giá trị nhỏ hơn giữa số tiền được cấu hình và 30% tiền trước giảm. Tổng số lượt mã đang được giữ cộng với số lượt đã sử dụng không được vượt quá giới hạn `maxUses`. Mỗi đơn chỉ được áp dụng tối đa một mã và mã phải thuộc đúng tổ chức của sự kiện.

Sau khi đơn được thanh toán, tổng tiền giảm được phân bổ theo tỷ lệ giá gốc của từng vé. Hệ thống phải bảo đảm tổng số tiền thực trả của tất cả vé bằng tổng thanh toán của đơn và không có vé mang số tiền âm. Phần đồng lẻ được phân bổ theo phần dư lớn nhất với thứ tự vé ổn định. Khi hoàn một vé, số tiền hoàn lấy từ giá trị thực trả đã lưu trên vé, không tính lại theo giá hoặc mã giảm giá hiện tại.

Đối với từng đơn hàng trong đối soát, hệ thống tính:

```text
tiền còn lại = tiền thanh toán hợp lệ sau giảm giá - tiền hoàn thành công

hoa hồng = 0, nếu tiền còn lại bằng 0
hoa hồng = giá trị nhỏ hơn giữa:
            tiền còn lại
            và tiền còn lại × tỷ lệ phần trăm / 100 + phí cố định

tiền ròng = tiền còn lại - hoa hồng
```

Hoa hồng được làm tròn đến đồng và không vượt tiền còn lại. Backend tổng hợp từng Order đủ điều kiện rồi gọi `Settlement.recalculate(grossRevenue, totalRefund, totalCommission)` chỉ khi DRAFT. `netPayable = grossRevenue - totalRefund - totalCommission`; `availableToPay = netPayable - paidAmount - pendingAmount`. Tổng đã chi và đang chờ không vượt netPayable, kể cả khi nhiều lần chi đồng thời.

Ngoài các phép tính về tiền, hệ thống phải kiểm tra các khoảng thời gian nghiệp vụ: thời gian mở bán và diễn ra sự kiện; thời hạn mười phút của lượt giữ; khoảng check-in từ 60 phút trước giờ bắt đầu đến trước thời điểm kết thúc; hiệu lực mã giảm giá; điều kiện gửi yêu cầu hoàn trước giờ bắt đầu; và điều kiện xác nhận đối soát sau khi sự kiện kết thúc, không còn giao dịch liên quan đang chờ hoặc chưa rõ kết quả.

##### 2.1.1.1.4. Kết xuất

Hệ thống phải tạo và cung cấp các kết quả đầu ra sau:

- Danh sách và trang chi tiết sự kiện, bao gồm thông tin mô tả, địa điểm, thời gian, ảnh bìa, khu vực, giá vé và tình trạng chỗ.
- Thông tin lượt giữ hiện tại, các vé đang được chọn và đồng hồ hiển thị thời gian còn lại dựa trên thời hạn do máy chủ cung cấp.
- Kết quả tạo đơn, áp dụng hoặc bỏ mã giảm giá và trạng thái thanh toán. Trang quay về từ VNPAY chỉ hiển thị kết quả đã biết, không tự xác nhận khoản tiền đã thu.
- Vé điện tử với mã vé riêng và hình ảnh QR tương ứng cho từng quyền vào cửa. Người mua có thể xem vé trong tài khoản hoặc chia sẻ QR cho người đi cùng.
- Email chứa OTP, thông tin vé và kết quả xử lý hoàn vé đã được lựa chọn. Lỗi gửi email không được làm đảo ngược giao dịch vé hoặc hoàn tiền đã hoàn tất.
- Kết quả kiểm soát vé rõ ràng cho nhân viên, phân biệt tối thiểu các trường hợp thành công, mã không hợp lệ, sai sự kiện, vé đã dùng, ngoài thời gian check-in và vé không hoạt động.
- Kết quả xét duyệt yêu cầu tạo tổ chức, xét duyệt sự kiện và xét duyệt hoàn vé, bao gồm lý do khi yêu cầu hoặc sự kiện bị từ chối.
- Báo cáo cho quản trị nền tảng và quản lý tổ chức, thể hiện rõ tiền đã thu, doanh thu vé, tiền hoàn, hoa hồng, tiền còn phải trả, số vé theo khu và dữ liệu check-in.
- Tệp CSV sử dụng cùng dữ liệu, phạm vi quyền và bộ lọc với báo cáo trên giao diện. Tệp phải hỗ trợ tiếng Việt khi mở bằng Excel và vô hiệu hóa dữ liệu người dùng có thể bị diễn giải thành công thức.

Các kết quả hiển thị phải sử dụng tiếng Việt, tiền VND và thời gian theo múi giờ `Asia/Ho_Chi_Minh`. Hệ thống không được đưa stack trace hoặc thông tin bí mật của máy chủ ra giao diện; lỗi đầu vào, chưa đăng nhập, không có quyền, xung đột trạng thái, hết chỗ và lỗi hệ thống phải được phân biệt bằng thông báo phù hợp.

#### 2.1.1.2. Yêu cầu chức năng hệ thống

##### 2.1.1.2.1. Môi trường hoạt động

Theo thiết kế, TicketsCenter là ứng dụng web triển khai thống nhất theo mô hình MVC. Giao diện được dựng phía máy chủ bằng JSP và Jakarta Tags/JSTL, kết hợp HTML5, Bootstrap, CSS tùy chỉnh và JavaScript/Fetch cho những thao tác cần cập nhật tương tác như chọn ghế, đếm thời gian giữ vé và quét QR.

Phần xử lý phía máy chủ sử dụng Java 25 và chạy trên Apache Tomcat 11.0.25. Controller được xây dựng bằng Jakarta Servlet; nghiệp vụ nằm trong Model và Service; dữ liệu được truy cập qua Jakarta Persistence/JPA với Hibernate ORM. Microsoft JDBC Driver được sử dụng làm trình điều khiển kết nối bên dưới Hibernate. Ứng dụng không sử dụng Spring hoặc Spring Boot.

Cơ sở dữ liệu local là Microsoft SQL Server, quản trị bằng SSMS; bản trực tuyến dự kiến dùng Azure SQL Database Free. Ánh xạ/truy vấn phải được kiểm thử trên cả hai môi trường. Yêu cầu DBMS330284 tại mục 14 của [spec.md](../../spec.md) là bắt buộc: ERD/lược đồ quan hệ, phân tích tối thiểu 3NF, ánh xạ đủ 15 lớp cùng dữ liệu kỹ thuật, 20 nhóm constraint, 10 View, 17 Stored Procedure, 10 Function, 10 Trigger, 15 Index bổ sung, 17 nghiệp vụ Transaction và 4 nhóm Role/Login. Mỗi mục cần script, nơi dùng và minh chứng kiểm thử; JPA/Hibernate không thay thế các đối tượng SQL này.

Dự án được quản lý và đóng gói bằng Maven dưới dạng tệp WAR. Môi trường local phục vụ phát triển và trình diễn bằng IntelliJ IDEA, JDK 25, Tomcat 11 và SQL Server. Môi trường trực tuyến dự kiến chạy WAR trong Docker trên Render Free, kết nối tới Azure SQL Database và sử dụng HTTPS.

Ảnh bìa phải được lưu ngoài filesystem không bền vững của container. Bí mật kết nối cơ sở dữ liệu, khóa VNPAY, thông tin gửi email và thông tin truy cập nơi lưu ảnh phải được cung cấp qua biến môi trường, không đặt trong JSP, JavaScript, repository mã nguồn hoặc Docker image.

Hệ thống phải hỗ trợ hai loại tác vụ: yêu cầu đồng bộ từ trình duyệt và tác vụ nền để giải phóng lượt giữ hết hạn, truy vấn thanh toán chưa rõ kết quả, tiếp tục xử lý hoàn tiền và gửi email đang chờ. Thời hạn lượt giữ phải được lưu trong cơ sở dữ liệu để trạng thái nghiệp vụ có thể khôi phục sau khi ứng dụng khởi động lại.

##### 2.1.1.2.2. Phân quyền

Hệ thống phải xác thực người dùng và kiểm tra quyền theo từng đối tượng nghiệp vụ. Việc người dùng chọn một tổ chức trên giao diện chỉ tạo ngữ cảnh sử dụng, không phải bằng chứng rằng người đó có quyền trên tổ chức hoặc dữ liệu đang thao tác.

Phạm vi quyền được xác định như sau:

- **Khách chưa đăng nhập:** được xem, tìm kiếm sự kiện đã công khai, xem giá và tình trạng chỗ và đăng ký tài khoản; không được giữ vé hoặc truy cập dữ liệu cá nhân.
- **Người dùng chưa xác minh email:** được đăng nhập, xem sự kiện và xác minh email; chưa được giữ hoặc mua vé.
- **Người mua đã xác minh:** được giữ và mua vé, quản lý lượt giữ của mình, áp dụng mã giảm giá, thanh toán, xem đơn và vé của mình, chia sẻ QR, gửi yêu cầu hoàn và gửi yêu cầu tạo tổ chức.
- **Quản lý tổ chức:** được quản lý thành viên, sự kiện, khu vực, ghế, giá vé, mã giảm giá, báo cáo và hoạt động check-in trong phạm vi tổ chức mà mình có tư cách thành viên quản lý đang hoạt động.
- **Nhân viên kiểm soát vé:** được chọn và kiểm tra vé cho các sự kiện thuộc tổ chức mà mình đang là thành viên; được xem kết quả hoặc lịch sử phù hợp với quyền; không được quản lý doanh thu, chính sách phí hoặc thành viên.
- **Quản trị nền tảng:** được duyệt yêu cầu tạo tổ chức, duyệt hoặc từ chối sự kiện, duyệt hoàn vé, hủy sự kiện, quản lý mã giảm giá của mọi tổ chức, thiết lập chính sách hoa hồng, lập và xác nhận đối soát, ghi nhận chi trả và xem nhật ký trên toàn hệ thống.

Một người dùng có thể thuộc nhiều tổ chức với vai trò khác nhau. Mỗi thao tác phải kiểm tra tư cách thành viên đang hoạt động và tổ chức sở hữu đối tượng. Quản lý không được hạ quyền hoặc vô hiệu hóa người quản lý đang hoạt động cuối cùng của tổ chức và không được cấp quyền quản trị nền tảng cho thành viên.

Chỉ chủ đơn hàng được xem đầy đủ đơn, quản lý vé và gửi yêu cầu hoàn. Chỉ nhân viên hoặc quản lý có quyền check-in trong đúng tổ chức mới được kiểm tra vé. Chỉ quản trị nền tảng được đưa ra quyết định cuối đối với yêu cầu tạo tổ chức, công khai sự kiện, hoàn vé, hủy sự kiện, chính sách hoa hồng và đối soát.

Các yêu cầu truy cập bằng cách thay đổi mã đối tượng phải bị từ chối nếu đối tượng không thuộc phạm vi của người dùng. Hệ thống phải bảo vệ thao tác thay đổi trạng thái bằng session, kiểm tra CSRF và kiểm tra quyền ở phía máy chủ; việc ẩn nút trên giao diện không thay thế kiểm soát quyền.

### 2.1.2. Yêu cầu phi chức năng

#### 2.1.2.1. Liên quan đến người dùng

##### 2.1.2.1.1. Tính tiến hóa

Hệ thống cần có khả năng điều chỉnh và mở rộng khi quy tắc nghiệp vụ hoặc môi trường triển khai thay đổi mà không làm sai dữ liệu lịch sử. Kiến trúc MVC và cách phân tầng Model, Service, Repository, Servlet, View và Integration phải được duy trì để mỗi thay đổi được thực hiện đúng tại lớp chịu trách nhiệm.

Các chính sách có ảnh hưởng đến dữ liệu lịch sử phải được quản lý theo phiên bản. Khi thay đổi tỷ lệ hoa hồng hoặc phí cố định, hệ thống tạo quy tắc mới thay vì sửa quy tắc đã gắn với sự kiện công khai. Giá vé mới chỉ tác động tới lượt giữ mới; giá của lượt giữ còn hiệu lực và đơn đã tạo được giữ nguyên. Tương tự, số tiền hoàn được lấy từ giá trị thực trả đã lưu trên vé, không tính lại theo cấu hình hiện tại.

Cấu trúc cơ sở dữ liệu phải được thay đổi bằng migration có thứ tự và có thể kiểm soát. Hibernate ở môi trường trực tuyến chỉ kiểm tra ánh xạ với schema, không tự động tạo, xóa hoặc thay đổi bảng. Enum được lưu bằng tên để việc thêm hoặc sắp xếp lại giá trị trong mã nguồn không làm đổi ý nghĩa dữ liệu đã lưu.

Các nhà cung cấp bên ngoài như cổng thanh toán, email và lưu trữ ảnh cần được tách qua lớp tích hợp. Việc thay đổi nhà cung cấp hoặc cấu hình môi trường không được làm thay đổi quy tắc cốt lõi của đơn hàng, vé và hoàn tiền.

##### 2.1.2.1.2. Tính tiện dụng

Giao diện phải sử dụng tiếng Việt, trình bày tiền theo VND và hiển thị thời gian theo múi giờ `Asia/Ho_Chi_Minh`. Bố cục phải đáp ứng trên máy tính và điện thoại, đặc biệt tại trang hiển thị QR và màn hình check-in bằng camera.

Các biểu mẫu phải có nhãn rõ ràng, kiểm tra dữ liệu ở trình duyệt để hỗ trợ người dùng và kiểm tra lại ở máy chủ để bảo đảm tính đúng đắn. Thông báo lỗi phải chỉ ra vấn đề ở mức người dùng có thể xử lý, không hiển thị stack trace hoặc chi tiết kỹ thuật nội bộ.

Mỗi màn hình phải thể hiện được trạng thái đang tải, không có dữ liệu, lỗi và thành công. Các thao tác có khả năng tạo giao dịch hoặc thay đổi trạng thái cần hạn chế việc nhấn lặp. Trang chọn vé phải cho người dùng thấy chỗ hoặc số lượng đã chọn và thời gian giữ còn lại; tuy nhiên phải ghi rõ rằng thời gian máy chủ là căn cứ cuối cùng.

Kết quả check-in phải rõ ràng và dễ nhận biết trong điều kiện thao tác tại sự kiện. Hệ thống phải hỗ trợ cả quét QR bằng camera và nhập mã thủ công. Người mua phải dễ dàng xem đơn, vé, QR và trạng thái yêu cầu hoàn trong tài khoản của mình.

##### 2.1.2.1.3. Tính hiệu quả

Hệ thống phải bảo đảm các thao tác giữ vé, sử dụng mã giảm giá, thanh toán, check-in và hoàn vé không tạo dữ liệu trùng hoặc vượt quá số lượng cho phép khi có nhiều yêu cầu đồng thời. Ghế và số lượng khu đứng phải được khóa hoặc cập nhật có điều kiện trong giao dịch; không chỉ dựa vào trạng thái đang có trong bộ nhớ Java.

Các thao tác nhận thông báo thanh toán, xử lý hoàn tiền, gửi email và ghi nhận chi trả phải có khả năng xử lý lặp an toàn. Một thông báo thanh toán hợp lệ được gửi lại không được phát hành thêm vé; một yêu cầu hoàn đã thành công không được tạo thêm giao dịch hoàn; một yêu cầu tạo tổ chức đã duyệt không được tạo thêm tổ chức.

Danh sách sự kiện và báo cáo phải có phân trang. Dữ liệu cần cho View phải được chuyển thành DTO trước khi đóng `EntityManager`; hệ thống không giữ phiên persistence mở đến lúc JSP kết xuất. Không giữ khóa cơ sở dữ liệu trong lúc chờ VNPAY, gửi email, tải ảnh hoặc chờ thao tác của người dùng.

Trong môi trường trực tuyến có tài nguyên hạn chế, hệ thống sử dụng connection pool nhỏ, session gọn và ảnh đã được tối ưu. Các tác vụ nền phải nhận công việc theo lô và có thời điểm thử lại phù hợp, không liên tục truy vấn cơ sở dữ liệu chỉ để giữ dịch vụ thức. Khả năng chịu tải và thời gian đáp ứng chỉ được kết luận sau khi đo trên môi trường triển khai; tài liệu không đặt một con số chưa được kiểm chứng.

##### 2.1.2.1.4. Tính tương thích

Ứng dụng phải hoạt động dưới dạng web trên môi trường Java 25 và Tomcat 11, sử dụng namespace `jakarta.*` tương thích với Servlet 6.1 và JSP 4.0. Các thư viện được đóng gói trong WAR phải tương thích với phiên bản Java và container đã chọn; không đóng gói thêm một Servlet container khác trong ứng dụng.

Tầng persistence phải hoạt động với Microsoft SQL Server ở môi trường local và Azure SQL Database ở môi trường trực tuyến thông qua Microsoft JDBC Driver. Kiểu dữ liệu UUID, số tiền, phần trăm, thời điểm, enum, chỉ mục và cơ chế khóa phải được kiểm thử trên cả hai môi trường trước khi kết luận tương thích.

Giao diện phải đáp ứng trên trình duyệt máy tính và điện thoại. Chức năng quét QR cần sử dụng camera điện thoại hoặc webcam trong ngữ cảnh HTTPS; khi không thể sử dụng camera, nhân viên vẫn có thể nhập mã thủ công để thực hiện cùng một quy trình kiểm tra.

Tệp CSV phải hiển thị được tiếng Việt khi mở bằng Excel. Ảnh bìa, email và thanh toán được tích hợp qua HTTPS hoặc giao thức an toàn phù hợp; bí mật của từng môi trường được cung cấp qua cấu hình thay vì ghi cố định trong mã nguồn.

#### 2.1.2.2. Liên quan đến chuyên viên tin học

##### 2.1.2.2.1. Tính tái sử dụng

Các thành phần dùng chung phải được tách khỏi Servlet và entity để có thể sử dụng ở nhiều quy trình. Bộ tạo QR được dùng chung cho việc phát hành và hiển thị vé; không tự mở kết nối cơ sở dữ liệu hoặc phụ thuộc vào HTTP. Việc kiểm tra vé bằng camera và nhập mã thủ công phải gọi cùng một logic nghiệp vụ để trả về kết quả nhất quán.

Các lớp tích hợp thanh toán, email và lưu trữ ảnh phải cung cấp giao diện rõ ràng để Service sử dụng mà không phụ thuộc trực tiếp vào chi tiết nhà cung cấp. Adapter VNPAY Sandbox, adapter hoàn tiền mô phỏng và adapter chi trả mô phỏng được đặt sau các giao diện tương ứng, giúp kiểm thử thanh toán/hoàn tiền thành công, thất bại hoặc chưa xác định, và chi trả SUCCEEDED/FAILED theo PayoutResult mà không thay đổi quy trình nghiệp vụ.

Repository truy vấn/lưu bằng JPA/Hibernate, gọi Stored Procedure qua StoredProcedureQuery, đọc View/gọi Function bằng truy vấn native có bind parameter. Service kiểm tra quyền và điều phối hành vi Model; Servlet gọi Service, JSP dùng DTO. Các use case được giao cho SP chỉ có một đường ghi nguyên tử và một chủ thể commit; không cập nhật entity managed rồi gọi SP để lặp lại cùng thay đổi. Refresh/clear dữ liệu bị SP thay đổi trước khi dùng lại. Một lớp nghiệp vụ không bắt buộc có một Servlet, Service và Repository riêng.

Các thành phần xử lý tiền, thời gian, phân quyền theo tổ chức, trạng thái giao dịch, phân trang, mã tương quan và kiểm tra dữ liệu cần được tổ chức nhất quán để tái sử dụng trong các chức năng liên quan. Việc tái sử dụng không được gom các nghiệp vụ không liên quan vào một lớp tiện ích tổng hợp hoặc đưa quyết định nghiệp vụ ra khỏi Model và Service.

## 2.2. Quy trình tác nghiệp

### 2.2.1. Quy trình đăng ký, xác minh email và quản lý tài khoản

Khi chưa có tài khoản, người dùng đăng ký bằng `userName` duy nhất, email và mật khẩu. Hệ thống kiểm tra userName không trùng, chuẩn hóa email và bảo đảm email chưa sử dụng, tạo User với platformRole CUSTOMER, map quyền tổ chức rỗng và mật khẩu đã băm, rồi gửi OTP sáu chữ số tới email đăng ký.

Mã OTP xác minh email có hiệu lực trong năm phút và chỉ được sử dụng một lần. Người dùng nhập mã để hoàn tất xác minh. Nếu mã đúng, còn thời hạn và chưa bị sử dụng hoặc vô hiệu hóa, hệ thống ghi nhận thời điểm xác minh email. Nếu nhập sai, hệ thống tăng số lần thất bại; sau năm lần sai, mã bị vô hiệu hóa. Người dùng chỉ được yêu cầu gửi lại mã sau ít nhất 60 giây, và mã mới thay thế mã cũ có cùng mục đích. Tần suất gửi và xác minh được giới hạn theo tài khoản và nguồn yêu cầu.

Sau khi đăng ký, người dùng đăng nhập bằng thông tin tài khoản. Hệ thống kiểm tra mật khẩu, trạng thái tài khoản và tạo session phía máy chủ nếu xác thực thành công. Mã session phải được tạo lại sau khi đăng nhập; cookie phiên sử dụng `HttpOnly`, dùng `Secure` khi chạy trên HTTPS và có cấu hình `SameSite` phù hợp với luồng thanh toán. Hệ thống cho phép một tài khoản đăng nhập trên nhiều thiết bị, nhưng chỉ người dùng đã xác minh email mới được giữ và mua vé.

Khi quên mật khẩu, người dùng yêu cầu OTP với mục đích đặt lại mật khẩu. OTP này độc lập với OTP xác minh email và tuân theo cùng quy tắc về thời hạn, số lần nhập sai và sử dụng một lần. Sau khi xác minh thành công, hệ thống cấp quyền đặt mật khẩu mới trong một khoảng thời gian giới hạn. Việc cập nhật mật khẩu và tăng phiên bản xác thực được thực hiện trong cùng giao dịch để vô hiệu hóa toàn bộ phiên đăng nhập cũ.

Người dùng đã đăng nhập có thể xem và cập nhật họ tên, số điện thoại trong hồ sơ. Email là thông tin định danh của tài khoản và không được thay đổi thông qua thao tác cập nhật hồ sơ thông thường. Mọi thao tác phải kiểm tra dữ liệu ở phía máy chủ và không ghi mật khẩu, OTP hoặc bí mật xác thực vào log.

### 2.2.2. Quy trình đăng ký và xét duyệt tổ chức

Người mua đã đăng nhập, tài khoản hoạt động và đã xác minh email nhập tên tổ chức, email liên hệ, điện thoại và mô tả. Hệ thống xác định requester từ session, tạo Organization DRAFT rồi gọi submitForApproval để chuyển cùng hồ sơ sang PENDING_APPROVAL, lưu thời điểm gửi và audit.

Người gửi xem nội dung và trạng thái của chính Organization. Trong thời gian PENDING_APPROVAL, hồ sơ đã tồn tại nhưng chưa là tổ chức được phép hoạt động; requester chưa có quyền quản lý sự kiện từ hồ sơ đó. Quyền MANAGER chỉ được cấp khi duyệt thành công.

Quản trị mở các Organization PENDING_APPROVAL để xem requester, tên, liên hệ và mô tả. Nếu từ chối, gọi Organization.reject(reason) để chuyển cùng hồ sơ sang REJECTED và lưu rejectionReason, người duyệt, thời điểm quyết định cùng audit. Kết quả hiển thị cho requester; chưa thiết kế gửi lại hồ sơ tổ chức đã bị từ chối.

Nếu chấp nhận, hệ thống gọi Organization.approve(), chuyển chính hồ sơ sang APPROVED, tạo CommissionRule ban đầu và gọi User.assignRole(organization, MANAGER) trên requester trong cùng giao dịch, cùng thông tin quyết định và audit. Xử lý lặp trả kết quả cũ, không tạo Organization, quy tắc phí hoặc liên kết quyền trùng.

### 2.2.3. Quy trình quản lý thành viên và phân quyền trong tổ chức

Sau khi tổ chức được tạo, người quản lý chọn tổ chức cần thao tác. Hệ thống kiểm tra tư cách thành viên quản lý đang hoạt động thay vì chỉ tin vào mã tổ chức được gửi từ giao diện. Nếu có quyền, người quản lý xem danh sách thành viên, vai trò và trạng thái của họ.

Để thêm thành viên, quản lý nhập `userName` duy nhất của tài khoản đã tồn tại. Hệ thống tìm User theo userName, kiểm tra quyền MANAGER của actor, cặp User–Organization và gọi User.assignRole(org, role) trên tài khoản mục tiêu. Quyền được cập nhật vào organizationRoles; UserOrganizationRole kỹ thuật lưu liên kết/lịch sử nếu cần. Thành viên bị vô hiệu có thể được kích hoạt lại trên cùng liên kết; thành viên đang hoạt động không được thêm trùng. Chưa có luồng lời mời riêng.

Quản lý đổi vai trò giữa MANAGER và CHECK_IN_STAFF bằng User.assignRole(org, role), hoặc thu hồi quyền bằng User.revokeRole(org). Thu hồi bỏ quyền hiệu lực khỏi organizationRoles, lưu trạng thái/lịch sử ở persistence và không vô hiệu hóa toàn bộ User. Hệ thống kiểm tra actor và tổ chức mục tiêu; nhân viên check-in không có quyền quản lý thành viên, doanh thu hoặc phí.

Hệ thống không cho phép hạ quyền hoặc vô hiệu hóa người quản lý đang hoạt động cuối cùng. Người quản lý cũng không thể cấp quyền quản trị nền tảng thông qua chức năng thành viên tổ chức. Sau khi thay đổi, quyền mới phải được áp dụng cho các yêu cầu tiếp theo và thao tác được ghi vào nhật ký để phục vụ truy vết.

### 2.2.4. Quy trình tạo sự kiện, thiết lập khu vé và xét duyệt công khai

Người quản lý chọn một tổ chức mà mình có quyền và tạo sự kiện ở trạng thái bản nháp. Người quản lý nhập tiêu đề, mô tả, danh mục, tên và địa chỉ địa điểm, thời gian mở bán, thời gian kết thúc bán, thời gian bắt đầu, thời gian kết thúc và ảnh bìa. Hệ thống kiểm tra sự kiện thuộc đúng tổ chức và thỏa điều kiện `saleStart < saleEnd <= startTime < endTime`.

Tiếp theo, người quản lý thiết lập khu vé ở Event DRAFT hoặc REJECTED. Khu ngồi nhập tên, số hàng, số ghế mỗi hàng và giá; hệ thống sinh hàng A, B,… và số ghế với nhãn duy nhất trong khu. Khu đứng nhập tên, capacity và giá; không tạo Seat, khởi tạo heldQuantity/soldQuantity bằng 0 để theo dõi quota. Không sửa cấu trúc khi PENDING_APPROVAL; sau PUBLISHED cấu trúc được khóa.

Trong thời gian sự kiện còn là bản nháp hoặc đã bị từ chối, người quản lý được chỉnh sửa thông tin, khu vực và cấu trúc vé. Khi hoàn tất, người quản lý gửi sự kiện để duyệt. Hệ thống kiểm tra lại dữ liệu bắt buộc, ảnh bìa, mốc thời gian, cấu trúc khu vực và chính sách phí có thể áp dụng, sau đó chuyển sự kiện sang trạng thái chờ duyệt.

Quản trị nền tảng xem nội dung sự kiện và quyết định công khai hoặc từ chối. Nếu từ chối, quản trị nhập lý do; tổ chức có thể chỉnh sửa rồi gửi lại. Nếu công khai, hệ thống gắn quy tắc hoa hồng đang có hiệu lực của tổ chức và mở sự kiện theo thời gian bán đã cấu hình. Từ thời điểm này, không được đổi loại khu, thêm hoặc xóa ghế, thay đổi số hàng hay sức chứa. Giá niêm yết vẫn có thể cập nhật, nhưng lượt giữ đã tồn tại tiếp tục dùng đơn giá được chụp tại thời điểm giữ.

### 2.2.5. Quy trình quản lý mã giảm giá

Quản lý tổ chức tạo mã giảm giá trong phạm vi tổ chức của mình; quản trị nền tảng có thể quản lý mã của mọi tổ chức. Người dùng nhập mã, loại giảm theo phần trăm hoặc số tiền cố định, giá trị giảm, thời gian hiệu lực, trạng thái hoạt động và giới hạn tổng lượt sử dụng. Hệ thống kiểm tra mã thuộc đúng một tổ chức, giá trị dương và tỷ lệ phần trăm không vượt quá 30%.

Mã giảm giá của tổ chức được áp dụng cho các sự kiện do chính tổ chức đó phát hành. Hệ thống không hỗ trợ mã chung không thuộc tổ chức và mỗi đơn chỉ được dùng tối đa một mã. Khi người mua nhập mã tại bước thanh toán, hệ thống kiểm tra mã đang hoạt động, còn trong thời hạn, đúng tổ chức của sự kiện và còn lượt sử dụng.

Nếu hợp lệ, hệ thống tính mức giảm ở máy chủ và giữ một lượt sử dụng cho đơn đến khi lượt giữ vé hết hạn. Với mã phần trăm, tỷ lệ giảm không vượt quá 30%. Với mã số tiền cố định, số tiền giảm thực tế là giá trị nhỏ hơn giữa số tiền cấu hình và 30% tiền trước giảm. Việc kiểm tra quota, giữ lượt và cập nhật tổng tiền đơn phải được thực hiện trong cùng giao dịch để tổng số lượt đang giữ và đã dùng không vượt quá `maxUses`.

Khi thanh toán thành công hoặc đơn 0 đồng được hoàn tất, lượt mã chuyển sang đã sử dụng. Nếu người mua bỏ mã trước khi bắt đầu thanh toán, hoặc đơn chưa thanh toán bị hủy hay hết hạn, hệ thống trả lại lượt đang giữ. Sau khi mã đã được sử dụng, việc hoàn vé một phần hoặc toàn bộ không trả lại lượt mã. Người quản lý không được giảm `maxUses` xuống thấp hơn tổng số lượt đang giữ và đã sử dụng.

### 2.2.6. Quy trình tìm kiếm sự kiện, chọn chỗ và giữ vé

Khách truy cập mở danh sách sự kiện công khai. Hệ thống chỉ hiển thị sự kiện đã được duyệt và không còn ở trạng thái hủy. Người dùng có thể tìm theo tên, lọc theo danh mục hoặc khoảng ngày, sắp xếp theo thời gian bắt đầu hoặc giá khu thấp nhất và chuyển trang khi có nhiều kết quả.

Khi mở chi tiết sự kiện, người dùng xem mô tả, địa điểm, thời gian, ảnh bìa, các khu vực, giá vé và tình trạng chỗ. Đối với khu ngồi, giao diện hiển thị các ghế có thể chọn. Đối với khu đứng, giao diện cho nhập số lượng trong phạm vi còn lại. Chỉ sự kiện đã công khai và đang trong khoảng mở bán mới cho phép giữ vé.

Để tiếp tục, người dùng phải đăng nhập và xác minh email. Người dùng chọn ghế hoặc số lượng vé của một hay nhiều khu thuộc cùng sự kiện; tổng số vé không vượt quá tám. Mục chọn ở khu ngồi luôn gắn một ghế và có số lượng bằng một, còn mục chọn ở khu đứng không gắn ghế và có số lượng lớn hơn không.

Khi người dùng xác nhận giữ, hệ thống kiểm tra tài khoản không có lượt giữ đang hoạt động khác, kiểm tra sự kiện còn bán, khóa các ghế hoặc quota khu đứng theo thứ tự ổn định và xác nhận toàn bộ lựa chọn còn khả dụng. Nếu bất kỳ chỗ nào không còn hợp lệ, hệ thống từ chối toàn bộ yêu cầu và không giữ một phần âm thầm.

Nếu mọi điều kiện đạt, hệ thống tạo `TicketHold` có thời hạn mười phút và các `TicketHoldItem` chứa khu, ghế hoặc số lượng cùng đơn giá hiện tại. Ghế được chuyển sang đang giữ hoặc số lượng đang giữ của khu đứng được tăng tương ứng. Giao diện hiển thị lựa chọn và đồng hồ đếm thời gian còn lại; thời gian máy chủ là căn cứ cuối cùng để xác định hiệu lực.

### 2.2.7. Quy trình lập đơn, áp dụng mã giảm giá, xử lý thanh toán và phát hành vé

Từ lượt giữ còn hiệu lực, người mua chuyển tới bước thanh toán. Hệ thống kiểm tra chủ lượt giữ, sự kiện, tổng số lượng và trạng thái chỗ, sau đó tạo tối đa một `Order` từ lượt giữ. Mỗi `TicketHoldItem` được chuyển thành một `OrderItem`; tên khu, nhãn ghế và đơn giá được lưu tại thời điểm tạo đơn. Việc tạo đơn không kéo dài thời hạn mười phút của lượt giữ.

Người mua có thể nhập một mã giảm giá. Hệ thống thực hiện quy trình kiểm tra mã, giữ quota và tính lại tiền trước giảm, tiền giảm và tổng thanh toán ở máy chủ. Nếu bỏ mã trước khi khởi tạo thanh toán, hệ thống giải phóng lượt mã và khôi phục tổng tiền. Khi đã có một lần thanh toán đang xử lý hoặc chưa rõ kết quả, người mua không được thay đổi tổng tiền hoặc mã giảm giá của lần thanh toán đó.

Với đơn có tổng tiền > 0, hệ thống gọi Payment.start(order, txnRef), lưu Payment.order bắt buộc và amount chụp bất biến từ Order.totalAmount, rồi commit trước khi chuyển tới VNPAY Sandbox. Return URL chỉ hiển thị; IPN hoặc query đã xác thực chữ ký, merchant, txnRef, amount, loại tiền và trạng thái mới quyết định khoản thu.

Chỉ khi Event PUBLISHED, saleStart <= now < saleEnd và Hold còn hiệu lực, hệ thống ghi Payment CAPTURED/paidAt, gọi Order.markPaid(payment, now) để đặt acceptedPayment và Order.paidAt, tiêu thụ Hold, bán chỗ/quota, tiêu thụ coupon và phát hành Ticket trong cùng giao dịch. Một quyền vào cửa tạo một vé; item đứng quantity N tạo N mã/QR riêng. Email gửi sau commit, lỗi gửi không đảo ngược giao dịch.

IPN lặp trả cùng kết quả, không đổi acceptedPayment hoặc phát hành thêm vé. Payment UNKNOWN phải được truy vấn, không tự tạo lần thu mới. Thu sau khi Hold hết hạn/giải phóng, thu trùng, Event hủy hoặc qua saleEnd vẫn ghi CAPTURED và tạo đúng một Refund PAYMENT_COMPENSATION/APPROVED gắn Payment, không có Ticket. Nghĩa vụ bù trừ theo cùng tiến độ/lịch sử hoàn, không thay đổi vé hoặc doanh thu vé của đơn.

Đơn 0đ vẫn kiểm tra chủ đơn, Hold, Event và cửa sổ bán, gọi Order.markPaid(null, now), ghi paidAt và phát hành vé mà không gọi VNPAY hoặc tạo Payment giả. acceptedPayment trống; coupon được tiêu thụ nếu có. Hoàn vé 0đ giữ Refund và hoàn tất không chuyển tiền.

### 2.2.8. Quy trình hủy lượt giữ và xử lý lượt giữ hết hạn

Người mua có thể chủ động hủy lượt giữ đang hoạt động trước khi thanh toán. Hệ thống xác định người yêu cầu là chủ lượt giữ, kiểm tra trạng thái hiện tại và khóa các dữ liệu liên quan. Nếu lượt giữ vẫn hoạt động, hệ thống chuyển trạng thái sang đã giải phóng, trả ghế về trạng thái còn trống hoặc giảm số lượng đang giữ của khu đứng.

Nếu lượt giữ đã tạo đơn chưa thanh toán, đơn được cập nhật sang trạng thái hủy hoặc hết hạn phù hợp. Lượt sử dụng mã giảm giá đang được giữ cho đơn được trả lại. Những thay đổi đối với lượt giữ, đơn, ghế hoặc quota và mã giảm giá phải được thực hiện trong cùng một giao dịch để không để lại chỗ bị khóa hoặc quota bị trừ sai.

Khi thời gian máy chủ lớn hơn hoặc bằng `expiresAt`, lượt giữ được xem là hết hạn dù tác vụ nền chưa kịp cập nhật trạng thái. Mỗi yêu cầu tạo đơn, áp dụng mã hoặc bắt đầu thanh toán phải kiểm tra thời hạn và xử lý dữ liệu quá hạn ngay trong giao dịch. Tác vụ nền chỉ hỗ trợ quét và dọn các lượt giữ đã hết hạn, không phải nguồn duy nhất xác định hiệu lực.

Người dùng không thể tự động thay lượt giữ hiện tại bằng một lựa chọn mới. Muốn giữ sự kiện hoặc chỗ khác, người dùng phải hủy lượt đang hoạt động hoặc chờ hệ thống giải phóng lượt đã hết hạn. Việc tải lại trang, tạo đơn hoặc áp dụng mã không làm thay đổi thời điểm hết hạn ban đầu.

### 2.2.9. Quy trình tra cứu đơn hàng, nhận và chia sẻ mã QR vé

Người mua đăng nhập và mở khu vực đơn hàng của mình. Hệ thống lấy danh sách đơn theo đúng chủ sở hữu, hiển thị mã đơn, sự kiện, các mục vé, tiền trước giảm, tiền giảm, tổng tiền, trạng thái và thông tin thanh toán liên quan. Đơn của người dùng khác phải bị từ chối ngay cả khi người truy cập thay đổi mã trên URL hoặc dữ liệu gửi lên.

Đối với đơn đã hoàn tất, người mua xem danh sách vé đã phát hành. Mỗi vé hiển thị mã vé, sự kiện, khu vực, ghế nếu có, số tiền thực trả và trạng thái. Người mua mở từng vé để xem mã QR; liên kết xem vé trong tài khoản yêu cầu đăng nhập và không được làm lộ toàn bộ đơn cho người không có quyền.

Hệ thống gửi email chứa thông tin vé và liên kết xem QR sau khi giao dịch phát hành vé được commit. Nếu gửi email thất bại, vé vẫn tồn tại trong tài khoản và tác vụ nền có thể thử gửi lại. Một sự kiện gửi phải có khóa theo dõi để hạn chế email trùng nhưng không làm thay đổi trạng thái đơn hoặc vé.

Người mua được chia sẻ ảnh QR hoặc một liên kết token riêng cho người đi cùng. Người nhận không cần có tài khoản để sử dụng vé tại cổng, nhưng thao tác chia sẻ không chuyển quyền sở hữu đơn, quyền yêu cầu hoàn hoặc quyền xem các vé còn lại. Mã QR và token chia sẻ phải khó đoán, không được suy ra trực tiếp từ ID tuần tự và không được ghi nguyên giá trị vào log.

### 2.2.10. Quy trình kiểm tra vé và xác nhận vào cửa

Nhân viên kiểm soát vé đăng nhập, chọn tổ chức và sự kiện cần thực hiện check-in. Hệ thống kiểm tra tư cách thành viên `CHECK_IN_STAFF` hoặc `MANAGER` còn hoạt động và xác nhận sự kiện thuộc đúng tổ chức. Nhân viên không được check-in cho sự kiện ngoài phạm vi hoặc sự kiện đã hủy.

Nhân viên sử dụng camera điện thoại, webcam để quét QR hoặc nhập mã vé thủ công. Cả hai cách nhập phải gọi cùng một logic kiểm tra. Hệ thống tìm vé theo mã, xác định sự kiện của vé, trạng thái vé và thời gian hiện tại. Khoảng check-in hợp lệ bắt đầu từ 60 phút trước giờ sự kiện và kết thúc trước `endTime`.

Nếu mã không tồn tại, vé thuộc sự kiện khác, vé đã sử dụng, đang chờ hoàn, đã hoàn, đã vô hiệu hóa hoặc ngoài thời gian cho phép, hệ thống từ chối vào cửa và hiển thị kết quả tương ứng. Nếu vé hợp lệ, hệ thống chuyển trạng thái vé từ hoạt động sang đã sử dụng và ghi một `TicketCheckInLog` kỹ thuật thành công trong cùng giao dịch.

Trong trường hợp hai thiết bị kiểm tra cùng một vé gần như đồng thời, hệ thống phải bảo đảm chỉ một giao dịch chuyển trạng thái thành công. Lần còn lại nhận kết quả vé đã sử dụng. Các lần kiểm tra được lưu với thời điểm, sự kiện, nhân viên thực hiện và kết quả để phục vụ xem lịch sử phù hợp với quyền.

### 2.2.11. Quy trình yêu cầu, xét duyệt và xử lý hoàn vé

Người mua mở một đơn đã thanh toán và chọn các vé muốn yêu cầu hoàn. Hệ thống kiểm tra người gửi là chủ đơn, tất cả vé được chọn thuộc cùng đơn, sự kiện chưa bắt đầu, vé chưa sử dụng và mỗi vé chưa có yêu cầu hoàn đang mở. Số tiền yêu cầu được tính bằng tổng `paidAmount` của các vé được chọn.

Người mua nhập lý do; hệ thống gọi Refund.requestForTickets(order, tickets, CUSTOMER_REQUEST, reason, now), tạo Refund REQUESTED với purpose CUSTOMER_REFUND, amount = tổng Ticket.paidAmount, payment = acceptedPayment hoặc null nếu 0đ. Trong cùng giao dịch, vé chuyển REFUND_PENDING và audit được ghi. Người mua theo dõi cùng nghĩa vụ trong tài khoản.

Quản trị xem Refund REQUESTED, đơn, vé, amount và lý do. Từ chối gọi Refund.reject(reason), lưu rejectionReason, chuyển REJECTED và khôi phục vé ACTIVE chỉ nếu Event chưa hủy. Lý do khách vẫn giữ trên Refund; quyết định và mốc thời gian lưu audit. Duyệt gọi Refund.approve() để chuyển APPROVED trên cùng nghĩa vụ.

Refund APPROVED có amount > 0 bắt đầu bằng beginAttempt(attemptId), lưu currentAttemptId và chuyển PROCESSING; lịch sử nằm ở RefundTransferLog kỹ thuật. Adapter hoàn mô phỏng chạy sau commit. recordAttemptResult(attemptId, SUCCEEDED, reference, processedAt) mới chuyển Refund COMPLETED và ghi mốc thực hoàn đã xác minh. Service cập nhật vé REFUNDED, trả đúng phân bổ ghế/quota và ghi lịch sử trong cùng giao dịch, không giải phóng ghế của lượt mua mới vì callback cũ. Amount = 0 dùng completeWithoutTransfer, vẫn hoàn tất Refund/vé/trả kho nhưng không tạo lần chuyển tiền hoặc mốc chuyển tiền giả.

Kết quả FAILED chuyển Refund RETRYABLE; UNKNOWN chuyển NEEDS_RECONCILIATION, vé vẫn REFUND_PENDING. Chỉ FAILED đã xác minh mới được thử lại bằng attemptId mới trên cùng nghĩa vụ; UNKNOWN phải đối chiếu về kết quả cuối trước. Service kiểm tra currentAttemptId, lịch sử và amount; ID lặp trả kết quả cũ, không hoàn/trả kho hai lần hoặc hồi quy kết quả cuối. Một nghĩa vụ tối đa một lần thành công; tổng thực hoàn không vượt Payment.amount. Coupon đã dùng không được trả; email sau commit không ảnh hưởng kết quả.

### 2.2.12. Quy trình hủy sự kiện và hoàn vé tự động

Quản trị nền tảng chọn một sự kiện đã công khai và yêu cầu hủy trước thời điểm bắt đầu. Hệ thống kiểm tra vai trò, trạng thái và thời gian của sự kiện. Nếu hợp lệ, sự kiện chuyển sang trạng thái hủy, ngừng xuất hiện như sự kiện có thể mua, chặn lượt giữ mới và chặn mọi thao tác check-in.

Hệ thống giải phóng các lượt giữ còn hoạt động của sự kiện, trả ghế hoặc quota khu đứng, cập nhật các đơn chưa thanh toán và trả lại lượt mã giảm giá đang được giữ. Các thao tác này phải sử dụng thứ tự khóa nhất quán với quy trình thanh toán để tránh trường hợp phát hành vé sau khi sự kiện đã hủy.

Theo từng Order đã phát hành, hệ thống khóa vé và các Refund liên quan. Refund REQUESTED của khách được dùng lại bằng adoptEventCancellation(), giữ lịch sử lý do, đổi reasonType sang EVENT_CANCELLATION và tự duyệt. Refund đã duyệt hoặc đang xử lý tiếp tục giữ nguyên tập vé/amount; không gộp thêm vé vào nghĩa vụ này. Chỉ vé đủ điều kiện còn lại mới tạo Refund CUSTOMER_REFUND với reasonType EVENT_CANCELLATION rồi tự duyệt. Vé đã hoàn hoặc đang được xử lý không sinh nghĩa vụ/lần hoàn trùng.

Các Refund được đưa vào quy trình hoàn mô phỏng trên cùng nghĩa vụ; thành công cập nhật Refund/vé/trả kho nguyên tử, hoàn 0đ dùng completeWithoutTransfer. Ticket.paidAmount không bị tính lại. Từng Order có thể xử lý theo lô và tiếp tục sau restart; thông tin Event CANCELLED và chặn bán/check-in có hiệu lực ngay.

Vé đã check-in trước thời điểm sự kiện bị hủy không tự động được xem là đủ điều kiện hoàn theo quy tắc hiện tại. Trường hợp này được ghi nhận để quản trị xử lý như một ngoại lệ, không tự động hoàn tiền hoặc khôi phục vé. Người mua được thông báo về việc hủy và kết quả hoàn; lỗi gửi thông báo không làm thay đổi trạng thái giao dịch.

### 2.2.13. Quy trình thiết lập chính sách hoa hồng, đối soát và chi trả cho tổ chức

Quản trị nền tảng thiết lập chính sách hoa hồng cho từng tổ chức bằng cách nhập tỷ lệ phần trăm, phí cố định và khoảng hiệu lực. Khi một sự kiện được duyệt công khai, hệ thống chọn quy tắc đang có hiệu lực của đúng tổ chức và gắn quy tắc đó với sự kiện. Tỷ lệ và phí của quy tắc đã gắn không được sửa; thay đổi chính sách được thực hiện bằng một quy tắc mới cho các sự kiện tiếp theo.

Sau endTime dự kiến, kể cả Event CANCELLED, quản trị có thể lập Settlement DRAFT nếu có Order đủ điều kiện và Event chưa có bản khác. Backend tổng hợp mỗi Order đúng một lần; SettlementOrderSnapshot kỹ thuật giữ chi tiết truy vết, không tạo lớp mục đối soát nghiệp vụ. Việc không còn Payment/Refund đang chờ hoặc chưa rõ là điều kiện confirm; DRAFT vẫn có thể tính lại trước khi chốt.

Với từng đơn, hệ thống lấy số tiền thanh toán hợp lệ sau giảm giá làm doanh thu gộp, trừ tiền hoàn thành công để xác định số tiền còn lại. Nếu số tiền còn lại bằng không thì hoa hồng bằng không; nếu lớn hơn không, hoa hồng được tính từ tỷ lệ phần trăm và phí cố định, làm tròn đến đồng và giới hạn không vượt số tiền còn lại. Khoản thu trùng, thu đến muộn và hoàn tiền bù trừ không tạo doanh thu vé hoặc hoa hồng.

Backend tính từng Order rồi gọi Settlement.recalculate để cập nhật grossRevenue, totalRefund, totalCommission khi DRAFT; netPayable là doanh thu trừ hoàn và phí. Trước confirm, hệ thống khóa dữ liệu, tính lại từ nguồn mới nhất và kiểm tra toàn bộ Payment/Refund chưa giải quyết trong cùng giao dịch. Settlement.confirm(now) chuyển CONFIRMED và đóng băng snapshot từng Order cùng tổng doanh thu/hoàn/phí; cấu hình mới không sửa số liệu đã xác nhận.

Quản trị gọi Settlement.beginPayout(payoutId, amount) để giữ khoản chờ với ID ổn định và amount <= availableToPay. SettlementTransferLog kỹ thuật giữ lịch sử. recordPayoutResult với SUCCEEDED giải phóng pendingAmount và tăng paidAmount; FAILED giải phóng pending, giữ lịch sử và lần thử mới dùng ID mới. Service khóa Settlement, kiểm tra ID/amount; cùng ID không giữ/chi thêm và khác amount bị từ chối. paidAmount + pendingAmount <= netPayable; chỉ paidAmount = netPayable và pendingAmount = 0 mới PAID. Số phải trả 0 hoàn tất không có lần chi giả.

### 2.2.14. Quy trình thống kê hoạt động và xuất báo cáo

Quản trị nền tảng hoặc quản lý tổ chức đăng nhập và mở chức năng báo cáo. Hệ thống xác định phạm vi dữ liệu từ vai trò: quản trị được xem toàn hệ thống, còn quản lý chỉ được xem dữ liệu của tổ chức mà mình có tư cách quản lý đang hoạt động. Nhân viên check-in không được truy cập báo cáo doanh thu.

Người dùng chọn sự kiện và khoảng thời gian cần thống kê. Hệ thống kiểm tra sự kiện thuộc phạm vi được phép, áp dụng bộ lọc và tổng hợp dữ liệu từ đơn hàng, thanh toán, vé, hoàn tiền, đối soát và check-in. Báo cáo phải phân biệt rõ tiền đã thu, doanh thu vé, tiền hoàn, hoa hồng, số tiền còn phải trả, số vé theo khu và kết quả kiểm soát vào cửa.

Các khoản thanh toán trùng hoặc đến muộn và hoàn tiền bù trừ được phản ánh riêng để đối chiếu dòng tiền, không được cộng vào doanh thu vé hoặc cơ sở tính hoa hồng. Sự kiện không có đơn đủ điều kiện vẫn có thể hiển thị số liệu bằng không, nhưng không tạo bản đối soát rỗng trái với ràng buộc dữ liệu.

Khi người dùng yêu cầu xuất báo cáo, hệ thống sử dụng cùng công thức, phạm vi quyền và bộ lọc đang áp dụng trên giao diện để tạo tệp CSV. Tệp phải hỗ trợ tiếng Việt khi mở bằng Excel và xử lý dữ liệu do người dùng nhập để nội dung ô không bị diễn giải thành công thức. Việc xuất dữ liệu không được mở rộng phạm vi truy cập so với báo cáo đang xem.

# CHƯƠNG 3: MÔ HÌNH HÓA YÊU CẦU

Chương này mô hình hóa các yêu cầu của TicketsCenter thông qua việc nhận diện tác nhân, xác định danh sách chức năng và xây dựng các lược đồ Use case. Các chức năng được hình thành trực tiếp từ phạm vi quyền, quy tắc nghiệp vụ và mô hình lớp đã xác định; không bổ sung tác nhân hoặc nghiệp vụ nằm ngoài đặc tả hệ thống.

## 3.1. Nhận diện tác nhân và chức năng của hệ thống

Tác nhân là người hoặc hệ thống bên ngoài có tương tác với TicketsCenter để đạt được một mục tiêu cụ thể. Các trạng thái khách chưa đăng nhập, người dùng chưa xác minh và người mua đã xác minh được tách riêng để thể hiện điều kiện truy cập khác nhau, dù đều liên quan đến cùng đối tượng người dùng trong mô hình dữ liệu. Ngoài các tác nhân con người, VNPAY Sandbox được xem là tác nhân hệ thống bên ngoài tham gia vào quá trình thanh toán thử nghiệm.

*Bảng 3.1: Nhận diện tác nhân và chức năng*

| Tác nhân | Chức năng |
|---|---|
| Khách chưa đăng nhập | Đăng ký tài khoản; đăng nhập; đặt lại mật khẩu; tìm kiếm và lọc sự kiện; xem chi tiết sự kiện, giá vé và tình trạng chỗ. |
| Người dùng chưa xác minh email | Đăng nhập; xác minh email bằng OTP; đặt lại mật khẩu; cập nhật hồ sơ; tìm kiếm và xem sự kiện. Người dùng chưa xác minh chưa được giữ vé. |
| Người mua đã xác minh email | Cập nhật hồ sơ; tìm kiếm và xem sự kiện; chọn chỗ và giữ vé; hủy lượt giữ; tạo đơn; áp dụng hoặc bỏ mã giảm giá; thanh toán; xem đơn và vé; chia sẻ QR; gửi yêu cầu hoàn; gửi yêu cầu tạo tổ chức và xem kết quả. |
| Quản lý tổ chức | Thêm thành viên; thay đổi vai trò hoặc vô hiệu hóa thành viên; tạo và chỉnh sửa sự kiện; thiết lập khu ngồi, ghế và khu đứng; cập nhật giá vé; gửi sự kiện để duyệt; quản lý mã giảm giá; kiểm tra vé; xem lịch sử kiểm tra; xem, lọc và xuất báo cáo của tổ chức. |
| Nhân viên kiểm soát vé | Chọn sự kiện thuộc tổ chức; kiểm tra vé bằng QR hoặc mã nhập tay; xem kết quả và lịch sử kiểm tra phù hợp với quyền. |
| Quản trị nền tảng | Duyệt hoặc từ chối yêu cầu tạo tổ chức; duyệt công khai hoặc từ chối sự kiện; quản lý mã giảm giá của mọi tổ chức; duyệt hoặc từ chối yêu cầu hoàn; xử lý hoàn tiền mô phỏng; hủy sự kiện; thiết lập chính sách hoa hồng; lập, tính lại và xác nhận đối soát; ghi nhận chi trả; xem báo cáo toàn hệ thống; xuất CSV và xem nhật ký thao tác. |
| VNPAY Sandbox | Tiếp nhận yêu cầu thanh toán thử nghiệm; chuyển người mua tới trang thanh toán; gửi kết quả qua luồng quay về và IPN; cung cấp dữ liệu để hệ thống truy vấn trạng thái giao dịch. |

*Bảng 3.2: Chi tiết các tác nhân*

| STT | Tác nhân | Mô tả |
|---:|---|---|
| 1 | Khách chưa đăng nhập | Người truy cập công khai, chưa có session xác thực. Tác nhân này chỉ được xem dữ liệu sự kiện đã công khai và sử dụng các chức năng tài khoản công khai. |
| 2 | Người dùng chưa xác minh email | Người dùng đã có tài khoản và có thể đăng nhập nhưng chưa hoàn tất xác minh email. Tác nhân này chưa được giữ hoặc mua vé. |
| 3 | Người mua đã xác minh email | Người dùng đã xác minh email, có quyền thực hiện toàn bộ quy trình mua vé và quản lý dữ liệu của chính mình. Tài khoản đăng ký mặc định có platformRole CUSTOMER. |
| 4 | Quản lý tổ chức | Người dùng có quyền `MANAGER` hiệu lực trong `User.organizationRoles` đối với Organization. Quyền chỉ có hiệu lực trong tổ chức tương ứng; một người có thể quản lý nhiều tổ chức. |
| 5 | Nhân viên kiểm soát vé | Người dùng có quyền `CHECK_IN_STAFF` hiệu lực trong `User.organizationRoles` đối với Organization. Tác nhân này được kiểm tra vé cho mọi sự kiện thuộc tổ chức nhưng không được quản lý doanh thu hoặc thành viên. |
| 6 | Quản trị nền tảng | Người dùng có quyền nền tảng `ADMIN`. Đây là tác nhân quyết định cuối đối với tổ chức, sự kiện, hoàn vé, hủy sự kiện, hoa hồng, đối soát và chi trả. |
| 7 | VNPAY Sandbox | Hệ thống bên ngoài cung cấp môi trường thanh toán thử nghiệm. TicketsCenter phải xác thực chữ ký và dữ liệu giao dịch trước khi ghi nhận kết quả. |

Quyền của quản lý tổ chức và nhân viên kiểm soát vé luôn được xác định từ tư cách thành viên đang hoạt động và tổ chức sở hữu đối tượng. Việc chọn tổ chức trên giao diện không tự tạo quyền truy cập. Quản trị nền tảng là quyền độc lập với vai trò trong tổ chức và không thể được cấp thông qua chức năng quản lý thành viên.

## 3.2. Danh sách các chức năng và mô tả chung

Danh sách dưới đây là tập chức năng được sử dụng thống nhất cho các lược đồ Use case và phần đặc tả chi tiết. Mỗi chức năng biểu diễn một mục tiêu có ý nghĩa đối với ít nhất một tác nhân; các xử lý nền như dọn lượt giữ hết hạn hoặc gửi lại email không được tách thành Use case của người dùng.

*Bảng 3.3: Mô tả các chức năng*

| STT | Chức năng | Mô tả |
|---:|---|---|
| 1 | Đăng ký tài khoản | Khách nhập userName duy nhất, email và mật khẩu; hệ thống kiểm tra userName/email không trùng và khởi tạo xác minh email. |
| 2 | Xác minh email bằng OTP | Người dùng nhập OTP còn hiệu lực để xác nhận quyền sử dụng email trước khi được giữ vé. |
| 3 | Đăng nhập | Người dùng cung cấp thông tin xác thực để hệ thống tạo session và xác định các quyền phù hợp. |
| 4 | Đặt lại mật khẩu bằng OTP | Người dùng xác minh OTP dành riêng cho mục đích khôi phục, đặt mật khẩu mới và làm mất hiệu lực các phiên cũ. |
| 5 | Cập nhật hồ sơ cá nhân | Người dùng cập nhật họ tên và số điện thoại của tài khoản. |
| 6 | Tìm kiếm và lọc sự kiện | Người dùng tìm theo tên, lọc danh mục hoặc khoảng ngày, sắp xếp và phân trang danh sách sự kiện công khai. |
| 7 | Xem chi tiết sự kiện, giá vé và tình trạng chỗ | Người dùng xem nội dung sự kiện, địa điểm, thời gian, khu vực, giá và khả năng còn vé. |
| 8 | Chọn chỗ và giữ vé | Người mua chọn ghế hoặc số lượng khu đứng của cùng một sự kiện và tạo lượt giữ có thời hạn mười phút. |
| 9 | Hủy lượt giữ vé | Chủ lượt giữ chủ động giải phóng chỗ, quota khu đứng và lượt mã giảm giá đang được giữ. |
| 10 | Tạo đơn mua vé | Người mua tạo tối đa một đơn từ lượt giữ còn hiệu lực; hệ thống chụp thông tin khu, ghế và giá. |
| 11 | Áp dụng và bỏ mã giảm giá | Người mua áp dụng một mã hợp lệ vào đơn hoặc bỏ mã trước khi khởi tạo thanh toán; hệ thống giữ hoặc trả quota mã tương ứng. |
| 12 | Thanh toán qua VNPAY Sandbox | Người mua thanh toán thử nghiệm; hệ thống chỉ ghi nhận kết quả sau khi xác thực IPN hoặc kết quả truy vấn. |
| 13 | Xem đơn mua vé và vé điện tử | Chủ đơn xem thông tin đơn, các lần thanh toán, vé đã phát hành, trạng thái và mã QR. |
| 14 | Chia sẻ mã QR của vé | Người mua cung cấp ảnh QR hoặc liên kết token riêng cho người đi cùng mà không chuyển quyền sở hữu đơn. |
| 15 | Gửi yêu cầu hoàn vé và xem kết quả | Chủ đơn chọn các vé đủ điều kiện, nhập lý do, gửi yêu cầu và theo dõi kết quả xử lý. |
| 16 | Gửi yêu cầu tạo tổ chức và xem kết quả | Người dùng đã đăng nhập gửi thông tin đề nghị tạo tổ chức và xem trạng thái hoặc lý do từ chối. |
| 17 | Duyệt hoặc từ chối yêu cầu tạo tổ chức | Quản trị xét duyệt yêu cầu; khi duyệt, cùng Organization chuyển APPROVED, tạo quy tắc phí ban đầu và cấp quyền MANAGER cho requester trong cùng giao dịch. |
| 18 | Thêm thành viên vào tổ chức | Quản lý thêm trực tiếp một người dùng đã có tài khoản vào tổ chức bằng userName duy nhất. |
| 19 | Thay đổi vai trò và vô hiệu hóa thành viên | Quản lý cập nhật vai trò hoặc trạng thái thành viên nhưng không được loại người quản lý đang hoạt động cuối cùng. |
| 20 | Tạo và chỉnh sửa sự kiện | Quản lý tạo bản nháp, nhập hoặc cập nhật thông tin sự kiện trước khi công khai. |
| 21 | Thiết lập khu ngồi, ghế và khu đứng | Quản lý cấu hình khu ngồi theo hàng–ghế hoặc khu đứng theo sức chứa khi cấu trúc sự kiện còn được phép thay đổi. |
| 22 | Cập nhật giá vé theo khu | Quản lý thay đổi giá niêm yết; lượt giữ hiện hữu tiếp tục dùng giá đã chụp. |
| 23 | Gửi sự kiện để duyệt | Quản lý gửi bản nháp đáp ứng điều kiện dữ liệu và ảnh bìa sang trạng thái chờ quản trị xét duyệt. |
| 24 | Duyệt công khai hoặc từ chối sự kiện | Quản trị công khai sự kiện và khóa cấu trúc chỗ, hoặc từ chối kèm lý do để tổ chức chỉnh sửa và gửi lại. |
| 25 | Quản lý mã giảm giá của tổ chức | Quản lý tổ chức hoặc quản trị tạo và cập nhật mã, thời hạn, giá trị giảm, trạng thái và giới hạn lượt sử dụng trong phạm vi được phép. |
| 26 | Kiểm tra vé vào cửa bằng QR hoặc mã nhập tay | Nhân viên chọn sự kiện, nhập mã bằng camera hoặc bàn phím; hệ thống kiểm tra quyền, trạng thái vé và thời gian check-in. |
| 27 | Xem lịch sử kiểm tra vé | Người có quyền xem các lần kiểm tra và kết quả trong phạm vi tổ chức hoặc toàn hệ thống phù hợp với vai trò. |
| 28 | Duyệt hoặc từ chối yêu cầu hoàn vé | Quản trị xem xét yêu cầu; từ chối kèm lý do hoặc duyệt để chuyển sang xử lý hoàn tiền. |
| 29 | Xử lý hoàn tiền mô phỏng | Quản trị vận hành kết quả hoàn thành công, thất bại hoặc chưa xác định; Refund trực tiếp giữ tiến độ; thành công cập nhật cùng Refund/vé/kho, bù trừ không tác động vé; hoàn 0đ không chuyển tiền. |
| 30 | Hủy sự kiện | Quản trị hủy sự kiện đã công khai trước giờ bắt đầu; hệ thống ngừng bán, giải phóng lượt giữ và khởi tạo hoàn vé tự động. |
| 31 | Thiết lập chính sách hoa hồng và phí | Quản trị tạo quy tắc theo tổ chức với tỷ lệ, phí cố định và thời gian hiệu lực. |
| 32 | Lập và tính lại đối soát sự kiện | Quản trị tạo bản đối soát sau sự kiện, tổng hợp đơn đủ điều kiện và tính lại khi bản đối soát còn ở trạng thái nháp. |
| 33 | Xác nhận đối soát sự kiện | Quản trị đóng băng số liệu khi sự kiện đã kết thúc và không còn thanh toán hoặc hoàn tiền chưa giải quyết. |
| 34 | Ghi nhận chi trả mô phỏng cho tổ chức | Quản trị ghi khoản chi, mã tham chiếu và trạng thái; Settlement giữ tổng đã chi/đang chờ; paidAmount + pendingAmount không vượt netPayable, lịch sử nằm ở SettlementTransferLog kỹ thuật. |
| 35 | Xem và lọc báo cáo | Quản trị xem toàn hệ thống; quản lý xem phạm vi tổ chức với bộ lọc sự kiện và thời gian. |
| 36 | Xuất báo cáo CSV | Người có quyền xuất đúng dữ liệu và bộ lọc của báo cáo đang xem thành tệp CSV hỗ trợ tiếng Việt. |
| 37 | Xem nhật ký thao tác hệ thống | Quản trị tra cứu người thực hiện, hành động, đối tượng, nội dung và thời điểm của các thao tác quan trọng. |

## 3.3. Lược đồ Use case

### 3.3.1. Lược đồ 3.1 - Use case tổng quát hệ thống TicketsCenter

### 3.3.2. Lược đồ 3.2 - Use case quản lý tài khoản và xác minh email

### 3.3.3. Lược đồ 3.3 - Use case đăng ký, xét duyệt và quản lý tổ chức

### 3.3.4. Lược đồ 3.4 - Use case quản lý sự kiện, khu vực và ghế

### 3.3.5. Lược đồ 3.5 - Use case tra cứu sự kiện, giữ vé và tạo đơn hàng

### 3.3.6. Lược đồ 3.6 - Use case quản lý và sử dụng mã giảm giá

### 3.3.7. Lược đồ 3.7 - Use case thanh toán và phát hành vé

### 3.3.8. Lược đồ 3.8 - Use case quản lý vé và kiểm soát vào cửa

### 3.3.9. Lược đồ 3.9 - Use case hoàn vé, hoàn tiền và hủy sự kiện

### 3.3.10. Lược đồ 3.10 - Use case quản lý hoa hồng, đối soát và chi trả

### 3.3.11. Lược đồ 3.11 - Use case báo cáo, xuất dữ liệu và tra cứu nhật ký hoạt động

## 3.4. Đặc tả Use case

### 3.4.1. Đăng ký tài khoản

| Use Case Đăng ký tài khoản | Use Case Đăng ký tài khoản |
|---|---|
| **Mô tả** | Cho phép khách tạo User bằng userName duy nhất, email và mật khẩu, khởi tạo xác minh email trước khi giữ vé. |
| **Tác nhân kích hoạt** | Khách chưa đăng nhập |
| **Tiền điều kiện** | Khách chưa đăng nhập; userName duy nhất và email chuẩn hóa chưa thuộc tài khoản khác. |
| **Các bước thực hiện** | 1. Khách mở đăng ký.<br>2. Khách nhập userName, email và mật khẩu.<br>3. Hệ thống chuẩn hóa email, kiểm tra định dạng và tính duy nhất của userName/email.<br>4. Hệ thống tạo User ACTIVE, platformRole CUSTOMER, organizationRoles rỗng, mật khẩu đã băm và email chưa xác minh.<br>5. Hệ thống tạo OTP sáu chữ số và gửi tới email đăng ký.<br>6. Hệ thống mở bước OTP; dữ liệu không hợp lệ hoặc trùng bị từ chối, không tạo User trùng. |

*Bảng 3.4: Use case Đăng ký tài khoản*

### 3.4.2. Xác minh email bằng OTP

| Use Case Xác minh email bằng OTP | Use Case Xác minh email bằng OTP |
|---|---|
| **Mô tả** | Cho phép người dùng xác minh địa chỉ email bằng OTP trước khi thực hiện thao tác giữ vé. |
| **Tác nhân kích hoạt** | Người dùng chưa xác minh |
| **Tiền điều kiện** | Tài khoản đã được tạo nhưng chưa xác minh email; người dùng có OTP xác minh còn hiệu lực. |
| **Các bước thực hiện** | 1. Người dùng nhập OTP gồm 6 chữ số.<br>2. Hệ thống kiểm tra đúng tài khoản, đúng mục đích xác minh email, thời hạn 5 phút, trạng thái chưa dùng và số lần nhập sai.<br>3. Nếu OTP hợp lệ, hệ thống đánh dấu mã đã dùng và ghi nhận thời điểm xác minh email.<br>4. Hệ thống thông báo xác minh thành công; người dùng có thể sử dụng chức năng dành cho người mua đã xác minh.<br>5. Nếu OTP sai, hệ thống tăng số lần sai; đủ 5 lần thì vô hiệu hóa mã.<br>6. Người dùng chỉ được yêu cầu gửi lại sau ít nhất 60 giây; OTP mới làm mất hiệu lực OTP cũ cùng mục đích. |

*Bảng 3.5: Use case Xác minh email bằng OTP*

### 3.4.3. Đăng nhập

| Use Case Đăng nhập | Use Case Đăng nhập |
|---|---|
| **Mô tả** | Cho phép người dùng truy cập hệ thống bằng tài khoản hợp lệ và thiết lập phiên đăng nhập phía máy chủ. |
| **Tác nhân kích hoạt** | Người dùng; Quản trị nền tảng |
| **Tiền điều kiện** | Tài khoản tồn tại, đang hoạt động và tác nhân có thông tin đăng nhập. |
| **Các bước thực hiện** | 1. Người dùng nhập email và mật khẩu; local/demo, quản trị dùng userName `admin` của tài khoản khởi tạo mặc định.<br>2. Hệ thống xác định User và kiểm tra hash mật khẩu cùng trạng thái tài khoản.<br>3. Hợp lệ thì tái tạo session ID và cookie phiên có thuộc tính bảo vệ.<br>4. Hệ thống lấy platformRole và organizationRoles đang hiệu lực để hiển thị khu vực được phép; mỗi thao tác vẫn kiểm tra quyền theo đối tượng.<br>5. Sai thông tin hoặc User DISABLED thì từ chối với thông báo phù hợp. Không có ca tạo/cấp thêm ADMIN qua UI. |

*Bảng 3.6: Use case Đăng nhập*

### 3.4.4. Đặt lại mật khẩu bằng OTP

| Use Case Đặt lại mật khẩu bằng OTP | Use Case Đặt lại mật khẩu bằng OTP |
|---|---|
| **Mô tả** | Cho phép chủ tài khoản đặt mật khẩu mới sau khi xác minh OTP dành riêng cho mục đích đặt lại mật khẩu. |
| **Tác nhân kích hoạt** | Người dùng |
| **Tiền điều kiện** | Tài khoản tồn tại; OTP đặt lại mật khẩu còn hiệu lực và chưa được sử dụng. |
| **Các bước thực hiện** | 1. Người dùng yêu cầu đặt lại mật khẩu bằng email.<br>2. Hệ thống tạo và gửi OTP có mục đích đặt lại mật khẩu; không sử dụng OTP xác minh tài khoản cho luồng này.<br>3. Người dùng nhập OTP; hệ thống kiểm tra thời hạn 5 phút, trạng thái dùng và giới hạn 5 lần nhập sai.<br>4. Khi OTP hợp lệ, hệ thống cấp quyền đổi mật khẩu một lần có thời hạn.<br>5. Người dùng nhập mật khẩu mới.<br>6. Hệ thống cập nhật mã băm mật khẩu và tăng `authVersion` trong cùng giao dịch.<br>7. Hệ thống vô hiệu hóa toàn bộ phiên đăng nhập cũ và thông báo hoàn tất. |

*Bảng 3.7: Use case Đặt lại mật khẩu bằng OTP*

### 3.4.5. Cập nhật hồ sơ cá nhân

| Use Case Cập nhật hồ sơ cá nhân | Use Case Cập nhật hồ sơ cá nhân |
|---|---|
| **Mô tả** | Cho phép người dùng cập nhật họ tên và số điện thoại trong hồ sơ cá nhân. |
| **Tác nhân kích hoạt** | Người dùng đã đăng nhập |
| **Tiền điều kiện** | Người dùng có phiên đăng nhập hợp lệ và tài khoản đang hoạt động. |
| **Các bước thực hiện** | 1. Người dùng mở hồ sơ cá nhân.<br>2. Hệ thống hiển thị thông tin hồ sơ hiện tại.<br>3. Người dùng sửa họ tên hoặc số điện thoại và gửi yêu cầu.<br>4. Hệ thống kiểm tra dữ liệu đầu vào.<br>5. Hệ thống gọi hành vi cập nhật hồ sơ của người dùng, lưu thay đổi và hiển thị kết quả. Email đăng nhập không được thay đổi trong chức năng này. |

*Bảng 3.8: Use case Cập nhật hồ sơ cá nhân*

### 3.4.6. Tìm kiếm và lọc sự kiện

| Use Case Tìm kiếm và lọc sự kiện | Use Case Tìm kiếm và lọc sự kiện |
|---|---|
| **Mô tả** | Cho phép người dùng tra cứu danh sách sự kiện công khai theo tên, danh mục, khoảng ngày và thứ tự mong muốn. |
| **Tác nhân kích hoạt** | Khách chưa đăng nhập; Người dùng |
| **Tiền điều kiện** | Không yêu cầu đăng nhập. |
| **Các bước thực hiện** | 1. Tác nhân mở danh sách sự kiện.<br>2. Tác nhân nhập tên cần tìm và chọn danh mục hoặc khoảng ngày nếu cần.<br>3. Tác nhân chọn sắp xếp theo thời gian bắt đầu hoặc giá khu thấp nhất.<br>4. Hệ thống chỉ truy vấn các sự kiện đã công khai, loại sự kiện đã hủy khỏi danh sách có thể mua và áp dụng bộ lọc.<br>5. Hệ thống trả kết quả theo từng trang.<br>6. Tác nhân có thể chọn một kết quả để xem chi tiết. |

*Bảng 3.9: Use case Tìm kiếm và lọc sự kiện*

### 3.4.7. Xem chi tiết sự kiện, giá vé và tình trạng chỗ

| Use Case Xem chi tiết sự kiện, giá vé và tình trạng chỗ | Use Case Xem chi tiết sự kiện, giá vé và tình trạng chỗ |
|---|---|
| **Mô tả** | Cho phép người dùng xem thông tin của sự kiện công khai, các khu vé, giá niêm yết và tình trạng chỗ hiện tại. |
| **Tác nhân kích hoạt** | Khách chưa đăng nhập; Người dùng |
| **Tiền điều kiện** | Sự kiện tồn tại và đã được công khai. |
| **Các bước thực hiện** | 1. Tác nhân chọn một sự kiện từ danh sách hoặc đường dẫn hợp lệ.<br>2. Hệ thống kiểm tra trạng thái công khai của sự kiện.<br>3. Hệ thống hiển thị tên, mô tả, danh mục, ảnh bìa, địa điểm, thời gian mở bán và thời gian diễn ra.<br>4. Hệ thống hiển thị các khu, giá hiện tại, sơ đồ ghế đối với khu ngồi hoặc số lượng còn lại đối với khu đứng.<br>5. Nếu sự kiện đã hủy hoặc không còn đủ điều kiện mua, hệ thống không cho bắt đầu lượt giữ mới. |

*Bảng 3.10: Use case Xem chi tiết sự kiện, giá vé và tình trạng chỗ*

### 3.4.8. Chọn chỗ và giữ vé

| Use Case Chọn chỗ và giữ vé | Use Case Chọn chỗ và giữ vé |
|---|---|
| **Mô tả** | Cho phép người mua đã xác minh giữ toàn bộ ghế hoặc số lượng vé đứng đã chọn trong 10 phút. |
| **Tác nhân kích hoạt** | Người mua đã xác minh |
| **Tiền điều kiện** | Người mua đã đăng nhập và xác minh email; sự kiện đã công khai, đang trong thời gian mở bán; người mua không có lượt giữ `ACTIVE` khác. |
| **Các bước thực hiện** | 1. Người mua chọn ghế ở khu ngồi hoặc số lượng tại khu đứng của cùng một sự kiện.<br>2. Hệ thống kiểm tra tổng số lượng không vượt 8 vé; ghế thuộc đúng khu, mỗi ghế có số lượng 1; mục khu đứng không gắn ghế và có số lượng dương.<br>3. Hệ thống khóa và kiểm tra đồng thời tình trạng ghế hoặc sức chứa khu đứng.<br>4. Nếu toàn bộ lựa chọn còn khả dụng, hệ thống tạo lượt giữ `ACTIVE` cùng các mục giữ trong một giao dịch.<br>5. Hệ thống chụp giá hiện tại vào từng mục và đặt thời điểm hết hạn sau 10 phút.<br>6. Hệ thống hiển thị lượt giữ và đồng hồ đếm ngược; thời gian máy chủ quyết định hiệu lực.<br>7. Nếu bất kỳ lựa chọn nào không còn khả dụng, hệ thống không giữ một phần và yêu cầu người mua chọn lại. |

*Bảng 3.11: Use case Chọn chỗ và giữ vé*

### 3.4.9. Hủy lượt giữ vé

| Use Case Hủy lượt giữ vé | Use Case Hủy lượt giữ vé |
|---|---|
| **Mô tả** | Cho phép chủ lượt giữ chủ động hủy lượt giữ chưa thanh toán để trả lại chỗ và quota liên quan. |
| **Tác nhân kích hoạt** | Người mua đã xác minh |
| **Tiền điều kiện** | Người mua là chủ của lượt giữ; lượt giữ đang `ACTIVE` và chưa được dùng để hoàn tất thanh toán. |
| **Các bước thực hiện** | 1. Người mua mở lượt giữ hiện tại và chọn hủy.<br>2. Hệ thống kiểm tra quyền sở hữu, trạng thái và thời hạn lượt giữ.<br>3. Trong một giao dịch, hệ thống chuyển lượt giữ sang trạng thái đã giải phóng, trả ghế hoặc số lượng khu đứng và trả lượt coupon đang được giữ nếu có.<br>4. Nếu đã có đơn chưa thanh toán từ lượt giữ, hệ thống cập nhật trạng thái đơn tương ứng.<br>5. Hệ thống thông báo hủy thành công. Lượt giữ hết hạn được xử lý theo cùng nguyên tắc giải phóng tài nguyên. |

*Bảng 3.12: Use case Hủy lượt giữ vé*

### 3.4.10. Tạo đơn mua vé

| Use Case Tạo đơn mua vé | Use Case Tạo đơn mua vé |
|---|---|
| **Mô tả** | Cho phép người mua tạo duy nhất một đơn hàng từ lượt giữ còn hiệu lực. |
| **Tác nhân kích hoạt** | Người mua đã xác minh |
| **Tiền điều kiện** | Người mua là chủ lượt giữ; lượt giữ đang `ACTIVE`, chưa hết hạn và chưa tạo đơn trước đó. |
| **Các bước thực hiện** | 1. Người mua chọn tiếp tục đặt vé từ lượt giữ hiện tại.<br>2. Hệ thống kiểm tra chủ sở hữu, trạng thái sự kiện, thời hạn lượt giữ và ràng buộc mỗi lượt giữ chỉ tạo một đơn.<br>3. Hệ thống tạo Order và chuyển từng TicketHoldItem thành OrderItem trong cùng giao dịch.<br>4. Hệ thống lưu snapshot khu, nhãn ghế, số lượng và đơn giá từ lượt giữ.<br>5. Hệ thống tính `subtotalAmount` ở máy chủ và hiển thị đơn chờ thanh toán.<br>6. Việc tạo đơn không gia hạn thời gian 10 phút của lượt giữ. |

*Bảng 3.13: Use case Tạo đơn mua vé*

### 3.4.11. Áp dụng và bỏ mã giảm giá

| Use Case Áp dụng và bỏ mã giảm giá | Use Case Áp dụng và bỏ mã giảm giá |
|---|---|
| **Mô tả** | Cho phép người mua áp dụng tối đa một mã giảm giá hợp lệ hoặc bỏ mã khỏi đơn chưa thanh toán. |
| **Tác nhân kích hoạt** | Người mua đã xác minh |
| **Tiền điều kiện** | Người mua là chủ đơn; đơn chưa thanh toán, lượt giữ còn hiệu lực và không có Payment đang xử lý hoặc chưa rõ kết quả. |
| **Các bước thực hiện** | 1. Người mua nhập mã giảm giá hoặc chọn bỏ mã đang áp dụng.<br>2. Khi áp dụng, hệ thống kiểm tra mã đang kích hoạt, còn thời hạn, thuộc cùng tổ chức với sự kiện và chưa vượt `maxUses`.<br>3. Hệ thống kiểm tra mỗi đơn chỉ có tối đa một mã và khóa Coupon để bảo đảm tổng lượt `RESERVED + CONSUMED` không vượt giới hạn.<br>4. Hệ thống giữ một lượt dùng cho đơn và tính mức giảm: phần trăm không vượt 30%; số tiền cố định được giới hạn ở 30% `subtotalAmount`.<br>5. Hệ thống cập nhật tổng tiền và hiển thị kết quả.<br>6. Khi người mua bỏ mã trước lúc khởi tạo thanh toán, hệ thống trả lượt đang giữ và tính lại tổng tiền.<br>7. Hệ thống từ chối thay đổi mã nếu Payment đang xử lý hoặc chưa rõ kết quả. |

*Bảng 3.14: Use case Áp dụng và bỏ mã giảm giá*

### 3.4.12. Thanh toán qua VNPAY Sandbox

| Use Case Thanh toán qua VNPAY Sandbox | Use Case Thanh toán qua VNPAY Sandbox |
|---|---|
| **Mô tả** | Cho phép người mua thanh toán đơn qua VNPAY Sandbox và phát hành vé sau khi kết quả thanh toán được xác thực. |
| **Tác nhân kích hoạt** | Người mua đã xác minh; VNPAY Sandbox |
| **Tiền điều kiện** | Người mua là chủ đơn; đơn đang chờ thanh toán; sự kiện còn hợp lệ để bán; lượt giữ còn hiệu lực. |
| **Các bước thực hiện** | 1. Người mua chọn thanh toán.<br>2. Hệ thống kiểm tra chủ đơn, Event PUBLISHED, saleStart <= now < saleEnd, Hold còn hạn, coupon và tổng tiền máy chủ.<br>3. Với totalAmount > 0, gọi Payment.start(order, txnRef), lưu Payment.order bắt buộc và amount snapshot bất biến, commit rồi chuyển tới VNPAY.<br>4. Return URL chỉ hiển thị; IPN/query xác thực chữ ký, merchant, tham chiếu, amount, loại tiền và trạng thái mới được ghi nhận.<br>5. Nếu còn đủ điều kiện bán/giữ, trong một giao dịch ghi Payment CAPTURED/paidAt, gọi Order.markPaid(payment, now), đặt acceptedPayment của chính Order và Order.paidAt, consume TicketHold, bán kho, dùng coupon và phát hành Ticket.<br>6. IPN lặp trả kết quả cũ, không đổi acceptedPayment hoặc phát hành thêm vé.<br>7. Thu khi Hold hết hạn/giải phóng, thu trùng, Event hủy hoặc qua saleEnd: ghi CAPTURED, tạo đúng một Refund PAYMENT_COMPENSATION/APPROVED gắn Payment, không có Ticket và không phát hành vé mới.<br>8. UNKNOWN phải truy vấn, không đoán thất bại để thu lại.<br>9. Đơn 0đ vẫn kiểm tra Event/Hold/cửa sổ bán, markPaid(null, now), ghi paidAt và phát hành vé; không có Payment giả, acceptedPayment trống.<br>10. Email sau commit; lỗi gửi không đảo ngược trạng thái. |

*Bảng 3.15: Use case Thanh toán qua VNPAY Sandbox*

### 3.4.13. Xem đơn mua vé và vé điện tử

| Use Case Xem đơn mua vé và vé điện tử | Use Case Xem đơn mua vé và vé điện tử |
|---|---|
| **Mô tả** | Cho phép người mua xem các đơn thuộc tài khoản mình và các vé điện tử đã được phát hành. |
| **Tác nhân kích hoạt** | Người mua đã xác minh |
| **Tiền điều kiện** | Người mua đã đăng nhập; đơn hoặc vé cần xem thuộc chính người mua. |
| **Các bước thực hiện** | 1. Người mua mở danh sách đơn hoặc “Vé của tôi”.<br>2. Hệ thống chỉ truy vấn dữ liệu thuộc tài khoản hiện tại.<br>3. Hệ thống hiển thị thông tin đơn, các mục vé, số tiền, trạng thái thanh toán và trạng thái hoàn nếu có.<br>4. Với vé đã phát hành, hệ thống hiển thị sự kiện, khu hoặc ghế, số tiền thực trả, trạng thái vé và mã QR.<br>5. Người mua có thể mở chi tiết từng vé hoặc chuyển sang chức năng chia sẻ QR.<br>6. Nếu sửa mã định danh để truy cập dữ liệu của người khác, hệ thống từ chối. |

*Bảng 3.16: Use case Xem đơn mua vé và vé điện tử*

### 3.4.14. Chia sẻ mã QR của vé

| Use Case Chia sẻ mã QR của vé | Use Case Chia sẻ mã QR của vé |
|---|---|
| **Mô tả** | Cho phép người mua cung cấp mã QR của một vé cho người đi cùng mà không chuyển quyền sở hữu đơn. |
| **Tác nhân kích hoạt** | Người mua đã xác minh |
| **Tiền điều kiện** | Người mua là chủ đơn; vé đã được phát hành và tồn tại trong “Vé của tôi”. |
| **Các bước thực hiện** | 1. Người mua mở một vé và chọn chia sẻ.<br>2. Hệ thống kiểm tra vé thuộc đơn của người mua.<br>3. Hệ thống cung cấp ảnh QR hoặc liên kết có token riêng cho đúng vé được chọn.<br>4. Người mua gửi nội dung đó cho người đi cùng.<br>5. Người cầm vé có thể xuất trình QR khi vào cửa mà không cần tài khoản.<br>6. Việc chia sẻ không chuyển quyền quản lý đơn hoặc quyền yêu cầu hoàn và không làm lộ các vé khác trong đơn. |

*Bảng 3.17: Use case Chia sẻ mã QR của vé*

### 3.4.15. Gửi yêu cầu hoàn vé và xem kết quả

| Use Case Gửi yêu cầu hoàn vé và xem kết quả | Use Case Gửi yêu cầu hoàn vé và xem kết quả |
|---|---|
| **Mô tả** | Cho phép chủ đơn chọn các vé đủ điều kiện để yêu cầu hoàn và theo dõi trạng thái xử lý. |
| **Tác nhân kích hoạt** | Người mua đã xác minh |
| **Tiền điều kiện** | Người mua là chủ Order PAID; vé được chọn ACTIVE, cùng Order, trước startTime và chưa có nghĩa vụ Refund đang mở. |
| **Các bước thực hiện** | 1. Chủ đơn chọn vé và nhập lý do.<br>2. Hệ thống kiểm tra chủ sở hữu, cùng Order, trước startTime, vé ACTIVE và nghĩa vụ đang mở.<br>3. Amount = tổng Ticket.paidAmount đã lưu.<br>4. Trong một giao dịch gọi Refund.requestForTickets với purpose CUSTOMER_REFUND, reasonType CUSTOMER_REQUEST; cùng Refund ở REQUESTED gắn Order/tập Ticket và payment = acceptedPayment hoặc null nếu 0đ, vé chuyển REFUND_PENDING.<br>5. Hệ thống hiển thị Refund chờ duyệt.<br>6. Khách theo dõi REQUESTED, APPROVED, PROCESSING, NEEDS_RECONCILIATION, RETRYABLE, REJECTED hoặc COMPLETED cùng lý do quyết định; hoàn 0đ vẫn lưu nghĩa vụ, không tạo chuyển tiền. |

*Bảng 3.18: Use case Gửi yêu cầu hoàn vé và xem kết quả*

### 3.4.16. Gửi yêu cầu tạo tổ chức và xem kết quả

| Use Case Gửi yêu cầu tạo tổ chức và xem kết quả | Use Case Gửi yêu cầu tạo tổ chức và xem kết quả |
|---|---|
| **Mô tả** | Cho phép người mua đã xác minh đề nghị tạo một tổ chức và theo dõi kết quả xét duyệt. |
| **Tác nhân kích hoạt** | Người mua đã xác minh |
| **Tiền điều kiện** | Người dùng đã đăng nhập, tài khoản đang hoạt động và email đã được xác minh. |
| **Các bước thực hiện** | 1. Người mua mở đăng ký tổ chức.<br>2. Nhập tên, email liên hệ, điện thoại và mô tả.<br>3. Hệ thống tạo Organization DRAFT với requester từ session, gọi submitForApproval để chuyển cùng hồ sơ PENDING_APPROVAL.<br>4. Hệ thống thông báo tiếp nhận; tổ chức chưa được hoạt động và requester chưa có MANAGER.<br>5. Người gửi xem PENDING_APPROVAL, APPROVED hoặc REJECTED của cùng Organization.<br>6. REJECTED hiển thị rejectionReason; APPROVED hiển thị chính tổ chức và quyền MANAGER đã cấp. Chưa có ca gửi lại hồ sơ tổ chức bị từ chối. |

*Bảng 3.19: Use case Gửi yêu cầu tạo tổ chức và xem kết quả*

### 3.4.17. Duyệt hoặc từ chối yêu cầu tạo tổ chức

| Use Case Duyệt hoặc từ chối yêu cầu tạo tổ chức | Use Case Duyệt hoặc từ chối yêu cầu tạo tổ chức |
|---|---|
| **Mô tả** | Cho phép quản trị xét duyệt yêu cầu tạo tổ chức và thiết lập dữ liệu ban đầu khi phê duyệt. |
| **Tác nhân kích hoạt** | Quản trị nền tảng |
| **Tiền điều kiện** | Quản trị đã đăng nhập; Organization tồn tại ở PENDING_APPROVAL. |
| **Các bước thực hiện** | 1. Quản trị mở Organization chờ duyệt và xem requester/nội dung.<br>2. Chọn duyệt hoặc từ chối.<br>3. Từ chối bắt buộc lý do, gọi Organization.reject(reason), lưu REJECTED/rejectionReason trên cùng hồ sơ.<br>4. Duyệt trong một giao dịch gọi Organization.approve() để chuyển cùng hồ sơ APPROVED, tạo CommissionRule ban đầu và gọi User.assignRole(organization, MANAGER) cho requester; không tạo Organization thứ hai.<br>5. Lưu người duyệt, thời điểm quyết định và AuditLog.<br>6. Xử lý lặp trả kết quả cũ, không tạo tổ chức, quy tắc hoặc quyền trùng; quyết định trái kết quả cuối bị từ chối. |

*Bảng 3.20: Use case Duyệt hoặc từ chối yêu cầu tạo tổ chức*

### 3.4.18. Thêm thành viên vào tổ chức

| Use Case Thêm thành viên vào tổ chức | Use Case Thêm thành viên vào tổ chức |
|---|---|
| **Mô tả** | Cho phép quản lý thêm một người dùng đã tồn tại vào tổ chức với vai trò thuộc tổ chức. |
| **Tác nhân kích hoạt** | Quản lý tổ chức |
| **Tiền điều kiện** | Quản lý có quyền `MANAGER` hiệu lực trong `User.organizationRoles` tại tổ chức; người cần thêm có tài khoản trong hệ thống. |
| **Các bước thực hiện** | 1. Quản lý chọn tổ chức và mở thành viên.<br>2. Nhập userName duy nhất của tài khoản đã tồn tại, chọn MANAGER hoặc CHECK_IN_STAFF.<br>3. Hệ thống kiểm tra quyền MANAGER của actor, tìm User theo userName và kiểm tra cặp User–Organization.<br>4. Gọi User.assignRole(org, role) trên User mục tiêu để cập nhật organizationRoles; UserOrganizationRole kỹ thuật lưu liên kết/lịch sử nếu cần.<br>5. Hiển thị quyền hiệu lực; liên kết bị vô hiệu có thể được kích hoạt lại trên cùng định danh.<br>6. User không tồn tại hoặc quyền đã hoạt động thì từ chối thêm trùng; không gửi lời mời, không dùng email để thêm thành viên. |

*Bảng 3.21: Use case Thêm thành viên vào tổ chức*

### 3.4.19. Thay đổi vai trò và vô hiệu hóa thành viên

| Use Case Thay đổi vai trò và vô hiệu hóa thành viên | Use Case Thay đổi vai trò và vô hiệu hóa thành viên |
|---|---|
| **Mô tả** | Cho phép quản lý đổi vai trò hoặc thu hồi quyền hiệu lực của User trong Organization; không vô hiệu hóa toàn bộ tài khoản. |
| **Tác nhân kích hoạt** | Quản lý tổ chức |
| **Tiền điều kiện** | Quản lý có quyền `MANAGER` hiệu lực trong `User.organizationRoles`; thành viên mục tiêu thuộc cùng tổ chức. |
| **Các bước thực hiện** | 1. Quản lý chọn thành viên của tổ chức.<br>2. Chọn vai trò mới hoặc thu hồi quyền.<br>3. Hệ thống kiểm tra actor và Organization mục tiêu.<br>4. Không được hạ quyền/thu hồi Manager đang hoạt động cuối cùng.<br>5. Chỉ cho vai trò tổ chức, không cấp ADMIN.<br>6. Hợp lệ thì assignRole(org, role) hoặc revokeRole(org); thu hồi bỏ mục khỏi organizationRoles, lưu trạng thái/lịch sử UserOrganizationRole kỹ thuật và audit. User vẫn hoạt động; vi phạm thì giữ dữ liệu và báo lý do. |

*Bảng 3.22: Use case Thay đổi vai trò và vô hiệu hóa thành viên*

### 3.4.20. Tạo và chỉnh sửa sự kiện

| Use Case Tạo và chỉnh sửa sự kiện | Use Case Tạo và chỉnh sửa sự kiện |
|---|---|
| **Mô tả** | Cho phép quản lý tạo bản nháp sự kiện và chỉnh sửa thông tin trước khi sự kiện được công khai. |
| **Tác nhân kích hoạt** | Quản lý tổ chức |
| **Tiền điều kiện** | Quản lý có quyền `MANAGER` hiệu lực trong `User.organizationRoles` tại tổ chức; khi chỉnh sửa, sự kiện thuộc tổ chức và đang ở trạng thái cho phép sửa. |
| **Các bước thực hiện** | 1. Quản lý chọn tạo sự kiện hoặc mở sự kiện `DRAFT` hay `REJECTED` để chỉnh sửa.<br>2. Quản lý nhập tên, mô tả, danh mục, ảnh bìa, địa điểm, thời gian mở bán và thời gian diễn ra.<br>3. Hệ thống kiểm tra sự kiện thuộc đúng tổ chức, dữ liệu bắt buộc và bất biến `saleStart < saleEnd <= startTime < endTime`.<br>4. Hệ thống tạo Event ở trạng thái `DRAFT` hoặc lưu nội dung đã chỉnh sửa.<br>5. Nếu dữ liệu không hợp lệ hoặc người dùng không có quyền với tổ chức, hệ thống từ chối lưu và hiển thị lỗi.<br>6. Sự kiện bị từ chối có thể được sửa để gửi duyệt lại. |

*Bảng 3.23: Use case Tạo và chỉnh sửa sự kiện*

### 3.4.21. Thiết lập khu ngồi, ghế và khu đứng

| Use Case Thiết lập khu ngồi, ghế và khu đứng | Use Case Thiết lập khu ngồi, ghế và khu đứng |
|---|---|
| **Mô tả** | Cho phép quản lý cấu hình các khu bán vé, ghế của khu ngồi và sức chứa của khu đứng cho sự kiện. |
| **Tác nhân kích hoạt** | Quản lý tổ chức |
| **Tiền điều kiện** | Quản lý có quyền MANAGER hiệu lực tại tổ chức sở hữu sự kiện; Event chỉ ở DRAFT hoặc REJECTED để thay đổi cấu trúc. |
| **Các bước thực hiện** | 1. Quản lý mở phần cấu hình khu của sự kiện.<br>2. Quản lý nhập tên khu, loại khu và giá không âm.<br>3. Với khu ngồi, quản lý nhập số hàng và số ghế mỗi hàng; hệ thống sinh hàng A, B,… cùng số ghế và bảo đảm nhãn ghế duy nhất trong khu.<br>4. Với khu đứng, quản lý nhập sức chứa dương; hệ thống không tạo Seat và dùng sức chứa để theo dõi số lượng giữ, bán.<br>5. Hệ thống kiểm tra mỗi khu thuộc đúng sự kiện và lưu cấu trúc hợp lệ.<br>6. Sau khi sự kiện được công khai, hệ thống không cho đổi loại khu, thêm hoặc xóa ghế, đổi số hàng hay sức chứa. |

*Bảng 3.24: Use case Thiết lập khu ngồi, ghế và khu đứng*

### 3.4.22. Cập nhật giá vé theo khu

| Use Case Cập nhật giá vé theo khu | Use Case Cập nhật giá vé theo khu |
|---|---|
| **Mô tả** | Cho phép quản lý thay đổi giá niêm yết của một khu thuộc sự kiện của tổ chức mình. |
| **Tác nhân kích hoạt** | Quản lý tổ chức |
| **Tiền điều kiện** | Quản lý có quyền MANAGER hiệu lực tại tổ chức sở hữu sự kiện; Zone thuộc Event đang DRAFT, REJECTED hoặc PUBLISHED. |
| **Các bước thực hiện** | 1. Quản lý chọn khu cần thay đổi giá.<br>2. Quản lý nhập giá mới.<br>3. Hệ thống kiểm tra quyền, quan hệ khu–sự kiện và giá không âm.<br>4. Hệ thống cập nhật `Zone.price` và hiển thị giá niêm yết mới.<br>5. Nếu sự kiện đã công khai, hệ thống chỉ cho thay đổi giá, không thay đổi cấu trúc khu hoặc ghế.<br>6. Các lượt giữ đã tạo tiếp tục dùng giá snapshot; chỉ lượt giữ mới nhận giá mới. |

*Bảng 3.25: Use case Cập nhật giá vé theo khu*

### 3.4.23. Gửi sự kiện để duyệt

| Use Case Gửi sự kiện để duyệt | Use Case Gửi sự kiện để duyệt |
|---|---|
| **Mô tả** | Cho phép quản lý gửi bản nháp hoặc sự kiện đã bị từ chối đến quản trị để xét duyệt công khai. |
| **Tác nhân kích hoạt** | Quản lý tổ chức |
| **Tiền điều kiện** | Quản lý có quyền tại tổ chức sở hữu sự kiện; sự kiện ở trạng thái `DRAFT` hoặc `REJECTED`. |
| **Các bước thực hiện** | 1. Quản lý mở sự kiện và chọn gửi duyệt.<br>2. Hệ thống kiểm tra tên, mô tả, danh mục, ảnh bìa, địa điểm, các mốc thời gian và ít nhất một khu vé hợp lệ.<br>3. Hệ thống kiểm tra bất biến `saleStart < saleEnd <= startTime < endTime`.<br>4. Nếu dữ liệu đầy đủ, hệ thống chuyển sự kiện sang `PENDING_APPROVAL`.<br>5. Hệ thống thông báo đã gửi duyệt và chờ quyết định của quản trị.<br>6. Nếu còn thiếu hoặc sai dữ liệu, hệ thống giữ nguyên trạng thái và chỉ rõ nội dung cần sửa. |

*Bảng 3.26: Use case Gửi sự kiện để duyệt*

### 3.4.24. Duyệt công khai hoặc từ chối sự kiện

| Use Case Duyệt công khai hoặc từ chối sự kiện | Use Case Duyệt công khai hoặc từ chối sự kiện |
|---|---|
| **Mô tả** | Cho phép quản trị quyết định công khai sự kiện hoặc từ chối để tổ chức chỉnh sửa và gửi lại. |
| **Tác nhân kích hoạt** | Quản trị nền tảng |
| **Tiền điều kiện** | Quản trị đã đăng nhập; sự kiện đang ở trạng thái `PENDING_APPROVAL`. |
| **Các bước thực hiện** | 1. Quản trị mở sự kiện chờ duyệt và xem thông tin, ảnh bìa, khu vé, giá cùng các mốc thời gian.<br>2. Quản trị chọn từ chối hoặc công khai.<br>3. Nếu từ chối, quản trị nhập lý do; hệ thống chuyển sự kiện sang `REJECTED` để tổ chức có thể sửa và gửi lại.<br>4. Nếu công khai, hệ thống chọn CommissionRule của đúng tổ chức đang có hiệu lực, gắn cố định quy tắc đó với sự kiện và chuyển sự kiện sang `PUBLISHED`.<br>5. Từ thời điểm công khai, hệ thống khóa cấu trúc khu và ghế; chỉ cho phép thay đổi giá niêm yết theo quy tắc đã xác định.<br>6. Hệ thống ghi nhật ký quyết định. Chỉ sự kiện đã công khai và trong thời gian mở bán mới được giữ hoặc mua vé. |

*Bảng 3.27: Use case Duyệt công khai hoặc từ chối sự kiện*

### 3.4.25. Quản lý mã giảm giá của tổ chức

| Use Case Quản lý mã giảm giá của tổ chức | Use Case Quản lý mã giảm giá của tổ chức |
|---|---|
| **Mô tả** | Cho phép người có quyền tạo và cập nhật mã giảm giá thuộc một tổ chức. |
| **Tác nhân kích hoạt** | Quản lý tổ chức; Quản trị nền tảng |
| **Tiền điều kiện** | Quản lý có quyền `MANAGER` hiệu lực trong `User.organizationRoles` tại tổ chức cần quản lý, hoặc tác nhân là quản trị nền tảng. |
| **Các bước thực hiện** | 1. Tác nhân chọn tổ chức và mở danh sách mã giảm giá.<br>2. Tác nhân tạo mới hoặc chọn mã hiện có để cập nhật trạng thái, thời gian hiệu lực và `maxUses`.<br>3. Hệ thống kiểm tra mã thuộc đúng tổ chức; quản lý không được thao tác mã của tổ chức khác, còn quản trị có phạm vi toàn hệ thống.<br>4. Với mã phần trăm, hệ thống yêu cầu giá trị lớn hơn 0 và không vượt 30%. Với mã số tiền cố định, hệ thống yêu cầu giá trị dương; mức giảm thực tế khi áp dụng vẫn bị giới hạn ở 30% đơn.<br>5. Hệ thống kiểm tra `maxUses` là số nguyên dương và không thấp hơn tổng lượt đang giữ cùng đã dùng.<br>6. Hệ thống lưu mã. Thay đổi chỉ áp dụng cho lượt mới, không thay đổi hồi tố coupon đã được giữ hợp lệ cho đơn. |

*Bảng 3.28: Use case Quản lý mã giảm giá của tổ chức*

### 3.4.26. Kiểm tra vé vào cửa bằng QR hoặc mã nhập tay

| Use Case Kiểm tra vé vào cửa bằng QR hoặc mã nhập tay | Use Case Kiểm tra vé vào cửa bằng QR hoặc mã nhập tay |
|---|---|
| **Mô tả** | Cho phép nhân sự của tổ chức kiểm tra vé tại cửa bằng camera hoặc nhập mã thủ công theo cùng một quy trình nghiệp vụ. |
| **Tác nhân kích hoạt** | Quản lý tổ chức; Nhân viên kiểm soát vé |
| **Tiền điều kiện** | Tác nhân có quyền tổ chức hiệu lực trong `User.organizationRoles` đối với tổ chức; đã chọn một sự kiện thuộc tổ chức; thời điểm hiện tại nằm từ 60 phút trước giờ bắt đầu đến trước giờ kết thúc; sự kiện chưa bị hủy. |
| **Các bước thực hiện** | 1. Tác nhân chọn Event thuộc tổ chức.<br>2. Quét QR hoặc nhập mã thủ công qua cùng logic.<br>3. Hệ thống kiểm tra organizationRoles, tổ chức sở hữu, Event chưa hủy và giờ từ startTime - 60 phút đến trước endTime; thiếu quyền bị từ chối trước check-in và ghi audit phù hợp.<br>4. Tìm vé đúng Event, trạng thái ACTIVE; vé chờ hoàn/đã hoàn/vô hiệu/đã dùng bị từ chối.<br>5. Vé hợp lệ gọi Ticket.checkIn(event, now), chuyển USED và ghi TicketCheckInLog kỹ thuật thành công trong cùng giao dịch.<br>6. Mã không hợp lệ, sai Event, đã dùng, ngoài giờ hoặc vé không hoạt động ghi nhật ký kết quả phù hợp.<br>7. Hai yêu cầu đồng thời chỉ một chuyển USED thành công, lần còn lại nhận kết quả đã dùng. |

*Bảng 3.29: Use case Kiểm tra vé vào cửa bằng QR hoặc mã nhập tay*

### 3.4.27. Xem lịch sử kiểm tra vé

| Use Case Xem lịch sử kiểm tra vé | Use Case Xem lịch sử kiểm tra vé |
|---|---|
| **Mô tả** | Cho phép nhân sự được phân quyền xem các lần kiểm tra vé và kết quả trong phạm vi tổ chức. |
| **Tác nhân kích hoạt** | Quản lý tổ chức; Nhân viên kiểm soát vé |
| **Tiền điều kiện** | Tác nhân có quyền tổ chức hiệu lực trong `User.organizationRoles` tại tổ chức sở hữu sự kiện. |
| **Các bước thực hiện** | 1. Tác nhân chọn Organization/Event được phép.<br>2. Chọn bộ lọc thời gian hoặc kết quả.<br>3. Hệ thống kiểm tra organizationRoles hiệu lực và tổ chức sở hữu.<br>4. Truy vấn TicketCheckInLog kỹ thuật, hiển thị thời điểm, phương thức, actor, kết quả và lý do, không đưa nguyên mã QR/token vào log.<br>5. Không hiển thị lịch sử tổ chức khác hoặc dữ liệu doanh thu/thành viên cho CHECK_IN_STAFF. |

*Bảng 3.30: Use case Xem lịch sử kiểm tra vé*

### 3.4.28. Duyệt hoặc từ chối yêu cầu hoàn vé

| Use Case Duyệt hoặc từ chối yêu cầu hoàn vé | Use Case Duyệt hoặc từ chối yêu cầu hoàn vé |
|---|---|
| **Mô tả** | Cho phép quản trị xem xét yêu cầu hoàn của khách và quyết định từ chối hoặc chuyển sang xử lý hoàn tiền. |
| **Tác nhân kích hoạt** | Quản trị nền tảng |
| **Tiền điều kiện** | Quản trị đã đăng nhập; Refund CUSTOMER_REFUND với reasonType CUSTOMER_REQUEST tồn tại ở REQUESTED. |
| **Các bước thực hiện** | 1. Quản trị xem cùng Refund, Order, Ticket, reason và amount bằng tổng paidAmount.<br>2. Chọn duyệt hoặc từ chối.<br>3. Từ chối nhập lý do, gọi Refund.reject(reason), lưu rejectionReason/REJECTED và audit; giữ lý do khách.<br>4. Khôi phục vé ACTIVE chỉ nếu Event chưa hủy, gửi thông báo sau commit.<br>5. Duyệt gọi approve chuyển APPROVED; amount > 0 thì beginAttempt(attemptId) với ID ổn định, lưu currentAttemptId/PROCESSING và RefundTransferLog kỹ thuật, gọi adapter sau commit.<br>6. Chỉ kết quả SUCCEEDED mới hoàn tất Refund/vé/trả kho; duyệt có tiền chưa đánh dấu vé đã hoàn.<br>7. Amount = 0 gọi completeWithoutTransfer, cùng Refund COMPLETED và vé REFUNDED/trả kho nguyên tử, không tạo Payment/RefundTransferLog hoặc processedAt chuyển tiền giả. Lời gọi lặp không tạo attempt mới. |

*Bảng 3.31: Use case Duyệt hoặc từ chối yêu cầu hoàn vé*

### 3.4.29. Xử lý hoàn tiền mô phỏng

| Use Case Xử lý hoàn tiền mô phỏng | Use Case Xử lý hoàn tiền mô phỏng |
|---|---|
| **Mô tả** | Cho phép quản trị ghi nhận kết quả mô phỏng của một lần hoàn tiền cho yêu cầu đã duyệt hoặc khoản thu cần bù trừ. |
| **Tác nhân kích hoạt** | Quản trị nền tảng |
| **Tiền điều kiện** | Có Refund APPROVED cần bắt đầu, PROCESSING/NEEDS_RECONCILIATION cần kết quả đúng attempt, hoặc RETRYABLE với chứng cứ FAILED đã xác minh để thử ID mới; có thể là hoàn vé hoặc PAYMENT_COMPENSATION. Nghĩa vụ chưa COMPLETED. |
| **Các bước thực hiện** | 1. Quản trị mở Refund và lịch sử; nếu amount = 0, hoàn tất bằng completeWithoutTransfer, không mô phỏng chuyển tiền.<br>2. Với amount > 0, beginAttempt(attemptId) từ APPROVED hoặc RETRYABLE hợp lệ, đặt currentAttemptId/PROCESSING và ghi RefundTransferLog; adapter chạy sau commit.<br>3. Kết quả mô phỏng SUCCEEDED, FAILED hoặc UNKNOWN được áp dụng qua recordAttemptResult với đúng attemptId; Service khóa Refund/lịch sử, đối chiếu ID, amount và kết quả đã lưu.<br>4. SUCCEEDED chuyển COMPLETED, lưu providerReference/processedAt đã xác minh và log nguyên tử; hoàn vé chuyển REFUNDED và trả đúng phân bổ kho, không trả ghế của giao dịch mới do callback cũ. PAYMENT_COMPENSATION không có Ticket và không tác động vé/kho.<br>5. FAILED chuyển RETRYABLE, chỉ sau xác minh mới dùng ID mới; UNKNOWN chuyển NEEDS_RECONCILIATION, phải đối chiếu trước khi thử lại, không tự khôi phục vé.<br>6. Cùng attemptId trả kết quả cũ, không hoàn/trả kho hai lần hoặc hồi quy kết quả cuối; một nghĩa vụ tối đa một lần thành công, tổng hoàn không vượt Payment.amount.<br>7. Chỗ chỉ bán lại nếu Event còn được bán; vé cũ không tái sử dụng, coupon đã dùng không trả.<br>8. Gửi thông báo sau commit, lỗi email không đổi kết quả. |

*Bảng 3.32: Use case Xử lý hoàn tiền mô phỏng*

### 3.4.30. Hủy sự kiện

| Use Case Hủy sự kiện | Use Case Hủy sự kiện |
|---|---|
| **Mô tả** | Cho phép quản trị hủy một sự kiện đã công khai trước giờ bắt đầu và khởi tạo quy trình hoàn tự động cho vé đủ điều kiện. |
| **Tác nhân kích hoạt** | Quản trị nền tảng |
| **Tiền điều kiện** | Quản trị đã đăng nhập; sự kiện đang `PUBLISHED` và thời điểm hiện tại trước `startTime`. |
| **Các bước thực hiện** | 1. Quản trị chọn hủy Event PUBLISHED trước startTime.<br>2. Hệ thống kiểm tra quyền/trạng thái/thời gian.<br>3. Chuyển Event CANCELLED, ngừng bán, chặn giữ mới và check-in ngay.<br>4. Giải phóng TicketHold còn hiệu lực, trả kho, cập nhật đơn chưa trả và trả coupon đang giữ.<br>5. Theo từng Order, khóa vé/Refund; dùng adoptEventCancellation trên Refund REQUESTED của khách, giữ lịch sử lý do, đổi reasonType EVENT_CANCELLATION và tự duyệt cùng nghĩa vụ.<br>6. Refund đã duyệt/đang xử lý tiếp tục giữ nguyên tập vé và amount; chỉ tạo Refund CUSTOMER_REFUND/EVENT_CANCELLATION rồi tự duyệt cho vé đủ điều kiện còn lại, không hoàn trùng vé đã hoàn/đang xử lý.<br>7. Vé USED là ngoại lệ cho quản trị, không tự hoàn hay khôi phục.<br>8. Chạy quy trình hoàn mô phỏng theo cùng Refund, hoàn 0đ không chuyển tiền; chia lô từng Order, lưu tiến độ để restart không tạo nghĩa vụ hoặc attempt trùng. |

*Bảng 3.33: Use case Hủy sự kiện*

### 3.4.31. Thiết lập chính sách hoa hồng và phí

| Use Case Thiết lập chính sách hoa hồng và phí | Use Case Thiết lập chính sách hoa hồng và phí |
|---|---|
| **Mô tả** | Cho phép quản trị tạo chính sách hoa hồng theo tổ chức với tỷ lệ, phí cố định và thời gian hiệu lực. |
| **Tác nhân kích hoạt** | Quản trị nền tảng |
| **Tiền điều kiện** | Quản trị đã đăng nhập; tổ chức cần cấu hình tồn tại. |
| **Các bước thực hiện** | 1. Quản trị chọn tổ chức và mở danh sách CommissionRule.<br>2. Quản trị nhập tỷ lệ phần trăm, phí cố định và khoảng thời gian hiệu lực.<br>3. Hệ thống kiểm tra dữ liệu tiền tệ, tỷ lệ và thời gian hiệu lực.<br>4. Hệ thống tạo quy tắc mới gắn với tổ chức.<br>5. Khi cần thay đổi chính sách, quản trị tạo quy tắc mới thay vì sửa tỷ lệ hoặc phí của quy tắc đã gắn với sự kiện.<br>6. Quy tắc đang hiệu lực được chọn lúc sự kiện được duyệt công khai và được giữ nguyên cho số liệu lịch sử. |

*Bảng 3.34: Use case Thiết lập chính sách hoa hồng và phí*

### 3.4.32. Lập và tính lại đối soát sự kiện

| Use Case Lập và tính lại đối soát sự kiện | Use Case Lập và tính lại đối soát sự kiện |
|---|---|
| **Mô tả** | Cho phép quản trị lập bản đối soát sau thời điểm kết thúc dự kiến của sự kiện và tính lại số liệu khi bản đối soát còn là nháp. |
| **Tác nhân kích hoạt** | Quản trị nền tảng |
| **Tiền điều kiện** | Quản trị đã đăng nhập; sự kiện đã qua `endTime`, kể cả sự kiện đã hủy; chưa có Settlement khác cho sự kiện hoặc Settlement hiện tại còn ở trạng thái nháp. |
| **Các bước thực hiện** | 1. Quản trị chọn Event đã qua endTime dự kiến, lập hoặc tính lại Settlement DRAFT.<br>2. Lấy CommissionRule đã gắn và Order PAID đủ điều kiện; đơn có tiền lấy đúng acceptedPayment CAPTURED của chính Order, đơn 0đ giữ số liệu 0.<br>3. Backend tổng hợp mỗi Order đúng một lần, lưu SettlementOrderSnapshot kỹ thuật: grossAmount là tiền hợp lệ sau giảm, refundAmount là tiền hoàn vé COMPLETED, remainingAmount = grossAmount - refundAmount.<br>4. Remaining bằng 0 thì hoa hồng 0; lớn hơn 0 thì commissionAmount = min(remainingAmount, remainingAmount × ratePercent / 100 + fixedFee), làm tròn đến đồng; netAmount = remainingAmount - commissionAmount.<br>5. Thu trùng/đến muộn và Refund PAYMENT_COMPENSATION được phản ánh riêng, không tạo doanh thu vé/hoa hồng.<br>6. Gọi Settlement.recalculate(grossRevenue, totalRefund, totalCommission) chỉ khi DRAFT; netPayable = grossRevenue - totalRefund - totalCommission là dẫn xuất.<br>7. Cho tính lại trước confirm; không có Order hợp lệ thì báo cáo 0, không tạo Settlement rỗng. Snapshot chi tiết là dữ liệu persistence, không phải lớp nghiệp vụ mới. |

*Bảng 3.35: Use case Lập và tính lại đối soát sự kiện*

### 3.4.33. Xác nhận đối soát sự kiện

| Use Case Xác nhận đối soát sự kiện | Use Case Xác nhận đối soát sự kiện |
|---|---|
| **Mô tả** | Cho phép quản trị xác nhận và đóng băng số liệu của bản đối soát đã hoàn tất điều kiện. |
| **Tác nhân kích hoạt** | Quản trị nền tảng |
| **Tiền điều kiện** | Settlement tồn tại và đang ở trạng thái nháp; sự kiện đã qua thời điểm kết thúc dự kiến; không còn Payment hoặc Refund liên quan đang chờ hay chưa rõ kết quả. |
| **Các bước thực hiện** | 1. Quản trị chọn confirm Settlement DRAFT.<br>2. Trong một giao dịch, khóa dữ liệu và kiểm tra đã qua endTime dự kiến, kể cả Event CANCELLED, cùng mọi Payment/Refund liên quan.<br>3. Tính lại từ nguồn mới nhất và kiểm tra mỗi Order đúng một lần trong SettlementOrderSnapshot kỹ thuật theo CommissionRule đã gắn.<br>4. Không còn Payment/Refund đang chờ hoặc chưa rõ thì gọi Settlement.confirm(now), chuyển CONFIRMED và đóng băng snapshot từng đơn cùng grossRevenue/totalRefund/totalCommission.<br>5. Còn giao dịch chưa giải quyết thì từ chối và liệt kê blocker.<br>6. Sau confirm không recalculate hoặc sửa snapshot do chính sách mới; paidAmount/pendingAmount chỉ thay đổi qua hành vi chi trả. |

*Bảng 3.36: Use case Xác nhận đối soát sự kiện*

### 3.4.34. Ghi nhận chi trả mô phỏng cho tổ chức

| Use Case Ghi nhận chi trả mô phỏng cho tổ chức | Use Case Ghi nhận chi trả mô phỏng cho tổ chức |
|---|---|
| **Mô tả** | Cho phép quản trị ghi nhận một hoặc nhiều khoản chi trả mô phỏng cho tổ chức theo bản đối soát đã xác nhận. |
| **Tác nhân kích hoạt** | Quản trị nền tảng |
| **Tiền điều kiện** | Settlement CONFIRMED; netPayable, paidAmount, pendingAmount và availableToPay được xác định; lần chi có amount > 0 không vượt availableToPay, hoặc netPayable = 0 để hoàn tất không chuyển tiền. |
| **Các bước thực hiện** | 1. Quản trị mở Settlement, nhập amount và reference; hệ thống cấp payoutId ổn định cho lần chi.<br>2. Khóa Settlement và lịch sử, kiểm tra amount <= availableToPay, paidAmount + pendingAmount <= netPayable.<br>3. Gọi beginPayout(payoutId, amount) để tăng pendingAmount, lưu SettlementTransferLog kỹ thuật, commit rồi gọi adapter mô phỏng.<br>4. recordPayoutResult với đúng ID/amount: SUCCEEDED giải phóng pending và tăng paidAmount; FAILED giải phóng pending, giữ lịch sử. Chỉ dùng SUCCEEDED/FAILED của PayoutResult.<br>5. Cùng ID không giữ/chi thêm; khác amount bị từ chối. Cho nhiều lần chi; thử sau FAILED dùng payoutId mới.<br>6. Chỉ paidAmount = netPayable và pendingAmount = 0 mới PAID.<br>7. netPayable = 0 hoàn tất không tạo lần chi hoặc nhật ký chuyển tiền giả; snapshot doanh thu/hoàn/phí đã confirm giữ nguyên. |

*Bảng 3.37: Use case Ghi nhận chi trả mô phỏng cho tổ chức*

### 3.4.35. Xem và lọc báo cáo

| Use Case Xem và lọc báo cáo | Use Case Xem và lọc báo cáo |
|---|---|
| **Mô tả** | Cho phép người có quyền xem số liệu vận hành, doanh thu và kiểm soát vé theo phạm vi được cấp. |
| **Tác nhân kích hoạt** | Quản lý tổ chức; Quản trị nền tảng |
| **Tiền điều kiện** | Tác nhân đã đăng nhập; quản lý có quyền `MANAGER` hiệu lực trong `User.organizationRoles` tại tổ chức cần xem. |
| **Các bước thực hiện** | 1. Tác nhân mở khu vực báo cáo.<br>2. Hệ thống xác định phạm vi: quản trị được xem toàn hệ thống, quản lý chỉ được xem dữ liệu của tổ chức mình.<br>3. Tác nhân chọn sự kiện hoặc khoảng thời gian cần lọc.<br>4. Hệ thống tổng hợp đơn, vé, tiền đã thu, doanh thu vé, tiền hoàn, hoa hồng, đối soát, số vé theo khu và số liệu check-in bằng các công thức thống nhất với đối soát.<br>5. Các khoản thu trùng hoặc đến muộn và hoàn bù trừ được phản ánh riêng cho đối chiếu dòng tiền, không tính thành doanh thu vé hoặc hoa hồng.<br>6. Hệ thống hiển thị số liệu và bảng chi tiết đúng phạm vi; yêu cầu truy cập dữ liệu tổ chức khác bị từ chối. |

*Bảng 3.38: Use case Xem và lọc báo cáo*

### 3.4.36. Xuất báo cáo CSV

| Use Case Xuất báo cáo CSV | Use Case Xuất báo cáo CSV |
|---|---|
| **Mô tả** | Cho phép người có quyền xuất dữ liệu báo cáo hiện tại thành tệp CSV dùng cùng phạm vi, công thức và bộ lọc với giao diện. |
| **Tác nhân kích hoạt** | Quản lý tổ chức; Quản trị nền tảng |
| **Tiền điều kiện** | Tác nhân có quyền xem báo cáo tương ứng; bộ lọc sự kiện hoặc thời gian đã được xác định. |
| **Các bước thực hiện** | 1. Tác nhân thiết lập bộ lọc trên báo cáo và chọn xuất CSV.<br>2. Hệ thống kiểm tra lại quyền và phạm vi dữ liệu ở phía máy chủ.<br>3. Hệ thống truy vấn dữ liệu bằng cùng công thức, điều kiện và bộ lọc đang dùng trên giao diện.<br>4. Hệ thống tạo tệp CSV hỗ trợ tiếng Việt khi mở bằng Excel.<br>5. Hệ thống vô hiệu hóa nội dung ô từ dữ liệu người dùng có thể bị chương trình bảng tính diễn giải thành công thức.<br>6. Hệ thống trả tệp cho tác nhân; dữ liệu ngoài phạm vi quyền không được đưa vào tệp. |

*Bảng 3.39: Use case Xuất báo cáo CSV*

### 3.4.37. Xem nhật ký thao tác hệ thống

| Use Case Xem nhật ký thao tác hệ thống | Use Case Xem nhật ký thao tác hệ thống |
|---|---|
| **Mô tả** | Cho phép quản trị tra cứu lịch sử các thao tác quan trọng đã được hệ thống ghi nhận. |
| **Tác nhân kích hoạt** | Quản trị nền tảng |
| **Tiền điều kiện** | Quản trị đã đăng nhập và có quyền nền tảng `ADMIN`. |
| **Các bước thực hiện** | 1. Quản trị mở chức năng nhật ký hệ thống.<br>2. Quản trị chọn điều kiện tra cứu theo người thực hiện, hành động, loại hoặc mã đối tượng và thời gian.<br>3. Hệ thống truy vấn AuditLog theo điều kiện đã chọn.<br>4. Hệ thống hiển thị người thực hiện, hành động, đối tượng, nội dung cần thiết và thời điểm ghi nhận.<br>5. Nhật ký không hiển thị mật khẩu, OTP, khóa bí mật, toàn bộ mã QR hoặc token chia sẻ.<br>6. Chỉ quản trị nền tảng được truy cập dữ liệu nhật ký này. |

*Bảng 3.40: Use case Xem nhật ký thao tác hệ thống*
