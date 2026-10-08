# Ngày 06 — Tổ chức, thành viên và chính sách hoa hồng

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ tư, 2026-09-30. **Ước lượng tạm của kế hoạch ban đầu:** 16 giờ công tổng của nhóm.

**Mục tiêu:** User đăng nhập gửi hồ sơ Organization, admin duyệt cùng tổ chức; manager quản lý thành viên và không loại người quản lý cuối.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §4, §5.2, §6.2, §6.11, §14 SP01/V10/TR06. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D03, D04, D05; schema CommissionRule đã có.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D06-T01 — Gửi/đọc/từ chối yêu cầu tổ chức

**Phụ trách đề xuất:** A. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/service/identity/OrganizationService.java; JAVA/controller/identity/OrganizationServlet.java; JAVA/repository/identity/OrganizationRepository.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** POST /organization-requests; GET /me/organization-requests; GET /admin/organization-requests; POST /admin/organization-requests/{id}/reject.

**Phụ thuộc của task:** D04 session và D03 transaction.

**Cách thực hiện:**

- [ ] 1. Validate tên/email/điện thoại/mô tả theo data dictionary; lấy applicant từ session; tạo Organization DRAFT rồi submitForApproval → PENDING_APPROVAL trên cùng record, requester lấy từ session.
- [ ] 2. Trả danh sách của chính applicant có phân trang; admin có danh sách duyệt và chi tiết đúng phạm vi.
- [ ] 3. Organization.reject(reason) chỉ PENDING_APPROVAL, bắt buộc lý do, lưu người quyết định/thời gian/audit; quyết định trái kết quả cuối trả conflict.
- [ ] 4. Không tự yêu cầu emailVerified nếu SPEC chỉ yêu cầu logged-in cho gửi tổ chức; giữ requirement verify email riêng ở giữ vé.
- [ ] 5. Kiểm tra Organization.submitForApproval/approve/reject; requester nullable ở DRAFT, bắt buộc sau khi gửi. Không có hồ sơ yêu cầu riêng hay Organization thứ hai.

**Kiểm chứng bắt buộc:**

- [ ] User sửa applicantId không tạo request cho người khác.
- [ ] Người gửi không tự approve/reject; bị reject đọc được lý do.

**Điều kiện hoàn thành:** Yêu cầu tổ chức quản lý được qua API. Ghi case và kết quả trong `docs/evidence/day-06.md`.

## D06-T02 — SP01/TX01 và quy tắc phí ban đầu

**Phụ trách đề xuất:** B. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D06_01_organization.sql; SQLTEST/day-06.sql; JAVA/repository/identity/OrganizationRepository.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_ApproveOrganization(organizationId,actorId,initialCommissionPolicy) → organizationId; chỉ admin.

**Phụ thuộc của task:** D06-T01 request, D02 CommissionRule schema; initial policy quản trị được cấu hình.

**Cách thực hiện:**

- [ ] 1. Khóa Organization PENDING_APPROVAL/requester, kiểm admin trong DB; đổi cùng record APPROVED, tạo CommissionRule ban đầu và User.assignRole(org, MANAGER) qua UserOrganizationRole nguyên tử.
- [ ] 2. initialCommissionPolicy đến từ cấu hình quản trị được chọn, không nhận rate tùy ý của applicant; chưa có chính sách phải lỗi rõ.
- [ ] 3. Đã approved trả organizationId cũ; rejected báo conflict; audit cùng transaction.
- [ ] 4. SP tuân ownership/savepoint, không commit outer JPA; cấp EXECUTE admin và kiểm tra forbidden buyer trực tiếp.
- [ ] 5. Test TX01 gây lỗi sau đổi APPROVED trước tạo rule/map role để rollback toàn bộ; hai session duyệt cùng organizationId không tạo rule/quyền trùng.

**Kiểm chứng bắt buộc:**

- [ ] Hai request approve cùng ID trả cùng organizationId.
- [ ] Organization vẫn PENDING_APPROVAL sau lỗi, không rule/map role bán phần hoặc Organization thứ hai.

