---
scenario_id: E2E-004-017
feature: FE-004
actor: GUEST_OR_MEMBER
test_level: e2e
test_case_type: B
source_ref: spec.md#6
target_url: http://localhost:5173/design-requests/new
browser_tool: browsermcp
isolation_key: e2e-004-locale-unicode
cleanup: [delete_request]
---
# Locale and image/font regression
Verify Vietnamese and English screens do not mix languages, Vietnamese diacritics render correctly, image quality messages are readable, and no AI labels or mojibake remain.
