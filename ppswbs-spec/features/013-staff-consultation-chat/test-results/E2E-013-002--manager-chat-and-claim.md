---
id: E2E-013-002
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: MANAGER
isolation_key: E2E-013-002-{timestamp}
cleanup: Close the isolated conversation and remove only disposable test context.
---

# E2E-013-002 — Manager global participation

## Preconditions

- Conversation has no branch assignment or belongs to another branch.
- Manager account is active.
- Staff account from a different branch exists.

## Browser steps

```browser
goto http://localhost:5173/login
fill getByLabel(/email/i) "e2e_manager_{timestamp}@mland.test"
fill getByLabel(/password/i) "<test-secret>"
click getByRole('button', { name: /Đăng nhập|Sign in/i })
goto http://localhost:5173/consultations
wait getByRole('heading', { name: /Hội thoại tư vấn|Consultation inbox/i })
click getByRole('link', { name: /Mở hội thoại|Open conversation/i })
click getByRole('button', { name: /Nhận xử lý|Claim/i })
click getByRole('button', { name: /Gửi|Send/i })
screenshot --full --path ppswbs-spec/features/013-staff-consultation-chat/test-results/screenshots/E2E-013-002--manager-reply.jpg
```

## Assertions

- Manager sees the global conversation.
- Manager can claim, assign and reply.
- Staff outside the assigned branch receives `403 CHAT_ACCESS_DENIED`.
- Assign operation requires `If-Match-Version` and creates audit metadata.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-18 17:36:04
- Executed by: Ngô Tiến Đạt
- Browser tool: browsermcp
- Failure step: None
- Actual result: Manager xem được toàn bộ cuộc trò chuyện chưa phân bổ chi nhánh, tiếp nhận tư vấn (claim), điều phối chi nhánh HN-HOANKIEM (assign) kèm If-Match-Version và gửi phản hồi thành công.
- Evidence screenshots: 
  - screenshots/E2E-013-002--manager-reply.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

