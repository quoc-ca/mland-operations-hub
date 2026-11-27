### 8.2 UC-22 Checkout Retail Order

#### UC Name

UC-22 Checkout Retail Order

#### Primary Actors

Member

#### Secondary Actors

Delivery Provider (GHTK)

#### Description

As a Member, I want my ticked cart lines revalidated, priced with the best benefits and fulfilment, and placed as one order per branch so that the items are held for payment.

#### Preconditions

- The Member is signed in with an active account.
- At least one cart line is ticked.

#### Normal Flow

***Checkout Retail Order***

1. The Member selects Mua hàng in the cart.
2. The system groups the ticked lines by branch and opens the checkout.
3. The system revalidates each line's sale status, price and branch stock.
4. The Member chooses store pickup or GHTK delivery for each branch order.
5. The Member chooses a saved delivery address for each GHTK branch order.
6. The system requests a GHTK delivery fee for each GHTK branch order.
7. The Member chooses VNPay as the payment method.
8. The system applies the best automatic discount, the chosen voucher and the requested points.
9. The system shows each branch order's subtotal, discount and fee, and the combined payable total.
10. The Member selects Đặt hàng.
11. The system rechecks the quote, stock, benefits and fees against the current data.
12. The system creates one order per branch and holds their stock, voucher and points for 10 minutes.
13. The system removes the ordered lines from the cart.
14. The system confirms that the order has been placed and opens VNPay (MSG77).

#### Alternative Flows

***Step 1.1 No Line Is Ticked***

1. The system asks the Member to select at least one item (MSG73).
2. The use case ends without opening the checkout.

***Step 2.1 Ticked Lines Are No Longer in the Cart***

1. The system informs the Member that the chosen items are no longer in the cart (MSG76).
2. The use case ends without creating an order.

***Step 3.1 A Line Is No Longer Available***

1. The system refuses the checkout and names the unavailable product or quantity (MSG72).
2. The use case ends without creating an order or a hold.

***Step 5.1 No Address or GHTK Does Not Deliver There***

1. The system asks the Member to add an address, choose another one or choose store pickup (MSG75).
2. The use case continues at step 4.

***Step 6.1 GHTK Fee Is Unavailable***

1. The system informs the Member that the delivery fee could not be loaded (MSG05).
2. The use case continues at step 4.

***Step 7.1 Member Chooses Cash at the Counter***

1. The system offers cash only when every branch order is store pickup.
2. The Member chooses cash at the counter.
3. At step 12, the system holds each branch order for 72 hours instead of 10 minutes.
4. The system opens My Orders and states where and before when to pay (MSG77).
5. The use case ends.

***Step 8.1 Voucher Cannot Be Used***

1. The system shows the voucher as not usable with its reason (MSG47).
2. The system falls back to the automatic discount.
3. The use case continues at step 9.

***Step 11.1 Quote, Fee or Benefit Has Changed***

1. The system refuses the order and asks the Member to review the new total (MSG50).
2. The system refreshes the fees and the total.
3. The use case continues at step 9.

***Step 11.2 Voucher Is Refused at Order Time***

1. The system informs the Member that the voucher no longer applies (MSG47).
2. The system removes the voucher and recalculates the total.
3. The use case continues at step 9.

***Step 12.1 Payable Total Is 0 VND***

1. The system marks the orders as paid without VNPay.
2. The system finalizes the voucher and points and records the sale.
3. The system opens My Orders with the paid orders.
4. The use case ends.

***Step 12.2 Same Request Is Sent Again***

1. The system returns the orders already created for that request.
2. The use case continues at step 14.

***Step 12.3 Save Outcome Is Not Confirmed***

1. The system informs the Member that the result could not be confirmed (MSG04).
2. The system creates no partial order, hold or benefit use.
3. The use case ends.

#### Postconditions

- One order per branch is created with frozen lines, prices, discounts, fees and recipient.
- The stock, voucher and points of the orders are held until payment, cancellation or expiry.
- The ordered lines are removed from the cart; unticked lines stay unchanged.

#### Business Rule

BR-09, BR-16, BR-18, BR-20, BR-33, BR-34, BR-37, BR-153, BR-155, BR-156, BR-157
