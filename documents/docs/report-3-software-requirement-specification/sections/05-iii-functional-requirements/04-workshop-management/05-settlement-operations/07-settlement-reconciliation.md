#### 4.5.7 Settlement Reconciliation

![](assets/screens/live/83-settlement-reconciliation.png)

This screen allows Managers to:

- List the bank transfer, VNPay and booking-time payment collections of one branch and working day.
- Narrow the list by payment method and by collection state.
- Compare the rows with the bank statement and VNPay Merchant Admin outside the system.
- See the total of confirmed collections for the chosen filter.

The screen title is **Reconciliation list**. The list is read-only: it records no "reconciled" mark and changes nothing. Only confirmed rows count in the total. A non-Manager sees "Only a Manager can open the reconciliation list." The filters are kept in the page URL.

**Field Description**

| Field Name | Description |
| --- | --- |
| **Branch ID** / **Working day** | Required numeric branch identifier and working day; both empty by default. |
| **Method** | Options: **All**, **Bank transfer**, **VNPay**, **Booking-time payment**. Default: **All**. |
| **State** | Options: **All**, **Confirmed**, **Pending verification**, **Voided**, **Excess**. Default: **All**. |
| **Show collections** | Loads the list; enabled only when branch and working day are set. |
| **Confirmed total** | Sum of the confirmed rows shown, with a note that unverified, late, voided and excess rows are listed but not counted. |
| Multi-branch warning | A booking-time payment with sessions at several branches appears once under each branch; branch totals must not be added together. |
| **Ticket** / **Group** | Ticket code or codes and booking group code of the row. |
| **Method** column | Method; a booking-time payment is marked "whole group payment" and lists its branches when it covers sessions at several branches. |
| **Amount** | Amount, with "excess {amount}" and the related refund reference when there is an excess. |
| **State** column | Collection state; an unverified booking-time payment is marked "unverified", a late payment "late payment under review". |
| **Reference** / **Confirmed by** / **Time** / **Note** | Transaction reference, the confirming user ("System (IPN)" for a booking-time payment), confirmation or creation time and note. |
| Empty state | "No transfer, VNPay or booking-time collection for these filters." |
| Unavailable message | When booking-time payments cannot be read, no partial list is shown; the Manager can retry or filter out booking-time payments. |
