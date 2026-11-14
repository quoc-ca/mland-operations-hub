---
id: UTC-007-009
feature: 007-notification-management
test_level: unit
test_case_type: B
source_ref: spec.md#FR-013
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.DeliveryWorkerService
source_method: deliverSingleRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.DeliveryWorkerServiceTest
test_method: shouldScheduleRetryInFiveMinutesOnSecondAttempt
---

# UTC-007-009: Temporary SMTP failure on second attempt schedules retry in 5 minutes

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## Test design

- Function code/name: `DeliveryWorkerService#deliverSingleRequest` — Second retry scheduling interval
- Test requirement: FR-013, WF-03 — On second temporary failure (attempt 2), worker sets nextAttemptAt to exactly 5 minutes (300s) from failure time.
- Case type: B

### Preconditions

- Mock `NotificationRequestRepository#findById(4L)` returns a request in `RETRY_SCHEDULED` state with `attemptCount = 1`.
- Mock `JavaMailSender.send(...)` throws `MailSendException("Connection timed out")`.

### Test data and actions

- Action: Invoke `workerService.deliverSingleRequest(4L)`.

### Expected assertions

- Captured updated request has:
  - `deliveryState` = "RETRY_SCHEDULED"
  - `attemptCount` = 2
  - `nextAttemptAt` is set approximately 300 seconds (5 minutes) from failure time
  - `terminalAt` remains null

### Exception and log expectation

- None — Worker logs warning and updates scheduling timestamps.

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=DeliveryWorkerServiceTest#shouldScheduleRetryInFiveMinutesOnSecondAttempt test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.DeliveryWorkerServiceTest.xml` testcase `shouldScheduleRetryInFiveMinutesOnSecondAttempt`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

