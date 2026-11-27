### 3.12 UC-36 Collect Ticket Balance

#### UC Name

UC-36 Collect Ticket Balance

#### Primary Actors

Staff, Manager

#### Secondary Actors

Payment Gateway (VNPay)

#### Description

As a Staff member or a Manager, I want to collect a ticket's remaining balance by VNPay, cash or bank transfer so that the folio is finalized when nothing is owed.

#### Preconditions

- The actor is signed in as an active Manager, or as active Staff assigned to a session of the ticket.
- The ticket has an Open folio with a balance due above 0.
- No VNPay part of the folio is pending verification.

#### Normal Flow

***Collect Ticket Balance in Cash***

1. The actor opens the ticket folio by its ticket code.
2. The system shows the folio lines, the collections and the balance due.
3. The actor selects cash as the collection method.
4. The actor enters the amount to apply and the cash received.
5. The actor submits the collection.
6. The system checks the actor's access, the folio state and the amount.
7. The system records the cash collection as confirmed, with the change returned and the branch cash day.
8. The system records the collection in the audit log.
9. The system recomputes the balance due.
10. The system finalizes the folio because the balance due is 0 and nothing is pending.
11. The system confirms that the collection has been recorded and the folio finalized (MSG215).

#### Alternative Flows

***Step 1.1 Ticket Has No Folio***

1. The system informs the actor that no folio matches the ticket code (MSG246).
2. The use case ends without collecting money.

***Step 3.1 Customer Pays by VNPay***

1. The actor selects VNPay and enters the amount.
2. The system checks the VNPay minimum and reserves this part of the balance.
3. The system creates a VNPay checkout and shows its QR code and validity time (MSG216).
4. The customer pays on their own phone through the Payment Gateway.
5. The system receives the signed VNPay confirmation and confirms the collection.
6. The system shows that the VNPay payment has succeeded (MSG217).
7. The use case continues at step 8.

***Step 3.2 VNPay Amount Is Below the Minimum***

1. The system warns that a VNPay part or the remainder after it would be under 5,000 VND (MSG218).
2. The system creates no VNPay checkout.
3. The use case continues at step 3.

***Step 3.3 Customer Pays by Bank Transfer***

1. The actor selects bank transfer and enters the amount and the customer's transaction reference.
2. The actor submits the collection.
3. The system records the transfer as pending verification without reducing the balance due.
4. The system records the collection in the audit log.
5. The system confirms that the collection has been recorded (MSG215).
6. The use case ends; a Manager verifies the transfer later.

***Step 6.1 Actor Is Outside the Ticket's Branch Scope***

1. The system refuses the collection (MSG01).
2. The use case ends without collecting money.

***Step 6.2 Staff Is Not Assigned to the Ticket's Session***

1. The system refuses the collection (MSG212).
2. The system records the refusal in the audit log.
3. The use case ends without collecting money.

***Step 6.3 VNPay Part Is Still Pending***

1. The system refuses a new collection until the pending VNPay part is confirmed or failed (MSG219).
2. The use case ends without collecting money.

***Step 6.4 Amount or Folio State Is Not Valid***

1. The system refuses the collection with the reason, such as cash received below the amount, no balance or a duplicate reference (MSG244).
2. The system records no collection.
3. The use case continues at step 4.

***Step 7.1 Cash Exceeds the Balance Due***

1. The system records the cash collection in full.
2. The system marks the amount above the balance as excess money for a Manager bank refund.
3. The use case continues at step 8.

***Step 10.1 Balance Due Remains***

1. The system keeps the folio Open with the new balance due.
2. The system confirms that the collection has been recorded (MSG215).
3. The use case continues at step 3.

#### Postconditions

- Each collection records method, amount, status, reference, actor and time on the ticket folio.
- Confirmed collections reduce the balance due; pending transfers do not.
- The folio is Finalized only when the balance due is 0 and no collection is pending.
- Excess money is recorded in full for a bank refund.

#### Business Rule

BR-113, BR-116, BR-117, BR-118, BR-125, BR-126
