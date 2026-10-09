# VUONG-05 Safe CSV Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tạo sanitizer và exporter CSV thuần Java cho phần độc lập của #32, ưu tiên Excel và được kiểm thử byte/stream/error.

**Architecture:** CsvSanitizer serialize từng logical cell theo profile Excel đã chọn. StreamingCsvExporter dùng sanitizer cho header/row, ghi vào stream mượn của caller; không nhận DB entity hoặc chịu trách nhiệm query/quyền.

**Tech Stack:** JDK 25, Maven 3.9.16, JUnit Jupiter 5.12.0 hiện có, java.io/java.nio.charset; không thêm dependency.

**Spec:** [2026-10-09-vuong-05-safe-csv-design.md](../specs/2026-10-09-vuong-05-safe-csv-design.md). Hai tài liệu lập theo yêu cầu lên kế hoạch; chưa bắt đầu implementation. Lựa chọn tab/Excel được người dùng xác nhận ngày 09/10/2026.

## Global Constraints

- Nhánh `feat/vuong/vuong-05-reports-csv-cache` từ `dev` tại `cfd77c60ce539f145a6af7749ba050561be6dd3f`; không lấy commit #29 làm dependency.
- Production/test chỉ trong `vn.ticketscenter.report.csv`; docs thuộc spec/plan/evidence. Giữ pom, model, identity, persistence, SQL và workflow.
- Mỗi field được quote; tab U+0009 được thêm trước raw value nguy cơ. Không trim, không nhận field đã escape, không thêm apostrophe.
- Null cell là rỗng; NUL/UTF-16 sai bị IllegalArgumentException không echo payload. Header/arguments invalid không ghi byte.
- BOM `EF BB BF` một lần, UTF-8, comma, CRLF cho mọi record. Không close borrowed output, truyền IOException/iterator error, không collect dataset.
- Tiền/thời gian là String do caller định dạng; không làm tròn hoặc suy diễn metric.
- #32 vẫn mở; unit output không thay HTTP/DB/multi-tenancy/app acceptance. CI DB/SMTP thật không được kích hoạt hoặc ghi PASS từ nhóm test chọn.

## Review Focus

1. Formula bị che bởi NBSP/zero-width/BOM hoặc full-width starter: Task 1 thêm fixture code point, vẫn giữ raw string sau tab bảo vệ.
2. Comma/quote/newline tách thêm ô để đặt formula ở ô mới: Task 1/2 so exact quoted bytes, không dùng split dòng/field làm oracle.
3. Writer buffer che lỗi final flush hoặc PrintWriter nuốt lỗi: Task 2 có output lỗi và flush lần hai lỗi; method phải ném IOException.
4. Exporter đọc hết cursor trước khi có data write: Task 2 có iterator 10.000 rows và kiểm bytes tiến triển mỗi 64 rows bằng sink không giữ dữ liệu.
5. Tiếng Việt/emoji bị thay hoặc số tiền lớn bị round: Task 1/2 dùng literal Unicode, long-limit String và UTC; tách app numeric parsing khỏi kiểm chứng bytes.

## File Structure

| File tạo | Trách nhiệm |
|---|---|
| `src/main/java/vn/ticketscenter/report/csv/CsvSanitizer.java` | Field serialization và profile Excel |
| `src/test/java/vn/ticketscenter/report/csv/CsvSanitizerTest.java` | Fixture value/escape/prefix/Unicode |
| `src/main/java/vn/ticketscenter/report/csv/StreamingCsvExporter.java` | Borrowed stream, BOM, header/rows và liveness |
| `src/test/java/vn/ticketscenter/report/csv/StreamingCsvExporterTest.java` | Bytes, iterator/sink đo tiến độ/lỗi; helper chỉ trong test |
| `docs/evidence/vuong/VUONG-05.md` | Kết quả thật, revision, contract change và integration gates |

## Chuẩn bị khi thực thi

- [ ] Dùng using-git-worktrees chuẩn bị checkout cách ly từ nhánh này; giữ worktree/WAR/preview #28/#29. Dùng executing-plans nếu Native được duyệt và ledger theo plan.
- [ ] Chọn JDK 25 và Maven trong terminal, không đổi môi trường máy toàn cục. Chạy nhóm regression an toàn 43 ca bên dưới trước khi viết test mới; expected exit 0, failures/errors/skips đều 0.

```powershell
$env:JAVA_HOME = 'C:\Users\Le Vuong\.jdks\openjdk-25'
$env:PATH = "$env:JAVA_HOME\bin;E:\apache-maven-3.9.16\bin;$env:PATH"
mvn -B '-Dtest=FeaturePackageStructureTest,AuthOtpServletsTest,IdentityWebFlowTest,UserPrincipalTest,SecurityContextTest,OtpCryptoHelperTest,AuthServiceTest#testRegisterInvalidEmail+testRegisterPasswordTooShort+testLoginNullOrBlankInputs+testCurrentUserDtoSanitization,EmailSenderTest#shouldValidateEmailMessageFields+fakeEmailSenderShouldRecordMessages+fakeEmailSenderShouldThrowWhenFailureSimulated' test
```

