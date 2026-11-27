#### 4.5.5 Settlement Cases

![](assets/screens/live/81-settlement-cases.png)

This screen allows Managers to:

- List the settlement and compensation cases that the server still serves.

On the screen, they can also:

- Filter the cases by type and by status.
- Reload the list.
- Follow the links to the Refund Queue for refunds and to Change Requests for ticket changes.

The list is read-only and shows only safe data; payment and provider evidence stays on the server. Per-ticket work is done on the Settlement Workspace, Refund Queue, Ticket Actions and Change Requests screens. The server refuses the list to any role other than Manager.

**Field Description**

| Field Name | Description |
| --- | --- |
| **All settlement cases** / **Refresh** | Panel heading; **Refresh** reloads the case list. |
| **Type** filter | Options: **All**, **Customer cancellation**, **Required session change**, **Mland remedy**. Default: **All**. Kept in the page URL. |
| **Status** filter | Options: **All**, **Open**, **Pending provider**, **Resolved**, **Rejected**, **Failed**. Default: **All**. Kept in the page URL. |
| **Case** | Case reference. |
| **Type** | Case type label. |
| **Status** | Case status label. |
| **Booking** | Booking code of the case. |
| Empty state | "No settlement cases found." |
| Queue links | **refund queue** opens Refund Queue; **change request queue** opens Change Requests. |
