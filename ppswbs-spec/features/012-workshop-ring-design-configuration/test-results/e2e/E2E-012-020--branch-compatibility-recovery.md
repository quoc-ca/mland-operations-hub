---
format_version: 2
id: E2E-012-020
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: N
source_ref: spec.md#4
target_url: http://localhost:5173/workshops
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: branch-compatibility-flow
  isolation_key: e2e-012-020-branch
cleanup:
  required: false
---

# E2E-012-020: Phát hiện không tương thích chi nhánh và giữ nguyên nút khám phá workshop

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**:
  - Khi thiết kế nhẫn sử dụng vật liệu không có sẵn tại chi nhánh được chọn, hệ thống thông báo rõ ràng và đề xuất đổi chi nhánh.
  - Nút "Khám phá các workshop" không xuất hiện ở màn hình cấu hình, mà chỉ xuất hiện ở màn hình preview sau khi đã lưu và thẩm định; nút này dẫn đúng đến trang `/workshops` và giữ nguyên không bị biến mất.
- **Phạm vi**: Điều hướng người dùng và tương thích chi nhánh.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Hoàn thành thẩm định và lưu thiết kế thành công.

## 3. Các bước thực hiện

### Bước 1 — Kiểm tra điều hướng khám phá workshop sau khi lưu
- **Mục đích**: Xác minh đường dẫn đúng đến trang workshop.
- **Thao tác**: Sau khi lưu mẫu nhẫn, quan sát màn hình preview thẩm định.
- **Kỳ vọng**:
  - Nút "Khám phá các workshop" hiển thị rõ ràng.
  - Bấm nút: điều hướng chính xác đến trang danh sách workshop `/workshops`, không dẫn đến trang lỗi 404.
  - Quay lại trang xem thiết kế: nút vẫn giữ nguyên, không biến mất.
- **Quy tắc**: `CONTINUE` nếu điều hướng đúng; `STOP` nếu link hỏng hoặc nút biến mất.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Đường dẫn khám phá workshop chuẩn xác đến /workshops; nút không bị biến mất và giao diện cấu hình sạch sẽ.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-020--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
