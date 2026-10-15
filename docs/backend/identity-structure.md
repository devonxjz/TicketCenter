# Cấu trúc package `identity`

Tài liệu mô tả sau đợt refactor `refactor(identity): …`. Hành vi bên ngoài (URL, HTTP status, `error.code`,
thông báo tiếng Việt, JSON shape) **không đổi**; DB và migration không đổi.

## Luồng và trách nhiệm

| Lớp | Việc duy nhất |
|---|---|
| `config/IdentityServices` | Composition root: dựng mọi service một lần, gắn vào `ServletContext`. Servlet/filter lấy bằng `IdentityServices.from(context)`; `IdentityServices.sessionValidator(context)` cho nơi ngoài `identity`. |
| `config/IdentityContextListener` | Cấu hình cookie phiên (HttpOnly, Secure ở production) và khởi động sớm `IdentityServices`. |
| `service/RegistrationService` | Đăng ký tài khoản. |
| `service/LoginService` | Xác thực (email → username → alias `admin`), rehash, dựng `UserPrincipal`. |
| `service/SessionValidator` + `UserSessionCache` | `isSessionValid` / `revokeAllSessions`; cache Redis `auth:user:{id}` (get/put/evict, lỗi Redis → fallback SQL). |
| `service/ProfileService` | Hồ sơ cá nhân và người dùng hiện tại. |
| `service/OtpService` | Điều phối gửi/xác minh OTP. `OtpHasher` (sinh + băm HMAC), `ResetTokenCodec` (token đặt lại mật khẩu), `integration/mail/OtpEmailComposer` (nội dung email). |
| `service/PasswordResetService` | Quên mật khẩu / đặt lại mật khẩu; xoá cache phiên qua `UserSessionCache`. |
| `service/OrganizationRequestService` | Người gửi yêu cầu: gửi mới, xem của mình. Idempotency ở `OrganizationRequestIdempotency`, thẻ lịch ở `EventScheduleTag`. |
| `service/OrganizationApprovalService` | Admin: liệt kê, đếm, duyệt, từ chối. Quyền admin kiểm bằng `AdminGuard`; sự kiện demo do `DemoInitialEventFactory` dựng. |
| `security/RouteAccessPolicy` | Một nguồn luật "ai được vào trang nào". `RoleAuthorizationFilter` thi hành, `LoginServlet` dùng để quyết định redirect sau đăng nhập. |

## Lỗi

Các luồng đăng ký / đăng nhập / OTP / đặt lại mật khẩu / yêu cầu tổ chức ném `IdentityException(IdentityError, message)`.
`IdentityError` giữ cặp `(HTTP status, error.code)`; `IdentityErrorWriter` đổi nó thành envelope
`{"error":{code,message,correlationId}}`. Một số hằng cố ý dùng chung code (ví dụ `EMAIL_ALREADY_EXISTS` và
`USERNAME_ALREADY_EXISTS` đều trả `IDENTITY_CONFLICT`) để giữ nguyên hợp đồng API. Lỗi validate đầu vào vẫn là
`IllegalArgumentException` và được ghi thành `VALIDATION_FAILED`.

## Controller

`controller/support`: `RequestFields` (đọc JSON hoặc form), `PathIds` (UUID trong path), `IdentityErrorWriter`,
`JsonObjectBuilder` (JSON có escape). DTO tự dựng JSON bằng `toJson()`.

`AdminOrganizationApprovalServlet` là nơi duy nhất xử lý `/api/admin/organization-requests/**`;
`OrganizationRequestServlet` chỉ còn phần của người gửi yêu cầu. Chính sách hoa hồng khi duyệt được đọc và kiểm
bởi `ApproveOrganizationRequestDto.from(body)`.
