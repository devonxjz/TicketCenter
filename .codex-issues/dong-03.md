> **Convention:** `dev` → `feat/dong/dong-03-zone-seat-inventory` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa Event inventory, Zone/Seat controller-service-repository-cache, JSP layout editor (UI-13) và test tương ứng. Cấm sửa Hold/Order/Payment của Liêm.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng module cấu hình phân vùng khán đài (Zone), sơ đồ ghế ngồi (Seat Matrix) và quản lý hạn mức kho vé (Inventory Constraints):
- Phục vụ màn hình **UI-13 (Cấu hình Khu vực vé & Sơ đồ Ghế ngồi)**.
- Quản lý 2 loại hình khán đài:
  - **SEATED (Khu vực có ghế ngồi cố định):** Nhập số hàng ghế (`rows`) và số ghế mỗi hàng (`seatsPerRow`). Hệ thống tự động sinh ma trận ghế với mã hàng (A, B, C...) và số thứ tự ghế (1, 2, 3...). Mỗi ghế là một bản ghi duy nhất trong `dbo.tc_seats`.
  - **STANDING (Khu vực đứng tự do):** Cấu hình tổng sức chứa (`capacity`), không sinh ghế con.
- Ràng buộc tồn kho bất biến (Inventory Invariants):
  - Giá vé tiền Việt Nam Đồng (`BigDecimal`), không có phần thập phân, không âm.
  - Sức chứa tổng không được nhỏ hơn tổng số vé đã giữ hoặc đã bán (`held + sold <= capacity`).
  - Khi sự kiện đã `PUBLISHED`: Bất biến cấu trúc không được thay đổi (không thể xóa Zone, không thể giảm capacity dưới mức đã phát hành, không thể đổi loại từ SEATED sang STANDING). Chỉ được phép đổi giá vé nếu chưa có vé nào bán ra hoặc theo quy tắc SPEC.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. POST `/api/events/{eventId}/zones`
- **Mục đích:** Thêm một khu vực vé mới vào sự kiện.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body (cho loại SEATED):**
```json
{
  "name": "Khán Đài VIP A",
  "zoneType": "SEATED",
  "basePrice": "1200000",
  "rowCount": 5,
  "seatsPerRow": 10
}
```
- **Request Body (cho loại STANDING):**
```json
{
  "name": "Khu Đứng Fanzone",
  "zoneType": "STANDING",
  "basePrice": "450000",
  "capacity": 300
}
```
- **Validation Rules:**
  - `zoneType`: Bắt buộc là `SEATED` hoặc `STANDING`.
  - `basePrice`: `BigDecimal >= 0`.
  - Loại SEATED: `rowCount` (1–26 tương ứng A-Z), `seatsPerRow` (1–100). Tổng ghế sinh ra = `rowCount * seatsPerRow`.
  - Loại STANDING: `capacity >= 1`.
- **Response Body (201 Created):**
```json
{
  "zoneId": 15,
  "eventId": 201,
  "name": "Khán Đài VIP A",
  "zoneType": "SEATED",
  "basePrice": "1200000.00",
  "totalCapacity": 50,
  "generatedSeatsCount": 50
}
```

### 2.2. POST `/api/zones/{zoneId}/price`
- **Mục đích:** Điều chỉnh giá vé của Zone.
- **Request Body:** `{"newPrice": "1350000"}`.
- **Response Body (200 OK):** Trả về thông tin Zone với giá mới.

### 2.3. POST `/api/zones/{zoneId}/delete`
- **Mục đích:** Xóa một Zone khỏi sự kiện.
- **Lỗi 409 Conflict:** Nếu sự kiện đã `PUBLISHED` hoặc đã phát sinh giao dịch đặt/giữ vé.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Tương tác Stored Procedures & Database Persistence
- Gọi các Stored Procedure canonical trong `database/procedures.sql`:
  - `dbo.sp_event_add_zone`:
    ```sql
    EXEC dbo.sp_event_add_zone
         @event_id = :eventId,
         @name = :name,
         @zone_type = :zoneType,
         @base_price = :basePrice,
         @capacity = :capacity,
         @new_zone_id = :newZoneId OUTPUT;
    ```
  - `dbo.sp_zone_configure_seating`:
    ```sql
    EXEC dbo.sp_zone_configure_seating
         @zone_id = :zoneId,
         @row_count = :rowCount,
         @seats_per_row = :seatsPerRow;
    ```
    - Nội tại SP: Sinh tự động các hàng từ ASCII 'A' trở đi. Tạo các dòng trong `dbo.tc_seats` với trạng thái ban đầu `'AVAILABLE'`.
  - `dbo.sp_zone_change_price`:
    ```sql
    EXEC dbo.sp_zone_change_price
         @zone_id = :zoneId,
         @new_price = :newPrice;
    ```
  - `dbo.sp_event_remove_zone`:
    ```sql
    EXEC dbo.sp_event_remove_zone
         @zone_id = :zoneId;
    ```

