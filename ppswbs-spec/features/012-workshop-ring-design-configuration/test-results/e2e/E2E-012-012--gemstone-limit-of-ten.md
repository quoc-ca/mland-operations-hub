---
format_version: 2
id: E2E-012-012
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: B
source_ref: spec.md#4A.1
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
actor_role: Guest_Or_Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: gemstone-limit-ten
  isolation_key: e2e-012-012-gemstone-limit
cleanup:
  required: true
  method: delete-draft-via-api
---

# E2E-012-012: Giới hạn tối đa 10 viên đá quý và chặn viên thứ 11 trên giao diện

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Người dùng cấu hình nhẫn thêm các viên đá quý (đá chủ và đá tấm). Khi tổng số lượng đá đạt 10/10 viên, hệ thống vô hiệu hóa các nút thêm đá và hiển thị thông báo song ngữ. Thử thêm viên thứ 11 không làm thay đổi bản thiết kế.
- **Phạm vi**: Giới hạn tổng số lượng đá quý trên sản phẩm.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Mở màn hình cấu hình nhẫn workshop chế độ tạo mới (BLANK).

## 3. Các bước thực hiện

### Bước 1 — Thêm đá quý đạt 10 viên
- **Mục đích**: Đạt giới hạn tối đa 10 viên.
- **Thao tác**: Chọn loại đá, tăng số lượng cho đến khi tổng số đá = 10.
- **Kỳ vọng**:
  - Bộ đếm hiển thị `10 / 10 viên`.
  - Hiển thị thông báo: "⚠️ Đã đạt giới hạn tối đa 10 viên đá quý." (hoặc song ngữ tiếng Anh).
  - Nút thêm đá và nút tăng số lượng bị disable.
- **Quy tắc**: `CONTINUE` nếu đạt giới hạn; `STOP` nếu vượt quá mà không có cảnh báo.

### Bước 2 — Thử thêm viên thứ 11 và lưu thử
- **Mục đích**: Xác minh chặn viên thứ 11.
- **Thao tác**: Cố gắng thao tác thêm hoặc bấm lưu thiết kế.
- **Kỳ vọng**:
  - Bản thiết kế giữ nguyên đúng 10 viên đá.
  - Không có viên thứ 11 nào được ghi nhận.
- **Quy tắc**: `CONTINUE` khi thiết kế hợp lệ; `STOP` nếu xuất hiện viên thứ 11.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Bộ đếm 10/10 hoạt động chính xác; vô hiệu hóa nút thêm đá; thông báo song ngữ hiển thị chuẩn.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-012--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
