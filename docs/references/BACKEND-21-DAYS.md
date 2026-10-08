> Lịch backend cũ giữ để truy vết. Phân công toàn dự án hiện tại dùng [README](README.md), năm file thành viên và [TEAM-CONTRACT](TEAM-CONTRACT.md). Những câu giới hạn frontend/hồ sơ dưới đây chỉ mô tả phạm vi lịch cũ, không áp dụng bộ nhiệm vụ mới.

> Quản lý Git hiện hành theo [GIT-WORKFLOW](GIT-WORKFLOW.md): feature dùng chung cho tính năng/sửa lỗi, mặc định từ develop → PR develop → PR nghiệm thu main → tag; không có nhánh release hoặc loại nhánh sửa khẩn cấp riêng. Ngoại lệ sửa bản main đã bàn giao theo GIT-WORKFLOW §7. Lịch/ownership A/B/C trong file này chỉ để truy vết; nhiệm vụ chính theo năm file thành viên.

# Kế hoạch backend TicketsCenter — 21 ngày

> Dành cho người thực hiện: đọc SPEC, quy ước và task của ngày; triển khai từng task có kiểm chứng. Khi dùng agent có thể dùng skill superpowers:executing-plans để thực hiện kế hoạch. Checklist là công việc tương lai, không phải xác nhận phần mềm đã có.

**Mục tiêu kế hoạch:** hoàn thiện backend theo [spec.md](../../spec.md) và [diagram chuẩn](../classdiagram/diagram.md) trong 21 ngày dự kiến, gồm cuối tuần. Đây là kế hoạch tương lai; không suy ra tiến độ thực tế từ ngày lịch hoặc nguyên mẫu giao diện.

**Kiến trúc:** monolith Servlet → Service → Model/Repository → SQL Server. JPA quản lý persistence/transaction, SP01–SP17 sở hữu đường ghi nghiệp vụ tương ứng; integration và worker thực hiện I/O ngoài transaction.

**Công nghệ:** JDK 25; Tomcat 11.0.25/Servlet 6.1; Maven WAR; JPA/Hibernate; SQL Server local, Azure SQL cho online; VNPAY Sandbox; hoàn/chi trả mô phỏng.

**Nguồn yêu cầu:** [spec.md](../../spec.md), đặc biệt §6, §8, §12 và §14. Yêu cầu hiện tại của chủ dự án loại frontend khỏi lịch này. Những đường dẫn code ghi trong task là nơi **sẽ tạo/cập nhật khi thực hiện**, không phải code đã tồn tại.

## 1. Lịch và năng lực thực hiện

- **Lịch giả định cũ: 25/09/2026–15/10/2026**, lập ngày 24/09/2026. Giữ để truy vết 84 task và phụ thuộc; chưa tái ước lượng sau cập nhật mô hình ngày 05/10/2026. Khi nhận việc phải chốt lại ngày bắt đầu/năng lực thực tế; không coi ngày đã qua là đã hoàn thành.
- **84 task, 4 task/ngày, tổng 351 giờ công ước lượng tạm theo kế hoạch cũ**, bao gồm code, kiểm chứng và tài liệu trong task. Giờ công là công sức tổng, không phải 351 giờ của từng thành viên.
- Phương án cơ sở: **3 người có thể làm khoảng 7 giờ/người/ngày**, có kỹ năng Java/SQL Server, tổng năng lực 441 giờ trong 21 ngày. Dành khoảng 70 giờ cho lỗi tích hợp/chờ phối hợp trong phần năng lực còn lại. Đây là lịch tập trung, có task cả thứ bảy/chủ nhật theo yêu cầu.
- Một người làm 7 giờ/ngày có khoảng 147 giờ/21 ngày, thấp hơn nhiều so với 351 giờ ước lượng; không thể bảo đảm đủ backend trong 3 tuần theo năng lực đó. Phải bổ sung người hoặc kéo lịch; không âm thầm cắt nghiệp vụ. Ước lượng cần hiệu chỉnh sau ngày 1 và mốc tuần 1/tuần 2.
- A: Java/HTTP/Service; B: SQL/transaction; C: kiểm thử/tích hợp/tài liệu. Đây là giả định chuyên môn của kế hoạch backend cũ, chưa phải phân công nhân sự thực tế và không thay bảng phân công 5 người của project. Đây là chuyên môn chính để giao việc, không phải ba làn công việc bất biến. Task A+B có số giờ tổng, cần chia rõ phần SQL/Java cho người nhận.
- Một người nhận chính cho mỗi task; người kiểm tra có thể khác. Cùng ngày không có nghĩa là mọi task làm đồng thời: SQL contract có thể chuẩn bị song song với Java/tests, nhưng nghiệm thu phải chờ phụ thuộc thực sự. Ngày7/11/17 nặng, chuyển người có kỹ năng SQL sang hỗ trợ và dùng dự phòng; không đợi dồn lỗi sang ngày 21.

