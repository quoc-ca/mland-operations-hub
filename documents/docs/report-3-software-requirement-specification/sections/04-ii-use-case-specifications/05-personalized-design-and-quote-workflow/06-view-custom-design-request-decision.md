### 5.6 View Custom Design Request Decision

#### Primary Actors

- Member

#### Secondary Actors

- None

#### Description

This use case allows a member to view the status and decision of their submitted custom design request. The system displays the reviewer decision, reason or note, estimated value, and the next available action without allowing the member to modify the recorded decision.

#### Preconditions

1. The member is signed in.
2. The member has at least one custom design request.
3. The request belongs to the signed-in member.

#### Normal Flow

**View Custom Design Request Decision**

1. The member opens the custom design request history.
2. The system retrieves requests belonging to the member.
3. The member selects a request.
4. The system displays the request image, status, estimated value, reviewer decision, decision note, and timestamp.
5. The system displays the next action, such as waiting, revising the request, contacting staff, or continuing to booking.

#### Alternative Flows

**Step 2 — No request is found**

1. The system finds no custom design request for the member.
2. The system displays an empty-state message and allows the member to start a new eligible request.

**Step 3 — Request is not accessible**

1. The selected request does not belong to the signed-in member or is unavailable.
2. The system denies access and displays a general error message.

**Step 4 — Request is still pending**

1. The request has not received a final decision.
2. The system displays the pending status and does not show an approval or rejection result.

#### Postconditions

- The member can view the latest permitted status and decision information.
- No request, approval, quotation, or payment data is changed.
- Access to another member's request is not granted.

#### Business Rules

- BR-37-01
- BR-37-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-37-01 | A member may view only custom design requests linked to the member's own account. |
| BR-37-02 | Viewing a decision is read-only and does not automatically create an order or payment. |

