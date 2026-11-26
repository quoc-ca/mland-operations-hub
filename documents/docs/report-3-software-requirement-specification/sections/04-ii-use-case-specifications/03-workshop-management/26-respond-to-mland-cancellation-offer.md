### 3.21 Respond to Mland Cancellation Offer

| Use Case ID | UC-88 | Use Case Name | Respond to Mland Cancellation Offer |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member, Staff, Manager | Secondary Actors | Mail Gateway |

#### Description

As the buyer of a ticket affected by an Mland cancellation, I want to choose a replacement session or a refund so that I am not disadvantaged by the cancellation.

#### Trigger

An actor opens a replacement offer announced by email after Mland cancels a session.

#### Preconditions

1. Mland cancelled a session of the ticket and a replacement offer is awaiting an answer.
2. The actor is the owning Member, a verified Guest, or Staff or Manager with buyer verification.

#### Postconditions

1. The ticket moves to the chosen session, or the refund path starts for all money collected for it.

#### Normal Flow

1. Actor opens the replacement offer.
2. System displays the offered equivalent sessions with each location.
3. Actor chooses a replacement session or refund, with operator confirmation and booking-code verification when assisted.
4. System verifies the buyer proof and outcome, then moves the ticket without extra charge.

#### Alternative Flows

3a. Buyer chooses a refund → Bank refund opens for 100% of net collected money, points return, and valid group voucher restores once.

#### Exception Flows

3a. No answer by the deadline or no equivalent session → Refund path opens automatically; ticket never moves without a choice.

4a. Chosen session is invalid or starts in under 24 hours → System refuses that choice.

#### Business Rules

BR-88-01, BR-88-02, BR-88-03, BR-88-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-88-01 | The answer deadline is 24 hours before the latest offered session. |
| BR-88-02 | A replacement does not count as the customer change. |
| BR-88-03 | A ticket with no money collected needs no refund request. |
| BR-88-04 | The remedy applies to open and finalized bills; a finalized bill is closed only after its refund is completed. |
