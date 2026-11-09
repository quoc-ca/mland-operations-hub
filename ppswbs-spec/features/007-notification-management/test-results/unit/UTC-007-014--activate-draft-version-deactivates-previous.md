---
id: UTC-007-014
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-009
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.TemplateManagementService
source_method: activateVersion
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.TemplateManagementServiceTest
test_method: shouldActivateDraftAndDeactivatePrevious
---

# UTC-007-014: Activating a draft version atomically deactivates previous active version

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## Test design

- Function code/name: `TemplateManagementService#activateVersion` — Template version lifecycle activation
- Test requirement: FR-009, SC-005, WF-06 — When a draft version is activated, it becomes the sole ACTIVE version for new requests and any prior active version transitions to DEACTIVATED.
- Case type: N

### Preconditions

- Mock `TemplateDefinitionRepository` returns ACTIVE definition for `BOOKING_CONFIRMED`.
- Mock `TemplateVersionRepository` returns current active version (versionNumber = 1) and target draft version (versionNumber = 2).

### Test data and actions

- Action: Invoke `service.activateVersion("BOOKING_CONFIRMED", 2, "mgr_01")`.

### Expected assertions

- Return `ActivateVersionResponse` has:
  - `activatedVersionNumber` = 2
  - `deactivatedVersionNumber` = 1
  - `activatedBy` = "mgr_01"
- Target draft version status changes to "ACTIVE".
- Previous active version status changes to "DEACTIVATED".

### Exception and log expectation

- None — Atomic lifecycle state transition commits cleanly.

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=TemplateManagementServiceTest#shouldActivateDraftAndDeactivatePrevious test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.TemplateManagementServiceTest.xml` testcase `shouldActivateDraftAndDeactivatePrevious`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

