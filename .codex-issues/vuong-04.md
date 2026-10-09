> **Convention:** `dev` → `feat/vuong/vuong-04-settlement-confirm-payout` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Vương sở hữu toàn bộ quy trình Chốt quyết toán (Confirm) và Thực hiện Chi trả (Payout) trong UI-22 cùng Adapter chi trả mô phỏng (`SimulatedPayoutAdapter`). Tuyệt đối không can thiệp vào Payment của Liêm hay Refund của Khánh.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng quy trình Quản trị viên Chốt Bảng kê Quyết toán (`Confirm Settlement`) và Thực hiện Chi trả Tiền vé (`Idempotent Payout`) cho Ban tổ chức:
- Phục vụ trực tiếp màn hình **UI-22 (Chốt Bảng kê & Lệnh chuyển tiền cho Ban tổ chức)**.
- Phân quyền: Chỉ Quản trị viên hệ thống (`PlatformRole.ADMIN`) mới có quyền thực hiện.
- **Quy trình Chốt Quyết toán (`Confirm`):**
  - Đóng băng vĩnh viễn bảng kê quyết toán: Chuyển trạng thái từ `DRAFT` sang `CONFIRMED`.
  - **Điều kiện chốt quyết toán bắt buộc (Zero-blocker Guarantee):**
    - Sự kiện phải đã kết thúc.
    - Không còn bất kỳ điểm nghẽn nào (`blockerCount == 0`).
    - Bảng kê đã được tính toán lại mới nhất.
    - Sau khi đã `CONFIRMED`, bảng kê trở thành bất biến (Immutable), không thể recalculate hay chỉnh sửa doanh thu nữa.
- **Quy trình Chi trả Tiền vé (`Payout Execution`):**
  - Admin có thể thực hiện chi trả một lần toàn bộ hoặc chi trả làm nhiều đợt (Partial Payouts).
  - **Bảo toàn số dư bất biến:** Tổng số tiền chi trả lũy kế (`totalPaidOut`) cộng với số tiền đang chờ chi trả (`pendingAmount`) **tuyệt đối không được vượt quá số tiền thực trả (`netPayable`)**.
  - Áp dụng cơ chế **Thực hiện chi trả 2 giai đoạn (Two-phase Payout Execution)** để chống thất thoát tiền bạc khi mạng bị ngắt quãng hoặc worker bị crash.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. POST `/api/admin/settlements/{settlementId}/confirm`
- **Mục đích:** Admin chốt đóng băng bảng quyết toán sự kiện.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`.
- **Response Body (200 OK):**
```json
{
  "success": true,
  "settlementId": 901,
  "status": "CONFIRMED",
  "netPayable": "133000000.00",
  "confirmedAt": "2026-10-09T18:55:00Z",
  "message": "Đã chốt bảng kê quyết toán thành công. Sẵn sàng tạo lệnh chi trả."
}
```
- **Lỗi 409 Conflict:** Nếu còn tồn tại điểm nghẽn (Blockers) chưa giải quyết xong.

### 2.2. POST `/api/admin/settlements/{settlementId}/payouts`
- **Mục đích:** Tạo lệnh và thực hiện chuyển tiền chi trả cho Ban tổ chức.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "amount": "133000000.00",
  "bankAccountNumber": "1903456789012",
  "bankName": "Techcombank",
  "accountHolderName": "CONG TY AM NHAC TRE VIET NAM",
  "note": "Chi trả tiền bán vé Đêm Nhạc Mùa Thu 2026 đợt 1"
}
```
- **Validation Rules:**
  - `amount`: `BigDecimal > 0`, không vượt quá `remainingPayable`.
  - Thông tin ngân hàng: Không được để trống.
- **Response Body (200 OK):**
```json
{
  "payoutId": 401,
  "settlementId": 901,
  "amount": "133000000.00",
  "status": "COMPLETED",
  "transferReference": "BANK_TRF_20261009_401",
  "paidAt": "2026-10-09T18:56:00Z",
  "remainingPayable": "0.00",
  "settlementStatus": "COMPLETED",
  "message": "Chi trả thành công."
}
```

### 2.3. GET `/api/admin/settlements/{settlementId}/payouts`
- **Mục đích:** Xem lịch sử các đợt chi trả của bảng quyết toán này.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Gọi Stored Procedures Canonical Chốt và Chi trả
- **Chốt quyết toán:** Gọi `dbo.sp_settlement_confirm`:
  ```sql
  EXEC dbo.sp_settlement_confirm
       @settlement_id = :settlementId,
       @admin_user_id = :adminId;
  ```
  - Trong nội tại SP: Kiểm tra 0 blocker, chuyển `status = 'CONFIRMED'`, `confirmed_at = SYSDATETIME()`.
- **Giai đoạn 1 của Chi trả:** Gọi `dbo.sp_settlement_begin_payout`:
  ```sql
  EXEC dbo.sp_settlement_begin_payout
       @settlement_id = :settlementId,
       @amount = :amount,
       @admin_user_id = :adminId,
       @bank_account = :bankAccount,
       @bank_name = :bankName,
       @account_holder = :accountHolder,
       @new_payout_id = :payoutId OUTPUT;
  ```
  - Trong nội tại SP:
    1. Sử dụng khóa dòng: `SELECT net_payable, total_paid_out, pending_payout_amount FROM dbo.tc_settlements WITH (UPDLOCK, ROWLOCK) WHERE id = @settlement_id`.
    2. Kiểm tra: `total_paid_out + pending_payout_amount + @amount <= net_payable`. Nếu vượt quá, ném lỗi `PAYOUT_EXCEEDS_REMAINING_PAYABLE`.
    3. Tăng `pending_payout_amount = pending_payout_amount + @amount`.
    4. Tạo bản ghi mới trong `dbo.tc_settlement_payouts` với `status = 'PENDING'`.
    5. Commit transaction cơ sở dữ liệu.
