### 5.5 Review Custom Design Request

#### Primary Actors

- Staff
- Manager

#### Secondary Actors

- None

#### Description

This use case allows Staff or a Manager to review a Member's custom design request and accept it, reject it, or ask for clarification, always with a written explanation. The review is done by a person; no AI result or estimated-value threshold is used.

#### Preconditions

1. A custom design request with status Need Review exists.
2. The reviewer is signed in as Staff or Manager and is not the owner of the request.

#### Normal Flow

**Review Custom Design Request**

1. The reviewer opens the design review queue.
2. The reviewer selects a request.
3. The system displays the request details, description, reference images and any saved-design snapshot.
4. The reviewer selects Accept, Reject or Request Clarification.
5. The reviewer enters the explanation and confirms.
6. The system checks the reviewer's role, the request state and version.
7. The system records the decision with the actor, role, request version, time and audit data, and updates the status.
8. The Member can read the status, explanation and next action.

#### Alternative Flows

**Step 4 — Request Clarification**

The status becomes Clarification Required. When the Member replies with a revision, the request returns to Need Review.

**Step 5 — Explanation is missing or too long**

The system rejects the decision.

**Step 6 — Request already decided or changed**

The system returns a conflict and asks the reviewer to reload.

**Any step — Actor is not Staff or Manager**

The system denies access. Admin has technical and audit access only and cannot decide.

#### Postconditions

- The request is Accepted, Rejected or Clarification Required with a recorded explanation.
- Accepted and Rejected are final.
- Accepting a request creates no payment; the quote and full payment follow in the custom-order flow.

#### Business Rules

- BR-36-01
- BR-36-02
- BR-36-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-36-01 | Staff and Managers may decide a request; every decision requires a non-empty explanation. |
| BR-36-02 | A reviewer cannot review their own request, and a decision applies only to the current version of an undecided request. |
| BR-36-03 | Feasibility, acceptance and quotation are decided by people only; no AI result is used. |
