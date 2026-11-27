### 8.4 UC-24 Track My Orders

#### UC Name

UC-24 Track My Orders

#### Primary Actors

Member

#### Secondary Actors

None

#### Description

As a Member, I want to follow my retail orders through payment, preparation, pickup or delivery so that I know where each order stands.

#### Preconditions

- The Member is signed in with an active account.

#### Normal Flow

***Track My Orders***

1. The Member opens My Orders.
2. The system lists the Member's own branch orders, newest first.
3. The system groups the orders into status tabs.
4. The Member selects a status tab.
5. The system shows each order's code, branch, items, amounts and current status.
6. The Member opens an order.
7. The system shows the payment, discount, fee, fulfilment method and recorded dates.
8. The system confirms the current order status to the Member.

#### Alternative Flows

***Step 2.1 Orders Cannot Be Loaded***

1. The system informs the Member that the data could not be loaded (MSG05).
2. The use case ends.

***Step 4.1 Member Searches Orders***

1. The Member enters an order code or a product name.
2. The system shows only the matching orders.
3. The use case continues at step 6.

***Step 5.1 Unpaid VNPay Order***

1. The system shows the remaining hold time and a Thanh toán action.
2. The Member selects Thanh toán.
3. The use case continues in UC-23 Pay for Retail Order.

***Step 5.2 Hold Has Ended Without Payment***

1. The system states that the hold has ended and the order will cancel itself (MSG80).
2. The use case continues at step 6.

***Step 5.3 Member Cancels an Unpaid Order***

1. The Member selects Hủy đơn.
2. The system asks the Member to confirm the cancellation (MSG286).
3. The Member confirms.
4. The system cancels the unpaid order and releases its stock, voucher and points.
5. The system returns the items to the cart.
6. The use case continues at step 2.

***Step 5.4 Unpaid Cash Branch Order Is Cancelled Alone***

1. The system cancels only that branch order of the checkout.
2. The other branch orders of the checkout stay unchanged.
3. The use case continues at step 2.

***Step 5.5 Order Is Already Paid***

1. The system offers no cancellation.
2. The use case continues at step 6.

***Step 5.6 Member Buys Again***

1. The Member selects Mua lại on a delivered, picked-up or cancelled order.
2. The system adds the rings that are still sold back to the cart, ticked.
3. The system lists the rings it could not add (MSG74).
4. The use case ends with the cart open.

***Step 5.7 Order Is Picked Up or Delivered***

1. The system offers Đánh giá for the order lines not yet reviewed.
2. The Member selects Đánh giá.
3. The use case continues in UC-26 Create or Edit Own Review.

#### Postconditions

- The Member sees only their own orders and their current status.
- A cancelled order holds no stock, voucher or points, and its items are back in the cart.

#### Business Rule

BR-34, BR-37, BR-38, BR-153, BR-158
