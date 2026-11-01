# Đối chiếu báo cáo `Nhom17_PhanPhoiVaQuanLyVeSuKien (1).docx` với SQL Server

- **Ngày đối chiếu:** 2026-10-25
- **DB đối chiếu:** Azure SQL `ticketcenter-backup` (`devonxjz.database.windows.net`, theo `application.properties`), SQL 12.0.2000.8.
- **Cách làm:** chỉ đọc catalog (`sys.*`, `OBJECT_DEFINITION`), không ghi gì vào DB.
- **Lưu ý:** doc ghi tên DB là `TicketsCenter`; memory dự án ghi DB dùng chung là `ticketscenter.database.windows.net`. Số liệu mục 5.7.4 khớp bản backup này, nhưng nếu nộp theo DB dùng chung thì cần chạy lại trên đó.

## 1. Kết luận

Số lượng đối tượng khớp, nhưng nội dung chi tiết **không khớp hoàn toàn**:

- Chương 2: lệch kiểu dữ liệu và một số ràng buộc NULL.
- Chương 3 và phụ lục A: nhiều tên đối tượng không tồn tại; mã thủ tục, hàm, view khác bản thật.
- Chương 4: ma trận phân quyền, mức cô cô lập và kịch bản sao lưu không đúng với thực tế.

Khuyến nghị: **sửa doc cho khớp DB thật**, không sửa DB cho khớp doc.

## 2. Phần khớp

| Hạng mục | Doc | DB |
|---|---|---|
| Bảng / View / Thủ tục / Hàm / Trigger | 43 / 67 / 93 / 21 / 25 | 43 / 67 / 93 / 21 / 25 |
| 14 bảng core | có đủ, tên cột đúng | đúng |
| Khóa ngoại | `ON DELETE NO ACTION` | 68/68 là `NO_ACTION` |
| `CK_Order_Amounts`, `CK_Event_TimeRange`, `CK_OrderItem_Seat_Quantity` | có | khớp |
| 4 UNIQUE ở mục 3.2.2 | có | khớp |
| `CK_Coupon_Discount_Limit` | có | khớp về ý nghĩa |
| `trg_AuditLog_AppendOnly` (51110), `trg_Event_AuditStatusChange` | có | khớp logic |
| `vw_CheckInHistory` | có | khớp |
| 6 role `tc_*` | có | đủ, đúng tên |
| Đối tượng ở mục 5.7.4 | có | đều tồn tại |
| Recovery model | FULL | FULL |

## 3. Chương 2 – Data dictionary (mục 2.4)

### 3.1. Kiểu dữ liệu lệch

