#### 14.1.1 Staff Home

![](assets/screens/live/46-staff-home.png)

This screen allows Staff to:

- See a greeting and today's date (Vietnam time).
- Switch between two large cards, Workshop and Orders; each shows how much work is waiting, and the card not selected shows a red alert with its waiting count.
- On the Workshop side, see the session tasks that need them, most urgent first, and open a session to check guests in or finalize attendance.
- On the Orders side, work directly in the branch retail order list.
- Open the final bills page.

This is the Staff home after sign-in, titled **Home** in the operations shell. Only the STAFF role can open it; other signed-in roles are routed to the not-found page. The backend already limits sessions and orders to the Staff member's assigned sessions and branch. A count that cannot be read shows "—".

**Field Description**

| Field Name | Description |
| --- | --- |
| Greeting | "Hello, {name}" with the weekday and date. |
| Workshop card | "Workshop" with the hint "Sessions, check-in, final bills" and "{count} tasks today". When the card is not selected, it also shows "{count} workshop tasks" in red and the next task. Selected by default. |
| Orders card | "Orders" with the hint "Branch retail, GHTK" and "{count} orders waiting": open pickups and shipments being prepared. When not selected, it shows "{count} orders to handle" in red. |
| Workshop work today | Workshop side only. Tasks of sessions from yesterday and today in Published, Sales paused or In progress state: attendance not finalized after the end, in progress, check-in open, or coming up today. Each row shows the session time, branch, a detail line and an action (**Finalize**, **Check guests in** or **Open session**); clicking it opens the session's details. |
| Open my sessions → | Opens Workshop Session List. |
| Empty / error state | "No session needs you today." when nothing is waiting; a load error offers a retry. |
| Final bills | Link under the task list that opens the final bills page. |
| Branch retail orders | Orders side only: the Staff branch retail order panel (see the retail order screens). |
