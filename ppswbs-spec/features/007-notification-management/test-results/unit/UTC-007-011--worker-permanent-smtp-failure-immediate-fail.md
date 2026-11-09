---
id: UTC-007-011
feature: 007-notification-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-013
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.DeliveryWorkerService
source_method: deliverSingleRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.DeliveryWorkerServiceTest
test_method: shouldFailImmediatelyOnPermanentSmtpError
---

# UTC-007-011: Permanent SMTP 5xx rejection terminates delivery immediately without retrying

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `DeliveryWorkerService#deliverSingleRequest` — Permanent failure detection
- Test requirement: FR-013, WF-03 — When SMTP server rejects message with a permanent 5xx error (e.g. 550 Mailbox not found), worker aborts retry schedule and immediately marks request FAILED.
- Case type: A

### Preconditions

- Mock `NotificationRequestRepository#findById(5L)` returns a PENDING request with `attemptCount = 0`.
- Mock `JavaMailSender.send(...)` throws `MailSendException("550 5.1.1 User unknown")`.

### Test data and actions

- Action: Invoke `workerService.deliverSingleRequest(5L)`.

### Expected assertions

- Captured updated request has:
  - `deliveryState` = "FAILED"
  - `attemptCount` = 1
  - `terminalAt` is not null
- `DeliveryAttempt` records `outcomeCategory` = "REJECTED_PERMANENT".

### Exception and log expectation

- None — Permanent rejection is classified and audited safely.

## Execution

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Command: NOT_RUN
- Surefire evidence: NOT_RUN
- Actual result: NOT_RUN
- Runs: 0
- PASS: 0
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

