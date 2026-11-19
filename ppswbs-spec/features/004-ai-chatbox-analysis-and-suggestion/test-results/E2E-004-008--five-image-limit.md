---
scenario_id: E2E-004-008
feature: FE-004
actor: GUEST_OR_MEMBER
test_level: e2e
test_case_type: B
source_ref: spec.md#3
target_url: http://localhost:5173/design-requests/new
browser_tool: browsermcp
isolation_key: e2e-004-five-images
cleanup: [delete_request]
---
# Five-image request limit
Upload five valid images, then attempt a sixth. The sixth slot is blocked, a localized limit message appears, and the request still contains exactly five images.
