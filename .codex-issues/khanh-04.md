> **Convention:** `dev` → `feat/khanh/khanh-04-admin-refund-decision` → PR `dev`; chỉ merge khi Actions xanh và có review.
> **Ownership:** Khánh sở hữu toàn bộ các chức năng Quản trị viên xét duyệt yêu cầu hoàn tiền (UI-21). Tuyệt đối không can thiệp vào Admin Event (Đông), Admin Tổ chức (Thái), Admin Coupon (Liêm) hay Admin Tài chính (Vương).

## 1. Mục tiêu và Nghiệp vụ

Xây dựng cổng Quản trị viên hệ thống thẩm định, ra quyết định Chấp thuận (`Approve`) hoặc Từ chối (`Reject`) các yêu cầu hoàn tiền vé:
- Phục vụ trực tiếp màn hình **UI-21 (Admin Refund Management & Review Portal)**.
- Phân quyền: Chỉ tài khoản có `PlatformRole.ADMIN` mới có quyền xem và xử lý.
- **Quy trình Chấp thuận (`Approve`):**
  - Chuyển trạng thái yêu cầu hoàn tiền từ `REQUESTED` sang `APPROVED`.
  - Đưa yêu cầu vào hàng đợi xử lý chuyển tiền hoàn (`Refund Processing Queue`).
  - Vé vẫn giữ trạng thái `REFUND_PENDING` cho đến khi việc chuyển khoản hoàn tất thành công.
- **Quy trình Từ chối (`Reject`):**
  - Chuyển trạng thái yêu cầu sang `REJECTED`, bắt buộc Quản trị viên phải nhập lý do từ chối rõ ràng.
  - **Khôi phục trạng thái vé (Ticket Restoration):** Hệ thống tự động phục hồi các vé liên quan từ `REFUND_PENDING` trở lại `ISSUED` (cho phép khách hàng tiếp tục sử dụng vé để đi xem sự kiện), **với điều kiện sự kiện đó chưa bị hủy**.
