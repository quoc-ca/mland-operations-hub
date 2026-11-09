---
id: UTC-007-013
feature: 007-notification-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-010
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.TemplateManagementService
source_method: createDraft
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.TemplateManagementServiceTest
test_method: shouldRejectUnauthorizedPlaceholder
---

# UTC-007-013: Reject draft template version containing unauthorized placeholders

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `TemplateManagementService#createDraft` — Draft template placeholder validation
- Test requirement: FR-010, WF-05 — The system strictly enforces the registered template's allowed placeholder whitelist when saving a draft.
- Case type: A

### Preconditions

- Mock `TemplateDefinitionRepository#findByCode("BOOKING_CONFIRMED")` returns definition with allowed placeholders: `["customerName"]`.
- Mock `RenditionService#extractPlaceholders` detects `customerName` and `unauthorizedField`.

### Test data and actions

- Action: Invoke `service.createDraft("BOOKING_CONFIRMED", req, "admin_user")` where `subjectTemplate` contains `"Booking {{unauthorizedField}}"`.

### Expected assertions

- Method throws `NotificationManagementException` containing "unauthorized placeholder".
- Verify `templateVersionRepository.save(any())` is never called.

### Exception and log expectation

- `NotificationManagementException` thrown with descriptive validation error message.

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

