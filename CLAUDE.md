# Hướng dẫn cho Claude

## Nạp đúng persona trước khi làm

Mỗi thư mục có `AGENT.md` riêng, là hợp đồng hành vi cho công việc trong thư mục đó.
**Đọc file tương ứng trước khi sửa bất cứ thứ gì trong thư mục đó.** Không áp persona
của thư mục này cho thư mục khác.

| Việc | Đọc trước |
| --- | --- |
| Code backend (Java/Spring) | `ppswbs_backend/AGENT.md` |
| Code frontend (React/Vite) | `ppswbs_frontend/AGENT.md` |
| Soạn/review spec, plan, tasks, contracts, data-model | `ppswbs-spec/AGENT.md` |
| QA, E2E, kịch bản kiểm thử | `ppswbs-testing/AGENT.md` |
| Sửa tài liệu, tracker, báo cáo | `documents/AGENT.md` |

Một nhiệm vụ chạm nhiều thư mục thì đọc từng file tương ứng.

## Hai file local-only cùng cấp AGENT.md

- `ERROR-LOG.md` — nhật ký blocker thiết kế. **Đọc trước khi code**; blocker còn mở
  liên quan nhiệm vụ phải xử lý trước. Khi gặp blocker mới: dừng, ghi log, báo lại.
- `RUN-PROJ-DEV.md` — runbook chạy local. Kiểm tra trước khi chạy ứng dụng.

Cả hai bị gitignore. Không commit chúng, cũng không commit `.env` hay `logs/`.

## Cách làm việc

- Gộp nhiều lệnh shell vào một lần gọi thay vì gọi lẻ nhiều lần.
- Chạy test hẹp theo class đang sửa; chỉ chạy cả suite khi được yêu cầu hoặc trước khi bàn giao.
- Không tự commit, push, merge hay deploy khi chưa được yêu cầu rõ.
- Không sửa migration đã áp dụng, không bỏ constraint hay hạ kỳ vọng test để lấy kết quả pass.

## Dùng subagent để khảo sát

Khi cần khảo sát rộng — tìm một thứ qua nhiều file, xem một module chưa quen hoạt động
ra sao, hoặc đọc tài liệu dài chỉ để rút vài kết luận — **dùng subagent và chỉ nhận về
kết luận**, thay vì đọc hàng loạt file vào ngữ cảnh chính. Mỗi file đọc trực tiếp sẽ bị
đọc lại ở mọi lượt sau trong phiên.

Đọc thẳng khi chỉ cần một hai file, hoặc khi cần nội dung chính xác để sửa code.

## Mở phiên mới cho mỗi đầu việc

Chi phí một lượt tỉ lệ với độ dài hội thoại, nên một phiên kéo qua nhiều đầu việc đắt lên
rất nhanh. Mỗi task/bước nên là một phiên riêng, mở đầu bằng brief ngắn:

```
Làm <task/bước>. Nhánh <tên>. <trạng thái đã xong>.
Đọc CLAUDE.md, <AGENT.md tương ứng> và tasks.md của feature trước.
```

Giữ nguyên phiên khi chỉ sửa tiếp hoặc hỏi lại về thứ vừa làm.

Claude chủ động nhắc một dòng khi thấy hợp lý mở phiên mới: vừa xong và commit một đầu
việc rồi chuyển sang việc khác, đổi sang thư mục/persona khác, hoặc hội thoại đã dài.
Kèm sẵn brief để copy. Nhắc một lần, không lặp lại nếu người dùng muốn làm tiếp ở đây.
