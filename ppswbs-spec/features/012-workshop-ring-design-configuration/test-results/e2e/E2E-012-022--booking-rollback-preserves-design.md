---
format_version: 2
id: E2E-012-022
status: SUPERSEDED
superseded_by: 2026-11-22 saved-design recommendation baseline
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: B
source_ref: spec.md#4
target_url: http://localhost:5173/workshops
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: booking-rollback-preservation
  account_ref: member.test@mland.vn
  isolation_key: e2e-012-022-rollback
cleanup:
  required: false
---

# E2E-012-022: Huỷ bỏ quy trình đặt chỗ (Rollback) bảo toàn mẫu thiết kế ở trạng thái khả dụng

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Người dùng đang trong luồng đặt chỗ gắn mẫu thiết kế nhưng hủy bỏ hoặc hết hạn thanh toán cọc booking. Mẫu thiết kế không bị mất và được trả về trạng thái có thể tái sử dụng cho lượt đặt tiếp theo.
- **Phạm vi**: Khả năng bảo toàn dữ liệu khi có giao dịch thất bại.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đã chọn mẫu thiết kế vào luồng đặt chỗ.

## 3. Các bước thực hiện

### Bước 1 — Hủy thanh toán hoặc hủy bỏ bước đặt chỗ
- **Mục đích**: Kích hoạt hoàn trả trạng thái thiết kế.
- **Thao tác**: Bấm "Hủy đặt chỗ" hoặc để hết hạn phiên thanh toán cọc.
- **Kỳ vọng**:
  - Giao dịch đặt chỗ bị hủy bỏ.
  - Mẫu thiết kế nhẫn trong tài khoản quay trở về trạng thái khả dụng (`VALIDATED`).
  - Khách hàng có thể sử dụng lại mẫu thiết kế này cho buổi workshop khác.
- **Quy tắc**: `CONTINUE` nếu thiết kế còn nguyên; `STOP` nếu thiết kế bị mất hoặc kẹt trạng thái khóa.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Bảo toàn dữ liệu thiết kế hoàn hảo; hoàn trả trạng thái VALIDATED ngay khi booking hủy.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-022--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
