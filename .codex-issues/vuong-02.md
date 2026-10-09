> **Convention:** `dev` → `feat/vuong/vuong-02-commission-rules` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Chỉ sửa code quản lý chính sách hoa hồng trong `settlement/commission/**`, JSP quản trị hoa hồng (UI-22) và test tương ứng. Không can thiệp vào quy trình duyệt tổ chức của Thái (Thái chỉ gửi `initialPolicy` khi approve).

## 1. Mục tiêu và Nghiệp vụ

Xây dựng module Quản trị viên hệ thống Quản lý Chính sách Phí Hoa hồng Nền tảng (Platform Commission Rules):
- Phục vụ trực tiếp màn hình **UI-22 (Admin Commission Policy & Rule Management)**.
- Phân quyền: Chỉ tài khoản có `PlatformRole.ADMIN` mới có quyền xem và cấu hình chính sách phí.
- Cơ cấu phí hoa hồng nền tảng:
  - **Tỷ lệ phần trăm (`ratePercent`):** Từ 0.0000% đến 100.0000% (ví dụ: 5.0000% trên doanh thu vé).
  - **Phí cố định mỗi đơn hàng (`fixedFee`):** Số tiền cố định thu trên mỗi đơn hàng thành công (ví dụ: 10,000 VND).
  - **Khoảng thời gian hiệu lực (`effectiveFrom` đến `effectiveTo`):** Cho phép áp dụng các mức phí khác nhau theo từng giai đoạn kinh doanh.
- **Ràng buộc Bất biến Tuyệt đối (Immutability Invariant):**
  - **Một `CommissionRule` đã được gắn vào bất kỳ Sự kiện nào (`dbo.tc_events.commission_rule_id = rule.id`) thì TUYỆT ĐỐI KHÔNG ĐƯỢC PHÉP CHỈNH SỬA HOẶC XÓA BỎ**.
  - Muốn thay đổi tỷ lệ phí của một tổ chức: Bắt buộc Quản trị viên phải tạo một bản ghi `CommissionRule` MỚI với thời gian hiệu lực mới. Các sự kiện cũ đã xuất bản vẫn giữ nguyên chính sách phí ban đầu mà họ đã đồng ý khi ký hợp đồng.

## 2. Đặc tả Endpoint & Hợp đồng API

### 2.1. GET `/api/admin/organizations/{organizationId}/commission-rules`
- **Mục đích:** Admin xem lịch sử toàn bộ các chính sách hoa hồng của một tổ chức (bao gồm đang áp dụng, hết hạn, và sắp tới).
- **Quyền hạn:** `PlatformRole.ADMIN`.
- **Response Body (200 OK):**
```json
[
  {
    "ruleId": 120,
    "organizationId": 45,
    "ratePercent": "5.0000",
    "fixedFee": "10000.00",
    "effectiveFrom": "2026-10-01T00:00:00Z",
    "effectiveTo": "2027-10-01T00:00:00Z",
    "isEffectiveNow": true,
    "associatedEventsCount": 3
  }
]
```

### 2.2. POST `/api/admin/organizations/{organizationId}/commission-rules`
- **Mục đích:** Admin thiết lập chính sách hoa hồng mới cho tổ chức.
- **Headers:** `X-CSRF-Token: <token>`, `Idempotency-Key: <uuid>`, `Content-Type: application/json`.
- **Request Body:**
```json
{
  "ratePercent": "4.5000",
  "fixedFee": "8000.00",
  "effectiveFrom": "2027-01-01T00:00:00Z",
  "effectiveTo": "2028-01-01T00:00:00Z"
}
```
- **Validation Rules:**
  - `ratePercent`: `BigDecimal` từ 0.0000 đến 100.0000.
  - `fixedFee`: `BigDecimal >= 0`, không có phần lẻ xu.
  - `effectiveFrom < effectiveTo`.
- **Response Body (201 Created):** Trả về thông tin `CommissionRule` vừa tạo.

### 2.3. POST `/api/admin/commission-rules/{ruleId}/edit`
- **Mục đích:** Chỉnh sửa thông tin chính sách.
- **Quy tắc bảo vệ:** Nếu `associatedEventsCount > 0` (đã có sự kiện sử dụng), ném lỗi `409 Conflict` (`{"error": "RULE_ALREADY_ATTACHED_TO_EVENTS"}`).

---

## 3. Kiến trúc Kỹ thuật Chuyên sâu

