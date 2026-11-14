---
id: UTC-007-005
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-022
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationRequestService
source_method: maskEmail
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationRequestServiceTest
test_method: testMaskEmail
---

# UTC-007-005: Email masking algorithm protects PII across standard address formats

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## Test design

- Function code/name: `NotificationRequestService#maskEmail` — Diagnostic email masking
- Test requirement: FR-021, FR-022 — Diagnostic and dashboard views expose only masked recipient email addresses to protect PII.
- Case type: N

### Preconditions

- Static helper `NotificationRequestService.maskEmail` initialized.

### Test data and actions

- Actions:
  - `maskEmail("n@example.com")`
  - `maskEmail("nguyenvana@gmail.com")`
  - `maskEmail("ab@yahoo.com")`
  - `maskEmail("invalid")`

### Expected assertions

- `maskEmail("n@example.com")` equals `"n***@example.com"`
- `maskEmail("nguyenvana@gmail.com")` equals `"n***a@gmail.com"`
- `maskEmail("ab@yahoo.com")` equals `"a***b@yahoo.com"`
- `maskEmail("invalid")` equals `"***"`

### Exception and log expectation

- None — Pure helper function handling string manipulation safely.

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=NotificationRequestServiceTest#testMaskEmail test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationRequestServiceTest.xml` testcase `testMaskEmail`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

