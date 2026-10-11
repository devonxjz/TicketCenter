# VUONG-01 — Đối chiếu OpenAPI/Redis, 11/10/2026

Issue [#28](https://github.com/devonxjz/TicketCenter/issues/28). Baseline dev `28ce056b97d778edcdf99b91e856c017b5c35e7b`. Đây là bàn giao hợp đồng đã khớp source, không đóng #28/#33 hoặc thay nghiệm thu E2E toàn sàn.

## Phần đã đối chiếu

- KHANH-01 đã có hai API đọc nhưng spec còn planned: `/api/check-ins/events` trả `data` là mảng; `/api/events/{eventId}/check-in-window` trả DTO. Đối chiếu servlet, filter/named dispatcher, repository/service, DTO và [bằng chứng owner](../khanh/KHANH-01-completion.md). Chỉ hai thao tác này chuyển implemented/matched-to-source; quét QR và refund vẫn planned.
- UUID, UTC, counter int64, lỗi cố định và membership ACTIVE MANAGER/CHECK_IN_STAFF khớp source. ADMIN không tự bypass membership. Selection không phân trang, organizationId tùy chọn; endTime từ đầu ngày UTC hiện tại, thứ tự startTime/eventId. Window PUBLISHED `[start−60 phút,end)`, CANCELLED trả400, thiếu Event404, membership sai403.
- Cache check-in hiện có TTL60/30, key scope actor/org hoặc event; SQL guard/UDF vẫn kiểm hiện trạng. Invalidation hook có nhưng writer Event phải gọi sau commit. Chỉ cập nhật registry; không sửa logic owner/model/pom/persistence/database.
- Đối chiếu hiện trạng các route đã bật theo nhóm: Health (HealthServlet/ReadinessServlet), Identity (các servlet auth/me), Event/images (EventApiServlet), Finance/report/audit/overview (các controller Vương). Kiểm thử hợp đồng có sẵn tiếp tục giữ DTO/quyền/schema của các nhóm này.

## Tồn kho thao tác trong spec hiện tại

103 thao tác: **40 implemented, 60 planned, 3 legacy**. Bảng dùng `x-owner` hiện có, không tự đổi người phụ trách theo tên miền: tám API Event/images hiện mang owner liem theo PR48; 13 hợp đồng Event còn planned mang dong. implemented là khả dụng theo source, không đồng nghĩa owner acceptance/E2E đã đạt.

| x-owner | implemented | planned | legacy |
|---|---:|---:|---:|
| platform | 2 | 0 | 0 |
| thai | 9 | 14 | 0 |
| vuong | 19 | 1 | 0 |
| dong | 0 | 13 | 0 |
| liem | 8 | 19 | 2 |
| khanh | 2 | 13 | 1 |

## Những phần cần ghép với owner

| Owner | Việc còn cần bàn giao | Điều kiện đóng phần tương ứng |
|---|---|---|
| Thái | Profile/organization/membership API; khác biệt auth key180s so với đề xuất86400s | Route/DTO/error/quyền khớp backend; chốt key/TTL và chứng minh revoke/reset/session; hook InitialPolicy/User/Organization sau commit |
| Đông | Các API Event/zone/seat/admin chưa có contract khớp; cache catalog/detail/inventory và hook check-in | Source/route thực tế, TTL/scope, publish/cancel/schedule/inventory cập nhật cache sau commit; giữ tương thích PR48 |
| Liêm | Hold/order/payment/ticket/coupon; identity dedupe IPN; chính sách QR | Backend routes/DTO/evidence; IPN chữ ký + provider identity/DB dedupe; không tự bật cache raw QR khi hợp đồng còn mâu thuẫn; thông báo finance/report sau commit |
| Khánh | Scan/refund/cancellation/worker; phối hợp QR; hook refund và KPI | Routes và state transitions có bằng chứng SQL/Redis/HTTP; trạng thái tiền/refund và quyền hiện tại; hook sau commit |
| Vương | Tổng quan tổ chức vẫn planned; nghiệm thu hợp đồng và E2E tổng hợp | Giữ scope ADMIN đã giao; ghép dữ liệu từ owner vào môi trường test đầy đủ, không dùng session bridge/provider mô phỏng làm bằng chứng toàn sàn |

Owner API mới cần method/path, input/output, quyền, status/error, idempotency và invalidation. API vẫn planned khi thiếu đối chiếu; sự tồn tại model/service không tự chứng minh HTTP route. Không sửa implementation owner để làm spec có vẻ hoàn tất.

## Danh sách 60 thao tác còn planned

| x-owner | Method | Path |
|---|---|---|
| dong | GET | `/api/admin/events` |
| dong | GET | `/api/admin/events/{eventId}` |
| dong | POST | `/api/admin/events/{eventId}/publish` |
| dong | POST | `/api/admin/events/{eventId}/reject` |
| dong | GET | `/api/event-categories` |
| dong | POST | `/api/events/{eventId}/delete` |
| dong | POST | `/api/events/{eventId}/submit` |
| dong | GET | `/api/events/{eventId}/zones` |
| dong | POST | `/api/events/{eventId}/zones` |
| dong | GET | `/api/organizations/{organizationId}/events` |
| dong | POST | `/api/zones/{zoneId}/delete` |
| dong | POST | `/api/zones/{zoneId}/price` |
| dong | GET | `/api/zones/{zoneId}/seats` |
| khanh | POST | `/api/admin/events/{eventId}/cancel` |
| khanh | GET | `/api/admin/events/{eventId}/cancellation-progress` |
| khanh | POST | `/api/admin/payments/{paymentId}/compensation/retry` |
| khanh | GET | `/api/admin/refund-requests` |
| khanh | GET | `/api/admin/refund-requests/{refundId}` |
| khanh | POST | `/api/admin/refund-requests/{refundId}/decision` |
| khanh | POST | `/api/admin/refund-requests/{refundId}/retry` |
| khanh | POST | `/api/check-ins` |
| khanh | GET | `/api/events/{eventId}/check-ins` |
| khanh | GET | `/api/me/refund-requests` |
| khanh | GET | `/api/orders/{orderId}/refundable-tickets` |
| khanh | POST | `/api/refund-requests` |
| khanh | GET | `/api/refund-requests/{refundId}` |
| liem | POST | `/api/coupons/{couponId}/activate` |
| liem | POST | `/api/coupons/{couponId}/deactivate` |
| liem | POST | `/api/holds` |
| liem | POST | `/api/holds/{holdId}/cancel` |
| liem | GET | `/api/me/hold` |
| liem | GET | `/api/me/orders` |
| liem | GET | `/api/me/tickets` |
| liem | POST | `/api/orders` |
| liem | GET | `/api/orders/{orderId}` |
| liem | POST | `/api/orders/{orderId}/coupon` |
| liem | GET | `/api/orders/{orderId}/coupon-eligibility` |
| liem | GET | `/api/orders/{orderId}/payment-status` |
| liem | POST | `/api/orders/{orderId}/payments` |
| liem | GET | `/api/organizations/{organizationId}/coupons` |
| liem | POST | `/api/organizations/{organizationId}/coupons` |
| liem | GET | `/api/payments/vnpay/ipn` |
| liem | GET | `/api/payments/vnpay/return` |
| liem | GET | `/api/tickets/{ticketId}` |
| liem | GET | `/api/tickets/{ticketId}/qr` |
| thai | GET | `/api/admin/organization-requests` |
| thai | POST | `/api/admin/organization-requests/{organizationId}/approve` |
| thai | POST | `/api/admin/organization-requests/{organizationId}/reject` |
| thai | GET | `/api/me/memberships` |
| thai | GET | `/api/me/organization-requests` |
| thai | GET | `/api/me/profile` |
| thai | POST | `/api/me/profile` |
| thai | POST | `/api/organization-requests` |
| thai | GET | `/api/organization-requests/{organizationId}` |
| thai | GET | `/api/organizations/{organizationId}/members` |
| thai | POST | `/api/organizations/{organizationId}/members` |
| thai | POST | `/api/organizations/{organizationId}/members/{userId}/activate` |
| thai | POST | `/api/organizations/{organizationId}/members/{userId}/deactivate` |
| thai | POST | `/api/organizations/{organizationId}/members/{userId}/role` |
| vuong | GET | `/api/organizations/{organizationId}/overview` |

## Bằng chứng đợt đối chiếu này

- Ba ca hợp đồng mới RED (planned và thiếu content/schema) trước cập nhật; sau đó nhóm contract/controller/service/cache48/48 GREEN, zero skips.
- Hai ca HTTP mới RED404 khi fixture chưa map route; sau wiring, ROOT/WAR Swagger/check-in HTTP12/12 GREEN, zero skips. Hai route thực sự tới owner servlet/filter và trả401 JSON/no-store khi thiếu phiên; không khởi tạo DB/SMTP/worker.
- Fresh clean package: Java389 discovered,330 executed,59 named external guarded skips, zero failures/errors. Safe Node56/56; finance/report/dashboard/audit actual HTTP/JDBC/JSP18/18 zero skips với marked loopback fixture, xác nhận phần wiring chung không làm hỏng hồi quy.
- Review và exact-head/post-merge CI: kết quả cuối sẽ được ghi trong PR metadata. Không tính external skipped thành pass; owner SQL/Redis/SMTP evidence ngày10/10 là bằng chứng lịch sử, không phải lượt chạy mới của Vương.