| Cột | Doc | DB |
|---|---|---|
| `version` (khoảng 10 bảng) | `int` | `bigint` |
| `tc_users.email`, `normalized_email` | `nvarchar(255)` | `nvarchar(320)` |
| `tc_users.full_name` | `nvarchar(255)` | `nvarchar(200)` |
| `tc_users.phone` | `nvarchar(50)` | `nvarchar(30)` |
| `tc_users.status` | `nvarchar(50)` | `varchar(20)` |
| `tc_users.avatar`, `avatar_public_id` | `nvarchar` | `varchar` |
| `tc_users.platform_role` | `nvarchar(50)` | `nvarchar(20)` |
| `tc_users.updated_at` | `datetime2(3)` | `datetime2(6)` |
| `tc_organizations.name` | `nvarchar(255)` | `nvarchar(200)` |
| `tc_organizations.contact_email` | `nvarchar(255)` | `nvarchar(320)` |
| `tc_organizations.contact_phone` | `nvarchar(50)` | `nvarchar(30)` |
| `tc_organizations.description`, `rejection_reason` | `nvarchar(2048)` | `nvarchar(2000)` |
| `tc_organizations.status` | `nvarchar(50)` | `varchar(30)` |
| `tc_event_categories.name` / `slug` | `nvarchar(100)` | `nvarchar(120)` / `varchar(120)` |
| `tc_events.title`, `venue_name` | `nvarchar(255)` | `nvarchar(250)` |
| `tc_events.description` | `nvarchar(2048)` | `nvarchar(max)` |
| `tc_events.status` | `nvarchar(50)` | `varchar(30)` |
| `tc_events.rejection_reason` | `nvarchar(2048)` | `nvarchar(1000)` |
| `tc_events.archive_reason` | `nvarchar(2048)` | `nvarchar(500)` |
| `tc_events.event_image`, `event_image_public_id` | `nvarchar` | `varchar` |
| `tc_zones.name` | `nvarchar(100)` | `nvarchar(120)` |
| `tc_zones.type`, `tc_seats.status`, `tc_ticket_holds.status` | `nvarchar(50)` | `varchar(20)` |
| `tc_coupons.code` | `nvarchar(50)` | `varchar(80)` |
| `tc_coupons.discount_type` | `nvarchar(50)` | `varchar(30)` |
| `tc_coupons.percentage_value` | `int` | `decimal(5,2)` |
| `tc_orders.order_code` | `nvarchar(100)` | `varchar(64)` |
| `tc_orders.status` | `nvarchar(50)` | `varchar(30)` |
| `tc_order_items.zone_name_snapshot` | `nvarchar(100)` | `nvarchar(120)` |
| `tc_payments.txn_ref` | `nvarchar(255)` | `varchar(100)` |
| `tc_payments.currency` | `nvarchar(10)` | `char(3)` |
| `tc_payments.status` | `nvarchar(50)` | `varchar(20)` |
| `tc_payments.provider_reference` | `nvarchar(255)` | `nvarchar(200)` |
| `tc_tickets.ticket_code` | `nvarchar(100)` | `varchar(100)` |
| `tc_tickets.qr_secret_hash` | `nvarchar(255)` | `varbinary(64)` |
| `tc_tickets.status` | `nvarchar(50)` | `varchar(30)` |
| `tc_ticket_check_in_logs.result` | `nvarchar(50)` | `varchar(30)` |
| `tc_ticket_check_in_logs.failure_reason` | `nvarchar(255)` | `nvarchar(2000)` |

### 3.2. Nullability ghi ngược

| Cột | Doc | DB |
|---|---|---|
| `tc_users.normalized_email` | NOT NULL | NULL |
| `tc_users.user_name` | NULL | NOT NULL |
| `tc_users.updated_at` | NOT NULL | NULL |
| `tc_organizations.contact_phone` | NOT NULL | NULL |
| `tc_organizations.requester_id` | NOT NULL | NULL |
| `tc_organizations.submitted_at` | NOT NULL | NULL |
| `tc_events.description` | NULL | NOT NULL |
| `tc_order_items.event_id` | NOT NULL | NULL |

### 3.3. Cột thiếu trong doc

`created_at` / `updated_at` của `tc_zones`, `tc_seats`, `tc_tickets`; `updated_at` của `tc_payments`.

## 4. Chương 3 – Tên đối tượng doc dùng nhưng DB không có

| Doc | Thực tế |
|---|---|
| `usp_CheckInTicket` (3.6.6, A.16, ma trận 4.3, A.22, phụ lục B) | Không tồn tại. Thủ tục thật là `sp_ticket_check_in` |
| `usp_ProcessPaymentCallbackSafe` (4.1.3, A.20) | Không tồn tại. Dùng `usp_ApplyPaymentResult`, `usp_BeginOrderPayment`, `usp_RetryPaymentCompensation` |
| `CK_CheckIns_Result` (3.2.1.5) | Không có. `tc_ticket_check_in_logs` không có CHECK nào cho `result` |
| `DF_TicketHolds_ExpiresAt` (3.2.3) | Không có default cho `expires_at`; thủ tục tự tính 10 phút |
| `IX_CheckIn_Event_ScannedAt` | Tên thật `IX_TicketCheckInLog_Event_ScannedAt` |
| `FK_CheckInLogs_*` | `FK_TicketCheckInLogs_*` |
| `DF_Holds_*`, `DF_Orgs_*`, `DF_Zones_*`, … (khoảng 30 tên ở phụ lục DDL) | Tên thật khác, ví dụ `DF_TicketHolds_*` |
| `TicketHoldItemInputTableType` (A.11) | DB không có table type nào |

