---
format_version: 2
id: E2E-004-032
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: N
source_ref: spec.md#7
target_url: http://localhost:5173/account/my-custom-orders
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: utf8-vietnamese-locale
  isolation_key: e2e-004-032-utf8
cleanup:
  required: false
---

# E2E-004-032: Kiểm tra chuẩn mã hóa UTF-8 tiếng Việt, không lỗi font hay mojibake

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Kiểm tra toàn bộ chuỗi hiển thị tiếng Việt trên các màn hình gửi yêu cầu, danh sách đơn hàng, modal phản hồi làm rõ và chi tiết đơn hàng; đảm bảo hiển thị đúng dấu, không bị vỡ font hay biến dạng ký tự (mojibake).
- **Phạm vi**: Đảm bảo chất lượng trải nghiệm ngôn ngữ người dùng.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Sử dụng giao diện tiếng Việt mặc định của hệ thống.

## 3. Các bước thực hiện

### Bước 1 — Quét kiểm tra hiển thị chữ tiếng Việt
- **Mục đích**: Phát hiện lỗi font hoặc ký tự lạ (như `Ã¡`, `á»`, `?`, ``).
- **Thao tác**: Duyệt qua các màn hình:
  - `/design-requests/new`
  - `/account/my-custom-orders`
  - `/account/my-custom-orders/:id`
- **Kỳ vọng**:
  - Tất cả các nhãn, tiêu đề, nút bấm hiển thị tiếng Việt trọn vẹn: "Yêu cầu thiết kế", "Đơn sản xuất riêng", "Thanh toán toàn bộ", "Cần làm rõ", "Chờ thẩm định", "Chế tác sản xuất", "Bàn giao sản phẩm".
  - Không có bất kỳ ký tự rác nào.
- **Quy tắc**: `CONTINUE` nếu tiếng Việt hiển thị hoàn hảo; `STOP` nếu xuất hiện mojibake.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: 100% văn bản tiếng Việt hiển thị sắc nét, chuẩn UTF-8, không có lỗi font hay ký tự lỗi.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-032--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
