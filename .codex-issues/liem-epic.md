> **Convention:** Mỗi sub-issue checkout từ `dev` sang `feat/liem/<task-id>-<ten-ngan>`, commit theo Conventional Commits và mở PR về `dev`.
> **Merge gate:** Chỉ merge khi toàn bộ GitHub Actions xanh, test có bằng chứng và PR đã được người khác review.

## Phạm vi sở hữu của Liêm

Liêm sở hữu feature Ticket Hold, Order checkout, Coupon discount, tích hợp thanh toán VNPAY, phát hành Ticket và signed JSON QR:

- UI-04: chọn vé và giữ chỗ tạm thời (Ticket Hold, TTL 10 phút, inventory lock).
- UI-05: áp dụng mã giảm giá (Coupon <= 30%) và tạo Order (Order checkout).
- UI-06: khởi tạo thanh toán VNPAY, nhận callback IPN và xử lý thanh toán idempotent.
- UI-07: vé của tôi, hiển thị danh sách vé đã mua và sinh mã QR bảo mật.
- UI-14: admin quản lý mã giảm giá Coupon (tạo/sửa, quota, điều kiện áp dụng).
- Quản lý Ticket, sinh chữ ký HMAC cho QR payload `{id, name}` và lịch sử mua vé trong UI-07/phần lịch sử đơn.
- Worker giải phóng hold hết hạn, thanh toán timeout/reconciliation và outbox.
- Package `ticketing/**`, `order/**`, `payment/**`, `coupon/**`, `fulfillment/**` và các JSP liên quan.

## File không được sửa

- Mọi `*/model/*.java` vì model hiện có được coi là đã khóa.
- `pom.xml`, `src/main/resources/META-INF/persistence.xml`.
- `src/main/java/vn/ticketscenter/config/**` nếu sub-issue không ghi rõ ngoại lệ (trừ VNPAY config).
- `database/**`, đặc biệt migration 026 và bộ SQL untracked.
- `src/main/resources/openapi.json`, CI, Dockerfile và README do Vương tích hợp.
- Package feature của Thái (`auth/**`, `identity/**`), Đông (`event/**`, `storage/**`), Khánh (`checkin/**`, `refund/**`, `cancellation/**`) và Vương (`settlement/**`, `report/**`, `audit/**`).
- Không sửa check-in QR / hoàn vé (thuộc Khánh), quản lý sự kiện (thuộc Đông), đối soát settlement (thuộc Vương).

Nếu phát hiện cần đổi file khóa, dừng thay đổi đó và mở issue phối hợp riêng; không lồng sửa vào feature PR.

## Ranh giới admin

- Thái: admin organization requests, UI-19.
- Đông: admin Event publish/reject, phần tương ứng UI-20.
- Khánh: admin refund, check-in operations và Event cancellation, UI-21 cùng API cancel của UI-20.
- Liêm: admin coupon trong UI-14.
- Vương: admin dashboard, commission, settlement, payout, report và audit, UI-17/18/22/23.

Không tạo một `AdminServlet`, `AdminService` hoặc JSP admin chung để nhiều người cùng sửa. Mỗi miền có controller/service/JSP admin riêng.

## Sub-issues

- [ ] #14 LIEM-01: Ticket Hold, inventory lock và Redis idempotency.
- [ ] #15 LIEM-02: Order, Coupon và quota concurrency.
- [ ] #16 LIEM-03: VNPAY payment initiation và idempotency.
- [ ] #17 LIEM-04: VNPAY IPN, replay và reconciliation.
- [ ] #18 LIEM-05: Ticket issuance và signed JSON QR.
- [ ] #19 LIEM-06: Order history và Coupon administration.

## Thứ tự ghép

1. LIEM-01 (khung giữ vé và khóa inventory).
2. LIEM-02 ghép sau LIEM-01 (kết nối Hold sang Order và tính toán Coupon).
3. LIEM-03 và LIEM-04 triển khai sau LIEM-02 (khởi tạo thanh toán và xử lý IPN/callback).
4. LIEM-05 ghép sau khi thanh toán thành công hoàn tất để phát hành Ticket và ký HMAC QR.
5. LIEM-06 ghép sau LIEM-02 và LIEM-05 (lịch sử đơn hàng cá nhân và admin coupon).

## Definition of Done của epic

- Đủ endpoint, Service, Repository, DTO, JSP và test của sáu sub-issue.
- SQL Server là source of truth; Redis cache-aside cho GET và dedupe/idempotency cho mutation.
- Luồng E2E Hold → Order → Coupon → VNPAY Payment → Ticket/QR chạy hoàn chỉnh từ Swagger và UI.
- Chống race condition giữ chỗ, duplicate Hold, vượt quota Coupon bằng DB transaction/lock.
- Chống replay VNPAY callback/IPN bằng unique key DB kết hợp Redis; xử lý an toàn ca thanh toán muộn/bù trừ.
- Mã QR dùng định dạng JSON `{id, name}` có HMAC envelope; không lộ VNPAY secret, private key ký hoặc raw SQL trong log/response.
- Tất cả PR đã merge vào `dev`, GitHub Actions trên `dev` xanh.
- Chạy nghiệm thu UI-04, 05, 06, 07 và 14 trên commit `dev` cuối cùng.
