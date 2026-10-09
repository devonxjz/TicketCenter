> **Convention:** `dev` → `feat/vuong/vuong-05-reports-csv-cache` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa code báo cáo, xuất file CSV an toàn, DTO và cache trong `report/**`, JSP báo cáo (UI-17 và UI-23) và test tương ứng. Tuyệt đối không can thiệp vào repository nghiệp vụ ghi của người khác.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng hệ thống Báo cáo Doanh thu Đa chiều (Multi-dimensional Reporting) và Xuất dữ liệu tệp CSV An toàn Chống Tấn công Mã độc Công thức (Safe CSV Export):
- **Phía Ban tổ chức (UI-17 - Báo cáo Doanh thu & Bán vé của Tổ chức):**
  - Quản lý (`MANAGER`) xem số lượng vé bán ra, tổng tiền thu về, số vé đã soát vé tại cửa, số tiền đã hoàn trả và doanh thu thực nhận theo từng sự kiện của tổ chức mình.
- **Phía Quản trị viên hệ thống (UI-23 - Báo cáo Toàn sàn & Phí Nền tảng):**
  - Quản trị viên (`ADMIN`) xem tổng doanh thu toàn hệ thống, tổng phí hoa hồng nền tảng đã thu, các sự kiện có doanh thu cao nhất.
- **Tính năng Xuất File Báo cáo CSV (`CSV Export`):**
  - Cho phép tải tệp CSV về máy tính để mở trên Microsoft Excel hoặc Google Sheets.
  - **Phòng chống Tấn công Tiêm nhiễm Công thức (CSV / Formula Injection Prevention):** Tự động trung hòa (Neutralize) các ô dữ liệu có nguy cơ kích hoạt lệnh thực thi trong Excel (bắt đầu bằng `=`, `+`, `-`, `@`).
  - **Xử lý Bộ nhớ Đệm Dòng (Streaming Response):** Không bao giờ nạp toàn bộ hàng chục nghìn bản ghi vào RAM gây tràn bộ nhớ (OutOfMemoryError), sử dụng luồng ghi trực tiếp (Stream directly) vào HTTP Response.
  - **Bảo toàn Tiếng Việt có dấu:** Gắn tiền tố UTF-8 Byte Order Mark (`\uFEFF`) để Excel tự động nhận diện font Unicode Tiếng Việt chuẩn.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/organizations/{organizationId}/reports`
- **Mục đích:** Ban tổ chức xem báo cáo số liệu các sự kiện của mình.
- **Quyền hạn:** `MANAGER` của chính `organizationId`.
- **Query Params:** `from`, `to`, `eventId` (tùy chọn).
- **Response Body (200 OK):**
```json
{
  "organizationId": 45,
  "summary": {
    "totalEvents": 3,
    "totalTicketsSold": 1250,
    "grossRevenue": "1250000000.00",
    "totalRefunded": "25000000.00",
    "platformCommission": "61250000.00",
    "netPayable": "1163750000.00"
  },
  "eventReports": [
    {
      "eventId": 201,
      "eventTitle": "Đêm Nhạc Mùa Thu 2026",
      "ticketsSold": 580,
      "ticketsCheckedIn": 540,
      "grossRevenue": "580000000.00",
      "status": "COMPLETED"
    }
  ]
}
```

### 2.2. GET `/api/admin/reports`
- **Mục đích:** Admin xem báo cáo tài chính toàn sàn.
- **Quyền hạn:** `PlatformRole.ADMIN`.

### 2.3. GET `/api/reports/export`
- **Mục đích:** Tải xuống tệp báo cáo dạng CSV.
- **Query Params:** `scope` (`ORGANIZATION` hoặc `PLATFORM`), `organizationId`, `from`, `to`, `format=csv`.
- **Response Headers:**
  - `Content-Type: text/csv; charset=UTF-8`
  - `Content-Disposition: attachment; filename="ticketscenter-report-20261009.csv"`

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Phân biệt Rạch ròi các Đại lượng Tài chính
- Khi truy vấn từ View `dbo.v_event_financials` và `dbo.v_orders`:
  - **Tiền thu thực tế (`Cash Collected`):** Tổng tiền thanh toán thành công từ cổng VNPAY.
  - **Doanh thu bán vé (`Ticket Revenue`):** Tổng giá trị vé được bán ra hợp lệ.
  - **Tiền bồi hoàn (`Compensation Refund`):** Khoản tiền hoàn lại do sự cố kỹ thuật hoặc thanh toán muộn. **Tuyệt đối không cộng vào doanh thu bán vé** để tránh làm sai lệch báo cáo thuế.

### 3.2. Thuật toán Phòng chống CSV Injection & Xuất Dữ liệu Dòng
- **Nguyên lý tấn công CSV Injection (DDE):** Nếu kẻ tấn công đặt tên sự kiện hoặc tên người dùng là `=cmd|' /C calc'!A0`, khi Admin mở tệp CSV bằng Excel, máy tính có thể bị thực thi mã độc tùy ý.
- **Thuật toán Khử độc Tế bào (Cell Sanitization):**
  ```java
  public static String escapeCsvCell(String value) {
      if (value == null) return "";
      String trimmed = value.trim();
      // Nếu bắt đầu bằng ký tự điều khiển công thức:
      if (trimmed.startsWith("=") || trimmed.startsWith("+") || 
          trimmed.startsWith("-") || trimmed.startsWith("@") || 
          trimmed.startsWith("\t") || trimmed.startsWith("\r")) {
          // Thêm dấu nháy đơn ở đầu để Excel hiểu là văn bản thuần
          trimmed = "'" + trimmed;
      }
      // Thoát dấu ngoặc kép theo chuẩn RFC 4180
      if (trimmed.contains("\"") || trimmed.contains(",") || trimmed.contains("\n")) {
          trimmed = "\"" + trimmed.replace("\"", "\"\"") + "\"";
      }
      return trimmed;
  }
  ```
- **Kỹ thuật Streaming Response với UTF-8 BOM:**
  ```java
  response.setContentType("text/csv; charset=UTF-8");
  OutputStream os = response.getOutputStream();
  // Ghi 3 bytes UTF-8 BOM để Excel tự động nhận diện tiếng Việt
  os.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});
  PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
  // Duyệt và flush từng dòng một
  ```

### 3.3. Cache Báo cáo trên Redis với Phân cách Scope
- Redis Key: `tc:report:v1:<scope>:<filterHash>` (TTL: 60 giây).
- `<scope>` phân tách giữa `admin` và `org_<orgId>`.
- Invalidate khi có giao dịch thanh toán hoặc quyết toán hoàn tất.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `OrganizationReportDto`, `PlatformReportDto`, `EventFinancialMetricDto`.
   - Safe CSV Utilities: `CsvSanitizer`, `StreamingCsvExporter`.
   - Repositories: `ReportRepository` (truy vấn tổng hợp từ `dbo.v_event_financials`, `dbo.v_orders`, `dbo.v_refunds`).
   - Core Services: `ReportService`.
   - Servlets: `OrgReportsServlet`, `AdminReportsServlet`, `ReportExportServlet`.
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/report/org-report.jsp`: Màn hình UI-17 cho Ban tổ chức (biểu đồ thống kê tóm tắt, bảng doanh thu từng sự kiện, nút "Xuất CSV").
   - `src/main/webapp/WEB-INF/views/report/admin-report.jsp`: Màn hình UI-23 cho Admin hệ thống (tổng doanh thu sàn, tổng hoa hồng nền tảng, xuất báo cáo toàn diện).
