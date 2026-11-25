### 3.16 Add Folio Charge

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As an assigned Staff member or a Manager, I want to add a customer-consented add-on or variable charge to a ticket folio so that extra services are billed to that ticket.

#### Preconditions

1. The actor is a Manager, or Staff with the ticket location in scope and assigned to a session of the ticket.
2. The folio is OPEN and a session of the ticket has started.

#### Postconditions

- The accepted charge is added to that folio only and the balance is recalculated.
- The acting account, time, and consent reference are recorded.

#### Normal Flow

**Add Folio Charge**

1. The actor opens the ticket folio.
2. The actor selects a catalogue add-on and confirms that the customer agreed.
3. The system validates the folio, the session, the consent, and the server-side price.
4. The system records the charge and shows the new balance.

#### Alternative Flows

**Step 2 — Variable charge**

The actor enters a description, amount, and reason instead of selecting a catalogue add-on.

**Step 3 — Session not started or folio not open**

The system refuses the charge; after finalization no add-on may be added, changed, or removed.

**Step 3 — Missing consent, unavailable add-on, or amount below 5,000 VND**

The system refuses the charge.

#### Business Rules

BR-78-01, BR-78-02, BR-78-03, BR-78-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-78-01 | Catalogue add-on prices are derived by the system; the actor cannot enter them. |
| BR-78-02 | Every add-on or variable charge is at least 5,000 VND. |
| BR-78-03 | The consent record evidences the operator confirmation, not the customer's own consent. |
| BR-78-04 | Members and Guests have read-only access and cannot add charges. |
