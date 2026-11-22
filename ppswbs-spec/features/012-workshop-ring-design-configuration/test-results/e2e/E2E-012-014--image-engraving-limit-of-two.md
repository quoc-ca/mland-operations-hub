---
format_version: 2
id: E2E-012-014
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: B
source_ref: spec.md#4A.2
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
actor_role: Guest_Or_Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: image-engraving-two-limit
  isolation_key: e2e-012-014-image-limit
cleanup:
  required: true
  method: delete-draft-via-api
---

# E2E-012-014: Giới hạn tối đa 2 hình ảnh khắc laser và thông báo lấy vân tay tại cửa hàng

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Người dùng tải ảnh mẫu khắc laser (chữ ký, logo, hình vẽ tay). Hệ thống cho phép tải tối đa 2 ảnh, tự động đóng khung 160px x 160px, hiển thị đúng dòng thông báo "Để có thể khắc lazer vân tay, các bạn hãy đến cửa hàng lấy vân tay trực tiếp nhé". Ảnh thứ 3 bị chặn.
- **Phạm vi**: Khắc hình ảnh laser và kiểm tra nội dung text hướng dẫn vân tay.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Có sẵn các file ảnh mẫu chữ ký/logo.

## 3. Các bước thực hiện

### Bước 1 — Kiểm tra dòng thông báo hướng dẫn
- **Mục đích**: Đảm bảo copy text đúng theo yêu cầu.
- **Thao tác**: Cuộn đến cuối mục khắc hình ảnh.
- **Kỳ vọng**:
  - Dòng text: "Tải lên hình ảnh mẫu (chữ ký, logo, hình vẽ tay, chữ viết tay). Hệ thống tự động chuyển sang màu đen trắng laser và đóng khung 160px x 160px để bạn chọn vị trí khắc đẹp nhất (tối đa 2 ảnh)."
  - Thông báo: "Để có thể khắc lazer vân tay, các bạn hãy đến cửa hàng lấy vân tay trực tiếp nhé" hiển thị rõ ràng.
- **Quy tắc**: `CONTINUE` nếu dòng chữ chính xác; `STOP` nếu sai nội dung text.

### Bước 2 — Tải 2 ảnh và kiểm tra chặn ảnh thứ 3
- **Mục đích**: Xác minh giới hạn tối đa 2 ảnh.
- **Thao tác**: Tải liên tiếp 2 ảnh và thử tải tiếp ảnh thứ 3.
- **Kỳ vọng**:
  - Nhận 2 ảnh thành công; hiển thị khung 160px x 160px.
  - Không cho phép chọn thêm ảnh thứ 3 (nút tải bị vô hiệu hóa).
- **Quy tắc**: `CONTINUE` khi giới hạn đúng; `STOP` nếu nhận 3 ảnh.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Hiển thị chuẩn xác text hướng dẫn vân tay laser; tải 2 ảnh đóng khung 160x160px và chặn ảnh thứ 3 thành công.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-014--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
