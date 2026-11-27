### 3.20 UC-51 Correct Attendance

#### UC Name

UC-51 Correct Attendance

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to correct the attendance of a ticket after the session is finalized, or reverse an erroneous check-in before, so that attendance records are accurate.

#### Preconditions

- The actor is signed in as an active Manager.
- The workshop session has attendance finalized.
- The ticket belongs to the session and is active.

#### Normal Flow

***Correct Finalized Attendance***

1. The Manager opens the detail of a workshop session.
2. The system shows the attendance roster of the session.
3. The Manager opens Xử lý ngoại lệ and enters the ticket code.
4. The Manager selects a reason code from the fixed catalogue.
5. The Manager enters a reason note and evidence reference.
6. The Manager selects Đánh dấu đã check-in.
7. The system checks that the actor is an active Manager.
8. The system checks that the session is finalized and the reason code is recognised.
9. The system checks that the ticket is active and entitled to the session.
10. The system checks that the 24-hour correction window of the ticket is still open.
11. The system saves the corrected attendance, appends a correction record and writes an audit event.
12. The system confirms that the attendance has been recorded (MSG334).

#### Alternative Flows

***Step 6.1 Manager Reverses an Erroneous Check-in***

1. The Manager selects Hoàn tác check-in.
2. The system checks that the actor is an active Manager.
3. The system checks that the session is not finalized or cancelled and the ticket is checked in.
4. The system returns the ticket to not checked in and clears its check-in time.
5. The system appends a reversal record and writes an audit event.
6. The system confirms that the reversal has been recorded (MSG334).
7. The use case ends after the reversal.

***Step 6.2 Ticket Is Not in the Required State***

1. The system informs the Manager that the action is not allowed in the current state (MSG335).
2. The system saves nothing.
3. The use case ends without a change.

***Step 7.1 Actor Is Not an Active Manager***

1. The system refuses the action (MSG01).
2. The system records the denied attempt in the audit log without personal data.
3. The use case ends without a change.

***Step 8.1 Reason Is Missing or Not Valid***

1. The system informs the Manager that a valid reason is required (MSG320).
2. The system saves nothing.
3. The use case continues at step 4.

***Step 8.2 Session Is Not Finalized***

1. The system informs the Manager that the action is not allowed in the current state (MSG335).
2. The system saves nothing.
3. The use case ends without a change.

***Step 9.1 Ticket Cannot Be Corrected***

1. The system informs the Manager that the ticket was not found, is not active or is not entitled to the session (MSG337).
2. The system saves nothing.
3. The use case continues at step 3.

***Step 10.1 Correction Window Has Closed***

1. The system informs the Manager that the correction window has closed (MSG336).
2. The system saves nothing.
3. The use case ends without a change.

***Step 10.2 Ticket Already Has the Chosen Attendance***

1. The system finds that the ticket already has the chosen attendance.
2. The system records nothing new.
3. The system confirms the attendance (MSG334).
4. The use case ends.

#### Postconditions

- The ticket shows the corrected attendance, or is back to not checked in after a reversal.
- The original actions stay in the history and the correction is appended.
- No refund, settlement or customer notification is triggered.

#### Business Rule

BR-171, BR-174, BR-185, BR-193, BR-194
