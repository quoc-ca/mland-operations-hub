### 3.4 UC-11 Send Ticket to Recipient

#### UC Name

UC-11 Send Ticket to Recipient

#### Primary Actors

Guest, Member

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Guest or Member buyer, I want to send a ticket to the person who will attend so that they can check in with their own QR code.

#### Preconditions

- The booking group is confirmed and the ticket is confirmed.
- The ticket has not been checked in.
- A Member owns the booking group; a Guest has opened the group by booking lookup.

#### Normal Flow

***Send Ticket to Recipient***

1. The actor selects Send the ticket to a companion on a ticket.
2. The system shows the recipient email field and the online recipient changes left.
3. The actor enters the recipient email.
4. The actor selects Send ticket.
5. The system verifies that the actor is the buyer of the booking group.
6. The system records the recipient email of the ticket.
7. The system sends the ticket with its QR code to the recipient email through the Mail Gateway.
8. The system confirms that the ticket was sent to the recipient email (MSG172).

#### Alternative Flows

***Step 1.1 Staff or Manager Acts for the Buyer at the Counter***

1. The Staff or Manager enters the ticket, the recipient email and a reason.
2. The system sends a verification code to the group contact email through the Mail Gateway.
3. The Staff or Manager enters the code that the buyer reads out.
4. The system verifies the code and the operator's location scope and session assignment.
5. The use case continues at step 6, and the change is not counted as an online change.

***Step 4.1 Guest Must Verify as the Buyer***

1. The system asks the Guest to verify as the buyer.
2. The Guest requests a verification code.
3. The system sends a 6-digit code to the group contact email through the Mail Gateway.
4. The Guest enters the code.
5. The system verifies the code and issues a single-use buyer proof.
6. The use case continues at step 5.

***Step 4.2 Verification Code Is Wrong, Locked or Expired***

1. The system informs the Guest that the code is wrong, locked or expired (MSG171).
2. The use case continues at step 2 of Step 4.1.

***Step 5.1 Actor Is Not the Buyer***

1. The system refuses the request because only the buyer may send the ticket (MSG175).
2. The use case ends without changing the recipient.

***Step 6.1 Same Recipient Email Is Entered Again***

1. The system resends the ticket with the same QR code to that email.
2. The system does not count a recipient change.
3. The use case continues at step 8.

***Step 6.2 Recipient Email Is Changed***

1. The system revokes the old QR code of the ticket.
2. The system issues a new QR code for the ticket.
3. The system counts one online recipient change.
4. The system informs the old recipient email that the ticket no longer works for it.
5. The use case continues at step 7.

***Step 6.3 Online Change Limit Is Reached***

1. The system refuses the change because the ticket has used all 3 online changes (MSG173).
2. The system asks the buyer to contact staff at the store.
3. The use case ends without changing the recipient.

***Step 6.4 Ticket Is Not Confirmed or Is Checked In***

1. The system refuses the change because the ticket is not confirmed or is already checked in (MSG174).
2. The use case ends without changing the recipient.

#### Postconditions

- The ticket is linked to the recipient email and its valid QR code was emailed there.
- After a change of recipient, only the newest QR code of the ticket can be used.
- The audit keeps the old and new recipient emails masked.
- The recipient gets ticket access only, not the buyer's management rights.

#### Business Rule

BR-91, BR-92, BR-93, BR-94
