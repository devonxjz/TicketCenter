# Phân công TicketsCenter cho năm thành viên

Ngày cập nhật: 06/10/2026. Đây là kế hoạch triển khai và bàn giao tương lai, không xác nhận phần mềm đã hoàn thành. Năm phiên soạn tạo năm bản nhiệm vụ bao phủ giao diện, backend, SQL, kiểm thử và phần hồ sơ của mỗi người. Ảnh/sơ đồ thiếu giữ để tác giả cập nhật.

## 1. Năm bản nhiệm vụ

| Thành viên | File | Task | Phạm vi chính |
|---|---|---|---|
| Khánh | [khanh.md](khanh.md) | KHANH-01…15 | User/auth/OTP/session, nền WAR/JPA/HTTP/layout/mail; UI03/24 |
| Đông | [dong.md](dong.md) | DONG-01…13 | Organization/membership/Event/Zone/Seat/category/storage; UI01/02/09–13 |
| Liêm | [liem.md](liem.md) | LIEM-01…16 | Hold/Order/Payment/Coupon/Ticket, VNPAY/QR, outbox core; UI04–07/14 |
| Thái | [thai.md](thai.md) | THAI-01…15 | Refund/check-in/cancellation/recovery; UI08/15/16 và Service cho UI21 |
| Vương | [vuong.md](vuong.md) | VUONG-01…17 | CommissionRule/Settlement/reports/audit/Admin, SQL quyền/CI/benchmark/đóng gói; UI17–23 |

**76 task cấp thành viên**, không phải 76 commit bắt buộc: task lớn chia thành các đơn vị kiểm chứng/commit/PR. Cùng với 15 Model/24 UI, phạm vi SQL giữ C20/V10/SP17/F10/TR10/IX15/TX17/R4. Không giảm danh mục để vừa lịch, không suy ra điểm hoặc trạng thái nghiệm thu từ số lượng.

Mỗi bản có mục tiêu, nguồn spec/diagram, file sở hữu, contracts DTO/Service/API, phụ thuộc M0–M4, ví dụ, negative/boundary/race/rollback/recovery, test commands/evidence/reviewer và bảng tên nhánh/subject commit theo từng task.

## 2. Tài liệu chung phải đọc

1. [spec.md](../../spec.md) và [diagram.md](../classdiagram/diagram.md): nguồn nghiệp vụ, thuộc tính/phương thức/quan hệ.
2. [TEAM-CONTRACT](TEAM-CONTRACT.md): ownership Model/Servlet/SQL, types/DTO/Service/SPI, migrations/fixture/phase và cách ghép.
3. [GIT-WORKFLOW](GIT-WORKFLOW.md): GitFlow giản lược main/develop/feature, nhánh task chung cho tính năng/sửa lỗi, không có nhánh release hoặc loại nhánh sửa khẩn cấp riêng; Conventional Commits, Issue/PR/review/tag/evidence.
4. [CONVENTIONS](CONVENTIONS.md): format code, HTTP, transaction, secret và kiểm chứng.
5. [API-MAP](API-MAP.md), [COVERAGE](COVERAGE.md): route/quyền/màn hình và truy vết miền/SQL/ca nghiệm thu.

Thứ tự thẩm quyền: spec/diagram → TEAM-CONTRACT và quyết định triển khai đã ghi → file thành viên. Khi bất đồng, xác định chủ miền và cập nhật nguồn/caller/tests; không tự đổi nghiệp vụ. Quy trình Git bổ sung quản lý, không thay nghiệp vụ.

## 3. Thứ tự ghép và tiêu chí mở mốc

| Mốc | Bàn giao | Điều kiện |
|---|---|---|
| M0 | Build/types/Model/DTO/schema/manifest/principals/worker SPI và layout nền | Contract compile, WAR health, DB connection; stubs ghi chưa triển khai, không success giả |
| M1 | Auth/verified buyer, approve org+fee+manager, publish Event/kho | Tạo qua API thật, UI cơ bản, quyền/membership hiện tại |
| M2 | Hold/order/coupon/payment/free/ticket/QR/check-in | Race/rollback/replay kiểm SQL Server thật; Sandbox tách mock |
| M3 | Refund/cancel/reconcile/settlement/payout/reports/CSV/audit, đủ24UI | Zero/UNKNOWN/restart, blockers/frozen snapshot, owner/org/financial invariants |
| M4 | Fresh-clone/WAR/local/Docker/restore/benchmark/hồ sơ/demo | Evidence/acceptance matrix gắn commit, PASS/FAIL/BLOCKED đúng thực tế |

