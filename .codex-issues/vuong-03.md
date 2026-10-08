> **Convention:** `dev` → `feat/vuong/vuong-03-settlement-recalculate` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa Settlement recalc/blocker/query/cache/JSP/test. Không sửa Order/Refund implementation.

## Mục tiêu và endpoint

- `GET /api/admin/events/{eventId}/settlement`
- `GET /api/admin/events/{eventId}/settlement-blockers`
- `POST /api/admin/events/{eventId}/settlement/recalculate`

Tính snapshot theo Order và phát hiện blocker từ Payment/Refund DB.

## Kỹ thuật

- SQL Server/View/UDF/F09 là nguồn blocker. Redis cache chỉ cho GET blocker/summary TTL ngắn.
- Recalculate bắt buộc Settlement DRAFT, Event đủ điều kiện và gọi SP14; một Event tối đa một Settlement, một Order một snapshot.
- Công thức gross/refund/commission/net dùng DB và đối chiếu Java fixture; compensation không tính revenue.
- Mutation có idempotency key và lock ngắn theo eventId. Sau commit refresh settlement cache; rollback giữ cache cũ.
- Refund đọc canonical obligation, không dùng legacy attempt table làm tổng tiền.

## Test

- Không Order, partial/full refund, compensation, concurrent recalc, lỗi một snapshot và idempotency replay.
- Manual Swagger summary/blockers/recalc; kiểm snapshot/tổng DB và Redis.

