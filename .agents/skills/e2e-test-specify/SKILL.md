---
name: e2e-test-specify
description: Create one traceable Markdown E2E/Integration test scenario for a PPSWBS feature tested with browsermcp.
metadata:
  short-description: Create E2E browser test scenario with Markdown case
---

# E2E Test Scenario Specify

Dùng kỹ năng này để tạo một kịch bản kiểm thử End-to-End (E2E) hoặc UI Integration test chuẩn hóa cho một tính năng trong PPSWBS, được thiết kế để thực thi thông qua công cụ `browsermcp`.

## 1. Khảo sát bắt buộc (Required Discovery)

Trước khi soạn kịch bản, Agent phải đọc:
1. File đặc tả tính năng `ppswbs-spec/features/<feature>/spec.md` (yêu cầu nghiệp vụ, acceptance criteria).
2. Thiết kế giao diện `design/ui-flow.md` hoặc `screen.md` (các màn hình, route, button, input fields).
3. Hợp đồng API liên quan `contracts/` (status code, payload kỳ vọng).
4. **Phân tích rủi ro đối nghịch:** kiểm tra tối thiểu input sai định dạng, submit lặp, timeout/tải chậm, mất phiên, sai quyền, dữ liệu trùng và lỗi 5xx. Nếu rủi ro áp dụng, tạo hoặc nêu rõ ca `A` (Abnormal) hoặc `B` (Boundary); không coi mọi feature chỉ có happy path là đã đủ.

## 2. Hợp đồng đầu ra (Output Contract)

- Đường dẫn file kịch bản: `ppswbs-spec/features/<feature>/test-results/E2E-<feature-number>-<NNN>--<lowercase-slug>.md`.
- Sử dụng đúng cấu trúc trong [template kịch bản](references/e2e-case-template.md).
- YAML Front matter bắt buộc gồm:
  - `id`: Mã kịch bản (ví dụ `E2E-003-001`).
  - `feature`: Tên thư mục feature (ví dụ `003-workshop-management`).
  - `test_level`: `e2e` (hoặc `integration`).
  - `test_case_type`: `N` (Normal/Happy path), `A` (Abnormal), hoặc `B` (Boundary).
  - `source_ref`: Truy vết tới yêu cầu trong `spec.md#FR-xxx` hoặc `tasks.md#xxx`.
  - `target_url`: Đường dẫn bắt đầu kiểm thử trên Vite frontend (ví dụ `http://localhost:5173/workshops`).
  - `actor_role`: Vai trò người dùng (Guest, Customer, Staff, Manager).
  - `browser_tool`: `browsermcp`.
  - `health_urls`: Các endpoint thật sự cần cho ca test (ít nhất frontend; backend khi ca test gọi backend).
  - `test_data`: Fixture/tài khoản test chuyên dụng và `isolation_key`; không ghi secret/mật khẩu thật.
  - `cleanup`: Cách hoàn nguyên hoặc đánh dấu dữ liệu thử nghiệm sau ca test.

## 3. Quy chuẩn lệnh browser trong kịch bản

Các bước hành động phải được viết bằng cú pháp lệnh trực quan của `browsermcp`:
- `goto <url>`: Mở đường dẫn trang.
- `click <target>`: Nhấp chuột vào phần tử (`getByRole`, `getByLabel`, `getByText`, CSS).
- `fill <target> "<text>"`: Nhập văn bản vào ô input.
- `select <target> "<value>"`: Chọn giá trị trong dropdown.
- `wait <ms>` hoặc `wait --text "<text>"`: Chờ xuất hiện văn bản hoặc chờ hiệu ứng animation.
- `snapshot -i`: Chụp cấu trúc accessibility DOM để tìm phần tử và lấy ref mới sau điều hướng.
- `get url`, `get text <target>`, `find "<text>"`: Đọc kết quả cần đối chiếu.
- `console --errors`, `requests --filter "<path>"`: Kiểm tra lỗi runtime và request khi assertion yêu cầu.

Không dùng các câu mô tả chung chung như *"Người dùng thao tác phù hợp"*, assertion có lựa chọn mơ hồ như *"A hoặc B"*, hoặc selector XPath dễ vỡ. Chia action thành các tiêu đề Markdown `### Step N — <mục đích>`; các dòng trong fenced block chỉ chứa lệnh Browser MCP, không có tiền tố số. Mọi assertion phải nêu lệnh đọc và giá trị/kết quả chính xác cần quan sát.

