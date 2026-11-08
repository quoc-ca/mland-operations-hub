# Đặt nhiều vé theo giỏ: quy trình của khách (Cách 1)

Tác giả: Hồ Hoàng Quốc. Ngày: 07/11/2026. Trạng thái: đề xuất, chưa triển khai.

## 1. Mục tiêu

Cho phép khách đặt nhiều vé khác nhau (khác package, khác session) trong một lần, xác nhận một lần và trả cọc một lần. Phương án này giữ nguyên mô hình hiện có: mỗi người là một booking riêng, các booking thuộc cùng một nhóm đặt chỗ.

Ví dụ: khách đặt Pha cà phê 12/11 sáng cho 2 người và Làm gốm 13/11 chiều cho 1 người. Hệ thống tạo 3 booking (mỗi người một vé) trong một nhóm.

## 2. Quy trình của khách

| Bước | Khách làm gì | Hệ thống làm gì |
|---|---|---|
| 1. Chọn vé | Chọn package, session, số người rồi bấm "Thêm vào giỏ". Có thể thêm vé khác package, khác session. | Chỉ hiển thị số chỗ còn lại, chưa giữ chỗ. |
| 2. Xem giỏ | Xem danh sách vé, tiền cọc từng vé và tổng cọc. Sửa hoặc xóa vé. | Tính tiền theo từng vé (cọc 50% giá). |
| 3. Nhập liên hệ | Nhập email và số điện thoại một lần cho cả giỏ. | Dùng chung cho các vé trong nhóm. |
| 4. Xác nhận | Bấm "Xác nhận đặt chỗ" một lần. | Giữ chỗ tất cả vé trong một giao dịch, giữ 10 phút. Nếu một vé hết chỗ thì báo rõ vé nào và không giữ vé nào (tất cả hoặc không gì cả). Giỏ vẫn còn để khách bỏ vé hoặc đổi session. |
| 5. Trả cọc | Thanh toán một lần cho tổng cọc cả nhóm trong 10 phút. | Nhận kết quả thanh toán đã xác minh, xác nhận tất cả vé trong nhóm và chia tiền cọc về từng vé. Hết 10 phút mà chưa trả thì cả nhóm tự hủy và nhả chỗ. |
| 6. Nhận vé | Nhận các vé, mỗi người một mã QR. Tra cứu được theo mã nhóm hoặc mã từng vé. | Gửi vé tới email hoặc số điện thoại liên hệ. |

## 3. Khi khách đến shop (giữ nguyên quy trình hiện có, theo từng vé)

| Bước | Diễn ra thế nào |
|---|---|
| Check-in | Mở từ 60 phút trước giờ bắt đầu. Nhân viên quét QR của từng người. |
| Trong buổi | Dịch vụ thêm (nếu có) được ghi vào đúng hóa đơn của vé đó, sau khi khách đồng ý. |
| Kết thúc buổi | Nhân viên xác nhận buổi đã kết thúc, người chưa check-in được ghi là vắng. Manager có 24 giờ để sửa điểm danh. |
| Checkout | Mỗi vé trả phần còn lại (tổng tiền trừ cọc, cộng dịch vụ thêm), rồi nhận sản phẩm. |
| Sau đó | Điểm thưởng tính theo từng vé nhưng cộng vào ví của người đặt vé, sau khi vé đã checkout và hết 24 giờ sửa điểm danh. Người đặt vé có thể đánh giá. |

## 4. Quy tắc về tiền, hủy, điểm và đánh giá (theo chính sách đã có)

- Cọc 50% và không hoàn khi khách hủy hoặc vắng. Chỉ hoàn trong trường hợp ngoại lệ do Mland gây ra.
- Khách chỉ hủy được cả một booking (một vé), không hủy một phần.
- Đổi lịch không tự làm online. Khách liên hệ shop, Manager đổi cho từng booking.
- Một người vắng thì booking của người đó đóng ở số cọc đã thu. Những người còn lại checkout bình thường.
- Điểm thưởng và đánh giá thuộc về người đặt vé (Member). Người đi cùng không cần có tài khoản. Khách vãng lai (Guest) không có điểm.

## 5. Phần mới và phần giữ nguyên

| Phần mới | Phần giữ nguyên |
|---|---|
| Giỏ vé ở trang đặt | Check-in bằng QR, điểm danh, vắng mặt |
| Nhóm đặt chỗ (gom các vé) | Hóa đơn mở, dịch vụ thêm, checkout từng vé |
| Giữ chỗ cả nhóm (tất cả hoặc không gì cả) | Hủy, đổi lịch, hoàn tiền ngoại lệ |
| Thanh toán cọc một lần cho cả nhóm | Tích điểm sau 24 giờ, đánh giá |
| Hết hạn và hủy theo nhóm | Hóa đơn cuối theo từng vé |

## 6. Vì sao không chọn "một mã, nhiều dòng"

Phương án đó buộc cả booking dùng chung một hóa đơn, nên chỉ chốt được sau buổi cuối cùng:

- Khách học buổi 12/11 phải chờ đến buổi cuối mới nhận sản phẩm và điểm thưởng.
- Người bỏ một dòng vẫn bị tính đủ tiền, vì quy tắc hiện tại là có người đến thì cả booking checkout đủ.
- Không hủy riêng được một dòng.
- Muốn sửa phải tách hóa đơn, hủy và vắng mặt theo từng dòng, tức là quay lại đúng Cách 1 nhưng tốn công hơn.

## 7. Các điểm cần thống nhất

1. Checkout nhóm: hiện mỗi vé checkout riêng, nên nhóm 3 người phải thanh toán 3 lần ở quầy. Có thể thêm checkout theo nhóm (mở theo mã nhóm, trả một lần, chia về từng vé) ở giai đoạn sau.
2. Điểm và voucher của Member trong giỏ: giai đoạn đầu chưa hỗ trợ, vì cách chia về từng vé cần thống nhất quy tắc (tối thiểu 10 điểm mỗi lần dùng, trần 10% giá trị đơn).
3. Số vé tối đa mỗi nhóm: đề xuất 10.
4. Người vắng chỉ mất cọc của mình: cần xác nhận đây là chính sách mong muốn.
5. Thanh toán nhóm chạm vào phần đăng ký chủ thanh toán, IPN và đối soát do người khác phụ trách, nên cần trao đổi trước khi sửa.

## 8. Các giai đoạn triển khai đề xuất

| Giai đoạn | Nội dung |
|---|---|
| 1 | Nhóm đặt chỗ, giữ chỗ cả nhóm, cọc nhóm, hết hạn và hủy theo nhóm (chưa có điểm và voucher trong giỏ). |
| 2 | Giỏ vé ở frontend, trang thanh toán và tra cứu gom theo nhóm. |
| 3 | Chia điểm và voucher về từng vé cho Member. |
| 4 | Checkout theo nhóm tại quầy (nếu cần). |
