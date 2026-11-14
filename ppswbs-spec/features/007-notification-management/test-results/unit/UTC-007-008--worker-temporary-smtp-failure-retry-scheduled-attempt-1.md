---
id: UTC-007-008
feature: 007-notification-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-013
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.DeliveryWorkerService
source_method: deliverSingleRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.DeliveryWorkerServiceTest
test_method: shouldScheduleRetryOnTemporaryError
---

# UTC-007-008: Temporary SMTP failure on first attempt schedules retry in 1 minute

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## Test design

- Function code/name: `DeliveryWorkerService#deliverSingleRequest` — First retry scheduling
- Test requirement: FR-013, FR-028, WF-03 — On first temporary failure (attempt 1), worker schedules next delivery attempt 1 minute later with state RETRY_SCHEDULED.
- Case type: A

### Preconditions

- Mock `NotificationRequestRepository#findById(2L)` returns a PENDING request with `attemptCount = 0`.
- Mock `JavaMailSender.send(...)` throws `MailSendException("Connection refused connecting to mail host")`.

### Test data and actions

- Action: Invoke `workerService.deliverSingleRequest(2L)`.

### Expected assertions

- Captured updated request has:
  - `deliveryState` = "RETRY_SCHEDULED"
  - `attemptCount` = 1
  - `nextAttemptAt` is set approximately 60 seconds from current time
  - `terminalAt` remains null

### Exception and log expectation

- Worker catches `MailException` internally and does not let it bubble out.

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=DeliveryWorkerServiceTest#shouldScheduleRetryOnTemporaryError test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.DeliveryWorkerServiceTest.xml` testcase `shouldScheduleRetryOnTemporaryError`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

