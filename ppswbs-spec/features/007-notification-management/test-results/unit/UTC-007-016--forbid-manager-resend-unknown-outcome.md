---
id: UTC-007-016
feature: 007-notification-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-016
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.OperationalService
source_method: resendRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.OperationalServiceTest
test_method: shouldForbidManagerResendUnknownOutcome
---

# UTC-007-016: Forbid Manager from resending requests with UNKNOWN_OUTCOME state

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## Test design

- Function code/name: `OperationalService#resendRequest` — UNKNOWN_OUTCOME role enforcement
- Test requirement: FR-016, WF-08 — Only an ADMIN may authorize a resend from UNKNOWN_OUTCOME; a MANAGER role attempting resend must be denied.
- Case type: A

### Preconditions

- Mock `NotificationRequestRepository#findById(20L)` returns a request in `UNKNOWN_OUTCOME` state.
- Actor is MANAGER with ACTIVE account.

### Test data and actions

- Action: Invoke `operationalService.resendRequest(20L, actor, command)` with `duplicateRiskAccepted = true`.

### Expected assertions

- Method throws `NotificationManagementException` containing "Chỉ Quản trị viên (ADMIN) mới có quyền".
- Verify no new request is saved in `NotificationRequestRepository`.

### Exception and log expectation

- `NotificationManagementException` thrown with 403 Forbidden semantics.

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=OperationalServiceTest#shouldForbidManagerResendUnknownOutcome test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.OperationalServiceTest.xml` testcase `shouldForbidManagerResendUnknownOutcome`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

