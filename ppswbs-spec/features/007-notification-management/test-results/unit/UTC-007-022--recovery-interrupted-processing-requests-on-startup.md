---
id: UTC-007-022
feature: 007-notification-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-014
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.RecoveryService
source_method: recoverInterruptedRequests
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.RecoveryServiceTest
test_method: shouldRecoverToUnknownOutcomeWhenSmtpStarted
---

# UTC-007-022: Recover interrupted PROCESSING requests on system startup safely

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `RecoveryService#recoverInterruptedRequests` — Crash recovery
- Test requirement: FR-014, SC-004, WF-04 — An application restart recovers interrupted PROCESSING requests: requests with recorded SMTP invocation become UNKNOWN_OUTCOME without auto-retry; requests without invocation return to PENDING.
- Case type: A

### Preconditions

- Mock `NotificationRequestRepository#findByDeliveryStateIn(List.of("PROCESSING"))` returns:
  - `req1` (ID=101L, `smtpInvocationStartedAt` set 60s ago)
  - `req2` (ID=102L, `smtpInvocationStartedAt` is null)

### Test data and actions

- Action: Invoke `recoveryService.recoverInterruptedRequests()`.

### Expected assertions

- Return recovery count is 2.
- `req1.getDeliveryState()` transitions to "UNKNOWN_OUTCOME".
- `req2.getDeliveryState()` transitions to "PENDING".
- Both updated entities are saved to `NotificationRequestRepository`.

### Exception and log expectation

- None — Recovery scans at startup and updates state safely without throwing.

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

