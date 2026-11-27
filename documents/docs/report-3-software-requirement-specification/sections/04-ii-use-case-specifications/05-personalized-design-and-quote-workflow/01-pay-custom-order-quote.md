### 5.1 UC-19 Pay Custom Order Quote

#### UC Name

UC-19 Pay Custom Order Quote

#### Primary Actors

Member

#### Secondary Actors

Payment Gateway (VNPay)

#### Description

As a Member, I want to pay the full Manager-approved quote of my custom order through VNPay so that Mland starts making my ring.

#### Preconditions

- The Member is signed in and owns the custom order.
- The latest quote of the order is approved by a Manager.
- The quote deadline has not passed.

#### Normal Flow

***Pay Custom Order Quote***

1. The Member opens the custom order in My custom orders.
2. The system shows the design snapshot, the quote amount, the deadline and the payment status.
3. The Member selects Pay the full amount.
4. The system checks ownership and that the latest quote is approved and payable.
5. The system checks that the quote deadline has not passed.
6. The system records a payment attempt for the full quote amount.
7. The system creates a VNPay payment link that expires at the quote deadline.
8. The system opens the VNPay payment page.
9. The Member pays on the VNPay page.
10. The system receives the signed VNPay payment confirmation.
11. The system checks the amount, the currency, the quote and the deadline against the payment attempt.
12. The system marks the quote and the custom order as Paid.
13. The system queues the custom order for production.
14. The system shows the custom order as paid.

#### Alternative Flows

***Step 4.1 Quote Is Not Yet Approved***

1. The system informs the Member that the quote is not ready for payment (MSG282).
2. The use case ends without a payment attempt.

***Step 4.2 Order Is Already Paid***

1. The system informs the Member that the order is already paid (MSG282).
2. The use case ends without a new payment attempt.

***Step 5.1 Quote Deadline Has Passed***

1. The system marks the quote and the custom order as Quote expired.
2. The system informs the Member that the quote has expired (MSG281).
3. The system offers the Request new quote action.
4. The use case ends without a payment attempt.

***Step 5.2 Member Requests a New Quote***

1. The Member selects Request new quote on an expired order.
2. The system checks ownership and that no valid quote remains.
3. The system sets the custom order back to waiting for a quote.
4. The use case ends; Staff prepares the new quote in UC-31 Submit Custom Order.

***Step 5.3 A Valid Quote Still Exists***

1. The system informs the Member that the order still has a valid quote (MSG284).
2. The use case ends without a new quote request.

***Step 8.1 VNPay Page Does Not Open***

1. The system informs the Member that the VNPay page did not open (MSG283).
2. The use case continues at step 3.

***Step 9.1 Member Leaves VNPay Without Paying***

1. The system keeps the order waiting for full payment.
2. The use case continues at step 1 while the quote deadline has not passed.

***Step 10.1 Only the VNPay Return Page Is Received***

1. The system does not treat the return page as a payment.
2. The system keeps the order waiting for full payment until a signed confirmation arrives.
3. The use case ends without confirming the payment.

***Step 11.1 Confirmed Payment Does Not Match the Quote***

1. The system keeps the payment for manual review.
2. The system does not mark the custom order as paid.
3. The use case ends without confirming the payment.

#### Postconditions

- The verified payment is linked to the custom order and its quote.
- The custom order is Paid and waits for production.
- A repeated confirmation of the same payment changes nothing.

#### Business Rule

BR-03, BR-04, BR-07, BR-152
