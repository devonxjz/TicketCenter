> **Convention:** `dev` → `feat/vuong/vuong-01-swagger-redis-contract` → PR `dev`; chỉ merge khi GitHub Actions xanh và có review.
> **Ownership:** Vương là người duy nhất sở hữu và chịu trách nhiệm chỉnh sửa `src/main/resources/openapi.json`, `src/main/webapp/swagger/**` và tài liệu hợp đồng Redis Key Registry toàn dự án. Không tự ý sửa code implementation tính năng của người khác.

## 1. Mục tiêu và Nghiệp vụ

Xây dựng cổng tài liệu API tương tác trực quan qua Swagger UI (`/swagger/`) và Chuẩn hóa Bộ quy tắc Hợp đồng Khóa (Locking Contract) cho Redis và OpenAPI:
- Cung cấp công cụ kiểm thử thủ công trực tiếp (Manual Testing Tool) trên trình duyệt cho cả 5 thành viên trong nhóm và giảng viên/người chấm điểm.
- Khóa toàn bộ các cấu trúc dữ liệu, tiêu chuẩn Envelope JSON lỗi/thành công, định dạng tham số ngày tháng ISO-8601 (`Instant`), tiền tệ (`BigDecimal`), mã định danh UUID.
- Thiết lập **Sổ đăng ký Khóa Redis (Redis Key Registry)** để đảm bảo không có bất kỳ xung đột nào giữa các miền (Namespace Isolation): Phân tách rạch ròi giữa Cache đọc và Lưu trữ Idempotency.
- Đảm bảo an toàn thông tin: Không bao giờ để lộ mật khẩu, mã OTP, secret key hay dữ liệu nhạy cảm trong tài liệu Swagger hoặc log hệ thống.

## 2. Đặc tả Cổng Swagger & Cấu hình OpenAPI 3.0

### 2.1. Cấu hình Đường dẫn Swagger UI
- Đường dẫn truy cập giao diện: `GET http://localhost:8080/ticketscenter/swagger/index.html` (hoặc `/swagger/`).
- File đặc tả nguồn: `GET /api/openapi.json` (phục vụ nội dung file `src/main/resources/openapi.json`).
- Thư viện: Nhúng tĩnh bộ Swagger UI (Static HTML/CSS/JS) trong `src/main/webapp/swagger/` hoặc thông qua CDN được kiểm duyệt, không thêm dependency nặng vào `pom.xml`.

### 2.2. Khai báo Cơ chế Xác thực trên Swagger UI (Security Schemes)
- Khai báo 2 Security Scheme chuẩn mực:
  1. `CookieAuth`: Loại `apiKey`, `in: cookie`, `name: JSESSIONID`.
  2. `CsrfAuth`: Loại `apiKey`, `in: header`, `name: X-CSRF-Token`.
  3. `IdempotencyAuth`: Loại `apiKey`, `in: header`, `name: Idempotency-Key`.
- Cho phép người test thực hiện đăng nhập tại `/api/auth/login`, Swagger UI tự động lưu Cookie và gắn Header `X-CSRF-Token` cho các lệnh POST/PUT/DELETE tiếp theo.

### 2.3. Cấu trúc Envelope Lỗi Chuẩn hóa Toàn Hệ thống
```json
{
  "timestamp": "2026-10-09T18:50:00Z",
  "status": 400,
  "errorCode": "INVALID_ARGUMENT",
  "message": "Dữ liệu đầu vào không hợp lệ.",
  "errors": [
    {
      "field": "contactEmail",
      "rejectedValue": "invalid-email",
      "message": "Địa chỉ email không đúng định dạng."
    }
  ]
}
```

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Sổ Đăng ký Khóa Redis Toàn Dự án (Redis Key Registry)
- **Quy tắc đặt tên khóa thống nhất:** `tc:<domain>:<purpose>:v<version>:<scope/id>`
- Bảng phân chia Namespace độc quyền:
  | Tiền tố Khóa | Chủ sở hữu | Mục đích & Dữ liệu lưu | TTL |
  |---|---|---|---|
  | `tc:user:auth-version:<userId>` | Thái (Identity) | Lưu authVersion kiểm tra stale session | 1 ngày |
  | `tc:event:list:v1:<hash>` | Đông (Event) | JSON danh sách sự kiện catalog công khai | 60 giây |
  | `tc:event:detail:v1:<eventId>` | Đông (Event) | JSON chi tiết sự kiện | 120 giây |
  | `tc:event:zones:v1:<eventId>` | Đông (Event) | JSON danh sách Zone và giá | 60 giây |
  | `tc:zone:seats:v1:<zoneId>` | Đông (Event) | JSON sơ đồ ghế ngồi | 30 giây |
  | `tc:idem:hold:<actorId>:<key>` | Liêm (Ticketing) | Request hash, Hold status, response | 600 giây |
  | `tc:user:active-hold:<userId>` | Liêm (Ticketing) | holdId đang hoạt động của buyer | 600 giây |
  | `tc:idem:vnpay:ipn:<txnRef>` | Liêm (Payment) | Dedupe gói tin IPN từ VNPAY | 7 ngày |
  | `tc:ticket:qr:v1:<ticketId>:<v>`| Liêm (Ticket) | Mảng byte PNG ảnh mã QR | 3600 giây |
  | `tc:checkin:window:v1:<eventId>`| Khánh (Check-in) | Trạng thái đóng/mở cửa soát vé | 30 giây |
  | `tc:lease:refund:<refundId>` | Khánh (Refund) | Distributed lease cho Refund Worker | 120 giây |
  | `tc:idem:refund-attempt:<id>` | Khánh (Refund) | Dedupe lượt gọi chuyển tiền | 24 giờ |
  | `tc:commission:rules:v1:<orgId>`| Vương (Finance) | Cache danh sách chính sách phí | 300 giây |
  | `tc:settlement:summary:v1:<id>` | Vương (Finance) | Snapshot tổng kết tài chính | 60 giây |
  | `tc:admin:overview:v1` | Vương (Report) | Số liệu tổng quan Dashboard Admin | 60 giây |

