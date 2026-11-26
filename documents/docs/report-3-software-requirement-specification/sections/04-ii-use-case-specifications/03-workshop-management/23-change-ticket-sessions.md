### 3.23 Change Ticket Sessions

| Use Case ID | UC-85 | Use Case Name | Change Ticket Sessions |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member, Staff, Manager | Secondary Actors | Mail Gateway |

#### Description

As the buyer, an assigned Staff member, or a Manager, I want to select valid replacement sessions for a ticket so that the participant can attend at another time.

#### Trigger

An actor opens an eligible ticket to request a session change.

#### Preconditions

1. The ticket folio is open. A Member or Guest requests an advance change of a session that has not started; late-arrival support is entered only by assigned Staff of the ticket's location or a Manager (before the old session ends).
2. The actor is the owning Member or a buyer-verified Guest; assigned Staff or a Manager acting for the buyer gives a reason and the buyer proof.

#### Postconditions

1. An applied change moves the ticket, keeps its code and QR, and uses one counted change.

#### Normal Flow

1. Actor opens the ticket and views the valid replacement sessions.
2. Actor selects new sessions for one or both sessions.
3. System rechecks capacity, reschedule quota, package rules, time window, and allowance.
4. System applies the change and emails the result.

#### Alternative Flows

3a. Price increases → Buyer confirms the new schedule and amount; the increase is added to the checkout balance.

3b. Quota is full, change is used, or advance window closed → Request is queued for a Staff or Manager decision (UC-86).

#### Exception Flows

1a. A Member or Guest picks a session that has already started → System refuses and never queues it; the screen asks them to contact the shop (branch name shown).

1b. A Member or Guest asks for late-arrival support → System refuses; only Staff or a Manager may enter it.

3c. Target session is invalid or full → System refuses the change; targets past day 7 after the replaced session are never queued.

#### Business Rules

BR-85-01, BR-85-02, BR-85-03, BR-85-04, BR-85-05

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-85-01 | Each ticket allows one customer change, plus any extra change granted by a Manager. |
| BR-85-02 | A change never lowers the price or creates a refund. |
| BR-85-03 | Several tickets of one group change all or none. |
| BR-85-04 | Changing to another location is marked draft in one part of the spec and approved in another; to be confirmed. |
| BR-85-05 | Members and Guests make advance changes only; late-arrival support is limited to assigned Staff of the ticket's location or a Manager. |
