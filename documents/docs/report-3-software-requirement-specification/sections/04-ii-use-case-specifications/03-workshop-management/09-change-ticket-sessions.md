### 3.9 UC-16 Change Ticket Sessions

#### UC Name

UC-16 Change Ticket Sessions

#### Primary Actors

Guest, Member, Staff, Manager

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a buyer, assigned Staff member or Manager, I want to move a ticket to valid replacement sessions so that the participant can attend at another time.

#### Preconditions

- The ticket is confirmed and its folio is open.
- The session being changed has not started, except for a late-arrival change.
- A Member owns the booking group; a Guest has opened the group by booking lookup.
- A Staff member is assigned to a session of the ticket at a location in scope.

#### Normal Flow

***Change Ticket Sessions***

1. The actor selects Change schedule on a ticket.
2. The actor chooses the session to change, or both sessions together.
3. The system lists the valid replacement sessions with location, date, time, seats left and changes left.
4. The actor selects a replacement session.
5. The actor confirms the schedule change.
6. The system verifies that the actor is the buyer of the booking group.
7. The system rechecks capacity, reschedule quota, package rules, change window and the ticket's change allowance.
8. The system moves the ticket to the new sessions and uses the ticket's counted change.
9. The system keeps the ticket code and QR code unchanged.
10. The system emails the old and new schedule to the buyer and the recipient through the Mail Gateway.
11. The system confirms that the schedule was changed (MSG189).

#### Alternative Flows

***Step 1.1 Staff or Manager Changes the Ticket for the Buyer***

1. The Staff or Manager enters the group code, the ticket and a reason.
2. The Staff or Manager chooses an advance change or a late-arrival change.
3. The system sends a verification code to the group contact email through the Mail Gateway.
4. The Staff or Manager enters the code that the buyer reads out.
5. The system verifies the code and the operator's location scope and session assignment.
6. The use case continues at step 2.

***Step 1.2 Several Tickets of the Group Change Together***

1. The Staff or Manager adds each ticket and its replacement sessions to one change.
2. The system applies every ticket's change, or none of them.
3. The use case continues at step 9.

***Step 1.3 Session Has Already Started***

1. The system refuses an online change and asks the customer to contact the shop at its location (MSG193).
2. The use case ends without changing the ticket or using its change.

***Step 2.1 Customer Asks for a Late-Arrival Change***

1. The system refuses the request because only Staff of the location or a Manager may enter it (MSG01).
2. The use case ends without changing the ticket.

***Step 3.1 No Replacement Session Is Available***

1. The system informs the actor that no session can replace this one yet (MSG194).
2. The use case ends without changing the ticket.

***Step 3.2 Replacement Sessions Cannot Be Loaded***

1. The system informs the actor that the sessions could not be loaded (MSG05).
2. The use case continues at step 2.

***Step 4.1 Change Raises the Ticket Price***

1. The system shows the new schedule and the price increase (MSG191).
2. The actor agrees to the price increase.
3. The system adds the increase to the ticket's balance due at check-in.
4. The use case continues at step 5.

***Step 5.1 Guest Must Verify as the Buyer***

1. The system sends a 6-digit code to the group contact email through the Mail Gateway.
2. The Guest enters the code.
3. The system verifies the code and issues a single-use buyer proof.
4. The use case continues at step 6.

***Step 5.2 Verification Code Is Wrong, Locked or Expired***

1. The system informs the Guest that the code is wrong, locked or expired (MSG171).
2. The use case continues at step 1 of Step 5.1.

***Step 7.1 Change Needs a Staff or Manager Decision***

1. The system stores the request in the change request queue instead of applying it.
2. The system informs the actor that the request waits for approval and the result will be emailed (MSG190).
3. The use case ends without moving the ticket yet.

***Step 7.2 Change Is Refused***

1. The system refuses the change and states the reason, such as a full session or a closed window (MSG192).
2. The system keeps the ticket unchanged and uses no change allowance.
3. The use case continues at step 3.

#### Postconditions

- The ticket holds the new sessions, or it is unchanged.
- An applied customer change uses the ticket's one counted change.
- The ticket price never decreases and no refund is created.
- The ticket code and QR code stay valid.

#### Business Rule

BR-131, BR-186, BR-91, BR-92, BR-94, BR-104, BR-105, BR-106