3. **Kiểm thử tự động:**
   - `CsvSanitizerTest`: Kiểm tra chuỗi chứa `=SUM(A1:A10)` được trung hòa an toàn thành `'=SUM(A1:A10)`.
   - `ReportMultiTenancyTest`: Quản lý của Tổ chức A không thể xem báo cáo hoặc xuất dữ liệu của Tổ chức B.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Triển khai `CsvSanitizer`**
  - Viết bộ lọc ký tự điều khiển Excel và thoát dấu ngoặc kép.
- **Bước 2: Xây dựng `StreamingCsvExporter`**
  - Ghi header BOM UTF-8, định dạng các cột số tiền và ngày tháng.
- **Bước 3: Triển khai `ReportRepository`**
  - Viết các truy vấn tổng hợp dữ liệu (Aggregation SQL) sử dụng View có sẵn.
- **Bước 4: Xây dựng `ReportService`**
  - Kiểm tra quyền truy cập (Manager vs Admin).
  - Tích hợp đệm bộ nhớ Redis.
- **Bước 5: Xây dựng Servlets**
  - Định tuyến các endpoint báo cáo JSON và xuất tệp CSV.
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| RPT-01 | Manager xem báo cáo tổ chức | Manager đăng nhập đúng org | 200 OK, trả về đúng số liệu của tổ chức |
| RPT-02 | Manager xem trộm báo cáo tổ chức khác | Actor gọi report của org khác | 403 Forbidden |
| RPT-03 | Xuất CSV phòng chống Formula Injection | Tên sự kiện = "=2+5*cmd" | Tệp CSV chứa `'=2+5*cmd`, mở trong Excel không chạy lệnh |
| RPT-04 | Kiểm tra hiển thị Tiếng Việt trong CSV | Tên sự kiện có dấu tiếng Việt | Tệp có UTF-8 BOM, mở trên Excel không bị lỗi font chữ (mojibake) |
| RPT-05 | Xuất CSV dữ liệu lớn | Sự kiện có 10,000 đơn hàng | Hệ thống stream mượt mà, RAM không bị spike đột ngột |
