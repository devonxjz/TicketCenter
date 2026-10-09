> **Convention:** Checkout từ `dev`, tạo nhánh `feat/thai/thai-04-admin-organization-approval`, commit theo Conventional Commits và mở PR về `dev`.
> **Merge gate:** Chỉ merge khi toàn bộ GitHub Actions xanh, test của task có bằng chứng và PR đã được người khác review.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng luồng Quản trị viên hệ thống xét duyệt hoặc từ chối yêu cầu thành lập tổ chức:
- Phục vụ trực tiếp màn hình **UI-19 (Admin Organization Approval Portal)**.
- Phân quyền nghiêm ngặt: Chỉ tài khoản có `PlatformRole.ADMIN` mới có quyền truy cập.
- Khi Duyệt (`Approve`):
  - Kích hoạt tổ chức sang trạng thái `APPROVED`.
  - Tự động gán quyền `OrganizationRole.MANAGER` cho người yêu cầu (`requesterId`).
  - Thiết lập chính sách hoa hồng ban đầu (`CommissionRule`) cho tổ chức.
  - Ghi vết kiểm toán (Audit Trail) đầy đủ.
- Khi Từ chối (`Reject`):
  - Chuyển trạng thái sang `REJECTED`, bắt buộc cung cấp lý do từ chối rõ ràng để gửi phản hồi cho người nộp đơn.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/admin/organization-requests`
- **Mục đích:** Admin xem danh sách tất cả các yêu cầu tổ chức, hỗ trợ lọc theo trạng thái (`PENDING_APPROVAL`, `APPROVED`, `REJECTED`).
- **Quyền hạn:** `PlatformRole.ADMIN`.
- **Query Params:** `status` (tùy chọn), `page` (mặc định 1), `pageSize` (mặc định 20).
- **Response Body (200 OK):**
```json
{
  "items": [
    {
      "organizationId": 45,
      "name": "Công ty Âm nhạc Trẻ Việt Nam",
      "contactEmail": "contact@amnhactre.vn",
      "contactPhone": "0987654321",
      "requester": {
        "userId": 1024,
        "fullName": "Nguyễn Văn A",
        "email": "buyer.nguyen@example.com"
      },
      "status": "PENDING_APPROVAL",
      "submittedAt": "2026-10-09T17:40:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

### 2.2. POST `/api/admin/organization-requests/{organizationId}/approve`
- **Mục đích:** Admin phê duyệt tổ chức kèm chính sách hoa hồng ban đầu.
- **Headers:** `X-CSRF-Token: <token>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "initialPolicy": {
    "ratePercent": "5.0000",
    "fixedFee": "10000.00",
    "effectiveFrom": "2026-10-01T00:00:00Z",
    "effectiveTo": "2027-10-01T00:00:00Z"
  }
}
```
- **Validation Rules:**
  - `ratePercent`: Số thực từ 0.0000 đến 100.0000 (tỷ lệ phần trăm hoa hồng nền tảng).
  - `fixedFee`: Số tiền cố định mỗi đơn hàng (>= 0).
  - `effectiveFrom < effectiveTo`.
- **Response Body (200 OK):**
```json
{
  "success": true,
  "message": "Đã phê duyệt tổ chức thành công.",
  "organizationId": 45,
  "status": "APPROVED",
  "assignedManagerId": 1024,
  "commissionRuleId": 120
}
```

### 2.3. POST `/api/admin/organization-requests/{organizationId}/reject`
- **Mục đích:** Admin từ chối yêu cầu thành lập tổ chức.
- **Request Body:**
```json
{
  "reason": "Hồ sơ đính kèm không có giấy phép đăng ký kinh doanh hợp lệ."
}
```
- **Validation Rules:**
  - `reason`: Không rỗng, từ 10 đến 500 ký tự.
- **Response Body (200 OK):**
```json
{
  "success": true,
  "message": "Đã từ chối yêu cầu thành lập tổ chức.",
  "organizationId": 45,
  "status": "REJECTED"
}
```

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Ủy thác Transaction cho Stored Procedure Canonical
- **Quy tắc quan trọng:** Toàn bộ nghiệp vụ phê duyệt/từ chối được đóng gói nguyên tử trong các Stored Procedure của SQL Server. Backend Java không tự viết nhiều câu update lẻ tẻ gây nguy cơ dữ liệu bán phần (partial commit).
- **Khi Phê duyệt:** Gọi `dbo.sp_organization_approve`:
  ```sql
  EXEC dbo.sp_organization_approve
       @organization_id = :orgId,
       @admin_user_id = :adminId,
       @rate_percent = :ratePercent,
       @fixed_fee = :fixedFee,
       @effective_from = :effectiveFrom,
       @effective_to = :effectiveTo;
  ```
  - Trong nội tại SP:
    1. Kiểm tra trạng thái hiện tại của `tc_organizations`: Nếu không phải `PENDING_APPROVAL`, ném lỗi THROW.
    2. Cập nhật `status = 'APPROVED'`, `approved_by = @admin_user_id`, `approved_at = SYSDATETIME()`.
    3. Tự động insert vào `dbo.tc_organization_memberships`: gán `user_id = requester_id`, `role = 'MANAGER'`, `is_active = 1`.
    4. Tự động insert vào `dbo.tc_commission_rules` cho organization vừa duyệt.
    5. Ghi nhận log kiểm toán vào `dbo.tc_audit_logs`.
- **Khi Từ chối:** Gọi `dbo.sp_organization_reject`:
  ```sql
  EXEC dbo.sp_organization_reject
       @organization_id = :orgId,
       @admin_user_id = :adminId,
       @rejected_reason = :reason;
  ```

### 3.2. Chống Xung đột Thao tác Admin Đồng thời (Admin Race Condition)
- Nếu hai Admin cùng mở một yêu cầu và nhấn Approve/Reject cùng thời điểm:
  - Stored Procedure sử dụng câu lệnh `UPDATE dbo.tc_organizations WITH (UPDLOCK, ROWLOCK) ... WHERE id = @orgId AND status = 'PENDING_APPROVAL'`.
  - Admin bấm trước sẽ thành công. Admin bấm sau sẽ nhận lỗi `ORGANIZATION_ALREADY_PROCESSED` và HTTP trả về `409 Conflict` kèm thông báo trạng thái hiện tại của tổ chức.

### 3.3. Kiểm tra Phân quyền Admin Tập trung
- Servlet sử dụng `SecurityContext.getCurrentUser(request)`:
  ```java
  CurrentUserDto user = SecurityContext.getCurrentUser(request);
  if (user == null || user.getPlatformRole() != PlatformRole.ADMIN) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Yêu cầu quyền Administrator.");
      return;
  }
  ```

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `AdminOrgApproveRequestDto`, `AdminOrgRejectRequestDto`, `AdminOrgDecisionResponseDto`.
   - Repositories: `AdminOrganizationRepository` (triển khai gọi `CallableStatement` thực thi các SP `sp_organization_approve` và `sp_organization_reject`).
   - Services: `AdminOrganizationService`.
   - Servlets: `AdminOrganizationApprovalServlet` (định tuyến `/api/admin/organization-requests/*`).
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/admin/organizations/list.jsp`: Danh sách tab Chờ duyệt / Đã duyệt / Đã từ chối, nút Xem chi tiết.
   - `src/main/webapp/WEB-INF/views/admin/organizations/detail.jsp`: Xem thông tin người nộp đơn, modal nhập phí hoa hồng khi duyệt, modal nhập lý do khi từ chối.
3. **Database Changes:**
   - Bảng `dbo.tc_organizations` đổi `status = 'APPROVED'` hoặc `'REJECTED'`.
   - Bảng `dbo.tc_organization_memberships` phát sinh 1 bản ghi `role = 'MANAGER'`.
   - Bảng `dbo.tc_commission_rules` phát sinh 1 bản ghi chính sách phí ban đầu.
4. **Kiểm thử tự động:**
   - Unit test mock DB SP execution.
   - Integration test kiểm tra Customer không được gọi endpoint admin (403), Admin duyệt thành công (200), duyệt lặp (409).

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs cho Admin Approval**
  - Tạo `AdminOrgApproveRequestDto` chứa `InitialPolicyDto`.
  - Tạo `AdminOrgRejectRequestDto` có kiểm tra chuỗi lý do không được để trống.
- **Bước 2: Xây dựng Repository với JDBC CallableStatement**
  - Mở kết nối qua DataSource / EntityManager unwrapped connection.
  - Chuẩn bị gọi `dbo.sp_organization_approve` và bắt mã lỗi SQLSTATE chuẩn để ánh xạ sang exception phù hợp.
- **Bước 3: Xây dựng `AdminOrganizationService`**
  - Xác thực quyền `PlatformRole.ADMIN`.
  - Thực hiện gọi repository và đóng gói kết quả trả về `AdminOrgDecisionResponseDto`.
- **Bước 4: Xây dựng Servlet `/api/admin/organization-requests`**
  - Xử lý các action tương ứng qua URL path: `/approve` và `/reject`.
  - Đảm bảo kiểm tra CSRF token trước khi gọi mutation.
- **Bước 5: Xây dựng Giao diện JSP (UI-19)**
  - Tích hợp giao diện quản trị rõ ràng, nút Duyệt màu xanh, nút Từ chối màu đỏ có hộp thoại xác nhận (Confirm Dialog).
- **Bước 6: Chạy kiểm thử tự động**
  - Chạy `mvn -B test` để bảo đảm tích hợp chuẩn chỉ và xanh toàn bộ.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| ADM-01 | Phê duyệt tổ chức thành công | Admin đăng nhập, ratePercent=5%, fixedFee=10000 | 200 OK, Org thành APPROVED, User thành MANAGER, có CommissionRule |
| ADM-02 | Customer cố tình duyệt | User có role CUSTOMER gọi approve | 403 Forbidden |
| ADM-03 | Duyệt tổ chức đã được duyệt trước đó | Org đã APPROVED | 409 Conflict |
| ADM-04 | Từ chối không có lý do | reason="" hoặc null | 400 Bad Request |
| ADM-05 | Chính sách hoa hồng sai lệch | ratePercent = -1% hoặc 105% | 400 Bad Request kèm thông báo lỗi tỷ lệ hoa hồng |
