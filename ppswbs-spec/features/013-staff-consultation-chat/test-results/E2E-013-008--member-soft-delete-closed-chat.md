---
id: E2E-013-008
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: MEMBER
isolation_key: E2E-013-008-{timestamp}
cleanup: Do not hard-delete the conversation; retain operational history.
---

# E2E-013-008 — Member soft hides a closed conversation

Browser MCP: open a CLOSED conversation, choose Remove from my list, confirm the
dialog, and assert DELETE is sent only after confirmation. Assert the item leaves
the Member sidebar while Staff/Manager can still access the transcript.
