---
id: UTC-007-025
feature: 007-notification-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-002
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.TemplateManagementService
source_method: createDraft
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.TemplateManagementServiceTest
test_method: shouldRejectDraftCreationForExternalTemplate
---

# UTC-007-025: Reject draft creation on EXTERNAL template

**Date created**: 2026-11-09  
**Date updated**: 2026-11-09  
**Author**: Senior QA & System Verification Engineer  
**Last updated by**: Senior QA & System Verification Engineer  

## Test design

- Function code/name: `TemplateManagementService#createDraft` — External template mutation guard
- Test requirement: FR-001, FR-002 — The system MUST treat Firebase verification and password-reset email as Firebase-managed, read-only references; it MUST NOT create or activate version drafts for EXTERNAL templates.
- Case type: A

### Preconditions

- Mock `TemplateDefinitionRepository#findByCode("AUTH_EMAIL_VERIFICATION")` returns a `TemplateDefinition` with `lifecycleStatus = "EXTERNAL"`.

### Test data and actions

- Action: Invoke `service.createDraft("AUTH_EMAIL_VERIFICATION", req, "admin_user")`.

### Expected assertions

- Method throws `NotificationManagementException` containing "External templates are managed by an external provider".
- Verify `templateVersionRepository.save(any())` is never called.

### Exception and log expectation

- `NotificationManagementException` thrown with error code `CANNOT_EDIT_EXTERNAL_TEMPLATE`.

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T10:24:43Z
- Executed by: Senior QA & System Verification Engineer
- Command: `mvn -q -Dtest=TemplateManagementServiceTest#shouldRejectDraftCreationForExternalTemplate test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.TemplateManagementServiceTest.xml` testcase `shouldRejectDraftCreationForExternalTemplate`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->
