> **Convention:** `dev` → `feat/khanh/khanh-01-checkin-events` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Chỉ sửa code trong `fulfillment/checkin/**`, JSP chọn sự kiện soát vé (UI-15), DTO/cache và test tương ứng. Cấm sửa Event của Đông, Ticket/QR encoder của Liêm hay model/config/database/OpenAPI.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng cổng lựa chọn Sự kiện và kiểm tra Khung thời gian soát vé hợp lệ (Check-in Time Window) dành cho nhân viên soát vé tại cửa:
- Phục vụ trực tiếp màn hình **UI-15 (Màn hình chọn Sự kiện Soát vé cho Nhân viên / Ban tổ chức)**.
- Phân quyền nghiêm ngặt: Chỉ tài khoản có vai trò `MANAGER` hoặc `CHECK_IN_STAFF` của Tổ chức sở hữu sự kiện mới được phép truy cập và thực hiện soát vé.
- Quy định Khung thời gian soát vé hợp lệ (Valid Check-in Window):
  - Check-in chỉ được phép mở từ: `[startTime - 60 phút, endTime)`.
  - Ngoài khoảng thời gian trên (đến quá sớm hoặc đến sau khi sự kiện kết thúc), hệ thống tự động khóa cổng quét vé.
  - Sự kiện đã bị hủy (`CANCELLED`) tuyệt đối không cho phép mở cổng soát vé dưới bất kỳ hình thức nào.
- Tối ưu hóa hiệu năng đọc qua Redis Cache-Aside để nhân viên tải danh sách sự kiện và trạng thái khung giờ tức thì mà không gây nghẽn database vào giờ cao điểm khán giả đổ xô vào cửa.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/check-ins/events` (hoặc `/api/organizations/{orgId}/check-in-events`)
- **Mục đích:** Nhân viên soát vé xem danh sách các sự kiện đang diễn ra hôm nay hoặc sắp diễn ra của tổ chức mình để chọn vào ca làm việc.
- **Quyền hạn:** `MANAGER` hoặc `CHECK_IN_STAFF` của tổ chức.
- **Response Body (200 OK):**
```json
[
  {
    "eventId": 201,
    "title": "Đêm Nhạc Mùa Thu 2026",
    "venue": "Nhà Hát Hòa Bình, TP.HCM",
    "startTime": "2026-11-15T19:00:00Z",
    "endTime": "2026-11-15T22:30:00Z",
    "checkInOpenTime": "2026-11-15T18:00:00Z",
    "isCheckInOpen": true,
    "totalTicketsIssued": 580,
    "totalCheckedIn": 142
  }
]
```

### 2.2. GET `/api/events/{eventId}/check-in-window`
- **Mục đích:** Kiểm tra trạng thái đóng/mở soát vé thời gian thực của sự kiện.
- **Response Body (200 OK):**
```json
{
  "eventId": 201,
  "isOpen": true,
  "status": "OPEN",
  "windowStart": "2026-11-15T18:00:00Z",
  "windowEnd": "2026-11-15T22:30:00Z",
  "currentServerTime": "2026-11-15T18:35:10Z",
  "message": "Cổng soát vé đang hoạt động bình thường."
}
```
- **Lỗi:**
  - `400 Bad Request`: Sự kiện đã bị hủy (`CANCELLED`).
  - `403 Forbidden`: Người dùng không có quyền soát vé tại sự kiện này.
  - `404 Not Found`: Sự kiện không tồn tại.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Xác thực Quyền Soát vé qua Database (DB Truth RBAC)
- Client truyền `organizationId` trên URL chỉ mang tính ngữ cảnh lọc.
- **Chốt chặn phân quyền tại Database:**
  ```sql
  SELECT m.role, m.is_active 
  FROM dbo.tc_organization_memberships m
  JOIN dbo.tc_events e ON e.organization_id = m.organization_id
  WHERE m.user_id = :actorId 
    AND e.id = :eventId 
    AND m.is_active = 1 
    AND m.role IN ('MANAGER', 'CHECK_IN_STAFF');
  ```
  Nếu không có bản ghi nào thỏa mãn, trả ngay `403 Forbidden`.

### 3.2. Thuật toán Tính Khung Giờ Soát vé & Tích hợp Function DB
- Gọi Function canonical `dbo.fn_event_is_check_in_open`:
  ```sql
  SELECT dbo.fn_event_is_check_in_open(:eventId, SYSDATETIME());
  ```
  - Logic tính toán:
    `status == 'PUBLISHED'` VÀ `SYSDATETIME() >= DATEADD(minute, -60, start_time)` VÀ `SYSDATETIME() < end_time`.

### 3.3. Cơ chế Đệm Bộ nhớ (Cache-Aside) với TTL Ngắn
- **Cấu trúc Redis Key:**
  - Danh sách sự kiện check-in của nhân viên: `tc:checkin:events:v1:<actorId>:<orgId>` (TTL: 60 giây).
  - Trạng thái khung giờ soát vé: `tc:checkin:window:v1:<eventId>` (TTL: 30 giây).
- **Lưu ý bảo mật:** Cache DTO này chỉ chứa ID, tên sự kiện, thời gian mở/đóng và số lượng vé vào cửa. **Tuyệt đối không lưu doanh thu, giá vé hoặc thông tin tài chính** vào cache này.
- Khi sự kiện bị hủy (`CANCELLED`) hoặc thay đổi lịch trình: Nhận sự kiện Invalidation từ Đông/Khánh để xóa sạch cache khung giờ.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `CheckInEventSummaryDto`, `CheckInWindowStatusDto`.
   - Repositories: `CheckInEventRepository` (truy vấn danh sách sự kiện cho phép soát vé, gọi function `fn_event_is_check_in_open`).
   - Core Services: `CheckInEventService`.
   - Servlets: `CheckInEventsServlet` (`/api/check-ins/events`), `CheckInWindowServlet` (`/api/events/{id}/check-in-window`).
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/checkin/select-event.jsp`: Màn hình UI-15 cho nhân viên soát vé, hiển thị các sự kiện đang diễn ra hôm nay, trạng thái badge Xanh (Đang mở soát vé) / Vàng (Chưa tới giờ) / Đỏ (Đã kết thúc / Bị hủy), nút "Vào điểm danh soát vé".
3. **Kiểm thử tự động:**
   - `CheckInEventServiceTest`: Kiểm tra nhân viên thuộc tổ chức khác bị 403, kiểm tra trước giờ diễn ra 61 phút thì báo chưa mở, đúng 60 phút thì báo mở, sự kiện CANCELLED báo lỗi.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs**
  - Khai báo `CheckInEventSummaryDto` và `CheckInWindowStatusDto`.
