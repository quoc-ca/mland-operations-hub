---
id: E2E-013-003
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: MEMBER
isolation_key: E2E-013-003-{timestamp}
cleanup: No cleanup may delete another Member's context; remove only isolated conversation.
---

# E2E-013-003 — Context preview and ownership integrity

## Browser steps

```browser
goto http://localhost:5173/consultations/new
wait getByRole('combobox', { name: /Mẫu thiết kế|Design context/i })
click getByRole('option', { name: /DSG-E2E-OWNED/i })
wait getByText(/DSG-E2E-OWNED/i)
screenshot --full --path ppswbs-spec/features/013-staff-consultation-chat/test-results/screenshots/E2E-013-003--owned-context-preview.jpg
```

Use the API/browser network evidence to submit a foreign reference, nonexistent
reference and stale version. Do not use a real customer's reference.

## Assertions

- The selector shows code, name, version and preview from one response item.
- Foreign context returns `403` or safe `404` and creates no conversation.
- Nonexistent context returns `CHAT_CONTEXT_INVALID`.
- Stale version returns `409 CHAT_CONTEXT_STALE`.
- No foreign design preview, name, owner or database ID is exposed.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-18 17:37:15
- Executed by: Ngô Tiến Đạt
- Browser tool: browsermcp
- Failure step: None
- Actual result: Context selector hiển thị chính xác mã hiển thị, tên, phiên bản và tóm tắt thiết kế thuộc quyền sở hữu của Member. Kiểm thử ngoại lai (foreign context, invalid context, stale version) từ chối an toàn với 403/404/409, không làm lộ dữ liệu thiết kế của người dùng khác.
- Evidence screenshots: 
  - screenshots/E2E-013-003--owned-context-preview.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

