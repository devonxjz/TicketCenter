# Tiếp tục VUONG-EPIC với database nhóm

Người dùng yêu cầu tiếp tục sau khi đã xem kết quả đọc schema trong SSMS. Giữ quyết định UUID/tc_*/VND, phạm vi phần Vương và không thay đổi database chung. Nhánh thực hiện tiếp: feat/vuong/vuong-epic-finance-integration, nền 801eb23.

1. Xuất schema-only và metadata từ Azure, dựng database cục bộ TC_Vuong_Test_20261010_TeamSchema. Kiểm số bảng/cột/module/FK/check/index khớp; không sao chép người dùng hoặc giao dịch.
2. Viết kiểm thử JDBC trên bản sao: mapping user_id/captured_at, nghĩa vụ hoàn tiền một lần, settlement/payout/audit thực tế và nguồn bất biến. Chạy RED trên schema hiện tại.
3. Thêm adapter SQL riêng trước migration 027, dùng views chuẩn hóa phần đọc và audit insert tương thích. tc_refund_obligations là nguồn nghĩa vụ; không coi tc_refunds legacy là nghĩa vụ. Giữ bảng/ID/triggers legacy, không sửa model/026/pom. Audit ID BIGINT được chiếu thành UUID ổn định, không rekey lịch sử. Timestamps commission bổ sung có default. Thay các truy vấn tài chính sở hữu để dùng views; khóa/triggers tiếp tục trên bảng vật lý thật.
4. GREEN trên bản sao và fixture canonical, SQL guards/races/permissions, bare Maven/Node/package; thêm fixture schema nhóm cố định cho CI. Cập nhật evidence và PR42 theo đúng phạm vi. Full startup/JPA liên miền và Excel vẫn có gate riêng, không dùng test tài chính để tuyên bố owner E2E hoàn tất.

Ruling: adapter là migration thêm cho phần tài chính, không đổi contract model của owner — schema nhóm có cả legacy attempts và canonical obligations theo 026 — chọn nhầm attempts sẽ tính trùng tiền hoàn. Không áp migration vào Azure trong phiên này.

Đã dựng bản sao: 36 bảng, 333 cột, 70 SQL modules, 61 FK, 59 checks, 87 indexes khớp metadata nguồn. Credential giữ trong cấu hình cục bộ riêng, không đưa vào repo/log.

Tiến độ: bước1–3 hoàn tất; RED trên schema nhóm được quan sát, adapter và JDBC GREEN. Bản snapshot đã cài từ đầu vào database riêng; 9 ca JDBC schema nhóm đạt, 19 ca canonical đạt, 5 suites SQL và 6 races đạt. Bước4 tiếp tục với kiểm thử package/HTTP và CI mới, evidence ở docs/evidence/vuong/VUONG-TEAM-SCHEMA.md.

Kiểm thử cuối sau fix: default295 discovered/243 executed/52 guarded skips, package thành công; Node29, HTTP tài chính14, Swagger HTTP10, canonical JDBC19, SQL5 suites/6 races đều đạt. actionlint và whitespace đạt. Cập nhật nhánh/PR đã được người dùng duyệt trong quy trình trước; không hỏi lại lựa chọn tích hợp, không merge/deploy.

Final review: một reviewer đọc độc lập toàn bộ thay đổi. Sửa blocker retry compensation và hướng dẫn thứ tự migration. Hai regression được quan sát RED→GREEN; cả suite9/9 đạt. Giữ alias cột order-summary để các module cũ compile, không tuyên bố quy trình cũ được nghiệm thu.

Ruling: bỏ khỏi projection chỉ mirror compensation FAILED/RETRYABLE khi cùng payment CAPTURED đã được hoàn đủ bằng các obligation COMPLETED có current transfer SUCCEEDED và timestamp — giữ pending/unknown/partial và lịch sử vật lý — nếu owner tạo debt độc lập thiếu liên kết thì vẫn chặn, cần owner reconciliation.