**Điều kiện hoàn thành:** SP01 có đường gọi Java thật và minh chứng TX01. Ghi case và kết quả trong `docs/evidence/day-06.md`.

## D06-T03 — Membership và chốt bảo vệ cuối cùng

**Phụ trách đề xuất:** B+A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D06_02_membership.sql; JAVA/service/identity/MembershipService.java; JAVA/controller/identity/MembershipServlet.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** V10 vw_OrganizationMembers; TR06 trg_UserOrganizationRole_ProtectLastManager; Quản lý User.organizationRoles qua UserOrganizationRole, thêm trực tiếp bằng userName tài khoản có sẵn.

**Phụ thuộc của task:** D06-T02 để có Organization+manager; viết trigger/CRUD theo schema trước.

**Cách thực hiện:**

- [ ] 1. Tìm User theo userName duy nhất rồi assignRole(org, role); revokeRole bỏ quyền hiệu lực, UserOrganizationRole giữ active/lịch sử. Unique user–organization kể cả inactive, kích hoạt lại không tạo dòng trùng.
- [ ] 2. Manager thêm/đổi MANAGER hoặc CHECK_IN_STAFF/vô hiệu; không có cấp ADMIN và không có invitation workflow.
- [ ] 3. Khóa Organization trước thay membership; TR06 kiểm tra theo tập cả organization cũ/mới khi đổi FK để giữ ít nhất một manager active.
- [ ] 4. Đọc qua V10 với filter membership owner hoặc organization được phép, không lộ password/auth fields.
- [ ] 5. Kiểm tra User bị DISABLED là một đường có thể ảnh hưởng quản lý hoạt động; ghi và áp dụng nhất quán định nghĩa active trong guard, không để luồng khác vượt bất biến.

**Kiểm chứng bắt buộc:**

- [ ] Hai manager hạ quyền đồng thời còn ít nhất một manager hoạt động.
- [ ] User manager A/check-in B không sửa thành viên B; trigger nhiều dòng có vi phạm rollback cả lệnh.

**Điều kiện hoàn thành:** V10/TR06 được sử dụng và quyền đa tổ chức đúng. Ghi case và kết quả trong `docs/evidence/day-06.md`.

## D06-T04 — Chính sách phí và nghiệm thu tuần nền

**Phụ trách đề xuất:** C+A. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** JAVA/service/settlement/CommissionRuleService.java; JAVA/controller/settlement/CommissionRuleServlet.java; TEST/acceptance/Day06IT.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** GET/POST /admin/organizations/{id}/commission-rules; GET /me/memberships; DTO có hiệu lực và tổ chức.

**Phụ thuộc của task:** D06-T02/T03; policy CRUD có thể chuẩn bị theo schema từ D02.

**Cách thực hiện:**

- [ ] 1. Cho admin tạo chính sách với rate/fixedFee không âm, khoảng thời gian hợp lệ; không tự mặc định tỷ lệ kinh doanh.
- [ ] 2. Manager chỉ đọc phần cần cho vận hành; sửa điều khoản đã áp dụng sẽ bị TR03 bảo vệ từ ngày 7.
- [ ] 3. Tạo integration case đăng ký tổ chức → duyệt → thêm nhân viên → nhân viên bị chặn API tài chính.
- [ ] 4. Ghi Java tests Organization.submitForApproval/approve/reject và User.assignRole/revokeRole theo diagram; Service sở hữu quyền/persistence. V10 trả userName/role/active, không auth secret.
- [ ] 5. Chạy lại auth/OTP vì membership và principal routing cùng chạm authentication context.

**Kiểm chứng bắt buộc:**

- [ ] SP01, V10, TR06 có lời gọi ứng dụng và evidence.
- [ ] Mỗi Organization approved có ít nhất một CommissionRule và manager.

**Điều kiện hoàn thành:** Có tổ chức và chính sách đủ để tạo/publish Event ngày 7. Ghi case và kết quả trong `docs/evidence/day-06.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day06IT` và `database/tests/day-06.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day06IT verify
```