- **Cơ chế Thử lại Tường minh (Explicit Admin Retry):**
  - Nếu một lượt chuyển tiền hoàn tiền trước đó bị thất bại (`FAILED`), Quản trị viên có thể bấm "Thử lại hoàn tiền" (`Retry`). Hệ thống sẽ sinh một `attemptId` mới để thực hiện chuyển tiền lại.
  - **Tuyệt đối không retry mù (No Blind Retry) khi trạng thái là `UNKNOWN`:** Nếu phiên chuyển tiền trước đang bị treo hoặc chưa rõ kết quả, bắt buộc phải chờ tiến trình đối soát (`Reconciliation`) xác minh, không được phép retry bừa bãi tránh hoàn tiền 2 lần.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/admin/refund-requests`
- **Mục đích:** Admin xem danh sách tất cả các yêu cầu hoàn tiền trên hệ thống (lọc theo `REQUESTED`, `APPROVED`, `PROCESSING`, `COMPLETED`, `REJECTED`, `FAILED`).
- **Quyền hạn:** `PlatformRole.ADMIN`.
- **Query Params:** `status`, `page`, `pageSize`.
- **Response Body (200 OK):**
```json
{
  "items": [
    {
      "refundId": 3001,
      "orderId": 5001,
      "buyer": {
        "userId": 1024,
        "fullName": "Nguyễn Văn A",
        "email": "buyer.nguyen@example.com"
      },
      "eventName": "Đêm Nhạc Mùa Thu 2026",
      "totalAmount": "1160000.00",
      "ticketCount": 1,
      "reason": "Bận việc đột xuất không thể tham dự chương trình.",
      "status": "REQUESTED",
      "createdAt": "2026-10-09T18:40:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

### 2.2. GET `/api/admin/refund-requests/{refundId}`
- **Mục đích:** Xem chi tiết hồ sơ yêu cầu hoàn tiền, lịch sử các lần thử chuyển tiền (`Refund Attempts`), thông tin tài khoản hoàn trả.

### 2.3. POST `/api/admin/refund-requests/{refundId}/decision`
- **Mục đích:** Admin ra quyết định Chấp thuận hoặc Từ chối yêu cầu hoàn tiền.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body (Chấp thuận):**
```json
{
  "decision": "APPROVE",
  "note": "Hồ sơ hợp lệ theo chính sách hủy vé trước 48h."
}
```
- **Request Body (Từ chối):**
```json
{
  "decision": "REJECT",
  "rejectReason": "Yêu cầu gửi sau thời hạn quy định (dưới 24h trước giờ diễn)."
}
```
- **Response Body (200 OK):**
```json
{
  "success": true,
  "refundId": 3001,
  "status": "APPROVED",
  "message": "Đã phê duyệt yêu cầu hoàn tiền. Hệ thống sẽ tiến hành hoàn tiền cho khách hàng."
}
```

### 2.4. POST `/api/admin/refund-requests/{refundId}/retry`
- **Mục đích:** Admin bấm thử lại chuyển tiền sau khi một lần thử trước bị `FAILED`.
- **Lỗi 409 Conflict:** Nếu yêu cầu không ở trạng thái cho phép thử lại (`status != 'FAILED'`).

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Gọi Stored Procedures Quyết định Hoàn tiền Canonical
- Gọi Stored Procedure `dbo.sp_refund_approve`:
  ```sql
  EXEC dbo.sp_refund_approve
       @refund_id = :refundId,
       @admin_user_id = :adminId,
       @admin_note = :note;
  ```
  - Trong nội tại SP:
    1. Kiểm tra trạng thái: Phải là `REQUESTED`.
    2. Cập nhật `status = 'APPROVED'`, `approved_by = @admin_user_id`, `approved_at = SYSDATETIME()`.
    3. Ghi vết kiểm toán vào `dbo.tc_audit_logs`.
- Gọi Stored Procedure `dbo.sp_refund_reject`:
  ```sql
  EXEC dbo.sp_refund_reject
       @refund_id = :refundId,
       @admin_user_id = :adminId,
       @reject_reason = :reason;
  ```
  - Trong nội tại SP:
    1. Cập nhật `status = 'REJECTED'`, `rejected_reason = @reject_reason`.
    2. Gọi `dbo.sp_ticket_restore_after_rejection`: Kiểm tra nếu sự kiện chưa bị hủy (`events.status != 'CANCELLED'`), chuyển các vé liên quan từ `REFUND_PENDING` trở lại `ISSUED`.

### 3.2. Chống Xung đột Thao tác Admin Đồng thời
- Khi Admin xử lý, sử dụng khóa dòng `UPDLOCK, ROWLOCK` trên bảng `dbo.tc_refunds`:
  - Tránh tình trạng 2 Admin cùng mở một màn hình và một người bấm Duyệt, một người bấm Từ chối gây xung đột dữ liệu.
- **Idempotency Key:** `tc:idem:refund-decision:<adminId>:<key>` (TTL: 86400s).

### 3.3. Invalidation Cache Sau Quyết định
- Khi Approve hoặc Reject thành công:
  - Xóa cache chi tiết hoàn tiền: `DEL tc:refund:detail:v1:<refundId>`
  - Xóa cache vé của người dùng: `DEL tc:user:tickets:v1:*`
  - Xóa cache số lượng yêu cầu hoàn tiền đang chờ: `DEL tc:admin:pending_refunds_count`

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `AdminRefundDecisionRequestDto`, `AdminRefundRetryRequestDto`, `AdminRefundSummaryDto`, `AdminRefundDetailDto`.
   - Repositories: `AdminRefundRepository` (gọi các SP `sp_refund_approve`, `sp_refund_reject`, truy vấn qua `dbo.v_refunds`, `dbo.v_refund_attempts`).
   - Core Services: `AdminRefundDecisionService`.
   - Servlets: `AdminRefundRequestsServlet`, `AdminRefundDecisionServlet`, `AdminRefundRetryServlet`.
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/admin/refunds/list.jsp`: Màn hình UI-21 hiển thị bảng danh sách các yêu cầu hoàn tiền, bộ lọc trạng thái, phân trang, nút Xem xét.
   - `src/main/webapp/WEB-INF/views/admin/refunds/review.jsp`: Xem chi tiết vé cần hoàn, số tiền, lý do của khách hàng, modal phê duyệt (nhập ghi chú), modal từ chối (bắt buộc nhập lý do), nút Thử lại (chỉ hiện khi FAILED).
3. **Database Changes:**
   - Bảng `dbo.tc_refunds` đổi trạng thái thành `'APPROVED'` hoặc `'REJECTED'`.
   - Nếu REJECT: Bảng `dbo.tc_tickets` được phục hồi về `'ISSUED'`.
4. **Kiểm thử tự động:**
   - `AdminRefundDecisionServiceTest`: Non-admin gọi bị 403, Approve chuyển sang APPROVED, Reject thiếu lý do bị 400, Reject phục hồi vé về ISSUED.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs cho Quyết định Hoàn tiền**
  - Khai báo validation rule: nếu `decision == REJECT` thì `rejectReason` bắt buộc không được rỗng.
- **Bước 2: Triển khai `AdminRefundRepository`**
  - Gọi các Stored Procedure canonical qua JDBC CallableStatement trong transaction.
- **Bước 3: Xây dựng `AdminRefundDecisionService`**
  - Kiểm tra quyền `PlatformRole.ADMIN` của Actor.
  - Phối hợp thực thi DB và xóa cache Redis tương ứng.
- **Bước 4: Xây dựng Servlets**
  - Định tuyến các endpoint `/api/admin/refund-requests/*`.
- **Bước 5: Xây dựng Giao diện JSP (UI-21)**
  - Tích hợp giao diện quản trị rõ ràng, nút Phê duyệt (Xanh), nút Từ chối (Đỏ).
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| DEC-01 | Phê duyệt yêu cầu hoàn tiền | Admin đăng nhập, decision=APPROVE | 200 OK, DB chuyển refund sang APPROVED, sẵn sàng cho worker |
| DEC-02 | Từ chối yêu cầu có lý do | decision=REJECT, reason="Quá hạn" | 200 OK, refund thành REJECTED, vé được phục hồi về ISSUED |
| DEC-03 | Từ chối không nhập lý do | decision=REJECT, reason="" | 400 Bad Request ("Lý do từ chối không được để trống") |
| DEC-04 | Non-admin gọi API quyết định | Customer hoặc Manager gọi API | 403 Forbidden |
| DEC-05 | Thử lại khi trạng thái chưa FAILED | Gọi retry khi refund đang PROCESSING | 409 Conflict ("Chỉ được thử lại khi lần thực hiện trước bị thất bại") |
