---
name: e2e-test-specify
description: Create one traceable, black-box Markdown E2E scenario for a PPSWBS feature, executed through browsermcp.
metadata:
  short-description: Create an exploratory E2E browser scenario with step decisions
---

# E2E Test Scenario Specify

Use this skill to create a new E2E/UI integration scenario at `ppswbs-spec/features/<feature>/test-results/E2E-<feature-number>-<NNN>--<lowercase-slug>.md`.

## 1. Required Discovery

Read the feature's `spec.md` to establish the actor, business goal, acceptance criteria, safe test data, and source traceability.

Create a **black-box journey**. Do not read product source code, `design/ui-flow.md`, `screen.md`, API contracts, internal routes, selectors, or existing test cases to pre-plan the UI path. The actor begins at the public `target_url` and discovers the available UI through observation during execution.

Perform adversarial risk analysis for applicable invalid input, repeat submission, slow/failed loading, lost session, wrong role, duplicate data, and server error. Cover an applicable risk in an `A` (abnormal) or `B` (boundary) case; do not treat the happy path as sufficient by default.

## 2. Output Contract

- Use [the v2 case template](references/e2e-case-template.md) exactly in shape.
- Set `format_version: 2`; the execution skill accepts only this format.
- Keep required YAML fields: `id`, `feature`, `test_level`, `test_case_type`, `source_ref`, `target_url`, `actor_role`, `browser_tool: browsermcp`, `health_urls`, `test_data.isolation_key`, and `cleanup`.
- Do not record passwords, tokens, or other secrets in the case.
- Do not modify existing E2E cases merely to conform to v2.

## 3. Step Contract

Each `Step N` must contain:

1. **User charter** — intent in the actor's language, not an implementation instruction.
2. **Explore & act** — inspect live UI affordances and act through the one a real user can find. Do not prescribe a private route, selector, or source-derived click path.
3. **Expected** — one or more concrete, observable bullets; no maximum. For a deliberate transition-only step, write `None — transition only`.
4. **Decision rule** — explicit triggers for:
   - `CONTINUE`: expectations allow the journey to move on.
   - `LOG_AND_CONTINUE`: a non-blocking unexpected finding; execution continues after recording it.
   - `STOP`: environment blocker, failed core expectation, data-integrity issue, or authorization/security concern.

Expected results must be observable in UI or user-visible state. Browser commands, console output, and network traffic are evidence tools, not substitutes for user-facing expectations. Do not use ambiguous alternatives such as "A or B".

## 4. Evidence and Completion Rules

- Every `LOG_AND_CONTINUE` and `STOP` outcome requires a screenshot, concise problem description, and reproducible user actions.
- Create a `BUG-LOG.md` reference when a finding is a confirmed product/spec defect.
- The case begins with `NOT_RUN`; do not write execution results while specifying it.
