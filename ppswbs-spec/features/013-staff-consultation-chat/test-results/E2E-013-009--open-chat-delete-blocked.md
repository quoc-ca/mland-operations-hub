---
id: E2E-013-009
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: MEMBER
isolation_key: E2E-013-009-{timestamp}
cleanup: Close the isolated conversation; do not delete history.
---

# E2E-013-009 — Open conversation cannot be hidden

Browser MCP: attempt to remove an OPEN conversation. Assert the close-first
warning is displayed, no DELETE request is sent, and the transcript remains.
