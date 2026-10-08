> **Convention:** `dev` → `feat/dong/dong-03-zone-seat-inventory` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa Event inventory, Zone/Seat controller-service-repository-cache, JSP editor và test. Không sửa Hold/Order của Liêm.

## Mục tiêu

Quản lý Zone/Seat của Event và bảo vệ cấu trúc tồn kho cho UI-13.

## Endpoint

- `POST /api/events/{eventId}/zones`
- `POST /api/zones/{zoneId}/edit`
- `POST /api/zones/{zoneId}/delete`
- Endpoint xem trước sơ đồ ghế thuộc controller Event của Đông.

## Cách làm kỹ thuật

- SEATED nhận `rows`, `seatsPerRow`, sinh nhãn hàng A, B… và số ghế duy nhất trong Zone; quantity vật lý do DB xác nhận.
- STANDING nhận capacity, không tạo Seat; luôn bảo vệ `held + sold <= capacity`.
- Giá VND dùng `BigDecimal` số nguyên, không âm. Không dùng `double`.
- Mutation dùng Redis idempotency key theo actor/event. DB transaction khóa Event/Zone theo thứ tự ID ổn định.
- Không đổi loại Zone, cấu trúc ghế hoặc capacity sau Event publish; chỉ giá được đổi theo SPEC.
- Sau commit invalidate Event detail, zones, seats và mọi list availability. Không cập nhật cache trước commit.
- Không tự giữ/release Seat trong feature này; Liêm gọi SP giữ vé hiện có.

## Test và nghiệm thu

- Test nhãn ghế, duplicate, seated/standing field mismatch, capacity âm và sửa layout sau publish.
- Concurrency test hai request tạo cùng nhãn/khu chỉ một transaction thành công.
- Manual Swagger create/edit/delete Zone, cache invalidation, 403 organization khác và 409 Event đã publish.
- Kiểm trực tiếp dữ liệu DB sau mỗi case, không chỉ HTTP 200.

