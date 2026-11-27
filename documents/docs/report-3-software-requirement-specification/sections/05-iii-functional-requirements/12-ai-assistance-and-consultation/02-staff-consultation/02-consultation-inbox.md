#### 12.2.2 Consultation Inbox

![](assets/screens/live/57-consultation-inbox.png)

This screen allows Staff and Managers to:

- Browse customer consultations and open one to read its design context and message history.
- Claim an unclaimed conversation, or release a claimed one.
- Reply to the customer in the conversation.
- Close an open conversation and reopen a closed one.
- As a Manager, assign a conversation to a branch.

On the screen, they can also:

- Filter the inbox by status.
- Load older conversations with **Load more**.

Staff see only conversations of their current branch; the reply box appears for Staff only once the conversation is claimed. Managers see all branches and can reply without claiming. The server lets Staff release only a conversation they claimed themselves.

The inbox and the open conversation refresh every 3 seconds while the page is visible (every 6 seconds when hidden), and again when it regains focus.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | **Consultation Inbox**. |
| Status filter | Options: All statuses, Open, Unclaimed (open and not claimed), Closed. Default: All statuses; the Customer consultation menu opens the inbox on Unclaimed. |
| Conversation list | One row per conversation: design code (or conversation reference), status **Open** or **Closed**, design name (or "Design"), assigned branch or **Unassigned**, and **{count} unread** when there are unread messages. The first conversation opens by default. Loading: "Loading the list...". "Design" and the loading text have no English label in the app. Empty: **No conversations found.** |
| Load more | Shown when older conversations exist; loads the next page. |
| Context header | Preview image (or an icon), design name or **General consultation**, design code, version (v{n}), assigned branch or **Unassigned**, and **Handled by** (without the handler name) or **Unclaimed**. Hovering over a workshop ring design shows its material, shape and size. |
| Status badge | **Open** or **Closed**. |
| Assign Branch | Manager only. Opens a dialog **Select a branch to assign this consultation:** with **Hoan Kiem Branch (HN-HOANKIEM)** (default) and **Tay Ho Branch (HN-TAYHO)**, plus **Cancel** and **Confirm**. Success: **Branch assigned successfully.** |
| Claim / Release | **Claim** when nobody has claimed the conversation, otherwise **Release**. Success: **Conversation claimed successfully.** or **Conversation released successfully.** |
| Close Conversation / Reopen Conversation | Closes or reopens the conversation after a browser confirmation. |
| Messages | Messages in order with time, each labelled **Customer**, **Consultant Staff** or **Manager**. When empty: **No messages yet. Send the first message to start the consultation!** |
| Reply input | Placeholder **Type your message (max 2,000 characters)...** and a **{count} / 2,000 characters** counter; Enter sends, Shift+Enter adds a line. For Staff on an unclaimed conversation it is replaced by **You need to claim this conversation before sending a reply.** and a **Claim** button. A closed conversation shows **This conversation is closed. Reopen it to send more messages.** |
| Send | Sends the reply. Disabled when the input is empty, over 2,000 characters, or while sending. |
| New message notice | When the customer sends a new message in the selected conversation, a chime plays and a notice "New message from {customer}:" (no English label in the app) shows the text for 6 seconds; clicking it opens that conversation. |
