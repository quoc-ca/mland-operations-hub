---
id: UTC-007-003
feature: 007-notification-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-010
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationRequestService
source_method: submitNotificationIntent
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationRequestServiceTest
test_method: shouldRecordFailureWhenTemplateInactive
---

# UTC-007-003: Record operational failure when template is inactive without throwing exception

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `NotificationRequestService#submitNotificationIntent` — Inactive template validation
- Test requirement: FR-010, SC-001 — When a requested template is inactive or in draft state, the system records an OperationalFailure and does not throw an exception or rollback caller transaction.
- Case type: A

### Preconditions

- Mock `TemplateDefinitionRepository` returns a template definition with `lifecycleStatus = "DRAFT"`.
- Mock `OperationalFailureRepository#save` returns saved failure with ID = 500L.

### Test data and actions

- Action: Invoke `service.submitNotificationIntent(cmd)` with:
  - `businessEventReference = "BK-002"`
  - `templateCode = "BOOKING_CONFIRMED"`
  - `recipientEmail = "customer@example.com"`

### Expected assertions

- Return result status is `NotificationIntentResult.Status.OPERATIONAL_FAILURE`.
- Return result `failureCode` is `"TEMPLATE_INACTIVE"`.
- Return result `operationalFailureId` is 500L.
- Verify `notificationRequestRepository.save(any())` is never called.

### Exception and log expectation

- None — Must not throw runtime exception to avoid breaking the caller's business transaction.

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

