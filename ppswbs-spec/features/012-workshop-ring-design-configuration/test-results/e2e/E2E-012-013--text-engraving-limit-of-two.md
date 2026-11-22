---
format_version: 2
id: E2E-012-013
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: B
source_ref: spec.md#4A.2
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
actor_role: Guest_Or_Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: text-engraving-two-limit
  isolation_key: e2e-012-013-text-limit
cleanup:
  required: true
  method: delete-draft-via-api
---

# E2E-012-013: Giới hạn tối đa 2 văn bản khắc và giới hạn 50 ký tự/văn bản

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Người dùng nhập chữ khắc laser vào nhẫn. Kiểm tra giới hạn 50 ký tự cho mỗi vị trí khắc và tối đa 2 vị trí khắc chữ. Vị trí thứ 3 bị chặn.
- **Phạm vi**: Khắc chữ laser trên đai nhẫn.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Đang ở tab Khắc laser của màn hình cấu hình nhẫn.

## 3. Các bước thực hiện

### Bước 1 — Nhập chữ khắc thứ 1 và thứ 2
- **Mục đích**: Cấu hình 2 chữ khắc hợp lệ.
- **Thao tác**: Nhập chữ thứ 1: "Anh yêu Em", chữ thứ 2: "20.10.2026". Kiểm tra giới hạn 50 ký tự.
- **Kỳ vọng**:
  - Cả 2 chữ khắc hiển thị rõ ràng.
  - Ô nhập không cho phép gõ quá 50 ký tự.
  - Đếm vị trí khắc chữ đạt 2/2.
- **Quy tắc**: `CONTINUE` khi đủ 2 chữ; `STOP` nếu lỗi nhập.

### Bước 2 — Cố gắng thêm chữ khắc thứ 3
- **Mục đích**: Xác minh chặn chữ khắc thứ 3.
- **Thao tác**: Quan sát nút "Thêm chữ khắc".
- **Kỳ vọng**: Nút thêm chữ khắc bị ẩn hoặc disable. Không thể thêm chữ thứ 3.
- **Quy tắc**: `CONTINUE` nếu bị chặn đúng; `STOP` nếu cho phép thêm chữ thứ 3.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 2
- Actual result: Đã xác minh giới hạn 50 ký tự và tối đa 2 chữ khắc; nút thêm chữ bị disable khi đạt 2 vị trí.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-012-013--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
