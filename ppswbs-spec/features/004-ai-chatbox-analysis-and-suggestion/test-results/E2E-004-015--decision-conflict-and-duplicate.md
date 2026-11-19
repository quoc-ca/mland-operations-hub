---
scenario_id: E2E-004-015
feature: FE-004
actor: STAFF_AND_MANAGER
test_level: e2e
test_case_type: A
source_ref: spec.md#4
target_url: http://localhost:5173/design-review-requests
browser_tool: browsermcp
isolation_key: e2e-004-decision-conflict
cleanup: [delete_review_fixture]
---
# Decision conflict and idempotency
Submit the same decision twice with one idempotency key and race Staff/Manager decisions. The retry returns the original result; the competing terminal command returns 409 and no second decision is recorded.
