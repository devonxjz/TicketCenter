> **Convention:** `dev` → `feat/khanh/khanh-06-event-cancellation` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Khánh sở hữu toàn bộ API Hủy sự kiện (`POST /api/admin/events/{id}/cancel`), API xem tiến độ xử lý (`GET /api/admin/events/{id}/cancellation-progress`), worker chạy nền xử lý theo lô `EventCancellationJob` và Stored Procedure tiếp nhận hoàn tiền tự động. Đông chỉ sở hữu nút bấm giao diện trên JSP UI-20.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng quy trình Quản trị viên Hủy bỏ Sự kiện (Event Cancellation) khẩn cấp và Tự động hoàn tiền hàng loạt theo lô (Batch Refund Processing) có khả năng phục hồi sau sự cố (Crash/Restart Recovery):
- Phục vụ trực tiếp nút "Hủy sự kiện" và thanh hiển thị tiến độ hoàn tiền trên màn hình **UI-20**.
- Phân quyền: Chỉ tài khoản có `PlatformRole.ADMIN` mới có quyền thực hiện.
- **Ràng buộc thời gian:** Chỉ được phép hủy sự kiện **trước thời điểm bắt đầu (`SYSDATETIME() < startTime`)**. Sau khi sự kiện đã bắt đầu, không thể hủy bằng endpoint này.
- **Quy trình 2 giai đoạn (Two-Phase Cancellation Architecture):**
  - **Giai đoạn 1 - Ngắt lập tức (Immediate Circuit Breaker - Đồng bộ):**
    - Chuyển trạng thái sự kiện sang `CANCELLED`.
    - Lập tức chặn mọi luồng bán vé, khóa cổng soát vé, tự động giải phóng toàn bộ các phiên giữ vé (`ACTIVE Holds`) đang treo.
    - Xóa toàn bộ cache sự kiện trên Redis để trang chủ công khai ẩn ngay sự kiện.
    - Khởi tạo tác vụ hủy theo lô trong cơ sở dữ liệu và trả về phản hồi cho Admin ngay lập tức (không bắt Admin chờ xử lý hàng nghìn đơn hàng).
  - **Giai đoạn 2 - Xử lý Hoàn tiền theo Lô (Asynchronous Batch Processing - Bất đồng bộ):**
    - Tiến trình nền `EventCancellationJob` quét từng đơn hàng đã thanh toán (`PAID Orders`) của sự kiện.
    - Tự động sinh yêu cầu hoàn tiền toàn phần cho người mua.
    - **Hấp thu yêu cầu hoàn tiền sẵn có (Refund Adoption):** Nếu một đơn hàng đã có yêu cầu hoàn tiền do khách gửi trước đó (`REQUESTED`), hệ thống sẽ tiếp nhận (`Adopt`) và chuyển thành diện hoàn tiền do hủy sự kiện, tuyệt đối không tạo bản ghi hoàn tiền thứ hai cho cùng một chiếc vé!
    - **Khả năng phục hồi khi Restart:** Lưu trữ con trỏ tiến độ (`last_processed_order_id`) vào DB. Nếu server bị sập nguồn hoặc restart giữa chừng, tiến trình sẽ tự động chạy tiếp từ vị trí dừng lại mà không bị lặp lại đơn hàng cũ.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. POST `/api/admin/events/{eventId}/cancel`
