> **Convention:** `dev` → `feat/vuong/vuong-06-admin-audit-integration` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Vương sở hữu toàn bộ màn hình Dashboard Admin (UI-18), module Tra cứu Nhật ký Kiểm toán (Audit Logs trong UI-23), kiểm thử tích hợp toàn sàn (Final Integration Test) và xác thực catalog cơ sở dữ liệu. Không tự ý sửa code nghiệp vụ nội bộ của các thành viên khác.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng Bảng điều khiển Quản trị viên Trung tâm (Admin Overview Dashboard), Cổng Tra cứu Nhật ký Kiểm toán Hệ thống (Audit Log Explorer) và Tiến hành Tích hợp Tổng thể Toàn Dự án:
- Phục vụ trực tiếp màn hình **UI-18 (Admin Central Dashboard)** và phân hệ Kiểm toán trong **UI-23 (System Audit Trail)**.
- **Bảng điều khiển Trung tâm (Admin Dashboard):**
  - Hiển thị các chỉ số đo lường hiệu quả then chốt (KPIs) thời gian thực: Tổng số người dùng, số tổ chức đang hoạt động, số yêu cầu tạo tổ chức đang chờ duyệt (Thái), số sự kiện đang chờ xuất bản (Đông), số yêu cầu hoàn tiền đang chờ quyết định (Khánh), tổng doanh thu và phí hoa hồng sàn thu được hôm nay (Vương).
- **Hệ thống Nhật ký Kiểm toán (Immutable Audit Logs):**
  - Bảng nhật ký `dbo.tc_audit_logs` là **bất biến và chỉ thêm (Append-only)**. Không một ai (kể cả Admin) được quyền xóa hay sửa các bản ghi lịch sử này.
  - Ghi nhận đầy đủ: Ai (`actorId`), làm gì (`action`), lên đối tượng nào (`targetTable`, `targetId`), vào lúc nào (`timestamp`), chi tiết thay đổi (`payloadSummary`).
  - **Khử độc Dữ liệu Nhạy cảm (Redaction Guarantee):** Tuyệt đối không bao giờ ghi mật khẩu, mã OTP, chữ ký bí mật, session token hay chuỗi QR nhạy cảm vào trường chi tiết của audit log.
