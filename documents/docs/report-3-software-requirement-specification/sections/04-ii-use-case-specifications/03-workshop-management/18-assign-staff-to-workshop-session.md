### 3.18 UC-49 Assign Staff to Workshop Session

#### UC Name

UC-49 Assign Staff to Workshop Session

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to assign or revoke the Staff of a workshop session so that every session is operated by the right people at its branch.

#### Preconditions

- The actor is signed in as an active Manager.
- The workshop session is Draft, Published, Sales Paused or In Progress.
- The branch of the session has at least one active Staff member.

#### Normal Flow

***Assign Staff to a Workshop Session***

1. The Manager opens the detail of a workshop session.
2. The system shows the session facts and the Staff currently assigned to it.
3. The Manager selects a Staff member of the session's branch in Phân công Staff.
4. The Manager selects Phân công.
5. The system checks that the actor is an active Manager.
6. The system checks that the session state allows Staff assignment.
7. The system checks that the account is an active Staff member of the session's branch.
8. The system saves the assignment and records it in the audit log.
9. The system confirms that the Staff assignment has been saved (MSG324).

#### Alternative Flows

***Step 2.1 System Assigns Branch Staff Automatically***

1. The system finds the active Staff of the branch who have no assignment record for the session.
2. The system assigns them and records each assignment in the audit log.
3. The system leaves every assignment revoked by a Manager unchanged.
4. The use case continues at step 2.

***Step 4.1 Manager Restores a Revoked Assignment***

1. The Manager selects a Staff member whose assignment was revoked earlier.
2. The Manager selects Phân công.
3. The use case continues at step 5.

***Step 4.2 Manager Removes a Staff Assignment***

1. The Manager selects Gỡ beside an assigned Staff member.
2. The system checks that the actor is an active Manager.
3. The system revokes the assignment, keeps its history and records the removal in the audit log.
4. The system confirms that the assignment has been removed (MSG325).
5. The use case ends after the removal.

***Step 4.3 Last Staff Assignment Needs Confirmation***

1. The system asks the Manager to confirm removing the last Staff assignment of a session whose check-in has opened (MSG326).
2. The system records the refused attempt in the audit log.
3. The Manager confirms the removal.
4. The system revokes the assignment and records that the last assignment was confirmed.
5. The system confirms that the assignment has been removed (MSG325).
6. The use case ends after the removal.

***Step 4.4 Manager Declines the Confirmation***

1. The Manager selects Hủy in the confirmation.
2. The system keeps the assignment.
3. The use case ends without a change.

***Step 5.1 Actor Is Not an Active Manager***

1. The system refuses the action (MSG01).
2. The system records the denied attempt in the audit log without personal data.
3. The use case ends without a change.

***Step 6.1 Session State Does Not Allow Assignment***

1. The system informs the Manager that Staff cannot be assigned in the current session state (MSG327).
2. The system saves nothing.
3. The use case ends without a change.

***Step 7.1 Staff Member Is Not Eligible***

1. The system informs the Manager why the account cannot be assigned (MSG328).
2. The system saves nothing.
3. The use case continues at step 3.

#### Postconditions

- The Staff member is assigned to, or revoked from, the session.
- An assignment revoked by a Manager is never re-added automatically.
- Every Manager change is recorded in the audit log.

#### Business Rule

BR-171, BR-174, BR-187, BR-188, BR-189
