---
id: E2E-013-006
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: MEMBER
isolation_key: E2E-013-006-{timestamp}
cleanup: Retain transcript history; remove only disposable test context.
---

# E2E-013-006 — Sidebar pagination and stable layout

Browser MCP: open Member consultations, capture loaded state, click `Load more`,
capture paginated state, and compare conversation references and sidebar width.
Assert no duplicate reference, selected conversation preservation, and equal
sidebar width in loading, empty and loaded screenshots.