- **Bước 2: Xây dựng `CheckInEventRepository`**
  - Viết câu truy vấn kiểm tra quyền soát vé và lấy danh sách sự kiện của tổ chức.
- **Bước 3: Xây dựng `CheckInEventService`**
  - Kiểm tra membership của Actor.
  - Tích hợp kiểm tra cache Redis trước khi truy vấn DB.
- **Bước 4: Xây dựng Servlets**
  - Định tuyến các endpoint GET phục vụ giao diện mobile web của nhân viên soát vé.
- **Bước 5: Xây dựng Giao diện JSP (UI-15)**
  - Tối ưu giao diện mobile-first cho nhân viên soát vé dùng điện thoại tại cổng.
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| CHK-01 | Nhân viên đúng tổ chức xem sự kiện | Actor có role CHECK_IN_STAFF của Org | 200 OK, trả về danh sách sự kiện kèm trạng thái mở soát vé |
| CHK-02 | Người lạ truy cập xem sự kiện soát vé | Actor không có membership trong Org | 403 Forbidden |
| CHK-03 | Kiểm tra trước giờ mở 61 phút | Hiện tại: 17:59, Sự kiện: 19:00 | isOpen = false, message = "Chưa đến thời gian mở cổng soát vé" |
| CHK-04 | Kiểm tra đúng giờ mở (trước 60 phút) | Hiện tại: 18:00, Sự kiện: 19:00 | isOpen = true, status = "OPEN" |
| CHK-05 | Sự kiện đã bị hủy (CANCELLED) | Event status = CANCELLED | isOpen = false, message = "Sự kiện đã bị hủy bỏ" |
