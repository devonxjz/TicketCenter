> **Convention:** Checkout từ `dev`, tạo nhánh `feat/thai/thai-03-organization-request`, commit theo Conventional Commits và mở PR về `dev`.\n> **Merge gate:** Chỉ merge khi toàn bộ GitHub Actions xanh, test của task có bằng chứng và PR đã được người khác review.

## 1. Mục tiêu và Nghiệp vụ

Triển khai quy trình đăng ký thành lập Nhà tổ chức sự kiện (Organization Request) và theo dõi tiến trình xét duyệt:
- Phục vụ màn hình **UI-09 (Organization Request Form & Tracking List)**.
- Người dùng có tài khoản `CUSTOMER` có thể gửi hồ sơ xin nâng cấp trở thành Ban tổ chức sự kiện.
- Đảm bảo tính nhất quán của vòng đời: Bản ghi yêu cầu sử dụng trực tiếp thực thể `Organization` (với trạng thái ban đầu là `PENDING_APPROVAL`), không tạo bảng trung gian thừa thãi.
- Ngăn ngừa tình trạng spam yêu cầu, gửi đúp (double submit) hoặc người dùng xem trộm hồ sơ của người khác (IDOR).

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. POST `/api/organization-requests`
- **Mục đích:** Gửi một hồ sơ yêu cầu tạo tổ chức mới.
- **Quyền hạn:** Đã đăng nhập (`AUTHENTICATED`).
- **Headers:** `X-CSRF-Token: <token>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "name": "Công ty Âm nhạc Trẻ Việt Nam",
  "contactEmail": "contact@amnhactre.vn",
  "contactPhone": "0987654321",
  "description": "Đơn vị chuyên tổ chức các sự kiện âm nhạc sống và biểu diễn nghệ thuật ngoài trời."
}
```
- **Validation Rules:**
  - `name`: Không rỗng, 3–150 ký tự, không chứa ký tự điều khiển lạ.
  - `contactEmail`: Định dạng email RFC hợp lệ.
  - `contactPhone`: Số điện thoại chuẩn Việt Nam (10 chữ số, bắt đầu bằng 0). Tùy chọn.
  - `description`: Tối đa 1000 ký tự. Tùy chọn.
- **Response Body (201 Created):**
```json
{
  "organizationId": 45,
  "name": "Công ty Âm nhạc Trẻ Việt Nam",
  "contactEmail": "contact@amnhactre.vn",
  "contactPhone": "0987654321",
  "description": "Đơn vị chuyên tổ chức các sự kiện âm nhạc sống và biểu diễn nghệ thuật ngoài trời.",
  "status": "PENDING_APPROVAL",
  "requesterId": 1024,
  "submittedAt": "2026-10-09T17:40:00Z"
}
```
- **Mã lỗi:**
  - `400 Bad Request`: Thiếu thông tin hoặc sai format.
  - `401 Unauthorized`: Chưa đăng nhập.
  - `409 Conflict`: Người dùng đang có một yêu cầu tạo tổ chức khác cùng tên hoặc đang ở trạng thái `PENDING_APPROVAL`.

### 2.2. GET `/api/me/organization-requests`
- **Mục đích:** Trả về danh sách tất cả các yêu cầu tạo tổ chức do chính tài khoản hiện tại gửi lên.
- **Quyền hạn:** Đã đăng nhập (`AUTHENTICATED`).
- **Response Body (200 OK):**
```json
[
  {
    "organizationId": 45,
    "name": "Công ty Âm nhạc Trẻ Việt Nam",
    "contactEmail": "contact@amnhactre.vn",
    "status": "PENDING_APPROVAL",
    "submittedAt": "2026-10-09T17:40:00Z",
    "rejectedReason": null
  },
  {
    "organizationId": 12,
    "name": "CLB Sự Kiện Đại Học ABC",
    "contactEmail": "contact@clbabc.edu.vn",
    "status": "REJECTED",
    "submittedAt": "2026-09-15T08:30:00Z",
    "rejectedReason": "Giấy tờ bảo trợ không có dấu mộc nhà trường."
  }
]
```

### 2.3. GET `/api/organization-requests/{organizationId}`
- **Mục đích:** Lấy thông tin chi tiết của một hồ sơ yêu cầu.
- **Kiểm tra phân quyền nghiêm ngặt:** Chỉ chủ sở hữu hồ sơ (`requesterId == actorId`) hoặc Quản trị viên hệ thống (`PlatformRole.ADMIN`) mới được phép truy cập. Mọi truy cập khác trả về `403 Forbidden`.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Tương tác Cơ sở dữ liệu & Tích hợp Stored Procedure
- Bảng chính: `dbo.tc_organizations` và View `dbo.v_organizations`.
- Gọi Stored Procedure chuẩn: `dbo.sp_organization_submit_for_approval`
  ```sql
  EXEC dbo.sp_organization_submit_for_approval
       @organization_id = ?,
       @requester_id = ?;
  ```
- Trạng thái vòng đời:
  - Khi mới insert: Trạng thái là `PENDING_APPROVAL`.
  - Thiết lập khóa ngoại `requester_id` trỏ về `dbo.tc_users(id)`.
  - Chưa cấp quyền `MANAGER` và chưa tạo `CommissionRule` ở bước này! Việc cấp quyền và tạo chính sách hoa hồng thuộc thẩm quyền của Admin ở issue THAI-04.

