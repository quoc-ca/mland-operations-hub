### 8.5 UC-29 Record Customer Pickup

#### UC Name

UC-29 Record Customer Pickup

#### Primary Actors

Staff

#### Secondary Actors

None

#### Description

As a Staff member, I want to record the handover of a ready pickup order and collect the counter cash where due so that the order is completed.

#### Preconditions

- The Staff member is signed in with an active account assigned to the order's branch.
- The pickup order is paid and marked ready, or awaits counter cash payment.

#### Normal Flow

***Record Customer Pickup***

1. The Staff member opens the branch retail orders page.
2. The system shows the branch orders in status tabs with their counts.
3. The Staff member searches for the order code given by the customer.
4. The system shows the matching order under Chờ khách nhận.
5. The Staff member selects Giao cho khách.
6. The system checks the Staff role, the branch and the ready state of the order.
7. The system records the handover with the Staff member and the time.
8. The system moves the order to Hoàn thành and records the received time.
9. The system confirms that the order has been handed to the customer (MSG81).

#### Alternative Flows

***Step 4.1 Order Awaits Counter Cash Payment***

1. The system shows the order under Chờ thu tiền mặt with the amount and the hold deadline.
2. The Staff member receives the exact amount in cash from the customer.
3. The Staff member selects Xác nhận đã thu with the amount.
4. The system checks the amount, the pickup deadline and the order state.
5. The system records the payment, the preparation and the handover in one transaction.
6. The system sells the held stock, settles the benefits and grants the earned points.
7. The system confirms that the cash has been collected and the order completed (MSG81).
8. The use case ends.

***Step 4.2 Order Is Not Found***

1. The system shows that no order matches the search.
2. The use case continues at step 3.

***Step 4.3 Pickup Deadline Has Passed***

1. The system refuses the cash confirmation because the order is no longer held (MSG289).
2. The system records no payment.
3. The use case ends.

***Step 6.1 Order Belongs to Another Branch or Actor Is Not Staff***

1. The system refuses the action (MSG01).
2. The system records nothing.
3. The use case ends.

***Step 6.2 Order Was Already Handed Over or Changed***

1. The system returns the recorded handover without a second record when it was already done.
2. The system informs the Staff member when the order is no longer in a valid state (MSG03).
3. The use case continues at step 2.

***Step 7.1 Result Is Not Confirmed***

1. The system informs the Staff member that the result could not be confirmed (MSG04).
2. The Staff member selects the same action again.
3. The use case continues at step 6.

#### Postconditions

- The pickup order is in Hoàn thành with the Staff member and handover time recorded.
- For a cash order, one collection of the exact amount is recorded and the order is paid.
- The Member sees the order as received at the store.

#### Business Rule

BR-08, BR-18, BR-19, BR-34, BR-35, BR-37, BR-160, BR-161, BR-162
