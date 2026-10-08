> **Convention:** `dev` → `feat/vuong/vuong-02-commission-rules` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa Settlement/Commission controller-service-repository-dto-cache, JSP UI-22 và test. Không sửa model/database hoặc Organization approval của Thái.

## Mục tiêu và endpoint

- `GET|POST /api/admin/organizations/{organizationId}/commission-rules`
- `POST /api/admin/commission-rules/{ruleId}/edit`

Quản lý policy hoa hồng bất biến sau khi đã gắn Event.

## Kỹ thuật

- Chỉ ADMIN. Organization approval của Thái chỉ truyền `initialPolicy`; Vương không sửa controller đó.
- Validate rate/fixedFee không âm, effectiveFrom < effectiveTo, VND nguyên. Không tự đặt mức mặc định kinh doanh.
- Mutation dùng `tc:idem:commission:<adminId>:<key>`; DB transaction quyết định create/revise.
- Rule đã được Event tham chiếu không sửa điều khoản; thay đổi tạo rule mới.
- GET cache-aside `tc:commission:rules:v1:<orgId>`; invalidate sau commit và khi approve Organization tạo initial rule.

## Test

- Non-admin, org không tồn tại, thời gian/tiền sai, edit rule đã áp dụng và replay idempotency.
- Manual Swagger create/list/edit, kiểm DB/Redis và Event cũ giữ policy.

