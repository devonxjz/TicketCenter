# Hoàn thiện báo cáo Hệ quản trị cơ sở dữ liệu

Nguồn chuẩn: [spec.md mục 14.12](../../spec.md#1412-báo-cáo-sản-phẩm-nộp-và-bảo-vệ) và [diagram.md](../classdiagram/diagram.md). Đồng bộ ngày 06/10/2026. Báo cáo Word hiện có mô tả thiết kế, chưa có kết quả SQL hoặc ứng dụng chạy thực tế. Các mục nghiệm thu dưới đây chỉ được ghi đạt khi có bằng chứng.

## Bố cục cần dùng khi hoàn thiện hồ sơ nộp

| Chương | Nội dung và nguồn có thể tái sử dụng | Bằng chứng còn cần bổ sung |
|---|---|---|
| 1. Tổng quan | Bài toán, phạm vi, tác nhân và chức năng từ BAOCAO.md; mô hình đúng 15 lớp | Xác nhận quy mô nhóm, vai trò, phạm vi và đóng góp thực tế |
| 2. Thiết kế dữ liệu | Diagram, THIET-KE-DU-LIEU.md; 15 lớp cùng các bảng kỹ thuật | ERD vật lý, khóa ứng viên, phụ thuộc hàm và chứng minh 3NF |
| 3. Cài đặt database | Danh mục C/V/SP/F/TR/IX ở spec mục 14; task ngày 2 và các ngày theo module | Script dựng database và dữ liệu; giải thích caller, ca lỗi, đa dòng; execution plan và benchmark trước/sau index |
| 4. Giao dịch, khôi phục và quyền | TX01–TX17, R01–R04; hợp đồng transaction và thứ tự khóa | Hai phiên cạnh tranh, commit/rollback, recovery, GRANT/REVOKE/DENY bằng đúng principal |
| 5. Ứng dụng | MVC Servlet/JSP/JPA, hợp đồng API, 24 khung UI | Ảnh ứng dụng kết nối SQL Server; CRUD, tìm kiếm, báo cáo, gọi SP/Function, xử lý lỗi và phân quyền |
| 6. Kết quả và phát triển | Đối chiếu từng yêu cầu/rubric, hạn chế và kế hoạch tiếp theo | Chỉ ghi kết quả đã có log/test; phân biệt mục đạt, chưa đạt và chưa kiểm chứng |

## Quy tắc biên tập và hồ sơ

- Giữ 50–100 trang nội dung, không tính bìa, mục lục và phụ lục; Times New Roman 13, giãn dòng 1.5 theo đặc tả. Đưa danh mục 37 use case chi tiết, SQL dài và nhật ký vào phụ lục; thân báo cáo tập trung thiết kế, quyết định và bằng chứng.
- Giữ danh mục hình/bảng, chú thích, nguồn, tài liệu tham khảo. Khung UI minh họa không được gắn nhãn ảnh ứng dụng thực chạy.
- Chuẩn bị slide tối đa 15 trang, database .bak hoặc bộ .sql dựng đủ đối tượng/quyền/seed, mã nguồn .zip/.rar và hướng dẫn cài/chạy; chốt tên nhóm và kênh nộp theo giảng viên.
- Phân công hiện có 5 người, khác yêu cầu nhóm 3–4 ở spec; xác nhận với giảng viên, không tự xóa tên thành viên. Mốc 10/10 và lịch task 21 ngày là kế hoạch hiện có, cần kiểm lại khi xác nhận năng lực và tiến độ thực tế.
