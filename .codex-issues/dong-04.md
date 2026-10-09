> **Convention:** `dev` → `feat/dong/dong-04-event-cover-cloudinary` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Đông sở hữu package `storage/**`, endpoint upload ảnh sự kiện, JSP upload ảnh bìa (trong UI-12) và test tương ứng. Cấm sửa config chung của người khác, cấm thêm dependency vào `pom.xml`.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng module tải lên và quản lý ảnh bìa sự kiện (Event Cover Image) tích hợp với dịch vụ đám mây Cloudinary:
- Phục vụ trực tiếp việc cập nhật ảnh đại diện sự kiện trong màn hình **UI-12 (Quản lý thông tin sự kiện)**.
- Quản lý (`MANAGER`) của tổ chức có thể tải lên tệp ảnh bìa chất lượng cao. Ảnh được lưu trữ trên Cloudinary CDN, và URL bảo mật được cập nhật vào trường `cover_image_url` trong cơ sở dữ liệu `dbo.tc_events`.
- Kiểm tra nghiêm ngặt an toàn tệp tải lên (File Upload Security): Xác thực Magic Bytes thực sự của tệp, chống mã độc đội lốt file ảnh (Polyglot files, fake extension).
- Nguyên tắc bất biến về Transaction & Kết nối mạng: **Không giữ Database Transaction trong lúc gọi HTTP Upload lên Cloudinary**.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. POST `/api/events/{eventId}/cover`
- **Mục đích:** Tải lên tệp ảnh bìa mới cho sự kiện.
- **Quyền hạn:** `MANAGER` của tổ chức sở hữu sự kiện.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: multipart/form-data`.
- **Form Data:**
  - `file`: Tệp dữ liệu nhị phân (Binary image file).
- **Validation Rules:**
  - Định dạng cho phép: JPEG (`image/jpeg`), PNG (`image/png`), WEBP (`image/webp`).
  - Kiểm tra Magic Bytes đầu tệp:
    - JPEG: `FF D8 FF`
    - PNG: `89 50 4E 47 0D 0A 1A 0A`
    - WEBP: `52 49 46 46 ... 57 45 42 50`
  - Dung lượng tối đa: 5MB (5,242,880 bytes).
- **Response Body (200 OK):**
```json
{
  "success": true,
  "eventId": 201,
  "coverImageUrl": "https://res.cloudinary.com/ticketscenter/image/upload/v1728500000/events/cover_201_a9b8c7.webp",
  "message": "Cập nhật ảnh bìa sự kiện thành công."
}
```
- **Lỗi:**
  - `400 Bad Request`: Tệp không hợp lệ, không phải định dạng ảnh được hỗ trợ, hoặc vượt quá 5MB.
  - `403 Forbidden`: Người dùng không phải là Quản lý của tổ chức sở hữu sự kiện.
  - `502 Bad Gateway`: Lỗi kết nối hoặc lỗi dịch vụ từ phía Cloudinary.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Phân tách Rõ rệt Giữa I/O Mạng và Database Transaction
- **Rủi ro cần tránh:** Mở Transaction DB -> Gọi Cloudinary API qua mạng (mất từ 1-5 giây) -> DB Connection bị chiếm dụng dẫn đến cạn kiệt Connection Pool (HikariCP Starvation).
- **Quy trình chuẩn 4 bước:**
  1. **Bước 1 (DB Read-only):** Kiểm tra sự tồn tại của sự kiện, xác thực quyền `MANAGER` của Actor đối với sự kiện. Kiểm tra `Idempotency-Key` trên Redis.
  2. **Bước 2 (In-memory & Network I/O):** Đọc stream tệp, kiểm tra Magic Bytes. Thực hiện upload lên Cloudinary qua HTTPS. Nhận về URL ảnh an toàn và `public_id`.
  3. **Bước 3 (DB Transaction):** Mở transaction nhanh, cập nhật `cover_image_url = newUrl` trong `dbo.tc_events`, commit transaction (thời gian chiếm dụng < 5ms).
  4. **Bước 4 (Post-Commit Cache Invalidation):** Xóa cache chi tiết sự kiện `tc:event:detail:v1:<eventId>` và cache danh mục `tc:event:list:v1:*`.
- **Xử lý tình huống Orphan Image (Ảnh mồ côi):**
  - Nếu Cloudinary upload thành công nhưng DB Transaction ở Bước 3 bị rollback do lỗi hệ thống: Ghi nhận log cảnh báo kèm `public_id` để tiến hành dọn dẹp (cleanup) sau, đảm bảo ảnh cũ trong DB vẫn còn nguyên vẹn và không bị đứt gãy liên kết.

### 3.2. Idempotency mà không Lưu Dữ liệu Nhị phân trên Redis
- **Tuyệt đối không lưu:** Raw bytes của tệp ảnh vào Redis (gây phình to bộ nhớ cache).
- **Cách xử lý:**
  - Tính mã băm SHA-256 từ nội dung byte của ảnh: `fileHash = sha256(fileBytes)`.
  - Redis Key: `tc:idem:cover:<eventId>:<idempotencyKey>`.
  - Lưu trữ payload: `{ "status": "COMPLETED", "fileHash": fileHash, "coverUrl": newUrl }` (TTL: 24h).
  - Khi client gửi lại cùng `Idempotency-Key` và cùng `fileHash`, trả về ngay URL đã tạo trước đó.

### 3.3. Bảo mật Thông tin Cấu hình Cloudinary
- Khóa bảo mật `CLOUDINARY_API_SECRET`, `CLOUDINARY_API_KEY`, `CLOUDINARY_CLOUD_NAME` chỉ được đọc từ biến môi trường (`.env.runtime`).
- Không bao giờ in Secret ra file log hoặc trả về client.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - Adapters: `StorageService` (interface), `CloudinaryStorageService` (triển khai gọi Cloudinary SDK/HTTP API), `FakeStorageService` (mock phục vụ unit test).
   - Validator: `ImageContentValidator` (kiểm tra kích thước, đọc Magic Bytes từ InputStream).
   - DTOs: `CoverUploadResponseDto`.
   - Core Services: `EventCoverService`.
   - Servlets: `EventCoverUploadServlet` (hỗ trợ multipart form data qua `@MultipartConfig`).
2. **Giao diện JSP:**
   - Phần tích hợp Upload ảnh trong `src/main/webapp/WEB-INF/views/event/organizer/event-form.jsp` (khung kéo thả ảnh, xem trước thumbnail trước khi tải lên, thanh tiến trình upload).
3. **Database Changes:**
   - Cột `cover_image_url` trong `dbo.tc_events` được cập nhật đường dẫn HTTPS mới.
4. **Kiểm thử tự động:**
   - `ImageContentValidatorTest`: Kiểm tra tệp PNG/JPEG thật hợp lệ, tệp text đổi đuôi .jpg bị từ chối, tệp vượt quá 5MB bị từ chối.
   - `EventCoverServiceTest`: Kiểm tra luồng upload thành công, xử lý lỗi khi storage provider timeout.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Triển khai `ImageContentValidator`**
  - Đọc 8 bytes đầu tiên của InputStream để kiểm tra chữ ký Magic Number:
    ```java
    byte[] header = new byte[8];
    inputStream.read(header);
    // Đối chiếu với mẫu JPEG, PNG, WEBP
    ```
- **Bước 2: Xây dựng `StorageService` và `CloudinaryStorageService`**
  - Khởi tạo client Cloudinary từ cấu hình hệ thống.
  - Viết hàm `upload(String folder, String fileName, InputStream stream)`.
- **Bước 3: Xây dựng `EventCoverService`**
  - Xác thực quyền sở hữu sự kiện của Actor.
  - Phối hợp validator -> upload storage -> cập nhật URL vào DB -> xóa cache Redis.
- **Bước 4: Xây dựng `EventCoverUploadServlet`**
  - Sử dụng `@MultipartConfig(maxFileSize = 5242880, maxRequestSize = 6291456)`.
  - Trích xuất Part `file`, kiểm tra CSRF token và Idempotency-Key.
- **Bước 5: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% ca test đều pass.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| IMG-01 | Upload ảnh JPEG hợp lệ | Tệp JPEG 2MB, quyền Manager | 200 OK, DB lưu URL mới, cache cũ bị xóa |
| IMG-02 | File giả mạo định dạng (Fake Extension) | Tệp shell script đổi đuôi thành image.jpg | 400 Bad Request ("Định dạng tệp không hợp lệ") |
| IMG-03 | File vượt dung lượng | Tệp ảnh 6.5MB | 400 Bad Request ("Dung lượng tệp vượt quá giới hạn 5MB") |
| IMG-04 | Quản lý tổ chức khác upload | Actor không quản lý Event | 403 Forbidden |
| IMG-05 | Lỗi Cloudinary | Giả lập Cloudinary timeout | 502 Bad Gateway, DB giữ nguyên ảnh cũ |
