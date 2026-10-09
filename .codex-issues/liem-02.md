> **Convention:** `dev` → `feat/liem/liem-02-order-coupon` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa code trong `order/**`, DTO/cache/JSP thanh toán (trong UI-04) và test tương ứng. Tuyệt đối không can thiệp vào Event của Đông, Refund của Khánh, Settlement của Vương, hay model/config/database/OpenAPI.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng quy trình khởi tạo Đơn hàng (Order) từ Phiên giữ vé (Ticket Hold), đóng băng bảng giá (Snapshot Order Items) và áp dụng Mã giảm giá (Coupon) có kiểm soát hạn mức cạnh tranh (Quota Concurrency):
- Phục vụ trực tiếp màn hình **UI-04 (Màn hình Thanh toán & Áp dụng Mã giảm giá)**.
- Ràng buộc nghiệp vụ quan trọng:
  - **Quan hệ 1-1 giữa Hold và Order:** Mỗi `TicketHold` hợp lệ chỉ được phép chuyển đổi thành đúng duy nhất 1 `Order`. Không được phép tạo nhiều đơn hàng từ cùng một lượt giữ vé.
  - **Đóng băng giá trị (Immutable Order Snapshot):** Thông tin đơn giá tại thời điểm tạo đơn hàng được ghi nhận vĩnh viễn vào `dbo.tc_order_items`, không thay đổi ngay cả khi Ban tổ chức điều chỉnh giá vé của Zone sau đó.
  - **Quy tắc Mã giảm giá (Coupon Policy):**
    - Mã giảm giá phải thuộc về chính Tổ chức tổ chức sự kiện đó (trừ mã khuyến mãi toàn sàn của Admin).
    - Phải đang trong thời hạn hiệu lực (`fn_coupon_is_active_at`), trạng thái `ACTIVE`.
    - Hạn mức sử dụng tối đa (`max_uses`): Đảm bảo không bị vượt quá số lượng cho phép khi có nhiều người cùng nhập mã giảm giá tại cùng một thời điểm.
    - **Trần giảm giá tối đa (Discount Cap):** Số tiền giảm giá không được vượt quá 30% tổng tiền vé ban đầu (`subtotal`), bất kể loại giảm phần trăm hay số tiền cố định.
  - Toàn bộ số tiền là số nguyên Việt Nam Đồng (`BigDecimal` không có phần thập phân). Khách hàng không được quyền gửi số tiền cần thanh toán từ trình duyệt.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. POST `/api/orders`
