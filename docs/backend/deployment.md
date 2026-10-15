# Khởi động và dừng local

[README](../../README.md) là hướng dẫn chính. Native Windows:
JDK25/Maven/Node/PowerShell7.4+/Tomcat11/SQL Server đã cài, không cần Docker.
Script fail trước build nếu sai JDK/Tomcat.

`run.ps1` build WAR với safe-default tests, tải hai JSTL jar3.0.1 từ Maven
Central và kiểm SHA512, không sửa POM/Tomcat installation. Mỗi lần tạo
CATALINA_BASE mới `target/full-war-smoke/<guid>`, bind loopback, giữ annotation,
listener/worker và Hibernate validate. Không cài Windows service.

Cấu hình WAR được thay trước startup; biến DB/provider/Java injection kế thừa
bị loại khỏi child process. Maven cũng loại MAVEN_ARGS/MAVEN_OPTS và ép test
ngoại vi=false, skipTests=false; khôi phục môi trường terminal khi kết thúc.
Không bật Cloudinary, SMTP/VNPAY ngoài hoặc payout.
Refund/cancel worker mô phỏng vẫn chạy trên fixture; chưa nghiệm thu toàn bộ
scheduler.

## Dừng

Ctrl+C ở terminal giữ run.ps1. Finally dừng đúng JVM vừa tạo, lưu stdout/stderr,
xóa file credential runtime/accounts. `.local/database.json` giữ để mở lại.
Không xóa Tomcat đã cài.

Nếu terminal bị kill cưỡng bức, kiểm command line/PID qua Task Manager hoặc
Get-CimInstance Win32_Process: chỉ dừng Java có `-Dcatalina.base` trỏ đúng
`target/full-war-smoke/<guid>` của lần đó, không kill mọi java.exe. Sau khi đã
xác nhận JVM dừng, xóa hai file application.properties/.example trong
WEB-INF/classes của đúng runtime và accounts.json còn lại. Không công khai
logs chưa lọc.

## Online là gate riêng

Dockerfile/compose chưa được bộ này nghiệm thu. `06-deploy-production.yml` chỉ
rollout simulation. Online cần schema/grants, JSTL, Redis, SMTP, Cloudinary,
merchant VNPAY Sandbox/callback HTTPS; kiểm Docker cold-start/restart,
quota/memory, backup/restore, E2E trên commit bàn giao. Không dùng login fixture
db_owner/config local/tài khoản demo mặc định cho cấu hình công khai.
