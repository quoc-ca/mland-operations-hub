---
scenario_id: E2E-004-012
feature: FE-004
actor: MEMBER
test_level: e2e
test_case_type: N
source_ref: spec.md#2
target_url: http://localhost:5173/my/designs?tab=custom
browser_tool: browsermcp
isolation_key: e2e-004-member-history
cleanup: [delete_member_request]
---
# Member request history
Member sees own requests, statuses and decision explanations. A second Member cannot read the request by changing the URL or request reference.
