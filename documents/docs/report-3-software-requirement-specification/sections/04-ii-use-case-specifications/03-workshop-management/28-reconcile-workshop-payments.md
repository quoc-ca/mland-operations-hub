### 3.28 UC-59 Reconcile Workshop Payments

#### UC Name

UC-59 Reconcile Workshop Payments

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to review the workshop bank-transfer and VNPay collections of a branch and day and record absorbed undercharges so that the money matches the bank and VNPay records.

#### Preconditions

- The actor is signed in as an active Manager.
- The branch has workshop collections or undercharge cases to review.

#### Normal Flow

***Reconcile Workshop Payments***

1. The Manager opens the reconciliation screen.
2. The Manager selects a branch and a working day.
3. The Manager selects the method and state filters to apply.
4. The system reads the transfer, VNPay and booking-time payments of that branch and day.
5. The system lists each row with ticket, group, amount, method, state and transaction reference.
6. The system shows the confirming actor and time, and the confirmed total without unverified rows.
7. The Manager compares the rows with the bank statement and VNPay Merchant Admin outside the system.
8. The Manager opens the finalized folio of a ticket that was undercharged.
9. The Manager enters the undercharged amount and a reason.
10. The system checks that the folio is finalized and the amount stays within the folio total.
11. The system records the absorbed undercharge without changing the folio, cash or loyalty points.
12. The system confirms that the absorbed undercharge has been recorded (MSG256).

#### Alternative Flows

***Step 1.1 Actor Is Not a Manager***

1. The system refuses access to the list (MSG01).
2. The use case ends without showing any collection.

***Step 4.1 Booking-Time Payments Cannot Be Read***

1. The system informs the Manager that the list is unavailable (MSG254).
2. The system shows no partial list.
3. The use case ends without reconciliation.

***Step 5.1 No Collection Matches the Filters***

1. The system informs the Manager that no collection matches (MSG255).
2. The use case continues at step 3.

***Step 8.1 Manager Voids a Mistaken Undercharge Record***

1. The Manager selects Void on an active undercharge record and enters a reason.
2. The system checks that the record was not voided before.
3. The system marks the record as voided and keeps its original content.
4. The system excludes the record from the totals (MSG256).
5. The use case ends after the void.

***Step 8.2 Manager Lists Absorbed Undercharges***

1. The Manager selects a branch and a period on the absorbed undercharges screen.
2. The system lists the records with the total of the records that are not voided.
3. The use case ends after the list is shown.

***Step 9.1 Reason Is Missing***

1. The system shows that a reason is required (MSG235).
2. The system records nothing.
3. The use case continues at step 9.

***Step 10.1 Undercharge Record Is Refused***

1. The system informs the Manager why the record cannot be made (MSG244).
2. The system records nothing.
3. The use case continues at step 9.

#### Postconditions

- The reconciliation list changes no collection, folio or cash close.
- An absorbed undercharge is recorded once, immutable, and audited with the Manager and reason.
- No amount is collected from the customer for an undercharge.

#### Business Rule

BR-139, BR-140, BR-130, BR-143
