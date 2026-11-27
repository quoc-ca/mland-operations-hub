#### 4.5.2 Cash Close

![](assets/screens/live/63-cash-close.png)

This screen allows Staff and Managers to:

- Choose a branch and working day and read the expected cash and the cash entries of that day, as computed by the server.
- Enter the counted cash and, when it differs from the expected amount, a difference reason.
- Close the cash book and read the server's expected, counted and difference figures.
- As a Manager, confirm a difference that is waiting for confirmation, from the close result or from the list of differences awaiting confirmation.

The screen never computes a difference itself. A close with a difference stays **Awaiting Manager confirmation** until a Manager confirms it; Staff see that a Manager must confirm. Cash recorded after the close belongs to the next working day, even while the difference waits. Retail cash is not included.

**Field Description**

| Field Name | Description |
| --- | --- |
| **Differences awaiting confirmation** | Manager only, shown when the screen is opened from the navigation menu or the Manager home. Columns **Close**, **Branch**, **Day**, **Expected**, **Counted**, **Difference** and **Reason**, with **Confirm difference** per row. Empty state: "No cash-close difference is waiting for confirmation." |
| **Branch** | Branches the user may see; the first one is selected by default. Shows "No branch assigned" when there is none. |
| **Working day** | Date of the working day; optional. When empty, the server uses the current working day. Changing the branch or day clears the draft count. |
| **Reload expected cash** | Reads the expected cash again. It is also read automatically whenever the branch or day changes. |
| Expected cash | **Working day** and **Expected cash** from the server, with a note that it cannot be edited. |
| **Cash entries of the day** | Columns **Ticket** (marked **Reversal** for a reversal entry), **Recorded at** and signed **Amount**. Empty state: "No cash entry on this day." |
| **Counted cash (VND)** | Shown after the expected cash is loaded. Counted cash as a whole VND amount; 0 is allowed. |
| **Difference reason (if the count differs)** | Free text up to 1,000 characters explaining a difference. |
| **Close cash book** | Closes the cash book; enabled only for a valid counted amount. Shows "Cash book closed." on success. |
| Close result | Close reference and status (**Awaiting Manager confirmation**, **Closed** or **Confirmed**), **Expected**, **Counted**, signed **Difference** and **Reason**, plus the cut-off note. |
| **Confirm difference** | Manager only, on a close awaiting confirmation. Opens a dialog that requires a reason; the day closes with the difference on record and the action is audited. Staff see "A Manager must confirm this difference before the day is closed." instead. |
