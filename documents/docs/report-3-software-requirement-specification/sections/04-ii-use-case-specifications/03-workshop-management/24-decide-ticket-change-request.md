### 3.24 Decide Ticket Change Request

| Use Case ID | UC-86 | Use Case Name | Decide Ticket Change Request |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | Mail Gateway |

#### Description

As a Staff member of the location or a Manager, I want to approve or reject a queued ticket change request so that exceptional changes are decided and the customer is informed.

#### Trigger

An actor opens the change request queue to review a pending request.

#### Preconditions

1. The actor is a Manager, or Staff whose location scope contains the request location.
2. A change request is pending and has not expired.

#### Postconditions

1. The request is approved or rejected with the deciding account and reason.
2. The customer is emailed the outcome.

#### Normal Flow

1. Actor opens the change request queue.
2. Actor reviews a request and enters an approval decision with a reason.
3. System checks the actor scope and the request state.
4. System applies the change as one granted counted change and emails the customer.

#### Alternative Flows

2a. Actor rejects the request → Ticket keeps its old schedule; internal reason is not sent to the customer.

#### Exception Flows

3a. Request has expired or is already decided → System refuses the decision; customer is notified when a request expires.

4a. Target location no longer sells the package → Approval is refused, change is not applied, and no allowance is used.

#### Business Rules

BR-86-01, BR-86-02, BR-86-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-86-01 | Staff decides only requests of locations in their scope; a Manager is global; Admin has no access. |
| BR-86-02 | An advance request expires at the earlier start of the changed session or a requested session. |
| BR-86-03 | A late-arrival request expires at the earlier of the end of the 7th calendar day or a requested session start. |
