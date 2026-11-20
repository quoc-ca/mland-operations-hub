---
scenario_id: E2E-004-013
feature: FE-004
actor: STAFF
test_level: e2e
test_case_type: N
source_ref: spec.md#4
target_url: http://localhost:5173/design-review-requests
browser_tool: browsermcp
isolation_key: e2e-004-staff-accept
cleanup: [delete_review_fixture]
---
# Staff accepts with explanation
Staff opens a pending request, selects `ACCEPTED`, enters a non-empty explanation, confirms, and sees the terminal state. Empty explanation is rejected before submission.
