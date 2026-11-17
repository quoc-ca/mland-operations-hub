# Nội dung cần hỗ trợ cho FE-08: Địa chỉ và VNPay

**Date created**: 2026-11-02
**Date updated**: 2026-11-02
**Author**: Thinh
**Last updated by**: Thinh

Chào Sơn, mình cần hỗ trợ hai phần để nối vào checkout FE-08:

## 1. Địa chỉ nhận hàng

- Member có thể thêm, sửa, xóa, chọn và đặt địa chỉ mặc định trong profile hoặc khi checkout.
- Mỗi địa chỉ gồm tên người nhận, số điện thoại, tỉnh/thành, quận/huyện, phường/xã và địa chỉ chi tiết. Các cấp hành chính chọn từ danh sách chuẩn; địa chỉ cấp 4 của GHTK có thể bổ sung khi cần.
- FE-08 lấy địa chỉ đã chọn để tính phí GHTK và lưu lại thông tin tại thời điểm đặt hàng. Sửa địa chỉ trong profile sau đó không làm đổi đơn cũ.
- Hiện tại chỉ cần tính phí GHTK thật; tạo vận đơn và trạng thái giao hàng vẫn giả lập.

Nhờ Sơn cho mình biết API sổ địa chỉ hiện có hoặc dự kiến cung cấp để FE-08 nối vào.

## 2. Thanh toán VNPay

- Một lần checkout gộp có thể gồm đơn từ nhiều chi nhánh nhưng chỉ thanh toán VNPay một lần, bằng VND.
- Nhờ Sơn hỗ trợ đăng ký thanh toán cho `GroupedCheckout`, tạo URL VNPay và chuyển kết quả đã xác minh về FE-08 để cập nhật trạng thái đơn.
- Cần kiểm tra chữ ký, số tiền và xử lý callback lặp hoặc đến sau khi đơn đã hết hạn/hủy. Trang quay về từ VNPay chỉ hiển thị kết quả, không tự xác nhận thanh toán thành công.
- Đơn 0 đồng không cần chuyển sang VNPay.

Nhờ Sơn gửi lại contract/API tạo thanh toán và cách nhận kết quả sau khi chốt để hai phần nối với nhau.
