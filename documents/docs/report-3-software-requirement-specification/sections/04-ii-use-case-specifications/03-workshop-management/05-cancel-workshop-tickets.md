### 3.5 UC-12 Cancel Workshop Tickets

#### UC Name

UC-12 Cancel Workshop Tickets

#### Primary Actors

Guest, Member

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Guest or Member buyer, I want to cancel one or more unused tickets of my booking group so that their seats are released.

#### Preconditions

- The booking group is confirmed.
- Each selected ticket is not cancelled, and its first session has not started.
- A Member owns the booking group; a Guest has opened the group by booking lookup.

#### Normal Flow

***Cancel Workshop Tickets***

1. The actor selects Cancel ticket on a ticket.
2. The system shows whether the cancellation is valid or late under the 24-hour rule (MSG177).
3. The actor enters a cancellation reason.
4. The actor confirms the cancellation.
5. The system verifies that the actor is the buyer of the booking group.
6. The system checks that every selected ticket can still be cancelled.
7. The system releases the seat of every future session of each ticket.
8. The system returns the used points of each ticket cancelled at least 24 hours before its first session.
9. The system closes each open ticket folio at the amount collected, without debt.
10. The system records the request time, 24-hour outcome, benefits returned, actor and channel.
11. The system confirms that the tickets were cancelled and shows each ticket's outcome (MSG176).

#### Alternative Flows

***Step 1.1 Staff or Manager Cancels for the Buyer at the Counter***

1. The Staff or Manager enters the group code, the ticket codes and a reason.
2. The system sends a verification code to the group contact email through the Mail Gateway.
3. The Staff or Manager enters the code that the buyer reads out.
4. The Staff or Manager confirms the cancellation of the listed tickets (MSG179).
5. The system verifies the code and that each ticket is in the operator's location scope.
6. The use case continues at step 6.

***Step 4.1 Guest Must Verify as the Buyer***

1. The system asks the Guest to verify as the buyer.
2. The system sends a 6-digit code to the group contact email through the Mail Gateway.
3. The Guest enters the code.
4. The system verifies the code and issues a single-use buyer proof.
5. The use case continues at step 5.

***Step 4.2 Verification Code Is Wrong, Locked or Expired***

1. The system informs the Guest that the code is wrong, locked or expired (MSG171).
2. The use case continues at step 2 of Step 4.1.

***Step 5.1 Actor Is Not the Buyer***

1. The system refuses the cancellation because a recipient or another account is not the buyer (MSG01).
2. The use case ends without cancelling any ticket.

***Step 6.1 A Selected Ticket Cannot Be Cancelled***

1. The system cancels none of the selected tickets.
2. The system informs the actor which tickets blocked the request (MSG178).
3. The use case ends without cancelling any ticket, and the buyer proof is not used up.

***Step 8.1 Cancellation Is Late***

1. The system treats the benefits of the ticket as a no-show and returns no points.
2. The use case continues at step 9.

***Step 8.2 Every Ticket of the Group Ended by Valid or Mland Cancellation***

1. The system restores the group voucher use once, if the voucher is still valid.
2. The system informs the actor whether the voucher was restored or had expired (MSG176).
3. The use case continues at step 9.

***Step 9.1 Ticket Folio Is Already Finalized***

1. The system keeps the finalized folio unchanged and refunds nothing.
2. The use case continues at step 10.

#### Postconditions

- Every selected ticket is cancelled, or none is.
- The seats of the cancelled tickets are released for other customers.
- The booking-time payment is not refunded.
- No other ticket of the group is changed.

#### Business Rule

BR-18, BR-91, BR-92, BR-94, BR-95, BR-96
