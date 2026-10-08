> **Convention:** `dev` → `feat/vuong/vuong-06-admin-audit-integration` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Vương sở hữu admin dashboard/report/audit, OpenAPI integration, CI/evidence/catalog verification. Không sửa business implementation của người khác.

## Mục tiêu và endpoint

- `GET /api/admin/overview`
- `GET /api/admin/audit-logs`
- `GET /api/admin/audit-logs/{auditId}`

Hoàn thiện UI-18/UI-23, tích hợp OpenAPI fragments, catalog checks và final Swagger run.

## Kỹ thuật

- Dashboard count đọc DB/projection và cache TTL ngắn `tc:admin:overview:v1`; invalidation theo domain event sau commit.
- Audit append-only; filter/time/page bind parameter; detail không chứa password, OTP, raw QR, token, provider secret hoặc connection data.
- Chỉ ADMIN. GET Redis lỗi fallback DB; không dùng cache để quyết định quyền.
- Thu endpoint/schema/example từ từng owner rồi cập nhật `openapi.json`; không sửa controller của họ.
- Catalog verifier read-only xác nhận tables/views/SP/functions/triggers/index/roles canonical.
- CI phải build/test WAR; manual Swagger chạy các luồng chính bằng DB/Redis test, không dùng `TicketCenterticke` để seed/xóa hàng loạt.

## Test và Done

- Admin/non-admin, filter audit, cache scope, secret redaction.
- Swagger smoke cho auth, Event, Hold/Payment/QR, Check-in/Refund, Settlement/Report.
- Evidence ghi commit, response, DB/Redis state đã lọc; GitHub Actions trên `dev` xanh.

