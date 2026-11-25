### 3.19 Manage Handover Exception and Debt

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to approve a handover before payment is verified and to record or close the resulting debt so that exceptions are controlled and traceable.

#### Preconditions

1. The actor is an active Manager.
2. The ticket has a folio that is not yet fully verified as paid.

#### Postconditions

- The exception or debt is recorded with the Manager, reason, and time.
- The balance and the folio state are not changed by the exception or the debt record.

#### Normal Flow

**Manage Handover Exception and Debt**

1. The Manager opens the ticket folio.
2. The Manager approves the handover exception and enters a reason.
3. The system records the exception and allows the product to be handed over.
4. The Manager records the outstanding debt with an amount and a reason.

#### Alternative Flows

**Step 2 — Folio already finalized**

No exception is needed because handover is already allowed.

**Step 4 — Debt amount is zero or above the balance due**

The system refuses the debt record.

**After Step 4 — Debt is followed up**

The Manager closes the recorded debt with a reason; the balance itself is settled by a collection.

#### Business Rules

BR-81-01, BR-81-02, BR-81-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-81-01 | Only a Manager may approve the exception, record a debt, or close it, and a reason is required. |
| BR-81-02 | A recorded debt never finalizes a folio. |
| BR-81-03 | There is no automatic reminder, deadline, or booking block. |
