# VUONG-05 — thiết kế phần CSV độc lập

Issue: [#32](https://github.com/devonxjz/TicketCenter/issues/32). Nhánh `feat/vuong/vuong-05-reports-csv-cache`, rẽ từ `origin/dev` tại `cfd77c60ce539f145a6af7749ba050561be6dd3f`. Tài liệu thiết kế và kế hoạch được lập theo yêu cầu người dùng ngày 09/10/2026; chưa phê duyệt triển khai.

## 1. Mục tiêu và phạm vi

Tạo hai tiện ích thuần Java để module report có thể xuất dữ liệu CSV phục vụ người mở bằng Excel: trung hòa ô có nguy cơ bị diễn giải thành công thức, giữ tiếng Việt và ghi tuần tự. Thành công ở giai đoạn này là API rõ ràng, unit test byte/stream/error thật và evidence đúng phạm vi.

Repo hiện chỉ có `report/.gitkeep`, chưa có luồng xuất CSV để sửa. Đây là thành phần mới. Chỉ tạo code/test trong `vn.ticketscenter.report.csv`; không đổi model, identity, pom, persistence, SQL hoặc workflow. Không thêm thư viện.

Chưa làm báo cáo tài chính/DTO metric, Repository/Service/Servlet/JSP, phân quyền tổ chức, Redis hoặc endpoint HTTP. Exporter nhận các ô đã được caller định dạng thành String; không tính tiền, đoán kiểu, làm tròn hoặc lấy thời gian máy. Format tiền nguyên VND và ISO-8601 UTC sẽ do adapter báo cáo dùng cùng contract với UI ở giai đoạn sau.

## 2. Phương án và lựa chọn

| Phương án | Đánh giá |
|---|---|
| Hai utility JDK thuần, output stream do caller sở hữu | Đề xuất: nhỏ, kiểm thử độc lập, không đụng dependency/file khóa |
| Thêm thư viện CSV rồi bọc sanitizer | Giảm phần xử lý cấu trúc CSV nhưng vẫn phải tự xử lý công thức và cần owner chấp thuận pom |
| Làm luôn HTTP/report/SQL | Phụ thuộc schema, dữ liệu và quyền liên miền; chưa phù hợp phần độc lập đã chọn |

Người dùng chọn **ưu tiên Excel với tab bên trong ô được quote** ngày 09/10/2026. Vì vậy, literal `'=SUM(...)` trong ví dụ/test của issue được thay bằng logical value `\t=SUM(...)`, serialized thành `"\t=SUM(...)"`. Dấu tab ở đây là ký tự U+0009 thật, không phải hai ký tự backslash/t. Cần owner chấp nhận thay đổi contract trước khi ghép adapter chung.

Nguồn bảo mật: [OWASP CSV Injection](https://community.owasp.org/attacks/CSV_Injection) mô tả nguy cơ công thức, ký tự khởi đầu và tab trong quoted field cho Excel, đồng thời nêu giới hạn giữa các spreadsheet và thao tác lưu/mở lại. Không coi unit test byte là bằng chứng an toàn cho mọi phiên bản Excel/Sheets. Quy tắc phân cột, quote và CRLF dựa trên [RFC 4180 §2](https://www.rfc-editor.org/info/rfc4180/).

## 3. CsvSanitizer

File: `src/main/java/vn/ticketscenter/report/csv/CsvSanitizer.java`. Final utility, constructor private; một API:

```java
public static String escapeCsvCell(String value)
```

Trả về **một field CSV hoàn chỉnh** theo profile Excel này:

1. Null tương đương ô rỗng. String phải là Unicode UTF-16 hợp lệ và không chứa NUL U+0000; vi phạm ném IllegalArgumentException với message cố định không echo dữ liệu.
2. Kiểm prefix bằng code point, không `trim` hoặc sửa chuỗi gốc. Bỏ qua khi xét ký tự đầu hữu nghĩa: `Character.isWhitespace`, `Character.isSpaceChar`, category CONTROL và FORMAT. Nếu prefix có CONTROL/FORMAT, hoặc ký tự đầu hữu nghĩa là một trong `= + - @ ＝ ＋ － ＠`, ô cần trung hòa.
3. Với ô cần trung hòa, thêm đúng một tab trước **toàn bộ** giá trị gốc. Một tab có sẵn trong dữ liệu vẫn được giữ; đây không phải API idempotent hay API nhận CSV đã escape.
4. Mọi field, kể cả rỗng, đều được bọc bằng double quote. Mỗi double quote bên trong được nhân đôi. Dấu phẩy, chấm phẩy và newline bên trong giữ nguyên trong cùng field.

Chuỗi thường, tiếng Việt, emoji và khoảng trắng đầu/cuối được giữ nguyên, ngoại trừ việc serialize và tab bảo vệ trên ô nguy cơ. Trường số âm dạng String cũng được xử lý như nội dung có dấu `-` đầu; không có đường bỏ qua sanitizer theo kiểu dữ liệu do client chọn.

| Raw input — escape notation trong tài liệu | Serialized field — escape notation |
|---|---|
| null hoặc empty | `""` |
| `Đêm nhạc` | `"Đêm nhạc"` |
| `=1+1` | `"\t=1+1"` |
| `  =1+1 ` | `"\t  =1+1 "` |
| `\t=1+1` | `"\t\t=1+1"` |
| `a,b"c` | `"a,b""c"` |
| `a\nb` | `"a\nb"` |

## 4. StreamingCsvExporter

File: `src/main/java/vn/ticketscenter/report/csv/StreamingCsvExporter.java`. Final utility, constructor private:

```java
public static void write(OutputStream output,
                         List<String> headers,
                         Iterable<? extends List<String>> rows) throws IOException
```

- Output/headers/rows bắt buộc; headers ít nhất một cột, mọi header non-null. Các vi phạm và header không hợp lệ bị IllegalArgumentException **trước byte đầu tiên**. Header được snapshot/sanitize trước khi bắt đầu output để caller không đổi số cột trong khi ghi.
- Ghi đúng một BOM `EF BB BF` đầu mỗi lần export. Ký tự FEFF có sẵn trong raw cell vẫn là dữ liệu của cell, không tính là BOM preamble được exporter thêm. UTF-8 rõ ràng; cột cách nhau bởi comma, mọi record kết thúc CRLF, kể cả cuối file. Header cũng đi qua sanitizer.
- Ghi/flush header trước khi lấy iterator của rows, sau đó lấy đúng một iterator, ghi từng row bằng cùng sanitizer. Caller đưa logical cell chưa escape. Không collect rows, dựng CSV toàn bộ hoặc serialize qua PrintWriter.
- Row non-null và số cột bằng header; row sai bị IllegalArgumentException trước khi ghi row đó. Null cell hợp lệ và thành field rỗng. Validation muộn/nguồn lỗi có thể để lại file dở; không thể rollback bytes đã gửi.
- Flush khi hoàn tất; không cần flush từng row vì writer buffer được giới hạn và tự ghi khi đầy. Không close OutputStream của caller, kể cả khi lỗi. IOException của write/flush được truyền ra, không nuốt hoặc trả thành công. Lỗi iterator RuntimeException giữ nguyên; ngừng đọc nguồn khi ghi lỗi.
- Exporter không sở hữu DB cursor/transaction: caller phải đóng nguồn trong finally và kiểm quyền trước khi gọi. Bộ nhớ phụ thuộc header/row/cell đang xử lý và buffer hữu hạn, không phụ thuộc tổng số rows; một cell cực lớn vẫn cần tài nguyên theo kích thước cell đó.

## 5. Kiểm chứng

Unit tests gọi implementation thật; stream/iterator trong test chỉ đo thứ tự đọc/ghi, bytes và lỗi, không thay nghiệp vụ DB.

| Nhóm | Ca cần kiểm |
|---|---|
| Sanitizer | Null/empty; Unicode/emoji; giữ spaces; ASCII/full-width formula starters; prefix spaces/NBSP/tab/CR/LF/BOM/zero-width; quote/comma/semicolon/multiline; NUL/lone surrogate bị từ chối |
| Bytes | BOM một lần; UTF-8; header; comma; CRLF cuối; ô được sanitize; string tiền `9223372036854775807` và UTC `2026-10-09T00:00:00Z` giữ nguyên trong output |
| Contract | Header/arguments invalid không ghi bytes; header snapshot; empty rows; null cell; null row/wrong width; một iterator |
| Liveness/errors | 10.000 rows phát sinh lười, sink chỉ đếm bytes; kiểm tiến độ ghi sau mỗi 64 rows; output/header/final flush lỗi được truyền ra; iterator lỗi; không close stream |

Fixture Excel/Sheets chỉ dùng phép tính vô hại như `=1+1`, không DDE, lệnh hệ thống, link/exfiltration hoặc dữ liệu thật. Khi thực thi sẽ xuất fixture nhỏ có dấu, công thức và số lớn cho kiểm tra trực tiếp mở/lưu/mở lại bằng app thật nếu môi trường cho phép. Nếu chưa chạy app, evidence ghi chưa kiểm; RPT-03/RPT-04/RPT-05 full HTTP/app không được báo đạt từ unit tests. CSV bytes số lớn đúng không đảm bảo Excel không tự làm tròn khi nhận kiểu number; chính sách cột tiền/ID và app acceptance cần chốt trước expose báo cáo.

## 6. Điều kiện tích hợp/bàn giao

1. Owner chấp nhận profile Excel/tab thay literal dấu nháy đơn trong #32; consumers nhập CSV bằng chương trình phải biết tab là một phần của logical value.
2. Owner chốt schema/View/UDF và metric cohort/cashflow; cùng công thức, filter, timezone và phạm vi quyền cho UI/JSON/CSV.
3. Có DB test JDBC riêng và test Manager tổ chức A không xem/export dữ liệu B; adapter phân quyền trước khi gửi BOM/header.
4. Có HTTP test header Content-Type/Content-Disposition, client disconnect/partial export và việc đóng DB cursor đúng khi lỗi.
5. Chốt cache scope/filter/invalidation; app acceptance Excel/Sheets trên version ghi trong evidence.

Giữ #32 mở. CI chung hiện có test DB/SMTP thật; dùng nhóm unit an toàn cục bộ, không chạy bare `mvn test` để ghi PASS. Push/Draft PR là bước bàn giao riêng khi được yêu cầu; CI phải được cô lập và đạt gate cùng review đồng đội trước merge.
