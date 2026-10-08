# Ngày 20 — Index, đóng gói Docker và diễn tập phục hồi

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ tư, 2026-10-14. **Ước lượng tạm của kế hoạch ban đầu:** 17 giờ công tổng của nhóm.

**Mục tiêu:** Đo đủ 15 index, chạy WAR trong container và kiểm tra SQL/Azure/khởi động lạnh trong khả năng tài khoản được cấp.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §8.4, §11, §12, §14.8/14.11. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D19 baseline/quyền; credential và quyền deploy nếu thực hiện online. Chuẩn bị local vẫn thực hiện được độc lập.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D20-T01 — Cài IX01–IX15 và đo trước/sau

**Phụ trách đề xuất:** B. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D20_01_performance_indexes.sql; database/benchmarks/queries.sql; docs/backend/index-benchmark.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** 15 index bổ sung ngoài PK theo SPEC §14.8; ghi key/include/filter chính xác, không tạo trùng chỉ để đủ số.

**Phụ thuộc của task:** D19-T03 baseline; không đo trên DB phục vụ demo.

**Cách thực hiện:**

- [ ] 1. Tạo IX01–IX15 trên đúng bảng/cột đã mapping; chạy fresh migrations theo thứ tự để xác nhận tất cả CREATE thành công.
- [ ] 2. Chạy lại baseline queries đúng data/params/count, cùng lần warm-up/repeat; lưu plans/reads/CPU/elapsed và write/storage overhead.
- [ ] 3. Nếu plan không dùng hoặc lợi ích chưa chứng minh, phân tích selectivity/coverage/query rồi điều chỉnh index và đo lại; không force INDEX để làm đẹp.
- [ ] 4. Kiểm query kết quả không đổi; index hiệu năng không thay unique constraints hoặc khóa transaction.
- [ ] 5. Nếu phải gộp/thay danh mục, ghi lý do đo được và cập nhật traceability/SPEC được chủ dự án thống nhất; không tự hạ tổng cam kết.

**Kiểm chứng bắt buộc:**

- [ ] Mỗi IX có before/after actual plan, logical reads, timing và giải thích.
- [ ] Không tuyên bố % cải thiện khi chưa có kết quả đo.

**Điều kiện hoàn thành:** Có 15 index có minh chứng và script dựng mới. Ghi case và kết quả trong `docs/evidence/day-20.md`.

## D20-T02 — Docker, cấu hình và kiểm tra Azure/online

**Phụ trách đề xuất:** A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** Dockerfile; .dockerignore; docs/backend/deployment.md; JAVA/controller/ReadinessServlet.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** WAR local/online cùng binary, secret qua môi trường; SQL local và Azure paths phân biệt.

**Phụ thuộc của task:** D01 tooling và D19 security; deploy online cần quyền/credential, local image làm độc lập.

**Cách thực hiện:**

- [ ] 1. Xác minh image/tag JDK25/Tomcat11 thực tồn tại và tương thích trước pin; build image, user/permission runtime tối thiểu khả thi.
- [ ] 2. Health liveness nhẹ, readiness SELECT1 timeout ngắn, startup fail rõ nếu cấu hình sai; secret không nằm image/build args/log.
- [ ] 3. Đặt heap/pool/session/thread budget từ SPEC làm khởi điểm, đo RSS thay vì coi cấu hình là bảo đảm đủ 512MB.
- [ ] 4. Nếu đã được cấp quyền dịch vụ: áp migrations Azure bằng account riêng, test mapping/locking/permissions và deploy HTTPS; xác minh free quota/auto-pause không tính phí lúc triển khai.
- [ ] 5. Nếu chưa có quyền/tài khoản: hoàn thiện image/runbook/local HTTPS thử được, ghi online/Azure blocked riêng. Không gọi local pass là online pass.

**Kiểm chứng bắt buộc:**

