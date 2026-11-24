#### 3.1.1 Staff Home

This screen allows Staff to:

- See a greeting and today's date (Vietnam time).
- Switch between two large cards, Workshop and Orders; each shows how much work is waiting, and the card not selected shows the waiting count in red.
- On the Workshop side, see today's session tasks, most urgent first, and open a session to check guests in or finalize attendance.
- On the Orders side, work directly in the branch retail order list.
- Open the final bills and the OTA shop add-ons pages.

This is the Staff home after sign-in. Only Staff can open it: a Manager is sent to Manager Dashboard and other roles to Home Page. The backend already limits sessions and orders to the Staff member's assigned sessions and branch. A count that cannot be read shows "—".

| Field Name | Description |
| --- | --- |
| Greeting | "Xin chào, {name}" with the weekday and date. |
| Workshop card | "Workshop – Buổi học, check-in, quyết toán" with the number of session tasks today and, when not selected, the next task. |
| Orders card | "Đơn hàng – Bán lẻ tại chi nhánh, GHTK" with the number of open branch retail orders (pickups and shipments being prepared). |
| Workshop work today | Session tasks from yesterday and today in Published, Sales paused or In progress state: attendance not finalized after the end, in progress, check-in open, or coming up today. Each row shows the time, branch and an action (Chốt điểm danh, Check-in khách, Xem buổi) and opens the session's details. |
| Open my sessions → | Opens Workshop Session List. |
| Empty / error state | "Hôm nay bạn không còn buổi nào cần xử lý." when nothing is waiting; a load error offers a retry. |
| Secondary links | **Hóa đơn quyết toán** (final bills) and **Add-on tại xưởng (OTA)**. |
| Branch retail orders | On the Orders side, the Staff branch retail order panel (see the retail order screens). |
