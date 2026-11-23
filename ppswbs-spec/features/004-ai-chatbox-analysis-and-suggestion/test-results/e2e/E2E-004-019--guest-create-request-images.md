---
format_version: 2
id: E2E-004-019
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: N
source_ref: spec.md#3
target_url: http://localhost:5173/design-requests/new
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: guest-design-images-only
  isolation_key: e2e-004-019-guest-images
cleanup:
  required: true
  method: delete-request-via-api
---

# E2E-004-019: Guest tạo yêu cầu thiết kế đính kèm hình ảnh mẫu

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Guest tải lên 2 hình ảnh mẫu tham khảo, hệ thống xác thực chất lượng ảnh đạt chuẩn (`PASS`), cho phép gửi yêu cầu và ghi nhận thành công.
- **Phạm vi**: Tải ảnh mẫu, xem trước và kiểm tra phản hồi chất lượng ảnh.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Chuẩn bị 2 file ảnh mẫu định dạng PNG/JPG rõ nét (< 5MB).
- [x] Session ở trạng thái Guest.

## 3. Các bước thực hiện

### Bước 1 — Tải ảnh lên và kiểm tra chất lượng
- **Mục đích**: Tải 2 ảnh mẫu vào khu vực upload.
- **Thao tác**: Chọn 2 file ảnh và thêm vào biểu mẫu.
- **Kỳ vọng**:
  - 2 ảnh hiển thị thumbnail xem trước rõ ràng.
  - Phản hồi kiểm tra chất lượng hiển thị hợp lệ (`Đạt tiêu chuẩn`).
  - Đếm số lượng hiển thị `2/5 ảnh`.
- **Quy tắc**: `CONTINUE` khi ảnh sẵn sàng; `STOP` nếu bị từ chối ảnh hợp lệ.

### Bước 2 — Gửi yêu cầu và xác nhận
- **Mục đích**: Xác nhận đồng ý và gửi yêu cầu thẩm định.
- **Thao tác**: Đánh dấu xác nhận sở hữu hình ảnh (nếu có yêu cầu consent) và nhấn "Gửi yêu cầu".
- **Kỳ vọng**:
  - Yêu cầu được gửi thành công, trạng thái `NEED_REVIEW`.
  - Cấp mã theo dõi tiến độ cho khách vãng lai.
- **Quy tắc**: `CONTINUE` khi thành công; `STOP` nếu thất bại.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Tải ảnh thành công, kiểm tra chất lượng PASS và gửi yêu cầu tạo bản ghi NEED_REVIEW chính xác.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-019--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
