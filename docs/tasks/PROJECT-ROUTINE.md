# Lộ trình & Quy trình phối hợp thực hiện dự án TicketsCenter (ROUTINE)

> **Tài liệu tham chiếu chuẩn:** [spec.md](../../spec.md), [docs/classdiagram/diagram.md](../classdiagram/diagram.md), [docs/tasks/TEAM-CONTRACT.md](TEAM-CONTRACT.md), [docs/tasks/GIT-WORKFLOW.md](GIT-WORKFLOW.md).  
> **Áp dụng cho 5 thành viên:** Khánh, Đông, Liêm, Thái, Vương.  
> **Nguyên tắc cốt lõi:** Làm việc song song (Parallel) theo từng mốc (Milestone); làm tuần tự theo cây phụ thuộc trong từng danh sách task cá nhân.

---

## 1. NGUYÊN TẮC LÀM VIỆC SONG SONG (PARALLEL WORKFLOW)

### 1.1. Thứ tự giữa các thành viên vs. Thứ tự cá nhân
* **Hàng ngang (Giữa 5 thành viên):** **LÀM SONG SONG**. Cả 5 người bắt tay làm việc cùng lúc trên 5 nhánh Git độc lập, không ai phải ngồi chờ ai.
* **Cột dọc (Cá nhân mỗi người):** **LÀM TUẦN TỰ** từ trên xuống dưới (`01` $\rightarrow$ `02` $\rightarrow$ `03`...) vì task sau sử dụng kết quả của task trước.

### 1.2. Giải pháp kỹ thuật để không bị kẹt phụ thuộc (Anti-Deadlock)
1. **Dữ kiện đã được đóng băng 100%:** Toàn bộ tên class, trường, kiểu dữ liệu, quan hệ đã có đầy đủ trong [diagram.md](../classdiagram/diagram.md) và [spec.md](../../spec.md). Khi code, bạn cứ khai báo tham chiếu bình thường (`User buyer`, `Event event`).
2. **Kỹ thuật Skeleton (Khung rỗng) & Test Fixture:** Nếu nhánh của bạn chưa có class của bạn khác, tạo class khung rỗng tạm thời để code Java biên dịch được (`compile`). Khi viết Unit Test, dùng mock/stub fixture để test độc lập logic của mình.
3. **Database tách riêng khóa ngoại liên miền:** Các file `0010_...` đến `0050_...` chỉ tạo bảng riêng của từng người. Toàn bộ Foreign Keys trỏ chéo bảng người khác nằm ở file `0100_cross_domain_keys.sql` do Vương ghép cuối mốc M0.

---

## 2. VÒNG LẶP 6 BƯỚC THỰC HIỆN 1 TASK CÁ NHÂN (TASK ROUTINE)

Mỗi khi bất kỳ thành viên nào bắt tay vào làm một task cụ thể, luôn thực hiện theo đúng 6 bước sau:

```text
[Bước 1: Rẽ nhánh Git]
       │
       ▼
[Bước 2: Đối chiếu Hợp đồng (Consumes/Produces)]
       │
       ▼
[Bước 3: Viết Test & Code (Chỉ sửa file sở hữu)]
       │
       ▼
[Bước 4: Chạy kiểm chứng thật (Maven & SQL Server)]
       │
       ▼
[Bước 5: Lập file Bằng chứng & Commit chuẩn]
       │
       ▼
[Bước 6: Tạo PR vào develop & Review chéo]
```

### Chi tiết từng bước:
* **Bước 1: Rẽ nhánh Git đúng quy chuẩn**
  * Cập nhật code mới nhất: `git checkout develop && git pull origin develop`
  * Tạo nhánh: `git checkout -b feature/<tên>/<task-id>-<tên-ngắn>`
  * *Ví dụ:* `feature/liem/liem-01-models-and-contracts`
* **Bước 2: Đối chiếu Hợp đồng**
  * Mở [TEAM-CONTRACT.md](TEAM-CONTRACT.md) và file nhiệm vụ cá nhân (`<tên>.md`).
  * Xác định rõ: Task này nhận đầu vào từ ai (Consumes) và bàn giao đầu ra cho ai (Produces).
