> **Convention:** Checkout từ `dev`, tạo nhánh `feat/thai/thai-05-organization-memberships`, commit theo Conventional Commits và mở PR về `dev`.
> **Merge gate:** Chỉ merge khi toàn bộ GitHub Actions xanh, test của task có bằng chứng và PR đã được người khác review.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng hệ thống quản lý thành viên và phân quyền trong nội bộ tổ chức:
- Phục vụ màn hình **UI-10 (Organization Context Selector)** và **UI-11 (Organization Team Management)**.
- Quản trị viên của tổ chức (`MANAGER`) có thể mời/thêm tài khoản người dùng vào tổ chức, gán vai trò (`MANAGER` hoặc `CHECK_IN_STAFF`), kích hoạt (`activate`) hoặc tạm ngưng (`deactivate`) thành viên.
- Bảo vệ bất biến nghiệp vụ tối quan trọng: **Không bao giờ cho phép hạ quyền hoặc vô hiệu hóa Quản lý (`MANAGER`) đang hoạt động cuối cùng của tổ chức**, tránh tình trạng tổ chức bị "mồ côi" không ai quản lý.
- Cách ly quyền giữa các tổ chức (Multi-tenant data isolation): Quản lý của Tổ chức A tuyệt đối không được xem hay can thiệp vào thành viên của Tổ chức B.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/me/memberships`
- **Mục đích:** Lấy danh sách tất cả các tổ chức mà tài khoản hiện tại đang là thành viên (để hiển thị bộ chọn tổ chức ở UI-10).
- **Quyền hạn:** `AUTHENTICATED`.
- **Response Body (200 OK):**
```json
[
  {
    "organizationId": 45,
    "organizationName": "Công ty Âm nhạc Trẻ Việt Nam",
    "role": "MANAGER",
    "isActive": true
  },
  {
    "organizationId": 78,
    "organizationName": "Lễ Hội Văn Hóa Sài Gòn",
    "role": "CHECK_IN_STAFF",
    "isActive": true
  }
]
```

### 2.2. GET `/api/organizations/{organizationId}/members`
- **Mục đích:** Xem danh sách toàn bộ thành viên trong tổ chức.
- **Quyền hạn:** Phải là `MANAGER` của chính `organizationId` này.
- **Response Body (200 OK):**
```json
[
  {
    "membershipId": 101,
    "userId": 1024,
    "userName": "buyer_nguyen",
    "fullName": "Nguyễn Văn A",
    "email": "buyer.nguyen@example.com",
    "role": "MANAGER",
    "isActive": true,
    "joinedAt": "2026-10-09T18:00:00Z"
  }
]
```

### 2.3. POST `/api/organizations/{organizationId}/members`
- **Mục đích:** Thêm thành viên mới vào tổ chức theo `userName` hoặc `email`.
- **Headers:** `X-CSRF-Token: <token>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "userName": "staff_tran",
  "role": "CHECK_IN_STAFF"
}
```
- **Validation Rules:**
  - `role`: Bắt buộc là `MANAGER` hoặc `CHECK_IN_STAFF` (không thể cấp quyền `ADMIN` hệ thống qua endpoint này).
  - Tài khoản phải đã tồn tại trong hệ thống và ở trạng thái `ACTIVE`.
- **Response Body (201 Created):** Trả về thông tin membership vừa tạo/tái kích hoạt.
- **Lỗi:** `400 Bad Request` (user không tồn tại), `409 Conflict` (thành viên này đã đang hoạt động trong tổ chức).

### 2.4. POST `/api/organizations/{organizationId}/members/{userId}/role`
- **Mục đích:** Đổi vai trò giữa `MANAGER` và `CHECK_IN_STAFF`.
- **Request Body:** `{"role": "MANAGER"}`.

### 2.5. POST `/api/organizations/{organizationId}/members/{userId}/deactivate`
- **Mục đích:** Vô hiệu hóa thành viên (thu hồi quyền truy cập tức thì).
- **Quy tắc bảo vệ:** Nếu `userId` là `MANAGER` đang hoạt động cuối cùng của tổ chức, từ chối với lỗi `400 Bad Request` (`{"error": "CANNOT_DEACTIVATE_LAST_MANAGER"}`).

### 2.6. POST `/api/organizations/{organizationId}/members/{userId}/activate`
- **Mục đích:** Kích hoạt lại thành viên đã bị vô hiệu hóa trước đó.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Mô hình Phân quyền Dựa trên Database (DB-backed RBAC)
- **Tuyệt đối không tin cậy:** `organizationId` do client gửi lên trên header hay URL!
- **Cơ chế xác thực quyền thực tế:**
  - Mỗi request gọi đến `/api/organizations/{organizationId}/**` phải chạy qua bước kiểm tra quyền tại Database:
    ```sql
    SELECT role, is_active 
    FROM dbo.tc_organization_memberships 
    WHERE user_id = :currentActorId AND organization_id = :organizationId;
    ```
  - Nếu kết quả trả về rỗng, hoặc `is_active == false`, hoặc `role != 'MANAGER'`, ngay lập tức ngắt luồng và trả về `403 Forbidden`.

### 3.2. Thuật toán Bảo vệ "Last Active Manager"
- Khi thực hiện đổi role sang `CHECK_IN_STAFF` hoặc `deactivate` một `MANAGER`:
  - Mở Transaction với mức cô lập `REPEATABLE READ` hoặc dùng `UPDLOCK, HOLDLOCK` để tránh race condition khi có 2 admin cùng thực hiện thao tác:
    ```sql
    SELECT COUNT(*) 
    FROM dbo.tc_organization_memberships WITH (UPDLOCK, HOLDLOCK)
    WHERE organization_id = :orgId 
      AND role = 'MANAGER' 
      AND is_active = 1;
    ```
  - Nếu `count <= 1` và thành viên mục tiêu chính là Manager này: Ném lỗi `BusinessException("Không thể vô hiệu hóa hoặc hạ quyền Quản lý duy nhất còn lại của tổ chức.")`.

### 3.3. Xử lý Thêm thành viên Trùng lặp (Upsert / Reactivate Pattern)
- Ràng buộc cơ sở dữ liệu `UNIQUE (organization_id, user_id)`:
  - Nếu cặp `(organization_id, user_id)` đã tồn tại nhưng có `is_active = 0`: Khi thêm lại, hệ thống sẽ thực hiện cập nhật `is_active = 1` và gán `role` mới, thay vì cố gắng thực hiện `INSERT` gây lỗi Duplicate Key.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `OrganizationMemberDto`, `AddMemberRequestDto`, `ChangeMemberRoleRequestDto`, `MembershipSummaryDto`.
   - Repositories: `OrganizationMembershipRepository` (cung cấp các truy vấn tìm kiếm theo orgId, userId, đếm active managers, cập nhật role, deactivate/activate).
   - Services: `OrganizationMembershipService`.
   - Servlets: `OrganizationMembersServlet`, `OrganizationMemberRoleServlet`, `OrganizationMemberStatusServlet`, `MyMembershipsServlet`.
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/organization/members/list.jsp`: Danh sách thành viên tổ chức, badge vai trò, nút Thêm thành viên, menu Thao tác (Đổi vai trò, Kích hoạt/Vô hiệu hóa).
   - `src/main/webapp/WEB-INF/views/organization/context-switch.jsp`: Modal / Dropdown chọn tổ chức làm việc.
3. **Database State Changes:**
   - Thay đổi trạng thái `role`, `is_active`, `updated_at` trong bảng `dbo.tc_organization_memberships`.
4. **Kiểm thử tự động:**
   - Unit test kiểm tra logic Last Active Manager.
   - Concurrency test mô phỏng 2 luồng cùng deactivate Manager để đảm bảo không bị lọt qua.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Tạo DTOs**
  - Tạo `AddMemberRequestDto` với validation `userName` không rỗng và enum `role`.
- **Bước 2: Xây dựng `OrganizationMembershipRepository`**
  - Viết truy vấn `countActiveManagers(Long orgId)`.
  - Viết truy vấn `findByOrgAndUser(Long orgId, Long userId)`.
  - Viết câu lệnh cập nhật role và trạng thái active.
- **Bước 3: Xây dựng `OrganizationMembershipService`**
  - Triển khai phương thức kiểm tra quyền: `assertManagerAccess(Long actorId, Long orgId)`.
  - Triển khai `addMember(Long actorId, Long orgId, AddMemberRequestDto dto)`: tìm user theo userName -> kiểm tra membership cũ -> kích hoạt lại hoặc insert mới.
  - Triển khai `deactivateMember(Long actorId, Long orgId, Long targetUserId)`: kiểm tra nếu target là Manager thì gọi `countActiveManagers`. Nếu <= 1 thì chặn lại.
- **Bước 4: Xây dựng Servlets**
  - Ánh xạ các đường dẫn URL RESTful theo chuẩn.
  - Đảm bảo kiểm tra CSRF token cho toàn bộ các POST mutation.
- **Bước 5: Xây dựng Giao diện JSP (UI-11)**
  - Hiển thị danh sách đẹp mắt, có modal xác nhận trước khi vô hiệu hóa thành viên.
- **Bước 6: Viết kiểm thử tự động**
  - Đảm bảo `mvn -B test` thành công 100%.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| MEM-01 | Thêm thành viên mới thành công | Manager đăng nhập, userName="staff1", role=CHECK_IN_STAFF | 201 Created, DB có bản ghi active=1 |
| MEM-02 | Thêm thành viên đã tồn tại nhưng bị inactive | Thành viên cũ có active=0 | 200 OK, kích hoạt lại active=1 với role mới |
| MEM-03 | Người ngoài tổ chức xem danh sách thành viên | User không có membership gọi GET `/members` | 403 Forbidden |
| MEM-04 | Vô hiệu hóa Manager cuối cùng | Tổ chức chỉ có 1 Manager duy nhất | 400 Bad Request, DB giữ nguyên trạng thái |
| MEM-05 | Đổi role sang CHECK_IN_STAFF của Manager cuối | Cố tình hạ quyền Manager duy nhất | 400 Bad Request |