### Task 1: Serialize cell theo profile Excel

**Files:** Create `CsvSanitizer.java` và `CsvSanitizerTest.java` theo File Structure.

**Interfaces:** Consumes raw String, có thể null. Produces `public static String CsvSanitizer.escapeCsvCell(String value)`, utility final/constructor private; luôn trả quoted field hoàn chỉnh hoặc IllegalArgumentException theo spec.

- [ ] **Step 1: Viết test đỏ.** `quotesOrdinaryValues` có fixture null/empty/`Đêm nhạc 🎵`/`  Hà Nội  `, các kỳ vọng quoted literal. `neutralizesFormulaPrefixes` tham số hóa tám starter ASCII/full-width, thêm spaces, NBSP U+00A0, zero-width U+200B, BOM U+FEFF trước `=1+1`; expected quoted value có tab đầu và giữ toàn bộ input. `neutralizesControlPrefixes` dùng tab/CR/LF và SPACE + tab + text thường; expected tab bảo vệ + raw. `escapesSeparatorsQuotesAndMultiline` dùng `a,b"c`, `a;b`, `a\nb`, `a\rb`, `a\r\nb` và `=1+1";,=1+1`; literal kỳ vọng không có field mới. `rejectsInvalidUnicodeOrNul` gồm lone high/low surrogate và NUL đầu/giữa/cuối. Empty/spaces không bị thêm tab. Test names bắt guard/escape mất, không kiểm private structure.

```java
assertEquals("\"\"", CsvSanitizer.escapeCsvCell(null));
assertEquals("\"  Hà Nội  \"", CsvSanitizer.escapeCsvCell("  Hà Nội  "));
assertEquals("\"\t=1+1\"", CsvSanitizer.escapeCsvCell("=1+1"));
assertEquals("\"\t\u00A0=1+1\"", CsvSanitizer.escapeCsvCell("\u00A0=1+1"));
assertEquals("\"\t\t=1+1\"", CsvSanitizer.escapeCsvCell("\t=1+1"));
assertEquals("\"a,b\"\"c\"", CsvSanitizer.escapeCsvCell("a,b\"c"));
assertThrows(IllegalArgumentException.class, () -> CsvSanitizer.escapeCsvCell("a\u0000b"));
```

- [ ] **Step 2: Chạy** `mvn -B '-Dtest=CsvSanitizerTest' test`. Expected RED ở testCompile vì API chưa tồn tại; ghi đúng nguyên nhân, không gọi đây là assertion failure. Không viết production trước lần RED.
- [ ] **Step 3: Triển khai** signature trong đúng file. Kiểm Unicode/NUL; xét prefix theo code point/character categories ở spec §3, thêm tab nếu nguy cơ, nhân đôi quote, bọc mọi field. Không trim/NFKC, không echo dữ liệu lỗi.
- [ ] **Step 4: Chạy lại** cùng lệnh. Expected exit 0, tất cả fixture chạy, failures/errors/skips=0; đọc output thật.
- [ ] **Step 5: Commit** đúng sanitizer/test: `feat(report): encode spreadsheet CSV cells safely`; body Task-Id VUONG-05, Refs #32, Validation và Evidence theo quy ước.

### Task 2: Export tuần tự và kiểm chứng bàn giao

**Files:** Create `StreamingCsvExporter.java`, `StreamingCsvExporterTest.java`, `docs/evidence/vuong/VUONG-05.md`.

**Interfaces:** Consumes `CsvSanitizer.escapeCsvCell(String)` từ Task 1. Produces `public static void StreamingCsvExporter.write(OutputStream output, List<String> headers, Iterable<? extends List<String>> rows) throws IOException`; utility final/constructor private, không đóng stream caller.

- [ ] **Step 1: Viết test đỏ.** `writesUtf8BomHeaderAndExactRows` so byte[] với literal UTF-8 dưới đây; check BOM chỉ ở đầu file, CRLF cuối, Vietnamese/quote/comma/UTC/large integer không đổi. `sanitizesHeadersAndRows` header `=1+1`, cell NBSP + `=1+1`, comma/quote/newline không phá record. `writesHeaderForEmptyRows` expected BOM + quoted header + CRLF. `preservesNullCell` dùng Arrays.asList cho null, expected `""`.

