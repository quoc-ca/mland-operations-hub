---
id: E2E-008-001
feature: 008-retail-order-and-fulfillment
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-08-044
target_url: http://localhost:5173/member/checkout
actor_role: Member
browser_tool: chrome
health_urls:
  - http://localhost:5173
  - http://localhost:8080
---

# E2E-008-001: Dùng điểm tích lũy khi thanh toán đơn online (ST_RO_04)

**Ngày tạo**: 2026-11-26
**Người phụ trách**: Bùi Trọng Thịnh

## 1. Tiền điều kiện

- Backend `8080`, frontend `5173` đang chạy trên local (`ppswbs_dev`).
- Tài khoản Member có điểm tích lũy; sản phẩm còn hàng; VNPay sandbox bật.

## 2. Các bước

1. Đăng nhập Member, ghi số điểm hiện có.
2. Thêm sản phẩm vào giỏ `/member/cart`, bấm **Thanh toán**.
3. Ở phần ưu đãi bật **dùng điểm**.
4. Đặt hàng và thanh toán VNPay bằng thẻ test NCB.
5. Mở `/member/orders` và xem lại số điểm.

## 3. Kết quả mong đợi

- Dòng trừ điểm hiển thị riêng, tổng phải trả giảm tương ứng.
- Đơn ở trạng thái đã thanh toán; số dư điểm giảm đúng bằng số điểm đã dùng.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26 (sau 00:18, giờ chính xác không ghi lại)
- Executed by: Bùi Trọng Thịnh
- Browser tool: Chrome
- Actual result: Chạy trên local theo các bước trên; dòng trừ điểm hiển thị, đơn được thanh toán, số dư điểm giảm đúng số đã dùng (theo xác nhận của người chạy). Mã đơn và số điểm trước/sau không ghi lại; không lưu ảnh chụp.
- Bug reference: không có
<!-- EXECUTION:END -->
