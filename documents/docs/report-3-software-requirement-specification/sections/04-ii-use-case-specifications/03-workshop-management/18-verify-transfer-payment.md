### 3.18 Verify Transfer Payment

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to confirm a pending bank-transfer collection or mark a pending collection as failed so that the ticket balance reflects only money actually received.

#### Preconditions

1. The actor is an active Manager.
2. A pending collection exists on the ticket folio.

#### Postconditions

- A confirmed transfer reduces the balance; a failed collection does not.
- The decision, acting account, and time are recorded.

#### Normal Flow

**Verify Transfer Payment**

1. The Manager opens the pending collection of the ticket.
2. The Manager checks that the money has arrived in the shop account.
3. The Manager confirms the transfer.
4. The system confirms the collection, updates the balance, and finalizes the folio if nothing is due or pending.

#### Alternative Flows

**Step 2 — Money cannot be verified**

The Manager marks the pending collection as failed with a reason so that the balance can be paid another way.

**Step 3 — Actor is not a Manager**

The system refuses the action.

**Step 3 — Collection belongs to another ticket**

The system reports it as not found and changes nothing.

#### Business Rules

BR-80-01, BR-80-02, BR-80-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-80-01 | Transfer confirmation is Manager-only in V1; remote confirmation is allowed. |
| BR-80-02 | A customer screenshot is never sufficient evidence. |
| BR-80-03 | A VNPay collection is never marked as paid manually. |
