#### 8.4.2 Carrier Orders

![](assets/screens/live/97-carrier-orders.png)

This screen allows the Carrier to:

- Browse the GHTK shipments of every Mland branch, newest first, in status tabs with their counts.
- Search by tracking number, order code, recipient name or phone.
- See where to collect, whom to deliver to and the item count of each shipment.
- Press **Picked up** after collecting a packed parcel and **Delivered** after delivering it.

Only an active account with the CARRIER role can open the page; other roles are sent to the not-found page. It is reached from the Carrier Portal.

**Field Description**

| Field Name | Description |
| --- | --- |
| Intro | "Shipments of every Mland branch. Once the shop has packed, the shipment moves to “Picking up”." |
| Status tabs | Options: Received, Picking up, Delivering, Delivered; each with its count. Default: the first of Received, Picking up and Delivering that has shipments, otherwise Picking up. |
| Search | "Tracking number, order code, recipient name or phone". |
| Received hint | On the Received tab: "The shop is still packing. No pickup needed yet." |
| Shipment card header | Tracking number, "Order {code} · {n} items" and a status pill: Received, Picking up, Delivering or Delivered. |
| Pick up at | Pickup branch name and address. |
| Deliver to | Recipient name, phone and address. No product prices, vouchers, points or Member account data are shown. |
| Times | Received, Shop packed, Picked up and Delivered times. |
| **Picked up** | Shown on a Picking up shipment; records the courier pickup once. |
| Cash on delivery (COD): 0 ₫ / **Delivered** | Shown on a Delivering shipment; COD is 0 because the customer prepaid goods and shipping. **Delivered** records the delivery once. |
| Action feedback | No confirmation dialog and no success message: the shipment moves to its next tab. Only an error is shown, such as a stale list that must be reloaded. |
| Empty state | "No shipment in this tab." or "No matching shipment." |
