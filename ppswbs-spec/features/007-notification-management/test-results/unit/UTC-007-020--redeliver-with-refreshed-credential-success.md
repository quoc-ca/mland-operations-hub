---
id: UTC-007-020
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-019
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.OperationalService
source_method: redeliverWithRefreshedCredential
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.OperationalServiceTest
test_method: shouldRedeliverWithRefreshedCredential
---

# UTC-007-020: Redeliver notification with replacement credential preserving original snapshot

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `OperationalService#redeliverWithRefreshedCredential` — Refreshed credential redelivery
- Test requirement: FR-019, US4, WF-10 — When an access link or QR artifact expires, an operator can trigger redelivery; FE-007 creates a linked delivery with replacement credential while preserving original snapshot and history.
- Case type: N

### Preconditions

- Mock `NotificationRequestRepository#findById(50L)` returns original request whose credential has expired.
- Mock cross-module collaborator validates authorization and returns replacement ticket/QR link.
- Actor is MANAGER with ACTIVE account.

### Test data and actions

- Action: Invoke `operationalService.redeliverWithRefreshedCredential(50L, actor, command)` with:
  - `reason = "Khách hàng yêu cầu link vé mới sau khi vé cũ hết hạn 24h"`

### Expected assertions

- New `NotificationRequest` created in PENDING state linking `originalRequestId = 50L`.
- Original business snapshot and recipient address remain identical to request 50L.
- New credential reference is attached to new request.
- `OperationalAuditRecord` is saved with `actionType = "REDELIVER_REFRESHED_CREDENTIAL"`.

### Exception and log expectation

- None — Redelivery enqueued cleanly with audit trail.

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

