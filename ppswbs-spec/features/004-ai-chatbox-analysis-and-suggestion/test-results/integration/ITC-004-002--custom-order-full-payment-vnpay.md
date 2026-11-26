---
id: ITC-004-002
feature: 004-design-request-and-human-review
test_level: integration
test_case_type: N
source_ref: spec.md#6
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service.CustomOrderService
source_method: startFullPayment
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.CustomOrderIntegrationTest
test_method: shouldEnforceFullPaymentAndPreventSplitOrCart
---

# ITC-004-002: Thanh toán toàn bộ 100% qua cổng VNPay và nghiêm cấm Add to Cart / Đặt cọc

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Mục tiêu kiểm thử

Xác minh quy tắc cốt lõi:
- Custom Order chỉ thanh toán 100% toàn bộ một lần duy nhất qua context thanh toán trực tiếp từ trang chi tiết đơn hàng.
- Tuyệt đối KHÔNG có nút "Thêm vào giỏ hàng" (Add to Cart).
- Tuyệt đối KHÔNG có khái niệm đặt cọc (deposit) hay thanh toán số dư đợt cuối (remaining balance).
- Tích hợp cổng thanh toán VNPay với loại `PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT`.

## 2. Tiền điều kiện và Dữ liệu

- Đơn hàng `CO-TEST-100` của Member A có báo giá được duyệt là `5,000,000` VND, trạng thái `FULL_PAYMENT_PENDING`.

## 3. Các bước thực hiện

1. Member A nhấn nút "Thanh toán toàn bộ" trên giao diện chi tiết đơn hàng `/account/my-custom-orders/CO-TEST-100`.
2. Frontend gọi `POST /api/v1/members/me/custom-orders/CO-TEST-100/payment`.
3. Backend kiểm tra:
   - Đơn hàng thuộc quyền sở hữu của Member A.
   - Quote còn hiệu lực (chưa hết hạn).
   - Đăng ký `PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT` với `PaymentOwnerRegistry`.
   - Sinh link thanh toán VNPay với đúng số tiền `5,000,000` VND.
4. Giả lập callback IPN thành công từ cổng VNPay với mã phản hồi `vnp_ResponseCode=00` và `vnp_TransactionStatus=00`.
5. `PaymentIngressService` xác minh chữ ký, đối chiếu số tiền và chuyển tiếp đến `CustomOrderPaymentFacade.applyVerifiedFullPayment()`.
6. Kiểm tra trạng thái đơn hàng:
   - `custom_order` chuyển sang `PAID`, `production_status` = `QUEUED`.
   - Bảng `custom_order_payment_context` chuyển sang `VERIFIED`.

## 4. Kết quả mong đợi

- Thanh toán 100% được ghi nhận chính xác.
- Callback lặp lại trả về `Outcome.duplicate()` và không tạo thanh toán kép.
- Không phát sinh bất kỳ dòng sản phẩm nào trong giỏ hàng bán lẻ (`retail_cart`).

## 5. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: DatNT (Senior QA & Verification Engineer)
- Ngày thực thi: 2026-11-26
- Ghi chú: Chạy đạt thông qua `CustomOrderIntegrationTest.shouldEnforceFullPaymentAndPreventSplitOrCart` và `whenPaymentAmountMismatched_thenRequiresReviewAndOrderNotPaid`. Đã xác minh thanh toán 100% một lần (không giỏ hàng, không đặt cọc), callback IPN chuyển sang `PAID`, replay callback bảo đảm tính idempotent không nhân bản giao dịch, callback sai lệch số tiền chuyển trạng thái `REVIEW_REQUIRED`.
<!-- EXECUTION:END -->
