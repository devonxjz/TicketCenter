> **Convention:** Mỗi sub-issue checkout từ `dev` sang `feat/khanh/<task-id>-<ten-ngan>`, commit theo Conventional Commits và mở PR về `dev`.
> **Merge gate:** Chỉ merge khi toàn bộ GitHub Actions xanh, test có bằng chứng và PR đã được người khác review.

## Phạm vi sở hữu của Khánh

Khánh sở hữu feature Check-in sự kiện, giải mã & xác thực QR vé, yêu cầu hoàn vé của khách hàng, admin duyệt hoàn vé, và quy trình hủy sự kiện:

- UI-08: khách hàng xem vé đủ điều kiện và gửi yêu cầu hoàn vé (Customer refund request).
- UI-15: người soát vé/tổ chức chọn sự kiện check-in và kiểm tra khung giờ hợp lệ.
- UI-16: quét/nhập mã QR check-in vé, xác thực chữ ký và xem lịch sử check-in.
- UI-20 (phần hủy sự kiện): API hủy sự kiện, hủy vé hàng loạt và theo dõi tiến trình (Event cancellation progress & exceptions).
- UI-21: admin duyệt hoặc từ chối yêu cầu hoàn vé, retry thủ công các giao dịch hoàn thất bại.
- Refund worker, attempt tracking, reconciliation gateway và worker hủy sự kiện hàng loạt.
- Package `checkin/**`, `refund/**`, `cancellation/**` và JSP/views liên quan Check-in, Refund, Cancellation.

## File không được sửa

- Mọi `*/model/*.java` vì model hiện có được coi là đã khóa.
- `pom.xml`, `src/main/resources/META-INF/persistence.xml`.
- `src/main/java/vn/ticketscenter/config/**` nếu sub-issue không ghi rõ ngoại lệ.
- `database/**`, đặc biệt migration 026 và bộ SQL untracked.
- `src/main/resources/openapi.json`, CI, Dockerfile và README do Vương tích hợp.
- Package feature của Thái (`auth/**`, `identity/**`), Đông (`event/**`, `storage/**`), Liêm (`ticketing/**`, `order/**`, `payment/**`) và Vương (`settlement/**`, `report/**`, `audit/**`).
- Không sửa bộ sinh chữ ký QR / bán vé (thuộc Liêm), CRUD Event (thuộc Đông), đối soát settlement (thuộc Vương).

Nếu phát hiện cần đổi file khóa, dừng thay đổi đó và mở issue phối hợp riêng; không lồng sửa vào feature PR.

## Ranh giới admin

- Thái: admin organization requests, UI-19.
- Đông: admin Event publish/reject, phần tương ứng UI-20.
- Khánh: admin refund, check-in operations và Event cancellation, UI-21 cùng API cancel của UI-20.
- Liêm: admin coupon trong UI-14.
- Vương: admin dashboard, commission, settlement, payout, report và audit, UI-17/18/22/23.

Không tạo một `AdminServlet`, `AdminService` hoặc JSP admin chung để nhiều người cùng sửa. Mỗi miền có controller/service/JSP admin riêng.

## Sub-issues

- [ ] #21 KHANH-01: Check-in Event selection và time window cache.
- [ ] #22 KHANH-02: Signed QR decode và atomic check-in.
- [ ] #23 KHANH-03: Customer Refund request trên canonical data.
- [ ] #24 KHANH-04: Admin Refund decision và explicit retry.
- [ ] #25 KHANH-05: Refund worker, attempt và reconciliation.
- [ ] #26 KHANH-06: Event Cancellation batch và restart recovery.

## Thứ tự ghép

1. KHANH-01 (nền tảng chọn event check-in và cache khung giờ).
2. KHANH-02 (giải mã QR HMAC và atomic check-in) ghép sau KHANH-01 (khi Liêm đã chốt contract ký QR).
3. KHANH-03 (Customer Refund request trên dữ liệu canonical).
4. KHANH-04 (Admin Refund decision/retry) ghép sau KHANH-03.
5. KHANH-05 (Refund worker/attempt reconciliation) ghép sau KHANH-04.
6. KHANH-06 (Event Cancellation batch) ghép sau cùng khi nền tảng refund/worker đã hoàn chỉnh.

## Definition of Done của epic

- Đủ endpoint, Service, Repository, DTO, JSP và test của sáu sub-issue.
- Xác thực chữ ký HMAC QR chặt chẽ trước khi parse; chỉ tin cậy `id` sau khi verify và đọc lại từ DB; atomic check-in chống quét trùng (double check-in).
- Canonical Refund sử dụng đúng `tc_refund_obligations` và View/SP canonical; không ghi trực tiếp legacy table; hoàn vé 0đ hoàn thành tự động mà không gọi cổng thanh toán giả.
- Quyền check-in và hoàn vé được kiểm tra chặt chẽ trên DB (chỉ manager/check-in staff của tổ chức mới được soát vé; chỉ chủ sở hữu đơn mới được yêu cầu hoàn).
- Worker bồi hoàn và batch cancel có idempotency, an toàn khi restart giữa chừng, không treo trạng thái PENDING/UNKNOWN.
- Không lưu raw QR trong log lịch sử check-in; không lộ bí mật HMAC hoặc thông tin tài khoản ngân hàng/hoàn tiền.
- Tất cả PR đã merge vào `dev`, GitHub Actions trên `dev` xanh.
- Chạy nghiệm thu UI-08, 15, 16, 21 và API cancel của UI-20 trên commit `dev` cuối cùng.
