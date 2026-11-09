---
id: UTC-007-007
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-012
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.DeliveryWorkerService
source_method: deliverSingleRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.DeliveryWorkerServiceTest
test_method: shouldDeliverSuccessfully
---

# UTC-007-007: Successful SMTP delivery transitions request state to SENT

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `DeliveryWorkerService#deliverSingleRequest` — Single delivery execution
- Test requirement: FR-011, FR-012, WF-02 — When SMTP provider successfully accepts message, worker updates request state to SENT, sets attemptCount = 1, records terminal timestamp, and writes DeliveryAttempt record.
- Case type: N

### Preconditions

- Mock `NotificationRequestRepository#findById(1L)` returns a PENDING request with `attemptCount = 0`.
- Mock `RenditionService#getOrCreateRendition` returns a valid rendition with rendered HTML and subject.
- Mock `JavaMailSender` is available and accepts message without error.

### Test data and actions

- Action: Invoke `workerService.deliverSingleRequest(1L)`.

### Expected assertions

- `javaMailSender.send(any(MimeMessage.class))` is verified called once.
- `deliveryAttemptRepository.save(any(DeliveryAttempt.class))` is verified called once.
- Captured updated request has:
  - `deliveryState` = "SENT"
  - `attemptCount` = 1
  - `terminalAt` is not null

### Exception and log expectation

- None — Delivery executes cleanly to completion.

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

