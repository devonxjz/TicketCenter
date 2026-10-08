> **Convention:** `dev` → `feat/liem/liem-02-order-coupon` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa `order/**`, DTO/cache/JSP checkout và test. Không sửa Event, Refund, Settlement, model/config/database/OpenAPI.

## Mục tiêu và endpoint

- `POST /api/orders`
- `POST /api/orders/{orderId}/coupon`
- `GET /api/orders/{orderId}/coupon-eligibility`

Tạo một Order từ Hold, snapshot OrderItem và quản lý Coupon/Redemption nguyên tử.

## Cách làm kỹ thuật

- `Idempotency-Key` cho tạo Order và áp/bỏ Coupon. Key Redis chứa actor, operation, order/hold và request hash.
- Mỗi Hold tối đa một Order; timeout sau commit phải tra Order theo holdId và trả kết quả cũ.
- Coupon kiểm Organization, thời hạn, active, quota và trần giảm 30% trong transaction DB; khóa Coupon trước khi RESERVED.
- Tiền là `BigDecimal` VND nguyên. Subtotal/discount/total do server và UDF/SP hiện có tính; client không gửi số tiền quyết định.
- Cache `tc:order:summary:<orderId>`, `tc:order:coupon-eligibility:<orderId>:<codeHash>`; TTL không vượt expiresAt Hold.
- Sau mutation commit refresh summary, invalidate eligibility và coupon usage; rollback giữ cache cũ.

## Test

- Hold hết hạn, owner sai, Order lặp, Coupon sai org/hết hạn/maxUses, fixed/percentage cap 30%.
- Concurrency hai Order tranh Coupon maxUses=1.
- Manual Swagger tạo Order, apply/remove Coupon, replay idempotency; đối chiếu DB/Redis và breakdown.

