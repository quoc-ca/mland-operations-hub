---
format_version: 2
id: E2E-012-016
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: N
source_ref: spec.md#4A.4
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
actor_role: Guest_Or_Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: multi-engraving-full-preview
  isolation_key: e2e-012-016-full-preview
cleanup:
  required: true
  method: delete-draft-via-api
---

# E2E-012-016: Hiển thị đầy đủ tất cả văn bản và hình ảnh khắc trên mô hình 3D/Preview nhẫn

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Khi người dùng cấu hình đồng thời 2 chữ khắc và 2 ảnh khắc, khu vực xem trước (Ring Preview) phải hiển thị đầy đủ cả 4 chi tiết khắc, không được chỉ hiển thị một chi tiết đầu tiên hoặc bị thiếu ảnh.
- **Phạm vi**: Đồng bộ hiển thị Preview đa chi tiết khắc.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đã nhập 2 văn bản khắc ("Forever", "Together") và 2 ảnh khắc (Logo trái tim, Chữ ký).

## 3. Các bước thực hiện

### Bước 1 — Quan sát mô hình xem trước nhẫn
- **Mục đích**: Xác minh toàn bộ các chi tiết khắc xuất hiện trên preview.
- **Thao tác**: Xoay mô hình hoặc quan sát các góc nhìn mặt trong và mặt ngoài của đai nhẫn.
- **Kỳ vọng**:
  - Chữ "Forever" và "Together" hiển thị đúng vị trí khắc đã chọn.
  - Cả 2 hình ảnh khắc hiển thị rõ ràng, không bị thay thế bằng ô trắng mặc định.
- **Quy tắc**: `CONTINUE` nếu đủ cả 4 chi tiết; `STOP` nếu thiếu chi tiết.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Khu vực preview kết xuất đồng thời đầy đủ 2 chữ khắc và 2 ảnh khắc laser với tọa độ chính xác.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-016--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
