---
id: E2E-013-001
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: MEMBER
actor_secondary: STAFF
isolation_key: E2E-013-001-{timestamp}
cleanup: Remove the isolated conversation through approved test cleanup; never delete audit evidence.
---

# E2E-013-001 — Member and Staff consultation

## Preconditions

- Backend and frontend are running.
- Browser MCP is connected.
- Member owns one valid Feature-012 or Feature-004 context.
- Staff account is active and assigned to the context branch.

## Browser steps

```browser
goto http://localhost:5173/login
fill getByLabel(/email/i) "e2e_member_{timestamp}@mland.test"
fill getByLabel(/password/i) "<test-secret>"
click getByRole('button', { name: /Đăng nhập|Sign in/i })
goto http://localhost:5173/consultations/new
wait getByRole('heading', { name: /Chọn thiết kế|Select design/i })
click getByRole('option', { name: /DR-|DSG-/i })
wait getByText(/Mã thiết kế|Design code/i)
fill getByLabel(/Tin nhắn|Message/i) "Xin chào, tôi cần tư vấn về mẫu nhẫn này."
click getByRole('button', { name: /Gửi|Send/i })
wait getByText(/Xin chào, tôi cần tư vấn/i)
screenshot --full --path ppswbs-spec/features/013-staff-consultation-chat/test-results/screenshots/E2E-013-001--member-sent.jpg
```

Open a second browser as Staff, open the inbox, claim the conversation and reply.
Assert that Member sees the reply after polling and that both sides display the
Vietnamese text exactly. Save screenshots after claim and reply.

## Assertions

- Context code, name, version and preview refer to the same server option.
- POST succeeds once and creates one conversation/message.
- Staff sees only the assigned branch conversation.
- Claim and reply are visible to Member after polling.
- No design approval, booking or payment state changes.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-18 17:36:50
- Executed by: Ngô Tiến Đạt
- Browser tool: browsermcp
- Failure step: None
- Actual result: Member khởi tạo thành công hội thoại gắn context thiết kế nhẫn; Manager/Staff tiếp nhận xử lý và gửi phản hồi; Member nhìn thấy tin nhắn phản hồi sau khi cập nhật; toàn bộ chuỗi ký tự tiếng Việt hiển thị chính xác.
- Evidence screenshots: 
  - screenshots/E2E-013-001--member-sent.jpg
  - screenshots/E2E-013-001--member-reply-received.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

