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
- Executed at: 2026-11-26 (sau 00:18, giờ chính xác không ghi lại)
- Executed by: Bùi Trọng Thịnh
- Browser tool: Chrome
- Actual result: Giỏ 2 chi nhánh được tách đúng 2 đơn, mỗi đơn có phí GHTK riêng; hoàn tất dưới 180 giây (theo xác nhận của người chạy). Số giây chính xác không ghi lại; không lưu ảnh chụp.
- Bug reference: không có
<!-- EXECUTION:END -->
