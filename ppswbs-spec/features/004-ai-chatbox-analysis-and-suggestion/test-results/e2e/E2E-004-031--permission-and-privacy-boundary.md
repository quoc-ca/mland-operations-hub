---
format_version: 2
id: E2E-004-031
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: A
source_ref: spec.md#2
target_url: http://localhost:5173/design-review-requests
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: rbac-route-masking
  account_ref: member.test@mland.vn
  isolation_key: e2e-004-031-rbac
cleanup:
  required: false
---

# E2E-004-031: Ranh giới phân quyền bảo mật và che giấu route nội bộ (Route Masking)

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Member hoặc Guest cố tình gõ trực tiếp URL quản trị nội bộ `/design-review-requests`. Hệ thống áp dụng cơ chế Route Masking thay thế điều hướng sang trang 404 Not Found, không để lộ giao diện hoặc API nội bộ.
- **Phạm vi**: An toàn bảo mật và phân quyền truy cập.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đăng nhập bằng tài khoản `member.test@mland.vn`.

## 3. Các bước thực hiện

### Bước 1 — Cố tình truy cập URL thẩm định nội bộ
- **Mục đích**: Thử truy cập trái quyền.
- **Thao tác**: Nhập URL `http://localhost:5173/design-review-requests` vào thanh địa chỉ.
- **Kỳ vọng**:
  - Không hiển thị bất kỳ danh sách hay dữ liệu yêu cầu nào.
  - Ứng dụng tự động chuyển hướng hoặc hiển thị trang `404 Không tìm thấy trang`.
- **Quy tắc**: `CONTINUE` nếu bị chặn bằng 404; `STOP` nếu lộ dữ liệu quản trị.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Route masking hoạt động chính xác; Member bị thay thế URL về /404, bảo vệ hoàn toàn dữ liệu nhân viên.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-031--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