Không đợi cả file của một người hoàn tất mới bắt đầu người khác. CommissionRule Vương/Refund Thái cung cấp từ M0, Liêm SP09 dùng schema sớm; worker/đối soát hoàn thiện sau thu/hoàn. Khánh shared types/model/interfaces nằm ở nhiều task M0; chỉ phụ thuộc artifact cần dùng.

Chưa có ngày bắt đầu/deadline và năng lực thực tế được nhóm chốt; không dùng ngày lịch cũ làm hạn mới. Bản nhiệm vụ không tự chứng minh có code/JSP/SQL/build/test profiles.

## 4. Quản lý Git và đóng nhiệm vụ

Nhánh feature dùng cho cả tính năng và sửa lỗi, mặc định rẽ từ develop, PR về develop: `feature/<ten>/<task-id>-<chuc-nang>`, ví dụ `feature/thai/thai-10-refund-result`; commit `feat(fulfillment): apply verified refund results atomically` có footer Task-Id/evidence. Ngoại lệ sửa bản main đã bàn giao khi develop còn việc chưa nghiệm thu: cùng loại nhánh feature từ main, PR main rồi PR đồng bộ main → develop theo GIT-WORKFLOW §7. Mỗi đơn vị đã kiểm commit ngay; PR đi qua reviewer/người nhận đầu ra; tiền/quyền/transaction cần hai người review chuyên môn.

Vương ghép contracts/migrations/CI/runbook, không ghi đè mã người khác. Develop tích hợp theo M0–M4; main nhận bản nghiệm thu qua PR develop → main với hai reviewer/checks, tag sau kiểm đúng SHA main, không có nhánh release riêng; thay đổi shared file do chủ file phối hợp. Không commit secrets/simulator state hoặc dùng `git add .` với workspace có thay đổi người khác. Quy trình chi tiết và cấu hình GitHub tương lai trong GIT-WORKFLOW; hiện không tự push/merge/deploy.

Task đạt khi code/SQL/UI đúng spec, tests phù hợp chạy thật, caller/dependency đã ghép và evidence/review đủ. Commit chưa thay nghiệm thu; thiếu DB/provider/browser ghi BLOCKED theo từng phần.

## 5. Lịch backend cũ và hồ sơ môn học

[Lịch backend 21 ngày](BACKEND-21-DAYS.md) giữ 84 task/351 giờ ước lượng, giả định 25/09–15/10/2026 và A/B/C để truy vết; đó là kế hoạch backend riêng, chưa tái ước lượng theo năm người/full-stack. Các file week-1/week-2/week-3 giữ nguyên Task DNN-TNN. Không cộng 84+76 thành khối lượng mới hoặc dùng lịch cũ thay ownership thành viên.

Hồ sơ DBMS theo spec §14.12: sáu chương/50–100 trang nội dung, slide<=15, demo10–15 phút/Q&A5–10 phút, nộp trước24 giờ theo hạn được xác nhận. [Kế hoạch báo cáo](../baocao/KE-HOACH-BAO-CAO-DBMS.md). Vương tổng hợp, từng người cung cấp phần miền/evidence. Giữ năm thành viên thực tế và xác nhận quy mô với giảng viên; không tự đổi danh sách.

## 6. Khi giao cho AI trên GitHub

```text
Đọc spec.md, docs/classdiagram/diagram.md, docs/tasks/TEAM-CONTRACT.md,
docs/tasks/GIT-WORKFLOW.md và docs/tasks/<ten>.md trong cùng repository.
Kiểm tra trạng thái repo và task/dependency thực; chỉ triển khai phần được giao.
Giữ ownership, DTO/Service/Servlet, SQL manifest và format prototype; không vẽ ảnh thiếu.
Làm từng đơn vị có kiểm chứng, lưu evidence; tính năng và sửa lỗi dùng chung
feature, mặc định từ develop. Ngoại lệ sửa bản main đã bàn giao khi develop
còn việc chưa nghiệm thu theo GIT-WORKFLOW §7: feature từ main, PR main rồi
đồng bộ main → develop; giữ review và kiểm chứng.
PR task vào develop; bản nghiệm thu qua PR develop → main, tag sau kiểm main.
Không tạo nhánh release riêng hoặc commit trực tiếp nhánh chung sau bootstrap.
Không tự biến mock/stub/placeholder thành PASS hoặc đổi spec để bỏ lỗi.
Báo nhánh/commit/task/file/API/SQL/UI/commands/actual/blocker/người nhận đầu ra.
Push/PR/merge/deploy theo quyền người giao việc, không suy ra từ việc đọc kế hoạch.
```
