---
id: UTC-007-017
feature: 007-notification-management
test_level: unit
test_case_type: B
source_ref: spec.md#FR-016
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.OperationalService
source_method: resendRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.OperationalServiceTest
test_method: shouldRequireDuplicateRiskAcceptedForAdmin
---

# UTC-007-017: Require explicit duplicate risk acceptance when Admin resends UNKNOWN_OUTCOME

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `OperationalService#resendRequest` — Duplicate risk confirmation gate
- Test requirement: FR-016, WF-08 — An ADMIN can resend from UNKNOWN_OUTCOME only when the duplicate delivery risk is explicitly accepted (duplicateRiskAccepted = true).
- Case type: B

### Preconditions

- Mock `NotificationRequestRepository#findById(20L)` returns a request in `UNKNOWN_OUTCOME` state.
- Actor is ADMIN with ACTIVE account.

### Test data and actions

- Action: Invoke `operationalService.resendRequest(20L, actor, command)` with `duplicateRiskAccepted = false`.

### Expected assertions

- Method throws `NotificationManagementException` containing "duplicateRiskAccepted = true".
- Verify no new request is saved in `NotificationRequestRepository`.

### Exception and log expectation

- `NotificationManagementException` thrown due to missing risk acceptance parameter.

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

