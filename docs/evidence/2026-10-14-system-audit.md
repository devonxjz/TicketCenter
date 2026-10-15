# Rà soát hệ thống TicketsCenter — 14/10/2026

Bổ sung sau phản hồi lỗi buổi sáng: lần triển khai giao diện khác đã bỏ sót sửa ghế/mua nhanh. Đã tái hiện, ghép đủ bản sửa, xử lý thêm window focus làm gián đoạn combo box và triển khai lại cổng chính. Xem [bằng chứng xử lý lỗi quay lại](2026-10-14-booking-deployment-regression.md). Kết quả phía dưới mô tả vòng kiểm tra trước đó; bằng chứng bổ sung xác định rõ bản triển khai mới.

Ứng dụng được kiểm tra tại `http://127.0.0.1:18082/ticketscenter`, kết nối Azure SQL `TicketsCenter`. Kết quả dưới đây phân biệt kiểm tra trên ứng dụng thật, fixture riêng và kiểm thử không gọi nhà cung cấp. Không suy ra mức hoàn thành 100% từ số lượng test.

## Kết luận theo yêu cầu

| Yêu cầu | Kết quả và giới hạn |
|---|---|
| Thống nhất giao diện | Đã sửa nhận diện header, menu theo vai trò, trạng thái trang hiện tại, bố cục số tiền dashboard, thông báo checkout và ngày giờ đơn/hoàn vé. Đã kiểm tra desktop và màn hình 390px ở các luồng chính. Chưa chứng minh mọi trang ở mọi trình duyệt/breakpoint. |
| Xem sự kiện → chọn vé/ghế → coupon → thanh toán → hoàn tiền | Đã đi qua thanh toán VNPAY sandbox thành công, phát hành vé/QR; nhánh hủy tại VNPAY và vòng mua/hoàn vé miễn phí cũng đạt. Refund 720.000 ₫ đã được admin duyệt nhưng provider chưa xác nhận hoàn thành; đang đối soát. Chưa đạt điều kiện tuyên bố toàn bộ luồng hoạt động 100%. |
| Báo cáo admin/manager | Đạt trong phạm vi đã đối chiếu: doanh thu theo đơn và dòng tiền, các khoảng all/7/30/90 ngày, CSV, tổng quan theo ngày; manager chỉ truy cập tổ chức có quyền. Kiểm tra lại sau giao dịch VNPAY mới: 141/141 đạt. |
| Dữ liệu nghiệp vụ từ database | Catalog public đủ 29 sự kiện khớp SQL; kiểm tra trạng thái hiển thị của 48 sự kiện. Số liệu báo cáo, vé, đơn, tồn kho và quyền được đối chiếu DB. Không phát hiện fallback số liệu giả trong phạm vi rà soát. Nhãn, biểu tượng, bản dịch trạng thái là tài nguyên giao diện hợp lệ. Chưa thể chứng minh mọi trường trên mọi trang chỉ bằng các kiểm tra này. |
| Bao quát hệ thống | Có [ma trận nghiệm thu](2026-10-14-system-acceptance-matrix.md) phân mức P0/P1/P2, đánh dấu bằng chứng và khoảng trống cụ thể. |

## Các lỗi đã sửa và xác minh

