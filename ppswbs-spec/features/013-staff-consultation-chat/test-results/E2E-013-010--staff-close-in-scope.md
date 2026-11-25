---
id: E2E-013-010
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: STAFF
isolation_key: E2E-013-010-{timestamp}
cleanup: Retain conversation and audit history.
---

# E2E-013-010 — Staff closes an in-scope conversation

Browser MCP: Staff opens a conversation in the active location, confirms Close,
and asserts status becomes CLOSED, composer is disabled and audit evidence exists.
Repeat with a stale version and assert `409 CHAT_STATE_CONFLICT`.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-25 23:56:00
- Executed by: Ngô Tiến Đạt
- Browser tool: browsermcp
- Failure step: None
- Actual result: Staff mở hội thoại trong phạm vi chi nhánh được phân công (in-scope active location), thực hiện đóng hội thoại thành công. Trạng thái hội thoại chuyển sang CLOSED, ô nhập tin nhắn (composer) bị vô hiệu hoá và bản ghi audit CLOSE được ghi nhận đầy đủ. Gửi lại yêu cầu đóng với version cũ trả về lỗi 409 CHAT_STATE_CONFLICT chính xác theo thiết kế optimistic locking.
<!-- EXECUTION:END -->
