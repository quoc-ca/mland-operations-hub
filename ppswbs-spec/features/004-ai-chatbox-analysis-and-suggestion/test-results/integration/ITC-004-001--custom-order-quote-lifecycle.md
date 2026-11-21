---
id: ITC-004-001
feature: 004-design-request-and-human-review
test_level: integration
test_case_type: N
source_ref: spec.md#6
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service.CustomOrderService
source_method: prepareOrUpdateQuote
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.CustomOrderIntegrationTest
test_method: shouldExecuteFullQuoteLifecycle
---

# ITC-004-001: Vòng đời báo giá đơn hàng gia công tùy chỉnh (Staff soạn, Manager duyệt)

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Mục tiêu kiểm thử

Xác minh quy trình chuyển giao từ yêu cầu thiết kế `ACCEPTED` sang đơn sản xuất tùy chỉnh:
`ACCEPTED` → Staff chuẩn bị báo giá → Manager duyệt toàn bộ báo giá → chuyển trạng thái sang `FULL_PAYMENT_PENDING`.
Đảm bảo Staff không thể tự phê duyệt báo giá và Admin không có thẩm quyền duyệt báo giá.

## 2. Tiền điều kiện và Dữ liệu

- Yêu cầu thiết kế `DR-ORD-001` thuộc về Member A đã được duyệt `ACCEPTED`.
- Staff A: `staff.test@mland.vn`
- Manager B: `manager.test@mland.vn`
- Admin C: `admin.test@mland.vn`

## 3. Các bước thực hiện

1. Staff A gọi `POST /api/v1/custom-design-requests/DR-ORD-001/quote` với số tiền `4500000` VND, ghi chú vật liệu vàng trắng 14k và thời hạn thanh toán 7 ngày.
2. Kiểm tra bản ghi `custom_orders` và `custom_order_quotes`:
   - `custom_order` có trạng thái `QUOTE_PENDING`.
   - `custom_order_quote` có trạng thái `PENDING_APPROVAL`, số tiền `4,500,000` VND, `quote_generation` = 1.
3. Staff A cố gắng gọi `POST /api/v1/custom-orders/{orderId}/approve-quote`.
   - Kết quả: Bị từ chối HTTP 403 (Staff không có quyền duyệt).
4. Admin C cố gắng gọi duyệt báo giá.
   - Kết quả: Bị từ chối HTTP 403 (Admin không có quyền duyệt nghiệp vụ).
5. Manager B gọi `POST /api/v1/custom-orders/{orderId}/approve-quote`.
   - Kết quả: Thành công HTTP 200, quote chuyển sang `APPROVED`, custom order chuyển sang `FULL_PAYMENT_PENDING`.

## 4. Kết quả mong đợi

- Báo giá được Manager duyệt hoàn chỉnh, có tổng tiền, loại tiền (VND), số lần báo giá (generation = 1) và thời hạn thanh toán `payment_deadline_at`.
- Phân quyền nghiêm ngặt theo đúng đặc tả ma trận RBAC.

## 5. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã xác minh tính toàn vẹn dữ liệu trong bảng `custom_orders` và `custom_order_quotes`.
<!-- EXECUTION:END -->