### 3.1. Tính toán Phí Hoa hồng qua Function Canonical
- Tích hợp các hàm Function trong `database/functions.sql`:
  - `dbo.fn_commission_rule_is_effective_at(@rule_id, @now)`:
    Kiểm tra `@now >= effective_from AND @now < effective_to`.
  - `dbo.fn_commission_rule_calculate_fee(@rule_id, @remaining_amount)`:
    Công thức tính phí: `(@remaining_amount * rate_percent / 100.0) + fixed_fee`.

### 3.2. Cơ chế Bảo vệ Bất biến tại Tầng Database & Service
- Khi có yêu cầu sửa/xóa rule:
  ```sql
  SELECT COUNT(*) FROM dbo.tc_events WHERE commission_rule_id = :ruleId;
  ```
  Nếu kết quả > 0: Từ chối ngay lập tức!
- Mọi thao tác thêm/sửa được thực thi trong Transaction độc lập, ghi nhật ký vào `dbo.tc_audit_logs`.

### 3.3. Cache-Aside với Redis
- Redis Key: `tc:commission:rules:v1:<organizationId>` (TTL: 300s / 5 phút).
- Khi Admin tạo rule mới hoặc khi Thái phê duyệt tổ chức mới (`initialPolicy`): Tự động Invalidate key này để danh sách luôn cập nhật.

---

## 4. Đầu ra Mong muốn (Expected Deliverables)

1. **Mã nguồn Backend:**
   - DTOs: `CommissionRuleCreateDto`, `CommissionRuleDto`.
   - Repositories: `CommissionRuleRepository` (truy vấn qua `dbo.v_commission_rules`, kiểm tra liên kết sự kiện).
   - Core Services: `CommissionRuleService`.
   - Servlets: `AdminCommissionRulesServlet`.
2. **Giao diện JSP:**
   - `src/main/webapp/WEB-INF/views/admin/commission/rules.jsp`: Màn hình UI-22 hiển thị bảng các chính sách phí theo tổ chức, badge "Đang áp dụng", nút "Tạo chính sách mới", cảnh báo "Không thể sửa vì đã có sự kiện áp dụng".
3. **Database Changes:**
   - Bảng `dbo.tc_commission_rules` thêm bản ghi chính sách mới.
4. **Kiểm thử tự động:**
   - `CommissionRuleServiceTest`: Kiểm tra tỷ lệ âm bị chặn, thời gian bắt đầu sau thời gian kết thúc bị chặn, sửa rule đã gắn sự kiện bị 409 Conflict.

---

## 5. Hướng dẫn Triển khai Step-by-Step

- **Bước 1: Thiết lập DTOs và Validation Rules**
  - Khai báo các ràng buộc `BigDecimal` cho `ratePercent` và `fixedFee`.
- **Bước 2: Xây dựng `CommissionRuleRepository`**
  - Viết câu lệnh đếm sự kiện liên kết và lưu rule mới.
- **Bước 3: Xây dựng `CommissionRuleService`**
  - Kiểm tra quyền `PlatformRole.ADMIN`.
  - Triển khai logic bảo vệ tính bất biến của rule.
- **Bước 4: Xây dựng Servlets**
  - Định tuyến các endpoint GET danh sách và POST thêm mới.
- **Bước 5: Xây dựng Giao diện JSP (UI-22)**
  - Tích hợp giao diện quản trị phí hoa hồng.
- **Bước 6: Viết kiểm thử tự động**
  - Chạy `mvn -B test` bảo đảm 100% xanh.

---

## 6. Kịch bản Kiểm thử & Tiêu chuẩn Nghiệm thu

| ID | Tình huống kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi |
|---|---|---|---|
| COM-01 | Tạo chính sách hoa hồng hợp lệ | Admin đăng nhập, rate=5%, fixedFee=10k | 201 Created, DB lưu rule mới, cache bị xóa |
| COM-02 | Tỷ lệ hoa hồng âm hoặc > 100% | ratePercent = -2.5% hoặc 120% | 400 Bad Request |
| COM-03 | Sửa rule đã có sự kiện sử dụng | ruleId đã được liên kết với Event 201 | 409 Conflict ("Chính sách đã áp dụng cho sự kiện không thể sửa đổi") |
| COM-04 | Non-admin gọi API cấu hình phí | Customer hoặc Manager gọi API | 403 Forbidden |
