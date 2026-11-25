---
id: E2E-008-002
feature: 008-retail-order-and-fulfillment
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-08-018
target_url: http://localhost:5173/carrier/orders
actor_role: Carrier
browser_tool: chrome
health_urls:
  - http://localhost:5173
  - http://localhost:8080
---

# E2E-008-002: Đối tác GHTK (tài khoản CARRIER) cập nhật giao hàng (ST_RP_04)

**Ngày tạo**: 2026-11-26
**Người phụ trách**: Bùi Trọng Thịnh

## 1. Tiền điều kiện

- Tài khoản `ghtk.test@mland.vn`: tài khoản CARRIER của hệ thống, tạo bởi migration `V190__carrier_role.sql`.
- Một đơn giao GHTK đã thanh toán; Staff của chi nhánh đã chuẩn bị và đánh dấu sẵn sàng giao ĐVVC.

## 2. Các bước

1. Đăng nhập `ghtk.test@mland.vn`, mở `/carrier/orders`, tìm đơn.
2. Kiểm tra màn hình không hiện giá sản phẩm, voucher, điểm.
3. Bấm **Đã lấy hàng**, rồi **Giao thành công**.
4. Member xem `/member/orders`; Staff xem `/staff/retail-orders`.
5. Trên trang Staff, kiểm tra không có nút giả lập giao hàng (Đã lấy hàng / Giao thành công).

## 3. Kết quả mong đợi

- Trạng thái chạy đúng thứ tự; Member thấy "Đã giao", Staff thấy đơn ở Hoàn thành.
- Trang Staff không có nút giả lập giao hàng.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-25 19:20:00 (+07:00), bản Vercel develop
- Executed by: lần chạy E2E-DEPLOY-001 (ppswbs-testing/master-e2e); xác nhận tài khoản và trang Staff: Bùi Trọng Thịnh, 2026-11-26
- Browser tool: Chrome
- Test data: đơn `ML2611256F91090C`, vận đơn `S4724552384`; Staff `staff.test@mland.vn`; CARRIER `ghtk.test@mland.vn` (role CARRIER do migration V190 tạo)
- Actual result: Bước 1–4: Staff bấm "Đã chuẩn bị xong"; CARRIER trên `/carrier/orders` bấm "Đã lấy hàng" rồi "Giao thành công" (19:15); Member thấy đơn ở tab "Đã giao". Bước 5: trên `/staff/retail-orders` Staff chỉ có thao tác chuẩn bị; ở các trạng thái chờ ĐVVC trang chỉ hiện dòng chờ, không có nút cập nhật giao hàng (StaffRetailOrdersPage.jsx). Chi tiết ghi trong verification.md mục 2026-11-26 (T080).
- Evidence: ppswbs-testing/master-e2e/E2E-DEPLOY-001--retail-order-voucher-shipping.md (khối EXECUTION); ppswbs-spec/features/008-retail-order-and-fulfillment/verification.md
- Bug reference: không có
<!-- EXECUTION:END -->
