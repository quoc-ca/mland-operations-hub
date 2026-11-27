### 3.21 UC-52 Verify Transfer Payment

#### UC Name

UC-52 Verify Transfer Payment

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to confirm a pending bank-transfer collection or mark it as failed so that the ticket balance counts only money that really arrived.

#### Preconditions

- The actor is signed in as an active Manager.
- The ticket folio has a bank-transfer collection pending verification.

#### Normal Flow

***Verify Transfer Payment***

1. The Manager opens the ticket folio by its ticket code.
2. The system shows the folio lines, the collections and the balance due.
3. The Manager checks that the transferred money is in the shop bank account.
4. The Manager enters the bank transaction reference of the pending transfer.
5. The Manager confirms the transfer.
6. The system checks that the collection belongs to this folio and is still pending.
7. The system marks the collection as confirmed with the Manager and the time.
8. The system recalculates the balance due of the folio.
9. The system finalizes the folio, since nothing is due and no collection is pending.
10. The system confirms that the transfer has been confirmed (MSG233).

#### Alternative Flows

***Step 3.1 Money Cannot Be Verified***

1. The Manager selects to mark the pending collection as not paid.
2. The system asks for a reason in a dialog.
3. The Manager enters the reason and confirms.
4. The system marks the collection as failed and records the Manager, reason and time.
5. The system frees the balance so that it can be paid another way (MSG234).
6. The use case ends without confirming the transfer.

***Step 3.2 Reason for the Failure Is Missing***

1. The system shows that a reason is required (MSG235).
2. The system does not mark the collection as failed.
3. The use case continues at step 3.

***Step 5.1 Actor Is Not a Manager***

1. The system refuses the action (MSG01).
2. The system changes no collection.
3. The use case ends without confirming the transfer.

***Step 6.1 Collection Is No Longer Pending***

1. The system informs the Manager that the collection was already confirmed or failed (MSG03).
2. The system changes nothing.
3. The use case ends without confirming the transfer.

***Step 6.2 Collection Does Not Belong to This Folio***

1. The system reports that the collection is not found (MSG246).
2. The system changes nothing.
3. The use case ends without confirming the transfer.

***Step 7.1 Ticket Was Cancelled by Mland or the Folio Is No Longer Open***

1. The system records the confirmed money in full without applying it to the balance.
2. The system opens a bank refund request for that money.
3. The use case continues at step 10.

***Step 8.1 Transfer Exceeds the Balance Due***

1. The system records the full transferred amount and flags the excess.
2. The system opens a bank refund request for the excess amount.
3. The use case continues at step 9.

***Step 9.1 Balance Is Still Due***

1. The system keeps the folio open.
2. The use case continues at step 10.

#### Postconditions

- A confirmed transfer reduces the balance due; a failed collection does not.
- The decision, the Manager and the time are recorded in the audit log.
- Money beyond the balance is kept in full and owed back by bank refund.
- The folio is finalized only when nothing is due and nothing is pending.

#### Business Rule

BR-125, BR-126, BR-143
