# VUONG-05 — CSV độc lập, bằng chứng thực thi

Issue [#32](https://github.com/devonxjz/TicketCenter/issues/32) vẫn mở. Người dùng duyệt thiết kế/kế hoạch ngày 09/10/2026 và chọn Native. Phạm vi hoàn tất local: `CsvSanitizer` và `StreamingCsvExporter` trong `report/csv`, test và tài liệu. Chưa triển khai endpoint, UI, SQL, quyền tổ chức hoặc Redis; chưa push/PR/merge.

## Revision và môi trường

- Base `origin/dev`: `cfd77c60ce539f145a6af7749ba050561be6dd3f`.
- Approved design/plan: `cd10a713b4e4693d4ee87da8f6f73f69ad6162d4`.
- Sanitizer: `a71afec492e03b59d1e92b32d17141b264f1d029`.
- TestedRevision Task 2: `24538c8ec64dc9ffbbd0ebb33522c729ddc3a7d6` (Task 2 thêm exporter/test lên `a71afec`). Task-done chạy lại nhóm 99 ca trên đúng commit này và đạt. Cả hai class production nằm trong WAR đã tạo từ cùng source. Commit bàn giao sau đó chỉ cập nhật tài liệu.
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

Reviewer độc lập fresh-context kiểm toàn range `cfd77c6..24538c8`, dùng model `gpt-6-astra`, không sửa file hoặc chạy build/tests. Reviewer đọc source, tests, spec/plan, evidence, persisted reports và kiểm hash fixture; cả năm Review Focus phù hợp. **Critical: 0; Important: 0; Minor: 0.** Verdict: phần utility được thông qua, không cần sửa code; whole #32 merge readiness chưa được xác lập. Executor đối chiếu lại và giữ kết luận này. Bàn giao tài liệu ngày 10/10/2026, không thêm code sau review.

Các hành vi reviewer không đánh giá được đã được executor quyết định giữ làm gate riêng theo phạm vi đã duyệt, không bỏ ngầm:

1. **Ruling:** Chưa xác nhận công thức trên Excel/Sheets sau mở/lưu/mở lại — app control được hỗ trợ chưa có; giữ app acceptance mở — nếu bỏ qua gate: ô nguy cơ có thể bị diễn giải thành công thức trên app đích.
2. **Ruling:** Chưa xác nhận delimiter/Unicode hiển thị theo locale — byte tests không thay app test; kiểm trên locale đích trước expose — nếu bỏ qua gate: sai cột hoặc hiển thị.
3. **Ruling:** Chưa xác nhận app giữ số lớn — utility giữ String/bytes, owner chốt import/column policy — nếu bỏ qua gate: app có thể làm tròn tiền/ID.
4. **Ruling:** Aggregation/format tiền/chuyển timezone thuộc adapter — utility không suy diễn nghiệp vụ — nếu adapter sai: báo cáo sai tổng/định dạng/thời điểm.
5. **Ruling:** HTTP headers, authorization/tenancy, disconnect thuộc tích hợp endpoint — chưa có endpoint trong scope — nếu bỏ qua gate: lộ dữ liệu hoặc xử lý export lỗi sai.
6. **Ruling:** Đóng cursor/transaction và bộ nhớ pipeline thuộc caller — utility chỉ chứng minh streaming của nó — nếu caller sai: rò tài nguyên hoặc tăng bộ nhớ.
7. **Ruling:** Cache scope/invalidation/consistency giữ chờ tích hợp — chưa có cache trong utility — nếu bỏ qua gate: dữ liệu cũ hoặc sai phạm vi.
8. **Ruling:** Full-suite/CI và hoàn tất #32 chưa được xác nhận — nhóm test an toàn không thay các gate này — nếu coi đã đạt: tích hợp chưa kiểm hoặc kích hoạt DB/SMTP chung.

Deferred minors: không có. Giữ nhánh local; thư mục chính được chuyển về nhánh này, worktree bảo toàn WAR, reports và fixture. Không push/PR/merge hoặc đóng issue trong phiên.

## Bàn giao Draft PR — 10/10/2026

Người dùng yêu cầu tiếp tục bước push/mở Draft PR vào `dev` sau khi phần utility hoàn tất. Giữ #32 mở; không merge hoặc chuyển PR sang ready-for-review khi các gate chưa đạt.

Đã fetch và merge `origin/dev` tại `8d5828a` (PR #39 của đồng đội) vào feature, không có conflict. TestedRevision mới: `208469448a75cab7f1ead95827fb025f50a4ccfe`. Không sửa code của đồng đội để đồng bộ; diff feature so với dev vẫn chỉ bảy file report CSV/spec/plan/evidence. Source CSV và test CSV không đổi so với revision đã review `24538c8`.

Chạy lại đúng lệnh nhóm an toàn trong mục TDD trên revision mới: **102 tests, 0 failures/errors/skips, exit 0** (56 CSV + 46 baseline; IdentityWebFlowTest tăng 16 → 19 do thay đổi dev). `mvn -B -DskipTests package` exit 0, WAR tạo lại thành công. `git diff --check` exit 0. Logs `target/vuong-05-pr-regression.log`, `target/vuong-05-pr-package.log` và reports giữ ở worktree CSV. Commit sau revision này chỉ cập nhật tài liệu.

Đích PR `dev` và tên `feat/vuong/vuong-05-reports-csv-cache` theo yêu cầu người dùng và convention của live #32/#34; tài liệu GIT-WORKFLOW dùng tên generic `develop`/`feature` nên không tự đổi nhánh đang được giao. Đồng bộ bằng merge, không rewrite/force-push.

Commit HEAD giữ `[skip ci]` để không kích hoạt pipeline push/pull_request đang chạy bare `mvn -B test` và có DB/SMTP chung. Đây là trạng thái **CI gate chưa đạt**, không phải CI xanh. [GitHub hướng dẫn skip workflows](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/skip-workflow-runs) nêu required checks có thể pending khi skip. Không đổi workflow hoặc branch protection. App acceptance vẫn chưa chạy.

Draft PR sẽ yêu cầu reviewer xác nhận TAB profile, raw-cell/quoted-field API, ownership/partial-output behavior. Merge gate vẫn cần app/contract/integration theo phạm vi ghép và CI được cô lập, chạy xanh cùng ít nhất một review phê duyệt.
