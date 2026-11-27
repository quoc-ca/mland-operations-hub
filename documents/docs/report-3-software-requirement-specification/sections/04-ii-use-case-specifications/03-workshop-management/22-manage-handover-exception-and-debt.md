### 3.22 UC-53 Manage Handover Exception and Debt

#### UC Name

UC-53 Manage Handover Exception and Debt

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to approve a product handover before payment is verified and record or close the resulting debt so that every exception stays traceable.

#### Preconditions

- The actor is signed in as an active Manager.
- The ticket folio is open and still has a balance due or a pending collection.

#### Normal Flow

***Manage Handover Exception and Debt***

1. The Manager opens the ticket folio by its ticket code.
2. The system shows the balance due and the collections pending verification.
3. The Manager selects to allow the handover before payment verification.
4. The system asks for a reason in a dialog.
5. The Manager enters the reason and confirms.
6. The system records the handover exception with the Manager, reason and time.
7. The system keeps the balance due and the folio state unchanged (MSG236).
8. The Manager selects to record a debt for the ticket.
9. The Manager enters the debt amount and a reason.
10. The system checks that the amount is above 0 and within the balance due.
11. The system records the debt with the Manager, reason and time.
12. The system confirms that the debt has been recorded (MSG237).

#### Alternative Flows

***Step 3.1 Folio Is Already Finalized***

1. The system does not offer the handover exception, because a finalized folio already allows handover.
2. The use case ends without recording an exception.

***Step 5.1 Reason Is Missing***

1. The system shows that a reason is required (MSG235).
2. The system records nothing.
3. The use case continues at step 5.

***Step 6.1 Actor Is Not a Manager***

1. The system refuses the action (MSG01).
2. The system records no exception or debt.
3. The use case ends without any change.

***Step 6.2 Handover Was Already Approved***

1. The system keeps the handover exception recorded earlier.
2. The system writes no second audit record.
3. The use case continues at step 8.

***Step 8.1 Manager Closes a Recorded Debt***

1. The Manager selects to close the recorded debt.
2. The Manager enters a reason and confirms.
3. The system checks that the ticket has a recorded debt.
4. The system records the closure with the Manager, reason and time.
5. The system keeps the balance due unchanged (MSG237).
6. The use case ends after closing the debt.

***Step 8.2 No Debt Exists to Close***

1. The system informs the Manager that the ticket has no recorded debt (MSG239).
2. The system changes nothing.
3. The use case ends without closing a debt.

***Step 10.1 Debt Amount Is Invalid***

1. The system informs the Manager that the debt amount is invalid (MSG238).
2. The system records no debt.
3. The use case continues at step 9.

#### Postconditions

- The handover exception, debt or debt closure is audited with the Manager, reason and time.
- The exception releases the product only; the balance due stays unchanged.
- A recorded debt never finalizes the folio; a later collection settles it.
- No automatic reminder, deadline or booking block is created.

#### Business Rule

BR-127, BR-126, BR-143
