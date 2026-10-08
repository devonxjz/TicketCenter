# Thiết kế dữ liệu TicketsCenter

> Đồng bộ 06/10/2026 theo [spec.md](../../spec.md) và [diagram.md](../classdiagram/diagram.md). Thiết kế chưa phải database đã triển khai.

## Mười lăm class nghiệp vụ

### User

Thuộc tính diagram: email: String; userName: String; fullName: String; phone: String [0..1]; status: UserStatus; emailVerified: Boolean; platformRole: PlatformRole; organizationRoles: Map<Organization, OrganizationRole>.

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| email | String | NOT NULL; chuẩn hóa và duy nhất |
| userName | String | NOT NULL; chuẩn hóa và duy nhất |
| fullName | String | NOT NULL |
| phone | String | NULL được phép |
| status | UserStatus | ACTIVE hoặc DISABLED |
| passwordHash | String | Hash mật khẩu; không lưu mật khẩu rõ |
| authVersion | Integer | Tăng khi reset để vô hiệu hóa phiên cũ |
| emailVerifiedAt | Instant | NULL khi chưa xác minh; suy ra emailVerified |
| platformRole | PlatformRole | CUSTOMER hoặc ADMIN |
| createdAt | Instant | UTC; metadata persistence |

### Organization

Thuộc tính diagram: name: String; contactEmail: String; contactPhone: String [0..1]; description: String [0..1]; requester: User [0..1]; status: OrganizationStatus; rejectionReason: String [0..1].

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| name | String | NOT NULL |
| contactEmail | String | NOT NULL |
| contactPhone | String | NULL được phép |
| description | String | NULL được phép |
| requesterId | UUID | FK User; cần khi gửi duyệt |
| status | OrganizationStatus | DRAFT / PENDING_APPROVAL / APPROVED / REJECTED |
| rejectionReason | String | Có khi từ chối |
| createdAt và mốc gửi duyệt | Instant | Metadata/audit; cùng một hồ sơ Organization |

### Event

Thuộc tính diagram: title: String; description: String; category: EventCategory; venueName: String; venueAddress: String; coverImageUrl: String [0..1]; saleStart: Instant; saleEnd: Instant; startTime: Instant; endTime: Instant; status: EventStatus; rejectionReason: String [0..1]; commissionRule: CommissionRule [0..1].

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| organizationId | UUID | FK Organization |
| category | EventCategory | Kiểu giá trị hoặc FK danh mục kỹ thuật |
| title, description | String | NOT NULL |
| venueName, venueAddress | String | NOT NULL |
| coverImageUrl | String | Có thể trống khi nháp; bắt buộc khi publish |
| saleStart, saleEnd, startTime, endTime | Instant | saleStart < saleEnd <= startTime < endTime |
| status | EventStatus | DRAFT / PENDING_APPROVAL / REJECTED / PUBLISHED / CANCELLED |
| rejectionReason | String | Có khi từ chối |
| commissionRuleId | UUID | FK CommissionRule; NULL trước publish; cùng tổ chức |

### Zone

Thuộc tính diagram: name: String; type: ZoneType; price: Money; standingCapacity: Integer [0..1]; standingHeld: Integer [0..1]; standingSold: Integer [0..1].

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| eventId | UUID | FK Event |
| name | String | NOT NULL |
| type | ZoneType | SEATED hoặc STANDING |
| price | Money | VND nguyên; không âm |
| standingCapacity | Integer | STANDING > 0; SEATED để NULL |
| standingHeld, standingSold | Integer | STANDING không âm; tổng không vượt capacity |

### Seat

Thuộc tính diagram: rowName: String; seatNumber: Integer; status: SeatStatus.

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| zoneId | UUID | FK Zone loại SEATED |
| rowName | String | NOT NULL |
| seatNumber | Integer | NOT NULL; nhãn duy nhất trong Zone |
| status | SeatStatus | AVAILABLE / HELD / SOLD |

### TicketHold

