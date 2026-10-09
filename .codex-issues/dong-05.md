> **Convention:** `dev` → `feat/dong/dong-05-admin-event-review` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Đông chỉ sở hữu các chức năng Quản trị viên duyệt sự kiện: endpoint duyệt/từ chối, JSP phần duyệt trong UI-20 và test tương ứng. Tuyệt đối không can thiệp vào tính năng Hủy sự kiện (`Event Cancellation`) hoặc Tiến trình hoàn tiền - phần đó thuộc trách nhiệm của Khánh.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng module Quản trị viên hệ thống thẩm định, phê duyệt và xuất bản sự kiện lên cổng bán vé công khai:
- Phục vụ trực tiếp màn hình **UI-20 (Admin Event Approval & Moderation)**.
- Phân quyền: Chỉ tài khoản có `PlatformRole.ADMIN` mới có quyền truy cập và thao tác.
- **Quy trình Phê duyệt & Xuất bản (`Publish`):**
  - Chỉ cho phép duyệt sự kiện đang ở trạng thái `PENDING_APPROVAL`.
  - Kiểm tra đầy đủ các điều kiện tiên quyết (Checklist bắt buộc): Đã có ảnh bìa (`cover_image_url != NULL`), lịch trình thời gian hợp lệ, có ít nhất một `Zone` với giá và sức chứa hợp lệ.
  - Tổ chức sở hữu sự kiện phải đang có một Chính sách hoa hồng (`CommissionRule`) có hiệu lực tại thời điểm diễn ra sự kiện.
  - Sau khi xuất bản, sự kiện chuyển sang trạng thái `PUBLISHED` và lập tức hiển thị trên Catalog công khai (UI-01).
