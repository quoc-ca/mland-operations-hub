### 3.26 Respond to Mland Cancellation Offer

#### Primary Actors

Guest, Member

#### Secondary Actors

Mail Gateway

#### Description

As the Guest or Member buyer of a ticket affected by an Mland cancellation, I want to choose a replacement session or a refund so that I am not disadvantaged by the cancellation.

#### Preconditions

1. Mland cancelled a session of the ticket and a replacement offer is awaiting an answer.
2. The actor is the owning Member, or a Guest with a valid buyer verification.

#### Postconditions

- The ticket moves to the chosen session, or the refund path starts for all money collected for it.

#### Normal Flow

**Respond to Mland Cancellation Offer**

1. The buyer opens the offer announced by email.
2. The buyer views the equivalent sessions offered, each with its location.
3. The buyer chooses one session.
4. The system verifies the buyer and the session and moves the ticket without extra charge.

#### Alternative Flows

**Step 3 — Buyer chooses a refund**

A bank refund request opens for 100% of the money collected less other refunds of the ticket, used points are returned, and the group voucher is restored once if still valid.

**Step 3 — No answer by the deadline or no equivalent session**

The refund path opens automatically; the ticket is never moved without a choice.

**Step 4 — Session no longer valid or starts in under 24 hours**

The system refuses that choice.

#### Business Rules

BR-88-01, BR-88-02, BR-88-03, BR-88-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-88-01 | The answer deadline is 24 hours before the latest offered session. |
| BR-88-02 | A replacement does not count as the customer change. |
| BR-88-03 | A ticket with no money collected needs no refund request. |
| BR-88-04 | Unlike the matrix and spec, the current backend also accepts a Staff or Manager answer with buyer proof and opens no offer for a finalized folio; to be confirmed. |