Thuộc tính diagram: buyer: User; event: Event; expiresAt: Instant; status: HoldStatus.

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| userId | UUID | FK User; buyer đã xác minh |
| eventId | UUID | FK Event |
| createdAt, expiresAt | Instant | TTL 10 phút do backend/SP02 đặt |
| status | HoldStatus | ACTIVE / RELEASED / CONSUMED; tối đa một ACTIVE/User |

### TicketHoldItem

Thuộc tính diagram: zone: Zone; seat: Seat [0..1]; quantity: Integer; unitPrice: Money.

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| ticketHoldId | UUID | FK TicketHold |
| zoneId | UUID | FK Zone cùng Event |
| seatId | UUID | FK Seat; NULL cho STANDING |
| quantity | Integer | SEATED = 1; STANDING > 0 |
| unitPrice | Money | Giá giữ bất biến, không âm |

### Order

Thuộc tính diagram: buyer: User; event: Event; hold: TicketHold; coupon: Coupon [0..1]; acceptedPayment: Payment [0..1]; orderCode: String; status: OrderStatus; discountAmount: Money; /subtotalAmount: Money; /totalAmount: Money.

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| userId, eventId | UUID | FK User/Event cùng Hold |
| ticketHoldId | UUID | FK TicketHold; UNIQUE |
| couponId | UUID | FK Coupon; tối đa một mã; nullable |
| acceptedPaymentId | UUID | Nullable; khoản CAPTURED của chính Order được chấp nhận |
| orderCode | String | Duy nhất |
| status | OrderStatus | PENDING_PAYMENT / PAID / CANCELLED / EXPIRED |
| subtotalAmount, discountAmount, totalAmount | Money | subtotal/total dẫn xuất; lưu cache nếu kiểm soát C08 |
| createdAt, paidAt | Instant | paidAt bắt buộc khi PAID, kể cả đơn 0đ |

### OrderItem

Thuộc tính diagram: zone: Zone; seat: Seat [0..1]; zoneNameSnapshot: String; seatLabelSnapshot: String [0..1]; quantity: Integer; unitPrice: Money.

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| orderId | UUID | FK Order |
| zoneId | UUID | FK Zone cùng Event |
| seatId | UUID | FK Seat nullable; đúng Zone |
| zoneNameSnapshot | String | Tên khu lúc tạo đơn |
| seatLabelSnapshot | String | Nhãn ghế lúc tạo đơn; nullable cho STANDING |
| quantity | Integer | SEATED = 1; STANDING > 0 |
| unitPrice | Money | Sao chép giá bất biến từ HoldItem |

### Payment

Thuộc tính diagram: order: Order; amount: Money; status: PaymentStatus; txnRef: String; transactionNo: String [0..1].

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| orderId | UUID | FK Order NOT NULL; Order có 0..* lần Payment |
| amount | Money | > 0; chụp totalAmount khi Payment.start |
| status | PaymentStatus | PENDING / CAPTURED / FAILED / UNKNOWN |
| txnRef | String | NOT NULL và UNIQUE |
| transactionNo | String | Nullable trước xác minh khoản thu |
| createdAt, paidAt | Instant | paidAt bắt buộc khi CAPTURED; đơn 0đ không tạo Payment |

### Coupon

Thuộc tính diagram: code: String; discountType: DiscountType; fixedAmount: Money [0..1]; percentage: Decimal [0..1]; validFrom: Instant; validTo: Instant; isActive: Boolean; maxUses: Integer.

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| organizationId | UUID | FK Organization |
| code | String | Duy nhất trong phạm vi tổ chức theo quy ước chuẩn hóa |
| discountType | DiscountType | PERCENTAGE hoặc FIXED_AMOUNT |
| fixedAmount | Money | Dương cho FIXED_AMOUNT; NULL cho PERCENTAGE |
| percentage | Decimal | Trong (0,30] cho PERCENTAGE; NULL cho FIXED_AMOUNT |
| validFrom, validTo | Instant | validFrom < validTo |
| isActive | Boolean | NOT NULL |
| maxUses | Integer | > 0; không dưới RESERVED + CONSUMED |

