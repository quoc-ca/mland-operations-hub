---
id: E2E-013-011
feature: 013-staff-consultation-chat
test_level: e2e
browser_tool: browsermcp
date_created: 2026-11-18
date_updated: 2026-11-18
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
actor_primary: STAFF
isolation_key: E2E-013-011-{timestamp}
cleanup: No business mutation; retain denied-access evidence.
---

# E2E-013-011 — Staff cannot close an out-of-scope conversation

Assert direct navigation and close command for another location return 403 or
safe 404, with no status mutation and no transcript disclosure.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-25 23:56:30
- Executed by: Ngô Tiến Đạt
- Browser tool: browsermcp
- Failure step: None
- Actual result: Staff điều hướng trực tiếp hoặc gửi yêu cầu đóng cuộc trò chuyện ngoài chi nhánh được phân công (out-of-scope). Hệ thống từ chối truy cập và trả về mã lỗi 404 CHAT_NOT_FOUND an toàn, không làm biến đổi trạng thái hội thoại và không để lộ nội dung tin nhắn.
<!-- EXECUTION:END -->
