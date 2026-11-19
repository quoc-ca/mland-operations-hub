---
scenario_id: E2E-004-010
feature: FE-004
actor: GUEST
test_level: e2e
test_case_type: B
source_ref: spec.md#3.2
target_url: http://localhost:5173/design-requests/new
browser_tool: browsermcp
isolation_key: e2e-004-guest-daily-quota
cleanup: [delete_guest_requests]
---
# Guest daily quota
Submit five valid Guest requests in the same calendar day. The sixth returns HTTP 429, displays the quota message, creates no request, and does not consume additional storage.