## 2. Phạm vi sẽ hoàn thành

Backend tài khoản/session/OTP; quyền người dùng và database; tổ chức/thành viên; chính sách phí; sự kiện/khu/ghế/upload ảnh; tìm kiếm; giữ vé/đơn/coupon; VNPAY Sandbox; phát hành/ảnh QR; check-in; yêu cầu/duyệt/hoàn/bù trừ; hủy Event theo lô; đối soát/chi trả mô phỏng; báo cáo/CSV/audit; worker/retry/recovery; schema/migrations/seed; SQL benchmarks; đóng gói/deploy và tài liệu API/runbook.

Giữ **15 class nghiệp vụ**, **20 nhóm constraint**, **10 View**, **17 SP**, **10 Function**, **10 Trigger**, **15 index bổ sung**, **17 transaction nghiệp vụ**, **4 Role/Login** theo SPEC. Các đối tượng SQL phải có caller thật từ API/job, không chỉ tồn tại trong SSMS.

**Ngoài lịch này:** JSP/HTML/CSS/Bootstrap, thiết kế 24 màn hình, browser camera UI và kiểm thử responsive/accessibility giao diện; báo cáo môn học 50–100 trang, slide/bảo vệ. API phục vụ 24 màn hình vẫn có đủ trong [bản đồ API](API-MAP.md). Do đó hoàn thành lịch backend không đồng nghĩa đã nghiệm thu toàn project môn học.

## 3. Tình trạng repo khi lập kế hoạch và việc cần giải quyết sớm

Quan sát lại ngày 06/10/2026: nguồn chuẩn là `spec.md` và `docs/classdiagram/diagram.md`, cả hai đã có trong repo. `pom.xml` đã có khai báo Java 25 nhưng chưa cấu hình WAR/dependency backend. Các thư mục `src` đã có, chưa có mã Java/JSP/SQL triển khai nghiệp vụ; `database/` vẫn là đường dẫn dự kiến. Nguyên mẫu frontend là hiện vật riêng để xem giao diện, chưa chứng minh backend kết nối SQL Server. Checklist và giờ công dưới đây chưa có bằng chứng thực hiện; không dùng lịch cũ để đánh dấu hoàn thành.

| Điều kiện còn thiếu/chưa xác minh | Xử lý ở task | Ảnh hưởng nếu chưa có |
|---|---|---|
| Diagram chuẩn đã có; mapping triển khai chưa được nghiệm thu | D01-T01 kiểm kê 15 class; D02/D03 đối chiếu ERD/JPA | Phải có bằng chứng mapping/hành vi, không chỉ đếm tên class |
| Phiên bản/dependency Java, JSON, pool, test, QR chưa chốt | D01-T02 kiểm tương thích và xin chủ dự án duyệt trước thêm | Build/implementation phụ thuộc thư viện bị chặn tương ứng |
| SQL Server và quyền tạo DB test chưa xác minh | D01-T03 | Integration/concurrency cần SQL Server thật; mock không thay bằng chứng |
| Email sender và storage chưa chọn | D05-T03, đăng ký nhu cầu từ D01-T04 | OTP/mail/upload thật chưa nghiệm thu; vẫn test được logic qua adapter kiểm thử |
| VNPAY credential/callback HTTPS | D01-T04, D10-T04 | Signature/transaction tests chạy được; sandbox thực giữ blocked |
| Azure/Render account, quota và quyền publish | D01-T04 kiểm nhu cầu; D20-T02 thực hiện nếu được cấp | Docker/local vẫn hoàn tất; không tuyên bố online đã chạy |

Không yêu cầu người nhận đoán phần thiếu. Ghi tên người xử lý và trạng thái ngay khi nhận task. Khi blocker kéo dài qua mốc phụ thuộc, báo tác động lịch, tiếp tục task độc lập và giữ phần bị chặn chưa đạt.

## 4. Đọc task và phân việc

