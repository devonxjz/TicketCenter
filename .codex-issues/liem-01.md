> **Convention:** `dev` → `feat/liem/liem-01-ticket-hold` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Chỉ sửa code trong `ticketing/controller/**`, `ticketing/service/**`, `ticketing/repository/**`, `ticketing/dto/**`, `ticketing/cache/**`, Hold workers, JSP chọn vé (trong UI-02/UI-04) và test tương ứng. Tuyệt đối không can thiệp vào Event inventory của Đông, Refund của Khánh hay model/config/database/OpenAPI.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng hệ thống Giữ vé tạm thời (Ticket Hold) có khóa hàng tồn kho nguyên tử (Atomic Inventory Lock):
- Phục vụ màn hình **UI-02 (Chọn ghế / Chọn số lượng vé)** và chuyển tiếp sang **UI-04 (Tạo đơn hàng & Thanh toán)**.
- Ràng buộc nghiệp vụ bất biến:
  - Người mua vé (`BUYER`) phải có tài khoản hợp lệ, trạng thái `ACTIVE` và **đã xác minh email thành công** (`fn_user_is_eligible_buyer`).
  - Tại một thời điểm, mỗi người dùng **chỉ được phép có duy nhất 1 Ticket Hold đang hoạt động** (`ACTIVE`) trên toàn hệ thống.
  - Số lượng vé trong một lượt giữ chỗ: Tối thiểu 1 vé, tối đa 8 vé, và **tất cả các vé phải thuộc về cùng một sự kiện**.
  - Thời hạn giữ vé: Chính xác 10 phút (600 giây). Hết 10 phút, worker chạy nền hoặc transaction hết hạn sẽ tự động giải phóng ghế về kho hàng để người khác có thể mua.
