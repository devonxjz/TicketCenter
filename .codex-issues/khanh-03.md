> **Convention:** `dev` → `feat/khanh/khanh-03-refund-request` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Chỉ sửa code tiếp nhận yêu cầu hoàn tiền trong `fulfillment/refund/controller/**`, `fulfillment/refund/service/**`, `fulfillment/refund/repository/**`, `fulfillment/refund/dto/**`, JSP yêu cầu hoàn tiền (UI-08) và test tương ứng. Cấm sửa Order/Ticket model hoặc migration 026.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng module Khách hàng gửi Yêu cầu Hoàn tiền Vé (Customer Ticket Refund Request):
- Phục vụ trực tiếp màn hình **UI-08 (Gửi Yêu cầu Hoàn vé & Theo dõi tiến độ hoàn tiền)**.
- Ràng buộc nghiệp vụ bảo vệ hệ thống:
  - **Chỉ chủ sở hữu đơn hàng (`BUYER`)** mới có quyền yêu cầu hoàn tiền cho các vé thuộc đơn hàng của mình.
  - **Tất cả các vé yêu cầu hoàn tiền trong một đợt phải thuộc về cùng một đơn hàng**.
  - **Điều kiện vé hợp lệ để hoàn:**
    - Trạng thái vé phải là `ISSUED`. Tuyệt đối không cho phép hoàn vé đã soát vé vào cửa (`CHECKED_IN`).
    - Vé chưa có yêu cầu hoàn tiền nào khác đang chờ xử lý (`REFUND_PENDING`).
    - Thời điểm gửi yêu cầu phải **diễn ra trước thời gian bắt đầu sự kiện (`event.startTime`)** theo chính sách hủy vé của Ban tổ chức.
  - **Bảo mật giá trị tiền tệ:** Số tiền hoàn (`refundAmount`) được tính toán tự động bằng tổng `paidAmount` của các vé được chọn trong database. **Khách hàng tuyệt đối không được tự gửi số tiền cần hoàn từ giao diện**.
  - **Đóng băng vé:** Ngay khi gửi yêu cầu thành công, các vé liên quan lập tức được chuyển sang trạng thái `REFUND_PENDING` để ngăn chặn khách hàng mang vé đi check-in tại cổng hoặc sang nhượng vé trong lúc chờ duyệt.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/orders/{orderId}/refundable-tickets`
- **Mục đích:** Lấy danh sách các vé trong đơn hàng đủ điều kiện để yêu cầu hoàn tiền.
- **Quyền hạn:** `AUTHENTICATED` (chủ sở hữu đơn hàng).
- **Response Body (200 OK):**
```json
[
  {
    "ticketId": 10001,
    "ticketCode": "TC-9F8A-4B2C-8D1E",
    "seatLabel": "A-01",
    "paidAmount": "1160000.00",
    "status": "ISSUED",
    "isRefundable": true,
    "unrefundableReason": null
  },
  {
    "ticketId": 10002,
    "ticketCode": "TC-9F8A-4B2C-8D1F",
    "seatLabel": "A-02",
    "paidAmount": "1160000.00",
    "status": "CHECKED_IN",
    "isRefundable": false,
    "unrefundableReason": "Vé đã được điểm danh vào cửa."
  }
]
```

### 2.2. POST `/api/refund-requests`
- **Mục đích:** Gửi hồ sơ yêu cầu hoàn tiền cho danh sách vé đã chọn.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "orderId": 5001,
  "ticketIds": [10001],
  "reason": "Bận việc đột xuất không thể tham dự chương trình."
}
```
- **Validation Rules:**
  - `ticketIds`: Danh sách không rỗng, tối đa bằng số vé của đơn hàng.
  - `reason`: Không rỗng, 10–500 ký tự.
- **Response Body (201 Created):**
```json
{
  "refundId": 3001,
  "orderId": 5001,
  "totalRefundAmount": "1160000.00",
  "status": "REQUESTED",
  "ticketCount": 1,
  "createdAt": "2026-10-09T18:40:00Z",
  "message": "Yêu cầu hoàn tiền đã được tiếp nhận và chuyển đến Ban quản trị xem xét."
}
```
- **Lỗi:**
  - `400 Bad Request`: Vé không đủ điều kiện, đã qua thời hạn hoàn vé.
  - `403 Forbidden`: Đơn hàng không thuộc về người dùng hiện tại.
  - `409 Conflict`: Một trong các vé đang có yêu cầu hoàn tiền khác chưa xử lý xong.

### 2.3. GET `/api/me/refund-requests`
- **Mục đích:** Xem danh sách các yêu cầu hoàn tiền của người dùng hiện tại.

### 2.4. GET `/api/refund-requests/{refundId}`
- **Mục đích:** Xem chi tiết tiến độ xử lý của một yêu cầu hoàn tiền cụ thể.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Gọi Stored Procedure Canonical Khởi tạo Hoàn tiền
- Sử dụng Stored Procedure `dbo.sp_refund_request_for_tickets`:
  ```sql
  EXEC dbo.sp_refund_request_for_tickets
       @order_id = :orderId,
       @buyer_user_id = :actorId,
       @ticket_ids_csv = :ticketIdsCsv,
       @reason = :reason,
       @new_refund_id = :refundId OUTPUT;
  ```
  - Trong nội tại SP:
    1. Kiểm tra quyền sở hữu đơn hàng: `orders.user_id == @buyer_user_id`.
    2. Sắp xếp danh sách `ticket_id` theo thứ tự tăng dần và khóa dòng:
       `SELECT id, status, paid_amount FROM dbo.tc_tickets WITH (UPDLOCK, ROWLOCK) WHERE id IN (...)`.
    3. Kiểm tra điều kiện từng vé: Phải có `status = 'ISSUED'` và `order_id = @order_id`. Nếu vi phạm, ném lỗi và Rollback.
    4. Kiểm tra thời gian: Sự kiện chưa diễn ra (`events.start_time > SYSDATETIME()`).
    5. Tính toán tổng số tiền hoàn: `@total_amount = SUM(paid_amount)`.
    6. Tạo bản ghi mới trong `dbo.tc_refunds` với `status = 'REQUESTED'`, `type = 'CUSTOMER_REQUEST'`.
    7. Tạo các bản ghi liên kết trong `dbo.tc_refund_tickets`.
    8. Cập nhật trạng thái các vé sang `REFUND_PENDING` (`dbo.sp_ticket_mark_refund_pending`).
    9. Ghi vết kiểm toán vào `dbo.tc_audit_logs`.

