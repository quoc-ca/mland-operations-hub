#### 11.2.1 Consultation Chat

This screen allows Members to:

- Start a text consultation about one of their workshop ring designs or design requests, or a general consultation, with an optional first message.
- Open a consultation directly from a saved design; the design is attached automatically, or the existing conversation for it is opened.
- Read the message history and send messages of up to 2,000 characters.
- Close an open conversation and reopen a closed one.
- Remove a closed conversation from their own list; the store keeps the history.

A Member sees only their own conversations. The list and the open conversation refresh every 3 seconds while the page is visible, and again when it regains focus. More than 30 messages in one minute are refused, and the input pauses for the wait time. Guests may see the entry point but must sign in before opening or sending a consultation; no Guest consultation session is supported.

| Field Name | Description |
| --- | --- |
| Conversation list | Each conversation's design name or "Tư vấn chung", status ("Đang mở" / "Đã đóng"), assigned branch or "Chưa phân bổ chi nhánh", and "{count} tin chưa đọc"; **Tải thêm** loads older ones. |
| Tạo cuộc tư vấn mới | Opens the context picker: **Tư vấn chung** or one of the Member's designs ("Thiết kế nhẫn Workshop" / "Yêu cầu thiết kế tự do", with code and version), plus an optional first message. |
| Context header | Preview image, design name, design code, version and branch of the open conversation. |
| Messages | Messages in order, each labelled "Khách hàng", "Nhân viên tư vấn" or "Quản lý"; an empty conversation invites the first message. |
| Waiting notice | While the last message is the Member's, shows "Vui lòng đợi, sẽ có nhân viên phản hồi trong thời gian sớm nhất có thể." |
| Message input | Up to 2,000 characters with a "{count} / 2.000 ký tự" counter; **Gửi** is disabled when the input is empty, too long or rate-limited. |
| Đóng / Mở lại cuộc trò chuyện | Closes or reopens the conversation after a confirmation; a closed conversation shows "Cuộc trò chuyện này đã đóng. Hãy mở lại để tiếp tục nhắn tin." instead of the input. |
| Xóa khỏi danh sách của tôi | Shown for a closed conversation; after a confirmation it hides the conversation from the Member's list only. |
| Rate-limit message | "Bạn đã gửi tin nhắn quá nhanh. Vui lòng chờ {seconds}s trước khi gửi tiếp." |