- **Quy trình Từ chối (`Reject`):**
  - Chuyển trạng thái sự kiện sang `REJECTED`, bắt buộc ghi nhận lý do từ chối rõ ràng để Ban tổ chức biết và chỉnh sửa lại.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/admin/events`
- **Mục đích:** Admin xem danh sách tất cả các sự kiện trên toàn hệ thống (hỗ trợ lọc theo trạng thái `PENDING_APPROVAL`, `PUBLISHED`, `REJECTED`, `CANCELLED`).
- **Quyền hạn:** `PlatformRole.ADMIN`.
- **Query Params:** `status` (mặc định `PENDING_APPROVAL`), `page`, `pageSize`.
- **Response Body (200 OK):**
```json
{
  "items": [
    {
      "eventId": 201,
      "title": "Đêm Nhạc Mùa Thu 2026",
      "organization": {
        "organizationId": 45,
        "name": "Công ty Âm nhạc Trẻ Việt Nam"
      },
      "venue": "Nhà Hát Hòa Bình, TP.HCM",
      "startTime": "2026-11-15T19:00:00Z",
      "totalCapacity": 600,
      "status": "PENDING_APPROVAL",
      "submittedAt": "2026-10-09T18:00:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

### 2.2. GET `/api/admin/events/{eventId}`
- **Mục đích:** Admin xem chi tiết đầy đủ hồ sơ sự kiện để thẩm định trước khi ra quyết định duyệt.

### 2.3. POST `/api/admin/events/{eventId}/publish`
- **Mục đích:** Admin phê duyệt và xuất bản sự kiện.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "commissionRuleId": 120,
  "adminNote": "Hồ sơ sự kiện và giấy phép biểu diễn đầy đủ, hợp lệ."
}
```
- **Response Body (200 OK):**
```json
{
  "success": true,
  "eventId": 201,
  "status": "PUBLISHED",
  "publishedAt": "2026-10-09T18:30:00Z",
  "message": "Sự kiện đã được phê duyệt và xuất bản thành công."
}
```
- **Lỗi:**
  - `400 Bad Request`: Sự kiện thiếu ảnh bìa, chưa có Zone nào, hoặc CommissionRule không thuộc về tổ chức này.
  - `409 Conflict`: Sự kiện không ở trạng thái `PENDING_APPROVAL`.

### 2.4. POST `/api/admin/events/{eventId}/reject`
- **Mục đích:** Admin từ chối phê duyệt sự kiện.
- **Request Body:**
```json
{
  "reason": "Chưa bổ sung giấy phép biểu diễn của Sở Văn Hóa Thông Tin."
}
```
- **Validation Rules:** `reason` không được để trống, độ dài từ 10 đến 500 ký tự.
- **Response Body (200 OK):**
```json
{
  "success": true,
  "eventId": 201,
  "status": "REJECTED",
  "message": "Đã từ chối duyệt sự kiện."
}
```

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Gọi Stored Procedure Canonical
- Tích hợp Stored Procedure trong `database/procedures.sql`:
  - `dbo.sp_event_publish`:
    ```sql
    EXEC dbo.sp_event_publish
         @event_id = :eventId,
         @admin_user_id = :adminId,
         @commission_rule_id = :commissionRuleId;
    ```
    - Trong nội tại SP:
      1. Kiểm tra `status == 'PENDING_APPROVAL'`. Nếu sai: Ném lỗi `EVENT_NOT_PENDING_APPROVAL`.
      2. Kiểm tra `cover_image_url IS NOT NULL`.
      3. Kiểm tra sự tồn tại của ít nhất 1 bản ghi trong `dbo.tc_zones` thuộc event này.
      4. Kiểm tra `dbo.fn_commission_rule_is_effective_at(@commission_rule_id, start_time)` có hợp lệ không.
      5. Cập nhật `status = 'PUBLISHED'`, `published_at = SYSDATETIME()`.
      6. Ghi vết kiểm toán vào `dbo.tc_audit_logs`.
  - `dbo.sp_event_reject`:
    ```sql
    EXEC dbo.sp_event_reject
         @event_id = :eventId,
         @admin_user_id = :adminId,
         @rejected_reason = :reason;
    ```

### 3.2. Chống Xung đột với Quản lý Tổ chức (Admin vs Organizer Race Condition)
- Sử dụng cơ chế khóa `UPDLOCK, ROWLOCK` trong SQL Server:
  - Nếu trong lúc Admin đang bấm duyệt, Quản lý tổ chức lại cố gắng sửa layout ghế hoặc xóa Zone, transaction của Organizer sẽ bị chặn hoặc báo lỗi trạng thái.
  - Ngăn ngừa tình trạng sự kiện được xuất bản với cấu hình rỗng.

### 3.3. Đồng bộ và Xóa Cache Toàn diện (Broad Cache Invalidation)
- Khi sự kiện được xuất bản (`PUBLISHED`):
  - Xóa cache chi tiết: `DEL tc:event:detail:v1:<eventId>`
  - Xóa cache danh sách zones: `DEL tc:event:zones:v1:<eventId>`
  - Xóa toàn bộ cache catalog công khai: `DEL tc:event:list:v1:*` (để trang chủ người dùng lập tức xuất hiện sự kiện mới).
  - Xóa cache danh sách sự kiện của tổ chức: `DEL tc:org:events:<orgId>:*`

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `AdminEventPublishRequestDto`, `AdminEventRejectRequestDto`, `AdminEventSummaryDto`, `AdminEventDetailDto`.
   - Repositories: `AdminEventRepository` (gọi các SP `sp_event_publish`, `sp_event_reject`, truy vấn danh sách sự kiện chờ duyệt qua `dbo.v_events`).
   - Core Services: `AdminEventReviewService`.
   - Servlets: `AdminEventsServlet`, `AdminEventDecisionServlet`.
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/admin/events/list.jsp`: Danh sách sự kiện chờ duyệt kèm thông tin tổ chức gửi, nút Xem hồ sơ.
   - `src/main/webapp/WEB-INF/views/admin/events/review.jsp`: Xem chi tiết sự kiện, danh sách các Zone và sơ đồ ghế, modal chọn chính sách hoa hồng khi bấm Duyệt, modal nhập lý do khi Từ chối.
3. **Database Changes:**
   - Bảng `dbo.tc_events` cập nhật `status = 'PUBLISHED'` hoặc `'REJECTED'`, `published_at`, `rejected_reason`.
   - Ghi 1 bản ghi vào `dbo.tc_audit_logs`.
4. **Kiểm thử tự động:**
   - `AdminEventReviewServiceTest`: Non-admin gọi bị 403, duyệt thiếu Zone bị chặn, duyệt thành công chuyển trạng thái PUBLISHED và kích hoạt invalidation cache.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs cho Admin Review**
  - Khai báo `AdminEventPublishRequestDto` và `AdminEventRejectRequestDto`.
- **Bước 2: Xây dựng `AdminEventRepository`**
  - Gọi `dbo.sp_event_publish` và `dbo.sp_event_reject` thông qua JDBC `CallableStatement`.
- **Bước 3: Xây dựng `AdminEventReviewService`**
  - Kiểm tra `SecurityContext`: Người thực hiện phải có `PlatformRole.ADMIN`.
  - Gọi repository thực thi SP trong database transaction.
  - Khi transaction commit xong, gọi `eventRedisCacheService.invalidateOnPublish(eventId, orgId)`.
- **Bước 4: Xây dựng Servlets**
  - Định tuyến các action `/api/admin/events/{id}/publish` và `/api/admin/events/{id}/reject`.
- **Bước 5: Xây dựng Giao diện JSP (UI-20)**
  - Tích hợp màn hình xem xét hồ sơ sự kiện của Admin.
  - Lưu ý: Nút "Hủy sự kiện" trên UI-20 sẽ gọi sang API hủy của Khánh, Đông không viết backend cho việc hủy.
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm toàn bộ hệ thống xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| REV-01 | Phê duyệt sự kiện hợp lệ | Admin đăng nhập, Event PENDING, đủ ảnh và Zone | 200 OK, Event thành PUBLISHED, cache catalog bị xóa để làm mới |
| REV-02 | Non-admin gọi API duyệt | User có role CUSTOMER hoặc MANAGER | 403 Forbidden |
| REV-03 | Duyệt sự kiện chưa có Zone | Event có zoneCount = 0 | 400 Bad Request ("Sự kiện chưa có khu vực vé nào") |
| REV-04 | Duyệt sự kiện chưa có ảnh bìa | coverImageUrl = null | 400 Bad Request ("Sự kiện chưa có ảnh bìa") |
| REV-05 | Từ chối không nhập lý do | reason = "" | 400 Bad Request |
| REV-06 | Duyệt lại sự kiện đã PUBLISHED | Event status = PUBLISHED | 409 Conflict |
