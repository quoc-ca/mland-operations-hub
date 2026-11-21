---
format_version: 2
id: E2E-004-025
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: N
source_ref: spec.md#4
target_url: http://localhost:5173/design-review-requests
actor_role: Staff
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: staff-accept-review
  account_ref: staff.test@mland.vn
  isolation_key: e2e-004-025-staff-accept
cleanup:
  required: true
  method: reset-request-state
---

# E2E-004-025: Staff phê duyệt (ACCEPTED) yêu cầu thiết kế kèm lý do bắt buộc

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Nhân viên (Staff) mở hàng đợi thẩm định, xem chi tiết mô tả và ảnh thiết kế, nhập văn bản giải thích bắt buộc và chọn chấp nhận (`ACCEPTED`).
- **Phạm vi**: Thao tác vận hành của nhân viên cửa hàng.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đăng nhập bằng tài khoản `staff.test@mland.vn`.
- [x] Có ít nhất 1 yêu cầu ở trạng thái `NEED_REVIEW`.

## 3. Các bước thực hiện

### Bước 1 — Mở hàng đợi và chọn yêu cầu cần duyệt
- **Mục đích**: Điều hướng vào chi tiết yêu cầu thẩm định.
- **Thao tác**: Tại `/design-review-requests`, chọn yêu cầu đang chờ duyệt.
- **Kỳ vọng**: Mở trang chi tiết, xem được ảnh và mô tả yêu cầu của khách hàng.
- **Quy tắc**: `CONTINUE` nếu mở được chi tiết; `STOP` nếu trang lỗi.

### Bước 2 — Nhập lý do và chọn ACCEPTED
- **Mục đích**: Ghi nhận quyết định phê duyệt.
- **Thao tác**: Chọn quyết định "Chấp nhận" (`ACCEPTED`), nhập văn bản giải thích: "Mẫu nhẫn khả thi, xưởng chế tác có sẵn khuôn và vật liệu vàng hồng theo yêu cầu", sau đó bấm "Xác nhận thẩm định".
- **Kỳ vọng**:
  - Trạng thái chuyển thành `ACCEPTED`.
  - Hiển thị hộp chuẩn bị báo giá cho khách hàng.
  - Ghi nhận lịch sử audit với tên và vai trò của Staff.
- **Quy tắc**: `CONTINUE` khi cập nhật thành công; `STOP` nếu lỗi thẩm định.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Quyết định ACCEPTED được ghi nhận cùng văn bản giải thích bắt buộc; chuyển trạng thái thành công và mở hộp báo giá.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-025--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