* **Bước 3: Viết mã nguồn & Unit Test**
  * Tuyệt đối **chỉ sửa các file thuộc phạm vi sở hữu** của mình.
  * Viết Unit Test kiểm tra các ca biên (boundary), ca lỗi (negative), ca tính tiền, kiểm tra null/rỗng.
* **Bước 4: Chạy kiểm chứng trên môi trường thật**
  * Chạy build: `mvn clean test` (hoặc profile `sqlserver-it` nếu có tích hợp DB).
  * Chạy script SQL kiểm thử trên SQL Server local, xác nhận cả ca thành công (`COMMIT`) và ca lỗi (`ROLLBACK`).
* **Bước 5: Ghi nhận bằng chứng & Commit**
  * Tạo file `docs/evidence/<tên>/<TASK-ID>.md` ghi lại nhật ký kiểm thử, lệnh đã chạy và kết quả.
  * Commit theo chuẩn Conventional Commits:
    `feat(<scope>): <nội dung tóm tắt>`  
    Footer: `Task-Id: <TASK-ID>`
* **Bước 6: Tạo Pull Request & Merge**
  * Push nhánh lên remote, mở PR vào nhánh `develop`.
  * Thành viên nhận đầu ra của task sẽ vào review, xác nhận đạt yêu cầu rồi merge.

---

## 3. LỘ TRÌNH PHỐI HỢP 5 THÀNH VIÊN THEO 5 MỐC (MILESTONE ROADMAP)

