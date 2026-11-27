#### 4.1.4 Workshop Booking Cart

![](assets/screens/live/21-workshop-booking-cart.png)

This screen allows Guests and Members to:

- Review the workshop cart lines added from package pages, each with its branch, sessions and number of tickets.
- Select the lines to book, change the number of tickets of a line, or remove a line.
- As a Member, choose a voucher and use loyalty points for the selected lines.
- Request one quote for the selected lines, enter the contact email and phone, and hold seats for all of them at once.
- Pay the whole group in one VNPay payment before the payment deadline ends.

On the screen, they can also:

- Switch to the product cart with the **Products** tab; the **Workshop** tab is this screen.

The cart is kept in this browser, separately for each signed-in account and for a guest. Booking from the cart removes only the booked lines. Confirming a **Book now** booking empties the cart. Cart prices are estimates; the server quote decides the amounts. The offer rules are the same as on Session and Participant Booking.

**Field Description**

| Field Name | Description |
| --- | --- |
| Cart tabs | **Products ({count})** and **Workshop ({count})**. Default: Workshop. |
| Page header | Eyebrow **WORKSHOP CART**, title **Workshop booking cart** and a short introduction. |
| Booking identity | **Booking as an MLAND Member.** or **Booking as a Guest.** |
| Line selection | Checkbox per line and a select-all checkbox in the header. Default: no line is selected when the cart opens. |
| Workshop package | Package name, **Line {n}** with the branch name, the list **Session {n}: {date and time}** and the offer badges. |
| Unit price | Price per ticket after the best automatic discount; vouchers are not included before the quote. |
| Tickets | Ticket stepper within the package limits and the seats left when the line was added. |
| Amount | Line total. After a quote, the quoted total with the public total struck through and **Automatic discount −{amount}**, **Voucher −{amount}** and **Points −{amount}**. |
| Remove line | Removes the line from the cart. |
| Line warnings | **This package is no longer sold at the chosen branch.** with **Choose another branch**, or **This line no longer has enough seats. Reduce the number of people or choose another session.** |
| Select all ({count}) | Selects or clears every line. |
| Total | **Estimate ({count} tickets)** with the note that vouchers and points are excluded. For a Member, once quoted, **Total after offers ({count} tickets)** with the discounts saved, or **No suitable voucher yet**. **Picking the best offer…** while the automatic pick runs. |
| View quote and book | Disabled until a line is selected. Opens **Workshop cart quote** with the per-ticket quote, the contact fields, **Confirm and hold seats** and **Choose again**. If any line cannot be held, nothing is held and the failing lines are named. |
| Offers & Reward points | Member only, below the cart. Voucher and points picker for the selected lines; it works as on Session and Participant Booking. When lines are selected, the best voucher and the maximum usable points are preselected. |
| Booking result | After confirmation, the booking code, group status, tickets, payment hold panel with **Pay {amount} via VNPay** and **Release hold**, as on Session and Participant Booking. |
| Empty state | **Your workshop cart is empty.** with **View workshop packages**. |
