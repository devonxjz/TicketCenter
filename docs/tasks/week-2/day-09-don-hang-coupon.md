# Ngày 09 — Tạo đơn từ Hold và quản lý coupon

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Lịch giả định ban đầu (chưa tái ước lượng):** Thứ bảy, 2026-10-03. **Ước lượng tạm của kế hoạch ban đầu:** 17 giờ công tổng của nhóm.

**Mục tiêu:** Một Hold sinh một Order giá snapshot; coupon tối đa 30%, quota và đổi mã được bảo vệ nguyên tử.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [spec.md](../../../spec.md), [diagram chuẩn](../../classdiagram/diagram.md) §6.4–6.5, §6.7, §14 SP03/SP06/F01/F08/V09/TR07. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D08; schema Order/Payment/redemption đã có.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D09-T01 — Tạo Order/OrderItem snapshot bằng SP06

**Phụ trách đề xuất:** B. **Giờ công:** 4h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D09_01_order.sql; JAVA/repository/order/OrderRepository.java; SQLTEST/day-09.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_CreateOrderFromHold(holdId,actorId) → orderId/orderCode/subtotal/total/expiresAt; POST /orders {holdId}.

**Phụ thuộc của task:** D08 Hold/SP07 và snapshot giá.

**Cách thực hiện:**

- [ ] 1. Kiểm owner, Event/Hold hợp lệ, khóa Hold/Order và trả Order cũ nếu đã tạo trong ngữ cảnh hợp lệ.
- [ ] 2. Sao quantity/unitPrice từ HoldItem và tên khu/nhãn ghế sang OrderItem; không lấy giá Zone hiện tại.
- [ ] 3. Tính subtotal/total trong DB, ban đầu discount 0; unique holdId chống race; TTL giữ nguyên.
- [ ] 4. TX06 gây lỗi insert Item thứ hai, kiểm không còn Order thiếu Item; outer transaction rollback cả SP.
- [ ] 5. Gọi hai lần/đồng thời và đối chiếu cùng orderId; giữ orderCode unique và không dùng nó như bằng chứng quyền.

**Kiểm chứng bắt buộc:**

- [ ] Giá Zone tăng sau Hold không đổi OrderItem.
- [ ] Hai tab tạo đơn chỉ một Order; owner khác, Hold expired/consumed không tạo đơn mới.

**Điều kiện hoàn thành:** SP06/TX06 và API tạo đơn chạy thực. Ghi case và kết quả trong `docs/evidence/day-09.md`.

## D09-T02 — CRUD coupon, F01/F08, V09 và TR07

**Phụ trách đề xuất:** B+A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D09_02_coupon_reads.sql; JAVA/service/order/CouponService.java; JAVA/controller/order/CouponServlet.java; TEST/order/CouponMoneyTest.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Coupon theo org; F01 discount, V09 usage, F08 eligibility; manager đúng org hoặc admin toàn hệ thống.

**Phụ thuộc của task:** D02 coupon/redemption và D06 quyền tổ chức; không chờ SP06 để viết F01/V09/TR07.

**Cách thực hiện:**

- [ ] 1. Tạo/sửa/kích hoạt mã: percentage (0,30], fixedAmount>0, maxUses>0, validFrom<validTo; quy ước code chuẩn hóa/unique theo quyết định ngày 1.
- [ ] 2. F01 floor đồng nguyên và cap 30%; tham số bắt buộc thiếu/sai trả NULL, caller reject chứ không coalesce 0.
- [ ] 3. V09 đếm RESERVED/CONSUMED/RELEASED, giữ RESERVED quá hạn đến khi SP07 release; coupon chưa dùng vẫn có dòng.
- [ ] 4. F08 trả lý do sai org/hết hạn/tắt/hết quota và preview discount; preview không đặt giữ lượt.
- [ ] 5. TR07 chặn maxUses dưới reserved+consumed với khóa Coupon; chỉ xóa vật lý coupon chưa có redemption, mã đã dùng thì vô hiệu.

**Kiểm chứng bắt buộc:**

- [ ] 500000 giảm fixed 200000 chỉ được 150000; subtotal 1 và 30% giảm 0.
- [ ] Hạ maxUses bằng lượng đang dùng được, thấp hơn bị chặn cả lệnh nhiều dòng.