1. Đọc [quy ước/hợp đồng](CONVENTIONS.md), [bản đồ API](API-MAP.md) và [truy vết SPEC](COVERAGE.md).
2. Mở file ngày; xem mục tiêu, nguồn SPEC và phụ thuộc trước khi nhận một task.
3. Mỗi task có mã Dxx-Txx, phụ trách đề xuất, giờ công, file dự kiến, hợp đồng vào/ra, checklist cách làm, ca kiểm chứng và điều kiện hoàn thành.
4. Ghi người nhận/kiểm tra vào task hoặc công cụ nhóm. Kết quả lưu ở `docs/evidence/day-NN.md` với command, case, commit và log đã lọc.
5. Với logic/quyền/tiền/parsing: test đỏ trước, cài đặt, test xanh; không tạo framework/abstraction chỉ để đủ tầng. Ponytail được áp dụng bằng cách dùng các Service/Repository theo nhóm, chọn chia sẻ ảnh QR và tái sử dụng SP chung.
6. Cuối ngày chạy test phù hợp, rà grant/caller mới, cập nhật blocker; chỉ tick khi đã kiểm chứng. Cuối tuần nghiệm thu mốc, không dựa vào số checkbox đã tích.

## 5. Lịch chi tiết

### Tuần 1 — Nền tảng, danh tính và sự kiện (115 giờ công)

| Ngày | Lịch | Công việc chính | Giờ công | Mã task |
|---|---|---|---:|---|
| [D01](week-1/day-01-nen-tang-va-hop-dong.md) | 2026-09-25 | Chốt nền tảng, mô hình và hợp đồng backend | 16 | D01-T01…T04 |
| [D02](week-1/day-02-schema-va-rang-buoc.md) | 2026-09-26 | Thiết kế và dựng toàn bộ schema SQL Server | 17 | D02-T01…T04 |
| [D03](week-1/day-03-model-jpa-transaction.md) | 2026-09-27 | Model nền, JPA và transaction theo principal | 18 | D03-T01…T04 |
| [D04](week-1/day-04-tai-khoan-session-phan-quyen.md) | 2026-09-28 | Đăng ký, đăng nhập, phiên và bảo vệ HTTP | 15 | D04-T01…T04 |
| [D05](week-1/day-05-otp-email-khoi-phuc-mat-khau.md) | 2026-09-29 | OTP email, đặt lại mật khẩu và nhà cung cấp tích hợp | 15 | D05-T01…T04 |
| [D06](week-1/day-06-to-chuc-thanh-vien-hoa-hong.md) | 2026-09-30 | Tổ chức, thành viên và chính sách hoa hồng | 16 | D06-T01…T04 |
| [D07](week-1/day-07-su-kien-khu-ghe-anh.md) | 2026-10-01 | Sự kiện, khu/ghế, ảnh bìa và công khai | 18 | D07-T01…T04 |

### Tuần 2 — Bán vé, thanh toán và check-in (117 giờ công)

| Ngày | Lịch | Công việc chính | Giờ công | Mã task |
|---|---|---|---:|---|
| [D08](week-2/day-08-giu-ve-va-giai-phong.md) | 2026-10-02 | Giữ vé nguyên tử và giải phóng khi hủy/hết hạn | 17 | D08-T01…T04 |
| [D09](week-2/day-09-don-hang-coupon.md) | 2026-10-03 | Tạo đơn từ Hold và quản lý coupon | 17 | D09-T01…T04 |
| [D10](week-2/day-10-vnpay-khoi-tao-xac-thuc.md) | 2026-10-04 | Khởi tạo VNPAY và xác thực kết quả cổng | 16 | D10-T01…T04 |
| [D11](week-2/day-11-ghi-nhan-thanh-toan-phat-hanh-ve.md) | 2026-10-05 | Ghi nhận thu tiền, phát hành vé và QR | 19 | D11-T01…T04 |
| [D12](week-2/day-12-outbox-worker-doi-soat-thanh-toan-email.md) | 2026-10-06 | Worker, đối chiếu thanh toán và email bền vững | 15 | D12-T01…T04 |
| [D13](week-2/day-13-check-in-qr.md) | 2026-10-07 | Check-in vé và lịch sử quét theo tổ chức | 15 | D13-T01…T04 |
| [D14](week-2/day-14-yeu-cau-va-duyet-hoan.md) | 2026-10-08 | Yêu cầu hoàn vé và quyết định của admin | 18 | D14-T01…T04 |

### Tuần 3 — Hoàn tiền, tài chính và nghiệm thu (119 giờ công)

