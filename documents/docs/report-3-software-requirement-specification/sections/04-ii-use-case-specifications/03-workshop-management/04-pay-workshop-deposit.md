### 3.4 Pay Workshop Deposit

#### Primary Actors

Guest, Member

#### Secondary Actors

Payment Gateway, Mail Gateway

#### Description

As a Guest or Member, I want to pay the booking-time amount of my booking group through VNPay so that the whole group is confirmed after the system verifies the payment.

#### Preconditions

1. The platform is available.
2. A booking group was created by UC 21 Create Workshop Booking and has an amount to collect of at least 5,000 VND.
3. The group hold is still active.
4. VNPay is configured.

#### Postconditions

- Verified evidence confirms the whole group, commits every hold, and issues each ticket QR code and the group Magic Link.
- Each ticket opens its own folio with the collected amount and the remaining balance.
- A failed, pending, or expired payment leaves the group unconfirmed.

#### Normal Flow

**Pay Workshop Deposit**

1. The actor reviews the group and the amount to collect.
2. The actor starts payment.
3. The system creates one VNPay payment for the whole group and redirects the actor.
4. The actor completes or cancels payment on VNPay.
5. The system receives a signed IPN, or checks the result through signed QueryDR at minutes 10 and 11.
6. The system validates signature, amount, reference, status, and duplicates.
7. The system confirms the group and issues the tickets.
8. The system sends one confirmation for each ticket and displays the result.

#### Alternative Flows

**Step 3 — Payment cannot be created or the payment window has closed**

The actor may retry only within the 10-minute payment window while no other attempt is live.

**Step 4 — Actor cancels or abandons payment**

The group stays unconfirmed until the hold expires. The actor may release the hold.

**Step 6 — Invalid evidence or wrong amount**

The system does not confirm the group and records the result. A signed definite failure marks the attempt as failed.

**Step 6 — Browser return without server-side evidence**

The redirect never confirms payment.

**Step 6 — Verified payment arrives after the hold expired**

The system confirms the group again automatically only when every late-payment condition holds (capacity, session timing, same amount, evidence within 60 minutes). Otherwise a Manager reviews it and either confirms the group or refunds it in full.

**Step 7 — Confirmation cannot complete yet**

The system retries the confirmation while the hold allows, then hands it to the late-payment review.

**Step 8 — Notification cannot be sent**

The group stays confirmed and the delivery failure is recorded for retry.

#### Business Rules

BR-41-01, BR-41-02, BR-41-03, BR-41-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-41-01 | One VNPay payment covers the whole group collection; a collection of 0 VND needs no VNPay. |
| BR-41-02 | The group is confirmed only by one valid, signed, non-duplicate payment result with the expected reference and amount. |
| BR-41-03 | A browser Return URL alone never confirms payment. |
| BR-41-04 | The VNPay link expires at minute 10 and the group hold lasts 12 minutes. |
