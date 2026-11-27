#### 14.1.2 Manager Dashboard

![](assets/screens/live/66-manager-dashboard.png)

This screen allows Managers to:

- See today's workshop operations, for all branches or one branch.
- See revenue and performance for a day, week, month or year, for all their branches or one branch, with trends against the previous period of the same length.
- See the five best-selling products and the five most-booked workshop packages for the same filters.
- Compare the figures by branch.
- See up to five recent events worth a look and open the audit log.

This is the Manager home after sign-in, titled **Dashboard**; the old Manager Home address leads here. Only a Manager can open it; other signed-in roles are routed to the not-found page, and a Manager without an active branch assignment is refused. The dashboard is read-only. Each block loads and fails on its own, with a retry. Revenue counts only verified transactions: unpaid or cancelled orders and sessions without finalized attendance are not counted.

**Field Description**

| Field Name | Description |
| --- | --- |
| Operations today | Branch drop-down (Options: All branches or one branch; Default: All branches), "Sessions today" with "of which {count} cancelled", and "Check-in (cancelled sessions excluded)" as check-ins / expected guests. "There are no sessions today." when none. |
| Updated at | Under the "Revenue and performance" heading: date and time the figures were produced, in Vietnam time. |
| Period | Options: Day, Week, Month, Year. Default: Month. |
| Date | Date picker up to today, empty by default; choosing a date switches the period to Day. |
| Branch | Chips: All (default) and one chip per branch; clicking a branch shows only it, and clicking it again returns to All. |
| Scope line | Period type, start and end dates and the number of branches, in Vietnam time. |
| Data notices | "Figures are incomplete." lists the unreadable or excluded sources (items marked "—" are not zero); "No verified transactions in this period." for an empty period. |
| Key figures | Retail revenue (after discounts, without shipping), Workshop revenue (fully paid bills), Total revenue (retail + workshop), Paid retail orders (confirmed VNPay and cash) and Workshop utilization ("{checkedIn} / {capacity} places checked in"). |
| Retail trends | Orders placed, Orders paid, Cancelled / expired and Retail revenue, each against the previous period, with charts by hour, day or month. |
| Workshop trends | Tickets booked, Check-ins, No-show rate and Workshop revenue, each against the previous period, with charts and a footnote on how places and revenue are counted. Missing charts are listed. |
| Best sellers | "Top 5 products" by units in paid orders and "Top 5 workshop packages" by confirmed tickets. Each row shows rank, name, count ("sold" or "tickets") and a bar; hovering or focusing shows its revenue before discounts and share of the top 5. Empty, unavailable or failed lists are stated with **Try again**. |
| By branch | Table of Branch, Retail, Workshop, Total revenue (with share bar), Paid orders and Utilization, with the branch count. |
| Recent events worth a look | Time, actor and kind ("Refused access attempt" or "Package availability change") from the last 24 hours, notes when a source is missing, and **Open the audit log**. "Nothing new in the last 24 hours." when empty. |
| Error messages | A 403 explains that only a Manager assigned to a branch can see the dashboard; an invalid filter keeps the last valid result. |
