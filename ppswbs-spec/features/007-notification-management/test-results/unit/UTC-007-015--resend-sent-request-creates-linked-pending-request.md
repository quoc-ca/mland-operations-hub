---
id: UTC-007-015
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-015
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.OperationalService
source_method: resendRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.OperationalServiceTest
test_method: shouldResendSentRequest
---

# UTC-007-015: Resend eligible SENT request clones request and links rendition

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `OperationalService#resendRequest` — Manual delivery resend
- Test requirement: FR-015, FR-018, WF-08 — An authorized operator can resend a SENT request with reason; a linked request is created in PENDING state retaining original rendition and recipient.
- Case type: N

### Preconditions

- Mock `NotificationRequestRepository#findById(10L)` returns original request in `SENT` state.
- Mock `RenditionRepository#findByNotificationRequestId(10L)` returns original rendition.
- Actor is MANAGER with ACTIVE account.

### Test data and actions

- Action: Invoke `operationalService.resendRequest(10L, actor, command)` with:
  - `reason = "Customer requested new email"`

### Expected assertions

- Return `ResendResponse` has:
  - `newRequestId` = 11L
  - `originalRequestId` = 10L
  - `deliveryState` = "PENDING"
- Verify `renditionRepository.save(any(Rendition.class))` copies rendition.
- Verify `operationalAuditRepository.save(any(OperationalAuditRecord.class))` records RESEND action.

### Exception and log expectation

- None — Cloned request enqueued cleanly with audit trail.

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

