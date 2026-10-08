Áp dụng bổ sung cho toàn bộ sub-issue trong epic này:

1. SQL Server là source of truth. Redis không được thay thế dữ liệu nghiệp vụ trong DB.
2. Luồng đọc dùng cache-aside: đọc Redis trước, cache miss mới đọc DB rồi cache DTO/projection có TTL. Không cache JPA entity hoặc lazy graph.
3. Mutation nhận `Idempotency-Key`; Redis giữ request hash/trạng thái/kết quả. Cùng key và payload trả lại kết quả cũ; cùng key nhưng payload khác trả 409. Chỉ refresh/invalidate cache sau khi DB commit.
4. Không cache password hash, OTP rõ, cookie, raw QR, khóa ký, VNPAY secret hoặc credential.
5. Mọi backend endpoint phải manual test qua `/swagger/`: ca thành công, chưa đăng nhập, thiếu quyền, validation, conflict/idempotency. Evidence phải ghi HTTP response, Redis key đã lọc và trạng thái DB, không chứa secret.
6. SQL Server quyết định quyền, trạng thái và số tiền cuối cùng. Cache miss hoặc cache cũ không được làm thay đổi kết quả mutation.

Nếu Redis lỗi: GET được phép fallback DB; mutation tài chính/check-in/idempotency-critical trả 503 nếu không thể chứng minh lời gọi lặp an toàn, trừ khi DB có khóa duy nhất và transaction đã chứng minh cùng mức bảo vệ.

