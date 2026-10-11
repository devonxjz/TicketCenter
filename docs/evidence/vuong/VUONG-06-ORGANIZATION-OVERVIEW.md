# VUONG-06 — Tổng quan tổ chức, 11/10/2026

Phần tiếp theo của [#33](https://github.com/devonxjz/TicketCenter/issues/33) và hợp đồng [#28](https://github.com/devonxjz/TicketCenter/issues/28), từ dev `5cf796ea1d8127eb0a4bcf321a0770d191c21ca0`. Service/repository đã có; đợt này nối route HTTP còn thiếu. Không thay implementation owner, model, pom, persistence, schema hoặc worker.

## Hành vi đã giao

- `GET /api/organizations/{organizationId}/overview` được dispatch tại `ReportServlet` đang sở hữu `/api/organizations/*`; không thêm wildcard servlet tranh route membership/report. `/reports` giữ bộ lọc hiện có, `/members` và suffix thừa không được nhận làm overview.
- ADMIN hiện tại hoặc MANAGER ACTIVE của đúng tổ chức; kiểm SQL status/authVersion và membership mỗi request trước tổng hợp. CHECK_IN_STAFF, người không có membership hoặc manager tổ chức khác nhận403. Phiên thiếu/stale/inactive nhận401. Tổ chức thiếu nhận404 sau kiểm quyền; không dùng404 để lộ tổ chức cho caller không có quyền.
- Scope chỉ từ UUID trên path. Không nhận query/filter; `organizationId`, ngày hoặc tham số lặp trên query trả400. Lỗi cố định `INVALID_FILTER` (query), `INVALID_REPORT_FILTER` (UUID sai), `UNAUTHENTICATED` (controller/SQL), `SESSION_EXPIRED` (bộ lọc phiên chung), `FORBIDDEN`, `NOT_FOUND`, `OVERVIEW_UNAVAILABLE`; không trả SQL hoặc credentials.
- `data` gồm asOf/UTC/from/to/organizationId, eventCount/pendingEvents/paidOrders toàn thời gian, dòng tiền và doanh thu/commission cohort ngày UTC hiện tại, bảy ngày UTC gần nhất. Cohort và cash flow giữ time basis riêng. Tiền nguyên VND dạng chuỗi, counter int64. Không có totalUsers/activeOrganizations/pendingOrganizations/pendingRefunds/draftSettlements của toàn sàn.
- Không đọc cache tổng quan ADMIN hoặc tạo producer Redis mới. HTTP `Cache-Control: no-store`. Schema `OrganizationOverview` riêng trong OpenAPI, giữ security/operationId/x-owner hiện có và chuyển route sang implemented/matched-to-source.

## Bằng chứng kiểm thử

- RED: route parser từ chối overview và OpenAPI còn planned; kiểm thử JDBC chứng minh tổ chức không tồn tại trả zero thay vì lỗi; HTTP mới thất bại404 thay vì200/403. 18 ca HTTP trước đó vẫn đạt.
- GREEN: clean package391 discovered,331 executed,60 named guarded external skips, zero failures/errors. Safe Node56/56.
- SQL Server canonical JDBC24/24, zero skips: scope tổ chức, KPI không lẫn ADMIN, membership revoke, authVersion hiện tại và missing organization; hồi quy finance/report/audit, ràng buộc tiền và CSV dữ liệu lớn.
- Schema-only team clone JDBC11/11, zero skips. Không ghi DB dùng chung/Azure.
- Isolated actual finance/report/dashboard/audit/organization-overview HTTP/JDBC/JSP20/20, zero skips: route thật, các vai trò, phạm vi, query không hỗ trợ, UUID sai/suffix thừa, tiền/UTC/counter và các chức năng cũ. Test-only session bridge; không phải đăng nhập owner hoặc full WAR. Mã SESSION_EXPIRED được đối chiếu từ SessionValidationFilter của production; fixture không khởi tạo owner AuthService nên không nghiệm thu HTTP nhánh đó.
- ROOT/WAR Swagger/check-in boundary12/12, zero skips, không khởi tạo DB/SMTP/worker. Review và exact-head/postmerge CI sẽ được ghi trong PR metadata.

## Phần chưa nghiệm thu

Spec có103 operation:41 implemented,59 planned,3 legacy; Vương có20 implemented, không còn operation planned của Vương. Đây là trạng thái hợp đồng/source, không tự hoàn thành toàn bộ #28/#33. Bản kê PR52 tại [VUONG-01-RECONCILIATION](VUONG-01-RECONCILIATION.md) là snapshot trước route này.

Các API hold/order/payment/ticket/scan/refund còn thiếu route bàn giao để chạy chuỗi đăng ký → sự kiện → giữ/mua vé → check-in → quyết toán. Đăng nhập thật, full-WAR/schema/startup/worker, hợp đồng namespace/TTL/QR/IPN và hook cache sau commit giữa các owner vẫn cần nghiệm thu. Provider mô phỏng/session bridge và job bị tắt/skipped không được tính là bằng chứng toàn sàn. #28/#33 và epic giữ mở, tổng hoàn thành4/6.

Cập nhật full WAR sau PR53: bản kiểm toàn ứng dụng phát hiện wildcard tổ chức trùng EventApiServlet. Scope sửa định tuyến, login/session thật và prerequisite runtime JSTL được ghi tại [VUONG-06-FULL-WAR](VUONG-06-FULL-WAR.md); các đoạn mô tả wildcard và login chưa nghiệm thu bên trên là snapshot tại PR53.
