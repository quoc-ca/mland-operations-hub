#### 12.1.1 AI Support Chat

![](assets/screens/live/12-ai-support-chat.png)

This screen allows Guests and Members to:

- Ask the AI assistant free-text questions about Mland's public services, workshops and products.
- Read each AI answer in the chat history, under a notice that answers are advisory only.
- See how many questions they have left today.
- Clear the stored chat history.

On the screen, they can also:

- Retry a question after a temporary AI service error.

A Guest may ask 3 questions per day and a signed-in Member 10; the count resets at 00:00 Vietnam time. All Guests together also share a daily pool of 100 questions. Staff, Manager, Admin and Carrier accounts are refused by the server, and the floating AI chat button (shown on other pages to open this screen) is hidden for them.

The chat history is stored in the browser until the user clears it. Questions that try to override the assistant's instructions, or that ask about payments, passwords or custom manufacturing orders, are refused and still use one question.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | **AI Assistant Support**, with the subtitle **Answers public information regarding Mland services, workshops, and products.** |
| Role badge | **Guest (Public)** for a visitor, or **Mland Member** for a signed-in Member. |
| Quota badge | **{remaining}/{limit} prompts left today • Resets at 00:00**. Read from the server when the page opens and after each question. Until the server answers, it shows the last known value or 3/3 for a Guest and 10/10 for a Member. |
| Clear history | Button "Clear history" (no English label in the app), shown only when the history is not empty; removes all stored questions and answers at once, without confirmation. |
| Disclaimer | **Notice: AI responses are advisory and informational only, not binding quotes or contracts.** |
| Chat history | Questions and answers in order, each labelled with the role badge or **AI Assistant Support**. When empty: **Ask a question to start chatting with the AI Assistant.** A loading indicator shows while an answer is pending. |
| Error message | Shown above the input when a question fails, for example **AI service is temporarily unavailable. Please try again later.**, with a retry action. |
| Quota exceeded message | **You have reached your daily AI prompt limit. Quota resets at 00:00.** Shown when no questions are left. |
| Question input | Labelled **AI Assistant Support**, placeholder **Type your question (up to 2,000 characters)…**, with a live **{count}/2,000 characters** counter. Empty input shows **This field is required.**; over 2,000 characters shows **Prompt exceeds the 2,000 character limit.** Disabled while an answer is pending or the limit is reached. |
| Send question | Sends the question. Disabled while an answer is pending, when the input is empty, or when the daily limit is reached. |
