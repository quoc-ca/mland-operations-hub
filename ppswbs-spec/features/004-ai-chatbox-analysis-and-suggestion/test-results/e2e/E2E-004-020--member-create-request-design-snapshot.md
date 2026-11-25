---
format_version: 2
id: E2E-004-020
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: N
source_ref: spec.md#6
target_url: http://localhost:5173/account/design-requests
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: member-design-snapshot
  account_ref: member.test@mland.vn
  isolation_key: e2e-004-020-member-snapshot
cleanup:
  required: true
  method: delete-request-via-api
---

# E2E-004-020: Member tạo yêu cầu thiết kế đính kèm bản thiết kế nhẫn Feature-012

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Thành viên (Member) đã lưu mẫu nhẫn trong Feature-012 chọn đính kèm snapshot mẫu nhẫn đó vào yêu cầu thiết kế gia công riêng.
- **Phạm vi**: Tích hợp liên tính năng giữa Feature-012 và Feature-004.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đăng nhập bằng tài khoản `member.test@mland.vn`.
- [x] Đã có ít nhất 1 mẫu thiết kế nhẫn lưu trong tài khoản.

## 3. Các bước thực hiện

### Bước 1 — Mở form yêu cầu và chọn mẫu nhẫn đã lưu
- **Mục đích**: Đính kèm mẫu nhẫn từ danh sách thiết kế cá nhân.
- **Thao tác**: Mở biểu mẫu tạo yêu cầu, chọn mục "Đính kèm mẫu thiết kế nhẫn đã lưu", chọn mẫu nhẫn mong muốn.
- **Kỳ vọng**:
  - Thông tin tóm tắt mẫu nhẫn (chất liệu, kích cỡ, đá quý, nội dung khắc) hiển thị trực quan trong form.
- **Quy tắc**: `CONTINUE` nếu chọn được thiết kế; `STOP` nếu không nạp được danh sách thiết kế.

### Bước 2 — Nhập ghi chú bổ sung và gửi
- **Mục đích**: Gửi yêu cầu kèm snapshot bất biến.
- **Thao tác**: Nhập thêm ghi chú gia công riêng và nhấn nút gửi.
- **Kỳ vọng**:
  - Yêu cầu được tạo với mã tham chiếu thiết kế `designReference`.
  - Xuất hiện trong danh sách "Yêu cầu thiết kế của tôi" tại `/account/my-custom-orders` (tab Requests).
- **Quy tắc**: `CONTINUE` khi hiển thị đúng trong danh sách; `STOP` nếu dữ liệu bị mất.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Đính kèm snapshot Feature-012 thành công; bản ghi yêu cầu lưu trữ snapshot bất biến và hiển thị trong My Custom Orders.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-020--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
