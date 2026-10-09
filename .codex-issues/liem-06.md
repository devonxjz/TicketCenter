> **Convention:** `dev` → `feat/liem/liem-06-order-history-coupon-admin` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Liêm sở hữu toàn bộ các màn hình và endpoint: Lịch sử đơn hàng cá nhân (UI-06/UI-07), Quản lý mã giảm giá của Ban tổ chức và Admin hệ thống (UI-14). Không can thiệp vào Admin Event (Đông), Admin Refund (Khánh) hay Admin Finance (Vương).

## 1. Mục tiêu và Nghiệp vụ

Xây dựng hệ thống tra cứu Lịch sử đơn hàng của người mua và Quản trị danh mục Mã giảm giá (Coupon Administration):
- **Phía Khách hàng (Buyer):**
  - **UI-06 (Lịch sử mua vé & Đơn hàng):** Xem danh sách tất cả các đơn hàng đã đặt kèm trạng thái thanh toán (`PENDING_PAYMENT`, `PAID`, `CANCELLED`, `REFUNDED`).
  - **UI-07 (Chi tiết đơn hàng):** Xem biên lai điện tử, danh sách vé đã mua, phương thức thanh toán, chi tiết giảm giá và thông tin bồi hoàn nếu có.
- **Phía Ban tổ chức sự kiện & Admin (Manager / Admin):**
  - **UI-14 (Quản lý mã khuyến mãi của Tổ chức):** Tạo mã coupon mới (loại phần trăm hoặc giảm tiền cố định), thiết lập hạn mức số lượt dùng (`max_uses`), thời hạn hiệu lực, bật/tắt kích hoạt mã.
  - Quản trị viên hệ thống (`PlatformRole.ADMIN`) có quyền xem danh sách coupon trên toàn sàn hoặc tạo coupon khuyến mãi dùng chung.
  - **Ràng buộc an toàn tồn kho mã:** Tuyệt đối không cho phép giảm `max_uses` xuống thấp hơn số lượt đã được sử dụng thực tế (`used_count`). Không cho phép xóa vật lý (hard delete) coupon nếu đã có bất kỳ đơn hàng nào từng áp dụng mã này (chỉ cho phép chuyển sang `INACTIVE`).

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/me/orders`
- **Mục đích:** Khách hàng xem danh sách đơn hàng của chính mình.
- **Quyền hạn:** `AUTHENTICATED`.
- **Query Params:** `status` (tùy chọn), `page` (mặc định 1), `pageSize` (mặc định 10).
- **Response Body (200 OK):**
```json
{
  "items": [
    {
      "orderId": 5001,
      "orderCode": "ORD-20261009-801A",
      "eventName": "Đêm Nhạc Mùa Thu 2026",
      "totalAmount": "2320000.00",
      "status": "PAID",
      "createdAt": "2026-10-09T18:25:00Z",
      "ticketCount": 2
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 10
}
```

### 2.2. GET `/api/orders/{orderId}`
- **Mục đích:** Xem chi tiết hóa đơn đơn hàng.
- **Lỗi 403 Forbidden:** Nếu đơn hàng không thuộc về người đang đăng nhập (hoặc không phải Admin).

### 2.3. GET `/api/organizations/{organizationId}/coupons`
- **Mục đích:** Ban tổ chức xem danh sách các mã giảm giá của tổ chức mình.
- **Quyền hạn:** `MANAGER` của chính `organizationId`.
- **Response Body (200 OK):**
```json
[
  {
    "couponId": 88,
    "code": "VIPAUTUMN20",
    "discountType": "PERCENTAGE",
    "discountValue": "20.00",
    "maxUses": 100,
    "usedCount": 35,
    "validFrom": "2026-10-01T00:00:00Z",
    "validTo": "2026-11-15T23:59:59Z",
    "isActive": true
  }
]
```

### 2.4. POST `/api/organizations/{organizationId}/coupons`
- **Mục đích:** Tạo mã giảm giá mới cho tổ chức.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "code": "EARLYBIRD10",
  "discountType": "PERCENTAGE",
  "discountValue": "10.00",
  "maxUses": 50,
  "validFrom": "2026-10-15T00:00:00Z",
  "validTo": "2026-10-31T23:59:59Z"
}
```

### 2.5. POST `/api/coupons/{couponId}/deactivate` & `/activate`
- **Mục đích:** Tạm dừng hoặc kích hoạt lại mã coupon.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Gọi Stored Procedures Quản trị Coupon Canonical
- Không viết update trực tiếp bảng `dbo.tc_coupons`. Gọi các Stored Procedure có sẵn:
  - `dbo.sp_coupon_activate`:
    ```sql
    EXEC dbo.sp_coupon_activate
         @coupon_id = :couponId,
         @manager_user_id = :actorId;
    ```
  - `dbo.sp_coupon_deactivate`:
    ```sql
    EXEC dbo.sp_coupon_deactivate
         @coupon_id = :couponId,
         @manager_user_id = :actorId;
    ```
  - `dbo.sp_coupon_revise_terms`:
    ```sql
    EXEC dbo.sp_coupon_revise_terms
         @coupon_id = :couponId,
         @new_max_uses = :newMaxUses,
         @new_valid_to = :newValidTo,
         @manager_user_id = :actorId;
    ```
    - Trong SP: Kiểm tra `@new_max_uses >= used_count`. Nếu vi phạm: Ném lỗi `INVALID_MAX_USES_REDUCTION`.

### 3.2. Bảo vệ Đơn hàng Cá nhân & Caching Phù hợp
- Dữ liệu đơn hàng là thông tin tài chính cá nhân nhạy cảm:
  - **Tuyệt đối không lưu cache public:** Cache danh sách đơn hàng phải được định danh theo Actor: `tc:user:orders:v1:<actorId>:<page>`.
  - Không bao giờ trả về các thuộc tính thanh toán bí mật (như secret hash, VNPAY raw params) trong Order Detail DTO.

### 3.3. Multi-tenancy & Phân tách Dữ liệu Tổ chức
- Khi Manager gọi danh sách Coupon:
  - Backend đối chiếu quyền qua `SecurityContext` và `OrganizationMembershipRepository`.
  - Query luôn gắn chặt điều kiện `organization_id = :organizationId` từ session được xác thực.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `OrderSummaryDto`, `OrderDetailFullDto`, `CouponCreateRequestDto`, `CouponReviseRequestDto`, `CouponDetailDto`.
   - Repositories: `OrderHistoryRepository` (truy vấn qua `dbo.v_orders`, `dbo.v_order_items`, `dbo.order_totals`), `CouponAdminRepository` (gọi các SP activate, deactivate, revise terms).
   - Core Services: `OrderHistoryService`, `CouponAdminService`.
   - Servlets: `MyOrdersServlet` (`/api/me/orders`), `OrderDetailServlet` (`/api/orders/{id}`), `OrgCouponsServlet` (`/api/organizations/{orgId}/coupons`), `CouponActionServlet` (`/api/coupons/{id}/*`).
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/order/order-history.jsp`: Danh sách đơn hàng UI-06 (mã đơn, ngày mua, tổng tiền, badge trạng thái thanh toán, nút Xem chi tiết).
   - `src/main/webapp/WEB-INF/views/order/order-detail.jsp`: Giao diện hóa đơn UI-07 (thông tin người mua, chi tiết từng vé, giá gốc, số tiền coupon giảm giá, mã tham chiếu VNPAY).
   - `src/main/webapp/WEB-INF/views/coupon/coupon-list.jsp`: Màn hình UI-14 cho Ban tổ chức quản lý mã giảm giá, hiển thị số lượt đã dùng / tổng lượt, nút bật/tắt mã.
3. **Database Changes:**
   - Cập nhật trạng thái `is_active`, `max_uses`, `valid_to` trong `dbo.tc_coupons`.
4. **Kiểm thử tự động:**
   - `OrderHistoryServiceTest`: Kiểm tra Buyer A không đọc được đơn của Buyer B, kiểm tra phân trang đơn hàng.
   - `CouponAdminServiceTest`: Kiểm tra hạ max_uses dưới số đã dùng bị từ chối, kiểm tra Manager tổ chức khác can thiệp coupon bị chặn (403).

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs cho Lịch sử đơn hàng và Coupon**
  - Khai báo các thuộc tính hiển thị, bảo đảm kiểu tiền tệ `BigDecimal`.
- **Bước 2: Triển khai `OrderHistoryRepository` & `CouponAdminRepository`**
  - Sử dụng JPA / JDBC truy vấn các View `dbo.v_orders` và gọi các SP quản trị coupon.
- **Bước 3: Xây dựng `OrderHistoryService` và `CouponAdminService`**
  - Đảm bảo kiểm tra phân quyền chặt chẽ trước khi xử lý.
- **Bước 4: Xây dựng Servlets**
  - Định tuyến các endpoint `/api/me/orders` và `/api/organizations/{id}/coupons`.
- **Bước 5: Xây dựng Giao diện JSP (UI-06, UI-07, UI-14)**
  - Tích hợp giao diện hiển thị hóa đơn và bảng quản lý coupon trực quan.
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| HIS-01 | Xem danh sách đơn hàng của chính mình | Buyer đã login, có 2 đơn hàng | 200 OK, trả về đúng 2 đơn hàng kèm số lượng vé |
| HIS-02 | Xem đơn hàng của người khác | Buyer A truyền orderId của Buyer B | 403 Forbidden |
| HIS-03 | Tạo mã Coupon thành công | Manager hợp lệ, discount 15%, maxUses=100 | 201 Created, DB lưu mã mới với status active |
| HIS-04 | Giảm maxUses dưới số đã dùng | Coupon có used_count=30, thử sửa max_uses=20 | 400 Bad Request ("max_uses không được nhỏ hơn số lượt đã dùng") |
| HIS-05 | Manager tổ chức A sửa coupon của tổ chức B | Actor không thuộc tổ chức sở hữu coupon | 403 Forbidden |
