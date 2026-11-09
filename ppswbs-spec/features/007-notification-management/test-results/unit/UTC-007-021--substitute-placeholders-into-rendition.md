---
id: UTC-007-021
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-011
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.RenditionService
source_method: substitute
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.RenditionServiceTest
test_method: shouldSubstitutePlaceholders
---

# UTC-007-021: Substitute business snapshot variables into subject and content rendition

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `RenditionService#substitute` — Template variable substitution
- Test requirement: FR-011 — The first delivery processing of a request generates rendered subject, HTML, and text by substituting frozen business data into template placeholders.
- Case type: N

### Preconditions

- `RenditionService` instantiated with `ObjectMapper`.

### Test data and actions

- Action: Invoke `renditionService.substitute(template, data)` with:
  - `template = "Hello {{customerName}}, booking #{{bookingCode}}."`
  - `data = Map.of("customerName", "Alice", "bookingCode", "BK-100")`

### Expected assertions

- Output string equals `"Hello Alice, booking #BK-100."`.

### Exception and log expectation

- None — Pure string substitution executes safely.

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