- [ ] docker build và container health/readiness pass; WAR không kèm container/secret.
- [ ] Migrations/query trên Azure được xác nhận riêng nếu có môi trường, không suy từ local.

**Điều kiện hoàn thành:** Backend có artifact chạy độc lập và cấu hình deploy cụ thể. Ghi case và kết quả trong `docs/evidence/day-20.md`.

## D20-T03 — Load giới hạn và restart có giao dịch đang chờ

**Phụ trách đề xuất:** C. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/recovery/DeploymentRecoveryIT.java; docs/backend/operations.md; docs/evidence/day-20.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Kịch bản nhỏ có đo RSS/pool/latency/error; không cam kết throughput trước số đo.

**Phụ thuộc của task:** D20-T02 container và D12/D15/D16 recovery; load lớn đối chiếu indexes D20-T01.

**Cách thực hiện:**

- [ ] 1. Chạy nhóm buyer tranh ghế/coupon, job pending và reports/export đồng thời với tải tăng có giới hạn; ghi số concurrency thực tế.
- [ ] 2. Kiểm standing/seat không oversell, không vượt coupon, pool không rò và query report không tải toàn bộ dữ liệu vào heap.
- [ ] 3. Restart container khi có Hold ACTIVE, Payment UNKNOWN, refund pending, cancellation dở và email waiting.
- [ ] 4. Cho DB timeout/unavailable: response503/UNKNOWN đúng, không tạo lần thu mới; phục hồi rồi đối chiếu bằng SP/job.
- [ ] 5. Xác minh ảnh ngoài container còn tồn tại, session RAM có thể mất nhưng dữ liệu nghiệp vụ được khôi phục; đo cold start.

**Kiểm chứng bắt buộc:**

- [ ] Sau recovery tổng tiền/kho giữ bất biến và không duplicate effect.
- [ ] Có RSS/thời gian thực đo cùng cấu hình, không chỉ đề xuất heap.

**Điều kiện hoàn thành:** Vận hành bản nhỏ được kiểm tra bằng tình huống cụ thể. Ghi case và kết quả trong `docs/evidence/day-20.md`.

## D20-T04 — Sửa lỗi nghiệm thu và khóa bản bàn giao

**Phụ trách đề xuất:** A+B+C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day20IT.java; docs/backend/known-issues.md; docs/backend/database-runbook.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Chỉ sửa lỗi được tái hiện từ kiểm thử; giữ log test regression và SQL inventory mới.

**Phụ thuộc của task:** Kết quả D20-T01/T02/T03 và gap D19; sửa theo test tái hiện.

**Cách thực hiện:**

- [ ] 1. Ưu tiên data loss/quyền/tiền/concurrency trước vấn đề latency; mỗi lỗi có failing test và một root-cause fix.
- [ ] 2. Đọc mọi caller shared function/SP thay đổi; chạy lại nhóm bị ảnh hưởng thay vì chỉ case báo lỗi.
- [ ] 3. Dựng một DB mới không dùng dữ liệu dev, chạy migrations/seed/grants theo runbook từ đầu.
- [ ] 4. Kiểm inventory đủ 20 C/10 V/17 SP/10 F/10 TR/15 IX và mapping 15 Model; SQL usage có trace.
- [ ] 5. Khóa danh sách artifact/version/schema và blockers thật; chuẩn bị bàn giao một máy sạch ngày 21, không đánh done cho lỗi chưa giải quyết.

**Kiểm chứng bắt buộc:**

- [ ] Day20IT/nhóm bị ảnh hưởng pass; fresh DB build thành công.
- [ ] Known issues ghi impact và bằng chứng thiếu, không viết 'mọi thứ hoàn tất' nếu còn blocked.

**Điều kiện hoàn thành:** Bản candidate sẵn cho nghiệm thu cuối, dự phòng lỗi có giới hạn. Ghi case và kết quả trong `docs/evidence/day-20.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day20IT` và `database/tests/day-20.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day20IT verify
```