### Ticket

Thuộc tính diagram: ticketCode: String; /zone: Zone; /seat: Seat [0..1]; status: TicketStatus; paidAmount: Money.

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| orderItemId | UUID | FK OrderItem; mỗi quyền vào cửa một Ticket |
| ticketCode | String | Duy nhất, ngẫu nhiên; không log mã đầy đủ |
| status | TicketStatus | ACTIVE / USED / REFUND_PENDING / REFUNDED / INVALIDATED |
| paidAmount | Money | >= 0; tổng các vé = totalAmount |
| issuedAt | Instant | Thời điểm phát hành bất biến |
| /zone, /seat | Dẫn xuất | Qua OrderItem; không tạo nguồn dữ liệu độc lập |

### Refund

Thuộc tính diagram: tickets: Ticket [0..*]; payment: Payment [0..1]; amount: Money; purpose: RefundPurpose; reasonType: RefundReason [0..1]; reason: String [0..1]; rejectionReason: String [0..1]; status: RefundStatus; currentAttemptId: Identifier [0..1]; providerReference: String [0..1]; processedAt: Instant [0..1].

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| orderId | UUID | FK Order; nghĩa vụ thuộc một Order |
| paymentId | UUID | FK Payment; nullable chỉ khi amount = 0 |
| amount | Money | >= 0; không vượt nguồn thu; tồn tại cả hoàn 0đ |
| purpose | RefundPurpose | CUSTOMER_REFUND hoặc PAYMENT_COMPENSATION |
| reasonType | RefundReason | CUSTOMER_REQUEST / EVENT_CANCELLATION; nullable cho bù trừ |
| reason, rejectionReason | String | Lý do yêu cầu/quyết định; lưu audit khi đổi nguồn |
| status | RefundStatus | REQUESTED / APPROVED / PROCESSING / NEEDS_RECONCILIATION / RETRYABLE / REJECTED / COMPLETED |
| currentAttemptId | Identifier | Nullable trước lần thực hiện; tham chiếu log kỹ thuật |
| providerReference | String | Nullable; kết quả đã xác minh |
| processedAt | Instant | Có khi hoàn có tiền thành công; không tạo mốc chuyển tiền giả cho 0đ |
| createdAt | Instant | Metadata persistence; tập Ticket qua bảng nối RefundTicket |

### CommissionRule

Thuộc tính diagram: ratePercent: Decimal; fixedFee: Money; effectiveFrom: Instant; effectiveTo: Instant.

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| organizationId | UUID | FK Organization |
| ratePercent | Decimal | Không âm; không tự thêm giới hạn kinh doanh |
| fixedFee | Money | Không âm |
| effectiveFrom, effectiveTo | Instant | effectiveFrom < effectiveTo; điều khoản đã gắn Event bất biến |

### Settlement

Thuộc tính diagram: status: SettlementStatus; grossRevenue: Money; totalRefund: Money; totalCommission: Money; /netPayable: Money; paidAmount: Money; pendingAmount: Money; /availableToPay: Money.

| Thuộc tính | Kiểu | Ràng buộc và ý nghĩa |
|---|---|---|
| id | UUID | PK |
| eventId | UUID | FK Event; UNIQUE; không tạo bản rỗng |
| status | SettlementStatus | DRAFT / CONFIRMED / PAID |
| grossRevenue, totalRefund, totalCommission | Money | Snapshot tổng; chỉ recalculate khi DRAFT |
| paidAmount, pendingAmount | Money | Không âm; chỉ đổi qua beginPayout/recordPayoutResult |
| /netPayable | Money dẫn xuất | grossRevenue - totalRefund - totalCommission |
| /availableToPay | Money dẫn xuất | netPayable - paidAmount - pendingAmount |
| createdAt, confirmedAt | Instant | Metadata; confirmedAt và snapshot khóa sau xác nhận |

## Dữ liệu persistence hỗ trợ

