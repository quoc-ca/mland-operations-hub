---
id: ITC-007-004
feature: 007-notification-management
test_level: integration
test_case_type: A
source_ref: spec.md#FR-014
test_scope: Application Startup Event & Crash Recovery
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT
test_method: verifyStartupRecoveryTransitionsCrashToUnknownOutcome
---

# ITC-007-004: Application restart transitions interrupted delivery with started SMTP to UNKNOWN_OUTCOME

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## 1. Mục tiêu kiểm thử tích hợp (Integration Objective)

Xác minh yêu cầu FR-014 & SC-004: Khi ứng dụng bị sập hoặc khởi động lại trong lúc một request đang ở trạng thái `PROCESSING` và đã ghi nhận `smtp_invocation_started_at`, tiến trình khôi phục lúc khởi động (`RecoveryService`) phải chuyển request đó sang `UNKNOWN_OUTCOME` và worker tuyệt đối không được tự động gửi lại.

## 2. Tiền điều kiện & Môi trường tích hợp (Preconditions & Environment)

- Cơ sở dữ liệu đang chạy và kết nối ổn định.
- Chèn trực tiếp vào bảng `notification_request` một bản ghi mô phỏng tiến trình bị ngắt đột ngột:
  - `delivery_state = 'PROCESSING'`
  - `smtp_invocation_started_at = now() - 30 seconds`
  - `attempt_count = 1`

## 3. Dữ liệu thử nghiệm & Kịch bản thực thi (Test Data & Execution Steps)

### Dữ liệu thử nghiệm:
- Request ID: `999`
- `business_event_reference`: `"BK-CRASH-TEST-999"`

### Các bước thực thi:
1. Chèn bản ghi mô phỏng lỗi sập nguồn vào DB.
2. Bắn sự kiện `ApplicationReadyEvent` vào Spring ApplicationContext (hoặc gọi trực tiếp `recoveryService.recoverInterruptedRequests()`).
3. Kích hoạt tiếp chu kỳ quét của worker: `deliveryWorkerService.pollAndDeliver()`.
4. Quan sát trạng thái request và hoạt động của mock SMTP server.

## 4. Kiểm chứng kết quả tích hợp (Expected Assertions & Invariants)

- **Kiểm tra DB sau khi phục hồi:**
  - `notification_request` có ID = 999 chuyển trạng thái sang `'UNKNOWN_OUTCOME'`.
  - Bảng `notification_delivery_attempt` có bản ghi ghi nhận `outcome_category = 'UNKNOWN'`.
- **Worker polling sau đó:**
  - Worker bỏ qua bản ghi ID = 999; không thực hiện bất kỳ lệnh gửi nào tới GreenMail.
  - GreenMail server nhận được `0` email đối với request này.

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=NotificationManagementIT#verifyStartupRecoveryTransitionsCrashToUnknownOutcome test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT.xml` testcase `verifyStartupRecoveryTransitionsCrashToUnknownOutcome`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

