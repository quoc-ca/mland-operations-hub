#### 8.4.3 Carrier Statistics

![](assets/screens/live/98-carrier-statistics.png)

This screen allows the Carrier to:

- Choose a period type and a date; the period is calculated in Vietnam time.
- View shipments received, picked up and delivered, the average time from pickup to delivery and the shipping fees of delivered shipments.
- View a trend chart over the period and a table per pickup branch.

Only an active account with the CARRIER role can open the page; other roles are sent to the not-found page.

**Field Description**

| Field Name | Description |
| --- | --- |
| Intro | "Shipments received, picked up and delivered, and shipping fees per period, Vietnam time." |
| Period type | Options: Day, Week, Month, Year. Default: Month. |
| Date | A date no later than today. Default: today. Picking a date switches the period type to Day. |
| Shipments received | Shipments created in the period ("Created in the period"). |
| Picked up | Shipments picked up in the period, with "Waiting for pickup: {n}", the count waiting now whatever the period. |
| Delivered | Shipments delivered in the period, with "Delivering: {n}", the count on the way now whatever the period. |
| Average delivery time | Average time from pickup to delivery. |
| Shipping fees | Shipping fees of the shipments delivered in the period. |
| Trend chart | "Trend by {unit}": Received, Picked up and Delivered per hour, day or month of the period. |
| By pickup branch | Per pickup branch: Branch, Received, Delivered and Shipping fees; "No shipment in this period." when empty. No failed delivery or return figures. |
