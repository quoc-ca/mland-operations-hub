#### 4.4.1 Workshop Session List

![](assets/screens/live/53-workshop-session-list.png)

This screen allows Staff and Managers to:

- See workshop sessions grouped in tabs by state, filtered by date range and location.
- See counts of all sessions, published sessions, sessions in progress and finalized sessions.
- Open a session to run check-in and attendance.

A Staff member sees only the sessions a Manager assigned to them, and the names of their own branches. A Manager sees all sessions. The list is read-only. The menu names it **My sessions** for Staff and **Session board** for Managers.

**Field Description**

| Field Name | Description |
| --- | --- |
| Scope note | Staff: "Only the sessions a Manager assigned to you are shown." with **Your branches:** and their branch names. Manager: "Coordinate capacity, check-in and attendance finalization across active sessions." |
| Metrics | **All sessions**, **Published**, **In progress** and **Finalized** counts of the sessions in the date range. |
| State tabs | Options: **All**, **In progress**, **Upcoming** (published or sales paused), **Ended** (attendance finalized), **Cancelled**, **Drafts**, each with its count. Default: **All**. **Drafts** appears only when a draft session exists. |
| **From** / **To** | Workshop date range. Default: **From** is today, or 7 days ago on the **Ended** tab; **To** is empty. The tab and filters are kept in the page URL. |
| **Location** | Options: **All locations** or one location the user may see. Default: **All locations**. |
| **Clear filters** | Clears the location and both dates and returns to the **All** tab. |
| **Session queue** | Columns **Date**, **Time**, **Session** (slot label, or a short session id), **Location**, **Capacity** and **State**, with the record count. In-progress sessions come first, then upcoming, then ended. 15 sessions per page. |
| **Open** | Opens Session Details / Check-in. |
| Empty state | Staff: "You have not been assigned any session (or none matches the filters). Contact a Manager to be assigned." Manager: "No sessions match the selected filters." |
