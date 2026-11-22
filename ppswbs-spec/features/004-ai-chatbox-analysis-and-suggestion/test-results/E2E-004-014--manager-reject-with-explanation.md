---
scenario_id: E2E-004-014
feature: FE-004
actor: MANAGER
test_level: e2e
test_case_type: N
source_ref: spec.md#4
target_url: http://localhost:5173/design-review-requests
browser_tool: browsermcp
isolation_key: e2e-004-manager-reject
cleanup: [delete_review_fixture]
---
# Manager rejects with explanation
Manager selects `REJECTED`, enters a mandatory explanation, confirms, and the customer projection shows the rejection and explanation.
