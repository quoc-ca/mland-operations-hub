---
id: UTC-007-012
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-008
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.TemplateManagementService
source_method: createDraft
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.TemplateManagementServiceTest
test_method: shouldCreateDraftVersion
---

# UTC-007-012: Create draft template version with allowed placeholders successfully

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `TemplateManagementService#createDraft` — Template version authoring
- Test requirement: FR-008, WF-05 — An authorized operator can draft a new presentation version for an active template definition using allowed placeholders.
- Case type: N

### Preconditions

- Mock `TemplateDefinitionRepository#findByCode("BOOKING_CONFIRMED")` returns ACTIVE definition with allowed placeholders: `["customerName", "bookingCode"]`.
- Mock `TemplateVersionRepository#findMaxVersionNumber(1L)` returns 2.

### Test data and actions

- Action: Invoke `service.createDraft("BOOKING_CONFIRMED", req, "admin_user")` with:
  - `subjectTemplate = "Booking {{bookingCode}}"`
  - `htmlTemplate = "<p>{{customerName}}</p>"`
  - `plainTextTemplate = "{{customerName}}"`

### Expected assertions

- Return `TemplateVersionDto` has:
  - `versionNumber` = 3
  - `status` = "DRAFT"
  - `createdBy` = "admin_user"
- Captured `TemplateVersion` is saved with immutable draft attributes.

### Exception and log expectation

- None — Draft is created without errors.

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