- Chọn ghế trực tiếp không hoạt động và số lượng không đồng bộ: xử lý lại thao tác ghế, số lượng và giới hạn 8 vé. Hai khách tranh cùng ghế chỉ một người thành công.
- Coupon bị ẩn/không thao tác được khi chưa tạo đơn; nút mua nhanh bỏ qua bước nhập mã: checkout giữ bước xác nhận và nhập coupon, tạo đơn theo hold khi cần, giữ kiểm tra chủ sở hữu và khóa gửi lặp.
- Callback VNPAY trỏ cổng 8080 thay vì 18082: launcher tạo return URL từ địa chỉ ứng dụng đang chạy. Đã kiểm tra callback thật từ sandbox.
- VNPAY hủy giao dịch trả mã tham chiếu `0`, gây trùng khóa: không lưu `0` thành tham chiếu giao dịch thất bại. Chỉ ghi nhận thành công khi trạng thái thanh toán phù hợp, tránh nhầm response thành công nhưng giao dịch thất bại.
- Tra cứu hoàn tiền chưa triển khai: bổ sung querydr có chữ ký; chỉ chấp nhận đúng merchant, đơn, số tiền, loại hoàn và dấu nhận diện lần thử. Giao dịch thanh toán gốc không được coi là bằng chứng đã hoàn tiền.
- Đăng xuất qua GET và POST trực tiếp chưa kiểm tra CSRF đầy đủ: GET giữ nguyên phiên, POST cần CSRF; giao diện báo lỗi khi đăng xuất thất bại.
- Email admin/đăng ký tổ chức được escape; bỏ email admin dự phòng tĩnh. Lỗi API đơn hàng không trả thông báo exception nội bộ.
- Dashboard manager có số tiền bị ngắt dòng; trạng thái phục hồi checkout hiển thị quá sớm; ngày giờ đơn/hoàn vé còn chuỗi ISO: sửa bố cục và cách hiển thị.
- Bộ tạo database kiểm thử thiếu migration/phụ thuộc: cập nhật công cụ chạy đầy đủ lịch sử migration, xử lý thứ tự 043/046 và tùy chọn sysdiagrams. Database mới cài bằng công cụ đã chạy ứng dụng WAR thành công, 17/17 kiểm tra đạt. Fixture dùng database owner nên không chứng minh least privilege của SQL.

Chi tiết tương tác ghế/coupon: [bằng chứng riêng](2026-10-14-seat-coupon-interaction-fix.md). Các sửa ảnh, tìm kiếm và điều hướng trước vòng này nằm trong [báo cáo trước](2026-10-14-audit-fixes.md); số lượng test cũ không thay thế kết quả cuối bên dưới.

## Giao dịch sandbox có bằng chứng

Khách `tran.minh.tam.122` mua một vé GA2A của Tinh Hà “Say Hi” Concert — Đêm 2: giá 800.000 ₫, coupon `SUKIEN005` giảm 80.000 ₫, thanh toán 720.000 ₫ bằng thẻ test công khai của VNPAY. Người dùng đã cho phép chấp nhận điều khoản sandbox. Không sử dụng tiền thật.

| Đối tượng | Giá trị xác minh |
|---|---|
| Order | `fa6ffbdc-574f-4a73-9fcd-ebadd1ea62de` — PAID |
| Payment | CAPTURED, 720.000 ₫, tham chiếu VNPAY `15703653` |
| Vé | `878f7fc6-5ec7-f111-bf5b-0022481628f1`; đã hiển thị QR và giá thực trả 720.000 ₫ |
| Refund | `f4e1b34c-a696-44d0-be42-75989d9e2ceb`, 720.000 ₫, NEEDS_RECONCILIATION |
| Lần chuyển tiền | `6de738c3-7102-4a54-8684-205bf0471be9`, UNKNOWN |

Khách đã gửi yêu cầu trên giao diện; admin đã duyệt. Vé giữ REFUND_PENDING và chưa trả vào tồn kho. Tra cứu VNPAY hiện trả loại `01`, nội dung thanh toán gốc, trạng thái thanh toán `00`; đây không phải xác nhận refund. Không sửa trạng thái thành COMPLETED hoặc gửi lại lệnh hoàn khi kết quả chưa rõ. Chỉ ghi nhận hoàn tiền và trả kho sau bằng chứng hợp lệ của provider/đối soát.

Trong ngày UTC của giao dịch, Tinh Hà có tổng thu vé 42.820.000 ₫, đã bao gồm khoản 720.000 ₫ mới; khoản hoàn này chưa được tính là tiền đã chi. Số liệu admin/manager sau giao dịch khớp SQL.

Vòng 0 đồng đã hoàn tất thật qua ứng dụng: order `972a00d9-d4cf-43fb-a495-6730ec42d82a`, refund `9dc0edb6-1754-4b0b-9688-5032c3c72385` COMPLETED, vé REFUNDED và khôi phục tồn kho. Vòng này chứng minh nghiệp vụ nội bộ, không chứng minh ngân hàng đã hoàn tiền.