### UserOrganizationRole

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| userId, organizationId | UUID | FK User/Organization; cặp UNIQUE |
| role, active | OrganizationRole / Boolean | MANAGER hoặc CHECK_IN_STAFF; chỉ active vào map |
| metadata và lịch sử | Persistence | Bảo vệ Manager active cuối cùng; không vô hiệu toàn User |

### EventCategory

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| id, name hoặc mã enum | Danh mục | Tham chiếu/kiểu giá trị cho Event; không phải class nghiệp vụ |

### RefundTicket

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| refundId, ticketId | UUID | FK Refund/Ticket; PK ghép |
| isOpen | Boolean | Dấu hiệu nghĩa vụ mở; unique một nghĩa vụ mở/Ticket |

### TicketCheckInLog

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| id, eventId, actorId | UUID | PK; Event/User có quyền trong tổ chức |
| ticketId | UUID | Nullable cho mã không tồn tại |
| result, scannedAt | CheckInResult / Instant | Giữ cả quét thành công và từ chối; không lưu raw QR |

### RefundTransferLog

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| attemptId, refundId | Identifier / UUID | attemptId UNIQUE; FK Refund |
| status, createdAt | Trạng thái kỹ thuật / Instant | PENDING / UNKNOWN / FAILED / SUCCEEDED |
| providerReference, processedAt | String / Instant | Có mốc SUCCEEDED đã xác minh; amount lấy từ nghĩa vụ cha |

### SettlementOrderSnapshot

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| settlementId, orderId | UUID | FK; mỗi Order chỉ một dòng snapshot |
| grossAmount, refundAmount, commissionAmount, netAmount | Money | Snapshot từng đơn; net = gross - refund - commission |
| metadata | Persistence | Chỉ sửa khi Settlement DRAFT; không nhân tiền khi join |

### SettlementTransferLog

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| id, payoutId, settlementId | UUID / Identifier | payoutId UNIQUE; FK Settlement |
| amount | Money | > 0; giữ nguyên số tiền cho cùng payoutId |
| status, reference, paidAt | Persistence | PENDING / SUCCEEDED / FAILED; cùng transaction số dư |

### OtpToken

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| userId, purpose, tokenHmac | Persistence | VERIFY_EMAIL / RESET_PASSWORD; không lưu mã rõ; đề xuất PK(userId, purpose), một bản ghi hiện hành/mục đích; mã mới thay mã cũ |
| expiresAt, failedAttempts, consumedAt | Instant / Integer | 5 phút; sai 5 lần vô hiệu; dùng một lần; gửi lại >= 60 giây |

### CouponRedemption

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| orderId, couponId | UUID | FK; UNIQUE Order |
| status | Trạng thái kỹ thuật | RESERVED / CONSUMED / RELEASED; không trả CONSUMED khi hoàn |

### OutboxMessage

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| id, aggregateId, kind, payload | Persistence | Cùng transaction nghiệp vụ; payload không chứa bí mật |
| idempotencyKey, lease, attempts, nextAttemptAt | Persistence | Nhận việc theo lô; retry hữu hạn; tiếp tục sau restart |

### AuditLog

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| id, actorId, action, aggregateType, aggregateId | Persistence | Actor backend; hệ thống có thể NULL |
| detail, createdAt | Persistence | Append-only; UTC; không chứa password/OTP/QR/token |

## Quy tắc nghiệp vụ và nghiệm thu

Organization giữ cùng hồ sơ khi duyệt. Refund 0đ vẫn tồn tại và COMPLETED, không có Payment/transfer giả. UNKNOWN phải đối chiếu; FAILED đã xác minh mới được thử attemptId mới. Settlement đóng băng số liệu khi confirm và giữ số dư paid/pending qua hành vi chi trả. Payment.order bắt buộc, acceptedPayment thuộc chính Order. Chi tiết nguồn chuẩn tại spec mục 5, 6, 8, 12 và 14.

Danh mục SQL gồm C01–C20, V01–V10, SP01–SP17, F01–F10, TR01–TR10, IX01–IX15, TX01–TX17 và R01–R04. Chỉ đạt khi có caller thực, kiểm thử và evidence.
