### 3.17 UC-48 Manage Workshop Schedule

#### UC Name

UC-48 Manage Workshop Schedule

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to create workshop sessions singly or in bulk with their capacity, and publish, pause or cancel them so that customers can book accurate branch schedules.

#### Preconditions

- The actor is signed in as an active Manager.
- The location has at least one active time slot.

#### Normal Flow

***Create and Publish a Workshop Session***

1. The Manager opens the Sessions tab of workshop operations administration.
2. The system shows the sessions with their date, time, location, capacity and state.
3. The Manager selects a location, a slot, a workshop date and a capacity.
4. The Manager selects New session.
5. The system checks that the slot belongs to the location and the capacity is at least 1.
6. The system checks that the session starts in the future and at most 90 days ahead.
7. The system checks that no session exists for the same location, date and slot.
8. The system creates the session as Draft, with check-in opening 60 minutes before the start.
9. The system assigns the active Staff of the location and records the creation in the audit log.
10. The system confirms that the session has been created (MSG312).
11. The Manager chooses Published as the target state of the session and selects Apply.
12. The system checks that the session may move to Published.
13. The system publishes the session for booking and records the transition in the audit log.
14. The system confirms that the session state has been updated (MSG316).

#### Alternative Flows

***Step 3.1 Manager Generates Sessions in Bulk***

1. The Manager selects a location, its active slots, a date range, weekdays, a capacity and an initial state.
2. The Manager selects Generate sessions.
3. The system checks the inputs and that the range ends at most 90 days ahead.
4. The system creates one independent session for every missing future date, weekday and slot combination.
5. The system skips every existing combination without changing it.
6. The system reports the number of sessions created and skipped (MSG312).
7. The use case ends after the generation.

***Step 3.2 Manager Changes Capacity or Check-in Opening Time***

1. The Manager selects Edit on a session.
2. The Manager enters a new capacity or check-in opening time and selects Save session.
3. The system checks the capacity against the confirmed seats and unexpired payment holds.
4. The system checks that the session has not started when the opening time changes.
5. The system saves the values and records each change in the audit log.
6. The system confirms that the session has been updated (MSG316).
7. The use case ends after the update.

***Step 3.3 Capacity Is Below Committed Seats***

1. The system informs the Manager that the capacity is below the committed seats (MSG317).
2. The system keeps the previous capacity.
3. The use case ends without a change.

***Step 3.4 Check-in Opening Time Is Locked***

1. The system informs the Manager that the opening time can no longer change (MSG323).
2. The system keeps the previous opening time.
3. The use case ends without a change.

***Step 3.5 Session Was Changed by Another User***

1. The system informs the Manager that the session changed after it was opened (MSG03).
2. The system saves nothing and offers to load the latest session.
3. The use case ends without a change.

***Step 3.6 Manager Cancels a Session with Customer Commitments***

1. The Manager opens the session and selects a cancellation reason code and a note.
2. The Manager selects Request cancellation.
3. The system asks the Manager to confirm the cancellation (MSG318).
4. The Manager confirms the cancellation.
5. The system checks that the session is not finalized and not already cancelled.
6. The system finds confirmed tickets or unexpired payment holds for the session.
7. The system moves the session to Cancellation Requested and stops sales and check-in.
8. The system hands every affected booking over to settlement and records the request in the audit log.
9. The system confirms that the cancellation request has been submitted (MSG319).
10. The use case ends after the request.

***Step 3.7 Session Has No Customer Commitment***

1. The system finds no confirmed ticket and no unexpired payment hold for the session.
2. The system moves the session directly to Cancelled and records it in the audit log.
3. The system confirms that the cancellation has been submitted (MSG319).
4. The use case ends after the cancellation.

***Step 3.8 Cancellation Reason Is Incomplete***

1. The system shows that a valid reason code, and a note when required, must be entered (MSG320).
2. The system does not cancel the session.
3. The use case ends without a change.

***Step 3.9 Session Cannot Be Cancelled***

1. The system informs the Manager that a finalized or already cancelled session cannot be cancelled (MSG322).
2. The system keeps the session state.
3. The use case ends without a change.

***Step 3.10 Manager Sets the Reschedule Quota***

1. The Manager opens the session, enters a new quota and a reason, and selects Set quota.
2. The system checks that the session is not finalized or cancelled.
3. The system saves the quota and records it with the reason in the audit log.
4. The system confirms that the quota has been updated (MSG321).
5. The use case ends after the update.

***Step 5.1 Session Input Is Invalid***

1. The system shows which session values are missing or invalid (MSG313).
2. The system creates no session.
3. The use case continues at step 3.

***Step 5.2 Actor Is Not an Active Manager***

1. The system refuses the action (MSG01).
2. The system changes no session.
3. The use case ends without a change.

***Step 6.1 Session Start Is Outside the Allowed Window***

1. The system informs the Manager that the session must start in the future and within 90 days (MSG314).
2. The system creates no session.
3. The use case continues at step 3.

***Step 7.1 Session Already Exists***

1. The system informs the Manager that a session already exists for that location, date and slot (MSG315).
2. The system creates no session.
3. The use case continues at step 3.

***Step 11.1 Manager Pauses or Resumes Sales***

1. The Manager chooses Sales Paused for a published session, or Published for a paused one.
2. The system moves the session and records the transition in the audit log.
3. The system confirms that the session state has been updated (MSG316).
4. The use case ends after the transition.

***Step 12.1 Transition Is Not Allowed***

1. The system informs the Manager that the session cannot move to that state (MSG322).
2. The system keeps the current state.
3. The use case ends without a change.

#### Postconditions

- Each session is unique for its location, date and slot and keeps the slot times it was created with.
- Only a Published session accepts new bookings; a paused session still lets earlier holds be paid.
- A cancelled session with customer commitments is handed over to settlement, never silently released.
- Every applied change is kept in the audit log.

#### Business Rule

BR-171, BR-174, BR-179, BR-180, BR-181, BR-182, BR-183, BR-184, BR-185, BR-186, BR-187
