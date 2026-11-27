### 3.25 UC-56 Process Refund Request

#### UC Name

UC-56 Process Refund Request

#### Primary Actors

Manager

#### Secondary Actors

Mail Gateway (SMTP), Cloud Storage Service (Cloudinary)

#### Description

As a Manager, I want to approve, record, confirm, refuse, close or reopen a refund request so that money owed to the customer is returned by bank transfer and tracked.

#### Preconditions

- The actor is signed in as an active Manager.
- A workshop refund request exists in a state that allows the selected action.

#### Normal Flow

***Process Refund Request***

1. The Manager opens the refund queue.
2. The system lists the refund requests with status, amount and masked account number.
3. The Manager opens a request pending approval.
4. The Manager selects to show the recipient account.
5. The system shows the bank, the full account number and the holder name, and audits the read.
6. The Manager approves the request with a reason.
7. The system records the approval and moves the request to pending transfer.
8. The Manager transfers the money from the shop bank account outside the system.
9. The Manager enters the bank transaction reference and attaches an optional receipt image.
10. The system records the transfer and moves the request to pending reconciliation.
11. The Manager matches the transfer against the bank statement.
12. The Manager confirms the request as refunded.
13. The system records the refund as final and deletes the stored account number, keeping the mask.
14. The system applies the folio and booking consequences of the refund.
15. The system confirms that the request has been refunded (MSG247).

#### Alternative Flows

***Step 3.1 Request Still Waits for an Account***

1. The Manager selects to resend the account link.
2. The system revokes the previous link and sends a new single-use link to the payer.
3. The system audits the resend (MSG247).
4. The use case ends with the request waiting for an account.

***Step 3.2 Customer Cannot Be Reached***

1. The Manager selects to close a request that still waits for an account.
2. The Manager enters a reason and confirms.
3. The system checks that 30 days have passed since the request was opened.
4. The system closes the request as unreachable and revokes its active links (MSG247).
5. The use case ends with the request closed as unreachable.

***Step 3.3 Request Is Closed Too Early***

1. The system informs the Manager that the 30 days have not passed (MSG248).
2. The system keeps the request waiting for an account.
3. The use case ends without closing the request.

***Step 3.4 Customer Comes Back***

1. The Manager selects to reopen a request closed as unreachable.
2. The system returns the request to waiting for an account.
3. The system sends a new account link to the payer (MSG247).
4. The use case ends with the request waiting for an account.

***Step 6.1 Manager Refuses the Request***

1. The Manager selects to return the request to account entry and enters a reason.
2. The system deletes the stored account number and keeps the mask.
3. The system voids the approval and any recorded transfer.
4. The system sends a new account link to the payer (MSG247).
5. The use case ends with the request waiting for an account.

***Step 6.2 Reason Is Missing***

1. The system shows that a reason is required (MSG235).
2. The system records nothing.
3. The use case continues at step 6.

***Step 6.3 Request Is No Longer in the Expected State***

1. The system informs the Manager that the request has changed (MSG03).
2. The system changes nothing.
3. The use case ends without any transition.

***Step 9.1 Bank Reference Is Missing***

1. The system does not allow the transfer to be recorded without the bank transaction reference.
2. The use case continues at step 9.

***Step 9.2 Receipt Image Is Refused***

1. The system informs the Manager that the image is invalid or cannot be stored (MSG249).
2. The system records nothing.
3. The use case continues at step 9.

***Step 11.1 Transfer Is Returned or Does Not Match***

1. The Manager returns the request to account entry with a reason.
2. The system deletes the stored account number, voids the approval and the transfer.
3. The system sends a new account link to the payer (MSG247).
4. The use case ends with the request waiting for an account.

***Step 12.1 Request Is Already Refunded***

1. The system keeps the refunded request unchanged and records no second confirmation.
2. The use case continues at step 15.

#### Postconditions

- Every step records the Manager, the time and the proof given.
- Folio and booking consequences apply only after the request is refunded.
- A refunded request is final; a late bank return is handled outside the system.
- The customer never loses the right to the refund before it is refunded.

#### Business Rule

BR-132, BR-133, BR-134, BR-135, BR-136
