---
format_version: 2
id: E2E-004-023
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: B
source_ref: spec.md#3.2
target_url: http://localhost:5173/design-requests/new
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: guest-daily-quota-exhaustion
  isolation_key: e2e-004-023-quota
cleanup:
  required: false
---

# E2E-004-023: Giới hạn 5 yêu cầu/ngày cho Guest và xử lý thông báo 429

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Guest gửi liên tiếp 5 yêu cầu trong một ngày thành công. Đến lần thứ 6, hệ thống hiển thị thông báo đã đạt giới hạn tối đa 5 yêu cầu/ngày và đề nghị đăng ký thành viên hoặc quay lại vào ngày mai.
- **Phạm vi**: Rate-limit và chính sách bảo vệ tài nguyên hệ thống.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Guest session đã hoàn thành 5 lần gửi trong ngày hôm nay.

## 3. Các bước thực hiện

### Bước 1 — Cố gắng gửi yêu cầu thứ 6
- **Mục đích**: Gửi yêu cầu khi đã hết lượt trong ngày.
- **Thao tác**: Nhập mô tả thiết kế và bấm gửi.
- **Kỳ vọng**:
  - Hệ thống trả về mã lỗi HTTP 429.
  - Giao diện hiển thị thông báo rõ ràng: "Bạn đã đạt giới hạn 5 yêu cầu thiết kế trong ngày. Vui lòng đăng nhập tài khoản thành viên để tiếp tục hoặc thử lại sau 00:00."
  - Form giữ nguyên nội dung người dùng đã nhập, không làm mất chữ.
- **Quy tắc**: `CONTINUE` nếu nhận đúng thông báo giới hạn; `STOP` nếu vẫn tạo thành công yêu cầu thứ 6.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Yêu cầu thứ 6 bị chặn với HTTP 429; hiển thị thông báo hết hạn ngạch chính xác và giữ nguyên form input.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-023--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