Tổng cộng có 46 tên trong doc không tìm thấy trong DB (gồm `sp_set_session_context`, là thủ tục hệ thống nên không tính là lỗi).

### Trạng thái vé `tc_tickets.status`

- Doc: `ISSUED`, `USED`, `CANCELLED` (bảng dữ liệu, `fn_GetRefundableTickets`, DDL, A.15, A.16, mục 3.5.1).
- DB: `ACTIVE`, `USED`, `REFUND_PENDING`, `REFUNDED`, `INVALIDATED`.
- `UX_Tickets_OneLiveSeat` thật lọc `seat_id IS NOT NULL AND status IN ('ACTIVE','USED','REFUND_PENDING')`, không phải `status = 'ISSUED'`.

### Trạng thái khác

- Hold: DB là `ACTIVE`, `RELEASED`, `CONSUMED` (doc ghi `EXPIRED` / `CANCELLED` ở nghiệp vụ 2 và A.12).

## 5. Chương 3 – Hàm, view, trigger, index

| Đối tượng | Doc | DB |
|---|---|---|
| `fn_CalculateCommission` | `@RatePercent INT`, không làm tròn | `@rate_percent decimal(12,4)`, làm tròn, chặn trần bằng số tiền gốc, trả `NULL` nếu đầu vào sai |
| `fn_event_is_check_in_open` | `(@EventStartTime, @EventEndTime, @CurrentTime)`, mở trước 120 phút, đóng sau `end + 30` phút | `(@event_id, @now)`, mở trước 60 phút, đóng đúng `end_time`, yêu cầu event `PUBLISHED` |
| `fn_GetEventCheckInWindow` | `(@EventId)`, -120 / +30 phút | `(@event_id, @now_utc)`, -60 phút / `end_time`, thêm `is_cancelled`, `is_open` |
| `fn_GetZoneAvailability` | `(@EventId)` | `(@zone_id)`, lấy từ `vw_ZoneInventory` |
| `fn_GetRefundableTickets` | `(@OrderId)`, quy tắc hủy trước 24 giờ, `status = 'ISSUED'` | `(@order_id, @now_utc)`, chỉ cần trước `start_time`, `status = 'ACTIVE'`, loại vé đang có yêu cầu hoàn mở |
| `fn_CalculateCouponDiscount` | 5 tham số, `INT` | 4 tham số, `decimal(5,2)`, không có `max_discount_amount`, trả `NULL` nếu không hợp lệ |
| `vw_PublicEvents` | không có điều kiện archive | thêm `AND e.archived_at IS NULL` |
| `vw_SettlementPayoutBalance` | `remaining_payable_balance` | `available_to_pay`, `confirmed_at` |
| `trg_Coupon_ProtectUsageLimit` | thông báo tiếng Việt `N'...'` | thông báo tiếng Anh (`Coupon max uses is below current usage`), mã 51107 giống |
| `trg_AuditLog_AppendOnly` | thông báo tiếng Việt | `Audit log is append-only`, mã 51110 giống |
| `trg_Seat_ProtectPublishedLayout` | khóa khi `PUBLISHED` | khóa khi `PUBLISHED` hoặc `CANCELLED`, cả INSERT, lỗi 51102 |
| `trg_Zone_ProtectPublishedLayout` | khóa capacity và type | khóa thêm `name`, `event_id`, lỗi 51101 |
| `trg_Membership_ProtectLastManager` | chặn xóa / hạ quyền manager cuối | khớp ý tưởng; lỗi 51106; kiểm tra `u.status = 'ACTIVE'` |
| `IX_Event_Status_StartTime`, `IX_Order_User_CreatedAt` | covering index (`INCLUDE`) | có tồn tại, khóa `(status, start_time, id)` và `(user_id, created_at, id)`, **không có `INCLUDE`** |

