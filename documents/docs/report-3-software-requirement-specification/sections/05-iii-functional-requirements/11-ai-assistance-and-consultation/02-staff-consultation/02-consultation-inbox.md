#### 11.2.2 Consultation Inbox

This screen allows Staff and Managers to:

- Browse customer consultations by status and open one to read its design context and message history.
- Claim an unclaimed conversation, or release a claimed one.
- Reply to the customer in the conversation.
- Close an open conversation and reopen a closed one.
- As a Manager, assign a conversation to a branch.

Staff see only conversations of their current active branch and the screen asks them to claim an unclaimed conversation before replying (the server does not require the claim); Managers see all branches and may reply without claiming. Both Staff and Managers may close or reopen a conversation within their authority. The server enforces role and branch scope. The inbox is paginated, and its loading, empty and loaded states reserve the same width. The inbox and the open conversation refresh every 3 seconds while the page is visible and every 6 seconds while the tab is hidden; returning to the tab or window focus triggers an immediate refresh without forcing the viewport or message scroll position to the bottom. When a newly observed last message is from a Member, the Staff screen shows an in-app toast and attempts a short Web Audio notification chime.

| Field Name | Description |
| --- | --- |
| Status filter | "Tất cả trạng thái", "Đang mở" or "Đã đóng". |
| Conversation list | Each conversation's design name or "Tư vấn chung", status, assigned branch or "Chưa phân bổ chi nhánh", and unread count; **Tải thêm** loads older ones. |
| Context header | Preview image, design name, design code, version, branch, and "Người tiếp nhận" or "Chưa có người tiếp nhận". |
| Tiếp nhận tư vấn / Bỏ tiếp nhận | Claims an unclaimed conversation or releases a claimed one, with a success message. |
| Điều phối chi nhánh | Manager only. Opens a dialog to choose the branch (Hoàn Kiếm or Tây Hồ) and confirm. |
| Đóng / Mở lại cuộc trò chuyện | Closes or reopens the conversation after a confirmation. |
| Messages | Messages in order, each labelled "Khách hàng", "Nhân viên tư vấn" or "Quản lý". |
| Reply input | Up to 2,000 characters with a counter and **Gửi**. For Staff, while nobody has claimed the conversation, it is replaced by "Bạn cần tiếp nhận cuộc trò chuyện này trước khi gửi tin nhắn tư vấn." and a claim button; a closed conversation shows the closed notice instead. |
