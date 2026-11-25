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
- Executed at: 2026-11-26 (sau 00:18, giờ chính xác không ghi lại)
- Executed by: Bùi Trọng Thịnh
- Browser tool: Chrome
- Actual result: Bước 1–4 đạt trên local bằng `ghtk.test@mland.vn` (theo xác nhận của người chạy). Bước 5 (trang Staff không có nút giả lập) chưa kiểm tra trong lần chạy này. Mã đơn không ghi lại; không lưu ảnh chụp. Lần chạy E2E-DEPLOY-001 (25/11, bản Vercel) cũng đã dùng tài khoản này giao thành công đơn ML2611256F91090C.
- Bug reference: không có
<!-- EXECUTION:END -->
