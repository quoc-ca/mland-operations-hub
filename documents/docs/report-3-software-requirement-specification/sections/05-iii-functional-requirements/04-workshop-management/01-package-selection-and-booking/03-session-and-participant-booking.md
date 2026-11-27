#### 4.1.3 Session and Participant Booking

![](assets/screens/live/15-session-and-participant-booking.png)

This screen allows Guests and Members to:

- Review the sessions chosen on the package page and swap a session for another one with enough seats.
- As a Member, choose a voucher and use loyalty points; a Guest books with the automatic discount only.
- See the server quote with each ticket's price, the amount paid at booking and the rest paid at the counter.
- Enter the contact email and phone, then confirm the quote to hold seats for the whole booking group.
- Pay the amount due at booking through VNPay before the payment deadline ends, or release the hold.

On the screen, they can also:

- Go back to the package page with the same choice through **← Back to choose sessions and tickets**.
- Open the workshop cart through **Workshop cart ({count})**.

Each participant is a separate ticket with its own QR code, check-in and invoice. Seats are held only when the quote is confirmed. The group is confirmed only after the server verifies the VNPay payment. The payment deadline is 10 minutes after the hold starts.

The best automatic discount applies to every quote, including a Guest's. A Guest gets no voucher or points. For a Member, one voucher may be added after the automatic discount, and points are applied last. When offers cover the whole price, no online payment is needed. When the amount due at booking is below the VNPay minimum, it is paid at the counter instead.

**Field Description**

| Field Name | Description |
| --- | --- |
| Page header | Eyebrow **WORKSHOP CHECKOUT**, the package name and a short introduction. |
| Booking identity | **Booking as an MLAND Member.** or **Booking as a Guest.** When the signed-in account's role cannot be verified, **Cannot verify the right to book workshops** is shown and all controls are locked. |
| 1. Chosen workshop sessions | The branch name and the chosen sessions with date and time. **Change session** turns each session into a list of other open sessions with **{left} seat(s) left**; a session without enough seats for the ticket count is marked **(not enough for the ticket count)** and cannot be chosen. **Cancel** closes the lists. With no session chosen, **You have not chosen any workshop session.** and **Choose sessions**. |
| 2. Offers & Reward points | Member only. One voucher covers the whole group. **MLAND Voucher** opens **Choose an MLAND voucher**: a code field with **Apply** and **Suggested vouchers** (saved usable and running workshop vouchers). **Your points** shows the available and held points, the value of one point and the minimum use. The points switch uses the maximum the quote allows. It is disabled below the minimum. |
| Quote again | Shown only when a quote fails; requests it again. The first quote is automatic. For a Member, the best of up to five candidate vouchers is preselected; points stay off until turned on. Changing a session, voucher or points requests a new quote at once. |
| Book-now quote | Per package line: sessions and, per ticket, **Ticket**, **Ticket price**, **Paid at booking**, **Remaining at counter** and **Type** (**Paid in full**, **Deposit**, **Free** or **Pay at the counter**). Totals: **Package total**, **Automatic discount**, **Voucher**, **Reward points**, **Total after offers**, **Pay at booking** and **Remaining to pay at the counter**. |
| Email for tickets & confirmation * / Contact phone number * | Required contact details of the buyer; the phone has a country code selector. Companions' details are not needed at this step. |
| Confirm and hold seats | Creates the booking group and holds all seats at once. On refusal, for example no capacity or the package not sold at the branch, nothing is held and the entered details are kept. |
| Booking result | After confirmation: a heading such as **Seats held for {count} ticket(s)**, the **Booking code**, group status, ticket count, amount paid at booking and **Tickets in this group**. |
| Payment hold panel | **⏳ Time left to pay:** with a countdown, **Pay {amount} via VNPay** and the note that held seats are released when time runs out. |
| Release hold | Opens **Confirm releasing the hold for the whole group** with a required **Reason \***; **Yes, release** returns all held seats. Replaced by a note while a payment attempt is in progress. |
| Resend confirmation email | Shown for a confirmed group; resends the confirmation to the booking email, within an hourly limit. |
| Messages | For example **The quote has expired. Please request a new quote.**, **The system has paused registration for this session.**, **This workshop package is not on sale yet or has been paused.** or **The system cannot process the request right now.** The entered details are kept. |
