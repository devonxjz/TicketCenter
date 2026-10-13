# Chạy web bằng một file cấu hình

Toàn bộ cấu hình máy, kết nối database và đường dẫn nằm trong
`src/main/resources/application.properties`. Trên máy Vương, đường dẫn là
`E:/TicketCenter/src/main/resources/application.properties`. Không cần `azure.properties`.

## Lần đầu trên máy đồng đội

Pull nhánh dev. Nếu chưa có file thật, chạy tại thư mục repository:

```powershell
Copy-Item src/main/resources/application.properties.example src/main/resources/application.properties
```

Không copy đè nếu đã có cấu hình. Điền trong cùng file:

```properties
db.host=ticketscenter.database.windows.net
db.name=TicketsCenter
db.user=
db.password=
runtime.java.home=C:/Tools/jdk-25
runtime.tomcat.home=C:/Tools/apache-tomcat-11.0.25
runtime.port=18082
```

`db.user` và `db.password` là tài khoản SQL Azure được cấp qua kênh riêng,
không phải tài khoản đăng nhập web. Điền giá trị nguyên văn, không thêm dấu nháy.
Dùng dấu `/` cho đường dẫn Windows. Có thể điền các `db.auth.*`, `db.buyer.*`,
`db.manager.*`, `db.check_in.*`, `db.admin.*`, `db.worker.*` nếu được cấp riêng;
để trống thì dùng tài khoản SQL chung từ `db.user` / `db.password`.

File thật được Git bỏ qua và Maven không đóng gói vào WAR. Chỉ file `.example`
không chứa mật khẩu được chia sẻ trong repo. Không force-add file thật vào Git.
JDK 25, Maven, PowerShell 7.4+ và Tomcat 11 phải được cài trước. Azure SQL cần
cho phép IP của máy kết nối. Không chạy setup local hoặc seed dữ liệu.

## Chạy

```powershell
./tools/run-web.ps1
```

Script lấy cả đường dẫn bộ cài và cổng từ file chính. Có thể tạm ghi đè bằng
`-JavaHome`, `-TomcatHome`, `-Port`; `-ConfigPath` dành cho môi trường kiểm thử.
Chờ `BUILD SUCCESS` và `AZURE_APP_READY`, rồi mở
[trang khám phá](http://127.0.0.1:18082/ticketscenter/). Giữ terminal mở; `Ctrl+C`
dừng đúng phiên này. Đăng nhập bằng tài khoản web hiện có trong Azure.

Mọi máy đọc cùng database TicketsCenter, không import hay sao chép dữ liệu.
Nguồn gốc dữ liệu phụ thuộc bản ghi Azure, không được xác nhận chỉ bằng việc kết nối.

Nếu `app.secret.key` trống, script sinh một lần và lưu ngay trong file chính.
Để QR hoạt động nhất quán giữa các máy, lấy cùng khóa ký hiện có từ người quản lý
qua kênh riêng và điền vào trường này; khóa mỗi máy khác nhau không tương đương khóa chung.

Các đường dẫn dữ liệu như `event.image.directory` cũng chỉnh trong file chính.
Đường dẫn tương đối được tính từ thư mục repository. Script tự tạo thư mục runtime
riêng trong `.local`; bản cấu hình hiệu lực ở đó là bản sinh tự động, không cần chỉnh
và được xóa khi dừng bình thường. Quyền đọc thuộc tài khoản Windows hiện tại và SYSTEM.
Thư mục socket JDK riêng dưới ProgramData giúp tránh lỗi TEMP của Windows.

Chế độ chạy không tạo tài khoản demo, không sửa schema/import, không bật hoàn tiền
mô phỏng, chi trả mô phỏng hoặc mail outbox. Các thao tác trên web và worker giữ vé
vẫn có thể cập nhật database chung. Đây là bộ chạy web local với Azure; provider
và luồng triển khai công khai cần cấu hình/nghiệm thu riêng.

Kiểm trường cấu hình mà không build, kết nối hoặc khởi động:

```powershell
./tools/run-web.ps1 -CheckConfig
```

`AZURE_CONFIG_VALID` không xác nhận mật khẩu đúng.
