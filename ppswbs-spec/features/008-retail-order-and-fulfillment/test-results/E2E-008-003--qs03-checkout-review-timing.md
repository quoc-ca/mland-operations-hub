---
id: E2E-008-003
feature: 008-retail-order-and-fulfillment
test_level: e2e
test_case_type: A
source_ref: spec.md#SC-08-004,quickstart.md#QS-03
target_url: http://localhost:5173/member/checkout
actor_role: Member
browser_tool: chrome
health_urls:
  - http://localhost:5173
  - http://localhost:8080
---

# E2E-008-003: Đo thời gian giỏ hàng → xem lại đơn → đặt hàng (ST_RP_05, QS-03)

**Ngày tạo**: 2026-11-26
**Người phụ trách**: Bùi Trọng Thịnh

## 1. Tiền điều kiện

- Member đã đăng nhập; sản phẩm của 2 chi nhánh còn hàng.

## 2. Các bước

1. Bật đồng hồ. Thêm sản phẩm của 2 chi nhánh vào giỏ, bấm **Thanh toán**.
2. Kiểm tra màn xem lại đơn: tách 2 đơn theo chi nhánh, mỗi đơn có tạm tính và phí GHTK riêng.
3. Chọn địa chỉ, phương thức, bấm **Đặt hàng**. Dừng đồng hồ khi trang VNPay mở (không tính thời gian nhập thẻ).

## 3. Kết quả mong đợi

- Chia đơn đúng theo chi nhánh; tổng thời gian dưới 3 phút (180 giây, SC-08-004).

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26, đặt đơn lúc 02:36, local
- Executed by: Bùi Trọng Thịnh
- Browser tool: Chrome
- Test data: Member `thinhbui732004`; giỏ 2 chi nhánh (Hoàn Kiếm: Nhẫn bạc hở tourmaline xanh 640.000 đ; Tây Hồ: Nhẫn bạc garnet tròn 480.000 đ); đơn `ML2611265F7D9792`, `ML2611268A8F9193`.
- Actual result: Thời gian đo được khoảng 175 giây, dưới mục tiêu 180 giây (SC-08-004). Màn xem lại đơn tách 2 nhóm theo chi nhánh, mỗi nhóm có tạm tính và lựa chọn giao nhận riêng (Nhận tại cửa hàng / Giao tận nơi qua GHTK); lần chạy chọn nhận tại cửa hàng cho cả hai nên dòng phí GHTK không hiện. Đặt hàng tạo đúng 2 đơn theo chi nhánh. Ở lần chạy 02:50 (E2E-008-001), Hoàn Kiếm chọn giao GHTK: phí 28.714 đ hiện riêng ở nhóm Hoàn Kiếm và ở dòng "Tổng tiền phí vận chuyển", Tây Hồ nhận tại cửa hàng "Miễn phí".
- Evidence: screenshots/E2E-008-001--checkout-points-two-branches.png, screenshots/E2E-008-001--member-orders.png
- Bug reference: không có
<!-- EXECUTION:END -->
