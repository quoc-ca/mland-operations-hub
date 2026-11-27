### 3.13 UC-37 Close Branch Cash Day

#### UC Name

UC-37 Close Branch Cash Day

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member or a Manager, I want to submit the counted cash of a branch for a working day so that the day's workshop cash is closed and any difference is confirmed by a Manager.

#### Preconditions

- The actor is signed in as an active Manager, or as active Staff whose location scope includes the branch.
- The branch's working day has not been closed yet.

#### Normal Flow

***Close Branch Cash Day***

1. The actor opens the cash close screen.
2. The actor enters the branch and the working day.
3. The actor requests the expected cash.
4. The system shows the day's expected cash, net of change returned and cash reversals.
5. The actor enters the counted cash.
6. The actor submits the cash close.
7. The system checks the actor's branch scope, the working day and that the day is not closed.
8. The system compares the counted cash with the expected cash.
9. The system records the close as Closed with the closer and the time.
10. The system records the close in the audit log.
11. The system shows that later cash belongs to the next working day (MSG221).
12. The system confirms that the cash book has been closed (MSG220).

#### Alternative Flows

***Step 5.1 Counted Cash Differs from the Expected Cash***

1. The actor enters the counted cash and a difference reason.
2. The actor submits the cash close.
3. The system records the close as Awaiting Manager confirmation with the difference and the reason.
4. The system records the close in the audit log.
5. The system shows that a Manager must confirm the difference (MSG222).
6. The use case continues at step 11.

***Step 5.2 Difference Has No Reason***

1. The system refuses the close and asks for a difference reason (MSG223).
2. The system records no close.
3. The use case continues at step 5.

***Step 5.3 Manager Confirms a Cash Difference***

1. The Manager opens the list of differences awaiting confirmation.
2. The Manager selects to confirm one difference.
3. The system asks for a confirmation reason (MSG224).
4. The Manager enters the reason and confirms.
5. The system marks the close as Closed with the Manager, the reason and the time.
6. The system records the confirmation in the audit log.
7. The system confirms that the difference is confirmed and the day is closed (MSG225).
8. The use case ends.

***Step 5.4 Confirmation Reason Is Empty***

1. The system asks for a reason (MSG235).
2. The system keeps the close Awaiting Manager confirmation.
3. The use case ends without confirming the difference.

***Step 5.5 Staff Tries to Confirm a Difference***

1. The system refuses the confirmation (MSG01).
2. The system leaves the close Awaiting Manager confirmation.
3. The use case ends.

***Step 7.1 Actor Is Outside the Branch Scope***

1. The system refuses the cash close (MSG01).
2. The use case ends without closing the day.

***Step 7.2 Working Day Is in the Future***

1. The system refuses a close for a future working day (MSG226).
2. The use case continues at step 2.

***Step 7.3 Working Day Is Already Closed***

1. The system informs the actor that the day is already closed (MSG227).
2. The system keeps the submitted close unchanged.
3. The use case ends without a new close.

#### Postconditions

- The branch has one close record for the working day with expected, counted and difference amounts.
- A close with a difference stays Awaiting Manager confirmation until a Manager confirms it.
- Cash recorded after the submission belongs to the next working day.
- The submitted count is never edited.

#### Business Rule

BR-119, BR-120, BR-121, BR-129, BR-143
