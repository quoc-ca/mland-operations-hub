### 3.23 UC-54 Correct Ticket Folio

#### UC Name

UC-54 Correct Ticket Folio

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to void a wrong folio line or collection, correct a finalized overcharge, or reverse cash of a closed day so that the folio is fixed without deleting history.

#### Preconditions

- The actor is signed in as an active Manager.
- The ticket folio exists.

#### Normal Flow

***Void a Wrong Folio Line***

1. The Manager opens the ticket folio by its ticket code.
2. The system shows the folio lines and collections with their void actions.
3. The Manager selects Void on the wrongly recorded line.
4. The system asks for a reason in a dialog.
5. The Manager enters the reason and confirms.
6. The system checks that the folio is open and that the line belongs to it.
7. The system marks the whole line as voided and keeps the original record.
8. The system records the void with the Manager, reason and time.
9. The system recalculates the balance due from the remaining lines and collections.
10. The system confirms that the line has been voided (MSG240).

#### Alternative Flows

***Step 3.1 Manager Voids a Wrong Collection***

1. The Manager selects Void on a confirmed collection other than the booking-time collection.
2. The system checks that the collection is not cash counted into a submitted cash close.
3. The use case continues at step 4.

***Step 3.2 Manager Reverses Cash of a Closed Cash Day***

1. The Manager selects Reverse on a confirmed cash collection of a closed cash day.
2. The Manager enters a reason and confirms.
3. The system checks that the folio is open and the collection was not reversed before.
4. The system checks that no refund request exists for that money.
5. The system records a negative cash entry dated the current working day.
6. The system recalculates the balance due and leaves the closed day unchanged (MSG242).
7. The use case ends after the reversal.

***Step 3.3 Manager Corrects an Overcharge on a Finalized Folio***

1. The Manager enters the ticket code, the overcharged amount and a reason.
2. The system checks that the folio is finalized and has no correction in progress.
3. The system checks that the amount does not exceed the unrefunded money collected for the ticket.
4. The system records the correction and opens a bank refund request for the amount.
5. The system keeps the folio finalized (MSG243).
6. The use case ends after the correction.

***Step 3.4 Cash Reversal Is Refused***

1. The system informs the Manager why the cash entry cannot be reversed (MSG244).
2. The system records no reversal.
3. The use case ends without a correction.

***Step 3.5 Overcharge Correction Is Refused***

1. The system informs the Manager why the folio cannot be corrected (MSG244).
2. The system records no correction and opens no refund request.
3. The use case ends without a correction.

***Step 5.1 Reason Is Missing***

1. The system shows that a reason is required (MSG235).
2. The system voids nothing.
3. The use case continues at step 5.

***Step 6.1 Actor Is Not a Manager***

1. The system refuses the action (MSG01).
2. The system changes nothing.
3. The use case ends without a correction.

***Step 6.2 Folio Is Not Open***

1. The system informs the Manager that a finalized or closed folio cannot be voided (MSG244).
2. The system changes nothing.
3. The use case continues at step 3.

***Step 6.3 Cash Belongs to a Closed Cash Day***

1. The system refuses to void cash counted into a submitted cash close (MSG241).
2. The system leaves the closed cash day unchanged.
3. The use case continues at step 3.

#### Postconditions

- The original line or collection is kept, marked as voided, with an audit record.
- A closed cash day is never edited; a reversal is a separate entry on the current day.
- An overcharge on a finalized folio is returned only through a bank refund request.
- An undercharge found after finalization is never collected from the customer.

#### Business Rule

BR-128, BR-129, BR-130, BR-143