```java
String expected = "\uFEFF\"eventTitle\",\"grossRevenue\",\"paidAt\"\r\n"
        + "\"Đêm \"\"Mùa Thu\"\", Hà Nội\",\"9223372036854775807\",\"2026-10-09T00:00:00Z\"\r\n";
assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), output.toByteArray());
```

  `rejectsArgumentsBeforeWriting` tham số hóa output/header/rows null, header empty/null cell/NUL/lone surrogate, kỳ vọng IllegalArgumentException và sink 0 bytes khi có output. `rejectsMalformedRows` null row và width nhỏ/lớn, không ghi cell của row lỗi. `snapshotsHeader` mutate header list khi rows.iterator() được gọi, vẫn ghi đúng header/số cột ban đầu. `usesOneIterator` nguồn ném nếu iterator() lần hai.

  `streamsTenThousandRowsWithoutMaterializing` headers `["n","name"]`, rows i=0..9999 chứa `[Integer.toString(i), "X".repeat(1024)]` sinh bằng iterator; counting sink không lưu output. Mỗi lần next ở i=64,128,… yêu cầu bytes > checkpoint rồi cập nhật checkpoint; catch collect/drain dataset trước khi write. Tổng expected **10,348,905 bytes**: BOM+header 15, mỗi row 1031+digits(i), tổng digits 38,890. Assert nextCalls=10000. Không đo heap/RSS để tuyên bố toàn bộ pipeline O(1).

  `propagatesOutputFailureAndStopsSource` sink fail BOM/header và fail ở data write, sentinel IOException được giữ, nguồn chưa bị đọc hết; data fixture 10.000 rows như trên. `propagatesFinalFlushFailure` sink flush lần đầu (header) thành công, lần hai ném sentinel; assertSame exception. `propagatesIteratorFailure` iterator.next() ném sentinel RuntimeException; không wrap thành success. Spy output của các ca success/failure có closeCalled=false; không thêm hooks vào production.

- [ ] **Step 2: Chạy** `mvn -B '-Dtest=CsvSanitizerTest,StreamingCsvExporterTest' test`. Expected RED do StreamingCsvExporter chưa tồn tại; phân biệt compile failure với fixture lỗi.
- [ ] **Step 3: Triển khai** signature đúng file. Snapshot/validate/sanitize header trước bytes, BOM và UTF-8 writer, comma/CRLF, flush header trước rows.iterator(), một vòng lặp ghi row, validate width/null trước row, flush cuối; IOException/RuntimeException truyền ra. Không try-with-resources đóng writer/borrowed stream, không PrintWriter, không collect rows. Nguồn DB/cursor do caller sở hữu.
- [ ] **Step 4: Chạy lại** cùng lệnh, expected exit 0, 0 failures/errors/skips. Sau đó chạy regression và package bên dưới; đọc output và báo số ca thật, không dự đoán số mới từ plan.

```powershell
mvn -B '-Dtest=CsvSanitizerTest,StreamingCsvExporterTest,FeaturePackageStructureTest,AuthOtpServletsTest,IdentityWebFlowTest,UserPrincipalTest,SecurityContextTest,OtpCryptoHelperTest,AuthServiceTest#testRegisterInvalidEmail+testRegisterPasswordTooShort+testLoginNullOrBlankInputs+testCurrentUserDtoSanitization,EmailSenderTest#shouldValidateEmailMessageFields+fakeEmailSenderShouldRecordMessages+fakeEmailSenderShouldThrowWhenFailureSimulated' test
mvn -B -DskipTests package
git diff --check
```

- [ ] **Step 5: Hồ sơ và fixture app.** Ghi red/green, testedRevision, test selection, lệnh/exit code và giới hạn trong VUONG-05.md. Tạo CSV nhỏ bằng implementation trong `target/` từ fixture vô hại ở spec §5; nếu có app thật, kiểm Excel/Sheets mở và lưu/mở lại, ghi version/locale/kết quả. Nếu không chạy app, ghi chưa kiểm và giữ RPT app acceptance mở; không thay bằng test byte. Test số lớn trên app cần phân biệt CSV bytes đúng với auto-number rounding.
- [ ] **Step 6: Commit** exporter/test/evidence: `feat(report): stream UTF-8 CSV with explicit IO failures`, footer task/ref/validation/evidence. Task hoàn tất local khi unit/regression/build đúng; #32 vẫn mở.

## Review và bàn giao cuối

- [ ] Với Native, dùng requesting-code-review cho **một reviewer độc lập toàn nhánh** theo approved spec/plan, gồm năm Review Focus; không gọi reviewer per task. Sửa finding quan trọng theo RED→GREEN, chạy lại regression/build bị ảnh hưởng, cập nhật evidence bằng kết quả thật.
- [ ] Giữ branch/commit để bàn giao; không push/merge hoặc đóng #32 trong phạm vi yêu cầu lên kế hoạch. Khi người dùng yêu cầu Draft PR, ghi rõ Excel/tab contract change, phần tích hợp thiếu và CI gate. Không kích hoạt pipeline SMTP/DB chung; không coi skip CI là gate đạt.

## Self-review và execution handoff

Spec §3 → Task 1; §4 → Task 2; §5 → fixtures/bytes/liveness/errors/app evidence; §6 → integration gates giữ mở. Hai task dùng cùng `escapeCsvCell` trả quoted field, exporter không quote lần hai. Tổng byte fixture 10.000 rows được tính độc lập từ literal, không gọi production để tạo oracle. Kế hoạch không thêm endpoint, thư viện hoặc fake repository.

Đề xuất **Native**: hai task nối tiếp, tự triển khai/test/commit trong phiên, reviewer độc lập cuối nhánh. Người dùng duyệt thiết kế và kế hoạch trước implementation; có thể chọn Native hoặc Subagent-driven. Không coi lựa chọn profile Excel là phê duyệt mã nguồn hoặc kết quả app acceptance.
