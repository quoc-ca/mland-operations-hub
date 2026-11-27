#### 4.5.3 Change Requests

![](assets/screens/live/64-change-requests.png)

This screen allows Staff and Managers to:

- Review the queue of ticket change requests that are blocked only by a rule that Staff or a Manager may lift.
- Open the full details of a request, including its current and requested sessions and who asked.
- Approve a pending request, which applies one counted change immediately, or reject it with a reason.

On the screen, they can also:

- Filter the queue by status and reload it.

Approval asks for confirmation only; rejection opens a dialog that requires a reason. The server still checks capacity and quota on approval. A rejected request keeps the ticket on its current schedule.

**Field Description**

| Field Name | Description |
| --- | --- |
| **Status** filter | Options: **Pending**, **Approved**, **Rejected**, **Expired**. Default: **Pending**. Kept in the page URL. |
| **Refresh** | Reloads the queue. |
| **Ticket** | Ticket code of the request. |
| **Type** | **Advance** or **Late arrival**. |
| **Current session** / **Requested session** | Date, start–end time and location of each current and requested session; "Session details unavailable" when a session cannot be read. |
| **Blocked by** | The rules that blocked the change, in words; an unknown rule shows its code. |
| **Price increase** | Price increase amount, followed by "(buyer confirmed)" when the buyer accepted it. |
| **Expires** / **Status** | Expiry time of the request and its status. |
| **Details** / **Hide details** | Column **Decision**. Expands a row with ticket, group, type, status, sessions, blocked rules, price increase, **Requested by** (name and role, or "Walk-in guest"), reason, created, expiry and decision times, and decision reason. |
| **Approve** | Column **Decision**, pending requests only. Confirmation dialog "Approve this change"; the change is applied at once. |
| **Reject** | Column **Decision**, pending requests only. Dialog "Reject this change" that requires a reason. A decided request shows its decision reason under the buttons. |
| Empty state | "No change requests found." |
