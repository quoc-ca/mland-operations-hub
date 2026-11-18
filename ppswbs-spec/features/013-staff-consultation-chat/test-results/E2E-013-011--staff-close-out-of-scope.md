---
id: E2E-013-011
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: STAFF
isolation_key: E2E-013-011-{timestamp}
cleanup: No business mutation; retain denied-access evidence.
---

# E2E-013-011 — Staff cannot close an out-of-scope conversation

Assert direct navigation and close command for another location return 403 or
safe 404, with no status mutation and no transcript disclosure.