- **Mục đích:** Admin phát lệnh hủy sự kiện.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "reason": "Điều kiện thời tiết bão lũ nguy hiểm không thể tổ chức biểu diễn ngoài trời."
}
```
- **Validation Rules:** `reason` không được để trống, 10–500 ký tự.
- **Response Body (200 OK):**
```json
{
  "success": true,
  "eventId": 201,
  "status": "CANCELLED",
  "cancelledAt": "2026-10-09T18:45:00Z",
  "totalOrdersToRefund": 350,
  "message": "Sự kiện đã được hủy thành công. Tiến trình hoàn tiền tự động đang được kích hoạt chạy nền."
}
```
- **Lỗi:**
  - `400 Bad Request`: Sự kiện đã bắt đầu hoặc đã kết thúc.
  - `409 Conflict`: Sự kiện đã bị hủy trước đó.

### 2.2. GET `/api/admin/events/{eventId}/cancellation-progress`
- **Mục đích:** Admin theo dõi tiến độ xử lý hoàn tiền theo thời gian thực.
- **Response Body (200 OK):**
```json
{
  "eventId": 201,
  "status": "IN_PROGRESS",
  "totalOrders": 350,
  "processedOrders": 280,
  "successfulRefunds": 278,
  "failedRefunds": 2,
  "progressPercentage": 80.0,
  "updatedAt": "2026-10-09T18:47:15Z"
}
```

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Giai đoạn 1 - Gọi Stored Procedure Canonical Hủy Sự kiện
- Gọi Stored Procedure `dbo.sp_event_cancel`:
  ```sql
  EXEC dbo.sp_event_cancel
       @event_id = :eventId,
       @admin_user_id = :adminId,
       @cancel_reason = :reason,
       @total_orders = :totalOrders OUTPUT;
  ```
  - Trong nội tại SP:
    1. Kiểm tra: `status != 'CANCELLED'` VÀ `start_time > SYSDATETIME()`. Nếu sai: Ném lỗi `EVENT_CANNOT_BE_CANCELLED`.
    2. Cập nhật `tc_events.status = 'CANCELLED'`, `cancelled_at = SYSDATETIME()`, `cancelled_reason = @cancel_reason`.
    3. Tự động giải phóng toàn bộ các Ticket Hold của sự kiện này: Đổi sang `CANCELLED`, hoàn trả ghế về kho.
    4. Tạo bản ghi tác vụ trong `dbo.tc_cancellation_jobs` với `status = 'PENDING'`, `last_processed_order_id = 0`.
    5. Đếm số lượng đơn hàng `PAID` cần hoàn tiền trả ra biến `@total_orders`.
    6. Ghi vết kiểm toán vào `dbo.tc_audit_logs`.
- **Ngay sau khi Transaction commit:**
  - Invalidate toàn bộ cache: `DEL tc:event:detail:v1:<eventId>`, `DEL tc:event:zones:v1:<eventId>`, `DEL tc:checkin:window:v1:<eventId>`, `DEL tc:event:list:v1:*`.

### 3.2. Giai đoạn 2 - Tiến trình Nền Hoàn tiền theo Lô (`EventCancellationJob`)
- Worker chạy nền theo chu kỳ (ví dụ mỗi 10 giây quét các job `PENDING` hoặc `IN_PROGRESS`):
  - Lấy danh sách 50 đơn hàng tiếp theo:
    ```sql
    SELECT TOP 50 id FROM dbo.tc_orders 
    WHERE event_id = :eventId AND status = 'PAID' AND id > :lastProcessedOrderId 
    ORDER BY id ASC;
    ```
  - Với từng đơn hàng, mở một transaction độc lập gọi:
    ```sql
    EXEC dbo.sp_refund_adopt_event_cancellation
         @order_id = :orderId,
         @event_id = :eventId,
         @cancel_reason = :reason;
    ```
    - Trong SP:
      1. Kiểm tra các vé trong đơn hàng: Nếu đã có `Refund` ở trạng thái `REQUESTED`, cập nhật `type = 'EVENT_CANCELLATION'`, `status = 'APPROVED'`.
      2. Nếu chưa có Refund: Tạo bản ghi `dbo.tc_refunds` mới với `type = 'EVENT_CANCELLATION'`, `status = 'APPROVED'`, số tiền bằng toàn bộ tiền vé chưa hoàn.
      3. Đánh dấu vé sang `REFUND_PENDING` để đưa sang cho `RefundWorker` (của KHANH-05) thực hiện chuyển tiền.
  - Cập nhật con trỏ: `UPDATE dbo.tc_cancellation_jobs SET last_processed_order_id = :currentOrderId, processed_orders = processed_orders + 1 WHERE id = :jobId`.

### 3.3. Cơ chế Khôi phục Khi Sự cố (Restart Recovery)
- Nguồn chân lý của tiến trình hoàn toàn nằm ở SQL Server (`dbo.tc_cancellation_jobs`).
- Redis chỉ lưu cache tiến độ: `tc:event:cancel-progress:<eventId>` (TTL: 10 giây).
- Nếu server Tomcat bị tắt hoặc ứng dụng bị restart giữa lúc đang xử lý đơn hàng thứ 150/350:
  - Khi khởi động lại, `EventCancellationJob` tự động đọc lại bản ghi job từ DB với `last_processed_order_id = 150` và tiếp tục xử lý đơn hàng thứ 151, không xử lý lại từ đầu và không bỏ sót bất kỳ đơn hàng nào.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `EventCancelRequestDto`, `EventCancelResponseDto`, `CancellationProgressDto`.
   - Jobs & Workers: `EventCancellationJob` (quét và xử lý batch theo lô), `CancellationProgressCacheHelper`.
   - Repositories: `EventCancellationRepository` (gọi các SP `sp_event_cancel`, `sp_refund_adopt_event_cancellation`, truy vấn và cập nhật tiến độ job).
   - Core Services: `EventCancellationService`.
   - Servlets: `AdminEventCancelServlet` (`/api/admin/events/{id}/cancel`), `AdminCancellationProgressServlet` (`/api/admin/events/{id}/cancellation-progress`).
2. **Giao diện JSP:**
   - Phần hiển thị trên UI-20 (Admin Event Review): Nút "Hủy sự kiện" màu đỏ, modal nhập lý do hủy, thanh tiến trình ProgressBar hiển thị phần trăm đơn hàng đã được tạo lệnh hoàn tiền.
3. **Database Changes:**
   - Bảng `dbo.tc_events` cập nhật `status = 'CANCELLED'`.
   - Bảng `dbo.tc_cancellation_jobs` lưu trữ tiến độ batch.
   - Bảng `dbo.tc_refunds` sinh hàng loạt các bản ghi hoàn tiền với `type = 'EVENT_CANCELLATION'`.
4. **Kiểm thử tự động:**
   - `EventCancellationServiceTest`: Hủy sau giờ bắt đầu bị chặn, hủy khi sự kiện đã CANCELLED trả về lỗi, hủy thành công đóng băng ngay lập tức.
   - `BatchRefundAdoptionTest`: Đơn hàng đã có yêu cầu hoàn tiền cũ được adopt chứ không sinh 2 refund.
   - `CancellationRecoveryTest`: Worker tiếp tục chính xác từ `last_processed_order_id` sau khi restart.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs cho Hủy sự kiện**
  - Khai báo `EventCancelRequestDto` và `CancellationProgressDto`.
- **Bước 2: Triển khai `EventCancellationRepository`**
  - Viết JDBC CallableStatement gọi `dbo.sp_event_cancel` và `dbo.sp_refund_adopt_event_cancellation`.
- **Bước 3: Xây dựng `EventCancellationService`**
  - Kiểm tra quyền `PlatformRole.ADMIN`.
  - Thực thi Giai đoạn 1 (Hủy sự kiện, giải phóng hold, xóa cache Redis).
- **Bước 4: Cài đặt `EventCancellationJob`**
  - Sử dụng ScheduledExecutor quét định kỳ mỗi 5-10 giây để chạy các lô đơn hàng.
- **Bước 5: Xây dựng Servlets**
  - Cung cấp endpoint POST hủy sự kiện và GET tiến độ hoàn tiền.
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| CAN-01 | Hủy sự kiện hợp lệ | Admin đăng nhập, trước startTime, có lý do | 200 OK, Event thành CANCELLED, tạo cancellation job |
| CAN-02 | Hủy sau khi sự kiện đã bắt đầu | Hiện tại > startTime | 400 Bad Request ("Không thể hủy sự kiện đã bắt đầu") |
| CAN-03 | Hủy lại sự kiện đã bị hủy trước đó | Event status = CANCELLED | 409 Conflict |
| CAN-04 | Đơn hàng đã có yêu cầu hoàn tiền | Order đã có Refund status REQUESTED | SP adopt refund cũ sang EVENT_CANCELLATION, không tạo refund thứ 2 |
| CAN-05 | Khôi phục tiến trình khi Worker restart | Job đang xử lý ở order 50/100, restart app | Worker tiếp tục từ order 51 đến 100, hoàn tất 100% |
