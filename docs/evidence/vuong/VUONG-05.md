# VUONG-05 — CSV độc lập, bằng chứng thực thi

Issue [#32](https://github.com/devonxjz/TicketCenter/issues/32) vẫn mở. Người dùng duyệt thiết kế/kế hoạch ngày 09/10/2026 và chọn Native. Phạm vi hoàn tất local: `CsvSanitizer` và `StreamingCsvExporter` trong `report/csv`, test và tài liệu. Chưa triển khai endpoint, UI, SQL, quyền tổ chức hoặc Redis; chưa push/PR/merge.

## Revision và môi trường

- Base `origin/dev`: `cfd77c60ce539f145a6af7749ba050561be6dd3f`.
- Approved design/plan: `cd10a713b4e4693d4ee87da8f6f73f69ad6162d4`.
- Sanitizer: `a71afec492e03b59d1e92b32d17141b264f1d029`.
- TestedRevision Task 2: `a71afec` + hai file exporter/test và evidence ở commit chứa tài liệu này. Cả hai class production nằm trong WAR đã tạo từ source đó. Commit Task 2 có thể tra bằng subject `feat(report): stream UTF-8 CSV with explicit IO failures`.
- Windows, OpenJDK 25, Maven 3.9.16. Worktree `C:/Users/Le Vuong/.codex/worktrees/vuong-05-safe-csv/TicketCenter` giữ artifact và reports; không thay target của checkout chính đang có preview.

## TDD và kiểm thử

Task 1 RED: `mvn -B '-Dtest=CsvSanitizerTest' test` exit 1 do chưa có API `CsvSanitizer`, không phải assertion thất bại. GREEN cùng lệnh exit 0: **34 tests, 0 failures/errors/skips**.

Task 2 RED: `mvn -B '-Dtest=CsvSanitizerTest,StreamingCsvExporterTest' test` exit 1 do chưa có API `StreamingCsvExporter`. GREEN cùng lệnh exit 0: **56 tests** (34 sanitizer + 22 exporter), **0 failures/errors/skips**.

Nhóm hồi quy an toàn chạy sau implementation, exit 0: **99 tests, 0 failures/errors/skips** (56 mới + 43 baseline). Lệnh thực tế:

```powershell
mvn -B '-Dtest=CsvSanitizerTest,StreamingCsvExporterTest,FeaturePackageStructureTest,AuthOtpServletsTest,IdentityWebFlowTest,UserPrincipalTest,SecurityContextTest,OtpCryptoHelperTest,AuthServiceTest#testRegisterInvalidEmail+testRegisterPasswordTooShort+testLoginNullOrBlankInputs+testCurrentUserDtoSanitization,EmailSenderTest#shouldValidateEmailMessageFields+fakeEmailSenderShouldRecordMessages+fakeEmailSenderShouldThrowWhenFailureSimulated' test
mvn -B -DskipTests package
git diff --check
```

Package exit 0 (`BUILD SUCCESS`), WAR `target/ticketscenter.war` chứa cả `CsvSanitizer.class` và `StreamingCsvExporter.class`. `git diff --check` exit 0. Package dùng `-DskipTests` sau khi nhóm test trên đã đạt; không coi package là một lần chạy test khác.

Không chạy bare `mvn test`: repo có ca DB/SMTP chung. Kết quả trên không chứng minh full-suite/CI hoặc tích hợp DB/HTTP đã đạt.

## Hành vi đã kiểm

- Quote mọi field, nhân đôi dấu nháy; giữ dấu phẩy/chấm phẩy/newline, tiếng Việt, emoji và khoảng trắng. Null cell thành field rỗng; NUL/UTF-16 lỗi bị từ chối với thông báo không echo dữ liệu.
- Prefix ASCII/full-width `=+-@`, whitespace Unicode và CONTROL/FORMAT ở đầu được trung hòa bằng một TAB thêm trước toàn raw value, bên trong quoted field. Đây là thay đổi contract so với apostrophe trong issue, theo lựa chọn người dùng; không idempotent, caller phải đưa raw logical cells.
- UTF-8 BOM, header được snapshot/sanitize trước byte đầu, comma và CRLF cho mọi record. Chuỗi số lớn `9223372036854775807` và UTC giữ nguyên ở cấp byte.
- Một iterator, header flush trước khi đọc nguồn, 10.000 rows sinh lười và sink không lưu dữ liệu. Có tiến độ output mỗi 64 rows; tổng độc lập **10.348.905 bytes**. Đây là kiểm chứng không thu toàn dataset trước khi ghi, không phải phép đo RSS hay bảo đảm toàn pipeline DB O(1).
- Output lỗi lúc BOM/header/data và final flush truyền cùng IOException; lỗi iterator giữ cùng RuntimeException, dừng đọc nguồn khi output lỗi. Không đóng stream caller ở cả success/failure. Row null/sai width bị từ chối trước khi ghi row đó; lỗi muộn có thể để lại file dở.

## Fixture và app acceptance

File `target/vuong-05-safe-csv-fixture.csv` được chạy qua implementation thật bằng JDK single-file runner trong `target/CsvFixture.java` (caller đóng file stream của mình). 425 bytes, SHA-256 `E696925EEA7024AE70D82854BC75DFDFBCF0BC7F5216146EDDFA4691DC85C592`.

Fixture chỉ có phép tính vô hại, các prefix spaces/NBSP/BOM/zero-width/tab, tiếng Việt/emoji, multiline/quote, số lớn và UTC. Không có link, DDE, lệnh hệ thống hoặc dữ liệu thật.

**Chưa chạy mở → lưu → mở lại trên Excel/Sheets.** Excel executable có trên máy, nhưng native app control bị vô hiệu hóa trong phiên và chưa xác minh được workbook/add-in/session bằng đường điều khiển được hỗ trợ. Không dùng byte tests hoặc thư viện đọc CSV để thay bằng chứng app. Version/locale và kết quả app chưa có. Giữ RPT app acceptance mở; cần kiểm ô nguy cơ không thành công thức sau cả hai lần mở và kiểm delimiter/Unicode trên locale đích. Excel auto-number có thể làm tròn số lớn dù CSV bytes đúng; owner cần chốt chính sách cột tiền/ID trước expose report.

## Gates tích hợp còn mở

1. Owner chấp nhận Excel/TAB contract và consumers biết TAB thay đổi logical value. Tham khảo [OWASP CSV Injection](https://community.owasp.org/attacks/CSV_Injection) và [RFC 4180](https://www.rfc-editor.org/info/rfc4180/).
2. Schema/View/UDF và metric/filter/timezone chung cho UI/JSON/CSV được owner chốt.
3. JDBC DB test riêng; tenancy Manager A/B, quyền trước BOM/header; nguồn/cursor đóng trong finally.
4. HTTP Content-Type/Content-Disposition, disconnect/partial export và error handling.
5. Cache scope/filter/invalidation, app acceptance version/locale, CI cô lập DB/SMTP, review đồng đội trước merge.

## Review và bàn giao

Review độc lập toàn nhánh đang chờ sau commit Task 2. Không xác nhận merge-ready cho toàn #32. Thiết kế và plan sẽ được cập nhật cùng kết quả review cuối.
