### 5.2 UC-31 Submit Custom Order

#### UC Name

UC-31 Submit Custom Order

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member or Manager, I want to turn an accepted design request into a custom order with a quote so that the Member can pay for the custom ring.

#### Preconditions

- The actor is signed in as Staff or Manager.
- The design request is Accepted and belongs to a Member.
- The custom order of the request, if any, is not yet paid.

#### Normal Flow

***Submit Custom Order***

1. The actor opens an accepted design request.
2. The system shows the quote section of the request.
3. The actor enters the total quote amount.
4. The actor enters the payment period in hours.
5. The actor enters a note explaining the quote.
6. The actor selects Send quote.
7. The system checks the amount and the payment period.
8. The system asks the actor to confirm the quote.
9. The actor confirms the quote.
10. The system checks the role, the accepted status, the Member owner and the order lock.
11. The system creates the custom order linked to the request and the Member.
12. The system creates a new quote version with the amount in VND and the deadline.
13. The system sets the quote and the custom order to waiting for Manager approval.
14. The system confirms that the quote has been sent to the Manager (MSG278).

#### Alternative Flows

***Step 2.1 Request Already Has a Quote***

1. The system shows the existing quote with its version, amount, deadline and approval status.
2. The system hides the quote form.
3. The use case ends without a new quote.

***Step 7.1 Amount or Payment Period Is Invalid***

1. The system informs the actor of the invalid value (MSG277).
2. The use case continues at step 3.

***Step 9.1 Actor Cancels the Confirmation***

1. The actor cancels the confirmation dialog.
2. The use case continues at step 3.

***Step 10.1 Request Is Not Accepted***

1. The system refuses the quote because the request is not Accepted (MSG279).
2. The use case ends without a quote.

***Step 10.2 Custom Order Is Already Paid***

1. The system refuses the quote because the order is locked (MSG279).
2. The use case ends without a quote.

***Step 11.1 Custom Order Already Exists***

1. The system reuses the existing custom order of the request.
2. The use case continues at step 12.

***Step 13.1 Manager Prepares the Quote***

1. The system records the Manager's approval on the new quote.
2. The system sets the quote ready and the custom order waiting for full payment.
3. The use case continues at step 14.

#### Postconditions

- One custom order exists for the accepted design request.
- The new quote is the latest quote version of the order.
- A Staff quote waits for Manager approval; a Manager quote is payable.

#### Business Rule

BR-06, BR-150
