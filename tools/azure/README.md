# Chạy web với dữ liệu Azure dùng chung

Đích của nhóm: **ticketscenter.database.windows.net / TicketsCenter**.
Mọi máy đọc cùng database; không cần chép SQL, tạo database local hay chạy seed.
Dữ liệu hiển thị phụ thuộc nội dung và quyền tài khoản hiện có trên Azure.
Kết nối database không xác nhận dữ liệu đó là giao dịch thật.

## Chuẩn bị lần đầu

Cài JDK 25, Maven, PowerShell 7.4+ và Tomcat 11. Azure SQL phải cho phép IP
máy bạn kết nối. Lấy tài khoản SQL được cấp cho bạn qua kênh riêng của nhóm;
không đưa mật khẩu vào Git, issue hoặc chat công khai.

Tại thư mục repository:

```powershell
New-Item -ItemType Directory -Path .local -Force | Out-Null
Copy-Item tools/azure/azure.properties.example .local/azure.properties
```

Mở `.local/azure.properties`, điền `db.user` và `db.password` là tài khoản
**SQL Azure**, không phải tài khoản đăng nhập web. Giữ nguyên host/database.
File dùng các cặp `key=value`; nhập mật khẩu nguyên văn, không thêm dấu nháy.
Không chạy lệnh copy lại nếu đã điền cấu hình.
Nếu nhóm có các tài khoản AUTH/BUYER/MANAGER/CHECK_IN/ADMIN/WORKER riêng,
bỏ comment và điền các dòng tương ứng. Không có override thì dùng `db.user`.

Nếu cần kiểm vé QR giữa các máy, điền cùng `app.secret.key` của môi trường
đang sử dụng, nhận riêng từ người quản lý. Khóa cục bộ tự sinh chỉ giữ ổn định
trên máy đó; không tương đương khóa ký chung của nhóm.

## Chạy

```powershell
# Thay bằng đường dẫn bộ cài trên máy bạn.
$env:JAVA_HOME = 'C:\Tools\jdk-25'
./tools/azure/run.ps1 -TomcatHome 'C:\Tools\apache-tomcat-11.0.25'
```

Chờ `BUILD SUCCESS` và `AZURE_APP_READY`, sau đó mở
[trang khám phá](http://127.0.0.1:18082/ticketscenter/).
Giữ terminal mở. `Ctrl+C` dừng đúng phiên này. Nếu cổng bị chiếm, dùng `-Port 18083`.
Có thể chỉ định `-JavaHome` và `-ConfigPath` cho đường dẫn riêng.

Script kiểm đúng Azure target, build/test không kết nối các fixture ngoại vi,
tải JSTL đã khóa checksum và tạo Tomcat base riêng trong `.local/azure-runtime`.
Chỉ báo sẵn sàng khi health và API sự kiện trả 200. Cấu hình mật khẩu runtime
được lưu ngoài WAR, chỉ tài khoản Windows hiện tại và SYSTEM được đọc;
được xóa khi dừng bình thường. Log riêng nằm trong `logs` của phiên đó.
JDK dùng thư mục socket riêng của tài khoản Windows dưới ProgramData để tránh
lỗi kết nối nội bộ với đường dẫn TEMP trên Windows; thư mục này không chứa mật khẩu.

Chế độ Azure không tạo/reset tài khoản mẫu, không cài schema hoặc import dữ liệu,
không bật worker hoàn tiền mô phỏng, payout mô phỏng hoặc gửi mail outbox.
Các thao tác thực hiện qua web và worker giữ vé hiện có vẫn có thể cập nhật
database dùng chung. Bộ chạy này phục vụ kiểm tra web local với Azure;
nó không thay cho cấu hình provider và nghiệm thu triển khai công khai.

Đăng nhập bằng tài khoản web đang có trong Azure. Menu cũ có thể còn dẫn tới
trang prototype hoặc lỗi; cấu hình Azure không tự sửa các trang đó.

Kiểm cấu hình mà không kết nối/build/khởi động:

```powershell
./tools/azure/run.ps1 -CheckConfig
```

`AZURE_CONFIG_VALID` chỉ xác nhận trường bắt buộc và đích kết nối, không xác nhận mật khẩu đúng.