Chưa đối chiếu từng cột của `vw_OrderFinancialSummary` và `vw_EventSalesReport` (định nghĩa dài).

## 6. Phụ lục A – Thủ tục

| Phụ lục | Doc | DB |
|---|---|---|
| A.11 `usp_CreateTicketHold` | `(@HoldId, @UserId, @EventId, @ExpiresAt, @Items TVP)`; chỉ khóa `tc_ticket_holds`, `tc_seats`; lỗi 51000 / 51001 | `(@user_id, @event_id, @selections nvarchar(max) JSON, @result_hold OUTPUT, @suppress_result)`; `sp_getapplock` theo user, khóa `tc_events` theo thứ tự id, rồi zone, seat; lỗi từ 51220 / 51225; hạn 10 phút tự tính |
| A.12 `usp_ReleaseTicketHold` | `(@HoldId, @Reason)`, `status = @Reason` | `(@hold_id, @actor_id, @reason, @suppress_result)`; hold luôn thành `RELEASED`; lý do `USER_CANCEL` / `EXPIRED` / `EVENT_CANCELLED`; xử lý cả vé đứng, coupon, đơn, audit; lỗi 51270–51275 |
| A.13 `usp_CreateOrderFromHold` | `(@OrderId, @HoldId, @OrderCode)`; lỗi 51002 | `(@hold_id, @actor_id)`; tự sinh mã `ORD-…`; kiểm tra event `PUBLISHED`; chạy lại không tạo trùng; lỗi 51260–51262 |
| A.14 `usp_ApplyOrderCoupon` | `(@OrderId, @CouponCode)`; gọi `fn_CalculateCouponDiscount`; lỗi 51101 | `(@order_id, @actor_id, @coupon_code)`; dùng `fn_GetCouponEligibility`, `fn_CalculateCouponDiscountCapped`; ghi `tc_coupon_redemptions`; lỗi 51230–51235 |
| A.15 `usp_ApplyPaymentResult` | `(@PaymentId, @OrderId, @TxnRef, @Status, @ProviderRef)`; tạo vé `'ISSUED'` | `(@order_id, @actor_id, @payment_id, @verified_result JSON, @ticket_codes, @retry_failed_compensation)`; kết quả `CAPTURED` / `FAILED` / `UNKNOWN` |
| A.16 `usp_CheckInTicket` | thủ tục này | không tồn tại; `sp_ticket_check_in(@event_id, @staff_user_id, @ticket_uuid, @ticket_code, @customer_name, @request_key, @request_fingerprint, @invalid_signature)`; kiểm tra thành viên `MANAGER` / `CHECK_IN_STAFF`; lỗi 53201–53204 |
| A.17 `usp_RecalculateSettlement` | `(@EventId)`, ghi thẳng `tc_settlements` | `(@event_id, @actor_id, @auth_version, @emit_result)`; bắt buộc admin; chỉ khi settlement `DRAFT`; tạo `tc_settlement_order_snapshots`; lỗi 527xx |
| A.18 `fn_CalculateCouponDiscount` | xem mục 5 | xem mục 5 |
| A.19 kịch bản 2 session | dùng thẳng `tc_seats` | minh họa độc lập, không gọi thủ tục thật; ổn nếu ghi rõ là minh họa |
| A.20 `usp_ProcessPaymentCallbackSafe` | thủ tục này | không tồn tại |

## 7. Chương 4

### 7.1. Giao dịch (4.1)

