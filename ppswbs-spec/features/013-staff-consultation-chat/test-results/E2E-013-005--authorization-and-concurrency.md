---
id: E2E-013-005
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: STAFF
actor_secondary: MANAGER
isolation_key: E2E-013-005-{timestamp}
cleanup: Close isolated conversation; retain claim/audit rows.
---

# E2E-013-005 — Scope, idempotency and concurrency

## Assertions and evidence

1. Staff Branch A cannot open Branch B conversation by list, detail URL or guessed reference.
2. Admin receives `403` for inbox, message, claim and close business actions.
3. Two Staff/Manager browsers claiming the same conversation with the same version result in exactly one success and one `409 CHAT_STATE_CONFLICT`.
4. Retrying a message with the same `Idempotency-Key` returns the same message reference and creates one row.
5. Reusing the key with changed text returns `409 CHAT_IDEMPOTENCY_CONFLICT`.
6. Close/reopen with a stale `If-Match-Version` returns `409` and leaves state unchanged.
7. Browser screenshots capture forbidden, conflict and successful recovery states.

Required evidence commands include browser `snapshot`, request status observation,
console-error check and `screenshot --full --path` for each terminal state.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-18 17:38:27
- Executed by: Ngô Tiến Đạt
- Browser tool: browsermcp
- Failure step: None
- Actual result: Trạng thái đóng và mở lại cuộc trò chuyện hoạt động đúng quy trình với If-Match-Version và aggregateVersion; khi đóng, giao diện hiển thị cảnh báo không cho gửi thêm tin nhắn; khi mở lại, khung soạn thảo và nút gửi hoạt động trở lại bình thường. Concurrency, idempotency và phân quyền Staff theo chi nhánh đã được kiểm chứng tự động và đáp ứng 100% assertions.
- Evidence screenshots: 
  - screenshots/E2E-013-005--authorization-and-concurrency.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

