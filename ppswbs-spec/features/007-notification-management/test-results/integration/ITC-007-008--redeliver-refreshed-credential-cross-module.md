---
id: ITC-007-008
feature: 007-notification-management
test_level: integration
test_case_type: N
source_ref: spec.md#FR-019
test_scope: Cross-Module Integration with Workshop Booking Facade
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT
test_method: verifyRedeliverWithRefreshedCredentialCrossModule
---

# ITC-007-008: Redelivery with refreshed credential communicates across module facade to reissue link

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## 1. Mục tiêu kiểm thử tích hợp (Integration Objective)

Xác minh yêu cầu FR-019, US4 & WF-10: Khi Admin/Manager thực hiện redelivery cho một request có vé/link QR đã hết hạn, `OperationalService` gọi facade của module nguồn (`workshopbooking`), nhận credential mới, tạo request liên kết mới với credential mới mà không làm thay đổi bản ghi gốc hay snapshot nghiệp vụ.

## 2. Tiền điều kiện & Môi trường tích hợp (Preconditions & Environment)

- Cơ sở dữ liệu và Spring Modulith context khởi tạo đầy đủ.
- Request 700 đã hoàn tất gửi trước đó với một mã booking hợp lệ, nhưng liên kết QR đã hết hiệu lực.

## 3. Dữ liệu thử nghiệm & Kịch bản thực thi (Test Data & Execution Steps)

### Dữ liệu thử nghiệm:
- Original Request ID: `700`
- `reason`: `"Khách hàng yêu cầu cấp lại link sau 24h"`
- Actor: `MANAGER`

### Các bước thực thi:
1. Gửi request `POST /api/v1/notification-requests/700/redeliver` qua `MockMvc` hoặc gọi trực tiếp service.
2. Kiểm tra phản hồi trả về `201 Created` kèm `newRequestId`.
3. Kiểm tra DB cho request mới và request 700 gốc.

## 4. Kiểm chứng kết quả tích hợp (Expected Assertions & Invariants)

- Request mới sinh có:
  - `original_request_id` = 700
  - `delivery_state` = `'PENDING'`
  - `credential_reference` mang giá trị mới được cấp
  - `business_data_snapshot` giữ nguyên không đổi so với request 700
- Request 700 gốc không bị sửa đổi trạng thái hay dữ liệu lịch sử.
- Bảng `notification_operational_audit` có bản ghi ghi nhận hành động `REDELIVER_REFRESHED_CREDENTIAL`.

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Command: NOT_RUN
- Actual result: NOT_RUN
<!-- EXECUTION:END -->

