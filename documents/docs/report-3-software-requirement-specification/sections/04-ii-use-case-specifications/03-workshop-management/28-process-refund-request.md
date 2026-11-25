### 3.28 Process Refund Request

#### Primary Actors

Manager

#### Secondary Actors

Mail Gateway

#### Description

As a Manager, I want to approve, record, confirm, or return a refund request so that money owed to the customer is paid by bank transfer and tracked.

#### Preconditions

1. The actor is an active Manager.
2. A refund request exists in a state that allows the selected action.

#### Postconditions

- Every step records the actor, time, and proof.
- Folio and booking consequences apply only after the request is refunded.

#### Normal Flow

**Process Refund Request**

1. The Manager reviews a request pending approval and approves it with a reason.
2. The Manager transfers the money outside the system.
3. The Manager records the bank reference, with an optional receipt image.
4. After matching the bank statement, the Manager confirms the request as refunded.

#### Alternative Flows

**Step 1, 3, or 4 — Refund refused, returned, or unmatched**

The Manager returns the request to pending account with a reason; the approval is void and a new link is sent.

**Before Step 1 — Account not yet received**

The Manager (or Staff, for the resend only) may resend the link; the Manager may close the request as unreachable after 30 days and reopen it later.

**Step 4 — Request already refunded**

Refunded is final; a late bank return is handled outside the system.

#### Business Rules

BR-90-01, BR-90-02, BR-90-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-90-01 | The system never calls VNPay Refund; there is no terminal rejection of a refund. |
| BR-90-02 | The total refunded or pending for a ticket never exceeds the money collected for it. |
| BR-90-03 | Staff may only resend the refund-account link; every refund decision stays with the Manager. |
