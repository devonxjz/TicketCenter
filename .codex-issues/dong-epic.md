> **Convention:** Mỗi sub-issue checkout từ `dev` sang `feat/dong/<task-id>-<ten-ngan>`, commit theo Conventional Commits và mở PR về `dev`.
> **Merge gate:** Chỉ merge khi toàn bộ GitHub Actions xanh, test có bằng chứng và PR đã được người khác review.

## Phạm vi sở hữu của Đông

Đông sở hữu feature Event Catalog, Zone & Seat Inventory, Event Draft lifecycle, Cover image storage và admin duyệt Event:

- UI-01: trang chủ và tìm kiếm sự kiện công khai (catalog, keyword, category, filter, pagination).
- UI-02: phần đọc chi tiết sự kiện, sơ đồ Zone và trạng thái Seat.
- UI-12: quản lý sự kiện của tổ chức (tạo draft, sửa thông tin sự kiện, submit duyệt).
- UI-13: quản lý Zone, Seat, giá vé và hạn mức inventory của sự kiện.
- UI-20 (phần duyệt sự kiện): admin publish hoặc reject sự kiện kèm lý do/commission rule.
- Package `event/**`, `storage/**` (Cloudinary cover upload), JSP/views liên quan đến Event và Admin Event review.
- Redis cache-aside cho danh mục/chi tiết Event và invalidation sau DB commit.

## File không được sửa

- Mọi `*/model/*.java` vì model hiện có được coi là đã khóa.
- `pom.xml`, `src/main/resources/META-INF/persistence.xml`.
- `src/main/java/vn/ticketscenter/config/**` nếu sub-issue không ghi rõ ngoại lệ (trừ Cloudinary/Storage config).
- `database/**`, đặc biệt migration 026 và bộ SQL untracked.
- `src/main/resources/openapi.json`, CI, Dockerfile và README do Vương tích hợp.
- Package feature của Thái (`auth/**`, `identity/**`), Liêm (`ticketing/**`, `order/**`, `payment/**`), Khánh (`checkin/**`, `refund/**`, `cancellation/**`) và Vương (`settlement/**`, `report/**`, `audit/**`).
- Không sửa luồng Ticket Hold / Order (thuộc Liêm) hoặc Event Cancellation / Refund (thuộc Khánh).

Nếu phát hiện cần đổi file khóa, dừng thay đổi đó và mở issue phối hợp riêng; không lồng sửa vào feature PR.

## Ranh giới admin

- Thái: admin organization requests, UI-19.
- Đông: admin Event publish/reject, phần tương ứng UI-20.
- Khánh: admin refund, check-in operations và Event cancellation, UI-21 cùng API cancel của UI-20.
- Liêm: admin coupon trong UI-14.
- Vương: admin dashboard, commission, settlement, payout, report và audit, UI-17/18/22/23.

Không tạo một `AdminServlet`, `AdminService` hoặc JSP admin chung để nhiều người cùng sửa. Mỗi miền có controller/service/JSP admin riêng.

## Sub-issues

- [ ] #8 DONG-01: Event catalog, detail và Redis cache.
- [ ] #9 DONG-02: Event draft lifecycle và idempotency.
- [ ] #10 DONG-03: Zone, Seat và inventory constraints.
- [ ] #11 DONG-04: Event cover upload qua Cloudinary.
- [ ] #12 DONG-05: Admin publish và reject Event.

## Thứ tự ghép

1. DONG-01 (nền tảng đọc catalog, detail và cache-aside).
2. DONG-02 và DONG-03 có thể chạy song song sau khi DONG-01 ổn định cấu trúc DTO/Service.
3. DONG-04 ghép sau DONG-02 để hoàn thiện media cho draft.
4. DONG-05 ghép sau khi DONG-02 và DONG-03 đã sẵn sàng luồng kiểm tra dữ liệu sự kiện trước khi duyệt.

## Definition of Done của epic

- Đủ endpoint, Service, Repository, DTO, JSP và test của năm sub-issue.
- Public chỉ thấy sự kiện `PUBLISHED`; quyền Organization không đọc/sửa chéo sự kiện của tổ chức khác.
- SQL Server là source of truth; Redis cache-aside cho GET, chỉ invalidate cache sau DB commit; Redis sự cố thì fallback DB và không trả dữ liệu giả; không cache entity/bytes/credential.
- Mutation nhận `Idempotency-Key`; concurrency, rollback và inventory constraints có test SQL Server thật.
- Không lộ secret Cloudinary hoặc stack trace trong log/API response.
- Tất cả PR đã merge vào `dev`, GitHub Actions trên `dev` xanh.
- Chạy nghiệm thu UI-01, 02 (phần đọc), 12, 13 và 20 (publish/reject) trên commit `dev` cuối cùng.
