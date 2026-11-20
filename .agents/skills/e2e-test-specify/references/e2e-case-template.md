# E2E Test Case Template v2

Use this template for every new browser-driven E2E case. It is a black-box test: the feature spec supplies the user goal and observable outcome, while the tester discovers the UI path in the live product.

Save the case at `ppswbs-spec/features/<feature>/test-results/E2E-<feature-number>-<NNN>--<lowercase-slug>.md`.

```markdown
---
format_version: 2
id: E2E-001-001
feature: 001-account-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-001
target_url: http://localhost:5173/
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: customer-e2e-001-001
  account_ref: E2E_CUSTOMER_001
  isolation_key: E2E-001-001-<timestamp>
cleanup:
  required: true
  method: delete-record-via-api
---

# E2E-001-001: [Short user-facing scenario title]

**Created**: YYYY-MM-DD
**Updated**: YYYY-MM-DD
**Owner**: [QA / team member]

## 1. Scenario Charter

- **User goal**: [What the actor is trying to accomplish.]
- **Starting context**: [What the actor knows before opening `target_url`; do not reveal internal routes or UI controls.]
- **Scope**: [Observable journey and risks covered.]
- **Out of scope**: [What this case intentionally does not assess.]

## 2. Preconditions and Test Data

- [ ] Required services in `health_urls` are ready.
- [ ] Fixture `[fixture]` is available and isolated by `[isolation_key]`.
- [ ] Test account/role is available; obtain secrets from the approved secret store.
- [ ] Starting session: [anonymous / authenticated as role].

## 3. Exploratory Step Contract

### Step 1 — [User intent]

- **User charter**: [The outcome the actor is trying to find or complete.]
- **Explore & act**: [Observe rendered UI, choose an affordance a real actor can find, then act. Do not prescribe private routes, DOM selectors, source-code knowledge, or a hidden click path.]
- **Expected**:
  - [Observable UI, state, or feedback expected by the actor.]
  - [Add as many assertions as needed.]
  - [Use `None — transition only` when this step intentionally has no assertion.]
- **Decision rule**:
  - `CONTINUE`: [Conditions showing this step can proceed.]
  - `LOG_AND_CONTINUE`: [Non-blocking unexpected finding to record.]
  - `STOP`: [Core expectation, data integrity, authorization/security, or environment condition that makes the journey invalid or impossible to continue.]

### Step 2 — [Next user intent]

- **User charter**: [...]
- **Explore & act**: [...]
- **Expected**:
  - [...]
- **Decision rule**:
  - `CONTINUE`: [...]
  - `LOG_AND_CONTINUE`: [...]
  - `STOP`: [...]

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: NOT_RUN | PASS | PASS_WITH_FINDINGS | FAIL | BLOCKED
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Browser tool: browsermcp
- Final step: NOT_RUN
- Actual result: NOT_RUN
- Findings: NONE
- Evidence screenshots: NONE
- Bug references: NONE
<!-- EXECUTION:END -->
```
