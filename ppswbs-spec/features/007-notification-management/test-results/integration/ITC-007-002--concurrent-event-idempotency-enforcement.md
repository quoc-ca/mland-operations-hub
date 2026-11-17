---
id: ITC-007-002
feature: 007-notification-management
test_level: integration
test_case_type: B
source_ref: spec.md#FR-006
test_scope: Database Unique Constraint & Concurrent Enqueue
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT
test_method: verifyConcurrentEventSubmissionProducesExactlyOneRequest
---

# ITC-007-002: Concurrent or repeated event submission enforces database-level idempotency

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## 1. Mục tiêu kiểm thử tích hợp (Integration Objective)

Xác minh yêu cầu FR-006 & SC-002: Khi có hai hoặc nhiều lời gọi đồng thời (race condition) hoặc tuần tự với cùng một sự kiện kinh doanh (`businessEventReference`), template, vai trò người nhận và fingerprint email, hệ thống phải tạo đúng duy nhất 1 bản ghi `NotificationRequest` gốc và không phát sinh lỗi 500 ra ngoài caller.

## 2. Tiền điều kiện & Môi trường tích hợp (Preconditions & Environment)

- Cơ sở dữ liệu khởi tạo đầy đủ Flyway migration `V93__notification_management.sql` với unique index trên `(business_event_reference, template_code, recipient_role, recipient_address_fingerprint) WHERE original_request_id IS NULL`.
- Template `RETAIL_ORDER_CONFIRMED` đang `ACTIVE`.

## 3. Dữ liệu thử nghiệm & Kịch bản thực thi (Test Data & Execution Steps)

### Dữ liệu thử nghiệm:
- `businessEventReference`: `"ORD-CONCUR-2026-001"`
- `templateCode`: `"RETAIL_ORDER_CONFIRMED"`
- `recipientEmail`: `"buyer.idempotent@example.com"`
- `businessDataSnapshot`: `{"orderCode": "ORD-CONCUR-2026-001", "orderTotal": "1,500,000 VND"}`

### Các bước thực thi:
1. Sử dụng `ExecutorService` kích hoạt đồng thời 5 thread cùng lúc gọi `notificationFacade.submitNotificationIntent(...)` với tham số giống hệt nhau.
2. Thu thập kết quả `NotificationIntentResult` từ cả 5 thread thông qua `Future`.
3. Kiểm tra các mã trạng thái phản hồi.

## 4. Kiểm chứng kết quả tích hợp (Expected Assertions & Invariants)

- Chính xác 1 thread nhận kết quả `status = ENQUEUED` với một ID mới sinh (ví dụ `requestId = N`).
- 4 thread còn lại nhận kết quả `status = DUPLICATE_IGNORED` và đều trỏ về cùng một `notificationRequestId = N`.
- **Kiểm tra DB:**
  ```sql
  SELECT COUNT(*) FROM notification_request WHERE business_event_reference = 'ORD-CONCUR-2026-001';
  ```
  Kết quả trả về chính xác là `1`.
- Không có thread nào bị ném `DataIntegrityViolationException` hay unhandled exception.

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=NotificationManagementIT#verifyConcurrentEventSubmissionProducesExactlyOneRequest test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT.xml` testcase `verifyConcurrentEventSubmissionProducesExactlyOneRequest`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

