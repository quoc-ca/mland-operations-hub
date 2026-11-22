---
format_version: 2
id: E2E-012-018
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: B
source_ref: spec.md#4
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: guest-session-expiry
  isolation_key: e2e-012-018-guest-expiry
cleanup:
  required: false
---

# E2E-012-018: Quản lý hết hạn phiên Guest 20 phút và hướng dẫn đăng nhập lưu mẫu

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Guest cấu hình nhẫn không hoạt động trong 20 phút. Phiên thiết kế tạm thời hết hạn; hệ thống nhắc nhở đăng nhập tài khoản để lưu giữ vĩnh viễn mẫu thiết kế.
- **Phạm vi**: Vòng đời phiên thiết kế ẩn danh của khách vãng lai.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Phiên thiết kế Guest đã trôi qua 20 phút không có hoạt động.

## 3. Các bước thực hiện

### Bước 1 — Kiểm tra cảnh báo hết hạn phiên
- **Mục đích**: Ghi nhận hết hạn phiên an toàn.
- **Thao tác**: Thử thực hiện lưu hoặc thẩm định khi phiên đã hết hạn.
- **Kỳ vọng**:
  - Xuất hiện thông báo: "Phiên thiết kế tạm thời đã hết hạn. Vui lòng đăng nhập hoặc tạo tài khoản để lưu lại mẫu thiết kế của bạn."
  - Cung cấp nút đăng nhập nhanh hoặc bắt đầu lại phiên mới.
- **Quy tắc**: `CONTINUE` nếu nhận đúng thông báo hết hạn; `STOP` nếu crash trang.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Xử lý hết hạn phiên 20 phút mượt mà; thông báo thân thiện hướng dẫn người dùng đăng nhập.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-018--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
