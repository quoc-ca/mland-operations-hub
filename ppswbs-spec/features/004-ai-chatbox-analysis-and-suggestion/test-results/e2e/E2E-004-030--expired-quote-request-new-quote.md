---
format_version: 2
id: E2E-004-030
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: B
source_ref: spec.md#6
target_url: http://localhost:5173/account/my-custom-orders
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: expired-quote-renewal
  account_ref: member.test@mland.vn
  isolation_key: e2e-004-030-expired-quote
cleanup:
  required: true
  method: reset-order-state
---

# E2E-004-030: Xử lý báo giá quá hạn và kích hoạt yêu cầu báo giá mới

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Báo giá quá hạn thanh toán chuyển sang trạng thái hết hạn (`EXPIRED`). Nút thanh toán bị ẩn/vô hiệu hóa, thay vào đó hiển thị nút "Yêu cầu báo giá mới". Member bấm nút này để yêu cầu xưởng định giá lại.
- **Phạm vi**: Khả năng tự phục hồi và tái tạo báo giá của khách hàng.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đơn hàng của Member có báo giá đã quá thời hạn `quoteExpiresAt`.

## 3. Các bước thực hiện

### Bước 1 — Xem đơn hàng quá hạn tại My Custom Orders
- **Mục đích**: Quan sát giao diện khi quote hết hạn.
- **Thao tác**: Mở tab Orders và tìm đơn hàng hết hạn.
- **Kỳ vọng**:
  - Badge trạng thái: `Hết hạn`.
  - Cảnh báo: "Báo giá này đã hết hạn thanh toán".
  - Nút "Thanh toán toàn bộ" KHÔNG hiển thị.
  - Nút "Yêu cầu báo giá mới" hiển thị.
- **Quy tắc**: `CONTINUE` nếu nút yêu cầu báo giá mới có mặt; `STOP` nếu vẫn cho thanh toán đơn hết hạn.

### Bước 2 — Bấm yêu cầu báo giá mới
- **Mục đích**: Gửi yêu cầu tái tạo báo giá.
- **Thao tác**: Bấm "Yêu cầu báo giá mới".
- **Kỳ vọng**:
  - Trạng thái đơn hàng chuyển sang `Đang chờ báo giá` (`QUOTE_PENDING`).
  - Thông báo gửi yêu cầu thành công tới ban quản lý xưởng.
- **Quy tắc**: `CONTINUE` khi cập nhật đúng; `STOP` nếu thất bại.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Báo giá hết hạn khóa thanh toán an toàn; nút yêu cầu báo giá mới kích hoạt thành công chuyển về QUOTE_PENDING.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-030--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
