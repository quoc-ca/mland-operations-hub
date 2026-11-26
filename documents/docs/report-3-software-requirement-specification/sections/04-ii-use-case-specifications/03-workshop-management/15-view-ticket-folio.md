### 3.15 View Ticket Folio

| Use Case ID | UC-77 | Use Case Name | View Ticket Folio |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member, Staff, Manager | Secondary Actors | None |

#### Description

As a ticket owner, an assigned Staff member, or a Manager, I want to view the bill, add-ons, and remaining balance of a workshop ticket so that I know what is paid and due.

#### Trigger

An actor requests the folio of a workshop ticket.

#### Preconditions

1. The ticket has its own folio.
2. The actor is the owning Member, a Guest with group contact proof, assigned in-scope Staff, or a Manager.

#### Postconditions

1. The folio is shown to the authorized actor.
2. Viewing changes no folio data and consumes no buyer verification.

#### Normal Flow

1. Actor opens the ticket.
2. Actor requests the ticket folio.
3. System verifies ownership, assignment, or Manager authority.
4. System displays the customer view or operator view with charges and balance due.

#### Alternative Flows

2a. Actor is a Guest → Guest supplies the ticket code and one stored contact email or phone in the request body, never in the URL.

#### Exception Flows

3a. Guest code or contact is wrong → System returns the same not-found result and rate-limits repeated lookups.

3b. Staff is not assigned to the ticket → System refuses the request and records the refusal.

#### Business Rules

BR-77-01, BR-77-02, BR-77-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-77-01 | Each ticket has its own folio, with the ticket final price as the base line. |
| BR-77-02 | A collection that is pending verification does not reduce the balance. |
| BR-77-03 | Staff needs both location scope and session assignment; a Manager has global access. |
