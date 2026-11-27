#### 8.3.1 Retail Order Operations

![](assets/screens/live/58-retail-order-operations.png)

This screen allows Staff and Managers (some actions Staff only) to:

- Work through the branch's retail orders in tabs, from preparation to completion.
- Press **Packed and ready** on a paid order: a pickup order moves to Awaiting pickup and a GHTK order to Waiting for courier.
- Collect cash at the counter with **Confirm {amount} collected**; payment and handover are recorded together and the order moves to Completed.
- Press **Hand to customer** to record the counter handover of a ready pickup order.
- Follow a packed GHTK order without acting on it: the GHTK partner records the pickup and the delivery on the carrier portal.
- Open a card to see its details inline, including the recipient and status history of a GHTK shipment.

Two former addresses open the same page on the Orders to prepare and Awaiting cash tabs. Without a chosen tab, it opens the first tab, left to right, that has orders. The server returns data only for the actor's assigned branches. Staff and Managers can mark orders packed; collecting cash and handing over are Staff only.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | "MLAND / RETAIL ORDERS" with "Pack GHTK parcels, collect counter cash and hand over pickups for your branch." |
| Summary | "Cash collected today", "Holds ending within 24 hours" (highlighted when above zero) and "Open tasks" (orders to prepare, collect cash for or hand over). |
| Search | "Order code (ML…), tracking number or recipient name". |
| **Refresh** | Reloads every tab. |
| Tabs | Options: Orders to prepare, Waiting for courier, Handed to courier, Awaiting cash, Awaiting pickup, Completed; each tab except Completed shows its count. Awaiting cash is sorted by hold deadline, the others newest first. At most 50 GHTK shipments per shipment status are loaded. |
| Cash hint | On Awaiting cash, for Staff: "When the customer pays at the counter, press "Confirm collected". The order goes straight to Completed." |
| Pickup order card | Order code, branch, a status pill (Preparing, Awaiting cash, Awaiting pickup or Completed), product lines, and "Total" or "To collect". |
| Held until | On Awaiting cash: the hold deadline and time left, highlighted when under 24 hours. |
| GHTK shipment card | Order code, branch with "GHTK {tracking number}", a status pill, recipient name, area, item count and total. |
| **Confirm {amount} collected** | Staff only. Records payment of exactly that amount and the handover once. If it fails, nothing is recorded and Staff presses again. |
| **Packed and ready** | Marks a paid order packed, shown with "Cash collected" or "Paid by VNPay". A GHTK shipment asks for confirmation first. |
| **Hand to customer** | Staff only. Records the handover of a pickup order once; "Handed over at" then shows in its details. |
| Card details | **Click for details ▼**: Order code, Ordered at, Payment, Handed over at, amounts and the cash received and change for a pickup order; Tracking number, Phone, Address and History for a GHTK shipment. |
| Courier hint | Read-only "Waiting for GHTK to pick up." or "GHTK is delivering." on a packed GHTK order. |
| Empty state | "No matching order." or "Nothing left in this tab." |
