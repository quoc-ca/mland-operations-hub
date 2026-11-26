### 3.16 Add Folio Charge

| Use Case ID | UC-78 | Use Case Name | Add Folio Charge |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | None |

#### Description

As an assigned Staff member or a Manager, I want to add a customer-consented add-on or variable charge to a ticket folio so that extra services are billed to that ticket.

#### Trigger

An actor opens an open ticket folio to add a charge.

#### Preconditions

1. The actor is a Manager, or Staff with the ticket location in scope and assigned to a session of the ticket.
2. The folio is OPEN and a session of the ticket has started.

#### Postconditions

1. The accepted charge is added to that folio only and the balance is recalculated.
2. The acting account, time, and consent reference are recorded.

#### Normal Flow

1. Actor opens the ticket folio.
2. Actor selects a catalogue add-on and confirms that the customer agreed.
3. System validates the folio, session, consent, and server-side price.
4. System records the charge and displays the new balance.

#### Alternative Flows

2a. Actor adds a variable charge → Actor enters a description, amount, and reason instead of a catalogue add-on.

#### Exception Flows

3a. Session has not started or folio is not open → System refuses the charge; finalized folios allow no add-on changes.

3b. Consent is missing, add-on is unavailable, or amount is below 5,000 VND → System refuses the charge.

#### Business Rules

BR-78-01, BR-78-02, BR-78-03, BR-78-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-78-01 | Catalogue add-on prices are derived by the system; the actor cannot enter them. |
| BR-78-02 | Every add-on or variable charge is at least 5,000 VND. |
| BR-78-03 | The consent record evidences the operator confirmation, not the customer's own consent. |
| BR-78-04 | Members and Guests have read-only access and cannot add charges. |
