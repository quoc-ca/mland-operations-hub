---
format_version: 2
id: E2E-004-028
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: A
source_ref: spec.md#4
target_url: http://localhost:5173/design-review-requests
actor_role: Staff
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: review-concurrency-conflict
  isolation_key: e2e-004-028-conflict
cleanup:
  required: false
---

# E2E-004-028: Xung đột phiên bản thẩm định đồng thời và chặn quyết định trùng lặp

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Hai nhân viên cùng mở một yêu cầu thiết kế. Nhân viên A gửi quyết định trước. Nhân viên B gửi sau với phiên bản cũ (stale version) phải nhận thông báo lỗi xung đột (HTTP 409) và không được ghi đè quyết định của nhân viên A.
- **Phạm vi**: Khóa lạc quan (Optimistic concurrency control) và Idempotency.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Yêu cầu thiết kế `DR-CONF-01` ở `version = 1`.

## 3. Các bước thực hiện

### Bước 1 — Giả lập gửi quyết định với version cũ
- **Mục đích**: Gửi quyết định thẩm định kèm header `If-Match-Version: 0` hoặc version đã lỗi thời.
- **Thao tác**: Gửi lệnh thẩm định khi yêu cầu đã có quyết định hoặc đã tăng version.
- **Kỳ vọng**:
  - Backend trả về mã lỗi HTTP 409 `DESIGN_REVIEW_STATE_CONFLICT` hoặc `DESIGN_REVIEW_ALREADY_DECIDED`.
  - Giao diện hiển thị thông báo: "Yêu cầu đã được cập nhật bởi người khác. Vui lòng tải lại trang."
- **Quy tắc**: `CONTINUE` nếu nhận đúng mã lỗi 409; `STOP` nếu cho phép ghi đè sai dữ liệu.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Backend chặn đứng vi phạm version với mã 409; frontend bắt lỗi và thông báo yêu cầu tải lại.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-028--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
