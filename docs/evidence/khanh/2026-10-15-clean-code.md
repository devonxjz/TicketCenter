# KHANH-01–06 — Dọn code và kiểm tra hồi quy

Ngày: 15/10/2026 (Asia/Saigon).

## Phạm vi

Phạm vi được xác nhận: chọn sự kiện soát vé, quét QR/mã vé, yêu cầu hoàn vé,
duyệt/từ chối/thử lại hoàn tiền, worker hoàn tiền và hủy sự kiện.

- Tách các đoạn kiểm tra request, đọc body, tạo fingerprint, xử lý kết quả gateway
  và kiểm tra cache thành hàm có tên rõ ràng.
- Dùng chung `RefundJsonReader` cho hai bộ phân tích JSON hoàn tiền, giữ giới hạn
  chuỗi riêng 500/1.000 ký tự và cách từ chối dữ liệu không đúng kiểu.
- Tách script người mua sang `assets/refund/request-form.js` và `my-refunds.js`;
  giữ nguyên vị trí chạy, thuộc tính dữ liệu và lời gọi API.
- Định dạng Java, JavaScript và JSP; bỏ comment lặp lại code, giữ ghi chú về
  quyền hiện tại, giao dịch, đối soát và gửi lại yêu cầu.
- Giữ nguyên route, mã lỗi/thông báo, SQL và tham số, transaction, CSRF/session,
  chữ ký QR, idempotency, khóa/TTL Redis và xử lý kết quả chưa xác định.

Không sửa migration, cấu hình triển khai hay dependency.

## Kết quả kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| Toàn bộ Java và đóng gói WAR | 1.066 ca: 923 đạt, 143 bỏ qua, 0 lỗi; BUILD SUCCESS |
| JavaScript unit/regression và bộ kiểm tra báo cáo | 229 ca: 216 đạt, 13 bỏ qua, 0 lỗi |
| Biên dịch JSP bằng Jasper/Tomcat 11.0.25 | 7/7 trang đạt |
| Kiểm tra khoảng trắng của diff | Đạt |
| Rà soát độc lập thay đổi so với HEAD | Không phát hiện lỗi hồi quy cần sửa |

Ba ca Java mới bảo vệ giới hạn chuỗi sau giải mã, escape/Unicode, giá trị null,
field trùng và khoảng trắng JSON. Ba ca Node mới bảo vệ chọn vé, tổng tiền
BigInt, xác nhận, CSRF, gửi lại cùng key/body, phản hồi đơn hàng cũ và lịch sử
hoàn tiền hiển thị bằng text. Bộ kiểm tra script người mua cũng đã chạy với
script inline gốc để đối chiếu hành vi trước và sau khi tách file.

Các JSP đã biên dịch: `checkin/select-event.jsp`, `checkin/scanner.jsp`,
`refund/request-form.jsp`, `refund/my-refunds.jsp`, `admin/refunds/list.jsp`,
`admin/refunds/review.jsp`, `event/cancellation.jsp`.

## Lệnh và giới hạn

JDK 25.0.4.1, Maven offline, dependency có sẵn:

```powershell
mvn -B -o '-Daether.syncContext.named.factory=noop' package `
  '-DargLine=-Djava.io.tmpdir=target/test-temp' `
  '-Dtc.tests.db=false' '-Dtc.tests.smtp=false' '-Dtc.tests.redis=false'
```

Thư mục tạm nằm trong workspace để các bài kiểm tra ghi/đổi tên tệp hoạt động
trong sandbox Windows. Cờ Maven chỉ áp dụng cho lần chạy, không sửa cấu hình dự án.

Node chạy các file `src/test/js/*.test.cjs`, `*.test.js` và
`src/test/scripts/test-report-summary.test.cjs`, ngoại trừ
`finance-http-smoke.test.cjs` và `swagger-http-smoke.test.cjs`: hai bộ này cần
fixture Tomcat riêng. Bài kiểm tra HTTP logout dùng máy chủ tạm trên loopback.

Log tại `target/clean-java-verification.log`, `target/clean-node-verification.log`
và `target/clean-jsp-verification.log`. WAR: `target/ticketscenter.war`.

Đợt này chưa chạy lại SQL Server/Redis/SMTP integration hoặc toàn bộ website với
cơ sở dữ liệu thật. Biên dịch JSP không khởi động listener, worker hay phiên đăng
nhập của ứng dụng; kết quả này xác nhận khả năng biên dịch trang, không thay thế
kiểm tra end-to-end.

## Xác minh bản chỉ clean trước khi đưa vào dev — 16/10/2026

Bản đưa lên được tách trong checkout riêng từ `origin/dev` tại
`69371845e53353330cbc178314e7443271535205`. Chỉ gồm refactor KHANH-01–06
và hai bộ test mới cho JSON hoàn tiền, giao diện hoàn vé người mua.
Các thay đổi hiệu năng check-in sau đó, cấu hình local/Hold/database và
file Word giải thích được giữ ngoài commit.

Đã chạy lại trên chính bản tách riêng: 1.066 ca Java (923 đạt, 143 bỏ qua,
0 lỗi), 229 ca JavaScript (216 đạt, 13 bỏ qua, 0 lỗi), biên dịch 7/7 JSP
và đóng gói WAR thành công. Log trong `target/clean-release-java.log`,
`target/clean-release-node.log` và `target/clean-release-jsp.log` của
checkout kiểm tra. Bản scanner clean còn vượt 7/7 test hồi quy gốc.

Ba lượt rà soát độc lập không phát hiện thay đổi hành vi trong quyền,
SQL/tham số/giao dịch, CSRF, chữ ký QR, idempotency, cache/TTL, hoàn tiền
và hủy sự kiện. Các integration test cần fixture riêng không được tính
là đã đạt khi bỏ qua. Không chạy giao dịch kiểm thử trên database chung.