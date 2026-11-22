---
format_version: 2
id: E2E-012-017
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: N
source_ref: spec.md#4A.4
target_url: http://localhost:5173/account/design-history
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: save-and-reload-restoration
  account_ref: member.test@mland.vn
  isolation_key: e2e-012-017-reload
cleanup:
  required: true
  method: delete-design-via-api
---

# E2E-012-017: Lưu thiết kế và nạp lại phục hồi 100% chi tiết khắc, đá quý và tham số ảnh

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Người dùng lưu thiết kế nhẫn có đầy đủ chất liệu, đá quý, 2 chữ khắc, 2 ảnh khắc kèm tham số contrast/sharpness. Khi tải lại trang hoặc mở xem lại mẫu thiết kế đã lưu, hệ thống khôi phục nguyên vẹn 100% tất cả các thuộc tính mà không mất dữ liệu.
- **Phạm vi**: Tính toàn vẹn của dữ liệu lưu trữ và nạp lại trạng thái.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đã lưu mẫu thiết kế nhẫn phức tạp thành công trong tài khoản Member.

## 3. Các bước thực hiện

### Bước 1 — Mở xem lại mẫu thiết kế đã lưu
- **Mục đích**: Tải lại cấu hình từ backend.
- **Thao tác**: Vào danh sách mẫu thiết kế, chọn xem lại mẫu nhẫn vừa lưu.
- **Kỳ vọng**:
  - Không xuất hiện lỗi "Đã xảy ra lỗi nội bộ máy chủ".
  - Thời gian tải mẫu nhẫn nhanh chóng (< 5 giây, đạt tiêu chí dưới 30 giây).
  - Khôi phục chính xác: chất liệu, kích cỡ, danh sách đá quý, vị trí khắc chữ, nội dung khắc chữ, ảnh khắc laser, độ tương phản và độ sắc nét.
- **Quy tắc**: `CONTINUE` khi nạp lại trọn vẹn; `STOP` nếu báo lỗi 500 hoặc mất thông số.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Khôi phục 100% dữ liệu thiết kế; không có lỗi nội bộ máy chủ; thời gian phản hồi dưới 3 giây.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-017--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
