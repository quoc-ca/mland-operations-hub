---
scenario_id: E2E-004-009
feature: FE-004
actor: GUEST_OR_MEMBER
test_level: e2e
test_case_type: A
source_ref: spec.md#3.1
target_url: http://localhost:5173/design-requests/new
browser_tool: browsermcp
isolation_key: e2e-004-blurry-image
cleanup: [delete_request]
---
# Blurry image is blocked
Upload a fixture below the configured blur threshold. The UI shows `TOO_BLURRY` immediately, prevents submit, and offers replace/remove actions. A bypassed backend request is rejected too.
