---
scenario_id: E2E-004-006
feature: FE-004
actor: GUEST
test_level: e2e
test_case_type: N
source_ref: spec.md#3
target_url: http://localhost:5173/design-requests/new
browser_tool: browsermcp
isolation_key: e2e-004-guest-submit
cleanup: [delete_guest_request]
---
# Guest submits a human-review design request
Guest enters a description, uploads one valid image, sees `PASS`, submits, and receives an opaque tracking link. The request is visible as `NEED_REVIEW`; no AI or booking data is shown.