```text
┌────────────────────────────────────────────────────────────────────────┐
│ M0: Xây dựng Nền tảng, Hợp đồng Interfaces, 15 Model & Database Schema │
├────────────────────────────────────────────────────────────────────────┤
│ M1: Xác thực tài khoản, Duyệt Ban tổ chức & Quản lý Sự kiện / Ghế ngồi │
├────────────────────────────────────────────────────────────────────────┤
│ M2: Giữ vé 10 phút, Thanh toán VNPAY, Phát hành vé QR & Quét Check-in  │
├────────────────────────────────────────────────────────────────────────┤
│ M3: Yêu cầu Hoàn vé, Hủy sự kiện, Đối soát tài chính & Đủ 24 màn hình  │
├────────────────────────────────────────────────────────────────────────┤
│ M4: Kiểm thử tranh chấp (Concurrency), Benchmark Index & Đóng gói nộp  │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 📌 MỐC M0: HẠ TẦNG NỀN TẢNG, HỢP ĐỒNG & SCHEMA
> **Mục tiêu:** Cả 5 người cùng cung cấp "khung xương" (Interfaces, DTOs, 15 Model và DDL Database). Không ai làm logic sâu, chỉ làm hợp đồng để phá vỡ thế kẹt phụ thuộc.

| Thành viên | Tasks thực hiện song song | Đầu ra bàn giao cho cả nhóm |
|---|---|---|
| **Khánh** | • `KHANH-01`: Cấu hình `pom.xml`, khóa dependencies Tomcat 11, Hibernate 6.x.<br>• `KHANH-02`: Viết `ActorContext`, `PageRequest`, `ClockProvider`.<br>• `KHANH-03`: Viết `TransactionRunner` quản lý transaction JPA `resource-local`.<br>• `KHANH-04`: Entity `User` & script `0010_identity.sql`.<br>• `KHANH-11`: Layout JSP và CSS/Bootstrap dùng chung. | Cung cấp khung build, `User`, context đăng nhập và bộ layout chung cho cả 4 người dùng. |
| **Đông** | • `DONG-01`: Model `Organization`, `Event`, `Zone`, `Seat` & các DTO liên quan.<br>• `DONG-02`: Script `0020_organizations_events.sql`. | Cung cấp Model sự kiện/khu/ghế cho Liêm dùng làm Order/Hold. |
| **Liêm** | • `LIEM-01`: Cung cấp 7 Model (`TicketHold`, `TicketHoldItem`, `Order`, `OrderItem`, `Payment`, `Coupon`, `Ticket`), toàn bộ DTOs và SPI Outbox Worker.<br>• `LIEM-02`: Script `0030_sales.sql`. | Cung cấp Model vé/đơn hàng cho Thái làm Refund và Vương làm Settlement. |
| **Thái** | • `THAI-01`: Model `Refund` (gồm hàm `createCompensation`) & DTO.<br>• `THAI-02`: Script `0040_refunds_checkin.sql`. | Cung cấp cấu trúc Refund để Liêm viết thủ tục thanh toán `SP09`. |
| **Vương** | • `VUONG-01`: Model `CommissionRule`, `Settlement`.<br>• `VUONG-02`: Script `0050_settlements_audit.sql`.<br>• `VUONG-03`: Script tạo 4 Role/Login DB (`tc_buyer`, `tc_manager`...).<br>• `VUONG-04`: Ghép Foreign Key liên miền `0100_cross_domain_keys.sql`. | Chốt cấu trúc phân quyền DB và tổng hợp chạy thử toàn bộ script SQL dựng Database. |

🏁 **Điều kiện đóng Mốc M0:** Dự án `mvn clean compile` thành công 100%; chạy toàn bộ file SQL tạo được database hoàn chỉnh không lỗi khóa ngoại.

---

### 📌 MỐC M1: TÀI KHOẢN, DUYỆT TỔ CHỨC & TẠO SỰ KIỆN
> **Mục tiêu:** Hệ thống có thể đăng ký, đăng nhập, duyệt ban tổ chức, tạo sự kiện và thiết lập sơ đồ ghế.

| Thành viên | Tasks thực hiện trong M1 | Phối hợp liên miền |
|---|---|---|
| **Khánh** | • `KHANH-07, 08, 09`: Hoàn thiện xác thực mật khẩu, gửi OTP qua email, quản lý Session, Filter bảo mật/CSRF.<br>• `KHANH-12`: Hoàn thiện màn hình `UI-03` (Đăng ký, Đăng nhập, Quên mật khẩu). | Cung cấp tài khoản người mua và tài khoản BTC hợp lệ cho cả nhóm test. |
| **Đông** | • `DONG-03`: Người dùng gửi yêu cầu mở BTC (`UI-09`).<br>• `DONG-04`: Viết `SP01` duyệt tổ chức, gán quyền `MANAGER`.<br>• `DONG-05`: Quản lý thành viên tổ chức (`UI-10, UI-11`).<br>• `DONG-06 $\rightarrow$ 08`: BTC tạo sự kiện, thiết lập khu ngồi, sinh ghế (`UI-12, UI-13`).<br>• `DONG-09`: Viết `SP12` công khai sự kiện.<br>• `DONG-10`: Màn hình xem danh sách và sơ đồ chọn ghế (`UI-01, UI-02`). | Chuyển giao sự kiện đã công khai (`PUBLISHED`) và sơ đồ ghế sẵn sàng để Liêm làm chức năng giữ chỗ. |
| **Vương** | • `VUONG-05`: Cấu hình chính sách hoa hồng (`UI-22`).<br>• `VUONG-12`: Adapter cho Admin duyệt tổ chức (`UI-19`) và duyệt sự kiện (`UI-20`). | Gọi `SP01` và `SP12` của Đông trên giao diện Admin. |
| **Liêm & Thái** | Chuẩn bị sẵn logic nội bộ (Liêm làm sạch cấu trúc Coupon, Thái chuẩn bị logic giờ check-in). | Sẵn sàng nhận sự kiện từ Đông. |

🏁 **Điều kiện đóng Mốc M1:** Khách có thể đăng ký/đăng nhập; Admin duyệt được Tổ chức; Tổ chức tạo được sự kiện và hiển thị lên trang chủ `UI-01` / `UI-02`.

---

### 📌 MỐC M2: GIỮ VÉ, THANH TOÁN VNPAY, XUẤT VÉ QR & CHECK-IN
> **Mục tiêu:** Luồng thương mại điện tử cốt lõi chạy thông suốt từ lúc chọn ghế đến lúc quét vé vào cửa.

| Thành viên | Tasks thực hiện trong M2 | Phối hợp liên miền |
|---|---|---|
| **Liêm** *(Chính)* | • `LIEM-03`: `SP07` nhả ghế khi hết hạn giữ vé.<br>• `LIEM-04`: `SP02` tạo `TicketHold` (tối đa 8 vé, TTL 10 phút, khóa bi quan).<br>• `LIEM-05`: `SP06` tạo đơn hàng `Order` từ lượt giữ.<br>• `LIEM-06, 07`: Quản lý Coupon (`UI-14`), `F01` tính giảm giá và `SP03` áp mã.<br>• `LIEM-08`: `SP08` bắt đầu thanh toán VNPAY.<br>• `LIEM-09`: Xử lý VNPAY IPN Webhook & Return URL.<br>• `LIEM-10`: `SP09` ghi nhận thanh toán, `F06` phân bổ tiền vé, đổi ghế sang `SOLD`, phát hành vé `Ticket`.<br>• `LIEM-11, 12`: Kích hoạt Outbox Dispatcher & Background worker quét hold hết hạn.<br>• `LIEM-13, 14`: Màn hình thanh toán (`UI-04`), kết quả (`UI-05`), đơn của tôi (`UI-06`), vé của tôi kèm ảnh QR (`UI-07`). | • Nhận lượt chọn ghế từ màn hình `UI-02` của Đông.<br>• Phát hành `Ticket` có mã QR an toàn cho Thái làm Check-in.<br>• Gửi message `EMAIL` vào outbox để Khánh gửi thư báo vé thành công. |
| **Thái** | • `THAI-03`: Viết `SP04` check-in vé (chống quét trùng).<br>• `THAI-04, 05`: Màn hình nhân viên quét camera QR và nhập mã vé (`UI-15, UI-16`). | Quét mã QR do Liêm phát hành để cho khách vào cửa. |
| **Khánh** | • `KHANH-10`: Viết `EmailJob` đọc Outbox gửi mail vé cho khách.<br>• `KHANH-13`: Viết `MeServlet` hiển thị danh sách đơn và vé của khách. | Hiển thị dữ liệu đơn hàng và vé do Liêm tạo ra. |

🏁 **Điều kiện đóng Mốc M2:** Người mua giữ ghế $\rightarrow$ thanh toán VNPAY Sandbox $\rightarrow$ nhận vé QR $\rightarrow$ nhân viên dùng camera quét check-in đổi vé thành `USED`.

---

### 📌 MỐC M3: HOÀN TIỀN, HỦY SỰ KIỆN, ĐỐI SOÁT TÀI CHÍNH & ĐỦ 24 UI
> **Mục tiêu:** Xử lý các quy trình hậu mãi phức tạp, hoàn tiền, hủy sự kiện, chốt sổ tài chính và hoàn thiện toàn bộ 24 màn hình.

| Thành viên | Tasks thực hiện trong M3 | Phối hợp liên miền |
|---|---|---|
| **Thái** *(Chính)* | • `THAI-06, 07`: Khách gửi yêu cầu hoàn vé (`UI-08`, `SP05`).<br>• `THAI-08 $\rightarrow$ 11`: Admin duyệt hoàn (`UI-21`, `SP10`), gọi adapter hoàn tiền mô phỏng (`SP11`), nhả lại ghế trống về kho.<br>• `THAI-12 $\rightarrow$ 14`: Admin hủy sự kiện (`UI-20`, `SP13`), worker quét từng đơn tự động duyệt hoàn (`SP17`). | • Đổi trạng thái vé của Liêm thành `REFUND_PENDING` $\rightarrow$ `REFUNDED`.<br>• Gọi hàm trả kho của Đông để bán lại vé. |
| **Vương** *(Chính)* | • `VUONG-06 $\rightarrow$ 08`: `SP14` tính lại doanh thu trừ hoàn vé, `SP15` chốt đối soát (`CONFIRMED`), `SP16` chi trả tiền cho BTC (`UI-22`).<br>• `VUONG-09 $\rightarrow$ 11`: Báo cáo doanh thu BTC (`UI-17`), Báo cáo toàn sàn và Audit Log (`UI-23`), xuất file CSV. | Đọc dữ liệu từ đơn hàng của Liêm và dữ liệu hoàn tiền của Thái để quyết toán. |
| **Liêm** | • `LIEM-15`: Xử lý trường hợp khách trả tiền trễ sau khi hết hạn giữ vé $\rightarrow$ tự động tạo `Refund` bù trừ (`PAYMENT_COMPENSATION`).<br>• Hoàn thiện các góc cạnh của `UI-04 $\rightarrow$ UI-07`. | Phối hợp với Thái để xử lý các khoản hoàn bù trừ kỹ thuật. |
| **Đông & Khánh** | Hoàn thiện nốt các trang Dashboard tổ chức (`UI-10`), trang Profile cá nhân (`UI-24`). | Hoàn tất đủ 24 màn hình. |

🏁 **Điều kiện đóng Mốc M3:** Đủ 24 màn hình JSP hoạt động thật; luồng hoàn vé và hủy sự kiện chạy tự động; chốt được tiền thanh toán cho ban tổ chức.

---

### 📌 MỐC M4: TỐI ƯU HIỆU NĂNG, ĐÓNG GÓI & BẢO VỆ ĐỒ ÁN
> **Mục tiêu:** Kiểm thử tải, tranh chấp đồng thời, đo benchmark Index và chuẩn bị hồ sơ nộp điểm rubric.

1. **Kiểm thử Concurrency & Transaction (Cả nhóm):**
   * Chạy kịch bản 2 kết nối song song tranh nhau 1 ghế (`TX02` của Liêm).
   * Chạy kịch bản 2 máy cùng quét 1 mã QR vé tại 1 thời điểm (`TX04` của Thái).
   * Kiểm thử Rollback khi xảy ra lỗi ở bước thanh toán (`TX09`).
2. **Benchmark 15 Non-Clustered Index (Vương chủ trì):**
   * Bơm dữ liệu lớn (seed data), bật `STATISTICS IO, TIME` đo tốc độ truy vấn trước và sau khi có Index để lấy Execution Plan nộp báo cáo.
3. **Phân quyền 4 Role Database (Vương chủ trì):**
   * Chứng minh tài khoản `tc_checkin` bị `DENY` không xem được doanh thu; tài khoản `tc_buyer` chỉ xem được đơn của mình.
4. **Đóng gói & Hồ sơ (Vương tổng hợp, 5 người cùng viết):**
   * Đóng gói Docker chạy trên Render / Tomcat.
   * Viết báo cáo Word 50–100 trang theo 6 chương chuẩn rubric môn DBMS330284 và slide thuyết trình $\le$ 15 trang.
   * Merge nhánh `develop` vào `main`, gắn tag `v1.0.0` để bảo vệ đồ án.

---

## 4. CHI TIẾT LỘ TRÌNH 16 TASK CỦA LIÊM (`LIEM-01` ĐẾN `LIEM-16`)

Dành riêng cho Liêm để theo dõi tiến độ công việc của mình:

| Task ID | Tên công việc | Mốc | Phụ thuộc đầu vào | Sản phẩm bàn giao |
|---|---|---|---|---|
| **LIEM-01** | Cung cấp 7 Model, DTOs, Service Interfaces & Worker SPI | **M0** | [diagram.md](../classdiagram/diagram.md) | 7 file Model trong Java, DTOs, `JobHandlerRegistry` |
| **LIEM-02** | DDL Schema `0030_sales.sql` & Constraints C06-C09, C11-C14 | **M0** | LIEM-01 | Script tạo bảng bán vé, đơn hàng, thanh toán |
| **LIEM-03** | Stored Procedure `SP07` (Hủy/Nhả giữ vé) & Transaction TX07 | **M2** | LIEM-02 | `0390_SP07.sql` |
| **LIEM-04** | Stored Procedure `SP02` (Tạo giữ vé 10p, tối đa 8 vé) & TX02 | **M2** | LIEM-03, Đông M1 | `0400_SP02.sql`, Service `HoldService` |
| **LIEM-05** | Stored Procedure `SP06` (Tạo Order từ Hold) & TX06 | **M2** | LIEM-04 | `0400_SP06.sql`, Service `OrderService` |
| **LIEM-06** | View `V09`, Function `F01`, Trigger `TR07` (Mã giảm giá Coupon) | **M2** | LIEM-02 | `0190_V09.sql`, `0200_F01.sql`, `0500_TR07.sql` |
| **LIEM-07** | Stored Procedure `SP03` (Áp mã Coupon vào đơn) & TX03 | **M2** | LIEM-05, LIEM-06 | `0400_SP03.sql` |
| **LIEM-08** | Stored Procedure `SP08` (Bắt đầu thanh toán Payment) & TX08 | **M2** | LIEM-05, LIEM-07 | `0400_SP08.sql`, Service `PaymentService` |
| **LIEM-09** | Tích hợp VNPAY Sandbox (URL generator & IPN Webhook verify) | **M2** | LIEM-08 | `VnpayGateway.java`, `VnpayServlet.java` |
| **LIEM-10** | Function `F06` (Phân bổ tiền vé) & `SP09` (Ghi nhận thanh toán, phát hành vé QR) | **M2** | LIEM-09, Thái M0 | `0200_F06.sql`, `0400_SP09.sql`, `QrEncoder.java` |
| **LIEM-11** | Transactional Outbox Publisher & Dispatcher | **M2** | LIEM-01, LIEM-10 | `OutboxPublisher.java`, `OutboxWorker.java` |
| **LIEM-12** | Background Jobs: Quét Hold hết hạn & Đối soát giao dịch | **M2** | LIEM-03, LIEM-11 | `HoldExpiryJob.java`, `PaymentReconciliationJob.java` |
| **LIEM-13** | View `V06`, Trigger `TR08` & Endpoint xem vé/tải ảnh QR | **M2** | LIEM-10 | `0300_V06.sql`, `0500_TR08.sql`, `TicketServlet.java` |
| **LIEM-14** | Màn hình JSP: `UI-04` (Checkout), `UI-05` (Kết quả), `UI-06` (Đơn của tôi), `UI-07` (Vé của tôi), `UI-14` (Coupon) | **M2** | LIEM-05..13 | 5 trang JSP kết nối dữ liệu thật |
| **LIEM-15** | Xử lý thanh toán trễ / bù trừ `PAYMENT_COMPENSATION` | **M3** | LIEM-10, Thái M3 | Kiểm thử ca tiền về sau khi hủy/hết hạn |
| **LIEM-16** | Đóng gói, kiểm thử Concurrency TX02/TX09 & Benchmark Index | **M4** | Toàn bộ | File Evidence, Execution Plans IX03, IX04, IX06, IX09, IX11 |

---

## 5. MA TRẬN REVIEW CHÉO (PEER REVIEW MATRIX)

Khi một thành viên tạo Pull Request vào nhánh `develop`, người được chỉ định review sẽ vào kiểm tra theo bảng sau:

| Tác giả PR | Thành viên Reviewer chính | Trọng tâm kiểm tra |
|---|---|---|
| **Khánh** | Vương & Liêm | Session, Authentication filter, bảo mật CSRF, TransactionRunner JPA. |
| **Đông** | Liêm & Vương | Cấu trúc rạp/ghế, khóa layout sau publish, logic kho đứng/ngồi. |
| **Liêm** | Đông (kho ghế) & Thái (vé/refund) | Khóa giữ chỗ không bị trùng ghế, phân bổ tiền vé F06, phát hành vé QR. |
| **Thái** | Liêm (vé/order) & Đông (trả kho) | Hoàn tiền trả ghế đúng vị trí, check-in không bị quét trùng, hủy sự kiện. |
| **Vương** | Khánh & Liêm | Độ chính xác tính toán hoa hồng, quyền hạn 4 Role DB, hiệu năng Index. |
