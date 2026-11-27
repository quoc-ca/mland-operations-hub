#### 8.1.1 Shopping Cart

![](assets/screens/live/32-shopping-cart.png)

This screen allows Members to:

- Review their own cart grouped by branch, with product images, stock left and current indicative prices; no line is ticked when the cart opens.
- Change whole-number quantities from 1 to 99 per line, limited by the branch stock; the cart holds at most 50 lines.
- Remove one line or all ticked lines.
- Tick the lines to buy, see the best eligible voucher and member points applied in an estimate, and check out with only those lines.
- After **Buy again** on an order, find the re-added rings ticked and a notice naming the rings that could not be added.

Only a signed-in Member can use the cart; a Guest sees "Sign in to see your cart." Other signed-in roles are sent to the not-found page. The cart creates no hold or order; prices are fixed only at checkout.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | "Cart" with **← Continue shopping**, which opens the product catalogue. |
| Header row | "Select all items" check box with the columns Product, Unit price, Quantity, Amount and Actions. |
| Branch group | Branch name with a check box that ticks every sellable line of the branch. |
| Line check box | Ticks the line to buy. Default: unticked, except rings re-added by **Buy again**. A line not for sale or without a price cannot be ticked. |
| Product | Image, name, "Size {size} · {SKU}", "{n} left" and "Temporarily not for sale" when the line can no longer be sold. |
| Unit price | Indicative unit price, or "No price yet". On a ticked discounted line, the old price is struck through with "Voucher applied" or "On sale". |
| Quantity | **−**, a number field and **+**; invalid entries show "Enter a quantity from 1 to 99." or "The branch has only {n} of this item left." |
| Amount | Line amount net of that line's own discount share; points are not spread over lines. |
| **Delete** | Removes that line. |
| MLAND voucher | Shows the chosen voucher with its saving, or "Select or enter a code". Default: the best eligible voucher. Opens the Choose an MLAND voucher dialog. |
| Member points | Check box with "You have {n} points · up to {max} usable", "You have no points yet" or "At least {n} points are needed", and the points discount. Default: off. |
| Footer | "Select all ({n})", **Delete** for all ticked lines, "Total ({n} items):", "You save" and "Calculating discounts…". |
| **Check out** | Opens Retail Checkout with the ticked lines and benefit choice; disabled when nothing is ticked. |
| Empty-cart notice | "Your cart is empty." with **Browse products**. |
