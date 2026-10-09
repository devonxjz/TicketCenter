> **Convention:** `dev` → `feat/vuong/vuong-03-settlement-recalculate` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa code tính toán đối soát, phát hiện điểm nghẽn (Blocker Detection), DTO và cache trong `settlement/recalculation/**`, JSP xem bảng kê và test tương ứng. Tuyệt đối không can thiệp vào logic Payment của Liêm hay Refund của Khánh.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng module Tính toán Bảng kê Quyết toán Tài chính (Settlement Recalculation) và Tự động phát hiện các Điểm nghẽn Giao dịch (Blocker Detection):
- Phục vụ trực tiếp màn hình **UI-22 (Admin Financial Settlement & Reconciliation)**.
- Phân quyền: Chỉ Quản trị viên hệ thống (`PlatformRole.ADMIN`) mới có quyền thực hiện.
- **Quy trình Tính toán Quyết toán Sự kiện (Settlement Calculation):**
  - Mỗi Sự kiện chỉ có duy nhất 1 bản ghi Quyết toán (`Settlement`) với trạng thái ban đầu là `DRAFT`.
  - Hệ thống chụp lại trạng thái tài chính (Financial Snapshot) của từng đơn hàng đã thanh toán (`PAID`).
  - **Công thức Tài chính Bất biến trong Cơ sở dữ liệu:**
    - `grossRevenue = SUM(paidOrders.totalAmount)` (Tổng tiền vé thu vào thực tế).
    - `totalRefunded = SUM(completedRefunds.amount)` (Tổng tiền vé đã hoàn trả cho khách).
    - `netRevenue = grossRevenue - totalRefunded` (Doanh thu ròng sau khi trừ hoàn tiền).
    - `platformCommission = CommissionRule(netRevenue)` (Phí hoa hồng mà nền tảng TicketsCenter thu theo hợp đồng).
    - `netPayable = netRevenue - platformCommission` (Số tiền thực tế nền tảng phải chi trả cho Ban tổ chức sự kiện).
- **Cơ chế Phát hiện Điểm nghẽn (Blocker Detection Engine):**
  - Tuyệt đối không cho phép chốt quyết toán nếu sự kiện còn tồn tại bất kỳ điểm nghẽn nào sau đây:
    1. Đơn hàng còn đang treo thanh toán (`PENDING_PAYMENT`) chưa hết hạn.
    2. Giao dịch thanh toán VNPAY đang ở trạng thái `PENDING` hoặc `UNKNOWN` chưa đối soát xong.
    3. Có yêu cầu hoàn tiền vé đang chờ Admin duyệt (`REQUESTED`) hoặc đang trong tiến trình chuyển tiền (`PROCESSING`).
    4. Tiến trình hủy sự kiện hàng loạt chưa xử lý xong 100%.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/admin/events/{eventId}/settlement`
- **Mục đích:** Admin xem bảng tính quyết toán hiện tại của sự kiện (bao gồm doanh thu, phí, số tiền phải trả cho BTC).
- **Quyền hạn:** `PlatformRole.ADMIN`.
- **Response Body (200 OK):**
```json
{
  "settlementId": 901,
  "eventId": 201,
  "organizationName": "Công ty Âm nhạc Trẻ Việt Nam",
  "status": "DRAFT",
  "grossRevenue": "150000000.00",
  "totalRefunded": "10000000.00",
  "netRevenue": "140000000.00",
  "platformCommission": "7000000.00",
  "netPayable": "133000000.00",
  "totalPaidOut": "0.00",
  "remainingPayable": "133000000.00",
  "lastCalculatedAt": "2026-10-09T18:50:00Z",
  "hasBlockers": true,
  "blockerCount": 2
}
```

### 2.2. GET `/api/admin/events/{eventId}/settlement-blockers`
- **Mục đích:** Liệt kê chi tiết tất cả các lý do / giao dịch đang ngăn cản việc chốt quyết toán.
- **Response Body (200 OK):**
```json
{
  "eventId": 201,
  "canConfirm": false,
  "blockers": [
    {
      "type": "PENDING_REFUND",
      "referenceId": "REF-3001",
      "description": "Yêu cầu hoàn tiền vé #3001 của khách đang ở trạng thái REQUESTED cần duyệt hoặc từ chối trước."
    },
    {
      "type": "UNKNOWN_PAYMENT",
      "referenceId": "TXN_20261009_5001_8F3A",
      "description": "Giao dịch VNPAY đang UNKNOWN cần chạy đối soát hoàn tất."
    }
  ]
}
```

### 2.3. POST `/api/admin/events/{eventId}/settlement/recalculate`
- **Mục đích:** Admin bấm tính toán lại số liệu quyết toán dựa trên dữ liệu mới nhất trong DB.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`.
- **Response Body (200 OK):** Trả về bảng số liệu quyết toán mới nhất sau khi tính toán lại.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Gọi Stored Procedures Tính toán Quyết toán Canonical
- Gọi Stored Procedure `dbo.sp_settlement_recalculate`:
  ```sql
  EXEC dbo.sp_settlement_recalculate
       @event_id = :eventId,
       @admin_user_id = :adminId,
       @settlement_id = :settlementId OUTPUT;
  ```
  - Trong nội tại SP:
    1. Kiểm tra trạng thái sự kiện: Bắt buộc đã kết thúc hoặc đã hủy (`status IN ('COMPLETED', 'PUBLISHED', 'CANCELLED')`).
    2. Kiểm tra trạng thái Settlement: Nếu đã là `CONFIRMED` thì **không cho phép tính toán lại** (bất biến dữ liệu đã chốt).
    3. Tạo hoặc lấy bản ghi `dbo.tc_settlements` ở trạng thái `DRAFT`.
    4. Quét các đơn hàng `PAID`: Insert hoặc Update bản ghi snapshot trong `dbo.tc_settlement_order_snapshots`.
    5. Tính toán các biến số tài chính theo công thức canonical.
    6. Cập nhật các cột tổng trong `dbo.tc_settlements`.
    7. Ghi vết kiểm toán vào `dbo.tc_audit_logs`.

