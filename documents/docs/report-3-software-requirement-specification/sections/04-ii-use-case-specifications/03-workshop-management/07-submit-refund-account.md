### 3.7 UC-14 Submit Refund Account

#### UC Name

UC-14 Submit Refund Account

#### Primary Actors

Guest, Member

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Guest or Member payer, I want to enter and confirm my receiving bank account so that Mland can transfer my workshop refund.

#### Preconditions

- A workshop refund request is waiting for a bank account.
- The payer holds an emailed refund-account link that is unused, not replaced and not expired.

#### Normal Flow

***Submit Refund Account***

1. The actor opens the refund-account link from the refund email.
2. The system shows the bank account form.
3. The actor enters the bank, the account number and the account holder name.
4. The actor selects Review details.
5. The system shows the bank, account number and holder name back to the actor.
6. The actor confirms that the details are correct.
7. The actor sends the account.
8. The system checks that the link is still usable and the request still waits for an account.
9. The system stores the account number encrypted and keeps only its mask for display.
10. The system marks the link as used and moves the request to pending approval.
11. The system confirms that the bank account was received (MSG186).

#### Alternative Flows

***Step 1.1 Staff or Manager Enters the Account at the Counter***

1. The Staff or Manager opens the refund request of the ticket.
2. The system verifies the operator's assignment to a session of the ticket.
3. The Staff or Manager enters the payer's bank, account number and holder name.
4. The system shows the details back for the payer to check.
5. The Staff or Manager confirms that the payer agreed the details are correct.
6. The system stores the account encrypted, records actor, channel and time, and revokes every active link.
7. The system confirms that the account was recorded (MSG187).
8. The use case ends.

***Step 1.2 Refund Has No Ticket***

1. The system refuses counter entry for a group-level refund request (MSG01).
2. The use case continues at step 1, through the emailed link only.

***Step 1.3 Link Is Unknown or Incomplete***

1. The system informs the actor that the link is not valid (MSG185).
2. The use case ends without saving an account.

***Step 6.1 Actor Wants to Correct the Details***

1. The actor selects Edit.
2. The use case continues at step 3.

***Step 8.1 Link Was Used, Replaced or Expired***

1. The system refuses the link and asks the actor to request a new link from the shop (MSG184).
2. The use case ends without saving an account.

***Step 8.2 Same Submission Is Sent Again***

1. The system returns the stored result without a second write or email.
2. The use case continues at step 11.

#### Postconditions

- The refund request holds an encrypted account and waits for Manager approval.
- The full account number never appears in email, logs or audit.
- The refund-account link can no longer be used.

#### Business Rule

BR-134, BR-136, BR-100, BR-101