### 3.2. Chống lỗi IDOR (Insecure Direct Object References)
- Không bao giờ nhận `requesterId` từ Request Body hoặc Query Param!
- Luôn trích xuất `actorId` từ `SecurityContext.getCurrentUser(request)`.
- Khi query chi tiết hoặc danh sách:
  ```sql
  SELECT * FROM dbo.v_organizations 
  WHERE requester_id = :actorId 
  ORDER BY created_at DESC
  ```

### 3.3. Xử lý Trùng lặp & Chống Gửi Đúp (Idempotency)
- Nếu người dùng bấm nút "Gửi yêu cầu" 2 lần liên tiếp:
  - Kiểm tra xem người dùng hiện có yêu cầu nào đang `PENDING_APPROVAL` với cùng tên hay không.
  - Sử dụng Transaction với isolation `READ COMMITTED` hoặc kiểm tra điều kiện duy nhất trước khi gọi procedure để chặn race condition.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `OrganizationRequestCreateDto`, `OrganizationRequestSummaryDto`, `OrganizationRequestDetailDto`.
   - Repositories: `OrganizationRequestRepository` (hoặc `OrganizationRepository` trong phạm vi request), có phương thức `findByRequesterId(Long requesterId)`.
   - Services: `OrganizationRequestService` (xử lý logic submit, kiểm tra giới hạn yêu cầu, đọc lịch sử).
   - Servlets: `OrganizationRequestServlet` (xử lý POST submit và GET me list).
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/organization/request/create.jsp`: Form điền thông tin tổ chức, tải lên ghi chú, nút submit kèm disable khi loading.
   - `src/main/webapp/WEB-INF/views/organization/request/my-requests.jsp`: Bảng danh sách trạng thái yêu cầu (`PENDING_APPROVAL`, `APPROVED`, `REJECTED`), hiển thị lý do từ chối nếu có.
3. **Database State Changes:**
   - Tạo mới một bản ghi trong `dbo.tc_organizations` có `status = 'PENDING_APPROVAL'`, `requester_id = actorId`.
4. **Kiểm thử tự động:**
   - `OrganizationRequestServiceTest`: Kiểm tra validation, submit thành công, chặn người chưa login.
   - `OrganizationRequestWebFlowTest`: Kiểm tra chống IDOR (user A không đọc được request của user B).

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Tạo DTOs**
  - Tạo `OrganizationRequestCreateDto` có các annotation / regex validation tiếng Việt thân thiện.
  - Tạo `OrganizationRequestSummaryDto` chứa id, tên, email liên hệ, trạng thái và ngày nộp.
- **Bước 2: Xây dựng Repository**
  - Viết `OrganizationRepository.submitRequest(...)` thực thi lệnh lưu và gọi `dbo.sp_organization_submit_for_approval`.
  - Viết câu truy vấn `findByRequesterId(Long userId)`.
- **Bước 3: Xây dựng `OrganizationRequestService`**
  - Phương thức `createRequest(Long actorId, OrganizationRequestCreateDto dto)`:
    - Kiểm tra xem user có đang bị `SUSPENDED` không.
    - Kiểm tra xem user đã có request nào đang `PENDING_APPROVAL` chưa.
    - Thực hiện lưu và trả về DTO chi tiết.
  - Phương thức `getMyRequests(Long actorId)`: Trả về danh sách tóm tắt.
  - Phương thức `getRequestDetail(Long actorId, Long orgId)`: Đối chiếu quyền xem, nếu không phải requester và không phải ADMIN thì ném `AccessDeniedException`.
- **Bước 4: Xây dựng Servlet**
  - `OrganizationRequestServlet`:
    - `doPost`: Kiểm tra CSRF, parse JSON, gọi service, trả HTTP 201 Created.
    - `doGet`: Điều hướng đến danh sách yêu cầu cá nhân hoặc chi tiết.
- **Bước 5: Xây dựng Giao diện JSP (UI-09)**
  - Tích hợp vào thư mục `WEB-INF/views/organization/request/`.
  - Hiển thị badge màu sắc cho các trạng thái: Vàng (Đang chờ duyệt), Xanh (Đã duyệt), Đỏ (Bị từ chối).
- **Bước 6: Viết Test tự động**
  - Chạy `mvn -B test` để bảo đảm không có hồi quy và pass toàn bộ kịch bản.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu kiểm tra | Kết quả mong đợi |
|---|---|---|---|
| ORG-01 | Gửi yêu cầu hợp lệ | Dữ liệu hợp lệ, đã đăng nhập CUSTOMER | 201 Created, DB có bản ghi PENDING_APPROVAL |
| ORG-02 | Chưa đăng nhập gửi yêu cầu | Không có Session | 401 Unauthorized |
| ORG-03 | Email liên hệ sai format | email="not-an-email" | 400 Bad Request kèm chi tiết lỗi validation |
| ORG-04 | User A xem yêu cầu của User B | User A truy cập `/api/organization-requests/99` của B | 403 Forbidden |
| ORG-05 | Hiển thị lý do khi bị từ chối | Request đã bị REJECTED | Trả về chuỗi `rejectedReason` để giao diện hiển thị |
