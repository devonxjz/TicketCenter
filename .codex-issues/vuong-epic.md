> **Convention:** Mỗi sub-issue checkout từ `dev` sang `feat/vuong/<task-id>-<ten-ngan>`, commit theo Conventional Commits và mở PR về `dev`.
> **Merge gate:** Chỉ merge khi toàn bộ GitHub Actions xanh, test có bằng chứng và PR đã được người khác review.

## Phạm vi sở hữu của Vương

Vương sở hữu feature Quản lý tỷ lệ hoa hồng (Commission Rules), Quyết toán tổ chức (Settlement), Báo cáo doanh thu, Admin Dashboard, Audit Log viewer và đóng vai trò tích hợp nền tảng (Platform Integration):

- UI-17: admin quản lý chính sách và quy tắc hoa hồng (Commission Rules).
- UI-18: quyết toán doanh thu tổ chức, phát hiện chặn thanh toán (blockers), và thực hiện lệnh payout.
- UI-22: báo cáo tài chính/doanh thu cho tổ chức và admin hệ thống, hỗ trợ xuất CSV an toàn.
- UI-23: admin dashboard tổng quan số liệu toàn sàn và trình xem nhật ký hệ thống (Audit Log viewer).
- Sở hữu độc quyền cấu hình OpenAPI (`openapi.json`), Swagger UI registry, CI/CD pipeline, Dockerfile và tài liệu tổng hợp bàn giao.
- Package `settlement/**`, `commission/**`, `report/**`, `audit/**`, `dashboard/**`, `swagger/**` và các JSP liên quan.

## File không được sửa

- Mọi `*/model/*.java` vì model hiện có được coi là đã khóa.
- `pom.xml`, `src/main/resources/META-INF/persistence.xml` (trừ khi có RFC/thống nhất chung toàn team).
- `database/**`, đặc biệt migration 026 và bộ SQL untracked (trừ khi có issue phối hợp schema).
- Package feature của Thái (`auth/**`, `identity/**`), Đông (`event/**`, `storage/**`), Liêm (`ticketing/**`, `order/**`, `payment/**`) và Khánh (`checkin/**`, `refund/**`, `cancellation/**`).
- Không tự ý sửa logic nghiệp vụ của 4 thành viên khác; chỉ tích hợp qua contract, DTO và tài liệu OpenAPI.

Nếu phát hiện cần đổi file khóa, dừng thay đổi đó và mở issue phối hợp riêng; không lồng sửa vào feature PR.

## Ranh giới admin

- Thái: admin organization requests, UI-19.
- Đông: admin Event publish/reject, phần tương ứng UI-20.
- Khánh: admin refund, check-in operations và Event cancellation, UI-21 cùng API cancel của UI-20.
- Liêm: admin coupon trong UI-14.
- Vương: admin dashboard, commission, settlement, payout, report và audit, UI-17/18/22/23.

Không tạo một `AdminServlet`, `AdminService` hoặc JSP admin chung để nhiều người cùng sửa. Mỗi miền có controller/service/JSP admin riêng.

## Sub-issues

- [ ] #28 VUONG-01: Swagger UI, OpenAPI và Redis key contract.
- [ ] #29 VUONG-02: Commission Rule administration.
- [ ] #30 VUONG-03: Settlement recalculate và blocker detection.
- [ ] #31 VUONG-04: Settlement confirm và idempotent payout.
- [ ] #32 VUONG-05: Organization/Admin reports và safe CSV.
- [ ] #33 VUONG-06: Admin dashboard, Audit và final integration.

## Thứ tự ghép

1. VUONG-01 (nền tảng Swagger UI, OpenAPI spec và registry Redis key dùng chung).
2. VUONG-02 (quản lý Commission Rules làm tiền đề cho tính toán phí khi duyệt Event và quyết toán).
3. VUONG-03 và VUONG-04 ghép sau khi logic Order (Liêm) và Refund (Khánh) đã ổn định để đối soát chính xác.
4. VUONG-05 (hệ thống báo cáo và export CSV an toàn).
5. VUONG-06 ghép sau cùng để tổng hợp số liệu lên Admin Dashboard, Audit Log và chạy kiểm thử tích hợp E2E toàn hệ thống.

## Definition of Done của epic

- Đủ endpoint, Service, Repository, DTO, JSP và test của sáu sub-issue.
- `openapi.json` và Swagger UI hiển thị đầy đủ, không conflict route và khớp chính xác contract của cả 5 thành viên.
- Quyết toán và Payout đảm bảo an toàn tuyệt đối: kiểm tra điều kiện chặn (sự kiện chưa xong, refund còn pending), đóng băng snapshot doanh thu, chống payout trùng lặp, không giữ lock DB khi gọi provider bên ngoài.
- Xuất CSV được khử độc (sanitized) triệt để chống Formula Injection (`=`, `+`, `-`, `@`); phân quyền báo cáo chặt chẽ, không xem chéo doanh thu giữa các tổ chức.
- Dashboard và Audit Log truy vấn đúng quyền admin, không rò rỉ dữ liệu nhạy cảm hoặc mật khẩu trong log audit.
- Tất cả PR đã merge vào `dev`, GitHub Actions trên `dev` xanh.
- Chạy nghiệm thu UI-17, 18, 22, 23 và E2E contract trên commit `dev` cuối cùng.