| Ngày | Lịch | Công việc chính | Giờ công | Mã task |
|---|---|---|---:|---|
| [D15](week-3/day-15-hoan-tien-va-bu-tru.md) | 2026-10-09 | Hoàn tiền mô phỏng, bù trừ và xử lý lần thử | 16 | D15-T01…T04 |
| [D16](week-3/day-16-huy-su-kien-theo-lo.md) | 2026-10-10 | Hủy sự kiện, hoàn tự động và tiếp tục sau restart | 18 | D16-T01…T04 |
| [D17](week-3/day-17-doi-soat-va-chi-tra.md) | 2026-10-11 | Đối soát, đóng băng số liệu và chi trả mô phỏng | 20 | D17-T01…T04 |
| [D18](week-3/day-18-bao-cao-csv-audit.md) | 2026-10-12 | Báo cáo, CSV và nhật ký quản trị | 16 | D18-T01…T04 |
| [D19](week-3/day-19-bao-mat-db-va-benchmark.md) | 2026-10-13 | Rà quyền toàn hệ thống và chuẩn bị benchmark index | 16 | D19-T01…T04 |
| [D20](week-3/day-20-hieu-nang-docker-azure-khoi-phuc.md) | 2026-10-14 | Index, đóng gói Docker và diễn tập phục hồi | 17 | D20-T01…T04 |
| [D21](week-3/day-21-nghiem-thu-va-ban-giao.md) | 2026-10-15 | Nghiệm thu backend toàn luồng và bàn giao tái lập | 16 | D21-T01…T04 |

## 6. Phụ thuộc và mốc kiểm tra

```text
D01 hợp đồng/môi trường → D02 schema → D03 JPA/transaction/principal
→ D04 auth → D05 OTP/tích hợp → D06 tổ chức → D07 sự kiện
→ D08 Hold → D09 Order/coupon → D10 Payment → D11 phát hành
→ D12 worker → D13 check-in → D14 request/duyệt hoàn
→ D15 kết quả hoàn → D16 hủy Event → D17 đối soát/chi trả
→ D18 báo cáo → D19 quyền/baseline → D20 benchmark/deploy → D21 nghiệm thu
```

- **Cuối ngày 7:** WAR+SQL thật; account verified; organization/member; Event có ảnh/khu/ghế được publish và tìm kiếm; quyền nền được test.
- **Cuối ngày 14:** giữ/mua/nhận vé/check-in, xử lý payment lặp/muộn, job recovery và request/duyệt refund. Refund có tiền chưa gọi xử lý thành công đến ngày 15.
- **Cuối ngày 18:** đủ nghiệp vụ backend và V/SP/F/TR, đã có sử dụng thật; còn hardening/index/deploy/final validation.
- **Cuối ngày 21:** kiểm chứng đầy đủ backend trên bản candidate, tài liệu dựng mới và trạng thái PASS/FAIL/BLOCKED minh bạch. Online chỉ PASS khi đã thử trên môi trường thật.

Các mốc là điều kiện kiểm tra, không có quyền bỏ task khi hết ngày. Nếu cuối ngày 7 chưa dựng/kiểm được SQL transaction, lịch tuần 2 phải cập nhật vì toàn bộ tiền/kho phụ thuộc nó.

## 7. Ràng buộc và tiêu chí đóng toàn kế hoạch

- Không thêm/nâng dependency khi chưa được chủ dự án đồng ý; không tạo external resource, chi phí hoặc publish nếu chưa được cấp quyền.
- Không destructive reset/drop/delete dữ liệu dùng chung khi chưa có xác nhận chính xác target. Test dùng DB riêng; kế hoạch không cấp phép xóa database hiện có.
- Model và schema bám diagram, một chủ sở hữu đường ghi/SP, một transaction owner; kiểm actor/phạm vi mỗi thao tác và không chạy runtime bằng db_owner/sysadmin.
- Không log/commit secret, password, OTP, raw QR hoặc cookie; endpoint payment/refund/payout không tin success do browser gửi.
- Build WAR, unit và integration SQL Server thật, concurrency/rollback, quyền, E2E, recovery và benchmark phải có evidence; test bị skip không được ghi đạt.
- [COVERAGE.md](COVERAGE.md) không còn requirement backend thiếu task/caller/evidence; toàn bộ phương thức diagram đã đối chiếu. Frontend/hồ sơ môn học giữ ngoài phạm vi đúng yêu cầu.
- API/runbook được người khác dùng để dựng từ DB mới, gửi request và kiểm kết quả. File task là hướng dẫn, không tự chứng minh backend đã tồn tại.

## 8. Các điểm review dễ sót

| Rủi ro | Task xử lý/test |
|---|---|
| Commit thành công nhưng response mất, gọi lại nhân giao dịch | D08-T03/T04, D10-T01, D11-T02/T04, D15-T04, D17-T04 |
| Hết hạn đúng mốc, sai timezone của cổng/báo cáo | D05-T04, D08-T04, D10-T02, D13-T01, D18-T01 |
| Principal/actor bị rò qua pooled connection, đổi quyền giữa phiên | D03-T03, D04-T02/T03, D13-T03, D19-T01/T04 |
| Snapshot và tổng tiền bị thay sau thanh toán/xác nhận | D09-T04, D11-T01/T03, D17-T02/T04 |
| Job chết giữa effect/ack hoặc giữa các Order bị hủy | D12-T01/T04, D15-T04, D16-T03/T04, D20-T03 |
