# Bản đồ API và trang JSP cho 24 màn hình

Bộ nhiệm vụ năm thành viên xây cả giao diện lẫn backend. Đây là danh sách route **dự kiến cần hiện thực** để chứng minh backend bao phủ luồng của [spec.md](../../spec.md) §9 và [diagram chuẩn](../classdiagram/diagram.md). Route request/payout chỉ là tên nghiệp vụ HTTP, không tạo class mới. [TEAM-CONTRACT](TEAM-CONTRACT.md) và file thành viên chốt chủ Servlet/Service/DTO, [GIT-WORKFLOW](GIT-WORKFLOW.md) chốt nhánh/commit/PR. Task DNN trong bảng chỉ truy vết backend cũ. M0 chốt hợp đồng tại `docs/backend/api-contract.md`; mỗi ngày triển khai bổ sung request/response/status code/test case theo [CONVENTIONS](CONVENTIONS.md).

Khi một ô ghi `POST /auth/register,/auth/login`, hiểu là các endpoint POST riêng. ID trong URL là đầu vào không đáng tin, luôn đối chiếu owner/org từ DB. Nội dung này chốt phạm vi, chưa phải OpenAPI sinh từ ứng dụng đang chạy.

Representation: GET/events và /events/{id} mặc định JSP, explicit Accept application/json trả cùng DTO/quyền. GET có trang dùng cùng cơ chế; /me/hold, coupon-eligibility, payment-status, check-in-window/history và refund attempts chỉ JSON. GET /check-ins là trang HTML UI-15/UI-16; GET /organizations/{id}/check-in-events mặc định HTML UI-15, explicit Accept application/json trả Page<EventDto>. Return HTML, IPN protocol, QR image, export CSV. GET/orders/{id} render checkout khi PENDING_PAYMENT/detail khi đã chốt. Servlet wildcard owners và JSP forward paths theo TEAM; không mỗi endpoint một Servlet trùng mapping.