![Thanh toán VNPAY sandbox thành công](E:/TicketCenter/.local/audit-vnpay-paid-success.png)

Tài liệu đối chiếu: [VNPAY sandbox và thẻ test](https://sandbox.vnpayment.vn/apis/docs/gioi-thieu/), [quy trình querydr/refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html).

## Bằng chứng kiểm tra

| Bộ kiểm tra | Kết quả |
|---|---|
| Java / Maven package, bản cuối 06:41 | 1.044 test, 0 failure/error; 151 test dịch vụ ngoài bỏ qua theo cấu hình |
| JavaScript, gồm kiểm tra an toàn launcher PowerShell | 228 test: 226 đạt, 2 bỏ qua, 0 thất bại |
| Ứng dụng WAR trên database fixture mới | 17/17 đạt, 0 bỏ qua |
| HTTP trên Azure sau thanh toán mới và khởi động bản cuối | 141/141 đạt |
| Tranh chấp giữ ghế/đơn và coupon qua HTTP | 14/14 đạt; các lượt giữ tạo trong vòng này được hủy |
| Kịch bản SQL canonical | PASS, 29 dòng kết quả: yêu cầu/duyệt/từ chối/hoàn lỗi/thử lại/gửi lặp/thanh toán muộn/hủy sự kiện/outbox; transaction rollback |
| Đối chiếu toàn DB | 364 khách/tài khoản, 1.895 đơn, 3.908 vé, 48 sự kiện, 29 tổ chức; không lệch tồn kho hoặc tổng tiền đơn PAID so với vé |

Log và snapshot riêng nằm trong `.local/system-audit-*`: `db.json`, `http-results.json`, `http-final.log`, `final-startup.log`, `booking-races.json`, `sql-scenarios.json`, `js-complete.log`, `full-war-final-verified.log`, `paid-lifecycle-final.json`, `paid-coupon.json`. Các tệp cấu hình, URL callback có chữ ký và thông tin xác thực không được đưa vào báo cáo công khai.

Ngày giờ trang hoàn vé được xác minh trên bản cuối bằng HTTP 200, JSP biên dịch và kiểm tra cú pháp script (`refund-page.json`). Trình duyệt từ chối tải mới localhost sau lần khởi động cuối nên không dùng trang cũ còn mở làm bằng chứng hình ảnh cho thay đổi ngày giờ. Ảnh thanh toán phía trên được chụp từ giao dịch sandbox trước lần khởi động này.

## Những điểm còn thiếu để nghiệm thu 100%

1. Xác nhận refund sandbox 720.000 ₫ bằng bằng chứng provider; tiếp tục luồng đối soát của lần thử hiện tại, không tự gửi thêm refund.
2. IPN độc lập từ VNPAY cần địa chỉ nhận truy cập được từ Internet. Thành công qua return URL trên localhost chưa chứng minh trường hợp khách đóng trình duyệt trước callback.
3. SMTP/OTP, email xác nhận mua và hoàn, reset mật khẩu cần môi trường email kiểm thử; outbox nội bộ không thay thế kiểm tra thư được nhận.
4. Cần nghiệm thu thêm tranh chấp vé đứng cuối, lượt coupon cuối, thu hồi quyền giữa phiên, quét QR đồng thời, thanh toán kép, các ranh giới thời gian và khôi phục sau sự cố.
5. Adapter chi trả cho tổ chức hiện chưa bật; báo cáo số dư không chứng minh tiền được chuyển về ngân hàng. Cần kiểm thử quyết toán/chi trả và quyền SQL tối thiểu bằng principal tách vai trò.
6. Cần vòng accessibility, Safari/Firefox, các breakpoint còn lại, tải đồng thời và diễn tập backup/restore. Chi tiết và điều kiện đạt được ghi trong ma trận nghiệm thu.

Dữ liệu dựng để trình bày đã lưu trong database và có quan hệ nghiệp vụ; không dùng các giao dịch đó làm bằng chứng ngân hàng đã thu hoặc hoàn tiền. Các thay đổi mã nguồn đang giữ trong workspace để xem xét, chưa commit/push.
