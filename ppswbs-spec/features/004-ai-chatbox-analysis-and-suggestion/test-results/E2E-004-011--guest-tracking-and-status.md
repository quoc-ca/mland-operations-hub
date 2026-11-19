---
scenario_id: E2E-004-011
feature: FE-004
actor: GUEST
test_level: e2e
test_case_type: N
source_ref: spec.md#4
target_url: http://localhost:5173/design-requests/new
browser_tool: browsermcp
isolation_key: e2e-004-guest-tracking
cleanup: [delete_guest_request]
---
# Guest tracking and decision explanation
Open the returned opaque link, verify `NEED_REVIEW`, then after a Staff decision verify the Guest sees `ACCEPTED` or `REJECTED` and the exact safe explanation without reviewer private data.
