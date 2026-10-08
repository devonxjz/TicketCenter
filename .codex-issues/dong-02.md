> **Convention:** `dev` → `feat/dong/dong-02-event-draft-lifecycle` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Chỉ sửa code Event draft, JSP organizer Event và test tương ứng. Cấm sửa model/config/database/OpenAPI và package của người khác.

## Mục tiêu

CRUD Event nháp, cấu hình lịch và gửi duyệt cho UI-12/UI-13.

## Endpoint

- `GET /api/organizations/{organizationId}/events`
- `GET /api/organizations/{organizationId}/events/{eventId}`
- `POST /api/organizations/{organizationId}/events`
- `POST /api/events/{eventId}/edit`
- `POST /api/events/{eventId}/delete`
- `POST /api/events/{eventId}/submit`

## Cách làm kỹ thuật

- Quyền Manager lấy từ hợp đồng current-user/membership của Thái; không tin organizationId từ client.
- Validation server-side: `saleStart < saleEnd <= startTime < endTime`, trường bắt buộc, UTC `Instant`, không nhận status/owner từ payload.
- Mutation bắt buộc `Idempotency-Key`. Redis key `tc:idem:event:<actorId>:<key>` lưu request hash, trạng thái `PROCESSING|COMPLETED` và response tối thiểu.
- DB transaction tạo/sửa/xóa/gửi duyệt là nguồn quyết định. Chỉ xóa vật lý Event DRAFT chưa có giao dịch và FK cho phép.
- Sau commit xóa `tc:event:list:*`, `tc:event:detail:<id>`, `tc:org:events:<orgId>:*`; nếu rollback không đụng cache.
- Event `PENDING_APPROVAL` không sửa; Event `REJECTED` được sửa và gửi lại; Event `PUBLISHED` chỉ sửa trường SPEC cho phép.

## Test và nghiệm thu

- Test quyền chéo Organization, lịch sai, trạng thái sai và delete bị FK chặn.
- Test cùng idempotency key/payload không tạo hai Event; key trùng payload khác trả 409.
- Manual Swagger đủ create/edit/delete/submit, 401/403/409 và kiểm DB/cache sau commit.
- UI có loading/empty/validation; không viết SQL hoặc Java scriptlet trong JSP.

