### 5.5 Review Custom Design Request

#### Primary Actors

- Staff
- Manager

#### Secondary Actors

- None

#### Description

This use case allows authorized staff or a manager to review a member's custom design request, including the submitted image, AI analysis results, estimated value, and workshop context. The reviewer may approve, reject, or request clarification according to the request value and authorization rules.

#### Preconditions

1. A custom design request has been created and is available for review.
2. The reviewer is authenticated and has the required role.
3. The request contains the available image, analysis result, and estimated value.

#### Normal Flow

**Review Custom Design Request**

1. The reviewer opens the pending custom design request queue.
2. The system displays the request details, submitted image, AI analysis result, member information, and estimated value.
3. The system determines the review authority based on the estimated value.
4. The reviewer checks the design requirements, feasibility information, and quotation details.
5. The reviewer selects Approve, Reject, or Request Revision.
6. The reviewer enters a decision note when required.
7. The system validates the reviewer's authority and stores the decision, reviewer, timestamp, and note.
8. The system updates the request status and notifies the member.

#### Alternative Flows

**Step 3 — Manager review is required**

1. The estimated value exceeds the staff approval threshold.
2. The system prevents staff approval and routes the request to a manager.

**Step 3 — Staff review is permitted**

1. The estimated value is within the staff approval threshold.
2. The system allows an authorized staff member to complete the review.

**Step 2 — Required information is missing**

1. The request does not contain enough image, analysis, or pricing information.
2. The system marks the request as needing clarification and prevents final approval.

**Step 5 — Request is rejected or returned for revision**

1. The reviewer selects Reject or Request Revision and provides a reason.
2. The system stores the reason, updates the status, and notifies the member.

#### Postconditions

- The custom design request has a recorded decision or remains pending clarification.
- The reviewer, timestamp, decision, and supporting note are recorded for audit.
- The member receives the decision status and next action.
- Approval does not create a payment transaction unless a later order workflow is completed.

#### Business Rules

- BR-36-01
- BR-36-02
- BR-36-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-36-01 | Staff may approve requests with an estimated value up to 3,000,000 VND; requests above this amount require manager approval. |
| BR-36-02 | The reviewer must have an authorized role for the request value and cannot approve a request outside that authority. |
| BR-36-03 | The AI result is advisory; final feasibility and quotation decisions remain with the authorized human reviewer. |

