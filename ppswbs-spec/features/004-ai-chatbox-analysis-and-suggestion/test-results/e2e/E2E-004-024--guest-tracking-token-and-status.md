---
format_version: 2
id: E2E-004-024
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: N
source_ref: spec.md#3.2
target_url: http://localhost:5173/design-requests/track
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: guest-tracking-lookup
  isolation_key: e2e-004-024-guest-track
cleanup:
  required: false
---

# E2E-004-024: Guest tra cứu trạng thái yêu cầu thiết kế bằng mã theo dõi bảo mật

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Guest mở trang tra cứu hoặc truy cập trực tiếp bằng đường link tracking token, xem được trạng thái yêu cầu, ảnh đã gửi và phản hồi của nhân viên mà không lộ bất kỳ thông tin riêng tư nào.
- **Phạm vi**: Khả năng hiển thị và bảo vệ dữ liệu công khai.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Có mã theo dõi hợp lệ của một yêu cầu thiết kế vừa gửi.

## 3. Các bước thực hiện

### Bước 1 — Mở trang tra cứu với mã tracking
- **Mục đích**: Xem thông tin tiến độ yêu cầu.
- **Thao tác**: Nhập mã token vào trang `/design-requests/track` hoặc mở link trực tiếp.
- **Kỳ vọng**:
  - Thông tin yêu cầu hiển thị: Mã tham chiếu, ngày gửi, trạng thái hiện tại.
  - Phản hồi giải thích của nhân viên (nếu đã được review).
  - TUYỆT ĐỐI KHÔNG hiển thị: email, số điện thoại, ID tài khoản cơ sở dữ liệu.
- **Quy tắc**: `CONTINUE` khi dữ liệu an toàn hiển thị; `STOP` nếu lộ PII hoặc lỗi 500.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Tra cứu thành công với mã token; hiển thị trạng thái và phản hồi an toàn; không lộ thông tin nội bộ.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-024--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
