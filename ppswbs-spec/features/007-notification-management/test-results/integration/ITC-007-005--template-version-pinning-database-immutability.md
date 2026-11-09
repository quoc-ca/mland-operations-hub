---
id: ITC-007-005
feature: 007-notification-management
test_level: integration
test_case_type: N
source_ref: spec.md#FR-007
test_scope: Database Immutability & Template Version Pinning
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT
test_method: verifyTemplateVersionPinningPreservesEarlierRendition
---

# ITC-007-005: Notification requests remain pinned to their creation version despite template activation

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## 1. Mục tiêu kiểm thử tích hợp (Integration Objective)

Xác minh yêu cầu FR-007, FR-008 & SC-005: Khi một phiên bản template mới được kích hoạt, các notification request được tạo từ trước vẫn giữ nguyên khóa ngoại trỏ tới phiên bản cũ và nội dung rendition không bao giờ bị cập nhật theo template mới.

## 2. Tiền điều kiện & Môi trường tích hợp (Preconditions & Environment)

- Template `BOOKING_CONFIRMED` đang có Version 1 `ACTIVE` với subject: `"[V1] Xác nhận {{bookingCode}}"`.
- Dịch vụ `TemplateManagementService` và database đã sẵn sàng.

## 3. Dữ liệu thử nghiệm & Kịch bản thực thi (Test Data & Execution Steps)

### Các bước thực thi:
1. Enqueue Request #1 (`businessEventReference = "BK-PIN-001"`).
2. Kiểm tra Request #1 đã gán `template_version_id` trỏ về Version 1.
3. Tạo bản nháp Version 2 với subject: `"[V2] Cập nhật mới: Xác nhận {{bookingCode}}"`.
4. Gọi `templateManagementService.activateVersion("BOOKING_CONFIRMED", 2, "admin_user")`: Version 2 trở thành `ACTIVE`, Version 1 trở thành `DEACTIVATED`.
5. Enqueue Request #2 (`businessEventReference = "BK-PIN-002"`).
6. Kích hoạt worker xử lý render và gửi cho cả Request #1 và Request #2.

## 4. Kiểm chứng kết quả tích hợp (Expected Assertions & Invariants)

- **Request #1:**
  - `template_version_id` vẫn là Version 1.
  - `notification_rendition.rendered_subject` chứa tiền tố `"[V1]"`.
- **Request #2:**
  - `template_version_id` trỏ tới Version 2.
  - `notification_rendition.rendered_subject` chứa tiền tố `"[V2]"`.
- Cả hai rendition được lưu độc lập, không bị ghi đè lẫn nhau.

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Command: NOT_RUN
- Actual result: NOT_RUN
<!-- EXECUTION:END -->

