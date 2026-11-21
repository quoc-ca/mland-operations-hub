---
format_version: 2
id: E2E-012-021
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: N
source_ref: spec.md#4
target_url: http://localhost:5173/workshops
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: booking-attachment-lock
  account_ref: member.test@mland.vn
  isolation_key: e2e-012-021-booking-lock
cleanup:
  required: true
  method: delete-booking-via-api
---

# E2E-012-021: Đính kèm mẫu thiết kế vào Workshop Booking và kiểm tra trạng thái khóa (LOCKED)

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Người dùng đặt chỗ workshop và đính kèm mẫu nhẫn đã thẩm định. Thiết kế chuyển sang trạng thái `ATTACHED_TO_BOOKING` và bị khóa không cho sửa đổi trực tiếp.
- **Phạm vi**: Tích hợp Workshop Booking và Ring Design.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Có mẫu nhẫn ở trạng thái `VALIDATED`.
- [x] Chọn 1 buổi workshop còn chỗ.

## 3. Các bước thực hiện

### Bước 1 — Đặt chỗ workshop đính kèm mẫu nhẫn
- **Mục đích**: Gắn kết cấu hình nhẫn vào buổi workshop.
- **Thao tác**: Trong quy trình đặt chỗ, chọn mẫu nhẫn đã lưu và hoàn tất bước xác nhận.
- **Kỳ vọng**: Mẫu nhẫn được liên kết với mã vé/đặt chỗ workshop.
- **Quy tắc**: `CONTINUE` khi liên kết thành công; `STOP` nếu đặt chỗ thất bại.

### Bước 2 — Thử chỉnh sửa mẫu nhẫn đã đính kèm
- **Mục đích**: Xác minh khóa chỉnh sửa.
- **Thao tác**: Vào xem thiết kế đã gắn booking và thử bấm chỉnh sửa thông số.
- **Kỳ vọng**:
  - Nút chỉnh sửa trực tiếp bị khóa; hiển thị thông báo thiết kế đã được gắn vào đơn đặt chỗ và hướng dẫn nhân bản (clone) nếu muốn tạo bản sửa đổi.
- **Quy tắc**: `CONTINUE` nếu bị khóa an toàn; `STOP` nếu cho phép sửa trực tiếp.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Mẫu nhẫn đính kèm booking được khóa an toàn; hỗ trợ nhân bản bản sao để chỉnh sửa linh hoạt.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-021--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
