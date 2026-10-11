# Database cho bản bàn giao local

PowerShell7.4+/Windows; SQL Server có TCP127.0.0.1:1433, SQL authentication.
Windows account có quyền CREATE DATABASE/LOGIN. Không đổi TCP/service/firewall.

`tools/local/setup.ps1` chỉ tạo DB MỚI `TC_Vuong_Test_Local_<name>`; name gồm
1–40 chữ/số/gạch dưới. Endpoint là literal loopback, không tham số server.
Từ chối DB/config đã tồn tại; không DROP/overwrite.

## Thứ tự cài trên database rỗng

1. `database/tests/vuong/002_team_schema_snapshot.sql`: schema-only snapshot
   UUID/tc_* ngày10/10, không dữ liệu/credential Azure; marker fixture.
2. `database/migrations/027_team_schema_adapter.sql`: projection tài chính.
3. `database/migrations/027_vuong_finance.sql`: finance objects/grants.
4. `database/migrations/028_vuong_report_checkins.sql`: projection check-in báo cáo.
5. `database/migrations/028_event_image_workflow.sql`: image objects đã merge.

Tên trùng số027/028 thuộc scope khác nhau; không sắp mọi file theo số để chạy.
026 archive và001–008 cũ không phải bộ dựng fixture này. Setup đọc GO theo
batch; ghi SHA256 mỗi file vào `dbo.tc_local_handoff_migrations`. Batch lỗi giữ
fixture để kiểm tra, không có config ready, không coi cài thành công. Retry
tên mới; không áp snapshot lên DB đã có bảng.

Login ngẫu nhiên có db_owner CHỈ trong DB mới. JSON giới hạn ACL current Windows
user/SYSTEM; không print password hoặc đưa SQL password lên dòng lệnh. Fixture
chưa chứng minh grants giới hạn của sáu runtime principal.

## Kiểm tra chỉ đọc trong SSMS

```sql
SELECT DB_NAME() AS database_name;
SELECT fixture_key FROM dbo.tc_vuong_test_fixture;
SELECT path, sha256, installed_at FROM dbo.tc_local_handoff_migrations ORDER BY installed_at, path;
```

Cần đủ5 file, marker `vuong-team-schema-v1`/`vuong-finance-v1`.
Chạy `tools/local/run.ps1 -Smoke` để kiểm Hibernate validate và HTTP/JSP/login.
Không sửa trực tiếp bảng/schema để làm test qua.

## Môi trường nhóm

Chủ DB cung cấp schema chuẩn, migration ledger, sáu login riêng và test
GRANT/DENY; #45 cần public Seat projection cho buyer. Bộ local không áp SQL
lên DB chung và không đóng các gate này. Migration owner tương lai phải chốt
trước khi bổ sung vào danh sách local.

Fixture giữ khi dừng web để mở lại. Khi không cần, chủ SQL kiểm đúng tên/không
còn kết nối rồi tự xóa riêng DB/login đã tạo. Không cleanup DB tự động.
