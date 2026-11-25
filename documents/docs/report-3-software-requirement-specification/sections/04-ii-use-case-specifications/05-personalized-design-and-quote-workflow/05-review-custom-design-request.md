### 5.5 Review Custom Design Request

#### Primary Actors

- Staff
- Manager

#### Secondary Actors

- None

#### Description

This use case allows authorized Staff or a Manager to review a Member's custom design request, including its description, submitted images, optional Feature-012 snapshot, and review context. The reviewer may accept, reject, or request clarification and must provide an explanation.

#### Preconditions

1. A custom design request has been created and is available for review.
2. The reviewer is authenticated and has the required role.
3. The request contains at least one valid input and any attached design snapshot has passed ownership validation.

#### Normal Flow

**Review Custom Design Request**

1. The reviewer opens the pending custom design request queue.
2. The system displays the request details, submitted images, safe Member context, and optional design snapshot.
3. The reviewer checks the design requirements and feasibility.
4. The reviewer selects Accept, Reject, or Clarification Required.
5. The reviewer enters a mandatory explanation.
7. The system validates the reviewer's authority and stores the decision, reviewer, timestamp, and note.
8. The system updates the request status and notifies the member.

#### Alternative Flows

**Step 2 — Required information is missing**

1. The request does not contain enough usable design information.
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
| BR-36-01 | Staff and Manager may review only requests within their approved feature scope; backend authorization is authoritative. |
| BR-36-02 | Every accept, reject, or clarification decision requires a non-empty explanation. |
| BR-36-03 | Human review determines feasibility and review outcome; no AI result is required or used. |