**Điều kiện hoàn thành:** F01/F08/V09/TR07 được gọi từ API coupon và preview. Ghi case và kết quả trong `docs/evidence/day-09.md`.

## D09-T03 — SP03 áp dụng/đổi/bỏ mã và khóa tổng tiền

**Phụ trách đề xuất:** B+A. **Giờ công:** 5h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** SQL/D09_03_apply_coupon.sql; JAVA/service/order/OrderService.java; JAVA/controller/order/OrderServlet.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_ApplyOrderCoupon(orderId,actorId,couponCodeOrNull); POST /orders/{id}/coupon; GET /me/orders, /orders/{id}.

**Phụ thuộc của task:** D09-T01/T02; D02 Payment schema cho guard PENDING/UNKNOWN.

**Cách thực hiện:**

- [ ] 1. Kiểm chủ đơn, PENDING_PAYMENT, Event/Hold còn hiệu lực; Payment PENDING/UNKNOWN chặn mọi thay coupon.
- [ ] 2. Khóa Order và cả coupon cũ/mới theo ID để tránh A→B/B→A deadlock; dùng redemption unique order.
- [ ] 3. Áp dụng cùng mã đã RESERVED hợp lệ trả kết quả cũ không reprice hoặc kiểm lại cấu hình hồi tố.
- [ ] 4. Đổi mã thực hiện trả cũ/giữ mới/tính discount cập nhật tổng trong một transaction; mã mới lỗi rollback giữ nguyên mã cũ.
- [ ] 5. Bỏ mã chỉ trước payment đang xử lý; completed payment CONSUMED không trả lượt khi hoàn tiền sau này.

**Kiểm chứng bắt buộc:**

- [ ] Hai đơn tranh maxUses=1 chỉ một RESERVED; đổi mã lỗi vẫn giữ discount cũ.
- [ ] Payment UNKNOWN khóa coupon; sau FAILED xác định và Hold còn hạn được thao tác hợp lệ.

**Điều kiện hoàn thành:** SP03/TX03 có Java caller duy nhất và API trả breakdown server-side. Ghi case và kết quả trong `docs/evidence/day-09.md`.

## D09-T04 — Đối chiếu tiền, snapshot và quyền đơn

**Phụ trách đề xuất:** C. **Giờ công:** 3h. **Trạng thái kế hoạch:** chưa có bằng chứng nghiệm thu backend.

**File cần tạo/cập nhật:** TEST/acceptance/Day09IT.java; TEST/concurrency/CouponConcurrencyIT.java; docs/evidence/day-09.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Bảng giá trị chung Java/SQL cho phần trăm/fixed; history GET luôn lọc owner.

**Phụ thuộc của task:** D09-T01/T02/T03; transaction/principal nền.

**Cách thực hiện:**

- [ ] 1. Tạo case 0đ, giá cực đại trong miền, phần đồng lẻ, coupon khác org, expired, inactive và 30% đúng trần.
- [ ] 2. Chạy bảng giá trị qua Model và F01 để cùng kết quả; trường hợp invalid phải lỗi tương đương.
- [ ] 3. TX03 lỗi sau trả lượt cũ và TX06 lỗi sau tạo Order đều rollback; test SQL và JPA outer transaction.
- [ ] 4. Thử manager có quyền tổ chức nhưng không phải chủ đơn mua của người khác; không tự có quyền sửa đơn.
- [ ] 5. Chạy flow Hold→Order→coupon→cancel và kiểm cả ghế/standing/redemption trở lại; lưu payload mẫu không có bí mật.

**Kiểm chứng bắt buộc:**

- [ ] Sum quantity vẫn <=8, một Event, Order snapshot không bị CRUD coupon/Zone sửa lại.
- [ ] SP03/SP06, F01/F08, V09/TR07 có trace gọi thật.

**Điều kiện hoàn thành:** Đơn và giá đã đủ ổn định để bắt đầu thanh toán. Ghi case và kết quả trong `docs/evidence/day-09.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day09IT` và `database/tests/day-09.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day09IT verify
```
