---
format_version: 2
id: E2E-004-027
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: N
source_ref: spec.md#4
target_url: http://localhost:5173/account/my-custom-orders
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: clarification-revision-loop
  account_ref: member.test@mland.vn
  isolation_key: e2e-004-027-clarification
cleanup:
  required: true
  method: reset-request-state
---

# E2E-004-027: Vòng lặp yêu cầu làm rõ (CLARIFICATION_REQUIRED) và phản hồi chỉnh sửa

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Staff yêu cầu làm rõ thông tin chi tiết thiết kế. Member thấy trạng thái `CLARIFICATION_REQUIRED`, bấm nút "Phản hồi làm rõ", bổ sung mô tả và gửi lại. Yêu cầu chuyển về `NEED_REVIEW` để nhân viên xem lại.
- **Phạm vi**: Luồng tương tác hai chiều Member - Staff.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Yêu cầu thiết kế của `member.test@mland.vn` đang ở trạng thái `CLARIFICATION_REQUIRED`.
- [x] Lời nhắn của Staff: "Vui lòng cho biết thêm kích cỡ ngón tay (size nhẫn) và loại đá mong muốn".

## 3. Các bước thực hiện

### Bước 1 — Member xem yêu cầu làm rõ tại My Custom Orders
- **Mục đích**: Nhận diện trạng thái và thông báo làm rõ từ xưởng.
- **Thao tác**: Mở tab "Yêu cầu thiết kế" tại `/account/my-custom-orders`.
- **Kỳ vọng**:
  - Badge trạng thái màu vàng: `Cần làm rõ`.
  - Khung thông báo màu vàng hiển thị lời nhắn của Staff.
  - Nút "Phản hồi làm rõ" hiển thị rõ ràng.
- **Quy tắc**: `CONTINUE` nếu nút hiển thị; `STOP` nếu thiếu nút phản hồi.

### Bước 2 — Mở modal phản hồi và gửi nội dung bổ sung
- **Mục đích**: Cập nhật mô tả chỉnh sửa.
- **Thao tác**: Bấm "Phản hồi làm rõ", nhập: "Size nhẫn là 15, sử dụng đá CZ cao cấp chống trầy", bấm "Gửi phản hồi".
- **Kỳ vọng**:
  - Modal đóng lại, thông báo cập nhật thành công.
  - Trạng thái yêu cầu chuyển về `NEED_REVIEW` ("Chờ duyệt").
  - Phiên bản (version) tăng lên 2.
- **Quy tắc**: `CONTINUE` khi trạng thái quay lại NEED_REVIEW; `STOP` nếu thất bại.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Modal phản hồi làm rõ hoạt động mượt mà; gửi revision thành công và chuyển trạng thái về NEED_REVIEW.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-027--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
