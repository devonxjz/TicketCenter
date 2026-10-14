# Ma trận nghiệm thu hệ thống TicketsCenter — 14/10/2026

Ma trận này bổ sung các tiêu chí ngoài luồng mua vé thông thường. Không dùng số lượng unit test để suy ra tỷ lệ hoàn thành toàn hệ thống.

**H**: đã đạt trên ứng dụng đang dùng Azure SQL, qua HTTP hoặc trình duyệt. **I**: đã đạt trong database/ứng dụng kiểm thử riêng trên máy. **U**: đã đạt kiểm thử tự động không gọi nhà cung cấp. **C**: cần chạy thêm, chưa có đủ bằng chứng nghiệm thu. H/I/U không thay thế nhau; một dòng có thể cần nhiều mức.

| ID | Ưu tiên | Tình huống và điều kiện đạt | Bằng chứng hiện có |
|---|---|---|---|
| UI01 | P1 | Các trang khách hàng, admin, manager và nhân viên dùng cùng nhận diện, điều hướng đúng vai trò | H: 29 trang tải được; kiểm tra giao diện các luồng chính |
| UI02 | P1 | Tổng quan không cắt hoặc xuống dòng giữa số tiền; bố cục co giãn trên mobile | H: dashboard Vie Channel, màn hình 390px |
| UI03 | P1 | Checkout hiển thị lựa chọn, tiền vé, giảm giá, tổng thanh toán và thời hạn rõ ràng | H: ghế và vé đứng |
| UI04 | P2 | Trạng thái đang gửi không bị mô tả là thất bại/chưa rõ; chỉ hiện phục hồi khi thực sự cần | H/U: sửa hiển thị và chạy lại bộ JavaScript |
| UI05 | P2 | Ngày giờ của sự kiện, vé và đơn hiển thị dễ đọc theo múi giờ người xem; báo cáo nêu rõ UTC | H: sửa ngày giờ đơn hàng và hoàn vé; C kiểm tra toàn bộ trang/ranh giới múi giờ |
| UI06 | P2 | Menu chỉ rõ trang hiện tại cho trình đọc màn hình, có skip link và trạng thái tải | H/I: aria-current trên menu, ứng dụng riêng 17 kiểm tra đạt |
| UI07 | P2 | Toàn bộ biểu mẫu dùng được chỉ bằng bàn phím, focus đúng sau modal/lỗi | C: cần vòng kiểm tra accessibility đầy đủ |
| UI08 | P2 | Các breakpoint 320/390/768/1280/1920, zoom 200%, Safari và Firefox đều dùng được | H một phần: 390px và desktop; C các môi trường còn lại |
| AU01 | P0 | Anonymous không đọc được dữ liệu riêng; khách hàng và staff không đọc tài chính | H/I: kiểm tra 401/403 |
| AU02 | P0 | Manager chỉ đọc đúng tổ chức đang có quyền; UUID của tổ chức khác bị từ chối | H/I: kiểm tra phạm vi options và truy cập chéo |
| AU03 | P0 | Khách không đọc hoặc hủy đơn/lượt giữ/vé của người khác | H: 403/404 |
| AU04 | P0 | Mutation cần CSRF; cả /auth/logout và /api/auth/logout được bảo vệ; GET không đổi trạng thái phiên | H/I/U: sau sửa |
| AU05 | P1 | Đăng nhập đổi session ID/CSRF; đăng xuất vô hiệu phiên; gửi lặp đăng xuất an toàn | H/I |
| AU06 | P0 | Thu hồi quyền, khóa user hoặc bỏ membership vô hiệu quyền ngay, kể cả khi request đang chạy | C: cần kiểm thử thu hồi quyền và cạnh tranh trên fixture riêng |
| AU07 | P1 | Đăng ký → OTP → kích hoạt → đăng nhập và quên mật khẩu → đặt lại mật khẩu hoàn chỉnh | C: cần SMTP sink và vòng email đầu cuối |
| AU08 | P1 | Rate limit đăng nhập/OTP, thời hạn mã, mã dùng một lần và chống dò tài khoản | C: cần nghiệm thu tích hợp riêng |
| EV01 | P1 | Toàn bộ catalog, giá tối thiểu, tên tổ chức và ảnh khớp DB | H: đủ 29 bản ghi public |
| EV02 | P0 | Tất cả trạng thái sự kiện/archived chỉ xuất hiện theo view public; event không tồn tại trả 404 | H: kiểm tra đủ 48 sự kiện |
| EV03 | P1 | Tìm kiếm, danh mục, ngày, sắp xếp, phân trang và back/forward giữ bộ lọc | H một phần/U; cần nghiệm thu kết hợp toàn bộ bộ lọc |
| EV04 | P1 | Thời điểm mở/đóng bán chính xác ở ranh giới, không giữ vé sau đóng bán | C: cần fixture chạy quanh ranh giới UTC |
| EV05 | P1 | Manager tạo/sửa/gửi duyệt; admin duyệt hoặc từ chối; version cũ không ghi đè | C: vòng nghiệp vụ đầy đủ và tranh chấp phiên bản |
| EV06 | P1 | Upload ảnh kiểm tra loại/kích thước, gửi lặp an toàn, còn ảnh sau restart | Bằng chứng riêng: 2026-10-14-audit-fixes.md; cần xác minh lại trên môi trường triển khai cuối |
| BK01 | P0 | Click ghế còn trống trực tiếp chọn được và đồng bộ số lượng | H/U: lỗi chọn ghế đã sửa |
| BK02 | P0 | Ghế đã bán/đang giữ không thể mua; hai khách tranh một ghế chỉ một người thắng | H: 201/409 khi gửi đồng thời |
| BK03 | P0 | Trên 8 vé và ghế bị lặp trong cùng lựa chọn bị từ chối | H/U: 400 |
| BK04 | P0 | Giữ vé đứng không vượt sức chứa khi nhiều khách mua sát số cuối | C: cần fixture cạnh tranh sức chứa cuối |
| BK05 | P0 | Gửi lại cùng khóa trả cùng hold; đổi payload với khóa cũ bị từ chối | H/U: same ID, 409 |
| BK06 | P0 | Tạo đơn đồng thời từ cùng hold không tạo hai đơn | H: hai phản hồi cùng order ID |
| BK07 | P0 | Hủy hold trả ghế và quota; ghế đặt lại được dù đã từng tạo đơn | H: hủy, đặt lại, dọn lượt giữ |
| BK08 | P1 | Hết hạn, reload, tab ngủ, back/forward và đổi tài khoản không khôi phục nhầm hold | H một phần/U; C vòng tab ngủ/đổi tài khoản dài hạn |
| CP01 | P0 | Coupon thao tác được trước thanh toán, không bị bỏ qua bởi nút mua nhanh | H/U: checkout giữ bước nhập mã |
| CP02 | P0 | Giảm phần trăm và mức trần đúng, số tiền do máy chủ quyết định | H: 4.000.000 → 3.800.000 và 800.000 → 720.000 |
| CP03 | P1 | Mã không tồn tại không giảm tiền; gỡ mã khôi phục tiền gốc | H |
| CP04 | P0 | Mã sai tổ chức, hết hạn, chưa bắt đầu, tắt hoặc hết quota đều bị từ chối | U một phần; C vòng tích hợp đủ loại |
| CP05 | P0 | Hai đơn tranh lượt coupon cuối không vượt quota; hủy/hết hạn trả lượt đúng một lần | C: fixture cạnh tranh quota |
| CP06 | P0 | Coupon không thay đổi sau khi payment đang xử lý hoặc đơn đã thanh toán | U; C nghiệm thu provider/đơn đã trả tiền |
| PA01 | P0 | Đơn 0 đồng phát hành vé qua backend mà không gọi ngân hàng | H: đã mua và hoàn một vé miễn phí |
| PA02 | P0 | URL sandbox hợp lệ, đúng số tiền và callback đúng cổng website đang chạy | H: sửa callback 8080 → 18082 và đi qua VNPAY thật |
| PA03 | P0 | Khách hủy tại VNPAY → callback xác thực → không phát hành vé; gửi lặp không đổi kết quả | H: nhánh hủy + reload callback; cần xem snapshot DB kèm báo cáo |
| PA04 | P0 | Thẻ test VNPAY thanh toán thành công → tiền CAPTURED → một bộ vé/QR → coupon consumed | H: order fa6ffbdc… thanh toán 720.000 ₫ sau coupon, provider ref 15703653, một vé/QR, coupon CONSUMED trong DB |
| PA05 | P0 | Callback sai chữ ký/số tiền/merchant/txnRef không ghi nhận thu tiền | U; C vòng HTTP có provider/fixture đầy đủ |
| PA06 | P0 | IPN độc lập hoạt động dù khách đóng trình duyệt; trả lời lặp đúng hợp đồng | C: cần callback từ Internet tới môi trường thử được phép |
| PA07 | P0 | Thanh toán tới sau hết hold tạo bồi hoàn, không phát hành vé không còn chỗ | I: kịch bản canonical, rollback toàn bộ |
| PA08 | P0 | Hai payment cùng CAPTURED chỉ một payment được chấp nhận, khoản thừa được theo dõi riêng | C: nghiệm thu tích hợp cạnh tranh, đối chiếu ledger |
| TK01 | P1 | Người sở hữu đọc danh sách/chi tiết/QR, người khác không truy cập được | H: owner checks và vé/QR sau thanh toán sandbox; C nghiệm thu scanner |
| TK02 | P0 | QR giả, token bị đổi, vé đã refund, event sai và ngoài giờ check-in bị từ chối | U một phần; C vòng scanner thật |
| TK03 | P0 | Hai scanner quét cùng vé chỉ có một lượt thành công, retry không thêm log thành công | C: fixture/scanner cạnh tranh |
| TK04 | P1 | Staff chỉ soát vé của tổ chức được phân quyền và không đọc giá/tài chính | H quyền tài chính; C projection check-in đầy đủ |
| RF01 | P0 | Khách chỉ chọn vé đủ điều kiện, số tiền hoàn lấy từ paid_amount, không lấy lại giá niêm yết | H/I: đơn giá 800.000 ₫ sau coupon còn 720.000 ₫; yêu cầu hoàn đúng 720.000 ₫ |
| RF02 | P0 | Yêu cầu hoàn gửi lặp trả cùng refund ID và không khóa vé hai lần | H/I |
| RF03 | P0 | Admin duyệt đơn miễn phí → COMPLETED → vé REFUNDED → tồn kho khôi phục | H |
| RF04 | P0 | Admin từ chối → REJECTED và giải phóng khóa hoàn vé | I |
| RF05 | P0 | Gateway FAILED → RETRYABLE → lần thử mới → COMPLETED; replay không hoàn hai lần | I |
| RF06 | P0 | Gateway không rõ → NEEDS_RECONCILIATION; không tự coi duyệt là đã trả tiền | I/U; H quan sát hồ sơ chưa rõ hiện tại |
| RF07 | P0 | Tra cứu provider chỉ chấp nhận chữ ký, số tiền, loại refund và đúng attempt; original payment không chứng minh refund | U: sửa querydr và test hồi quy |
| RF08 | P0 | Refund sandbox thật có bằng chứng provider và hoàn đúng số tiền, toàn phần/lẻ | C: refund f4e1b34c… 720.000 ₫ đã duyệt, NEEDS_RECONCILIATION; querydr chỉ xác nhận thanh toán gốc |
| RF09 | P0 | Hủy sự kiện xử lý mọi đơn, payment tới muộn và refund đang mở, không mất nghĩa vụ | I một phần: canonical cancellation; C batch/quy mô và cạnh tranh |
| RF10 | P1 | Email mua/hoàn vé được gửi một lần, retry outbox không lặp và lỗi SMTP không làm mất giao dịch | I: outbox context; C vận chuyển SMTP |
| FI01 | P0 | COHORT_REVENUE và CASH_FLOW khớp SQL từng sự kiện với all/7/30/90 ngày cho admin/manager | H: 16 bộ báo cáo |
| FI02 | P0 | CSV cùng bộ lọc khớp từng số tiền SQL, giữ số âm và chống công thức độc hại | H/U: 16 file CSV, có xử lý tab bảo vệ số âm |
| FI03 | P0 | Tổng quan/biểu đồ 7 ngày khớp gross revenue và net cash flow mỗi ngày UTC | H: 14 ngày/phạm vi đối chiếu SQL |
| FI04 | P1 | EVENT_SALES và SETTLEMENT_BALANCE khớp các view liên quan | H: manager Vie Channel |
| FI05 | P0 | Tổng đơn PAID = tổng paid_amount của vé = accepted CAPTURED payment; tồn kho phù hợp vé đang sống | H: kiểm tra toàn DB, không có chênh lệch |
| FI06 | P0 | Refund/compensation được ghi nhận đúng ngày xử lý; revenue cohort và cash flow có thể khác nhau hợp lệ | I một phần/H các khoảng báo cáo; C fixture ranh giới ngày cụ thể |
| FI07 | P0 | Quyết toán chặn nghĩa vụ chưa rõ, snapshot sau confirm bất biến, payout không vượt available | C: nghiệm thu payout/quyết toán riêng; adapter chi trả hiện không bật |
| FI08 | P1 | Phân trang và tổng hợp toàn bộ không bị nhầm với tổng của trang hiện tại, filter event/org không rò tenant | H một phần/U; C tổ hợp filter và phân trang lớn |
| OP01 | P0 | Business data lấy qua DB; không có bảng số tiền/sự kiện/đơn giả hardcode trong trang | H: catalog/report/stock parity + rà soát nguồn |
| OP02 | P1 | Database/Redis/provider lỗi phải hiện trạng thái không khả dụng; không thay bằng dữ liệu giả hoặc báo thành công | U/I một phần; C fault injection hệ thống |
| OP03 | P1 | Restart khôi phục idempotency, hold, refund và worker; không mất receipt | H restart và dữ liệu DB giữ nguyên; C kill tại điểm giữa commit/response |
| OP04 | P0 | Log/API không lộ mật khẩu, secret, SQL nội bộ; dữ liệu người dùng được escape trong HTML/CSV | H/U: sửa email và lỗi API đơn hàng; C security audit toàn diện |
| OP05 | P0 | DB principal riêng cho các vai trò có least privilege, quyền của user không thay thế quyền SQL | C: fixture dùng owner không chứng minh tiêu chí này |
| OP06 | P1 | Bộ cài DB mới có đúng phụ thuộc module/migration và tự kiểm tra phiên bản | I: công cụ cài mới đã xử lý dependency và sysdiagrams; WAR trên DB mới 17/17 đạt; thứ tự lịch sử ghi trong APPLY_ORDER.md |
| OP07 | P1 | Backup/restore phục hồi được dữ liệu, ảnh, QR secret và receipt; diễn tập có đối chiếu | C |
| OP08 | P1 | SLA tải trang, truy vấn và tải đồng thời; không cạn connection pool hoặc deadlock kéo dài | C: cần kiểm thử tải có mục tiêu và quy mô xác định |

## Điều kiện chốt nghiệm thu

Trước khi xác nhận hệ thống đạt toàn bộ yêu cầu: các dòng P0 phải có bằng chứng tích hợp thích hợp, không còn lỗi P0/P1 ảnh hưởng phạm vi sử dụng, và các khoảng trống ở provider/IPN/SMTP/SQL permissions phải được xử lý hoặc được chấp nhận rõ. Hiện đủ cơ sở trình bày các luồng nội bộ đã kiểm; chưa đủ cơ sở tuyên bố thanh toán và hoàn tiền qua ngân hàng hoạt động 100%.

Các giao dịch dựng dữ liệu trình bày trong database không được dùng làm bằng chứng rằng ngân hàng đã thu/hoàn tiền. Dữ liệu tĩnh của giao diện như nhãn, biểu tượng và bản dịch trạng thái vẫn là mã giao diện, còn số liệu nghiệp vụ phải có nguồn DB.
