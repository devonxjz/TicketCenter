# Đông — tiến độ và tích hợp ngày 12/10/2026

Phạm vi giữ nguyên DONG-01–05 (#8–12), epic #13; SQL phối hợp #45/PR56.
Đã ghép `dev` af2ded209122d8c5604ae4ccee2e765fefde190e vào cả hai nhánh Đông.
Không ghép hoặc tự phê duyệt các PR chưa được nhận vào dev.

## Tiến độ dự án đã đọc trên GitHub

| Thành viên | Trạng thái quan sát được |
|---|---|
| Thái | Issues1–5 closed;6/profile và epic7 open. Luồng tổ chức được ghép vào dev. |
| Liêm | PR58/60/62/63/64/66 của cả sáu task đã merge; issues14–19 vẫn open. Merge không tự chứng minh toàn bộ nghiệm thu provider/worker. |
| Khánh | PR38/check-in selection đã merge; PR50/refund request và61/signed check-in đang open, chưa review. Các task refund worker/cancellation chưa có bằng chứng nghiệm thu cuối. |
| Vương | PR57/nghiệm thu riêng và59/finance UI mới đã merge; issues29–32 closed,28/33/34 open. PR65/SQL hardening draft và67/cross-domain integration open; toàn sản phẩm chưa được nghiệm thu. |
| Đông | PR51 và56 open/draft, chưa có review phê duyệt. Năm task triển khai/nghiệm thu riêng hoàn thành; tiếp tục cập nhật tích hợp trong lần này. |

Snapshot lấy từ API, không lấy số issue open làm tỷ lệ mã nguồn chưa làm.
Trước cập nhật này: PR51 head10b287f có13 success/10 skipped; PR56 head68cc3e9
có12 success/10 skipped. dev af2ded2 có7 success/5 skipped. Những số này không
phải CI của commit mới được đẩy sau hồ sơ này; kiểm tra exact head khi review.

## Phần Đông đã xử lý tiếp

- [x] Giải quyết conflict public event detail: giữ sơ đồ Zone/Seat và thêm nút
  Chọn vé vào trang của Đông; cả `?eventId=` và `?id=` đi đúng trang Hold.
  Link chỉ hiện sau khi API public trả được Event, ẩn khi ID sai/404.
- [x] Scoped JDBC quản lý Event theo actor lấy từ phiên đã xác thực; khôi phục
  SESSION_CONTEXT trước đó khi thành công hoặc bị từ chối, không lưu quyền vào cache.
- [x] SQL command và canonical SP12 khôi phục actor cũ; SP12 có actor cho audit
  ngay cả khi gọi trực tiếp và cập nhật giờ kiểm tra sau khi chờ khóa Event.
- [x] Đổi migration Đông **chưa triển khai** từ030 sang043:029/030 đã có Hold/Order
  trên dev;031–042 được các PR65/67 sử dụng. Không đổi các migration đã áp dụng.
- [x] Kiểm thử lại nguồn dev mới, JDBC/context, SQL proposal và full WAR.

## Bằng chứng lần chạy mới

| Kiểm tra | Kết quả |
|---|---|
| Package lựa chọn an toàn | 842 tổng:659 chạy đạt,183 guard-skipped,0 failures/errors; WAR build thành công |
| JavaScript hiện hành + Hold/Checkout | 116 đạt,0 skipped |
| Public DOM fixture | Đạt; cả hai alias, link Hold và ghế/empty/error/unsafe URL |
| Real SQL/Redis | 16 đạt,0 skipped; gồm actor scope và khôi phục context sau read/edit/403/direct SQL |
| Cloudinary thật | 1 đạt,0 skipped, SQL/public URL và cleanup kiểm reference |
| Full WAR HTTP | 3 đạt,0 skipped; chạy lại sau bổ sung hai alias và trang Hold, giữ Report/overview/multipart/CSRF/role checks |
| Migration043 riêng | 1 đạt,0 skipped; full artifact apply/reapply, receipts và cleanup fingerprint không mất, public Seat grant/private denial |

Tổng nghiệm thu Event thực: **20 =16+1+3**, không cộng lần chạy lại HTTP vào tổng.
Provider là thật cho ca Cloudinary/HTTP; lỗi provider/Redis outage vẫn là injection
được ghi rõ. Context tests dùng restricted database-user impersonation, không
phải production-login acceptance. Alias/link chạy DOM + HTTP/JSP, không tuyên bố
đã chạy lại toàn bộ browser checkpoint ngày11/10.

Lựa chọn build thêm `-Dtest=*,!OrganizationRepositoryTest#testRepositoryIntegration`:
method tổ chức mới trên dev chưa có DB guard (bản sửa ở PR65 chưa merge). Lần đầu
bị dừng khi nó khởi tạo kết nối; probe chung bị chặn40615. Chỉ null-guard method
của lớp đó chạy trong build sau; không coi method bị loại là đã đạt. Không sửa test
của Thái hoặc CI/POM/config chung. Lần đầu Maven thiếu quyền cache dependency mới
cũng thất bại trước build; lần chạy có quyền cache và lựa chọn an toàn nêu trên đạt.

Một helper đồng bộ migration ban đầu dùng đường dẫn tương đối sai, chỉ còn prefix
trong artifact tạm; smoke đó không được tính nghiệm thu. Artifact đầy đủ đã được
khôi phục, helper fail-fast, test thêm kiểm tra body bắt buộc, compile/rerun và
apply/reapply đầy đủ đạt. Không triển khai artifact tạm lên DB chung.

Logs ignored: target/dong-20261012-package-final.log, dong-20261012-node.log,
dong-20261012-real-it.log, dong-20261012-http-handoff.log. SQL worktree:
target/dong-sql-20261012-migration-final.log. PID/helpers và credentials fixture
ở target riêng, không đưa vào GitHub hoặc hồ sơ này.

## Các gate còn chờ

- [ ] Independent review và merge PR51/56 vào dev, CI/acceptance trên dev cuối.
- [ ] Owner nhận migration043, kiểm combined rollout với PR65/67 và actual runtime
  logins/grants. Bộ test20 ca ở trên dùng baseline hiện tại, chưa chứng minh toàn
  bộ schema/RLS031–042 hoặc WAR của các PR chưa merge. Các sửa image/actor/routing
  chung trong PR67 phải được phối hợp khi nó được nhận vào dev.
- [ ] Shared DB metadata/login acceptance: lần đọc mới trảSQL40615/S0001.
- [ ] Hold/payment/refund/cancellation owners nối postcommit Event invalidation;
  handoff interface sẵn có, không tự sửa các miền đó. TTL giới hạn độ trễ nhưng
  chưa chứng minh immediate cross-domain invalidation.

Không đổi firewall, credential/grants/data DB chung, không merge hoặc tự approve.
Các checkbox task vẫn chỉ xác nhận phần Đông; không đóng epic toàn dự án.
