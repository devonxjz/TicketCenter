# THAI-01 — M0 Refund model checkpoint

Trạng thái: đang làm, chưa nghiệm thu M0.

## Phạm vi đã thực hiện

- Nhánh: `feature/thai/thai-01-refund-foundation`
- Model: `src/main/java/vn/ticketscenter/fulfillment/model/Refund.java`
- Test hành vi: `src/test/java/vn/ticketscenter/fulfillment/model/RefundTest.java`
- Commit: ghi sau khi checkpoint này được kiểm tra

Model đã có các thuộc tính nghiệp vụ của Refund, factory cho customer refund và
payment compensation, cùng các chuyển trạng thái approve/reject/attempt/result
và zero-value completion. Các liên kết Order/Ticket/Payment hiện giữ bằng UUID
vì các model sở hữu bởi Liêm chưa tồn tại trong baseline.

## Kiểm chứng

| Lệnh | Kỳ vọng | Thực tế |
|---|---|---|
| `mvn -B test` | Build thành công | PASS, compile 6 source + 1 test source |
| `java -cp target/classes:target/test-classes vn.ticketscenter.fulfillment.model.RefundTest` | Không có assertion failure | PASS |
| `git diff --check` | Không có whitespace error | PASS |

Maven Surefire nhận diện lớp test nhưng chạy 0 test vì test stack JUnit chung
chưa được khóa. Bằng chứng hành vi hiện tại là lệnh `main` dependency-free ở
trên, không được xem là nghiệm thu tích hợp.

## Còn thiếu / bị chặn

- Chưa có persistence records, DTO, migration `0040_refunds_checkin.sql`,
  mapping Hibernate, SQL test và evidence database.
- Chưa thể dùng đúng chữ ký `Order`/`Ticket`/`Payment` của diagram cho đến khi
  các model sở hữu của Liêm được ghép.
- Lombok chưa được khai báo trong `pom.xml`; không tự thêm dependency khi
  dependency/tooling chung chưa được Khánh khóa.
