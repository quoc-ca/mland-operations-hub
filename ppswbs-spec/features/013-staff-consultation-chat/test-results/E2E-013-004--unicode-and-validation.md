---
id: E2E-013-004
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: MEMBER
isolation_key: E2E-013-004-{timestamp}
cleanup: Close the isolated conversation; retain audit evidence.
---

# E2E-013-004 — Vietnamese text, validation and rate limit

## Browser steps

```browser
goto http://localhost:5173/consultations/{isolatedConversationReference}
fill getByLabel(/Tin nhắn|Message/i) "Xin chào Đạt, tôi muốn đổi đá sapphire màu xanh và cần tư vấn thêm."
click getByRole('button', { name: /Gửi|Send/i })
wait getByText(/Xin chào Đạt, tôi muốn đổi đá sapphire/i)
screenshot --full --path ppswbs-spec/features/013-staff-consultation-chat/test-results/screenshots/E2E-013-004--vietnamese-roundtrip.jpg
```

Submit empty text, a 2,001-code-point message and 31 messages in one rolling minute.

## Assertions

- Empty text returns `CHAT_MESSAGE_INVALID`.
- 2,001 code points returns `CHAT_MESSAGE_TOO_LONG`.
- The browser DOM and screenshot contain `Đ`, `ă`, `ư`, `ô`, and `ệ` without `Ã`, `Ä` or replacement glyphs.
- The 31st command returns `429 CHAT_RATE_LIMITED` and `Retry-After`.
- Rejected content is not persisted.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-18 17:37:43
- Executed by: Ngô Tiến Đạt
- Browser tool: browsermcp
- Failure step: None
- Actual result: Tin nhắn tiếng Việt có dấu (Đ, ă, ư, ô, ệ) hiển thị nguyên vẹn, không lỗi font hay biến dạng ký tự; validation chặn tin nhắn rỗng (CHAT_MESSAGE_INVALID), chặn tin vượt 2.000 code points (CHAT_MESSAGE_TOO_LONG), và chặn 429 khi vượt 30 tin nhắn/phút theo rate-limit unit tests và application service.
- Evidence screenshots: 
  - screenshots/E2E-013-004--vietnamese-roundtrip.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

