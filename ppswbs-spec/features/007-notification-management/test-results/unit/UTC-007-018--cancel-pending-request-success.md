---
id: UTC-007-018
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-017
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.OperationalService
source_method: cancelRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.OperationalServiceTest
test_method: shouldCancelPendingRequest
---

# UTC-007-018: Cancel pending delivery request with auditable reason

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `OperationalService#cancelRequest` — Delivery cancellation
- Test requirement: FR-017, WF-09 — An authorized operator can cancel a PENDING or RETRY_SCHEDULED request with a mandatory reason; request enters terminal CANCELLED state.
- Case type: N

### Preconditions

- Mock `NotificationRequestRepository#findById(30L)` returns request in `PENDING` state.
- Actor is MANAGER with ACTIVE account.

### Test data and actions

- Action: Invoke `operationalService.cancelRequest(30L, actor, command)` with:
  - `reason = "Customer cancelled order"`

### Expected assertions

- Return `CancelResponse` deliveryState is "CANCELLED".
- Target request `deliveryState` is "CANCELLED" and `terminalAt` is recorded.
- Verify `operationalAuditRepository.save(any(OperationalAuditRecord.class))` records CANCEL action.

### Exception and log expectation

- None — Request cancelled cleanly with audit entry.

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

