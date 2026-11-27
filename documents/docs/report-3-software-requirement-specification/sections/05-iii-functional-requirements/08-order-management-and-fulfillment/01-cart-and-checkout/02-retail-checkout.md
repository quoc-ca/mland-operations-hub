#### 8.1.2 Retail Checkout

![](assets/screens/live/33-retail-checkout.png)

This screen allows Members to:

- Review the ticked lines grouped by branch.
- Choose store pickup or GHTK home delivery for each branch, with a saved or new address; GHTK quotes the delivery fee.
- Get the best automatic product discount, add at most one eligible voucher on the price after it, then use member points, or continue without a voucher.
- Choose VNPay or cash at the counter; cash is allowed only when every branch order is a store pickup, and goods are held up to 3 days.
- Press **Place order** to create one order per branch, with all holds taken together or none. VNPay opens straight away; a cash order shows where and until when to pay.
- Read why the voucher, points, shipping fee or total changed, and press **Update** to review the new total before ordering.

Only a signed-in Member can open the page; a Guest sees "Sign in to check out." A browser return from VNPay never marks the order paid.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | "Checkout" with "Check the items, delivery and discounts before placing the order." and **← Back to cart**. |
| Delivery address | The default saved address (recipient, phone, "Default" tag and address); pressing it opens My addresses. Without addresses: "Add a delivery address". An address GHTK cannot use shows its problems with **Update**. |
| Items by branch | Read-only lines with image, name, "Size {size} · Quantity {n}" and amount. |
| Delivery method | Per branch: "Store pickup" ("At {branch}", Free) or "Home delivery by GHTK" ("Fast", with the quoted fee). Default: Store pickup. GHTK needs a valid address and is refused where GHTK does not deliver. |
| MLAND voucher | Chosen voucher with its saving, or "Select or enter a code"; opens the Choose an MLAND voucher dialog. Default: the cart's choice, or the best eligible voucher when coming from Buy now. |
| Use {n} member points | Switch for the points on the remainder. When the voucher blocks points: "Voucher {code} cannot be combined with points. Remove the voucher to use points." |
| Payment method | Options: "VNPay" ("Pay online now, at least 5,000đ") and "Cash at counter" ("Pay at the branch on pickup; held for up to 3 days", or "Only when every branch order is a store pickup"). Default: VNPay. |
| Payment details | Merchandise subtotal, Shipping total, Product discount, Voucher discount (with its code), Points discount, Total payment and "You save", in whole VND. |
| Conflict notice | The reason the last order attempt was refused, such as a changed total, shipping fee or quote, with **Update** to recalculate. |
| **Place order** | Creates the order; disabled until the total is ready. Shows "Placing order…" or "Opening VNPay…". |
| Result | VNPay: "Order placed, opening the VNPay payment page…", with **Pay with VNPay** and **View orders** if the page did not open. Cash: the branch, deadline and amount to pay, with **View orders**. |

##### 8.1.2.1 Choose an MLAND voucher (voucher selection dialog)

![](assets/screens/live/33a-voucher-selection-dialog.png)

The Member opens this dialog from the MLAND voucher line in the cart or at checkout. At the top, "Voucher code" has a field of at most 128 characters with **Apply**. Below, "Product vouchers" lists the vouchers usable for this order; the one with the largest saving carries "Best choice". Each card shows its discount, "Min. spend", the code and "Expires". Under "Not eligible for this order", unusable vouchers are greyed out with their reason, for example "You have used up this voucher" or "Spend {amount} more to use it". The footer shows "1 voucher selected, saves {amount}" or "No voucher selected", and **OK** applies the choice.

##### 8.1.2.2 My addresses (saved-address dialog)

![](assets/screens/live/33b-address-list-dialog.png)

The Member opens this dialog from the delivery address at the top of the checkout; an address is required only for GHTK delivery. It lists the saved addresses with recipient name, phone number and full address; the default carries "Default". The Member selects one address, presses **Update** to edit one, or **+ Add a new address**. **Confirm** applies the selected address, and **Cancel** closes the dialog without changing it.

##### 8.1.2.3 Add a new delivery address (new-address dialog)

![](assets/screens/live/33c-new-address-dialog.png)

The form collects "Recipient full name", "Phone number", "Province / City", "Ward / Commune / Special zone" and "Street address (house number, building, street)", all required. The fields unlock one after another, and each locked field names what to fill in first, for example "— Enter the full name first —". "Address type" is Home or Office, and "Set as default delivery address" makes it the default. **Done** stays disabled until every required field is valid; **Cancel** or the close icon leaves without saving. The names, phone numbers and addresses in the screenshots are test data.
