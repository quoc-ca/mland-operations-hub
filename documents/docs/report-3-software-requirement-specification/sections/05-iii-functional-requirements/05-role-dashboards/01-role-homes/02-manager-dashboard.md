#### 5.1.2 Manager Dashboard

![](assets/screens/15-manager-dashboard.png)

This screen allows Managers to:

- See today's workshop operations, for all branches or one branch.
- See revenue and performance for a day, week, month or year, for all their branches or one branch, with trends against the previous period of the same length.
- See the five best-selling products and the five most-booked workshop packages for the same filters.
- Compare the figures by branch.
- See up to five recent events worth a look and open the audit log.

This is the Manager home after sign-in (the old Manager Home route redirects here). Only a Manager can open it; any other role is sent to its own home. The dashboard is read-only and each block loads and fails on its own, with a retry. Revenue counts only verified transactions: unpaid or cancelled orders and sessions without finalized attendance are not counted.

| Field Name | Description |
| --- | --- |
| Operations today | Branch drop-down ("All branches" or one branch), the number of sessions today with how many are cancelled, and check-ins against expected guests (cancelled sessions excluded); "There are no sessions today." when none. |
| Period / Date / Branch filters | Day, Week, Month (default) or Year; a date picker up to today that switches to that day; branch chips, where clicking a branch shows only it and clicking it again returns to all. |
| Scope line | Period type, start and end dates and the number of branches, in Vietnam time. |
| Key figures | Retail revenue (after discounts, without shipping), Workshop revenue (fully paid bills), Total revenue, Paid retail orders (confirmed VNPay and cash) and Workshop utilization (checked-in places / capacity of finalized sessions). |
| Retail and workshop trends | Orders placed, paid, cancelled or expired and retail revenue; tickets booked, check-ins and workshop revenue; each with the change against the previous period and a chart by hour, day or month. |
| Best sellers | Top 5 products by units in paid orders and Top 5 workshop packages by confirmed tickets; each row shows rank, name, count (chiếc or vé) and a bar; hovering or focusing a row shows its revenue before discounts and its share of the five. An empty period, an unreadable list or a failed load (with Thử lại) is stated without hiding the rest of the page. |
| By branch | Table of Branch, Retail, Workshop, Total revenue (with share bar), Paid orders and Utilization. |
| Recent events worth a look | Time, actor and kind (refused access attempt or package availability change) from the last 24 hours, notes when a source is missing, and **Open the audit log**. |
| Error messages | A 403 explains that only a Manager assigned to a branch can see the dashboard; an invalid filter keeps the last valid result. |
