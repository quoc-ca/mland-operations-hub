#### 4.5.8 Absorbed Undercharges

![](assets/screens/live/84-absorbed-undercharges.png)

This screen allows Managers to:

- List the undercharges Mland absorbed on finalized ticket bills for one branch and period.
- See the server's total of active records.
- Void an active record once, with a reason, when it was recorded by mistake.

Each record is documentation only: nothing is collected and no bill changes. New records are made on the ticket bill in Settlement Workspace, not here. A voided record stays in the list, is marked and no longer counts in the total. A non-Manager sees "Only a Manager can open the absorbed-undercharge list."

**Field Description**

| Field Name | Description |
| --- | --- |
| **Branch ID** | Required numeric branch identifier; empty by default. |
| **From** / **To** | Required period; empty by default. The filters are kept in the page URL. |
| **Show records** | Loads the records; enabled only when branch and both dates are set. |
| **Total of ACTIVE records** | Server total of active records, in the **Records** panel heading. |
| **Reference** / **Ticket** | Undercharge reference and ticket code. |
| **Amount** / **Reason** | Absorbed amount and the reason given when it was recorded. |
| **Status** | **Active** or **Voided**; a voided record shows "not in the total" and its void reason. |
| **Recorded** | Who recorded it and when. |
| **Void record** | Active records only. Opens a dialog that requires a reason; the record keeps its content and can be voided only once. |
| Empty state | "No absorbed-undercharge record for this branch and period." |
