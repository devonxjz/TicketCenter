> **Convention:** `dev` → `feat/vuong/vuong-01-swagger-redis-contract` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Vương là người duy nhất sửa `src/main/resources/openapi.json`, `src/main/webapp/swagger/**` và tài liệu key registry. Không sửa implementation feature của người khác.

## Mục tiêu

Biến `/swagger/` thành công cụ manual test dùng được và khóa contract Redis/OpenAPI cho cả nhóm.

## Cách làm kỹ thuật

- Tạo `src/main/webapp/swagger/index.html` trỏ đến `/api/openapi.json`; pin phiên bản Swagger UI. Có thể vendor static assets hoặc dùng CDN được duyệt, không tự thêm Maven dependency.
- Khai báo security scheme session cookie + `X-CSRF-Token`, header `Idempotency-Key`, error envelope và các schema tiền/UUID/Instant.
- Mỗi domain owner cung cấp endpoint/schema/example; chỉ Vương merge vào `openapi.json` để tránh conflict.
- Lập registry Redis key: prefix `tc:<domain>:<purpose>:v<version>:...`, TTL, dữ liệu lưu, owner và invalidation event.
- Quy định GET cache-aside, mutation idempotency request hash, pending/completed response, same-key-different-payload=409.
- Không đưa secret, password, OTP, cookie, raw QR, VNPAY secret hoặc credential vào example/log/cache.

## Manual test template bắt buộc

1. Mở `/swagger/`, lấy CSRF/login bằng account test.
2. Execute success, validation, 401, 403, 409/replay.
3. Kiểm SQL Server row/status và Redis key/TTL đã lọc.
4. Lưu evidence không chứa token/secret.

## Test và Done

- Smoke test `/swagger/` 200 và tải được `/api/openapi.json` hợp lệ.
- OpenAPI validator không có duplicate path/operationId.
- Redis registry không có hai owner dùng cùng prefix.

