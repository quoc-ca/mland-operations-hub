#### 8.2.1 My Retail Orders

![](assets/screens/live/34-my-retail-orders.png)

This screen allows Members to:

- Browse their own branch orders, newest first, in status tabs, and search by order code or product name.
- Show an order's details inline and collapse them again; there is no separate order-details page. A product name opens its product page.
- Return here from VNPay or from placing an order and land on that order's tab; the payment result shows only after the server has verified it.
- For an unpaid VNPay order, see the time left and press **Pay** to reopen VNPay, or **Cancel order** to cancel every order of the same checkout.
- For an unpaid cash-at-the-counter order, see where and until when to pay, and cancel only that branch order.
- Press **Buy again** on a completed or cancelled order to put its rings back in the cart.
- On a completed order, press **Review** to review every product of the order, or **View reviews** once all are reviewed.

Only a signed-in Member can open this page; a Guest sees "Sign in to see your orders." Cancelling returns the items to the cart and gives back the voucher and points.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | "My orders". |
| Status tabs | Options: All, To pay, To ship, To receive, Store pickup, Completed, Cancelled; each tab except All shows its count. Default: All, or the tab of the order just placed or paid. |
| Search | "Search by order code (ML…) or product name". |
| Payment notice | Toast after VNPay or placing an order: "Confirming the payment with VNPay…", "Payment successful. Mland is preparing your order.", "Payment is not complete…" with the hold deadline, or the cash pickup instructions. |
| Order card header | Order code, branch with " · GHTK delivery" or " · Store pickup", and a status pill such as "To pay", "Preparing", "Waiting for GHTK pickup", "Being delivered", "Pay on pickup", "Ready · pay on pickup", "Ready for pickup", "Delivered", "Picked up in store", "Cancelled" or "Cancelled · expired". |
| Product lines | Image, name, "Size {size} · x{n}" and line amount, frozen from the accepted order. |
| Stage hint | Short text for the current stage, e.g. "Pay within mm:ss by VNPay to keep the items and discounts.", the branch and deadline to pay cash, or the code to give at the counter. |
| **Click for details ▼** / **Show less ▲** | Shows or hides the details: Order code, Ordered at, Branch, Delivery, Payment, Paid at, Delivered at or Picked up at, Held until or Pay by, and the amount breakdown. |
| Total | Payable amount of the branch order in whole VND. |
| **Cancel order** / **Pay** / **Buy again** | Shown only when valid for the order's state; **Cancel order** asks for confirmation first. |
| **Review** / **View reviews** | Shown on a completed order. **Review** opens one review screen for the products of the order; once every line is reviewed, **View reviews** opens the Member's reviewed tab. |
| Empty states | "No orders in this tab."; with no orders at all, "You have no orders yet." with **Shop now**. |
