> **Convention:** `dev` → `feat/dong/dong-02-event-draft-lifecycle` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Chỉ sửa code Event draft, controller/service/repository trong `event/**`, JSP organizer Event (UI-12/UI-13) và test tương ứng. Cấm sửa model/config/database/OpenAPI và package của người khác.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng toàn bộ vòng đời tạo mới, chỉnh sửa, xóa và nộp duyệt sự kiện của Ban tổ chức:
- Phục vụ trực tiếp màn hình **UI-12 (Tạo & Quản lý sự kiện của Tổ chức)**.
- Quản lý (`MANAGER`) của tổ chức có thể tạo bản thảo sự kiện (`DRAFT`), tinh chỉnh thông tin (tiêu đề, địa điểm, thể loại, mô tả), thiết lập lịch trình (thời gian mở bán, kết thúc bán, bắt đầu sự kiện, kết thúc sự kiện).
- Nộp sự kiện để chờ Quản trị viên hệ thống phê duyệt (`SUBMIT FOR APPROVAL` -> chuyển trạng thái sang `PENDING_APPROVAL`).
- Quy định vòng đời trạng thái nghiêm ngặt:
  - Sự kiện `DRAFT`: Được phép sửa toàn bộ, được phép xóa vật lý (nếu chưa có bất kỳ giao dịch/ràng buộc liên quan nào).
  - Sự kiện `PENDING_APPROVAL`: Bị khóa, không được phép sửa đổi trong lúc chờ Admin duyệt.
  - Sự kiện `REJECTED`: Cho phép sửa lại thông tin và nộp duyệt lại.
  - Sự kiện `PUBLISHED`: Bị khóa hầu hết các trường cấu trúc, chỉ cho phép cập nhật mô tả theo quy định của SPEC.
