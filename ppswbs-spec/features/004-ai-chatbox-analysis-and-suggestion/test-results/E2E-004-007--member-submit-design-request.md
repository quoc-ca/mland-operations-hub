---
scenario_id: E2E-004-007
feature: FE-004
actor: MEMBER
test_level: e2e
test_case_type: N
source_ref: spec.md#3
target_url: http://localhost:5173/design-requests/new
browser_tool: browsermcp
isolation_key: e2e-004-member-submit
cleanup: [delete_member_request]
---
# Member submits a human-review design request
Member submits description-only, image-only and combined requests. Each valid request enters `NEED_REVIEW` and is visible only to its owner.
