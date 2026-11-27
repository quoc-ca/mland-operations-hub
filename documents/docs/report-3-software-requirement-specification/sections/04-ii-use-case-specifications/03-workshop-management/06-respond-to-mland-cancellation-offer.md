### 3.6 UC-13 Respond to Mland Cancellation Offer

#### UC Name

UC-13 Respond to Mland Cancellation Offer

#### Primary Actors

Guest, Member

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Guest or Member buyer whose session Mland cancelled, I want to choose a replacement session or a refund so that I lose nothing by the cancellation.

#### Preconditions

- Mland cancelled a session of the ticket.
- The ticket has a replacement offer waiting for an answer.
- The answer deadline of the offer has not passed.
- A Member owns the booking group; a Guest opens the offer from the emailed link.

#### Normal Flow

***Respond to Mland Cancellation Offer***

1. The actor opens the replacement offer of the ticket.
2. The system shows the answer deadline and each offered session with its location, date and time.
3. The actor chooses one offered session.
4. The actor sends the answer.
5. The system asks the actor to confirm that the answer is final (MSG182).
6. The actor confirms the answer.
7. The system verifies that the actor is the buyer of the booking group.
8. The system checks that the chosen session still has a seat and starts at least 24 hours later.
9. The system moves the cancelled session of the ticket to the chosen session without extra charge.
10. The system records the answer, actor, channel and time.
11. The system confirms that the new session is booked (MSG183).

#### Alternative Flows

***Step 1.1 Offer Cannot Be Found***

1. The system informs the actor that no offer exists for this ticket (MSG180).
2. The use case ends.

***Step 1.2 Staff or Manager Submits the Buyer's Choice at the Counter***

1. The Staff or Manager opens the offer of the ticket.
2. The system sends a verification code to the group contact email through the Mail Gateway.
3. The Staff or Manager enters the code that the buyer reads out.
4. The system verifies the code and the operator's location scope.
5. The use case continues at step 2.

***Step 3.1 Buyer Chooses a Refund***

1. The actor chooses to refund the ticket in full to a bank account.
2. The actor confirms the answer.
3. The system verifies that the actor is the buyer of the booking group.
4. The system opens a bank refund request for 100% of the money collected for the ticket.
5. The system returns the ticket's used points.
6. The system restores the group voucher use once, if the voucher is still valid.
7. The system sends the refund-account link to the payer through the Mail Gateway.
8. The system confirms that the refund request was recorded (MSG183).
9. The use case ends.

***Step 3.2 Ticket Had No Money Collected***

1. The system records the refund answer and closes the ticket folio without a refund request.
2. The use case ends.

***Step 6.1 Guest Must Verify as the Buyer***

1. The system sends a 6-digit code to the group contact email through the Mail Gateway.
2. The Guest enters the code.
3. The system verifies the code and issues a single-use buyer proof.
4. The use case continues at step 7.

***Step 6.2 Verification Code Is Wrong, Locked or Expired***

1. The system informs the Guest that the code is wrong, locked or expired (MSG171).
2. The use case continues at step 1 of Step 6.1.

***Step 7.1 Actor Is Not the Buyer***

1. The system refuses the answer because only the buyer may choose (MSG01).
2. The use case ends without changing the ticket.

***Step 8.1 Chosen Session Is No Longer Available***

1. The system refuses the choice because the session is full, not offered or no longer sold there (MSG181).
2. The use case continues at step 3.

***Step 8.2 Chosen Session Starts Within 24 Hours***

1. The system refuses the choice because the time to choose it has passed (MSG181).
2. The use case continues at step 3.

***Step 8.3 Offer Was Already Answered***

1. The system refuses a second answer and shows the recorded outcome (MSG183).
2. The use case ends.

***Step 8.4 Answer Deadline Passes Without an Answer***

1. The system records a refund answer by default at the deadline.
2. The use case continues at step 4 of Step 3.1.

#### Postconditions

- The ticket is moved to the chosen session, or its refund path is open.
- A replacement does not use the ticket's customer change.
- The recipient of the ticket is told of the cancellation but cannot choose.
- Mland is told when every affected ticket of the session is resolved.

#### Business Rule

BR-18, BR-132, BR-91, BR-92, BR-94, BR-97, BR-98, BR-99
