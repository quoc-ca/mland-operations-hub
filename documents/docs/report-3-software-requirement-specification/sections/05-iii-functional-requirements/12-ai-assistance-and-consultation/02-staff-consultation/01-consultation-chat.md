#### 12.2.1 Consultation Chat

![](assets/screens/live/43-consultation-chat.png)

This screen allows Members to:

- Start a general consultation, or one about their workshop ring design or design request, with an optional first message.
- Open a consultation directly from a saved design; the design is attached automatically, or its existing conversation opens.
- Read the message history of a conversation and send messages of up to 2,000 characters.
- Close an open conversation and reopen a closed one.
- Remove a closed conversation from their own list; the store keeps its history.

On the screen, they can also:

- Load older conversations with **Load more**.
- Hover over an attached workshop ring design to see its material, ring shape and size.

Only signed-in Members can open the screen; a signed-out visitor is asked to sign in. A Member sees only their own conversations, and removed conversations are not listed.

The list and the open conversation refresh every 3 seconds while the page is visible (every 6 seconds when hidden), and again when it regains focus. More than 30 messages per minute are refused, and the input pauses for the wait time.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | **Design Consultation**. |
| + New Consultation | Opens the **Select Design Context** dialog. |
| Conversation list | One row per conversation: design code (or conversation reference), status **Open** or **Closed**, design name (or **Design**), **Assigned Branch: {branch}** or **Unassigned**, and **{count} unread** when there are unread messages. The first conversation opens by default. Loading: **Loading conversations...**; empty: **No conversations found.** |
| Load more | Shown when older conversations exist; loads the next page. |
| Context header | Preview image (or an icon), design name or **General consultation**, design code, version (v{n}) and the assigned branch or **Unassigned**. |
| Attached design card | Shown on hover over the preview of a workshop ring design: code, name, version, material, ring shape and size (no English label in the app). |
| Status badge | **Open** or **Closed**. |
| Close Conversation / Reopen Conversation | Closes an open conversation or reopens a closed one after a browser confirmation (**Are you sure you want to close this consultation?** / **Are you sure you want to reopen this consultation?**). |
| Remove from my list | Shown for a closed conversation. After the confirmation **This conversation will only be hidden from your list. The store will retain the history. Continue?** it hides the conversation from the Member's list. |
| Messages | Messages in order with time, each labelled **Customer**, **Consultant Staff** or **Manager**. When empty: **No messages yet. Send the first message to start the consultation!** |
| Waiting notice | Shown while the last message is the Member's: **Please wait, our staff will reply to you as soon as possible.** |
| Message input | Placeholder **Type your message (max 2,000 characters)...** and a **{count} / 2,000 characters** counter; Enter sends, Shift+Enter adds a line. Disabled while sending or rate-limited. For a closed conversation it is replaced by **This conversation is closed. Reopen it to send more messages.** |
| Send | Sends the message. Disabled when the input is empty, over 2,000 characters, sending or rate-limited. |
| Rate-limit message | **Rate limit reached. Please wait {seconds}s before sending again.** |
| Select Design Context dialog | Prompt **Please select a custom ring design or design request for consultation:**, a **General consultation** card, then the Member's designs, each with code, **Workshop Ring Design** or **Independent Design Request** (v{n}), name and summary. The first design is preselected. With no designs: **You have no active designs or requests available for consultation.** |
| Initial message (optional): | Optional first message with the same 2,000-character limit and counter. |
| Cancel / Confirm | **Cancel** closes the dialog. **Confirm** creates the conversation and opens it; disabled while loading, and when there are no designs and General consultation is not chosen. |