- DB bật cả `READ_COMMITTED_SNAPSHOT = ON` lẫn `SNAPSHOT ISOLATION = ON`. "READ COMMITTED (mặc định)" thực tế chạy theo row-versioning, không phải khóa đọc như bảng 4.1 mô tả.
- Các thủ tục không đặt `SET TRANSACTION ISOLATION LEVEL`; chúng dùng gợi ý `UPDLOCK, HOLDLOCK`. Doc nói `SERIALIZABLE` cho thanh toán và `REPEATABLE READ` cho giỏ hàng, nhưng DB không dùng như vậy.
- Thứ tự khóa doc nêu: `tc_users → tc_events → tc_zones → tc_seats → tc_ticket_holds → tc_orders → tc_payments → tc_tickets`. `usp_CreateTicketHold` khóa `tc_events` trước, nên chưa chắc đúng với "mọi thủ tục".
- Retry lỗi 1205 có trong Java (`SqlCall.java:377`, `EventTransactions.java:89`). Chưa kiểm tra "tối đa 3 lần, exponential backoff".

### 7.2. Sao lưu (4.1.5, A.21)

- Recovery model `FULL` đúng.
- DB chạy trên Azure SQL, nơi `BACKUP LOG` và `RESTORE ... STOPAT` bằng T-SQL kiểu SQL Server tự quản lý **không dùng được** (Azure tự sao lưu, khôi phục theo thời điểm qua portal).
- Repo có `database/backup_recovery/*.sql` và `docs/baocao/BACKUP-RECOVERY-PLAN.md`. Doc cần ghi rõ đây là kịch bản cho SQL Server tự quản lý, hoặc viết lại theo Azure.

### 7.3. Phân quyền (4.2, A.22)

- `tc_buyer` không có `EXECUTE` trên `usp_CreateTicketHold`, `usp_ApplyPaymentResult`, `usp_ReleaseTicketHold`, `usp_CreateOrderFromHold`. Quyền thật là các thủ tục `*Request` (`usp_CreateHoldRequest`, `usp_CreateOrderRequest`, `usp_BeginOrderPayment`, `usp_CancelHoldRequest`, `usp_CreateRefundRequest`, …).
- `usp_ApplyPaymentResult` chỉ cấp cho `tc_worker` (cùng `usp_ReleaseTicketHold`, `usp_ExpireHolds`, …).
- `tc_buyer` không có `SELECT` trực tiếp trên `tc_events`, `tc_seats`, `tc_orders`, `tc_tickets`; đọc qua view.
- `tc_manager` bị `DENY INSERT/UPDATE/DELETE` trên `tc_events`, `tc_seats` (doc ghi `SELECT, INSERT, UPDATE`).
- `tc_checkin` được `EXECUTE` `sp_ticket_check_in` (doc ghi `usp_CheckInTicket`); `tc_manager` cũng có quyền này.
- `DENY` cho `tc_checkin` trên `tc_payments`, `tc_orders`, `vw_EventSalesReport` khớp; `tc_buyer` cũng bị `DENY` trên `vw_EventSalesReport`.
- User `usr_api_buyer`, `usr_scanner_device`, `usr_org_portal` (A.22) **không có**. User thật: `railway_admin`, `railway_auth`, `railway_buyer`, `railway_checkin`, `railway_manager`, `railway_worker` và một user worker `tc_khanh05_worker_…`.
- Chưa kiểm tra `DENY` trên `vw_OrderFinancialSummary` và `fn_CalculateCommission` cho `tc_checkin`.

## 8. Phụ lục B

Còn ghi `usp_CheckInTicket` và `IX_CheckIn_Event_ScannedAt`; cần đổi thành `sp_ticket_check_in` và `IX_TicketCheckInLog_Event_ScannedAt`.

## 9. Chưa kiểm tra

- Từng cột của `vw_OrderFinancialSummary`, `vw_EventSalesReport`, `vw_CouponUsage`.
- Các `DENY` còn lại trong A.22 (xem mục 7.3).
- Cơ chế retry deadlock chi tiết.
- Chương 5 ngoài mục 5.7.4 (ứng dụng Java, giao diện).
- Argon2id trong 4.2.4: đây là phần ứng dụng, chưa đối chiếu với mã nguồn.
