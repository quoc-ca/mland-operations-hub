### 3.24 Decide Ticket Change Request

#### Primary Actors

Staff, Manager

#### Secondary Actors

Mail Gateway

#### Description

As a Staff member of the location or a Manager, I want to approve or reject a queued ticket change request so that exceptional changes are decided and the customer is informed.

#### Preconditions

1. The actor is a Manager, or Staff whose location scope contains the request location.
2. A change request is pending and has not expired.

#### Postconditions

- The request is approved or rejected with the deciding account and reason.
- The customer is emailed the outcome.

#### Normal Flow

**Decide Ticket Change Request**

1. The actor opens the change request queue.
2. The actor reviews a request and enters a decision with a reason.
3. The system checks the actor scope and the request state.
4. On approval the system applies the change as one granted counted change.

#### Alternative Flows

**Step 2 — Request is rejected**

The ticket keeps its old schedule; the internal reason is not sent to the customer.

**Step 3 — Request expired or already decided**

The system refuses the decision; the customer is told when a request expires.

**Step 4 — Target location no longer sells the package**

The approval is refused, the change is not applied, and no allowance is used.

#### Business Rules

BR-86-01, BR-86-02, BR-86-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-86-01 | Staff decides only requests of locations in their scope; a Manager is global; Admin has no access. |
| BR-86-02 | An advance request expires at the earlier start of the changed session or a requested session. |
| BR-86-03 | A late-arrival request expires at the earlier of the end of the 7th calendar day or a requested session start. |