- Bảo đảm tính Idempotent cho tất cả các thao tác thay đổi dữ liệu thông qua cơ chế `Idempotency-Key` kết hợp Redis.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/organizations/{organizationId}/events`
- **Mục đích:** Ban tổ chức xem danh sách toàn bộ các sự kiện của tổ chức mình (bao gồm cả DRAFT, PENDING_APPROVAL, PUBLISHED, REJECTED, CANCELLED).
- **Quyền hạn:** Phải là `MANAGER` của chính `organizationId`.
- **Query Params:** `status` (tùy chọn), `page`, `pageSize`.
- **Response Body (200 OK):**
```json
{
  "items": [
    {
      "eventId": 201,
      "title": "Đêm Nhạc Mùa Thu 2026",
      "status": "DRAFT",
      "startTime": "2026-11-15T19:00:00Z",
      "endTime": "2026-11-15T22:30:00Z",
      "createdAt": "2026-10-09T18:00:00Z",
      "totalZones": 2,
      "totalCapacity": 600
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

### 2.2. POST `/api/organizations/{organizationId}/events`
- **Mục đích:** Tạo mới một bản thảo sự kiện (`DRAFT`).
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "title": "Đêm Nhạc Mùa Thu 2026",
  "description": "Chương trình hòa nhạc thính phòng đặc biệt.",
  "categoryId": 1,
  "venue": "Nhà Hát Hòa Bình, TP.HCM",
  "saleStartTime": "2026-10-20T08:00:00Z",
  "saleEndTime": "2026-11-14T23:59:59Z",
  "startTime": "2026-11-15T19:00:00Z",
  "endTime": "2026-11-15T22:30:00Z"
}
```
- **Validation Rules thời gian (Chronological Constraints):**
  - Ràng buộc bất biến: `saleStartTime < saleEndTime <= startTime < endTime`.
  - `saleStartTime` phải lớn hơn thời điểm hiện tại (`SYSDATETIME()`).
  - Tiêu đề: 5–200 ký tự.
- **Response Body (201 Created):**
```json
{
  "eventId": 201,
  "organizationId": 45,
  "title": "Đêm Nhạc Mùa Thu 2026",
  "status": "DRAFT",
  "saleStartTime": "2026-10-20T08:00:00Z",
  "saleEndTime": "2026-11-14T23:59:59Z",
  "startTime": "2026-11-15T19:00:00Z",
  "endTime": "2026-11-15T22:30:00Z"
}
```

### 2.3. POST `/api/events/{eventId}/edit`
- **Mục đích:** Chỉnh sửa thông tin chi tiết hoặc lịch trình của sự kiện.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`.
- **Lỗi 409 Conflict:** Nếu sự kiện đang ở trạng thái `PENDING_APPROVAL`.

### 2.4. POST `/api/events/{eventId}/delete`
- **Mục đích:** Xóa bản thảo sự kiện.
- **Quy tắc:** Chỉ được xóa khi `status == 'DRAFT'` và chưa có bất kỳ Zone/Vé nào có liên kết giao dịch giữ chỗ.

### 2.5. POST `/api/events/{eventId}/submit`
- **Mục đích:** Nộp sự kiện lên Admin để phê duyệt.
- **Điều kiện tiên quyết (Prerequisites):**
  - Sự kiện phải có ít nhất 1 `Zone` với giá vé và sức chứa hợp lệ.
  - Tổng sức chứa của sự kiện phải > 0.
- **Response Body (200 OK):** Chuyển trạng thái sang `PENDING_APPROVAL`.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Tích hợp Stored Procedures Canonical
- Không tự viết các câu lệnh update thủ công dễ làm sai lệch trạng thái. Sử dụng các SP canonical có sẵn:
  - `dbo.sp_event_update_details`:
    ```sql
    EXEC dbo.sp_event_update_details
         @event_id = :eventId,
         @title = :title,
         @description = :description,
         @venue = :venue,
         @category_id = :categoryId;
    ```
  - `dbo.sp_event_configure_schedule`:
    ```sql
    EXEC dbo.sp_event_configure_schedule
         @event_id = :eventId,
         @sale_start = :saleStart,
         @sale_end = :saleEnd,
         @start_time = :startTime,
         @end_time = :endTime;
    ```
  - `dbo.sp_event_submit_for_approval`:
    ```sql
    EXEC dbo.sp_event_submit_for_approval
         @event_id = :eventId,
         @manager_user_id = :actorId;
    ```
    - Trong nội tại SP: Kiểm tra tồn tại Zone, kiểm tra tính hợp lệ của lịch bán, chuyển `status = 'PENDING_APPROVAL'`, ghi `dbo.tc_audit_logs`.

### 3.2. Cơ chế Idempotency với Redis
- Mọi mutation (`POST`, `PUT`) bắt buộc có header `Idempotency-Key`.
- **Cấu trúc Redis Key:** `tc:idem:event:<actorId>:<idempotencyKey>` (TTL: 86400 giây / 24 giờ).
- **Quy trình xử lý Idempotency:**
  1. Tính mã băm SHA-256 của toàn bộ request body: `payloadHash = sha256(requestBody)`.
  2. Dùng Redis Atomic command `SETNX` (Set if Not Exists) với key trên:
     - Nếu key chưa tồn tại: Đặt giá trị `{ "status": "PROCESSING", "hash": payloadHash }`, set TTL 120s. Tiếp tục xử lý nghiệp vụ DB.
     - Nếu key đã tồn tại:
       - Đọc dữ liệu trong key. Nếu `storedHash != payloadHash`: **Trả về ngay 409 Conflict** (phát hiện tái sử dụng cùng key nhưng nội dung payload khác nhau).
       - Nếu `status == "PROCESSING"`: **Trả về 409 Conflict hoặc 425 Too Early** (yêu cầu đang được xử lý đồng thời).
       - Nếu `status == "COMPLETED"`: **Trả lại nguyên vẹn kết quả Response đã lưu trong Redis** từ lần gọi trước mà không thực hiện lại DB!
  3. Sau khi Transaction DB commit thành công:
     - Cập nhật Redis key: `{ "status": "COMPLETED", "hash": payloadHash, "statusCode": 201, "responseBody": ... }` với TTL 24h.

### 3.3. Xóa Cache sau DB Commit (Post-Commit Invalidation)
- **Tuyệt đối không:** Xóa cache trước khi DB commit (nguy cơ Dirty Read do transaction khác đọc DB cũ rồi ghi đè cache).
- **Quy trình chuẩn:**
  1. Transaction SQL Server hoàn tất `commit()`.
  2. Bắn sự kiện xóa cache (Event Invalidation):
     - `tc:event:detail:v1:<eventId>`
     - `tc:event:zones:v1:<eventId>`
     - `tc:org:events:<organizationId>:*`
     - Xóa các trang danh mục công khai `tc:event:list:v1:*`.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `EventCreateDraftDto`, `EventUpdateDetailsDto`, `EventConfigureScheduleDto`, `EventDraftSummaryDto`, `EventDraftDetailDto`.
   - Repositories: `EventDraftRepository` (thực hiện tương tác với bảng `dbo.tc_events` và gọi các SP `sp_event_update_details`, `sp_event_configure_schedule`, `sp_event_submit_for_approval`).
   - Idempotency Handler: `EventIdempotencyHelper` (lưu trữ và đối soát trên Redis).
   - Core Services: `EventDraftService`.
   - Servlets: `OrganizationEventsServlet`, `EventDraftEditServlet`, `EventDraftSubmitServlet`, `EventDraftDeleteServlet`.
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/event/organizer/event-list.jsp`: Danh sách sự kiện của tổ chức, badge trạng thái, nút Tạo sự kiện, nút Chỉnh sửa/Nộp duyệt.
   - `src/main/webapp/WEB-INF/views/event/organizer/event-form.jsp`: Form điền tiêu đề, thể loại, địa điểm, datetime pickers (sale start/end, event start/end) có kiểm tra ràng buộc trước khi gửi.
3. **Database Changes:**
   - Bảng `dbo.tc_events` có bản ghi mới với `status = 'DRAFT'` hoặc cập nhật sang `'PENDING_APPROVAL'`.
4. **Kiểm thử tự động:**
   - `EventDraftServiceTest`: Kiểm tra ràng buộc thời gian (sai thứ tự thời gian ném lỗi), kiểm tra chuyển trạng thái hợp lệ.
   - `EventIdempotencyTest`: Kiểm tra gửi cùng key lặp lại trả response cũ, gửi khác payload trả 409.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs và Bộ kiểm tra Ràng buộc Thời gian**
  - Khởi tạo `EventConfigureScheduleDto` có phương thức kiểm tra `isValidChronology()`:
    `saleStart.isBefore(saleEnd) && !saleEnd.isAfter(startTime) && startTime.isBefore(endTime)`.
- **Bước 2: Xây dựng `EventDraftRepository`**
  - Triển khai gọi các Stored Procedures qua JDBC CallableStatement trong transaction.
- **Bước 3: Xây dựng `EventIdempotencyHelper`**
  - Kết nối Jedis / Redis cluster, thiết lập helper atomic `acquireLock(key, hash)` và `recordSuccess(key, response)`.
- **Bước 4: Xây dựng `EventDraftService`**
  - Đảm bảo kiểm tra quyền: Actor phải là `MANAGER` của tổ chức sở hữu sự kiện.
  - Phối hợp giữa Database Transaction và Post-Commit Cache Invalidation.
- **Bước 5: Xây dựng Servlets**
  - Ánh xạ đường dẫn REST, kiểm tra bắt buộc header `Idempotency-Key` trên các method POST.
- **Bước 6: Xây dựng Giao diện JSP (UI-12)**
  - Tích hợp giao diện quản lý chuyên nghiệp cho Ban tổ chức sự kiện.
- **Bước 7: Chạy kiểm thử tự động**
  - Kiểm tra `mvn -B test` bảo đảm 100% ca test đều xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| DRF-01 | Tạo Event Draft hợp lệ | Manager hợp lệ, thời gian chuẩn | 201 Created, DB lưu DRAFT, Redis lưu Idempotency key |
| DRF-02 | Thời gian bắt đầu sau thời gian kết thúc | startTime > endTime | 400 Bad Request kèm chi tiết lỗi thứ tự thời gian |
| DRF-03 | Quản lý tổ chức A sửa sự kiện tổ chức B | Actor không thuộc tổ chức sở hữu | 403 Forbidden |
| DRF-04 | Nộp duyệt sự kiện chưa có Zone nào | Event chưa tạo Zone | 400 Bad Request ("Sự kiện phải có ít nhất 1 khu vực vé") |
| DRF-05 | Idempotency Replay | Gửi lại cùng key và cùng payload | Trả lại kết quả 201 cũ mà không insert thêm bản ghi thứ 2 |
| DRF-06 | Idempotency Payload Conflict | Gửi lại cùng key nhưng đổi tiêu đề | 409 Conflict |
