#### 11.1.1 AI Support Chat

This screen allows Guests and Members to:

- Ask the AI assistant free-text questions about Mland's public services, workshops and products on the `/ai-support` page.
- Send up to 3 questions per day as a Guest and up to 10 per day as a signed-in Member; the counter resets at 00:00 Vietnam time.
- Read each AI answer in the chat history, under a notice that answers are advisory only and are not a quote or contract.
- See how many questions they have left today; once the limit is reached, the input and **Gửi câu hỏi** are disabled until the reset.

Staff, Manager, Admin and Carrier accounts are refused by the server. The chat history is stored in the browser and stays until the user presses **Xóa lịch sử**. Basic AI Support stays in scope (use case 33, confirmed by the Feature-004 owner), although the Feature-004 spec no longer owns it.

| Field Name | Description |
| --- | --- |
| Title | "Trợ lý Tư vấn AI" with a subtitle about public Mland information. |
| Role badge | "Khách (Công khai)" for a Guest or "Hội viên Mland" for a signed-in Member. |
| Quota notice | "Còn {remaining}/{limit} lượt hỏi hôm nay • Reset lúc 00:00", updated from the server after each answer. |
| Disclaimer | States that AI answers are for reference only and are not a binding quote or contract. |
| Chat history | Questions and AI answers in order; an empty chat shows "Hãy đặt câu hỏi để bắt đầu trò chuyện cùng Trợ lý AI." |
| Xóa lịch sử | Shown when the history is not empty; clears the stored history. |
| Question input | Required text of up to 2,000 characters with a live "{count}/2.000 ký tự" counter; an empty or too-long question shows a validation message. |
| Gửi câu hỏi | Sends the question; disabled while an answer is pending, when the input is empty or when the daily limit is reached. |
| Quota exceeded message | "Bạn đã sử dụng hết số lượt hỏi AI trong ngày. Lượt hỏi sẽ được làm mới vào lúc 00:00." |
| Error message | A safe message with a retry when the AI service does not respond ("Dịch vụ AI tạm thời không phản hồi. Vui lòng thử lại sau."). |
| Refused questions | Questions that try to override the assistant's instructions, or that concern payment, passwords or custom manufacturing orders, are refused and still use one question of the daily limit. |