### 3.2. Chống Tranh chấp Yêu cầu Hoàn tiền Đồng thời (Concurrency & Idempotency)
- Sử dụng Redis Distributed Lock trên danh sách ID vé đã sắp xếp:
  - Key: `tc:lock:refund-tickets:<ticketId1>_<ticketId2>` (TTL: 10 giây).
  - Ngăn ngừa tình trạng người dùng mở 2 tab trình duyệt cùng lúc và bấm gửi yêu cầu hoàn tiền cho cùng 1 chiếc vé.
- **Idempotency Key:** `tc:idem:refund-request:<actorId>:<key>` (TTL: 86400s).

### 3.3. Invalidation Cache sau Ghi nhận Hoàn tiền
- Sau khi Transaction DB commit:
  - Xóa cache chi tiết vé: `DEL tc:ticket:detail:v1:<ticketId>`
  - Xóa cache mã QR của vé: `DEL tc:ticket:qr:v1:<ticketId>:*` (để QR lập tức mất hiệu lực nếu quét tại cổng).
  - Xóa cache danh sách vé của người dùng: `DEL tc:user:tickets:v1:<actorId>:*`

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `CreateRefundRequestDto`, `RefundTicketOptionDto`, `RefundSummaryDto`, `RefundDetailDto`.
   - Repositories: `RefundRequestRepository` (gọi `dbo.sp_refund_request_for_tickets`, truy vấn qua `dbo.v_refunds`, `dbo.v_refund_tickets`).
   - Core Services: `RefundRequestService`.
   - Servlets: `RefundRequestServlet` (`/api/refund-requests`), `OrderRefundableTicketsServlet` (`/api/orders/{id}/refundable-tickets`), `MyRefundRequestsServlet` (`/api/me/refund-requests`).
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/refund/request-form.jsp`: Màn hình UI-08 cho phép tick chọn các vé muốn hoàn tiền trong đơn hàng, hiển thị số tiền hoàn dự kiến, ô nhập lý do hoàn vé, hộp thoại xác nhận chính sách.
   - `src/main/webapp/WEB-INF/views/refund/my-refunds.jsp`: Bảng theo dõi danh sách các yêu cầu hoàn tiền, badge trạng thái (Vàng: Chờ duyệt, Xanh: Đã hoàn tiền, Đỏ: Bị từ chối kèm lý do).
3. **Database Changes:**
   - Tạo bản ghi mới trong `dbo.tc_refunds` (`status = 'REQUESTED'`).
   - Các vé liên quan trong `dbo.tc_tickets` chuyển `status = 'REFUND_PENDING'`.
4. **Kiểm thử tự động:**
   - `RefundRequestServiceTest`: Kiểm tra vé đã CHECKED_IN không cho hoàn, kiểm tra số tiền hoàn tính đúng từ DB, kiểm tra không hoàn được vé của người khác.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs cho Yêu cầu Hoàn vé**
  - Khai báo `CreateRefundRequestDto` có validation danh sách vé không được rỗng.
- **Bước 2: Xây dựng `RefundRequestRepository`**
  - Sử dụng JDBC CallableStatement gọi Stored Procedure `dbo.sp_refund_request_for_tickets`.
- **Bước 3: Xây dựng `RefundRequestService`**
  - Kiểm tra quyền sở hữu đơn hàng của Actor.
  - Mở transaction thực thi lưu yêu cầu hoàn tiền và khóa trạng thái vé.
  - Thực hiện xóa cache mã QR của vé trên Redis sau khi commit thành công.
- **Bước 4: Xây dựng Servlets**
  - Định tuyến các endpoint `/api/refund-requests` và `/api/orders/{id}/refundable-tickets`.
- **Bước 5: Xây dựng Giao diện JSP (UI-08)**
  - Tích hợp giao diện chọn vé hoàn và bảng theo dõi tiến độ.
- **Bước 6: Chạy kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| RFD-01 | Yêu cầu hoàn vé hợp lệ | Vé status ISSUED, trước giờ diễn | 201 Created, DB tạo refund status REQUESTED, vé thành REFUND_PENDING |
| RFD-02 | Hoàn vé đã điểm danh vào cửa | Vé status CHECKED_IN | 400 Bad Request ("Không thể hoàn vé đã sử dụng") |
| RFD-03 | Hoàn vé của người khác | Buyer A gửi ticketId của Buyer B | 403 Forbidden |
| RFD-04 | Hoàn vé khi sự kiện đã bắt đầu | Hiện tại > startTime | 400 Bad Request ("Đã quá thời hạn cho phép yêu cầu hoàn vé") |
| RFD-05 | Gửi yêu cầu lặp cho cùng 1 vé | Vé đang là REFUND_PENDING | 409 Conflict ("Vé đang có yêu cầu hoàn tiền chờ xử lý") |
