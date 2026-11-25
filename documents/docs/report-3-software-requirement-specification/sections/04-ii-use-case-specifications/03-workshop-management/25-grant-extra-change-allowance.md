### 3.25 Grant Extra Change Allowance

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to grant one extra counted change to a ticket so that the customer can change sessions after the normal allowance is used.

#### Preconditions

1. The actor is an active Manager.
2. The ticket exists.

#### Postconditions

- The ticket receives one extra counted customer change.
- The grant is recorded with the Manager, reason, and time.

#### Normal Flow

**Grant Extra Change Allowance**

1. The Manager opens the ticket.
2. The Manager chooses to grant an extra change and enters a reason.
3. The system validates the authority and the reason.
4. The system records the allowance.

#### Alternative Flows

**Step 2 — Reason is missing**

The system refuses the grant.

**Step 3 — Actor is not a Manager**

The system refuses the grant.

**Step 4 — Same request is repeated**

The system returns the recorded grant and adds no second allowance.

#### Business Rules

BR-87-01, BR-87-02, BR-87-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-87-01 | Only a Manager may grant an extra change, with a reason and audit. |
| BR-87-02 | A grant never performs the change itself. |
| BR-87-03 | Mland-caused changes do not count against the allowance. |
