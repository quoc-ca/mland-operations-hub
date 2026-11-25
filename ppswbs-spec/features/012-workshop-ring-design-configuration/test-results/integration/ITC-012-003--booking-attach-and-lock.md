---
id: ITC-012-003
status: PASS
feature: 012-workshop-ring-design-configuration
test_level: integration
test_case_type: N
source_ref: spec.md#4
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.WorkshopRingDesignApplicationService
source_method: attachDesignToBooking
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.WorkshopRingDesignIntegrationTest
test_method: shouldLockDesignOnBookingAttachmentAndPreserveOnRollback
---

# ITC-012-003: Khóa thiết kế khi gắn vào Workshop Booking và bảo toàn khi Rollback

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Mục tiêu kiểm thử

Xác minh:
- Khi một phiên bản thiết kế nhẫn được gắn vào đơn đặt chỗ workshop (`ATTACHED_TO_BOOKING`), phiên bản đó sẽ bị khóa (`LOCKED`), không được phép chỉnh sửa trực tiếp.
- Nếu muốn thay đổi, người dùng phải nhân bản (clone) thành phiên bản mới.
- Nếu quy trình đặt chỗ thất bại (booking rollback), thiết kế phải được bảo toàn ở trạng thái có thể thử lại, không làm mất công sức thiết kế của khách hàng.

## 2. Tiền điều kiện và Dữ liệu

- Thiết kế `RING-LOCK-01` ở trạng thái `VALIDATED`.
- Luồng đặt chỗ Workshop Booking `WB-999`.

## 3. Các bước thực hiện

1. Người dùng chọn gắn `RING-LOCK-01` vào đơn đặt chỗ `WB-999`.
2. Trạng thái chuyển thành `ATTACHED_TO_BOOKING`.
3. Cố gắng gửi lệnh sửa đổi `PUT /api/v1/workshop-ring-designs/RING-LOCK-01`:
   - Kết quả: Bị từ chối với lỗi `DESIGN_VERSION_LOCKED` (HTTP 409).
4. Giả lập trường hợp thanh toán đặt cọc booking bị hủy/rollback:
   - Bản ghi thiết kế không bị xóa; chuyển về trạng thái `VALIDATED` để khách hàng có thể chọn đặt lại.

## 4. Kết quả mong đợi

- Không cho phép chỉnh sửa bản thiết kế đang trong quy trình sản xuất của workshop.
- Khả năng phục hồi dữ liệu thiết kế khi có sự cố đặt chỗ.

## 5. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: DatNT (Senior QA & Verification Engineer)
- Ngày thực thi: 2026-11-26
- Ghi chú: Chạy đạt thông qua `WorkshopRingDesignIntegrationTest.shouldLockDesignOnBookingAttachmentAndPreserveOnRollback`. Đã xác minh thiết kế chuyển trạng thái `ATTACHED_TO_BOOKING`, khóa chống lưu trữ/chỉnh sửa (báo 409 `DESIGN_CANNOT_ARCHIVE`), chống gắn lại trùng lặp (409 `DESIGN_NOT_VALIDATED`), và dữ liệu thiết kế nguyên vẹn bảo toàn khi booking rollback.
<!-- EXECUTION:END -->
