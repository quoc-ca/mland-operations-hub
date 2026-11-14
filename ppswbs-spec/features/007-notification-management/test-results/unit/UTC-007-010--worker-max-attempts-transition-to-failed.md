---
id: UTC-007-010
feature: 007-notification-management
test_level: unit
test_case_type: B
source_ref: spec.md#FR-013
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.DeliveryWorkerService
source_method: deliverSingleRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.DeliveryWorkerServiceTest
test_method: shouldFailAfterMaxAttempts
---

# UTC-007-010: Third failed SMTP attempt transitions request to terminal FAILED state

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## Test design

- Function code/name: `DeliveryWorkerService#deliverSingleRequest` — Max attempts termination
- Test requirement: FR-013, SC-003, WF-03 — System enforces maximum 3 SMTP attempts. On 3rd failure, request transitions to terminal state FAILED with terminal_at set.
- Case type: B

### Preconditions

- Mock `NotificationRequestRepository#findById(3L)` returns a request in `RETRY_SCHEDULED` state with `attemptCount = 2`.
- Mock `JavaMailSender.send(...)` throws `MailSendException("Timeout connecting to mail host")`.

### Test data and actions

- Action: Invoke `workerService.deliverSingleRequest(3L)`.

### Expected assertions

- Captured updated request has:
  - `deliveryState` = "FAILED"
  - `attemptCount` = 3
  - `terminalAt` is not null
  - `nextAttemptAt` is cleared (or ignored)
- `deliveryAttemptRepository.save(any(DeliveryAttempt.class))` is verified called.

### Exception and log expectation

- None — Failure is gracefully recorded in request and delivery attempt tables.

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=DeliveryWorkerServiceTest#shouldFailAfterMaxAttempts test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.DeliveryWorkerServiceTest.xml` testcase `shouldFailAfterMaxAttempts`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

