# Cập nhật nghiệm thu phần việc của Đông

Cập nhật 11/10/2026 (UTC+7). Phạm vi đã thống nhất: DONG-01–05, issues #8–12,
epic #13. Dấu hoàn thành dưới đây xác nhận **triển khai và nghiệm thu riêng của
Đông trên nhánh bàn giao**, chưa xác nhận Definition of Done tích hợp của epic.

## Task đã hoàn thành trong phạm vi

- [x] DONG-01 / #8: catalog, category, detail, Zone/Seat public, lọc/phân trang,
  chỉ hiển thị PUBLISHED, Redis cache-aside/fallback và invalidation sau commit.
- [x] DONG-02 / #9: draft CRUD/submit theo tổ chức, quyền hiện tại, CSRF,
  chronology, khóa trạng thái, idempotency và rollback/concurrency.
- [x] DONG-03 / #10: SEATED/STANDING, ma trận ghế, giá/sức chứa, giới hạn
  held+sold, chống xóa/sửa inventory không hợp lệ và khóa layout đã publish.
- [x] DONG-04 / #11: multipart/content validation, Cloudinary, quyền/version,
  replay, invalidation và metadata thu hồi ảnh sau commit/xóa draft.
- [x] DONG-05 / #12: admin list/detail/layout/rule, canonical publish,
  reject có lý do, role hiện tại, audit và chống phản hồi duyệt cũ.
- [x] Bàn giao SQL riêng theo #45: migration030, kiểm thử apply/reapply và
  quyền projection public trong fixture, qua PR56.
- [x] Sửa và kiểm chứng sáu lỗi tái hiện được trong đợt rà soát cuối.

## Bằng chứng đã đối chiếu

Nguồn Event: `d43250e64faa94bae1c8f94bd569a0b06e28f81c`, [PR51](https://github.com/devonxjz/TicketCenter/pull/51).
Nguồn SQL: `68cc3e995b7d120cd57bb2777210994d35ea8c16`, [PR56](https://github.com/devonxjz/TicketCenter/pull/56).
Cả hai ghép dev `3e380bf24ba0e8d25609614655f0f24e738c290d`.

| Kiểm tra | Kết quả đã xác minh |
|---|---|
| Java và đóng gói WAR | 385 chạy đạt; 60 external tests được guard bỏ qua; 445 tổng |
| Node | 59 đạt, không skip |
| Platform/local-handoff guards | 18 đạt, không skip |
| SQL/Redis + Cloudinary + full-WAR HTTP | 15 + 1 + 3 = 19 đạt, không skip |
| Migration riêng | 1 đạt; apply/reapply giữ receipt và fingerprint cleanup |
| Cleanup fixture provider | 1 đạt, chỉ xóa asset của fixture đã kiểm tra reference |
| CI trên mỗi head nguồn nêu trên | 12 success, 10 skipped, không pending/failed |

Đây là đối chiếu kết quả cuối đã chạy ngày 11/10, không phải tuyên bố chạy lại
toàn bộ test trong lần cập nhật checklist. Không cộng toàn bộ XML còn lưu trong
target: report EventCatalogSqlServerIT ngày 10/10 thất bại do thiếu biến fixture
TC_EVENT_TEST_PUBLISHED_ID vẫn được giữ; suite epic thay thế đã kiểm chứng public
projection trong 19 ca hiện tại. Test bỏ qua không được tính là đạt.

Hợp đồng UUID/VND nguyên/UTC/data và SP canonical được ưu tiên theo tài liệu
chuẩn. Ví dụ ID số/tên SP trong issue không phải hợp đồng triển khai; upload quá
cỡ trả 413 theo kiểm thử WAR, thay vì ví dụ 400 trong IMG-03.
Browser là checkpoint trước đó; ba regression chống phản hồi admin cũ là DOM
simulation. Redis outage và lỗi provider là fault injection; các ca provider/
HTTP riêng dùng Cloudinary thật. Quyền SQL được kiểm tra bằng database-user
impersonation trên Windows transport, chưa chứng minh login runtime chung.

## Các bước tích hợp chưa hoàn thành

- [ ] Review phê duyệt độc lập: PR51 và PR56 vẫn draft, chưa có review.
- [ ] Chủ database nhận/áp dụng migration030 và xác minh grants/login runtime.
- [ ] Merge PR vào dev sau khi đủ điều kiện; kiểm tra CI trên dev sau merge.
- [ ] Nghiệm thu UI-01/02 phần đọc, UI-12/13 và UI-20 publish/reject trên dev cuối.
- [ ] Chủ miền khác nối các hook cache cho hold/refund/cancellation khi tích hợp.

Probe metadata chung mới lúc khoảng 19:01 ngày 11/10 trả SQL40613/S0001; lần
trước trả SQL40615. Chưa xác minh schema/login chung. Không thay firewall,
credentials, grants hoặc dữ liệu DB chung. Issues #8–13 và #45 giữ open cho các
gate tích hợp; không tự phê duyệt PR hoặc đóng toàn epic.

Chi tiết lỗi/kiểm thử: [DONG-REVIEW-20261011.md](DONG-REVIEW-20261011.md).
Hợp đồng bàn giao: [DONG-INTEGRATION.md](DONG-INTEGRATION.md).
Tái lập: [RUNBOOK.md](RUNBOOK.md).
