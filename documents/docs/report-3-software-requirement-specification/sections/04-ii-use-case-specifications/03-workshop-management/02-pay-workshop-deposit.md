### 3.2 UC-09 Pay Workshop Deposit

#### UC Name

UC-09 Pay Workshop Deposit

#### Primary Actors

Guest, Member

#### Secondary Actors

Payment Gateway (VNPay), Mail Gateway (SMTP)

#### Description

As a Guest or Member, I want to pay the booking-time amount of my booking group through VNPay so that the whole group is confirmed.

#### Preconditions

- The booking group is awaiting payment and its hold has not ended.
- The group collects at least 5,000 VND at booking.
- The 10-minute payment window of the group is still open.
- A Guest proves the group with its code and one stored contact; a Member owns the group.

#### Normal Flow

***Pay Workshop Deposit***

1. The actor requests to pay the booking group.
2. The system rechecks the group collection against the stored ticket amounts.
3. The system creates one VNPay payment for the whole group collection.
4. The system redirects the actor to the VNPay payment page.
5. The actor completes the payment on VNPay.
6. The Payment Gateway sends the signed payment result to the system.
7. The system verifies the signature, merchant reference, amount, currency and that the result is not a duplicate.
8. The system confirms the group, commits every seat hold and settles the held voucher and points.
9. The system issues each ticket's QR code and the group Magic Link.
10. The system opens one folio per ticket with the collected amount and the remaining balance.
11. The system sends the confirmation email with every ticket through the Mail Gateway.
12. The system confirms that the payment succeeded and lists the tickets (MSG160).

#### Alternative Flows

***Step 1.1 A Payment Attempt Is Already in Progress***

1. The system returns the same VNPay payment instead of creating a new one.
2. The use case continues at step 4.

***Step 2.1 Payment Window Has Closed***

1. The system informs the actor that the payment cannot be made (MSG157).
2. The system creates no payment and does not extend the hold.
3. The use case ends without confirming the booking group.

***Step 3.1 VNPay Payment Cannot Be Created***

1. The system informs the actor that the payment cannot be made now (MSG157).
2. The system creates no payment attempt and does not extend the hold.
3. The use case continues at step 1 while the payment window is open.

***Step 5.1 Actor Cancels or Abandons the Payment***

1. The system shows that the payment was not completed (MSG158).
2. The system marks the attempt failed when VNPay reports a definite failure.
3. The actor may pay again within the payment window or release the hold in UC-10 View My Booking.
4. The use case continues at step 1.

***Step 6.1 Browser Returns Before the Signed Result***

1. The system does not confirm the group from the browser redirect.
2. The system shows that it waits for the verified VNPay result (MSG159).
3. The use case continues at step 6.

***Step 6.2 No Signed Result Arrives in Time***

1. The system queries VNPay with a signed status query at minutes 10 and 11 of the hold.
2. The system shows that it is still waiting for the payment result (MSG161).
3. The use case continues at step 7 with a matching successful answer.

***Step 7.1 Signed Result Does Not Match the Group***

1. The system records the mismatching evidence and does not confirm the group.
2. The use case ends without confirming the booking group.

***Step 7.2 Signed Result Reports a Definite Failure***

1. The system marks the payment attempt failed and keeps the group awaiting payment.
2. The use case continues at step 1 while the payment window is open.

***Step 7.3 Hold Ends Without Verified Payment***

1. The system releases every seat, the voucher use and the points of the group.
2. The system marks every live payment attempt expired.
3. The system informs the actor that the hold has ended (MSG162).
4. The system sends a verified payment that arrives later to UC-58 Review Late Payment.
5. The use case ends without confirming the booking group.

***Step 8.1 Confirmation Cannot Complete Yet***

1. The system stores the verified payment and keeps the seats, voucher and points held.
2. The system retries the confirmation every minute while the hold lasts.
3. The system sends the payment to UC-58 Review Late Payment when the hold ends unconfirmed.
4. The use case continues at step 9 after a successful retry.

***Step 8.2 Group Was Already Confirmed***

1. The system records the second verified payment as excess money.
2. The system opens a bank refund for 100% of that payment and alerts the active Managers.
3. The use case ends with the booking group unchanged.

***Step 11.1 Confirmation Email Is Not Delivered***

1. The system keeps the group confirmed.
2. The system resends the email 5 minutes, 30 minutes and 2 hours after the failure.
3. The system alerts the active Managers to call the buyer after the last failure.
4. The use case continues at step 12.

#### Postconditions

- The verified payment is recorded once and linked to the booking group.
- Every ticket is confirmed with its own QR code, and the group has one Magic Link.
- Each ticket has an open folio with its collected amount and balance due.
- A failed, pending or expired payment never confirms the group and never extends the hold.

#### Business Rule

BR-02, BR-03, BR-04, BR-82, BR-83, BR-84