- **Giai đoạn 2 của Chi trả:** Gọi Ngân hàng / Adapter ngoài transaction:
  - Gọi `PayoutAdapter.transfer(...)`.
- **Giai đoạn 3 của Chi trả:** Gọi `dbo.sp_settlement_record_payout_result`:
  ```sql
  EXEC dbo.sp_settlement_record_payout_result
       @payout_id = :payoutId,
       @status = :status, -- 'SUCCEEDED' | 'FAILED' | 'UNKNOWN'
       @transfer_reference = :transferRef,
       @error_message = :errorMsg;
  ```
  - Nếu `SUCCEEDED`: Trừ `pending_payout_amount`, cộng `total_paid_out = total_paid_out + @amount`, chuyển payout sang `COMPLETED`. Nếu `total_paid_out == net_payable`, chuyển settlement sang `COMPLETED`.
  - Nếu `FAILED`: Trừ `pending_payout_amount`, chuyển payout sang `FAILED`.

### 3.2. Chống Tranh chấp Chi trả (Distributed Lock & Idempotency)
- Redis Distributed Lock:
  - Key: `tc:lock:settlement-payout:<settlementId>` (TTL: 30 giây).
  - Ngăn ngừa tình trạng 2 admin cùng bấm nút Chi trả cùng lúc gây vượt hạn mức chi trả.
- **Idempotency Key:** `tc:idem:payout:<adminId>:<key>` (TTL: 86400s).

### 3.3. Invalidation Cache Toàn Diện
- Sau khi Confirm hoặc Payout thành công:
  - Invalidate: `DEL tc:settlement:summary:v1:<eventId>`
  - Invalidate: `DEL tc:admin:overview:v1`
  - Invalidate các báo cáo tài chính liên quan.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `SettlementConfirmResponseDto`, `PayoutRequestDto`, `PayoutResponseDto`, `PayoutHistoryItemDto`.
   - Adapters: `PayoutAdapter` (interface), `SimulatedPayoutAdapter` (triển khai chuyển tiền mô phỏng cho test).
   - Repositories: `SettlementPayoutRepository` (gọi các SP `sp_settlement_confirm`, `sp_settlement_begin_payout`, `sp_settlement_record_payout_result`).
   - Core Services: `SettlementConfirmationService`, `PayoutExecutionService`.
   - Servlets: `AdminSettlementConfirmServlet`, `AdminSettlementPayoutServlet`.
2. **Giao diện JSP:**
   - Màn hình UI-22: Nút "Chốt bảng kê quyết toán" (chỉ kích hoạt khi 0 blocker), modal "Tạo lệnh chi trả" (nhập số tiền, thông tin tài khoản ngân hàng), bảng lịch sử các đợt chi trả kèm mã giao dịch ngân hàng.
3. **Database Changes:**
   - Bảng `dbo.tc_settlements` cập nhật `status = 'CONFIRMED'` hoặc `'COMPLETED'`, cập nhật `total_paid_out`.
   - Bảng `dbo.tc_settlement_payouts` ghi nhận từng đợt chi trả.
4. **Kiểm thử tự động:**
   - `SettlementConfirmTest`: Có blocker bị chặn chốt, không blocker chốt thành công.
   - `PayoutExecutionTest`: Chi trả vượt số dư bị chặn, chi trả thành công cập nhật số dư đúng, chi trả lặp cùng idempotency key trả kết quả cũ.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs cho Confirm và Payout**
  - Khai báo các thuộc tính ngân hàng và `BigDecimal amount`.
- **Bước 2: Xây dựng `SimulatedPayoutAdapter`**
  - Mô phỏng cuộc gọi API ngân hàng (sinh mã `TRF_...`, trả về thành công hoặc timeout).
- **Bước 3: Triển khai `SettlementPayoutRepository`**
  - Viết JDBC CallableStatement gọi 3 Stored Procedures canonical.
- **Bước 4: Xây dựng Services**
  - `SettlementConfirmationService`: Kiểm tra blockers trước khi confirm.
  - `PayoutExecutionService`: Triển khai mô hình 2 giai đoạn an toàn.
- **Bước 5: Xây dựng Servlets**
  - Định tuyến các endpoint POST confirm và POST payouts.
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| PAY-01 | Chốt quyết toán không có blocker | Blocker count = 0 | 200 OK, Settlement thành CONFIRMED |
| PAY-02 | Chốt quyết toán khi còn blocker | Có 1 blocker chưa xử lý | 409 Conflict ("Còn tồn tại giao dịch chưa giải quyết") |
| PAY-03 | Chi trả hợp lệ đợt 1 | amount = 50tr / 133tr net | 200 OK, Payout COMPLETED, remaining = 83tr |
| PAY-04 | Chi trả vượt số dư còn lại | amount = 150tr / 133tr net | 400 Bad Request ("Số tiền chi trả vượt quá số tiền còn lại") |
| PAY-05 | Chốt quyết toán lần 2 | Đã CONFIRMED rồi bấm lại | 200 OK (Idempotent) |
