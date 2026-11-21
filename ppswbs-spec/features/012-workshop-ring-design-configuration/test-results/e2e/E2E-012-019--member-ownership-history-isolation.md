---
format_version: 2
id: E2E-012-019
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: N
source_ref: spec.md#4
target_url: http://localhost:5173/account/design-history
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: member-design-isolation
  account_ref: member.test@mland.vn
  isolation_key: e2e-012-019-isolation
cleanup:
  required: false
---

# E2E-012-019: Phân lập quyền sở hữu thiết kế Member, nút xóa mềm và phân trang 5 mục/trang

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Member xem danh sách mẫu thiết kế nhẫn cá nhân. Kiểm tra:
  - Phân trang chuẩn 5 mục trên mỗi trang (pagination 5 items/page).
  - Không bị nhảy dòng nút bấm khi mô tả nhẫn quá dài (các nút cùng 1 hàng, mô tả xuống dòng nhưng không đổi chiều cao item).
  - Nút Xóa màu đỏ bên phải nút Chỉnh sửa với icon thùng rác, pop-up cảnh báo xác nhận xóa vĩnh viễn trước khi thực hiện soft delete.
- **Phạm vi**: Trải nghiệm quản lý mẫu thiết kế cá nhân.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đăng nhập bằng `member.test@mland.vn`.
- [x] Có từ 6 mẫu thiết kế trở lên trong tài khoản để kiểm tra phân trang.

## 3. Các bước thực hiện

### Bước 1 — Kiểm tra phân trang và bố cục nút bấm
- **Mục đích**: Xác minh phân trang 5 mục và đồng bộ UI.
- **Thao tác**: Quan sát danh sách mẫu thiết kế tại trang quản lý thiết kế.
- **Kỳ vọng**:
  - Hiển thị đúng 5 item ở trang 1; có thanh điều hướng phân trang.
  - Các nút bấm (Xem, Chỉnh sửa, Xóa) nằm trên cùng một dòng ngang hàng.
  - Mô tả dài xuống dòng mà không làm méo mó độ cao của item.
- **Quy tắc**: `CONTINUE` nếu layout chuẩn; `STOP` nếu vỡ layout hoặc nút bị rớt dòng.

### Bước 2 — Bấm xóa và kiểm tra pop-up cảnh báo
- **Mục đích**: Xác nhận pop-up xóa mềm.
- **Thao tác**: Nhấn vào nút Xóa màu đỏ (icon thùng rác).
- **Kỳ vọng**:
  - Pop-up cảnh báo xuất hiện: cảnh báo thiết kế sẽ mất vĩnh viễn và yêu cầu xác nhận.
  - Bấm xác nhận: thiết kế biến mất khỏi danh sách người dùng nhưng lưu trữ ở phía shop.
- **Quy tắc**: `CONTINUE` khi pop-up hoạt động đúng; `STOP` nếu xóa mà không cảnh báo.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Phân trang 5 item/trang hoàn hảo; nút bấm thẳng hàng; nút xóa đỏ với pop-up xác nhận hoạt động chuẩn xác.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-019--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
