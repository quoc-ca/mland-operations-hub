---
id: UTC-007-002
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-006
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationRequestService
source_method: submitNotificationIntent
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationRequestServiceTest
test_method: shouldIgnoreDuplicateIntent
---

# UTC-007-002: Ignore duplicate notification intent with matching fingerprint

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `NotificationRequestService#submitNotificationIntent` — Idempotency check
- Test requirement: FR-006, SC-002 — Submitting a notification intent with the same business event reference, template code, role, and recipient fingerprint returns DUPLICATE_IGNORED and creates no second request.
- Case type: N

### Preconditions

- Mock `TemplateDefinitionRepository` returns an ACTIVE `BOOKING_CONFIRMED` template definition.
- Mock `TemplateVersionRepository` returns an ACTIVE `TemplateVersion`.
- Mock `NotificationRequestRepository#findByBusinessEventReferenceAndTemplateCodeAndRecipientRoleAndRecipientAddressFingerprintAndOriginalRequestIsNull` returns an existing `NotificationRequest` with ID = 77L.

### Test data and actions

- Action: Invoke `service.submitNotificationIntent(cmd)` with:
  - `businessEventReference = "BK-001"`
  - `templateCode = "BOOKING_CONFIRMED"`
  - `recipientEmail = "customer@example.com"`

### Expected assertions

- Return result status is `NotificationIntentResult.Status.DUPLICATE_IGNORED`.
- Return result `notificationRequestId` is 77L.
- Verify `notificationRequestRepository.save(any())` is never called.

### Exception and log expectation

- None — Method absorbs duplicate gracefully without throwing exceptions.

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

