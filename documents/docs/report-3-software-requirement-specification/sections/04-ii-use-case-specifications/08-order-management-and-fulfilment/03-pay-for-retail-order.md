### 8.3 UC-23 Pay for Retail Order

#### UC Name

UC-23 Pay for Retail Order

#### Primary Actors

Member

#### Secondary Actors

Payment Gateway (VNPay)

#### Description

As a Member, I want to pay my placed retail order in full through VNPay so that the sale is completed and Mland prepares my items.

#### Preconditions

- The Member is signed in with an active account.
- The Member has an unpaid VNPay order whose 10-minute hold is still active.

#### Normal Flow

***Pay for Retail Order***

1. The Member requests to pay the order from the checkout or from My Orders.
2. The system checks that the hold of the order is still active.
3. The system creates one VNPay payment link for the full payable total.
4. The system opens the VNPay payment page.
5. The Member completes the payment on VNPay.
6. VNPay sends a signed payment result to the system.
7. The system verifies the signature, the amount and the active hold.
8. The system marks every branch order of the checkout as paid.
9. The system converts the held stock into a sale and finalizes the voucher and points.
10. The system grants the earned loyalty points.
11. The system creates a shipment for each GHTK branch order.
12. The system confirms that the payment has been received successfully (MSG79).

#### Alternative Flows

***Step 1.1 Member Returns to Pay Again***

1. The system reopens the same VNPay link while the hold lasts.
2. The use case continues at step 4.

***Step 2.1 Hold Has Ended***

1. The system refuses to start the payment.
2. The system informs the Member that the hold has ended (MSG80).
3. The use case ends without payment.

***Step 3.1 VNPay Page Does Not Open***

1. The system informs the Member that the order is placed but VNPay did not open (MSG78).
2. The use case continues at step 1.

***Step 5.1 Member Leaves VNPay Without Paying***

1. The system shows the order under Chờ thanh toán with its hold deadline (MSG287).
2. The use case continues at step 1.

***Step 6.1 VNPay Result Has Not Arrived Yet***

1. The system informs the Member that it is confirming the payment with VNPay (MSG288).
2. The system rechecks the order status a few times.
3. The system informs the Member that no result has arrived yet and the order will update itself (MSG288).
4. The use case ends without confirming payment.

***Step 6.2 Browser Return Only***

1. The system treats the VNPay return page as display only.
2. The system waits for a signed result or a signed status query.
3. The use case continues at step 6.

***Step 7.1 Signed Result Is Invalid or Does Not Match***

1. The system rejects the result and leaves the order unpaid.
2. The use case ends without confirming payment.

***Step 7.2 Payment Arrives After the Hold Ended***

1. The system records the payment in the Manager reconciliation queue.
2. The system does not revive the order.
3. The use case ends.

***Step 7.3 VNPay Reports a Failed or Expired Payment***

1. After the hold deadline, the system releases the stock, voucher and points.
2. The system marks the order as expired and returns its items to the cart.
3. The use case ends without payment.

***Step 7.4 Result Remains Unconfirmed***

1. The system queries VNPay with a signed status request after the deadline.
2. The system records the checkout in the reconciliation queue after 60 minutes without a result.
3. The use case ends without releasing or paying the order.

***Step 8.1 Duplicate Result Arrives***

1. The system recognises the payment already applied.
2. The use case ends without changing the order.

#### Postconditions

- A verified payment is recorded once and every branch order of the checkout is paid.
- The held stock becomes sold, benefits are finalized and points are granted.
- A GHTK branch order has a shipment waiting for preparation.

#### Business Rule

BR-03, BR-04, BR-18, BR-19, BR-34, BR-35, BR-153, BR-159
