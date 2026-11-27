#### 4.6.3 Locations and Slots

![](assets/screens/live/77-locations-and-slots.png)

This screen allows Managers to:

- Create workshop locations and edit their name, address and active status.
- Create reusable, non-overlapping time slots for an active location, edit them, and activate or deactivate them.
- Create a session for a location, slot and date; new sessions start as **Draft**.
- Generate future sessions in bulk for several slots and weekdays over a date range, skipping sessions that already exist.
- Review sessions by date range, state and location, edit a session's capacity and check-in opening time, and move it to another state.

The page has three tabs: **Locations**, **Slots** and **Sessions**. Default: **Locations**. Slots and sessions are planned only at active locations.

**Field Description**

| Field Name | Description |
| --- | --- |
| **Locations** tab: create | **Code**, **Name**, **Address** and **Active** (ticked by default) with **Create location**; code and name are required. |
| **Location queue** | Columns **Code**, **Name**, **Address**, **Status** (**Active** or **Inactive**) and **Edit**. **Edit** opens **Name**, **Address** and **Active** with **Save location**. Empty state: "No workshop locations found." |
| **Slots** tab: location | Location selector; default: the first active location. |
| **Slots** tab: create | **Label**, **Starts at** (default 09:00), **Ends at** (default 12:00) and **Active** with **Create slot**; the start must be before the end on the same day. |
| **Reusable slots** | Columns **Label**, **Time**, **Status** and **Action**, with **Edit** (label and times, **Save slot**) and **Activate** or **Deactivate**. |
| **Sessions** tab: create | **Location**, **Slot**, **Workshop date** and **Capacity** (default 10, at least 1), with **New session**. |
| **Generate sessions** | **Location**, **From** / **To** dates, **Capacity per session** (default 10), **Initial state** (Options: **Draft**, **Published**. Default: **Draft**), **Slots** and **Weekdays** checkboxes. **Generate sessions** reports "{created} sessions created; {skipped} skipped as duplicates." |
| Session filters | **From**, **To**, **State** (Options: **All states** and each session state. Default: **All states**) and **Location** (Default: **All locations**), with **Clear filters**. |
| **Session queue** | Columns **Date**, **Time**, **Session** (id), **Location**, **Capacity**, **Status**, **Transition** and **Edit**. **Transition** offers **Published** and **Sales paused** with **Apply**. A session cancellation is requested from Session Details / Check-in, not here. |
| Session edit | **Capacity** and **Check-in opens at** (default 60 minutes before the start) with **Save session** and **Close**. A capacity below the committed tickets is refused. |
