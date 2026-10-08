> **Convention:** `dev` → `feat/liem/liem-06-order-history-coupon-admin` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Liêm sở hữu UI-06/UI-07/UI-14 và admin Coupon. Không sửa admin Event/Refund/Finance.

## Mục tiêu và endpoint

- `GET /api/me/orders`, `GET /api/orders/{orderId}`
- CRUD/activate/deactivate/delete Coupon theo Organization.
- `GET /api/admin/coupons`, `GET|POST /api/admin/organizations/{organizationId}/coupons`

## Cách làm kỹ thuật

- Order/Ticket query luôn kiểm owner; admin Coupon kiểm ADMIN, Manager chỉ đúng Organization.
- GET dùng cache-aside với key chứa actor scope, filter và page. Không dùng cache public cho dữ liệu đơn cá nhân.
- Mutation Coupon bắt buộc idempotency key; không hạ maxUses dưới RESERVED+CONSUMED; delete chỉ khi chưa có redemption.
- Sau commit invalidate coupon list/eligibility/order breakdown. Không trả lượt CONSUMED khi Refund.
- DTO thể hiện rõ Payment UNKNOWN, compensation Refund và Order expired; không suy diễn từ cache.

## Test

- Quyền chéo order/org, phân trang, Coupon CRUD/state/quota và cache invalidation.
- Manual Swagger list/detail Order, Coupon manager/admin, 401/403/409, Redis hit/miss và DB state.

