---
format_version: 2
id: E2E-012-015
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: N
source_ref: spec.md#4A.3
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
actor_role: Guest_Or_Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: image-slider-preprocessing
  isolation_key: e2e-012-015-sliders
cleanup:
  required: true
  method: delete-draft-via-api
---

# E2E-012-015: Tiền xử lý ảnh khắc laser với thanh trượt Contrast và Sharpness

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Người dùng điều chỉnh thanh trượt Độ tương phản và Độ sắc nét của ảnh khắc laser. Hình ảnh preview thay đổi trực tiếp theo thời gian thực (real-time visual feedback) tương ứng với giá trị thanh trượt.
- **Phạm vi**: Xử lý đồ họa trực quan trên canvas trình duyệt.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đã tải lên 1 ảnh khắc mẫu trong hộp đóng khung 160px x 160px.

## 3. Các bước thực hiện

### Bước 1 — Kéo thanh trượt Contrast và Sharpness
- **Mục đích**: Thay đổi giá trị tương phản và sắc nét.
- **Thao tác**: Kéo slider Contrast từ 1.0 lên 1.3 và Sharpness từ 1.0 lên 1.6.
- **Kỳ vọng**:
  - Giá trị số cạnh thanh trượt cập nhật tương ứng.
  - Hình ảnh khắc laser trong khung xem trước trở nên đậm nét và tương phản rõ rệt.
- **Quy tắc**: `CONTINUE` nếu preview cập nhật mượt; `STOP` nếu slider không có tác dụng.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Thanh trượt phản hồi nhạy bén; hình ảnh xử lý tương phản và sắc nét trực quan trên canvas.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-015--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