- Toàn vẹn giao dịch tuyệt đối: Nếu chọn 4 ghế mà 1 ghế vừa bị người khác giữ trước đó dù chỉ 1 mili-giây, toàn bộ giao dịch phải được Rollback ngay lập tức (All-or-Nothing).

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. POST `/api/holds`
- **Mục đích:** Khóa tạm thời các ghế hoặc số lượng vé đứng đã chọn.
- **Quyền hạn:** `AUTHENTICATED` (đã verify email).
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "eventId": 201,
  "items": [
    {
      "zoneId": 10,
      "seatId": 501
    },
    {
      "zoneId": 10,
      "seatId": 502
    },
    {
      "zoneId": 11,
      "seatId": null,
      "quantity": 2
    }
  ]
}
```
- **Validation Rules:**
  - `items`: Không rỗng, tổng số vé (ghế lẻ + vé standing) từ 1 đến 8.
  - Nếu `zoneType == SEATED`: Bắt buộc cung cấp `seatId`, `quantity = 1`.
  - Nếu `zoneType == STANDING`: `seatId = null`, `quantity >= 1`.
- **Response Body (201 Created):**
```json
{
  "holdId": 801,
  "eventId": 201,
  "userId": 1024,
  "status": "ACTIVE",
  "expiresAt": "2026-10-09T18:50:00Z",
  "ttlSeconds": 600,
  "totalAmount": "2900000.00",
  "items": [
    {
      "itemId": 1201,
      "zoneId": 10,
      "zoneName": "Khu VIP Khán Đài A",
      "seatId": 501,
      "seatLabel": "A-01",
      "unitPrice": "1200000.00",
      "quantity": 1
    },
    {
      "itemId": 1202,
      "zoneId": 10,
      "zoneName": "Khu VIP Khán Đài A",
      "seatId": 502,
      "seatLabel": "A-02",
      "unitPrice": "1200000.00",
      "quantity": 1
    },
    {
      "itemId": 1203,
      "zoneId": 11,
      "zoneName": "Khu Đứng Fanzone",
      "seatId": null,
      "seatLabel": "STANDING",
      "unitPrice": "250000.00",
      "quantity": 2
    }
  ]
}
```
- **Lỗi:**
  - `400 Bad Request`: Email chưa xác minh, chọn quá 8 vé, chọn khác sự kiện.
  - `409 Conflict`: Người dùng đang có một Hold khác chưa hết hạn; hoặc một trong các ghế được chọn đã bị giữ/bán bởi người khác; hoặc khu vực standing không còn đủ sức chứa trống.

### 2.2. GET `/api/me/hold`
- **Mục đích:** Xem Ticket Hold hiện tại đang còn hiệu lực của người mua.
- **Response Body (200 OK):** Trả về chi tiết Hold kèm số giây còn lại (`remainingSeconds`). Nếu không có Hold nào active, trả về `{"activeHold": null}`.

### 2.3. POST `/api/holds/{holdId}/cancel`
- **Mục đích:** Người dùng chủ động hủy giữ vé để nhường cho người khác hoặc để chọn lại ghế mới.
- **Response Body (200 OK):** Giải phóng ghế và chuyển trạng thái Hold sang `CANCELLED`.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Quy trình Thực thi Transaction Khóa Tồn kho (Pessimistic Concurrency Control)
- **Tuyệt đối không:** Dựa vào cache availability của Đông trên Redis để xác định ghế còn trống hay không! Redis chỉ mang tính tham khảo giao diện. Cơ sở dữ liệu SQL Server là chốt chặn cuối cùng.
- **Thứ tự Lock để chống Deadlock:**
  1. Khóa bản ghi Event: `SELECT id FROM dbo.tc_events WITH (UPDLOCK) WHERE id = :eventId`.
  2. Sắp xếp danh sách `zoneId` theo thứ tự tăng dần (`zoneId ASC`), khóa các Zone liên quan: `SELECT id FROM dbo.tc_zones WITH (UPDLOCK) WHERE id IN (...)`.
  3. Sắp xếp danh sách `seatId` theo thứ tự tăng dần (`seatId ASC`), khóa các Seat: `SELECT id, status FROM dbo.tc_seats WITH (UPDLOCK) WHERE id IN (...)`.
- **Gọi các Stored Procedure Canonical:**
  - `dbo.sp_ticket_hold_create`: Khởi tạo bản ghi trong `dbo.tc_ticket_holds`.
  - Với mỗi ghế SEATED: Gọi `dbo.sp_seat_hold`:
    ```sql
    EXEC dbo.sp_seat_hold @seat_id = :seatId, @hold_id = :holdId;
    ```
    - Trong SP: Kiểm tra `status == 'AVAILABLE'`. Cập nhật `status = 'HELD'`. Nếu không thỏa mãn, ném lỗi THROW làm rollback toàn bộ giao dịch!
  - Với vé STANDING: Gọi `dbo.sp_zone_hold_standing`:
    ```sql
    EXEC dbo.sp_zone_hold_standing @zone_id = :zoneId, @quantity = :qty;
    ```
    - Trong SP: Kiểm tra `held_standing + sold_standing + @quantity <= total_capacity`. Tăng `held_standing`.
  - Giá vé được chụp lại (`Price Snapshot`) trực tiếp từ `tc_zones.base_price` tại thời điểm giữ vé, khách hàng không được tự ý gửi giá từ client.

### 3.2. Quản lý Idempotency & Active Hold trên Redis
- **Idempotency Key:** `tc:idem:hold:<actorId>:<key>` (TTL 600s).
  - Sử dụng chuỗi Canonical Hash: `sha256(eventId + sortedSelections)`.
  - Replay cùng payload -> Trả về kết quả hold đã tạo kèm số giây còn lại.
- **Active Hold Tracking Key:** `tc:user:active-hold:<userId>` (lưu `holdId`, TTL 600s).
  - Giúp kiểm tra nhanh xem người dùng có đang giữ vé ở phiên nào không trước khi mở kết nối nặng vào DB.

### 3.3. Cơ chế Tự động Giải phóng Ghế Hết hạn (Hold Expiration Job)
- Xây dựng một luồng chạy nền (Background Scheduled Worker):
  - Định kỳ mỗi 15-30 giây gọi Stored Procedure:
    ```sql
    EXEC dbo.sp_release_expired_holds;
    ```
  - Trong SP: Tìm các bản ghi `dbo.tc_ticket_holds` có `status = 'ACTIVE'` và `expires_at < SYSDATETIME()`.
  - Tự động hoàn trả các ghế SEATED về `AVAILABLE`, trừ số lượng `held_standing` tương ứng, cập nhật hold thành `EXPIRED`.
  - Sau khi SP hoàn thành, phát sự kiện Invalidate cache ghế của các Event liên quan.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `CreateHoldRequestDto`, `HoldItemSelectionDto`, `HoldResponseDto`, `HoldItemDetailDto`.
   - Repositories: `TicketHoldRepository` (gọi các SP tạo hold, giữ ghế, giữ standing, release hold, kiểm tra active hold).
   - Core Services: `TicketHoldService`.
   - Workers: `HoldCleanupWorker` (dùng `ScheduledExecutorService` gọi `sp_release_expired_holds`).
   - Servlets: `TicketHoldServlet` (`/api/holds`), `MyActiveHoldServlet` (`/api/me/hold`), `CancelHoldServlet` (`/api/holds/{id}/cancel`).
2. **Giao diện JSP:**
   - Tích hợp thanh đếm ngược thời gian giữ vé (Countdown Timer: "Thời gian giữ vé còn lại: 09:59") trên màn hình đặt vé UI-04.
3. **Database Changes:**
   - Tạo bản ghi trong `dbo.tc_ticket_holds` (`status = 'ACTIVE'`, `expires_at = NOW + 10m`).
   - Tạo các bản ghi trong `dbo.tc_ticket_hold_items`.
   - Bảng `dbo.tc_seats` chuyển `status = 'HELD'`.
   - Bảng `dbo.tc_zones` tăng `held_standing`.
4. **Kiểm thử tự động:**
   - `TicketHoldServiceTest`: Kiểm tra chọn quá 8 vé bị chặn, user chưa verify email bị chặn.
   - `HoldConcurrencyTest`: Mô phỏng 10 luồng người dùng cùng tranh chấp 1 ghế duy nhất -> Đúng 1 luồng nhận 201 Created, 9 luồng nhận 409 Conflict; không bị vỡ kho hàng.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs và Bộ Validator**
  - Khai báo `CreateHoldRequestDto` kèm validation rule kiểm tra tổng vé từ 1 đến 8.
- **Bước 2: Triển khai `TicketHoldRepository`**
  - Viết logic thực thi transaction khóa hàng tồn kho với SP `sp_ticket_hold_create`, `sp_seat_hold`, `sp_zone_hold_standing`.
- **Bước 3: Xây dựng `TicketHoldService`**
  - Kiểm tra điều kiện `fn_user_is_eligible_buyer`.
  - Kiểm tra người dùng có đang giữ vé nào khác còn hạn không.
  - Mở transaction thực thi repository.
  - Lưu trữ `active-hold` vào Redis sau khi commit thành công.
- **Bước 4: Cài đặt `HoldCleanupWorker`**
  - Đăng ký `ServletContextListener` khởi động scheduler quét định kỳ 20 giây một lần gọi `sp_release_expired_holds`.
- **Bước 5: Xây dựng Servlets**
  - Xử lý các endpoint tạo hold, xem hold hiện tại và hủy hold.
- **Bước 6: Viết kiểm thử đa luồng (Concurrency Test)**
  - Chạy `mvn -B test` bảo đảm vượt qua toàn bộ bài kiểm tra tranh chấp ghế.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| HLD-01 | Giữ vé hợp lệ (1 ghế SEATED + 1 STANDING) | User đã verify email, ghế trống | 201 Created, DB chuyển ghế sang HELD, TTL 600s |
| HLD-02 | Tài khoản chưa xác minh email | User có is_email_verified = false | 400 Bad Request ("Vui lòng xác minh email trước khi mua vé") |
| HLD-03 | Chọn vượt quá 8 vé | Gửi 9 vé trong items | 400 Bad Request ("Chỉ được giữ tối đa 8 vé một lần") |
| HLD-04 | Tranh chấp ghế đồng thời (Race Condition) | 2 buyer bấm cùng 1 ghế tại cùng mili-giây | Đúng 1 buyer nhận 201, buyer còn lại nhận 409 Conflict |
| HLD-05 | Buyer đang có Hold chưa hết hạn | Tạo thêm Hold mới khi Hold cũ còn 5 phút | 409 Conflict ("Bạn đang có một phiên giữ vé đang hoạt động") |
| HLD-06 | Tự động hoàn trả khi hết hạn | Sau 10 phút không thanh toán | Worker giải phóng ghế về AVAILABLE, hold đổi thành EXPIRED |
