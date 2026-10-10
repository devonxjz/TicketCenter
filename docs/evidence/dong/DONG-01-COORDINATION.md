# Issue phối hợp — quyền đọc sơ đồ ghế public

Trạng thái: đã đăng [issue #45](https://github.com/devonxjz/TicketCenter/issues/45), liên quan [DONG-01 #8](https://github.com/devonxjz/TicketCenter/issues/8).

## Vấn đề

DONG-01 sở hữu `GET /api/zones/{zoneId}/seats`, dùng principal BUYER để phục vụ public. Migration `008_create_runtime_roles_and_permissions.sql` có SELECT trên `vw_PublicEvents`, `vw_ZoneInventory` và `tc_event_categories` cho `tc_buyer`, nhưng chưa có SELECT trên `tc_seats`. View/UDF hiện có chỉ trả số lượng inventory, chưa trả row/number/label/status từng Seat.

Repository dùng query guard public Event rồi mới đọc cột Seat; với login thật bị giới hạn đúng `tc_buyer`, query này sẽ không đủ quyền. API báo 503 khi DB từ chối; không đọc bằng tài khoản admin/manager và không dựng sơ đồ ghế giả.

## Phối hợp với chủ database/runtime integration

Cung cấp đường đọc được cấp quyền public, ưu tiên View hoặc inline TVF chỉ trả Seat của Event PUBLISHED. Projection cần `id`, `zone_id`, `row_name`, `seat_number`, `label`, `status`; không cần hold owner/order/payment hoặc thông tin khách hàng. Chốt tên object/chữ ký rồi Đông đổi query và test ở Repository tương ứng.

Nếu thống nhất dùng quyền cột thay vì public View, cần migration tiếp theo cấp SELECT các cột trên cho `tc_buyer`, cùng test quyền. Không sửa migration đã áp dụng. Quyết định này thuộc chủ database và cần review vì login sẽ đọc được các Seat trực tiếp; query HTTP vẫn phải guard public Event.

## Điều kiện chấp nhận

- Login runtime `tc_buyer` đọc được sơ đồ ghế của Event PUBLISHED qua đường đọc đã chốt.
- HTTP không lộ Seat của Event DRAFT/PENDING_APPROVAL/REJECTED/CANCELLED hoặc Zone ngoài public scope.
- Integration test dùng SQL Server thật và login runtime giới hạn; không dùng `sa` để báo quyền đã đạt.
- Đông không sửa `database/**`, model, `pom.xml` hoặc persistence/config chung trong feature PR.