### 3.2. Chống Xung đột Đồng thời (Concurrency Locking Order)
- Tránh Deadlock khi nhiều thao tác cấu hình diễn ra đồng thời:
  - Luôn acquire lock theo thứ tự ID tăng dần: Khóa Event ID trước, sau đó khóa Zone ID:
    ```sql
    SELECT id FROM dbo.tc_events WITH (UPDLOCK) WHERE id = :eventId;
    SELECT id FROM dbo.tc_zones WITH (UPDLOCK) WHERE id = :zoneId;
    ```
- Không bao giờ cho phép 2 request tạo trùng tên Zone trong cùng một sự kiện (`UNIQUE(event_id, name)`).

### 3.3. Invalidation Cache sau Thay đổi Cấu hình
- Sau khi Transaction thay đổi Zone/Seat commit thành công trong DB:
  - Xóa cache chi tiết sự kiện: `DEL tc:event:detail:v1:<eventId>`
  - Xóa cache danh sách zones: `DEL tc:event:zones:v1:<eventId>`
  - Xóa cache sơ đồ ghế: `DEL tc:zone:seats:v1:<zoneId>`
  - Xóa cache danh mục catalog: `DEL tc:event:list:v1:*`

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `CreateZoneRequestDto`, `ZonePriceChangeDto`, `SeatLayoutMatrixDto`, `SeatMatrixRowDto`.
   - Repositories: `ZoneRepository` (gọi các SP thêm zone, cấu hình ghế, đổi giá, xóa zone; kiểm tra ràng buộc capacity).
   - Core Services: `ZoneInventoryService`.
   - Servlets: `EventZonesServlet`, `ZoneManagementServlet`.
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/event/organizer/zone-config.jsp`: Form thêm Zone (chọn Seated hoặc Standing), bảng danh sách Zone hiện có, modal trực quan sơ đồ ghế.
3. **Database Changes:**
   - Bảng `dbo.tc_zones` thêm bản ghi mới.
   - Bảng `dbo.tc_seats` phát sinh N bản ghi ghế tương ứng `rowCount * seatsPerRow` với `status = 'AVAILABLE'`.
4. **Kiểm thử tự động:**
   - `ZoneInventoryServiceTest`: Kiểm tra sinh mã ghế A-01, B-05, kiểm tra giá không âm, chặn sửa layout khi sự kiện đã PUBLISHED.
   - `ZoneConcurrencyTest`: 2 request cùng tạo Zone cùng tên chỉ 1 request thành công.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs và Validation Rules**
  - Tạo `CreateZoneRequestDto` kiểm tra nếu `zoneType == SEATED` thì bắt buộc có `rowCount` và `seatsPerRow`.
- **Bước 2: Xây dựng `ZoneRepository`**
  - Sử dụng JDBC / CallableStatement để thực thi `dbo.sp_event_add_zone` và `dbo.sp_zone_configure_seating` trong cùng một transaction.
- **Bước 3: Xây dựng `ZoneInventoryService`**
  - Kiểm tra xem Event có đang thuộc quyền quản lý của Actor không.
  - Kiểm tra trạng thái Event: Nếu `PUBLISHED`, chặn thay đổi sơ đồ ghế.
  - Sau khi ghi DB, gọi `eventRedisCacheService.invalidateEventZones(eventId)`.
- **Bước 4: Xây dựng Servlets**
  - Xử lý các request POST thêm Zone, đổi giá vé, xóa Zone.
- **Bước 5: Xây dựng Giao diện JSP (UI-13)**
  - Hiển thị ma trận ghế dạng bảng grid trực quan để người tổ chức xem trước layout.
- **Bước 6: Chạy kiểm thử tự động**
  - Kiểm tra `mvn -B test` bảo đảm 100% ca test đều pass.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| ZON-01 | Thêm Zone SEATED thành công | rows=3, seatsPerRow=4 | 201 Created, DB tạo 1 Zone và 12 Seat từ A-01 đến C-04 |
| ZON-02 | Thêm Zone STANDING thành công | capacity=500 | 201 Created, DB tạo 1 Zone có totalCapacity=500, không có ghế lẻ |
| ZON-03 | Giá vé âm | basePrice = -50000 | 400 Bad Request |
| ZON-04 | Sửa layout khi Event đã PUBLISHED | Event status = PUBLISHED | 409 Conflict |
| ZON-05 | Xóa Zone đã có vé giữ chỗ | Zone có heldSeats > 0 | 409 Conflict, DB không xóa |
