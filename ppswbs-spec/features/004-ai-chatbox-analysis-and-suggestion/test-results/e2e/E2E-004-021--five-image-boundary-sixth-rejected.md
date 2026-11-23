---
format_version: 2
id: E2E-004-021
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: B
source_ref: spec.md#3
target_url: http://localhost:5173/design-requests/new
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: six-images-boundary
  isolation_key: e2e-004-021-six-images
cleanup:
  required: false
---

# E2E-004-021: Kiểm tra ranh giới tối đa 5 ảnh và chặn tải ảnh thứ 6

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Người dùng cố tình tải lên 6 ảnh. Hệ thống phải chặn ngay ảnh thứ 6 tại giao diện và thông báo vượt quá giới hạn 5 ảnh cho phép.
- **Phạm vi**: Kiểm tra giới hạn biên (Boundary testing).

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Chuẩn bị sẵn 6 file ảnh mẫu.

## 3. Các bước thực hiện

### Bước 1 — Tải liên tiếp 5 ảnh
- **Mục đích**: Đạt giới hạn tối đa 5 ảnh.
- **Thao tác**: Chọn và tải lên 5 file ảnh.
- **Kỳ vọng**: Cả 5 ảnh được tiếp nhận, hiển thị chỉ số `5/5 ảnh`.
- **Quy tắc**: `CONTINUE` khi đủ 5 ảnh; `STOP` nếu không tải được đủ 5 ảnh.

### Bước 2 — Cố gắng tải tiếp ảnh thứ 6
- **Mục đích**: Xác minh chặn ảnh thứ 6.
- **Thao tác**: Chọn thêm 1 ảnh thứ 6 hoặc kéo thả 6 ảnh cùng lúc.
- **Kỳ vọng**:
  - Giao diện vô hiệu hóa nút tải ảnh hoặc hiển thị cảnh báo: "Chỉ được tải lên tối đa 5 ảnh".
  - Danh sách ảnh giữ nguyên đúng 5 ảnh, không vượt quá.
- **Quy tắc**: `CONTINUE` nếu ảnh thứ 6 bị chặn; `STOP` nếu hệ thống nhận 6 ảnh.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Bộ đếm ảnh đạt 5/5; ảnh thứ 6 bị chặn hoàn toàn cả ở client input và backend multipart limit.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-021--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
