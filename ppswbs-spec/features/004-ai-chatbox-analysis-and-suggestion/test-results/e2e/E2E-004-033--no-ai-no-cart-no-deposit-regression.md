---
format_version: 2
id: E2E-004-033
feature: 004-design-request-and-human-review
test_level: e2e
test_case_type: N
source_ref: spec.md#8
target_url: http://localhost:5173/account/my-custom-orders
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/auth/status
test_data:
  fixture: no-ai-no-cart-no-deposit-regression
  isolation_key: e2e-004-033-regression
cleanup:
  required: false
---

# E2E-004-033: Kiểm tra hồi quy không còn AI, không có giỏ hàng, không có tiền cọc hay số dư

**Ngày tạo**: 2026-11-21
**Chủ sở hữu**: Senior QA Engineer

## 1. Mục tiêu kịch bản

- **Mục tiêu**: Kiểm tra hồi quy toàn diện để đảm bảo các yếu tố bị cấm KHÔNG xuất hiện trên giao diện và luồng dữ liệu Custom Manufacturing:
  - Không có AI / Gemini gợi ý giá hoặc tự động quyết định.
  - Không có nút "Thêm vào giỏ hàng" (Add to Cart).
  - Không có từ ngữ hoặc cơ chế "đặt cọc" (deposit).
  - Không có "số dư còn lại" (remaining balance) hay thanh toán chia đợt.
- **Phạm vi**: Tuân thủ triệt để các bất biến nghiệp vụ mới.

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- [x] Mở trang chi tiết đơn hàng Custom Order.

## 3. Các bước thực hiện

### Bước 1 — Tìm kiếm các từ khóa bị cấm trên DOM
- **Mục đích**: Quét nội dung văn bản hiển thị.
- **Thao tác**: Kiểm tra các cụm từ: "đặt cọc", "tiền cọc", "thanh toán đợt cuối", "số dư còn lại", "thêm vào giỏ hàng", "AI gợi ý".
- **Kỳ vọng**:
  - Không tìm thấy bất kỳ cụm từ nào nêu trên.
  - Luôn ghi rõ: "Thanh toán toàn bộ (100%)", "(Thanh toán một lần duy nhất)".
- **Quy tắc**: `CONTINUE` nếu hoàn toàn sạch từ khóa cấm; `STOP` nếu phát hiện từ khóa cấm.

## 4. Bằng chứng và Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-21 20:30:00
- Executed by: Senior QA Agent
- Browser tool: browsermcp
- Final step: Bước 1
- Actual result: Đã xác minh 0 trường hợp vi phạm; luồng custom order tuân thủ tuyệt đối quy định thanh toán toàn bộ 1 lần, không cart, không deposit.
- Findings: NONE
- Evidence screenshots: screenshots/E2E-004-033--pass.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
