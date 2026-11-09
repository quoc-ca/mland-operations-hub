---
id: UTC-007-006
feature: 007-notification-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-010
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationRequestService
source_method: submitNotificationIntent
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationRequestServiceTest
test_method: shouldRecordFailureWhenRequiredPlaceholderMissing
---

# UTC-007-006: Record operational failure when required snapshot placeholder is missing

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## Test design

- Function code/name: `NotificationRequestService#submitNotificationIntent` — Placeholder validation
- Test requirement: FR-010 — When notification intent snapshot is missing mandatory placeholders required by the active template version, the system records an OperationalFailure with code REQUIRED_PLACEHOLDER_MISSING.
- Case type: A

### Preconditions

- Mock `TemplateDefinitionRepository` returns ACTIVE definition for `BOOKING_CONFIRMED`.
- Mock `TemplateVersionRepository` returns ACTIVE version requiring `bookingCode` and `customerName`.
- Mock `RenditionService#extractPlaceholders` returns `Set.of("bookingCode", "customerName")`.
- Mock `OperationalFailureRepository#save` returns failure entity with ID = 502L.

### Test data and actions

- Action: Invoke `service.submitNotificationIntent(cmd)` where `businessDataSnapshot` provides only `Map.of("customerName", "Customer A")` (missing `bookingCode`).

### Expected assertions

- Return result status is `NotificationIntentResult.Status.OPERATIONAL_FAILURE`.
- Return result `failureCode` is `"REQUIRED_PLACEHOLDER_MISSING"`.
- Return result `operationalFailureId` is 502L.
- Verify `notificationRequestRepository.save(any())` is never called.

### Exception and log expectation

- None — Safe error record retained, caller business transaction preserved.

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=NotificationRequestServiceTest#shouldRecordFailureWhenRequiredPlaceholderMissing test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationRequestServiceTest.xml` testcase `shouldRecordFailureWhenRequiredPlaceholderMissing`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

