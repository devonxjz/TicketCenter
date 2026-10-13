# Thứ tự áp migration

Sáu số bị dùng cho nhiều file (`027`, `028`, `029`, `030`, `043`, `045`). Các file **không được đổi tên**: bảng
`dbo.tc_schema_migrations` trên Azure SQL ghi nhận migration theo tên file, và CI, `tools/acceptance` cùng các
nhánh khác tham chiếu những tên này. Thứ tự dưới đây là thứ tự đã áp trên DB dùng chung; DB mới phải áp đúng
thứ tự này. Từ `046` trở đi mỗi số chỉ có một file và áp theo số.

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

Lưu ý: `045_order_ticket_access_assertions.sql` được áp **trước** `044`, và `043_khanh04_admin_refund.sql`
được áp **sau** `047`.

Migration mới: lấy số kế tiếp chưa có trong `dbo.tc_schema_migrations` (kiểm tra cả các nhánh chưa merge),
một số một file.
