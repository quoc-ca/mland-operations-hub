---
format_version: 2
id: E2E-004-018
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
  fixture: guest-design-description-only
  isolation_key: e2e-004-018-guest-desc
cleanup:
  required: true
  method: delete-request-via-api
---

# E2E-004-018: Guest tạo yêu cầu thiết kế bằng mô tả văn bản (Không upload ảnh)

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Guest mở trang gửi yêu cầu thiết kế mới, nhập mô tả yêu cầu chi tiết (chất liệu vàng hồng 18k, phong cách tối giản), không tải ảnh, gửi yêu cầu thành công và nhận được liên kết theo dõi an toàn (opaque tracking link).
- **Phạm vi**: Luồng submit độc lập của Guest, không cần đăng nhập tài khoản.
- **Không bao gồm**: Đăng nhập tài khoản Member hay đặt lịch workshop.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Frontend `http://localhost:5173` và Backend `http://localhost:8080` sẵn sàng.
- [x] Trình duyệt ở trạng thái ẩn danh (Guest / signed-out).
- [x] Quota gửi yêu cầu trong ngày của Guest < 5.

## 3. Các bước thực hiện

### Bước 1 — Mở form yêu cầu thiết kế
- **Mục đích**: Truy cập trang tạo yêu cầu thiết kế mới từ giao diện người dùng.
- **Thao tác**: Điều hướng đến `http://localhost:5173/design-requests/new`.
- **Kỳ vọng**:
  - Tiêu đề hoặc khu vực biểu mẫu gửi yêu cầu thiết kế hiển thị rõ ràng.
  - Trường nhập mô tả văn bản sẵn sàng.
  - Khu vực tải ảnh hiển thị cho phép tối đa 5 ảnh.
- **Quy tắc**: `CONTINUE` khi form sẵn sàng; `STOP` nếu trang lỗi hoặc không tải được.

### Bước 2 — Nhập mô tả và gửi yêu cầu
- **Mục đích**: Nhập mô tả thiết kế và thực hiện gửi yêu cầu.
- **Thao tác**: Nhập "Nhẫn đôi vàng hồng 18K thiết kế lượn sóng nhám mờ phong cách Bắc Âu" vào ô mô tả, sau đó bấm nút "Gửi yêu cầu".
- **Kỳ vọng**:
  - Hệ thống gửi thành công và hiển thị màn hình thông báo hoàn tất.
  - Hiển thị liên kết/mã theo dõi tiến độ dành riêng cho Guest.
  - Trạng thái yêu cầu ban đầu là "Chờ duyệt" (`NEED_REVIEW`).
  - Không xuất hiện bất kỳ thông tin hay gợi ý nào từ AI.
- **Quy tắc**: `CONTINUE` khi nhận được kết quả gửi thành công; `STOP` nếu bị lỗi hệ thống.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Form gửi yêu cầu hoạt động tốt với mô tả văn bản; tạo yêu cầu thành công với trạng thái NEED_REVIEW và mã theo dõi cho Guest.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-018--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
