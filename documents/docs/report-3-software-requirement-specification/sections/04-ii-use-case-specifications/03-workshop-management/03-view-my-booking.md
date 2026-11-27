### 3.3 UC-10 View My Booking

#### UC Name

UC-10 View My Booking

#### Primary Actors

Guest, Member

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Guest or Member, I want to open my booking group so that I can see its tickets, their status and amounts, and the check-in QR codes.

#### Preconditions

- A booking group exists.
- A Guest holds the booking code with one stored contact value, or the group Magic Link.
- A Member is signed in and owns the booking group.

#### Normal Flow

***View My Booking***

1. The actor opens the booking lookup page.
2. The actor enters the booking code and the email or phone number used at booking.
3. The system verifies the booking code with exactly one stored contact value.
4. The system shows the booking group with its status and the amount paid at booking.
5. The system shows each ticket with package, sessions, price, amounts paid, balance due, status, attendance and masked recipient.
6. The system shows the check-in QR code of each confirmed ticket.
7. The system confirms that the booking group has been found and displayed.

#### Alternative Flows

***Step 1.1 Member Opens My Bookings***

1. The Member opens My bookings while signed in.
2. The system lists up to 30 of the Member's own booking groups, newest first.
3. The Member opens one booking group.
4. The system verifies that the Member owns the booking group.
5. The use case continues at step 4.

***Step 1.2 My Bookings Cannot Be Loaded***

1. The system informs the Member that the bookings could not be loaded (MSG05).
2. The use case ends without showing a booking group.

***Step 1.3 Actor Opens the Magic Link***

1. The actor opens the Magic Link from the confirmation email.
2. The system verifies that the Magic Link is active.
3. The system shows the read-only group view without hold, resend or ticket actions.
4. The use case continues at step 4.

***Step 1.4 Magic Link Is Expired or Revoked***

1. The system informs the actor that the Magic Link has expired or been revoked (MSG164).
2. The system offers the booking lookup page instead.
3. The use case continues at step 1.

***Step 2.1 Actor Gives Both or Neither Contact Value***

1. The system asks for exactly one contact value (MSG163).
2. The use case continues at step 2.

***Step 3.1 Proof Does Not Match***

1. The system informs the actor that no booking group matches the details, without naming the wrong value (MSG165).
2. The use case continues at step 2.

***Step 3.2 Too Many Lookup Attempts***

1. The system refuses further lookups of this booking code for a while (MSG166).
2. The use case ends without showing a booking group.

***Step 3.3 Member Session Has Expired***

1. The system asks the Member to sign in again (MSG02).
2. The use case ends without showing a booking group.

***Step 4.1 Group Is Awaiting Payment***

1. The system shows the payment countdown and the Pay button of UC-09 Pay Workshop Deposit.
2. The actor selects Release hold.
3. The actor enters a reason and confirms the release.
4. The system releases the seats, the held voucher use and the held points of every ticket.
5. The system informs the actor that the booking group has been cancelled (MSG167).
6. The use case ends with the booking group cancelled.

***Step 4.2 Release Is Refused During a Live Payment***

1. The system refuses the release because a payment attempt is still in progress (MSG168).
2. The system keeps the hold until the payment result or the hold expiry.
3. The use case continues at step 4.

***Step 4.3 Group Has Expired or Been Cancelled***

1. The system shows that the held seats have been released (MSG162).
2. The system shows no QR code and no payment action.
3. The use case ends.

***Step 6.1 Actor Requests the Confirmation Email Again***

1. The actor selects Resend confirmation email.
2. The system sends the confirmation with every ticket to the stored contact email through the Mail Gateway.
3. The system informs the actor that the resend request was received (MSG169).
4. The use case continues at step 7.

***Step 6.2 Resend Limit Is Reached***

1. The system refuses the resend for the rest of the hour (MSG170).
2. The use case continues at step 7.

#### Postconditions

- The actor sees only the booking group that the proof allows.
- Viewing changes no booking, payment, seat or ticket.
- A released hold returns every seat, voucher use and point of the group.

#### Business Rule

BR-04, BR-85, BR-86, BR-87, BR-88, BR-89, BR-90
