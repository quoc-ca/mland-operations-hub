---
id: ITC-004-003
feature: 004-design-request-and-human-review
test_level: integration
test_case_type: B
source_ref: spec.md#6
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service.CustomOrderService
source_method: requestNewQuote
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.CustomOrderIntegrationTest
test_method: shouldHandleQuoteExpiryAndGenerateNewQuoteVersion
---

# ITC-004-003: Xử lý hết hạn báo giá và cơ chế sinh báo giá thế hệ mới (New Quote Generation)

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Mục tiêu kiểm thử

Xác minh hành vi khi báo giá quá hạn thanh toán (`quoteExpiresAt` đã qua):
- Không cho phép thanh toán báo giá đã hết hạn.
- Giải phóng hàng chờ giữ chỗ (queue hold).
- Cho phép Member bấm "Yêu cầu báo giá mới" để sinh thế hệ báo giá tiếp theo (`quote_generation` tăng từ 1 lên 2) trên cùng một custom order.
- Giữ nguyên toàn bộ lịch sử báo giá cũ để phục vụ audit.

## 2. Tiền điều kiện và Dữ liệu

- Đơn hàng `CO-EXP-001` có báo giá generation 1 hết hạn lúc 12:00 hôm qua.
- Trạng thái đơn hàng là `EXPIRED`.

## 3. Các bước thực hiện

1. Thử gọi thanh toán cho đơn hàng đã hết hạn: `POST /api/v1/members/me/custom-orders/CO-EXP-001/payment`.
   - Kết quả: Bị từ chối với HTTP 409 hoặc 422 `CUSTOM_ORDER_QUOTE_EXPIRED`.
2. Member gọi `POST /api/v1/members/me/custom-orders/CO-EXP-001/request-new-quote`.
3. Backend kiểm tra chính sách cho phép yêu cầu báo giá lại:
   - Trạng thái đơn hàng chuyển về `QUOTE_PENDING`.
   - `custom_order_quotes` giữ nguyên bản ghi cũ ở trạng thái `EXPIRED`.
4. Staff soạn báo giá mới:
   - Bản ghi báo giá mới được sinh ra với `quote_generation` = 2, số tiền được điều chỉnh.
   - Quote cũ (generation 1) vẫn tồn tại bất biến trong cơ sở dữ liệu.

## 4. Kết quả mong đợi

- Báo giá hết hạn không thể thanh toán được.
- Đơn hàng tái sinh luồng báo giá với số thứ tự generation tăng dần.
- Dữ liệu lịch sử kiểm toán hoàn toàn toàn vẹn.

## 5. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã xác minh logic cập nhật version và thế hệ quote tại `CustomOrderService.java`.
<!-- EXECUTION:END -->
