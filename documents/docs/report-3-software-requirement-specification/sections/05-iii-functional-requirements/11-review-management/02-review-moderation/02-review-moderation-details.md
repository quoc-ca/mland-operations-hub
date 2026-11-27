#### 11.2.2 Review Moderation Details

![](assets/screens/live/60-review-moderation-details.png)

This screen allows Staff and Managers to:

- Read one review with its rating, text, status, target and creation time.
- As Staff, hide a review that is not hidden; Staff cannot restore, remove or reply.
- As a Manager, hide a review, restore a hidden review to public, or remove a review.
- As a Manager, write the one current reply of the review or edit it.
- Give a reason with each visibility change; the change is recorded with the actor, role, reason, time and previous and new status.

Removal keeps the review and its history but takes it out of public view for good. Only Staff and Managers can open the screen; other signed-in roles are routed to the not-found page.

**Field Description**

| Field Name | Description |
| --- | --- |
| Moderation queue | Returns to the Review Moderation Queue. |
| Header | Title "Review management", then the masked name and status. |
| Review summary | Star rating, status badge, review text, target type with its reference, and creation time. |
| Reason | Reason for the visibility change, at most 500 characters, with a counter and the placeholder "Enter a moderation reason (up to 500 characters)…". The server requires a reason. |
| Hide review | Sets the review to HIDDEN; shown whenever the status is not HIDDEN, including REMOVED. A Staff member viewing a hidden review sees "This review is HIDDEN. Only a Manager can restore or remove it." |
| Restore review | Manager only; shown for a hidden review and sets it back to PUBLIC. |
| Remove review | Manager only; sets the review to REMOVED; not shown for a removed review. |
| Manager reply | Shows the current reply ("Official Response from Mland Management (v{version})") with its time, if any. |
| Reply / Save reply | Manager only; required reply text up to 2,000 characters that creates the reply or replaces the current one. Staff see "Only a Manager can send an official reply to the customer." |
| Messages | "Review visibility updated." or "Reply saved." on success; a conflict or not-permitted message when the review changed or the action is not allowed. |
