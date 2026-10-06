### 3.4 Pay Workshop Deposit

#### Primary Actors

Guest, Member

#### Secondary Actors

Payment Gateway, Mail Gateway

#### Description

As a Guest or Member, I want to pay the required workshop deposit through VNPay so that my pending workshop booking can be confirmed after the system verifies a valid payment notification.

#### Preconditions

1. The platform is available.
2. A pending workshop booking and its package invoice have been created by UC17 Create Workshop Booking.
3. The booking has a valid temporary capacity hold.
4. The required deposit amount is available and equals 50% of the selected package price.
5. VNPay credentials and the payment endpoint are configured.

#### Normal Flow

Pay Workshop Deposit
1. The actor reviews the pending booking and deposit amount.
2. The actor starts payment for the workshop deposit.
3. The system creates a VNPay payment request for the required 50% deposit and redirects the actor to VNPay.
4. The actor completes or cancels the payment on VNPay.
5. VNPay returns the payment result through the approved signed confirmation mechanism.
6. The system validates the signature, transaction amount, booking reference, payment status, and duplicate-event status while the capacity hold remains active.
7. The system records one verified deposit transaction.
8. The system marks the invoice deposit as paid and changes the booking status to confirmed.
9. The system generates the booking code and QR check-in ticket.
10. The system sends a booking confirmation notification to the actor's contact email.
11. The system displays the confirmed booking and payment result. If the actor is a Member, the booking, invoice, and transaction are saved in Member history. If the actor is a Guest, the system may offer optional Member registration.

#### Alternative Flows





Step 3 — Payment request cannot be created
The system displays an error and keeps the booking unconfirmed. The actor may retry while the capacity hold remains active.

Step 4 — Actor cancels or abandons payment
The system keeps the booking in payment-pending/unconfirmed status. The capacity hold remains only until its expiry time.

Step 6 — VNPay confirmation is invalid or the amount does not match
The system rejects the confirmation, records the failed/invalid result for audit purposes, and does not confirm the booking.

Step 6 — Browser Return URL is received without valid server-side confirmation
The system does not treat the redirect as payment confirmation. The booking remains unconfirmed until a valid signed IPN or approved signed recovery result is received.

Step 6 — Duplicate payment event
The system ignores the duplicate event and does not create a second transaction or alter the already recorded result.

Step 6 — Payment is pending or failed
The system displays the pending/failed status and keeps the booking unconfirmed. The actor may retry if the hold is still active.After 

Step 6 — Hold or payment link has expired
The system rejects late confirmation and releases the temporary capacity hold. The actor must create a new booking if they want to try again.

Step 10 — Confirmation notification cannot be sent
The booking remains confirmed and the system records the notification failure for retry or operational follow-up.

#### Postconditions

• If valid payment confirmation is received, exactly one deposit transaction is recorded, the invoice deposit is marked paid, and the booking is confirmed.• A booking reference code and QR check-in ticket are generated for a confirmed booking.• A confirmation notification is sent to the booking contact email.• If payment is pending, failed, expired, invalid, or duplicated, the booking remains unconfirmed and the capacity hold is released when its hold window expires.

#### Business Rules

BR-18-01, BR-18-02, BR-18-03, BR-18-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-18-01 | The workshop deposit is 50% of the selected package price and must be paid through the configured VNPay payment flow. |
| BR-18-02 | A workshop booking is confirmed only after one valid, signed, non-duplicate VNPay confirmation with the expected booking reference and amount is received. |
| BR-18-03 | A VNPay browser Return URL alone never confirms payment; the system requires the approved signed IPN or signed QueryDR recovery result. |
| BR-18-04 | The VNPay payment link expires after 10 minutes. The related workshop capacity hold lasts at most 15 minutes and is released without valid confirmation. |
