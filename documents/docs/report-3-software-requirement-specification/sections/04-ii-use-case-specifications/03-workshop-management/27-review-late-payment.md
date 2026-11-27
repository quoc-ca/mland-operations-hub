### 3.27 UC-58 Review Late Payment

#### UC Name

UC-58 Review Late Payment

#### Primary Actors

Manager

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Manager, I want to decide a VNPay payment that arrived after the booking hold expired so that the payer either gets the booking or a full bank refund.

#### Preconditions

- The actor is signed in as an active Manager.
- A verified late VNPay payment was not re-confirmed automatically and waits for a Manager.

#### Normal Flow

***Refund a Late Payment***

1. The Manager opens the late payments queue.
2. The system lists the waiting reviews by earliest session start.
3. The system shows each amount, the failed conditions and the response deadline.
4. The Manager chooses a standard decision reason and adds an optional note.
5. The Manager selects to open a full refund for a review.
6. The system checks that the review still waits for a decision.
7. The system opens a group bank refund request for the whole verified amount.
8. The system sends the payer a link to enter the refund account.
9. The system records the decision, reason, Manager and time in the audit log.
10. The system confirms that the refund request has been opened (MSG251).

#### Alternative Flows

***Step 4.1 Reason Needs a Note***

1. The Manager chooses the "other reason" option.
2. The system does not allow a decision until the Manager enters a note.
3. The use case continues at step 4.

***Step 4.2 Manager Escalates the Payment***

1. The Manager chooses the reason for a payment dispute handed to accounting.
2. The system keeps the reason and note for the decision.
3. The use case continues at step 5.

***Step 5.1 Manager Confirms the Late Payment***

1. The Manager selects to confirm the late payment.
2. The system checks that only the arrival-window or duplicate-booking condition failed.
3. The system checks that capacity, start time and amount conditions still hold.
4. The system holds the seats again and confirms the booking group.
5. The system records the decision with the Manager, reason and time (MSG251).
6. The use case ends with the group confirmed.

***Step 5.2 Confirmation Is Not Allowed***

1. The system informs the Manager that the payment cannot be confirmed (MSG252).
2. The system keeps the review waiting.
3. The use case continues at step 5.

***Step 6.1 Actor Is Not a Manager***

1. The system refuses the decision (MSG01).
2. The system records the denied attempt.
3. The use case ends without a decision.

***Step 6.2 Review Was Already Decided***

1. The system informs the Manager that the review has been decided (MSG03).
2. The system changes nothing.
3. The use case ends without a decision.

***Step 7.1 Refund Request Cannot Be Opened***

1. The system informs the Manager that the refund could not be opened (MSG253).
2. The system keeps the review waiting.
3. The use case ends without a decision.

#### Postconditions

- The review has exactly one decision with its reason, Manager and time.
- A refunded late payment never revives the booking group.
- The late money is never moved to another booking.
- A confirmed group has its seats held and its tickets issued.

#### Business Rule

BR-02, BR-03, BR-04, BR-138, BR-132
