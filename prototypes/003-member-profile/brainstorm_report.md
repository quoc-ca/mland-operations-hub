# Brainstorm — prototype Member Profile 003

**Cập nhật theo spec mới:** prototype web/Figma đã tách đăng nhập đã xác minh (về trang chủ), đăng ký/chưa xác minh (tiếp tục kích hoạt rồi về trang chủ), bỏ policy gate và cho ACTIVE/PENDING_POLICY_ACCEPTANCE dùng hồ sơ. Các lựa chọn hình thức/đầu ra bên dưới được giữ; luồng hiện hành xem [handoff](FIGMA-HANDOFF.md). Trang chủ chỉ là đích minh họa, không mở rộng feature 003.

## Vấn đề và bằng chứng

Prototype cũ gom booking, thiết kế nhẫn và retail nên khó review một feature độc lập. Người dùng muốn bỏ toàn bộ code cũ, làm rõ frontend là bản mẫu và ưu tiên 003. Bằng chứng là yêu cầu trực tiếp và khảo sát source; chưa có usability study hay kết luận từ khách hàng thật.

Ba cách nhìn vấn đề: (1) repository gây nhầm prototype với ứng dụng production; (2) giao diện thiếu một hành trình hồ sơ tập trung; (3) người review khó kiểm tra các nhánh lỗi/gate trước khi backend được implement. Thiết kế mới giải quyết cả ba bằng thư mục theo feature, hành trình gọn và công cụ reviewer tách riêng.

## Các phương án đã cân nhắc

| Phương án | Điểm mạnh | Đánh đổi |
| --- | --- | --- |
| Chỉ Figma | Review bố cục và layer thuận tiện | Khó thử validation, dữ liệu nhập và các lỗi động |
| Chỉ web tĩnh | Thử tương tác thực tế, không cần backend | Thiếu bản Design để trao đổi và bàn giao |
| Web + Figma | Review cả tương tác lẫn thiết kế chỉnh sửa được | Cần đối chiếu hai đầu ra và ghi giới hạn font hệ thống |

Người dùng chọn web + Figma, toàn luồng 003, sáng/thanh lịch, VI/EN mặc định VI và tạo file Design mới. Không phát triển framework frontend hoặc tích hợp API thật để giải quyết nhu cầu review.

## Giả định cần kiểm tra

- Form một cột, nhãn rõ và giải thích email/điện thoại giúp hiểu đúng các field. Kiểm tra bằng task xem email, sửa tên và xóa điện thoại.
- Các nhánh lỗi/gate cần review trước khi code backend. Kiểm tra qua 24 tình huống và danh mục Figma.
- Thiết kế sáng phù hợp mục tiêu người dùng; chưa có bằng chứng nghiên cứu thương hiệu. Nếu người dùng không đọc được field/lỗi ở mobile hoặc không hoàn thành task, cần điều chỉnh UI thay vì mở rộng feature.

## Kết quả và giới hạn

Đối chiếu [handoff](FIGMA-HANDOFF.md) và [verification](VERIFICATION.md). Mục tiêu là người review tìm được từng trạng thái, hoàn thành luồng demo và hiểu đây là dữ liệu giả. Không coi ảnh đẹp hay test prototype là bằng chứng an toàn auth hoặc dữ liệu production.

Thông điệp bàn giao: “Bộ mẫu mới tập trung hồ sơ Member và các bước vào hồ sơ. Hãy review nội dung, thao tác và trạng thái lỗi ở đây trước khi triển khai theo spec 003.”