### 3.2. Thuật toán Phát hiện Điểm nghẽn (Blocker Detection Queries)
- Thực thi truy vấn kiểm tra 4 điều kiện:
  ```sql
  -- 1. Kiểm tra Refund chưa hoàn tất
  SELECT COUNT(*) FROM dbo.tc_refunds r
  JOIN dbo.tc_orders o ON o.id = r.order_id
  WHERE o.event_id = :eventId AND r.status IN ('REQUESTED', 'APPROVED', 'PROCESSING');

  -- 2. Kiểm tra Payment đang treo
  SELECT COUNT(*) FROM dbo.tc_payments p
  JOIN dbo.tc_orders o ON o.id = p.order_id
  WHERE o.event_id = :eventId AND p.status IN ('PENDING', 'UNKNOWN');

  -- 3. Kiểm tra Order chưa hoàn tất giữ chỗ
  SELECT COUNT(*) FROM dbo.tc_orders 
  WHERE event_id = :eventId AND status = 'PENDING_PAYMENT';
  ```
- Nếu tổng số lượng > 0: Gắn cờ `hasBlockers = true` và khóa nút Chốt quyết toán.

### 3.3. Cache Redis với TTL Ngắn
- Redis Key: `tc:settlement:summary:v1:<eventId>` (TTL: 60 giây).
- Redis Key: `tc:settlement:blockers:v1:<eventId>` (TTL: 30 giây).
- Sau khi bấm Recalculate thành công, Invalidate ngay lập tức hai key trên.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `SettlementSummaryDto`, `SettlementBlockerDto`, `SettlementBlockerListDto`.
   - Repositories: `SettlementRepository` (gọi các SP `sp_settlement_create`, `sp_settlement_recalculate`, truy vấn qua `dbo.v_settlements`, `dbo.v_event_financials`).
   - Core Services: `SettlementRecalculationService`, `SettlementBlockerService`.
   - Servlets: `AdminSettlementServlet`, `AdminSettlementBlockersServlet`, `AdminSettlementRecalculateServlet`.
2. **Giao diện JSP:**
   - Màn hình UI-22: Bảng chi tiết doanh thu, hoa hồng, danh sách các blocker hiển thị bằng icon cảnh báo màu vàng, nút "Tính toán lại số liệu" kèm hiệu ứng loading.
3. **Database Changes:**
   - Cập nhật số liệu `gross_revenue`, `total_refunded`, `net_revenue`, `platform_commission`, `net_payable` trong bảng `dbo.tc_settlements`.
   - Tạo/cập nhật các bản ghi trong `dbo.tc_settlement_order_snapshots`.
4. **Kiểm thử tự động:**
   - `SettlementBlockerServiceTest`: Kiểm tra phát hiện đúng khi có refund đang REQUESTED, phát hiện đúng khi có payment UNKNOWN.
   - `SettlementCalculationTest`: Đối chiếu kết quả tính toán giữa Java BigDecimal và Stored Procedure đảm bảo khớp chính xác đến từng đồng.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs cho Bảng kê Quyết toán**
  - Khai báo các thuộc tính tài chính kiểu `BigDecimal`.
- **Bước 2: Triển khai `SettlementRepository`**
  - Thực thi Stored Procedure `dbo.sp_settlement_recalculate` qua JDBC CallableStatement.
  - Viết câu truy vấn kiểm tra blockers từ View và bảng canonical.
- **Bước 3: Xây dựng `SettlementRecalculationService`**
  - Kiểm tra quyền `PlatformRole.ADMIN`.
  - Thực thi tính toán lại và xóa cache Redis tương ứng.
- **Bước 4: Xây dựng Servlets**
  - Định tuyến các endpoint GET settlement, GET blockers và POST recalculate.
- **Bước 5: Xây dựng Giao diện JSP (UI-22)**
  - Hiển thị bảng kê tài chính chi tiết và danh sách cảnh báo blocker.
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| SET-01 | Tính toán quyết toán sự kiện hợp lệ | Sự kiện kết thúc, 100 vé bán được | 200 OK, grossRevenue đúng 100 vé, netPayable trừ hoa hồng chính xác |
| SET-02 | Phát hiện Blocker khi có hoàn vé treo | Có 1 đơn đang có Refund REQUESTED | canConfirm = false, blockers chứa chi tiết đơn treo |
| SET-03 | Tính toán lại khi Settlement đã CONFIRMED | Settlement status = CONFIRMED | 409 Conflict ("Bảng quyết toán đã chốt không thể tính toán lại") |
| SET-04 | Non-admin gọi API quyết toán | Manager hoặc Customer gọi API | 403 Forbidden |
| SET-05 | Bồi hoàn Late Payment | Có bồi hoàn do thanh toán muộn | Số tiền bồi hoàn không được tính vào doanh thu bán vé |
