#### 4.4.2 Session Details / Check-in

![](assets/screens/live/54-session-details-check-in.png)

This screen allows Staff and Managers to:

- Check in a participant by typed ticket code or by scanning the ticket QR code with the camera.
- Look up the tickets of a booking code in this session.
- Follow the attendance roster and its check-in progress, and open a ticket to check it in, undo or correct it.
- Start the session and finalize attendance while the session is in progress.
- Resend all tickets of a booking.
- As a Manager, open or pause sales, set the reschedule quota, assign or remove session Staff, revoke or rotate a booking's magic link, and cancel the session.

A Staff member can only start a session at their location that they are assigned to; only a Manager can open or pause sales. A session in progress cannot return to a sales state. Every refused state change is explained on the screen with its code and correlation id.

**Field Description**

| Field Name | Description |
| --- | --- |
| Header | "Workshop session on {date}, {time}", the location name and the session state badge. |
| Missing Staff warning | Manager only: "Check-in is open for this session but no Staff is assigned." |
| **Check-in desk** | Shown while the session is published, sales paused or in progress; otherwise a note explains why check-in is closed. **Scan with camera** opens the QR scanner, which shows **CHECKED IN**, **ALREADY CHECKED IN** or **NOT CHECKED IN** per scan. **Ticket code** with **Check in**. A check-in before the opening time shows "Check-in opens at {when}." |
| **Look up tickets by booking code** | Booking code with **Look up**. Lists each ticket with its attendance and validity, and **Select this ticket** fills the ticket code. |
| **Session facts** | **Capacity**, **Check-in opens** and **Assigned Staff** (number of Staff, or **Unassigned**). |
| State buttons | Manager: **Open sales**, **Pause sales** and **Start session**. Staff: **Start session** only. A button is disabled, with the reason as a tooltip, when the change is not allowed. |
| **Finalize attendance** | Enabled only while the session is **In progress**. A confirmation shows how many tickets are checked in; no check-in is possible afterwards. |
| **Attendance roster** | "{checked}/{total} checked in" with a progress bar and filter tabs (Options: **All**, **Checked in**, **Not checked in**, **No-show**. Default: **All**). Columns **Ticket code**, **Booking code**, **Status**, **Checked in at** and **Actions**. The status also shows **Paid**, **Unpaid** or **Bill closed** from the ticket bill. |
| Roster actions | **Undo** or **Correct** where the role allows it, "Correction closed" when the window has ended, and **Bill / Add-on**, which opens the Settlement Workspace for the ticket. |
| Ticket dialog | Opens on a roster row. **Confirm check-in** for a ticket not checked in. **Reverse check-in** before finalization: a Manager in any open state, a Staff member only while in progress. **Mark as no-show** or **Mark as checked in** after finalization, for both roles, until the correction window closes. Changes need **Reason** (Options: "Attendance recorded by mistake or missed", "Other reason". Default: the first) and **Reason note (required)**. A finalized bill blocks reversal and no-show. |
| **Reschedule quota** | Manager only. **Effective quota**, **Source** (Manager or default of 25% of capacity, at least 1) and **Used**. **New quota** (0 closes the session to customer changes) and **Reason** (up to 500 characters) with **Set quota**, or **Restore default**. Not changeable once finalized or cancelled. |
| **Staff assignment** | Manager only. Lists the Staff in charge with **Remove**. **Select a Staff member** lists Staff of the session's location, or a manual Staff account ID; **Assign**. Removing the last Staff after check-in opened asks for confirmation. |
| **Exception handling** | Collapsed section holding the three panels below. |
| **Ticket delivery** | Booking code with **Resend ticket**; resends all e-tickets, at most 3 times in 15 minutes per booking. |
| **Manager booking recovery** | Manager only. **Booking code to handle** (bookings on the roster), **Reason for the action** (Options: suspected link leak, customer asked for a new link, other reason. Default: suspected link leak) and **Detailed note** (required for the other reason). **Revoke magic link** or **Rotate magic link**, each after a confirmation. |
| **Cancel session** | Manager only, hidden once finalized, cancellation requested or cancelled. **Cancellation reason** (Options: instructor unavailable, facility issue, weather emergency, other. Default: instructor unavailable) and a required **Explanation note or incident reference**; **Request cancellation** asks for confirmation with the number of affected tickets. |
