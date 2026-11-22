---
format_version: 2
id: E2E-004-026
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: N
source_ref: spec.md#4
target_url: http://localhost:5173/design-review-requests
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: manager-reject-review
  account_ref: manager.test@mland.vn
  isolation_key: e2e-004-026-manager-reject
cleanup:
  required: true
  method: reset-request-state
---

# E2E-004-026: Manager từ chối (REJECTED) yêu cầu thiết kế kèm lý do chi tiết

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Quản lý (Manager) thẩm định yêu cầu thiết kế, xác định mẫu nhẫn không khả thi về mặt kỹ thuật chế tác và từ chối (`REJECTED`) kèm lý do rõ ràng cho khách hàng.
- **Phạm vi**: Thẩm định từ chối có trách nhiệm giải trình (accountability).

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đăng nhập bằng tài khoản `manager.test@mland.vn`.
- [x] Có yêu cầu ở trạng thái `NEED_REVIEW`.

## 3. Các bước thực hiện

### Bước 1 — Chọn yêu cầu và nhập quyết định từ chối
- **Mục đích**: Gửi quyết định từ chối.
- **Thao tác**: Chọn "Từ chối" (`REJECTED`), nhập lý do: "Độ dày đai nhẫn quá mỏng (< 0.8mm) không đảm bảo độ bền khi đính đá chủ kích thước 6.0mm. Đề nghị khách hàng chọn mẫu đai dày tối thiểu 1.2mm", bấm "Xác nhận thẩm định".
- **Kỳ vọng**:
  - Trạng thái yêu cầu chuyển thành `REJECTED`.
  - Khách hàng khi xem lại chi tiết yêu cầu sẽ thấy rõ lời giải thích từ chối này.
  - Không sinh ra bất kỳ đơn báo giá hay thanh toán nào.
- **Quy tắc**: `CONTINUE` khi cập nhật đúng; `STOP` nếu xảy ra lỗi.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Quyết định REJECTED ghi nhận thành công; lý do từ chối hiển thị minh bạch cho khách hàng.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-026--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