Đồng bộ hợp đồng ngày 07/10/2026 theo [TEAM-CONTRACT §3.5](TEAM-CONTRACT.md#35-hợp-đồng-http-dùng-chung-cho-duyệt-tổ-chức-và-check-in): POST /admin/organization-requests/{id}/approve dùng trường JSON `initialPolicy` chứa ratePercent/fixedFee/effectiveFrom/effectiveTo, gọi OrganizationService.approve của Đông qua AdminServlet Vương. Tên tham số logic SP01 `initialCommissionPolicy` không dùng làm tên trường JSON.

| UI | Nghiệp vụ | Endpoint dự kiến | Đầu vào/đầu ra chính | Quyền/bất biến | Task backend cũ | Người/task hiện tại |
|---|---|---|---|---|---|---|
| UI-01 | Danh sách/tìm kiếm | GET /events | keyword, categoryId, from/to, sort, page/pageSize → event cards/minPrice/status; V01 | Public chỉ PUBLISHED, filter bind/order allowlist | D07-T04 | Đông — DONG-10 |
| UI-02 | Event/khu/giữ vé | GET /events/{id}; GET /events/{id}/zones; GET /zones/{id}/seats; POST /holds | Event/Zone availability qua V01/V02/F03; hold nhận eventId + selections(zoneId,seatId?,quantity) | Giữ cần ACTIVE+verified; 1–8 vé cùng Event, server price/time | D07-T04; D08-T02/T03 | Đông — DONG-10; Liêm LIEM-04 giữ vé |
| UI-03 | Auth/OTP/reset | GET /auth/csrf; POST /auth/register,/auth/login,/auth/logout,/auth/otp/send,/auth/otp/verify,/auth/password/reset; POST /auth/password/forgot | Register: userName/fullName/email/password; login thường email/password, User trả userName; OTP/purpose → verification/reset grant; forgot dùng phản hồi hạn chế dò account | CSRF phù hợp từng phiên; one-use OTP/reset, rate limit, không trả secret | D04-T01…T04; D05-T01/T02/T04 | Khánh — KHANH-12 |
| UI-04 | Hold/Order/coupon/pay | GET /me/hold; POST /holds/{id}/cancel; POST /orders; POST /orders/{id}/coupon; POST /orders/{id}/payments; GET /orders/{id}/coupon-eligibility | holdId → Order; couponCode hoặc null → breakdown; pay → URL hoặc completed zero; preview F08 | Owner, Hold còn hạn, không đổi coupon khi payment PENDING/UNKNOWN | D08-T03; D09-T01…T03; D10-T03; D11-T02 | Liêm — LIEM-14 |
| UI-05 | Trạng thái payment | GET /payments/vnpay/return; GET /orders/{id}/payment-status; GET /payments/vnpay/ipn | Return/status → confirmed/pending/failed/compensation; IPN → protocol response | Return không mutate; status owner; IPN signature verified, principal technical | D10-T02/T03; D11-T02/T04; D12-T02 | Liêm — LIEM-09/14 |
| UI-06 | Lịch sử/chi tiết đơn | GET /me/orders; GET /orders/{id} | status/time/page → Order + tickets/refund/payment states; V03 sau D17 | Owner mọi lần, cancelled/expired vẫn có lịch sử | D09-T03/T04; D11-T03; D14-T03; D17-T01 | Liêm — LIEM-13 |
| UI-07 | Vé/QR/chia sẻ | GET /me/tickets; GET /tickets/{id}; GET /tickets/{id}/qr | V06 detail + endpoint ảnh QR riêng; chia sẻ ảnh tải được | Owner trước đọc QR; không token chia sẻ mới, không lộ cả đơn | D11-T03 | Liêm — LIEM-13 |
| UI-08 | Yêu cầu hoàn | GET /orders/{id}/refundable-tickets; POST /refund-requests; GET /me/refund-requests; GET /refund-requests/{id}; GET /refund-requests/{id}/attempts | orderId,ticketIds,reason → refundId/amount; F04/V07 một Refund, currentAttemptId và lịch sử RefundTransferLog, kể cả 0đ | Owner, cùng Order, trước startTime, chưa dùng/chưa open request | D14-T01/T03; D15-T03 | Thái — THAI-14; Liêm OrderServlet |
| UI-09 | Yêu cầu tạo tổ chức | GET/POST /organization-requests; GET /me/organization-requests | name/contact/description → organizationId/PENDING_APPROVAL trên cùng Organization; đọc lý do reject | Logged-in, applicant session; không cần vai trò mua đã verify ngoài SPEC | D06-T01 | Đông — DONG-03 |
| UI-10 | Ngữ cảnh/tổng quan org | GET /me/memberships; GET /organizations/{id}; GET /organizations/{id}/overview | V10 memberships hiện tại + chỉ số org được phép | OrganizationId chỉ ngữ cảnh; backend kiểm manager | D06-T03/T04; D18-T03 | Đông — DONG-11; Vương ReportService |
| UI-11 | Thành viên | GET /organizations/{id}/members; POST /organizations/{id}/members; POST /organizations/{id}/members/{userId}/role; POST /organizations/{id}/members/{userId}/deactivate; POST /organizations/{id}/members/{userId}/activate | userName tài khoản có sẵn/role → User.organizationRoles qua UserOrganizationRole; V10 | Manager đúng org; khóa org, TR06 giữ manager cuối; không cấp ADMIN | D06-T03 | Đông — DONG-05 |
| UI-12 | Danh sách nội bộ Event | GET /organizations/{id}/events; GET /organizations/{id}/events/{eventId} | status/page → drafts/rejected/published; rejection reason audit | Manager đúng org; không dùng public V01 để đọc draft | D07-T01/T04 | Đông — DONG-06 |
| UI-13 | CRUD Event/khu/ảnh | POST /organizations/{id}/events; POST /events/{id}/edit,/events/{id}/delete,/events/{id}/submit; POST /events/{id}/zones; POST /zones/{id}/edit,/zones/{id}/delete; POST /events/{id}/cover | Event schedule/detail + zone seated(rows,seatsPerRow)/standing(capacity); image multipart | Manager; delete thật chỉ draft không giao dịch; khóa layout sau publish | D07-T01/T02/T03 | Đông — DONG-07/08/09 |
| UI-14 | Coupon org/admin | GET/POST /organizations/{id}/coupons; POST /coupons/{id}/edit,/coupons/{id}/deactivate,/coupons/{id}/activate,/coupons/{id}/delete; GET /admin/coupons; GET/POST /admin/organizations/{id}/coupons | code/type/value/validity/maxUses → config + V09 usage; admin filter organizationId | Manager own org hoặc admin; delete chỉ chưa redemption; cap30% | D09-T02/T03 | Liêm — LIEM-06/14; Vương chọn org admin |
| UI-15 | Chọn Event check-in | GET /check-ins?organizationId=...&eventId=...; GET /organizations/{id}/check-in-events; GET /events/{id}/check-in-window | Event/time/window via F07; không dữ liệu revenue | Active manager/check-in cùng org; thời gian/cancelled được giải thích | D07-T04; D13-T02 | Thái — THAI-02/05 |
| UI-16 | Quét/history | POST /check-ins; GET /events/{id}/check-ins | eventId,ticketCode → CheckInResult; V05 history | Membership/time/status, một success/ticket, không raw QR trong history | D13-T01…T04 | Thái — THAI-03/04/05 |
| UI-17 | Report org/CSV | GET /organizations/{id}/reports; GET /reports/export | ReportFilter(metric,eventId,from,to,page) → tiền/vé/kho/check-in; V02–V05/V08/F05/F10 | Manager đúng org; cohort khác cashflow; JSON/CSV cùng query | D17-T01/T03; D18-T01/T02 | Vương — VUONG-09/10 |
| UI-18 | Tổng quan admin | GET /admin/overview | Số pending org/Event/refund/settlement; JSP cards dùng dữ liệu thật | Admin hiện hành, query phân trang/detail tương ứng | D18-T03 | Vương — VUONG-11 |
| UI-19 | Duyệt tổ chức | GET /admin/organization-requests; GET /admin/organization-requests/{id}; POST /admin/organization-requests/{id}/approve; POST /admin/organization-requests/{id}/reject | ID route = organizationId hiện hữu; approve chọn initial policy quản trị, cùng record APPROVED; reject reason → REJECTED | Admin, SP01; repeated approve same org | D06-T01/T02 | Vương — VUONG-12; Đông DONG-04 |
| UI-20 | Duyệt/hủy Event | GET /admin/events; GET /admin/events/{id}; POST /admin/events/{id}/publish; POST /admin/events/{id}/reject; POST /admin/events/{id}/cancel; GET /admin/events/{id}/cancellation-progress | publish commissionRuleId; reject reason; cancel → progress/exceptions | Admin; SP12/SP13; cancellation processed by worker SP17 | D07-T03; D16-T01…T04 | Vương — VUONG-12; Đông DONG-09; Thái THAI-12/13 |
| UI-21 | Duyệt/vận hành hoàn | GET /admin/refund-requests; GET /admin/refund-requests/{id}; POST /admin/refund-requests/{id}/decision; POST /admin/refund-requests/{id}/retry; POST /admin/payments/{id}/compensation/retry | ID route = refundId; decision APPROVE/REJECT + rejectionReason; backend sinh attemptId, retry explicit chỉ Refund RETRYABLE sau FAILED đã xác minh | Admin, SP10/SP09; no browser SUCCEEDED setter; SP11 worker only | D14-T02/T03; D15-T03 | Vương — VUONG-12; Thái THAI-08/11 |
| UI-22 | Rule/Settlement/chi trả | GET/POST /admin/organizations/{id}/commission-rules; POST /admin/commission-rules/{id}/edit; GET /admin/events/{id}/settlement; GET /admin/events/{id}/settlement-blockers; POST /admin/events/{id}/settlement/recalculate; POST /admin/settlements/{id}/confirm; POST /admin/settlements/{id}/payouts; GET /admin/settlements/{id}/payouts | Rule terms; Settlement gross/refund/commission/paid/pending + snapshot kỹ thuật; payoutId/amount/reference → log và backend simulated result; F02/F09/V08 | Admin; TR03 protects applied terms; SP14–SP16; pending reserves balance | D06-T04; D07-T03; D17-T01…T04 | Vương — VUONG-13 |
| UI-23 | Report/audit toàn hệ thống | GET /admin/reports; GET /reports/export; GET /admin/audit-logs; GET /admin/audit-logs/{id} | Filter org/Event/date/action/object → scoped metrics/audit detail | Admin; CSV formula guard; TR10 append-only | D18-T01…T03 | Vương — VUONG-09/10/11 |
| UI-24 | Profile | GET /me/profile; GET /me/memberships; POST /auth/otp/send | userName/fullName/email/verified/platformRole/organizationRoles hiện tại; OTP verify đã có | User của session, không trả passwordHash/auth secret | D06-T03/T04; D18-T03 | Khánh — KHANH-13 |

## Job và endpoint kỹ thuật ngoài UI

| Đường chạy | Việc cần làm | Task |
|---|---|---|
| GET /health/live; GET /health/ready | Liveness/readiness nhẹ, không secret; readiness DB timeout hữu hạn | D01-T02, D03-T04, D20-T02 |
| HoldExpiryJob | SP07 từng batch; không thay kiểm expiry trong request | D12-T02 |
| PaymentReconciliationJob | Query gateway → verified result → SP09, xử lý UNKNOWN/late | D12-T02 |
| OutboxWorker/EmailJob | Claim lease, retry bounded, email sau commit | D12-T01/T03 |
| RefundJob | Refund APPROVED chưa attempt khởi tạo qua SP09 bù trừ; PROCESSING/NEEDS_RECONCILIATION tra currentAttemptId + RefundTransferLog → submit/query → SP11; không tự bật retry FAILED | D15-T03 |
| EventCancellationJob | SP07 Hold chưa Order + SP17 từng Order → SP10; refund sau commit | D16-T03 |
| ImageCleanupJob | Xóa ảnh không còn tham chiếu sau grace period; storage boundary đã kiểm quyền | D12-T03 |
| EventCategory | Seed danh mục và GET /event-categories cho bộ lọc/form; không yêu cầu CRUD danh mục mới | D02-T03, D07-T04 |

## Ví dụ hợp đồng có thể kiểm tra

```http
POST /holds
Content-Type: application/json
X-CSRF-Token: <token của phiên kiểm thử, không lưu vào Git>

{"eventId":"10000000-0000-0000-0000-000000000001","selections":[{"zoneId":"20000000-0000-0000-0000-000000000001","seatId":"30000000-0000-0000-0000-000000000001","quantity":1},{"zoneId":"20000000-0000-0000-0000-000000000002","quantity":2}]}
```

Fixture test tạo các UUID trên hoặc cập nhật ví dụ về fixture thống nhất. Expected success: một Hold ACTIVE, quantity tổng 3, đúng Event; `expiresAt-createdAt=10 phút`; price từ server; một Seat HELD và standingHeld tăng2. Expected conflict khi thiếu một chỗ: không có Hold mới, mọi kho giữ nguyên. Test client lấy CSRF/session runtime, không copy token sống vào tài liệu.

```json
{"data":{"id":"40000000-0000-0000-0000-000000000001","status":"ACTIVE","serverNow":"2026-10-06T03:00:00Z","expiresAt":"2026-10-06T03:10:00Z"}}
```

Response thực còn có items/giá snapshot theo task. Đối với đổi trạng thái, kiểm cả dữ liệu SQL và quyền chứ không chỉ HTTP200. Không tạo route `/mark-paid`, `/mark-refunded` hay `/mark-payout-success` cho browser.
