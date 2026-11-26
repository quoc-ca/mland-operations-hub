### 3.4 Pay Workshop Deposit

| Use Case ID | UC-22 | Use Case Name | Pay Workshop Deposit |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | Payment Gateway, Mail Gateway |

#### Description

As a Guest or Member, I want to pay the booking-time amount of my booking group through VNPay so that the whole group is confirmed after verification.

#### Trigger

An actor starts payment for a held booking group with an amount due of at least 5,000 VND.

#### Preconditions

1. The platform is available.
2. A booking group was created by UC-21 with at least 5,000 VND to collect.
3. The group hold is still active.
4. VNPay is configured.

#### Postconditions

1. Verified evidence confirms the group, commits every hold, and issues each ticket QR code and the group Magic Link.
2. Each ticket opens its own folio with the collected amount and remaining balance.
3. A failed, pending, or expired payment leaves the group unconfirmed.

#### Normal Flow

1. Actor reviews the booking group and the amount to collect.
2. Actor starts payment.
3. System creates one VNPay payment for the whole group and redirects the actor.
4. Actor completes payment on VNPay.
5. System receives a signed IPN or checks via signed QueryDR at minutes 10 and 11.
6. System validates signature, amount, reference, status, and duplicates.
7. System confirms the group and issues the tickets.
8. System sends one confirmation for each ticket and displays the result.

#### Alternative Flows

None.

#### Exception Flows

3a. Payment creation fails or the 10-minute payment window closed → Actor may retry within the window while no other attempt is live.

4a. Actor cancels or abandons payment → Group stays unconfirmed until hold expiry; actor may release the hold.

6a. Evidence is invalid or amount mismatches → System leaves the group unconfirmed and marks definite signed failures as failed.

6b. Browser returns without server-side evidence → System never confirms payment from a Return URL redirect alone.

6c. Verified payment arrives after hold expiry → System auto-confirms only if all late-payment rules pass; otherwise Manager confirms or refunds.

7a. Confirmation cannot complete yet → System retries while the hold allows, then routes to late-payment review.

8a. Confirmation email fails to send → Group remains confirmed and the system records the delivery failure for retry.

#### Business Rules

BR-41-01, BR-41-02, BR-41-03, BR-41-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-41-01 | One VNPay payment covers the whole group collection; a collection of 0 VND needs no VNPay. |
| BR-41-02 | The group is confirmed only by one valid, signed, non-duplicate payment result with the expected reference and amount. |
| BR-41-03 | A browser Return URL alone never confirms payment. |
| BR-41-04 | The VNPay link expires at minute 10 and the group hold lasts 12 minutes. |
