#### 6.2.1 Design Review Queue

![](assets/screens/live/55-design-review-queue.png)

This screen allows Staff and Managers to:

- Browse submitted design requests, filter them by review status and reload the list.
- See when each request was sent, who sent it, its idea description and its number of images.
- Open a request in Design Review Details to review it.

Only the STAFF and MANAGER roles can open the screen; other signed-in roles are sent to the not-found page. Staff and Managers have the same review authority, and the server decides the queue content and order.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title and subtitle | "Design Review Queue" with "Requests awaiting human review (Staff / Manager)." |
| Status filter | Options: All statuses, Awaiting review, Accepted, Rejected, Clarification needed. Default: All statuses. Its texts have no English label in the app. |
| Refresh | Reloads the list; shows a loading text while loading (no English label in the app). |
| Reference | Request reference, with the internal request ID below it when they differ. |
| Submitted at | Date and time the request was submitted. |
| Submitted by | The sender's email, or a "Guest" badge when there is none. |
| Idea description | The description on one line, with the full text on hover; "—" when empty. |
| Images | "{count} images", or "—". |
| Status | "Awaiting staff review", "Accepted", "Rejected" or "Clarification required". |
| **Review** | Opens Design Review Details for the request. |
| Empty state | "No requests currently awaiting human review." |