- **Mục đích:** Chuyển đổi một Ticket Hold thành Đơn hàng chính thức (trạng thái ban đầu `PENDING_PAYMENT`).
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "holdId": 801
}
```
- **Response Body (201 Created):**
```json
{
  "orderId": 5001,
  "orderCode": "ORD-20261009-801A",
  "userId": 1024,
  "eventId": 201,
  "holdId": 801,
  "subtotal": "2900000.00",
  "discountAmount": "0.00",
  "totalAmount": "2900000.00",
  "status": "PENDING_PAYMENT",
  "expiresAt": "2026-10-09T18:50:00Z",
  "items": [
    {
      "orderItemId": 9001,
      "zoneName": "Khu VIP Khán Đài A",
      "seatLabel": "A-01",
      "unitPrice": "1200000.00",
      "quantity": 1,
      "lineTotal": "1200000.00"
    }
  ]
}
```
- **Lỗi:**
  - `400 Bad Request`: Hold đã hết hạn hoặc không tồn tại.
  - `403 Forbidden`: Hold không thuộc về người dùng hiện tại.
  - `409 Conflict`: Hold này đã được chuyển thành một Order trước đó.

### 2.2. POST `/api/orders/{orderId}/coupon`
- **Mục đích:** Áp dụng hoặc gỡ bỏ mã giảm giá cho đơn hàng.
- **Request Body (Áp dụng mã):**
```json
{
  "couponCode": "VIPAUTUMN20"
}
```
- **Request Body (Gỡ bỏ mã):**
```json
{
  "couponCode": null
}
```
- **Response Body (200 OK):**
```json
{
  "orderId": 5001,
  "appliedCouponCode": "VIPAUTUMN20",
  "subtotal": "2900000.00",
  "discountAmount": "580000.00",
  "totalAmount": "2320000.00",
  "message": "Áp dụng mã giảm giá thành công."
}
```
- **Lỗi:** `400 Bad Request` (mã không tồn tại, hết hạn, hết lượt sử dụng, hoặc không áp dụng cho tổ chức này).

### 2.3. GET `/api/orders/{orderId}/coupon-eligibility`
- **Mục đích:** Kiểm tra tính hợp lệ và số tiền giảm giá dự kiến của một mã coupon trước khi chính thức áp dụng.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Quy trình Chuyển đổi Hold sang Order qua Stored Procedure
- Sử dụng Stored Procedure canonical `dbo.sp_order_from_hold`:
  ```sql
  EXEC dbo.sp_order_from_hold
       @hold_id = :holdId,
       @buyer_user_id = :actorId,
       @new_order_id = :orderId OUTPUT;
  ```
  - Trong nội tại SP:
    1. Kiểm tra tính hợp lệ của Hold: `status == 'ACTIVE'` và `expires_at > SYSDATETIME()`.
    2. Đánh dấu Hold sang `CONSUMED` (`dbo.sp_ticket_hold_consume`).
    3. Tạo bản ghi trong `dbo.tc_orders` với `status = 'PENDING_PAYMENT'`.
    4. Sao chép toàn bộ các dòng từ `dbo.tc_ticket_hold_items` sang `dbo.tc_order_items`.
    5. Tính toán `subtotal` ban đầu qua `dbo.fn_order_subtotal(@new_order_id)`.

### 3.2. Quản lý Hạn ngạch Mã giảm giá Cạnh tranh (Coupon Quota Concurrency)
- Gọi Stored Procedure `dbo.sp_order_apply_coupon`:
  ```sql
  EXEC dbo.sp_order_apply_coupon
       @order_id = :orderId,
       @coupon_code = :couponCode;
  ```
  - Cơ chế khóa chống vượt hạn ngạch:
    - SP thực hiện khóa bản ghi Coupon bằng `WITH (UPDLOCK, ROWLOCK)` trong `dbo.tc_coupons`.
    - Kiểm tra: `used_count < max_uses`. Nếu đã đạt trần, ném lỗi `COUPON_QUOTA_EXCEEDED`.
    - Kiểm tra tổ chức phát hành: Tổ chức của Coupon phải trùng với tổ chức sở hữu Event của Order.
    - Áp dụng hàm tính toán giảm giá: `dbo.fn_coupon_calculate_discount(@coupon_id, @subtotal)`.
    - Ràng buộc trần giảm giá: `discount = MIN(@calculated_discount, @subtotal * 0.30)`.
    - Cập nhật đơn hàng: `discount_amount = @discount`, `total_amount = @subtotal - @discount`, `coupon_id = @coupon_id`.
    - Tăng tạm thời `used_count = used_count + 1` (hoặc đánh dấu bản ghi reservation trong `dbo.tc_coupon_redemptions`).

### 3.3. Idempotency & Cache Management
- **Idempotency Key:** `tc:idem:order:<actorId>:<key>` (TTL 600s).
  - Tránh tình trạng mạng chập chờn khiến người dùng bấm thanh toán 2 lần sinh 2 đơn hàng trùng.
- **Cache Order Summary:** `tc:order:summary:<orderId>` (TTL bằng thời gian còn lại của Hold).
  - Tự động xóa hoặc refresh cache này ngay sau khi có sự thay đổi về mã giảm giá.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `CreateOrderRequestDto`, `ApplyCouponRequestDto`, `OrderDetailDto`, `OrderItemDetailDto`, `CouponEligibilityResponseDto`.
   - Repositories: `OrderRepository` (gọi các SP `sp_order_from_hold`, `sp_order_apply_coupon`, `sp_order_remove_coupon`, truy vấn qua `dbo.v_orders`, `dbo.order_totals`).
   - Core Services: `OrderService`.
   - Servlets: `OrderCreationServlet`, `OrderCouponServlet`, `OrderEligibilityServlet`.
2. **Giao diện JSP:**
   - Màn hình UI-04: Bảng tóm tắt đơn hàng, danh sách ghế đã chọn, ô nhập mã giảm giá có nút "Áp dụng", dòng hiển thị số tiền giảm trừ chi tiết và tổng tiền cuối cùng.
3. **Database Changes:**
   - Bảng `dbo.tc_ticket_holds` đổi trạng thái thành `'CONSUMED'`.
   - Bảng `dbo.tc_orders` thêm 1 bản ghi mới (`status = 'PENDING_PAYMENT'`).
   - Bảng `dbo.tc_order_items` lưu danh sách vé với giá đóng băng.
   - Bảng `dbo.tc_coupons` cập nhật `used_count`.
4. **Kiểm thử tự động:**
   - `OrderServiceTest`: Kiểm tra áp dụng mã coupon sai tổ chức bị từ chối, kiểm tra trần giảm 30%.
   - `CouponQuotaConcurrencyTest`: 20 đơn hàng cùng nhập 1 mã chỉ còn 1 lượt dùng -> Đúng 1 đơn hàng được giảm giá, 19 đơn hàng còn lại nhận lỗi hết lượt.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Khai báo DTOs**
  - Tạo `CreateOrderRequestDto`, `ApplyCouponRequestDto` kèm các trường tiền tệ kiểu `BigDecimal`.
- **Bước 2: Xây dựng `OrderRepository`**
  - Thực thi `dbo.sp_order_from_hold` và `dbo.sp_order_apply_coupon` qua JDBC CallableStatement.
- **Bước 3: Xây dựng `OrderService`**
  - Đảm bảo kiểm tra người mua sở hữu Hold.
  - Quản lý transaction DB và cập nhật cache Redis.
- **Bước 4: Xây dựng Servlets**
  - Định tuyến các endpoint `/api/orders` và `/api/orders/{id}/coupon`.
- **Bước 5: Xây dựng Giao diện JSP (UI-04)**
  - Hiển thị breakdown số tiền: Tiền vé gốc, Tiền giảm giá, Tổng thanh toán.
- **Bước 6: Chạy kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| ORD-01 | Tạo Order từ Hold hợp lệ | Hold đang ACTIVE của chính Buyer | 201 Created, Hold thành CONSUMED, Order thành PENDING_PAYMENT |
| ORD-02 | Tạo Order từ Hold của người khác | Buyer A truyền holdId của Buyer B | 403 Forbidden |
| ORD-03 | Tạo Order từ Hold đã hết hạn | Hold status = EXPIRED | 400 Bad Request |
| ORD-04 | Áp dụng Coupon vượt trần 30% | Mã giảm 50% trên đơn 1,000,000 | 200 OK, số tiền giảm bị chặn ở mức tối đa 300,000 (30%) |
| ORD-05 | Tranh chấp lượt dùng cuối cùng của Coupon | max_uses = 10, used_count = 9, 2 buyer cùng áp mã | 1 buyer thành công, 1 buyer nhận 400 ("Mã giảm giá đã hết lượt dùng") |
