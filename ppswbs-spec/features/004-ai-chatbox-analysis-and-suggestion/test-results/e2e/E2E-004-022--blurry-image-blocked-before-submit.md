---
format_version: 2
id: E2E-004-022
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: B
source_ref: spec.md#3.1
target_url: http://localhost:5173/design-requests/new
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: blurry-image-filter
  isolation_key: e2e-004-022-blurry-image
cleanup:
  required: false
---

# E2E-004-022: Phát hiện và chặn ảnh quá mờ trước khi gửi yêu cầu

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Người dùng tải lên ảnh có chất lượng mờ, không rõ nét chi tiết nhẫn. Hệ thống phát hiện ngay và hiển thị thông báo yêu cầu đổi ảnh trước khi cho phép bấm gửi.
- **Phạm vi**: Sàng lọc chất lượng đầu vào của ảnh.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Chuẩn bị 1 file ảnh có độ mờ cao (out of focus, motion blur).

## 3. Các bước thực hiện

### Bước 1 — Tải ảnh mờ lên biểu mẫu
- **Mục đích**: Kích hoạt bộ kiểm tra chất lượng ảnh.
- **Thao tác**: Chọn file ảnh mờ và tải lên.
- **Kỳ vọng**:
  - Badge cảnh báo chất lượng chuyển sang màu đỏ hoặc vàng cam: `Ảnh quá mờ, vui lòng thay ảnh rõ nét hơn`.
  - Nút gửi yêu cầu bị vô hiệu hóa hoặc bị chặn kèm nhắc nhở khi cố bấm gửi.
- **Quy tắc**: `CONTINUE` nếu ảnh mờ bị phát hiện; `STOP` nếu hệ thống bỏ qua và báo PASS.

### Bước 2 — Thay thế bằng ảnh rõ nét
- **Mục đích**: Khắc phục lỗi và kiểm tra phục hồi giao diện.
- **Thao tác**: Xóa ảnh mờ, tải lên ảnh mới sắc nét.
- **Kỳ vọng**:
  - Badge chất lượng chuyển sang xanh: `Đạt tiêu chuẩn` (`PASS`).
  - Nút gửi yêu cầu được kích hoạt lại bình thường.
- **Quy tắc**: `CONTINUE` khi form phục hồi; `STOP` nếu vẫn bị khóa.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Ảnh mờ bị chặn chính xác với thông báo rõ ràng; thay ảnh nét khôi phục trạng thái sẵn sàng gửi.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-022--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
