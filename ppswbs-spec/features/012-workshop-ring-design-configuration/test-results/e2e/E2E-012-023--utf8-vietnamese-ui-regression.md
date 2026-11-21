---
format_version: 2
id: E2E-012-023
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: N
source_ref: spec.md#7
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
actor_role: Guest_Or_Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: utf8-vietnamese-ring-ui
  isolation_key: e2e-012-023-utf8
cleanup:
  required: false
---

# E2E-012-023: Kiểm tra hồi quy giao diện tiếng Việt UTF-8 chuẩn xác tại cấu hình nhẫn

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Toàn bộ nhãn, thông báo, hướng dẫn lấy vân tay, tooltip và các nút chức năng trên màn hình cấu hình nhẫn Feature-012 hiển thị đúng tiếng Việt có dấu, chuẩn UTF-8, không có bất kỳ lỗi mã hóa hay ký tự lạ nào.
- **Phạm vi**: Đảm bảo chất lượng hiển thị ngôn ngữ tiếng Việt.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Mở màn hình cấu hình nhẫn.

## 3. Các bước thực hiện

### Bước 1 — Rà soát toàn bộ văn bản giao diện
- **Mục đích**: Quét lỗi ký tự.
- **Thao tác**: Duyệt qua các tab: "Chất liệu & Size", "Đá quý", "Khắc laser", "Xem trước & Thẩm định".
- **Kỳ vọng**:
  - Dòng text: "Tải lên hình ảnh mẫu (chữ ký, logo, hình vẽ tay, chữ viết tay). Hệ thống tự động chuyển sang màu đen trắng laser và đóng khung 160px x 160px để bạn chọn vị trí khắc đẹp nhất (tối đa 2 ảnh)."
  - Dòng text: "Để có thể khắc lazer vân tay, các bạn hãy đến cửa hàng lấy vân tay trực tiếp nhé"
  - Các thông báo: "⚠️ Đã đạt giới hạn tối đa 10 viên đá quý.", "Tối đa 2 chữ khắc", "Tối đa 2 ảnh khắc".
  - Hiển thị 100% chuẩn xác, không có lỗi mojibake.
- **Quy tắc**: `CONTINUE` nếu tiếng Việt chuẩn; `STOP` nếu phát hiện lỗi font/ký tự.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Toàn bộ chuỗi tiếng Việt trên Feature-012 đều chuẩn UTF-8, sắc nét, đúng chính tả và hoàn toàn không có lỗi mã hóa.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-023--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
