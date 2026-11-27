#### 4.3.1 Customer Folio

![](assets/screens/live/25-customer-folio.png)

This screen allows the buyer of a workshop ticket (Member or Guest) to:

- Open the bill of one ticket from its ticket code.
- As a signed-in Member, see the bill of a ticket that belongs to their account directly.
- As a Guest, prove ownership by entering exactly one contact of the booking (email or phone) before the bill is shown.
- See the ticket total, the amount paid, the amount awaiting verification and the balance due.
- See the additional charges and each collection's method, amount and status.
- As a Member, look up a bill correction of the ticket by its correction reference.

The screen is read-only. A wrong ticket code and a wrong contact give the same "not found" answer. The contact is cleared from the form as soon as the answer arrives and is never placed in the address. Any signed-in account skips the contact form; the server shows only that account's own tickets.

**Field Description**

| Field Name | Description |
| --- | --- |
| Page header | Title **Ticket payment**. A Guest also sees the instruction to enter the booking email or phone. A Member sees **← Back to my tickets**, which opens Booking Lookup. |
| Email / Phone | Guest only. One of the two must be filled in a valid format (**Use one of the two, in a valid format.**); the phone has a country code selector. |
| Show my ticket | Guest only. Enabled once one valid contact is entered; sends the lookup. |
| Ticket code and status | Header of the bill with its status: **Open**, **Finalized** or **Closed**. |
| Total / Paid / Awaiting verification / Balance due | Total amount, confirmed paid amount, amount still awaiting verification (only when the server returns it) and balance due. A closed bill shows **Nothing to collect** as the balance. |
| Voucher notice | Shown only when a voucher used on the ticket expired and was not restored: **The voucher had expired, so it was not restored. Your refund is not affected.** |
| Additional charges | **Charge** and **Amount** for each charge that is not voided. Hidden when there is none. |
| Collections table | **Method** (**VNPay**, **Cash**, **Bank transfer** or **Booking-time payment**), **Amount** and **Status** (**Confirmed**, **Pending verification**, **Voided**, **Failed**, **Excess** or **Reversed**). Hidden when there is no collection. |
| Bill correction | Member only. **Correction reference** and **View correction** show the reference, ticket, **Overcharge refunded**, status and, when present, the bank refund request and its status. An unknown reference shows **No correction with this reference is visible for this ticket.** |
| Not found messages | Member: **We could not find this ticket for your account.** Guest: **We could not find a ticket with this code and contact.** |
