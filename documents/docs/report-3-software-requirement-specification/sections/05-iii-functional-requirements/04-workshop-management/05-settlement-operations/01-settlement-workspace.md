#### 4.5.1 Settlement Workspace

![](assets/screens/live/62-settlement-workspace.png)

This screen allows Staff and Managers to:

- Open the ticket bill (folio) of one ticket by its ticket code, or from the **Bill / Add-on** link of the attendance roster.
- Read the ticket's balance due, total, paid and pending verification amounts, with its collections and bill lines.
- Record a counter collection by VNPay (shown as a QR code the customer scans), cash or bank transfer.
- Add catalogue add-ons or a one-off variable charge that the customer agreed to, while the bill is open.
- Finalize the bill.
- As a Manager, confirm or fail pending collections, void lines and collections, and reverse cash of a closed cash day.
- As a Manager, record or close a debt, allow handover before payment, and record or void an absorbed undercharge on a finalized bill.
- As a Manager, submit an overcharge correction on a finalized bill, which opens a bank refund request.

On the screen, they can also:

- Look up a bill correction of the ticket by its correction reference.

The screen title is **Ticket bill**. The server decides access: Staff act only on tickets of their own branch and of sessions they are assigned to. All amounts and statuses come from the server. Every fail, void, reversal, debt, handover and undercharge action opens a dialog that requires a reason, which is written to the audit log.

**Field Description**

| Field Name | Description |
| --- | --- |
| **Ticket code** / **Open bill** | Ticket code input, prefilled from a link. **Open bill** is enabled once a code is typed and keeps the code in the page URL. Shows "No bill was found for this ticket." when none exists. |
| **Add-ons sold at the previous location** | Manager only, read-only, shown when the ticket moved branch and add-ons were sold at the earlier branch: description, amount and both location references. |
| Bill summary | Ticket code, bill status (**Open**, **Finalized** or **Closed**), **Balance due**, **Total**, **Paid** and **Pending verification**. |
| **Finalize bill** | Available to Staff and Managers; disabled when the bill is already finalized or closed. The server refuses while a balance is due or a part is pending verification. |
| **Record debt** / **Close debt** | Manager only. Each opens a reason dialog. |
| **Allow handover** | Manager only, shown while a balance is due. Lets the customer take the product now; the balance stays owed. |
| **Record absorbed undercharge** | Manager only, on a finalized bill. The dialog asks for **Amount (VND)** (whole VND above 0) and a reason; nothing is collected and the bill does not change. |
| **Collections** table | Hint "VNPay, cash, then bank transfer". Rows ordered booking-time payment, VNPay, cash, bank transfer, with **Method**, **Amount** (and change returned for cash), **Status** and **Actions**. Shows "No collection recorded." when empty. |
| Booking-time payment row | Marked "allocation of the booking-time payment (view only)"; it has no row action. |
| Collection row actions | Manager only. For a pending bank transfer: **Bank reference** and **Confirm transfer**. For any pending part: **Mark as failed**. For a confirmed cash entry of a closed cash day: **Reverse cash entry**, which posts a negative cash entry on the current working day. For another confirmed collection of an open bill whose cash day is not closed: **Void**. Staff see **Manager verifies** on a pending row. |
| **Bill lines** | Shown when the bill has lines: **Line** (shortened reference), **Description**, **Amount** and **Status** (**Active**, **Confirmed** or **Voided**). A Manager can **Void line** on a line that is not voided while the bill is open. |
| VNPay checkout | After a VNPay collection: **VNPay checkout created**, the expiry time, a QR code the customer scans with their phone and **Open VNPay checkout**. The screen refreshes the bill every few seconds and removes the QR once VNPay answers; the part counts only after VNPay's signed confirmation. |
| **Record a collection** | Open bill only. **Method** (options: **VNPay**, **Cash**, **Bank transfer**; default **VNPay**). **Amount (VND)** is prefilled with the balance due minus pending parts. **Cash received (VND)** appears for cash; **Customer transaction reference** (up to 128 characters) for bank transfer. The **Record collection** button needs a whole VND amount above 0. |
| VNPay minimum warning | Shown before recording when a VNPay part would be under 5,000 VND, or when the remainder left after a cash or transfer part would be under 5,000 VND. |
| **Add-ons and charges** | Open bill only. **Add-on from catalogue** lists the add-ons of the ticket's package and location as checkboxes with prices ("No add-on is available for this package at this location." when none). **Add selected add-on** needs at least one ticked add-on and **Customer agreed**. |
| **Variable charge** | **Description** (up to 200 characters), **Amount (VND)** and **Reason** (up to 255 characters); **Add charge** needs all three and **Customer agreed**. The server refuses while the session has not started. |
| **Absorbed undercharges** | Manager only, lists the records made on this screen for this ticket: **Reference**, **Amount**, **Reason**, **Status**, and **Void record** for an active record. |
| **Manager bill correction** | Manager only, on a finalized bill. **Overcharge refund amount (VND)** (whole VND above 0) and **Correction reason** (up to 1,000 characters); **Submit bill correction** opens a bank refund request. The finalized bill, Loyalty points, ticket and capacity do not change. |
| **Bill correction** | **Correction reference** and **View correction** show the correction, ticket, overcharge refunded, status, and the related bank refund request and its status. |
