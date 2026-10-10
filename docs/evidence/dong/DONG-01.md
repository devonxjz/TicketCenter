# DONG-01 — Public Event catalog, detail và Redis cache

Trạng thái: mã nguồn đã kiểm tra cục bộ; còn blocker nghiệm thu, chưa đủ điều kiện merge/đóng issue.

Ngày kiểm chứng: 10/10/2026 (UTC+7). Issue: [#8](https://github.com/devonxjz/TicketCenter/issues/8). Nhánh: `feat/dong/dong-01-event-catalog-cache`, tạo từ `origin/dev` tại `71e02ad`; fetch lại trước commit không có commit dev mới.

## Kết quả triển khai và rà soát

- Đủ 5 GET API public: `/api/events`, `/api/events/{eventId}`, `/api/events/{eventId}/zones`, `/api/zones/{zoneId}/seats`, `/api/event-categories`.
- Chỉ đọc Event qua `dbo.vw_PublicEvents`, Zone qua `dbo.vw_ZoneInventory`; Seat join public Event để không rò dữ liệu nháp/ẩn. Parent thiếu/ẩn trả 404, parent có nhưng không có con trả danh sách rỗng. Không tạo dữ liệu mẫu khi DB lỗi.
- Theo issue #8: keyword tối đa 100 ký tự; tìm tên sự kiện, venue name/address bằng bind và escape ký tự LIKE. Không có trường nghệ sĩ trong schema hiện tại nên không tự thêm model/cột.
- page mặc định 1, pageSize mặc định 12, tối đa 50. sort allowlist: `startTime_asc` (mặc định), `startTime_desc`, `price_asc`, `price_desc`; có event ID làm khóa phụ. `from` inclusive / `to` exclusive theo startTime; UI chuyển giờ địa phương sang UTC. Giới hạn offset 1.000.000, UUID đầy đủ và phạm vi thời gian hợp lệ cho SQL Server.
- DTO có tên category/organization, địa điểm, ảnh, lịch mở bán, `isSaleActive`, `status=PUBLISHED`, `minPrice`/`maxPrice`; Zone dùng `zoneType`, `basePrice`, `totalCapacity`, `availableSeats`; Seat dùng `seatLabel`. Sale active tính bằng giờ UTC của SQL Server. DTO không có credential/owner/entity graph.
- Giữ hợp đồng chuẩn CONVENTIONS: ID là chuỗi UUID, VND là chuỗi số nguyên, success envelope `data`, list có page/pageSize/total/totalPages. Các ví dụ numeric ID/decimal amount/raw response trong #8 khác quy chuẩn bắt buộc và model khóa; chưa đổi model/dependency/database để khớp ví dụ minh họa.
- SQL object thực tế là `vw_PublicEvents`/`vw_ZoneInventory`; không có các View/UDF `v_events`, `v_zones`, `v_seats`, `fn_event_is_sale_active` minh họa trong issue. Repository dùng các object canonical hiện có và expression UTC cho sale active.
- Key Redis list SHA-256 gồm scope PUBLIC, page, pageSize, sort và filter canonical. Namespace `tc:event:list:v1:<hash>`, `tc:event:detail:v1:<id>`, `tc:event:zones:v1:<id>`, `tc:zone:seats:v1:<id>`, `tc:event:categories:v1`. TTL list/Zone/Seat 30s, detail 120s, category 300s.
- Cache chỉ lưu JSON DTO response sau transaction đọc hoàn tất; kiểm tra JSON hoàn chỉnh, giới hạn kích thước/độ sâu, loại cache hỏng. Redis lỗi fallback DB, WARN có giới hạn tần suất và không chứa provider detail/filter/credential. DB lỗi không cache và trả 503; lỗi mapping nội bộ không bị nhầm thành 400.
- Hai JSP public bỏ dữ liệu mẫu, dùng API, có filter/pagination/loading/empty/error và sơ đồ ghế theo hàng. Màu xanh/trống, vàng/đang giữ, xám/đã bán; dữ liệu DB hiển thị bằng textContent, ảnh chỉ HTTP/HTTPS. Không có SQL/Java scriptlet hoặc tự giữ vé/đặt đơn. Trang detail có thể xem khi chưa đăng nhập.
- Ownership: chỉ Event controller/service/repository/dto/cache, hai JSP public Event, test Event và evidence; không thay model, config, pom, persistence.xml, database, OpenAPI, CI hoặc domain khác.

## Kiểm chứng bản được commit

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| Unit/controller/repository/cache + hồi quy Event/check-in/identity không gọi dịch vụ thật | **126 tests, 0 failures, 0 errors, 0 skipped**, exit 0 | `target/dong-01-review-verify.log`, `target/surefire-reports/` |
| Biên dịch và đóng gói WAR trong cùng lần verify | BUILD SUCCESS, exit 0 | `target/ticketscenter.war`, log verify |
| UI JavaScript/DOM với API fixtures | PASS: catalog, pagination, empty/error, literal text/XSS, unsafe image URL, invalid/hidden Event, Zone/Seat rows/colors | `src/test/resources/event/dong-01-ui-check.cjs` |
| SQL Server IT thật, không skip | Exit 1: thiếu `TC_EVENT_TEST_PUBLISHED_ID`; chưa kết nối DB | `target/dong-01-review-sqlserver-it.log` |
| Diff/ownership | Kiểm trước commit bằng staged diff/check | Các đường dẫn ở commit |

Lệnh verify:

```powershell
mvn -B '-Dtest=*Test,!JpaConnectionTest,!DemoAccountSeederTest,!MigrationSuiteVerificationTest,!OtpRepositoryTest,!AuthServiceTest,!PasswordResetServiceTest,!OtpServiceTest,!EmailSenderTest' verify
node src/test/resources/event/dong-01-ui-check.cjs
mvn -B -Dit.test=EventCatalogSqlServerIT failsafe:integration-test failsafe:verify
```

Các suite bị loại khỏi verify trên có kết nối/seed DB hoặc gửi SMTP thật; không gọi chúng để báo toàn bộ suite xanh. `PasswordResetIT` cũng không được chọn bởi `*Test`. Full mặc định/SQL Server/Redis thật/browser/Actions chưa được xác nhận đạt. DOM fixture kiểm tra hành vi JavaScript, không thay bằng chứng chạy WAR trong trình duyệt thật.

Máy kiểm chứng dùng JDK 25 tại `C:\Users\Lenovo\.jdks\openjdk-25.0.2` và Maven đi kèm IntelliJ tại `D:\New folder\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd`; chỉ đặt JAVA_HOME trong phiên lệnh, không sửa config dự án. Đầu ra `target/` bị Git ignore và không được commit/upload như evidence đã lọc.

## Blocker và điều kiện merge

1. Quyền public Seat projection: đã mở [issue #45](https://github.com/devonxjz/TicketCenter/issues/45), nội dung [DONG-01-COORDINATION.md](DONG-01-COORDINATION.md). Runtime `tc_buyer` chưa có quyền đọc `tc_seats`; API Seat sẽ 503 với đúng principal giới hạn cho đến khi owner database cung cấp đường đọc. Không chuyển sang admin/manager để vượt quyền.
2. Fixture SQL riêng: `TC_SQL_TEST_DB`, `TC_SQL_TEST_LOGIN`, `TC_SQL_TEST_PASSWORD` qua môi trường riêng; login thuộc `tc_buyer` và không phải sysadmin. Cần `TC_EVENT_TEST_PUBLISHED_ID`, `TC_EVENT_TEST_HIDDEN_ID`, `TC_EVENT_TEST_ZONE_ID` (SEATED Zone có ghế thuộc public Event). IT đọc DB để kiểm visibility, Zone/Seat, pagination và cache hit tránh query; cache trong IT là adapter bộ nhớ để đếm query, chưa chứng minh Redis thật.
3. Nghiệm thu WAR/browser/Swagger/Redis thật: filter/empty/400/404/503, key/TTL/cache hit/miss, dữ liệu DB đối chiếu. Không suy ra đã đạt từ unit tests hoặc HTTP 200.
4. Mutation DONG-02/03/04/05 và hold/cancellation cần invalidate đúng namespace v1 sau commit khi ghép; rollback không invalidate. DONG-01 chỉ có GET và chưa nối vào feature khác. Cache còn có độ trễ trong TTL nếu mutation chưa nối invalidation.
5. Commit dùng `Task-Id: DONG-01`, `Refs: #8`; PR draft về dev và issue #8 giữ mở. Chỉ merge khi blocker được xử lý, checks phù hợp xanh và có reviewer khác phê duyệt. Không merge/close issue trong lần bàn giao này.
