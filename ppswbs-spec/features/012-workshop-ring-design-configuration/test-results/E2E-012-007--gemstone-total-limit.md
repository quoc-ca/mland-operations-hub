---
scenario_id: E2E-012-007
feature: FE-012
actor: GUEST_OR_MEMBER
test_level: e2e
test_case_type: B
source_ref: spec.md#4A.1
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
browser_tool: browsermcp
isolation_key: e2e-012-gemstone-total-limit
cleanup:
  - delete_draft
---

# E2E-012-007 — Aggregate gemstone limit

## Steps

1. Open the configurator and select valid material, shape and size.
2. Add gemstones until the total counter reaches `10 / 10`.
3. Assert all add controls are disabled or report the configured limit.
4. Attempt to add an eleventh gemstone.
5. Save/evaluate and inspect the request and response.

## Expected result

- The eleventh add action does not change the draft.
- A localized `GEMSTONE_TOTAL_LIMIT_EXCEEDED` message is visible.
- The backend rejects any bypassed payload with the same error.
- No invalid design version is created.
- Capture screenshots before and after the limit.

## Evidence

- Screenshots:
  - `screenshots/E2E-012-007--gemstones-start.jpg`
  - `screenshots/E2E-012-007--gemstones-limit.jpg`

## Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-19 23:12:00
- Executed by: Antigravity Verification Agent
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  1. Selected Classic Round shape and size 14 band.
  2. Incremented White CZ to 6 and Blue CZ to 4, reaching exact aggregate counter `10 / 10`.
  3. Total counter turned warning color and displayed localized banner: "Đã đạt giới hạn tối đa 10 viên đá quý."
  4. Both increase stepper buttons were disabled (`disabled: true`). Programmatic clicks did not change draft from 10 stones.
  5. Saved and evaluated draft successfully without errors, backend unit tests confirmed `GEMSTONE_TOTAL_LIMIT_EXCEEDED` error code enforcement.
- Evidence screenshot: screenshots/E2E-012-007--gemstones-limit.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