- **Cổng Nghiệm thu Tích hợp Toàn diện (Final Integration Gate):**
  - Đảm bảo toàn bộ 34 issue của cả 5 thành viên (Thái, Đông, Liêm, Khánh, Vương) ghép nối đồng bộ, tài liệu Swagger OpenAPI khớp 100% với endpoint thực tế, và bộ kiểm thử `mvn -B test` xanh tuyệt đối.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/admin/overview`
- **Mục đích:** Admin xem số liệu tổng quan toàn hệ thống.
- **Quyền hạn:** `PlatformRole.ADMIN`.
- **Response Body (200 OK):**
```json
{
  "totalUsers": 1520,
  "activeOrganizations": 42,
  "pendingOrganizationRequests": 3,
  "pendingEventsCount": 5,
  "pendingRefundsCount": 2,
  "todayGrossRevenue": "85000000.00",
  "todayPlatformCommission": "4250000.00",
  "systemStatus": "HEALTHY"
}
```

### 2.2. GET `/api/admin/audit-logs`
- **Mục đích:** Tra cứu lịch sử kiểm toán các thao tác nhạy cảm trong hệ thống.
- **Query Params:** `actorId`, `targetTable`, `action`, `from`, `to`, `page`, `pageSize`.
- **Response Body (200 OK):**
```json
{
  "items": [
    {
      "auditId": 80001,
      "actorId": 1,
      "actorName": "Admin System",
      "action": "APPROVE_ORGANIZATION",
      "targetTable": "tc_organizations",
      "targetId": 45,
      "timestamp": "2026-10-09T18:00:00Z",
      "details": "Đã phê duyệt tổ chức Công ty Âm nhạc Trẻ VN kèm tỷ lệ phí 5.0%."
    }
  ],
  "total": 120,
  "page": 1,
  "pageSize": 20
}
```

### 2.3. GET `/api/admin/audit-logs/{auditId}`
- **Mục đích:** Xem chi tiết đầy đủ của một bản ghi kiểm toán cụ thể.

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Tính bất biến của Nhật ký Kiểm toán trong SQL Server
- Bảng `dbo.tc_audit_logs` được bảo vệ:
  - Phân quyền SQL Login chỉ cấp quyền `INSERT` và `SELECT`, thu hồi quyền `UPDATE` và `DELETE`.
  - Có Trigger chặn mọi thao tác sửa đổi hoặc xóa bản ghi đã tồn tại.
- Truy vấn phân trang ổn định sử dụng `ORDER BY timestamp DESC, id DESC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY`.

### 3.2. Cơ chế Đệm Dashboard trên Redis
- Redis Key: `tc:admin:overview:v1` (TTL: 60 giây).
- Đọc cache nhanh để tải trang Admin tức thì. Nếu Redis gặp sự cố, tự động fallback truy vấn tổng hợp từ các bảng DB.

### 3.3. Quy trình Kiểm định Toàn sàn (Platform Verification Gate)
- Kiểm tra tính tương thích giữa các phân hệ:
  1. Luồng Xác thực & Tổ chức: Thái (THAI-01 -> THAI-06).
  2. Luồng Danh mục & Sự kiện: Đông (DONG-01 -> DONG-05).
  3. Luồng Giữ vé, Đơn hàng, VNPAY, Phát hành vé: Liêm (LIEM-01 -> LIEM-06).
  4. Luồng Soát vé, Hoàn tiền, Hủy sự kiện: Khánh (KHANH-01 -> KHANH-06).
  5. Luồng Quyết toán, Báo cáo, Audit: Vương (VUONG-01 -> VUONG-06).

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `AdminOverviewDto`, `AuditLogSummaryDto`, `AuditLogDetailDto`.
   - Repositories: `AdminOverviewRepository` (truy vấn KPI tổng hợp), `AuditLogRepository` (truy vấn bảng `dbo.tc_audit_logs`).
   - Core Services: `AdminOverviewService`, `AuditLogService`.
   - Servlets: `AdminOverviewServlet` (`/api/admin/overview`), `AdminAuditLogsServlet` (`/api/admin/audit-logs/*`).
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/admin/dashboard.jsp`: Màn hình UI-18 với các card KPI nổi bật (kèm icon), biểu đồ doanh thu theo ngày, danh sách lối tắt đến các mục chờ duyệt.
   - `src/main/webapp/WEB-INF/views/admin/audit/audit-list.jsp`: Màn hình xem nhật ký kiểm toán với các bộ lọc thông minh, modal xem chi tiết.
3. **Kiểm thử tự động & Tích hợp:**
   - `AdminOverviewServiceTest`: Kiểm tra tổng hợp số liệu chính xác.
   - `AuditLogSanitizationTest`: Bảo đảm không có bất kỳ mật khẩu hoặc secret nào bị ghi vào audit log.
   - `PlatformEndToEndSmokeTest`: Chạy một lượt toàn bộ các kịch bản chính từ Đăng ký -> Tạo Event -> Giữ Vé -> Mua Vé -> Soát Vé -> Quyết Toán.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs cho Dashboard và Audit**
  - Khai báo các đối tượng tóm tắt và chi tiết.
- **Bước 2: Triển khai `AuditLogRepository` & `AdminOverviewRepository`**
  - Viết câu truy vấn đếm và tổng hợp dữ liệu từ các View và bảng canonical.
- **Bước 3: Xây dựng `AuditLogService` và `AdminOverviewService`**
  - Tích hợp kiểm tra quyền `PlatformRole.ADMIN`.
  - Tích hợp đệm bộ nhớ Redis cho Overview KPI.
- **Bước 4: Xây dựng Servlets**
  - Định tuyến các endpoint `/api/admin/overview` và `/api/admin/audit-logs`.
- **Bước 5: Xây dựng Giao diện JSP (UI-18 và phân hệ UI-23)**
  - Tối ưu giao diện quản trị Admin chuyên nghiệp.
- **Bước 6: Chạy kiểm thử tự động toàn dự án**
  - Thực thi lệnh `mvn -B test` đảm bảo toàn bộ hệ thống đạt 100% test pass.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| INT-01 | Xem Dashboard Admin | Admin đăng nhập hợp lệ | 200 OK, hiển thị đầy đủ các KPI tổng quan |
| INT-02 | Non-admin truy cập Dashboard | Customer hoặc Manager truy cập | 403 Forbidden |
| INT-03 | Lọc nhật ký kiểm toán | Lọc theo action = "APPROVE_ORGANIZATION" | 200 OK, chỉ trả về các log phê duyệt tổ chức |
| INT-04 | Kiểm tra bất biến của Audit Log | Cố tình gọi lệnh UPDATE / DELETE log | DB trigger hoặc permission chặn lại với lỗi phân quyền |
| INT-05 | Kiểm định toàn sàn (Smoke Test) | Chạy kịch bản tích hợp liên hoàn 5 miền | Tất cả các luồng hoạt động nhịp nhàng, 100% test xanh |
