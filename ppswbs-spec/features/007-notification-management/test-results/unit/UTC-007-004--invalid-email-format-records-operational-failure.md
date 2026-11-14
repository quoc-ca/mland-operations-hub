---
id: UTC-007-004
feature: 007-notification-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-010
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationRequestService
source_method: submitNotificationIntent
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationRequestServiceTest
test_method: shouldRecordFailureWhenEmailInvalid
---

# UTC-007-004: Record operational failure when recipient email format is invalid

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## Test design

- Function code/name: `NotificationRequestService#submitNotificationIntent` — Recipient email validation
- Test requirement: FR-010, FR-020 — When the submitted recipient email lacks valid email syntax, the system records an OperationalFailure with code INVALID_RECIPIENT without rolling back caller transaction.
- Case type: A

### Preconditions

- Mock `OperationalFailureRepository#save` returns saved failure with ID = 501L.

### Test data and actions

- Action: Invoke `service.submitNotificationIntent(cmd)` with:
  - `businessEventReference = "BK-003"`
  - `templateCode = "BOOKING_CONFIRMED"`
  - `recipientEmail = "invalid-email-no-at"`

### Expected assertions

- Return result status is `NotificationIntentResult.Status.OPERATIONAL_FAILURE`.
- Return result `failureCode` is `"INVALID_RECIPIENT"`.
- Verify `notificationRequestRepository.save(any())` is never called.

### Exception and log expectation

- None — Method returns structured failure result instead of crashing.

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=NotificationRequestServiceTest#shouldRecordFailureWhenEmailInvalid test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationRequestServiceTest.xml` testcase `shouldRecordFailureWhenEmailInvalid`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

