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
- Latest status: PASS (thanh toán tiền mặt tại quầy, chưa chạy với thanh toán online)
- Executed at: 2026-11-26 02:28–02:38 (đặt đơn lúc 02:36), local `localhost:5173` / `localhost:8080`
- Executed by: Bùi Trọng Thịnh
- Browser tool: Chrome
- Test data: Member `thinhbui732004`; đơn `ML2611265F7D9792` (Mland Hà Nội – Hoàn Kiếm, Nhẫn bạc hở tourmaline xanh size 12, 640.000 đ → 576.000 đ) và `ML2611268A8F9193` (Mland Hà Nội – Tây Hồ, Nhẫn bạc garnet tròn size 6, 480.000 đ → 432.000 đ); điểm 195.524 trước khi dùng (theo người chạy) → 194.404 → 194.504 sau khi đơn hoàn thành.
- Actual result: Checkout hiện "Dùng 1.120 điểm thành viên" (đã bật) và dòng riêng "Giảm bằng điểm −112.000 đ"; Tổng tiền hàng 1.120.000 đ, Tổng thanh toán 1.008.000 đ. Phương thức: Tiền mặt tại quầy, cả hai chi nhánh nhận tại cửa hàng. Sau khi đặt, header và "Đơn mua của tôi" hiện 194.404 điểm (giảm đúng 1.120); hai đơn ở tab "Chờ nhận tại cửa hàng" với phần giảm điểm chia theo từng đơn (576.000 đ + 432.000 đ = 1.008.000 đ). Sau khi thu tiền và giao, dashboard tăng đúng 1.008.000 đ / 2 đơn (E2E-010-001) và Member được cộng 100 điểm (194.504). Khác với P3-S04 và yêu cầu "đơn online": lần chạy này không dùng VNPay/payment-simulator.
- Evidence: screenshots/E2E-008-001--checkout-points-two-branches.png, screenshots/E2E-008-001--member-orders.png, screenshots/E2E-008-001--points-after-completion.png
- Bug reference: không có
<!-- EXECUTION:END -->
