### 5.6 View Custom Design Request Decision

| Use Case ID | UC-41 | Use Case Name | View Custom Design Request Decision |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | — |

#### Description

As a Member, I want to view the status, decision, and explanation of my custom design request so that I know the next action.

#### Trigger

A Member opens the custom design request history.

#### Preconditions

1. The Member is signed in.
2. The Member has at least one custom design request.
3. The selected request belongs to the signed-in Member.

#### Postconditions

1. The Member views the latest permitted status and decision information.
2. No request, approval, quotation, or payment data is changed.
3. Access to another Member's request is not granted.

#### Normal Flow

1. Member opens the custom design request history.
2. System retrieves custom design requests belonging to the Member.
3. Member selects a request.
4. System displays the description, inputs, status, reviewer decision, explanation, and timestamp.
5. System displays the next available action for the current status.

#### Alternative Flows

2a. No custom design request is found → System displays an empty-state message and allows starting a new request.

4a. Request has no final decision yet → System displays the pending status without an approval or rejection result.

#### Exception Flows

3a. Request does not belong to the Member or is unavailable → System denies access and displays a general error.

#### Business Rules

BR-37-01, BR-37-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-37-01 | A member may view only custom design requests linked to the member's own account. |
| BR-37-02 | Viewing a decision is read-only and does not automatically create an order or payment. |
