---
scenario_id: E2E-004-016
feature: FE-004
actor: ADMIN_AND_UNAUTHORIZED
test_level: e2e
test_case_type: A
source_ref: spec.md#2
target_url: http://localhost:5173/design-review-requests
browser_tool: browsermcp
isolation_key: e2e-004-permission-boundary
cleanup: [delete_review_fixture]
---
# Permission boundary
Guest/Member cannot access the operational queue, Admin cannot accept/reject, and unauthenticated access receives safe denial without leaking request existence.
