> **Convention:** `dev` → `feat/khanh/khanh-06-event-cancellation` → PR `dev`; merge khi Actions xanh và có review.
> **Ownership:** Khánh sở hữu API cancel/progress, EventCancellationJob/Repository và Refund adoption. Đông chỉ sở hữu JSP UI-20 và publish/reject.

## Mục tiêu và endpoint

- `POST /api/admin/events/{eventId}/cancel`
- `GET /api/admin/events/{eventId}/cancellation-progress`

Hủy Event trước startTime, chặn bán/check-in ngay và xử lý từng Order theo lô có thể tiếp tục sau restart.

## Kỹ thuật

- Chỉ ADMIN. SP13 transaction đầu chuyển Event CANCELLED, tạo outbox/job và release Hold phù hợp; không hoàn tất mọi Refund trong một transaction dài.
- Redis idempotency `tc:idem:event-cancel:<adminId>:<key>`, lease `tc:lease:event-cancel:<eventId>` và progress cache TTL ngắn.
- EventCancellationJob dùng SP17 từng Order. Nếu có Refund REQUESTED thì adopt cancellation; không tạo nghĩa vụ thứ hai cho cùng Ticket.
- USED Ticket ghi ngoại lệ, không tự hoàn. Refund approved/processing giữ nguyên tập vé; phần còn lại tạo nghĩa vụ riêng theo DB.
- Sau commit invalidate public Event, availability, check-in window, Hold, Refund và report caches.
- Restart nhận lại lease hết hạn và tiếp tục từ DB/outbox; Redis không giữ cursor duy nhất.

## Test

- Cancel lặp, cancel sau startTime, concurrent payment/cancel, request Refund có sẵn, USED Ticket, worker chết giữa batch và restart.
- Manual Swagger cancel/progress, kiểm Event bị chặn ngay và tiến độ DB/Redis tăng idempotent.