### 3.2. Quy tắc Bắt buộc về Idempotency trên Redis
- Mọi mutation có header `Idempotency-Key` phải đối chiếu mã băm `payloadHash = sha256(requestBody)`.
- Replay cùng key và cùng hash: Trả lại response cũ (`200`/`201`).
- Replay cùng key nhưng khác hash: Bắt buộc trả về `409 Conflict`.
- Không bao giờ lưu trữ mật khẩu, OTP, cookie hay raw QR vào Redis.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend & Tài nguyên:**
   - Swagger Static Files: `src/main/webapp/swagger/index.html`, `swagger-initializer.js`, CSS/JS.
   - OpenAPI Specification: `src/main/resources/openapi.json` chứa tài liệu của toàn bộ 34 endpoint trong hệ thống.
   - Servlets: `OpenApiJsonServlet` (phục vụ nội dung file `openapi.json` với header `Content-Type: application/json; charset=UTF-8`).
   - Key Registry Documentation: Bảng đăng ký Redis Key đặt tại `docs/references/REDIS-REGISTRY.md` hoặc tích hợp trong issue này.
2. **Kiểm thử tự động:**
   - `OpenApiContractTest`: Kiểm tra cú pháp của `openapi.json`, bảo đảm không có duplicate path, không có duplicate operationId, tất cả schema model đều được khai báo đầy đủ.
   - `SwaggerUiAvailabilityTest`: Kiểm tra endpoint `/swagger/index.html` và `/api/openapi.json` trả về HTTP 200 OK.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập thư mục Swagger UI**
  - Tải bộ static asset Swagger UI bản nhẹ (hoặc nạp qua CDN unpkg/cdnjs).
  - Cấu hình `url: "../api/openapi.json"` trong `swagger-initializer.js`.
- **Bước 2: Xây dựng `OpenApiJsonServlet`**
  - Đọc tệp `/resources/openapi.json` qua `ClassLoader.getResourceAsStream`.
  - Cache nội dung vào bộ nhớ và xuất ra response.
- **Bước 3: Thu thập và Tích hợp `openapi.json`**
  - Nhận các endpoint, schema, example từ Thái, Đông, Liêm, Khánh.
  - Hợp nhất vào một file duy nhất, chuẩn hóa thẻ tags (`Identity`, `Event`, `Ticketing`, `Payment`, `Check-in`, `Refund`, `Settlement`, `Report`).
- **Bước 4: Viết `OpenApiContractTest`**
  - Sử dụng Jackson ObjectMapper để parse `openapi.json` và kiểm tra tính toàn vẹn cú pháp.
- **Bước 5: Chạy kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| SWG-01 | Truy cập Swagger UI | GET `/swagger/index.html` | 200 OK, hiển thị đầy đủ giao diện Swagger |
| SWG-02 | Tải tệp OpenAPI JSON | GET `/api/openapi.json` | 200 OK, JSON hợp lệ, chứa các security schemes |
| SWG-03 | Thực hiện gọi API qua Swagger | Đăng nhập -> Lấy CSRF -> Gọi GET `/api/events` | 200 OK, hiển thị response JSON đúng schema |
| SWG-04 | Kiểm tra trùng lặp Path | Chạy unit test kiểm tra toàn bộ openapi.json | 0 đường dẫn bị trùng lặp |
| SWG-05 | Kiểm tra rò rỉ Secret trong Example | Rà soát các example trong openapi.json | Không chứa password, OTP, secret key |
