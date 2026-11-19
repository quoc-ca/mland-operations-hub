---
id: E2E-013-007
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: MEMBER
isolation_key: E2E-013-007-{timestamp}
cleanup: Close the isolated conversation; retain transcript and audit evidence.
---

# E2E-013-007 — General consultation without design

Browser MCP: open New Consultation, choose General consultation, enter Vietnamese
initial text and submit. Assert the request sends all context fields as null,
the conversation is created, and Vietnamese text renders without corruption.
