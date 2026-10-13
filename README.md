# TicketsCenter

Ứng dụng Java25, Servlet6.1/JSP trên Tomcat11, JPA/Hibernate và SQL Server.
Mục tiêu demo/portfolio theo [SPEC](docs/references/SPEC.md): VNPAY Sandbox;
hoàn tiền và chi trả mô phỏng. SQL Server quyết định nghiệp vụ/quyền;
Redis phục vụ cache/idempotency.

## Chạy local trên Windows từ checkout sạch

**Để xem cùng dữ liệu Azure của nhóm:** cấu hình duy nhất tại `src/main/resources/application.properties`, rồi chạy `./tools/run-web.ps1` theo [hướng dẫn](docs/backend/running-web.md).
Bộ chạy local dưới đây dùng database test riêng, không phải database dùng chung.

Cài **JDK25**, **Maven**, **Node.js**, **PowerShell7.4+**, **Tomcat11** và
**SQL Server** trước. SQL Server phải cho phép TCP tại `127.0.0.1:1433`,
SQL authentication; Windows account hiện tại có quyền tạo database/login.
Bộ chạy không thay cấu hình SQL Server hoặc khởi động lại dịch vụ.

Mở PowerShell7.4+ ở thư mục repository vừa clone:

```powershell
# Thay hai đường dẫn bằng nơi đã cài trên máy bạn.
$env:JAVA_HOME = 'C:\Tools\jdk-25'
$tomcatHome = 'C:\Tools\apache-tomcat-11.0.25'

# Chỉ chạy lần đầu. Tên MỚI, không phải database dùng chung của nhóm.
./tools/local/setup.ps1 -Database TC_Vuong_Test_Local_Demo

# Build, test, bổ sung JSTL vào Tomcat base riêng, kiểm tra login rồi giữ web chạy.
./tools/local/run.ps1 -TomcatHome $tomcatHome
```

Mở **http://127.0.0.1:18082/ticketscenter/auth/login** hoặc
[Swagger](http://127.0.0.1:18082/ticketscenter/swagger/).
Giữ terminal mở; `Ctrl+C` dừng JVM vừa tạo. Không dừng Tomcat khác hoặc sửa
Tomcat đã cài. Port mặc định18082; dùng `-Port 18083` nếu cần.

Tài khoản demo: Manager `abcc@manager.com`, Staff `abcc@staff.com`,
Buyer `buyer@ticketscenter.vn`, mật khẩu `123`. ADMIN dùng mật khẩu ngẫu nhiên;
đọc file tại dòng `LOCAL_ACCOUNTS` trong terminal, không đưa file lên Git hoặc
gửi mật khẩu trong chat. Mật khẩu ADMIN đổi mỗi lần chạy.

Để nghiệm thu rồi tự dừng:

```powershell
./tools/local/run.ps1 -TomcatHome $tomcatHome -Smoke
```

Chỉ tính đạt khi có `BUILD SUCCESS`, full WAR tests không lỗi và
`FULL_WAR_ACCEPTANCE_PASS`. Integration test ngoại vi bị skip trong Maven
không được tính là nghiệm thu các miền đó. Node.js bắt buộc để chạy smoke.

## Phạm vi của bộ chạy

Đây là **fixture local biệt lập** để chứng minh đóng gói, startup, JSP,
login/session/CSRF và API tài chính đã ghép. Chỉ kết nối DB có tên/marker test
hợp lệ trên loopback. Cấu hình đóng gói được thay và biến môi trường kế thừa
được loại trước khi bật listener/seeder/worker.

DB mới dựng từ snapshot UUID/`tc_*` đã duyệt và các migration theo thứ tự ở
[database runbook](docs/backend/database-runbook.md). Login ngẫu nhiên có
`db_owner` **chỉ trong fixture mới**, dùng cho sáu persistence principal;
đây **không phải** bằng chứng least privilege. Không chạy setup trên Azure/DB
dùng chung hoặc áp migration001–008 cũ lên snapshot này.

Mail/VNPAY dùng loopback vô hiệu, Cloudinary không cấu hình, payout không bật.
Mua vé/check-in/refund liên hoàn và provider vẫn phụ thuộc bàn giao owner.
Web chạy được trong khi API còn planned; không coi trang/dữ liệu mẫu là toàn
bộ sản phẩm đã nghiệm thu.

## Cấu hình và lỗi thường gặp

Setup tự sinh `.local/database.json` với ACL riêng, không nhập password trên
dòng lệnh. [database.example.json](tools/local/database.example.json) mô tả cấu
trúc, không phải credential dùng được. `.env.example` liệt kê biến cấu hình cho
môi trường nhóm được duyệt riêng; Java **không tự đọc** `.env`; bộ local dùng
JSON do setup tạo. Riêng bộ fixture local không dùng `application.properties`.
Bộ chạy web Azure đọc file đó theo hướng dẫn ở trên; không commit file cấu hình thật.

- `JDK 25 is required`: chọn đúng JAVA_HOME; Java mặc định có thể khác.
- SQL lỗi: kiểm Windows permissions, TCP/SQL authentication; xem runbook.
- DB/config đã tồn tại: giữ lại; dùng config cũ để chạy lại, hoặc tên DB mới
  với `setup.ps1 -ConfigPath .local/another-database.json`.
- Thiếu Maven/Node/Tomcat: cài prerequisites; không tự cài service toàn máy.
- Tải JSTL lỗi: cần Internet tới Maven Central. Artifact3.0.1 được kiểm SHA512
  cả khi dùng cache `.local/lib`.
- Startup/test lỗi: xem `FULL_WAR_LOGS`. Secret runtime được xóa khi JVM dừng;
  nếu kill terminal cưỡng bức, xem [deployment runbook](docs/backend/deployment.md).

Xem [backend acceptance](docs/evidence/backend-acceptance.md) cho bằng chứng và
gate còn chờ. CI xanh không tự chứng minh deployment online.
