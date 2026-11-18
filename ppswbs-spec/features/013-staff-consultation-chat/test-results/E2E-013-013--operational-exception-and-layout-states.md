---
id: E2E-013-013
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: STAFF
actor_secondary: MANAGER
isolation_key: E2E-013-013-{timestamp}
cleanup: Retain audit evidence; remove disposable test context only.
---

# E2E-013-013 — Operational exceptions and layout states

Assert loading and empty sidebar states keep the loaded width; 403, 409, 404 and
503 responses show safe messages without collapsing the layout; Staff and Manager
close controls appear only within permitted scope.
