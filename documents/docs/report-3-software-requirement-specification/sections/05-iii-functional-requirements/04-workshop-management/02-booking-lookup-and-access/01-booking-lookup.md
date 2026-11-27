#### 4.2.1 Booking Lookup

![](assets/screens/live/23-booking-lookup.png)

![](assets/screens/live/23-booking-lookup-member.png)

![](assets/screens/live/23-booking-lookup-member-detail.png)

This screen allows Members and Guests to:

- As a Member, see the list of their own booking groups and open one.
- As a Guest, find a booking group by its booking code and the email or phone number used for the booking.
- View the group's status, tickets, sessions and payment information, and each ticket's check-in QR code.
- Pay or release the hold of a group that is still waiting for payment.
- Resend the confirmation email, send a ticket to a companion, cancel a ticket or change a ticket's schedule.
- Open a ticket's bill and, as a Member, write a review after attending.

A Guest first verifies a 6-digit code sent to the booking email before sending, cancelling or changing a ticket. A signed-in account without the Member role cannot look up bookings.

**Field Description**

| Field Name | Description |
| --- | --- |
| Page header | Eyebrow **LOOKUP & MANAGE BOOKINGS** and title **Look up a Workshop booking group**. |
| Lookup identity | **Looking up booking groups of the signed-in Member account.** or **Guest lookup with the booking group code and one contact detail.** |
| My bookings | Member only. **{count} booking group(s)**; per group: code, status, package names, tickets, sessions and **Paid at booking:**. **View tickets and details** (confirmed group) or **View details** opens the group below. **See the package reviews** appears once a session has started. Empty: **You have no bookings yet. After you book and pay, your tickets will appear here.** |
| Booking code * | Guest only. Required booking group code, typed in upper case (placeholder GRP-…). |
| Email used for the booking / Phone number used for the booking | Guest only, under **Verification (fill in just one of the two):**. Typing in one field clears the other; the phone has a country code selector. |
| Look up booking group | Runs the Guest lookup. **No booking group matches the code and contact details provided.** when nothing matches. |
| Group header | **Booking code**, group status (**Awaiting payment**, **Confirmed**, **Cancelled** or **Hold expired**), ticket count, amount paid at booking and the package lines with ticket count and price per ticket. |
| Payment hold panel | Pending group only. **⏳ Time left to pay:**, **Pay {amount} via VNPay** and **Release hold**, which needs a reason. **Release hold** is replaced by a note while a payment attempt is in progress. |
| Tickets in this group | Per ticket: **Ticket code**, status or attendance, package and guest number, sessions, **Ticket price**, **Paid at booking** or **Paid so far**, **Balance due**, **Payment type**, **Ticket recipient:** and **Check-in QR code** when the ticket has one. |
| Change schedule | Confirmed ticket without attendance. Lists only sessions that can replace the chosen one, with seats left. A choice marked **Needs manager approval** sends a request. A price increase must be accepted first. A session that has started cannot be changed online; the customer is asked to contact the shop. |
| Cancel ticket | Confirmed ticket without attendance; needs a reason. Cancelling 24 hours or more before the first session returns the points used on the ticket; a later cancellation does not. The booking-time payment is not refunded. The group voucher is restored only when every ticket is cancelled validly and the voucher has not expired. The result states what was returned. |
| Send the ticket to a companion | Confirmed ticket without attendance. Sends the ticket to **Ticket recipient email**; at most three online recipient changes per ticket. |
| View the ticket bill / Choose a replacement session or a refund | Opens Customer Folio for a confirmed or completed ticket, or Mland Remedy when Mland cancelled or moved a session. |
| Write a review | Member only. **Review after attending** is disabled before attendance. |
| Resend confirmation email | Confirmed group only; resends the confirmation to the booking email, within an hourly limit. |
| Expired / cancelled notice | States that all held seats were released; an expired group offers **Book the workshop again**. |
