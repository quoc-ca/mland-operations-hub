# Unit-Test Case Template

Use this template verbatim in shape, replacing every bracketed value. The metadata block follows `ppswbs-spec/.specify/metadata-policy.md`.

```markdown
---
id: UTC-001-001
feature: 001-account-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-001
source_class: edu.example.SourceClass
source_method: methodUnderTest
test_class: edu.example.SourceClassTest
test_method: shouldDoExpectedThing
---

# UTC-001-001: Short scenario title

**Date created**: YYYY-MM-DD
**Date updated**: YYYY-MM-DD
**Author**: Responsible member
**Last updated by**: Responsible member

## Test design

- Function code/name: [source method and business purpose]
- Test requirement: [observable requirement under test]
- Case type: N | A | B

### Preconditions

[Concrete fixture, mock, or state.] 

### Test data and actions

[Inputs and one method invocation.]

### Expected assertions

[Return value, exception, state change, and/or collaborator verification.]

### Exception and log expectation

[Expected exception/log behavior, or “None — reason”.]

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
```
