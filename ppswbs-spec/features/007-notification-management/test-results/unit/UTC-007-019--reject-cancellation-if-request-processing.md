---
id: UTC-007-019
feature: 007-notification-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-017
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.OperationalService
source_method: cancelRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.OperationalServiceTest
test_method: shouldRejectCancelIfProcessing
---

# UTC-007-019: Reject cancellation when request is actively in PROCESSING state

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `OperationalService#cancelRequest` — Processing cancellation protection
- Test requirement: FR-017, WF-09 — A request in PROCESSING state must not be cancelled because SMTP transmission may already be underway.
- Case type: A

### Preconditions

- Mock `NotificationRequestRepository#findById(40L)` returns request in `PROCESSING` state.
- Actor is ADMIN with ACTIVE account.

### Test data and actions

- Action: Invoke `operationalService.cancelRequest(40L, actor, command)` with `reason = "Cancel request"`.

### Expected assertions

- Method throws `NotificationManagementException` containing "đang trong tiến trình xử lý".
- Verify request `deliveryState` remains "PROCESSING".
- Verify `operationalAuditRepository.save(any())` is never called.

### Exception and log expectation

- `NotificationManagementException` thrown due to concurrent state conflict.

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

