# Thứ tự áp migration

Các số `027`, `028`, `029`, `030`, `043`, `045`, `061` bị dùng cho nhiều file. Các file **không được đổi tên**: bảng
`dbo.tc_schema_migrations` trên Azure SQL ghi nhận migration theo tên file, và CI, `tools/acceptance` cùng các
nhánh khác tham chiếu những tên này. Thứ tự dưới đây mô tả lịch sử trên DB dùng chung, không phải một bộ cài
database rỗng có thể chạy nguyên từng file theo số. Các file từ `046` áp theo số, với hai file `061` riêng biệt.

| # | File |
|---|---|
| 1–8 | `001` … `008` (theo số) |
| 9 | `027_team_schema_adapter.sql` |
| 10 | `027_vuong_finance.sql` |
| 11 | `028_event_image_workflow.sql` |
| 12 | `028_vuong_report_checkins.sql` |
| 13 | `029_admin_organization_approval.sql` |
| 14 | `029_hold_runtime.sql` |
| 15 | `029_khanh03_refund_request.sql` |
| 16 | `030_khanh02_signed_checkin.sql` |
| 17 | `030_order_runtime.sql` |
| 18–29 | `031` … `042` (theo số) |
| 30 | `043_coupon_admin.sql` |
| 31 | `045_order_ticket_access_assertions.sql` |
| 32 | `044_buyer_order_history_views.sql` |
| 33 | `045_event_commands_and_public_seats.sql` |
| 34 | `046_admin_refund_current_authority.sql` |
| 35 | `047_event_authority_lock_order.sql` |
| 36 | `043_khanh04_admin_refund.sql` |
| 37+ | `048` … (theo số) |

Lưu ý lịch sử: `045_order_ticket_access_assertions.sql` được áp **trước** `044`, và `043_khanh04_admin_refund.sql`
được ghi nhận **sau** `047` trên DB đã có các đối tượng nền tương ứng.

## Dựng mới môi trường kiểm thử trên máy

`tools/local/setup.ps1` dựng database mới riêng, có marker và tên `TC_Vuong_Test_Local_*`, từ snapshot schema
không có dữ liệu ứng dụng rồi áp đầy đủ migration hiện tại. Công cụ này không kết nối Azure và không ghi đè DB
hay cấu hình đã tồn tại. Đã kiểm tra dựng mới qua migration `067` ngày 14/10/2026.

Khi dựng từ nền chưa có `sp_admin_refund_decide`, phải áp `043_khanh04_admin_refund.sql` **trước** `046`;
preflight của `046` yêu cầu procedure đó tồn tại. Công cụ tạo các SQL role còn thiếu trước migration sử dụng chúng.
Preflight của `052` được thực thi động sau khi xác nhận bảng SSMS `sysdiagrams` tồn tại, tránh lỗi phân giải tên
ở một DB mới không có bảng này; migration lịch sử giữ nguyên byte và các định nghĩa module không thay đổi.

Không dùng vòng lặp áp nguyên `001`–`008` theo số để bootstrap: `004` có hàm phụ thuộc các view trong `005`,
và `vw_EventSalesReport` trong `005` phụ thuộc `vw_OrderFinancialSummary` cùng file. Một bộ cài baseline từ
những file đó phải sắp xếp các batch theo phụ thuộc, tạo các hàm scalar cần thiết trước view sử dụng chúng,
rồi tạo các hàm/view còn lại. Snapshot dành riêng cho kiểm thử không được dùng để thay schema trên Azure.

Kết nối owner dùng chung trong fixture là lựa chọn kiểm thử ứng dụng; nó không chứng minh quyền tối thiểu
của các SQL principal ở môi trường triển khai.

Migration mới: lấy số kế tiếp chưa có trong `dbo.tc_schema_migrations` (kiểm tra cả các nhánh chưa merge),
một số một file.

`068_checkout_eligible_coupons.sql` bổ sung procedure chỉ đọc và quyền EXECUTE cho `tc_buyer`,
phục vụ combo box ưu đãi trên trang thanh toán. Áp migration này trước khi triển khai API/UI chọn mã;
việc áp dụng mã vẫn đi qua command hiện có để kiểm tra điều kiện và giữ lượt sử dụng.
