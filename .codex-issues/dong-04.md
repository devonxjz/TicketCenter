> **Convention:** `dev` → `feat/dong/dong-04-event-cover-cloudinary` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Đông sở hữu `storage/**`, endpoint ảnh Event, JSP upload và test. Không sửa Cloudinary config hoặc thêm dependency.

## Mục tiêu

Upload và thay ảnh bìa Event qua Cloudinary, giữ DB là nguồn URL đang được sử dụng.

## Endpoint

- `POST /api/events/{eventId}/cover`
- `GET /api/events/{eventId}/image` nếu cần proxy/redirect theo contract hiện có.

## Cách làm kỹ thuật

- Kiểm tra quyền Manager và Event thuộc Organization trước upload.
- Kiểm kích thước và magic bytes bằng `ImageContentValidator`; không tin extension hoặc Content-Type client.
- `Idempotency-Key` bắt buộc. Redis lưu request hash gồm eventId, actorId và SHA-256 nội dung, không lưu bytes ảnh.
- Không giữ DB transaction khi gọi Cloudinary. Quy trình: xác thực DB → upload → transaction cập nhật URL/publicId → commit → invalidate cache Event.
- Nếu upload thành công nhưng DB rollback, ghi nhận orphan để cleanup an toàn; không xóa ảnh cũ trước khi URL mới commit.
- Không trả API secret/public credential về browser hoặc log.

## Test và nghiệm thu

- Fake StorageService cho unit test; test file giả extension, quá dung lượng, lỗi provider, DB rollback và retry cùng key.
- Manual Swagger multipart: ảnh hợp lệ, sai loại, quá lớn, 403, retry; kiểm DB URL và Redis metadata.
- Xác nhận ảnh cũ vẫn dùng nếu cập nhật DB thất bại.

