#### 4.1.2 Workshop Package Details

![](assets/screens/live/14-workshop-package-details.png)

This screen allows Guests and Members to:

- Read a published package's description, price per ticket, offers and package rules.
- Choose a branch that sells the package and a date range, then pick the required number of sessions.
- Choose the number of tickets within the package range and the seats left in the chosen sessions.
- Add the selection to the workshop cart with **Add to cart**, or book it at once with **Book now**.

On the screen, they can also:

- Go back to the package list with **← Back to the workshop list**.
- Jump to the package's reviews at the bottom of the page.

Seat counts on this page are indicative only; no seat is held until the booking is confirmed. A package sold at no branch shows no session or ticket controls. The chosen branch, sessions, ticket count and dates stay in the page address, so the browser Back button returns with the same choice.

**Field Description**

| Field Name | Description |
| --- | --- |
| Page header | Eyebrow **WORKSHOP PACKAGE DETAILS** and the package name. |
| Package rules | **Package rules**, beside the package image: **Number of sessions:**, **Tickets per booking:**, **Location rule:** (**All sessions must be at the same branch** or **Sessions may be at different branches**), **Minimum gap when attending on the same day:** (only when the package sets one) and **Deposit policy:** **Deposit of up to ₫200,000 per ticket** when reserving; the rest is paid at the branch. |
| Price | Price per ticket (**/ ticket**) after the best automatic discount and the best usable voucher; the public price when no offer applies. A voucher limited to sessions or dates is never counted. The note says the final price is quoted by the system when booking. |
| Description | The package description. |
| Offers on this package | **Shop discounts** applied automatically (one that needs a bigger order shows **Applied on orders from {minimum}**) and **Vouchers** covering the package, each with its minimum and **View and save code** or **View conditions**. A visitor who is not signed in gets **Sign in to use member codes**. Hidden when there is no offer. |
| See reviews of this package ↓ | Scrolls to the package's reviews at the bottom of the page. |
| Branch | Branches where the package is currently sold. Default: the first branch in the list. If the chosen branch stops selling the package, the screen shows **This package is no longer sold at the branch you chose. Please choose another branch.** |
| From date / To date | Date range for the session search. Default: today to 14 days from today. Changing the branch or a date clears the chosen sessions. |
| Session choice | **Choose {count} workshop session(s)** with **({chosen}/{count} chosen)**: one button per session with branch, date and time and **{count} seat(s) left** or **Full**. A full session, or one with fewer seats than the package minimum, cannot be chosen. Six sessions per page, with **‹ Previous**, a page selector and **Next ›**. |
| Ticket quantity | Stepper enabled only after all required sessions are chosen. The maximum is the lower of the package maximum and the seats left; the hint under it states the limit. |
| Add to cart | Adds the package, branch, sessions and ticket count as one line of the workshop cart. A pop-up **Added to your cart** closes after one second. |
| Book now | Opens Session and Participant Booking with the chosen branch, sessions and ticket count. |
| Selection messages | Shown when the choice is incomplete, for example **Please choose all {count} workshop session(s).** or **The minimum number of tickets is {count}.** |
| Workshop reviews | The reviews of this package, at the bottom of the page. |
| Load and error states | **Loading the workshop package...** while loading. An unknown package shows **Workshop package not found.** or **The workshop package does not exist or is no longer offered.** with **← Back to the workshop list**. **This package is not sold at any branch right now. Please come back later.** when no branch sells it. **No workshop session matches the chosen period at this branch. Please choose another period or branch.** when no session is found. |
