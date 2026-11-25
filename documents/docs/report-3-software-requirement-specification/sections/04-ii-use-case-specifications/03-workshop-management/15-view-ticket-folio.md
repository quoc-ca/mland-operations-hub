### 3.15 View Ticket Folio

#### Primary Actors

Guest, Member, Staff, Manager

#### Secondary Actors

None

#### Description

As a ticket owner, an assigned Staff member, or a Manager, I want to view the bill, add-ons, and remaining balance of a workshop ticket so that I know what has been paid and what is still due.

#### Preconditions

1. The ticket has its own folio.
2. The actor is the owning Member, a Guest with a contact of the booking group, Staff with the location in scope and assigned to a session of the ticket, or a Manager.

#### Postconditions

- The folio is shown to the authorized actor.
- Viewing changes no folio data and consumes no buyer verification.

#### Normal Flow

**View Ticket Folio**

1. The actor opens the ticket.
2. The actor requests the bill.
3. The system verifies ownership, assignment, or Manager authority.
4. The system shows the customer view or the operator view with the charges and balance due.

#### Alternative Flows

**Step 2 — Guest lookup**

The Guest supplies the ticket code and one stored contact email or phone in the request body, never in the URL.

**Step 3 — Guest code or contact is wrong**

The system answers with the same not-found result; repeated lookups are rate limited.

**Step 3 — Staff is not assigned to the ticket**

The system refuses the request and records the refusal.

#### Business Rules

BR-77-01, BR-77-02, BR-77-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-77-01 | Each ticket has its own folio, with the ticket final price as the base line. |
| BR-77-02 | A collection that is pending verification does not reduce the balance. |
| BR-77-03 | Staff needs both location scope and session assignment; a Manager has global access. |
