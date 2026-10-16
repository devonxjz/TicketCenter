# KHANH-01–06 — Ghép dev vào main theo cấu trúc DAO

Ngày: 16/10/2026 (Asia/Saigon).

## Phạm vi

- Nguồn: `dev` tại `6dce11fc6204a1295086218af6adf3af1293f912`, gồm clean code
  KHANH-01–06 (`0fa0664`) và bằng chứng kiểm tra (`009451a`).
- Đích: `main` tại `cbdd93f61a1f0f86c31a55a5faa14e1a78e8810f`.
- Giữ nội dung mới của dev cho soát vé, hoàn tiền và hủy sự kiện; giải quyết
  năm xung đột bằng package/import `dao` hiện có trên main. Tên lớp
  `*Repository` theo quy ước main được giữ nguyên.
- Sửa import DAO trong JSP hoàn vé, tên lớp phản chiếu và điều kiện kiểm thử
  identity, cùng đường dẫn/tên suite đã chuyển sang DAO trong workflow.
  Điều kiện bật kiểm thử tích hợp và tiêu chí kiểm tra vẫn giữ nguyên.
- Không sửa SQL, migration, dependency hoặc cấu hình triển khai.

## Kiểm tra trên bản ghép

- Maven/JDK 25: đóng gói WAR thành công; 1.066 ca Java, 923 đạt,
  143 bỏ qua, 0 failure/error. `ExternalTestIsolationTest`: 10/10 đạt.
- Node: 230 ca, 217 đạt, 13 bỏ qua. Bài logout cần máy chủ localhost bị
  sandbox chặn kết nối ở lượt đầu; chạy lại toàn bộ suite `site-shell`
  ngoài sandbox đạt 4/4. Các suite còn lại đạt ngay lượt đầu.
- Jasper/Tomcat 11.0.25: biên dịch 7/7 JSP soát vé, hoàn vé, quản trị
  hoàn tiền và hủy sự kiện. Chỉ biên dịch; không khởi động ứng dụng.
- Kiểm tra dấu xung đột, package/import Java thuộc phần KHÁNH và khoảng
  trắng diff: đạt. Rà soát độc lập xác nhận nội dung dev chỉ được điều
  chỉnh đường dẫn DAO và các tham chiếu tương ứng.

Log cục bộ nằm trong `target/merge-java-final.log`,
`target/merge-node-verification.log` và
`target/merge-node-loopback-verification.log`; kết quả JSP tại
`target/merge-jsp-verification.log`.

Các ca SQL Server/Redis/SMTP cần fixture riêng và kiểm thử HTTP cần ứng dụng
đang chạy chưa được nghiệm thu trong lượt này; ca bỏ qua không được tính là đạt.
