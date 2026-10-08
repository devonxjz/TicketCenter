> **Convention:** `dev` → `feat/vuong/vuong-05-reports-csv-cache` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa `report/**`, JSP UI-17/UI-23, CSV/cache/test. Không sửa repositories nghiệp vụ của người khác.

## Mục tiêu và endpoint

- `GET /api/organizations/{organizationId}/reports`
- `GET /api/admin/reports`
- `GET /api/reports/export`

Báo cáo UI và CSV dùng cùng query/formula/phạm vi quyền.

## Kỹ thuật

- Query View/UDF DB bằng bind parameters; Manager chỉ org của mình, Admin có scope toàn hệ thống.
- Cache-aside key gồm actor scope, org/event, metric, from/to, page, sort và version: `tc:report:v1:<scope>:<filterHash>`.
- TTL 30–120 giây. Invalidation sau Payment/Refund/Check-in/Settlement commit qua key version hoặc domain invalidation contract.
- Phân biệt cash collected, ticket revenue, refund, commission, payable; không cộng compensation vào ticket revenue.
- CSV UTF-8 BOM nếu cần Excel; neutralize cell bắt đầu `=`, `+`, `-`, `@`; stream/paginate, không tải toàn DB vào RAM.

## Test

- Quyền chéo org, empty data, filter boundary timezone, UI/CSV equality, cache key isolation và formula injection.
- Manual Swagger JSON và CSV cùng filter; kiểm DB aggregate/Redis, không lộ dữ liệu org khác.

