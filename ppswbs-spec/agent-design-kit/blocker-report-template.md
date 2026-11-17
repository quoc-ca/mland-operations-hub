# Bị chặn triển khai: [Tên tác vụ]

Mẫu báo thiếu, không phải đề xuất thiết kế hay quyền làm một phần. Đọc [quy tắc](README.md) và [checklist](readiness-checklist.md). Không tự sửa template này, checklist reviewer hoặc trạng thái phê duyệt khi báo thiếu.

## Tác vụ và hiện trạng

- Yêu cầu/phạm vi được giao: [nội dung và nguồn].
- Feature/phiên bản đang đối chiếu: [đường dẫn + mục/mốc nội dung].
- Kết luận: **BỊ CHẶN — dừng toàn tác vụ triển khai**.
- Đã làm trước khi phát hiện: [khảo sát/kiểm tra/thay đổi đã có, kết quả, phần chưa xác minh; hoặc chưa có thay đổi].
- Việc dừng gồm production/test code, scaffold, migration, cấu hình triển khai và deploy. Không tự hoàn tác thay đổi đã có hoặc tự triển khai phần độc lập.

## Thông tin cần bổ sung hoặc quyết định

| ID | Thiếu / mâu thuẫn / không khả thi | Nguồn cụ thể | Ảnh hưởng | Câu hỏi cần chốt | Người cần quyết định | Điều kiện nhận lại |
| --- | --- | --- | --- | --- | --- | --- |
| B01 | [sự thật còn thiếu, không điền giải pháp đoán] | [file + mục, lỗi đọc hoặc hai nguồn xung đột] | [vì sao không thể triển khai đúng] | [câu hỏi trả lời cụ thể được] | [người có thẩm quyền; chưa rõ thì yêu cầu xác định] | [nội dung cần rõ + bằng chứng duyệt + kiểm tra lại] |

## Bước chờ người dùng

[Nêu việc cần cung cấp/chốt. Có thể yêu cầu giao riêng công việc hoàn thiện thiết kế hoặc tách phạm vi độc lập đủ rõ. Không tự mở nhiệm vụ nghiên cứu/soạn nháp trong lúc chờ.]

“Cứ đoán/code trước” không giải quyết các mục trên. Khi có bổ sung, kiểm tra lại nội dung, dependency, tính nhất quán và bằng chứng duyệt trước khi sửa code. Không coi im lặng, timeout hoặc agent tự tick là chấp thuận.

Nếu file này hoặc file vận hành khác thiếu/không đọc được, báo ngắn bằng văn bản: phạm vi, file/lỗi, ảnh hưởng, việc cần khôi phục, điều kiện kiểm tra lại. Không tạo mẫu thay thế hoặc code tiếp; việc khôi phục kit phải được giao riêng.
