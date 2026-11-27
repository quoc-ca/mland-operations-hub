### 5.5 Review Custom Design Request

| Use Case ID | UC-40 | Use Case Name | Review Custom Design Request |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | — |

#### Description

As Staff or a Manager, I want to review a Member's custom design request and record a decision with an explanation so that the Member knows the outcome.

#### Trigger

Staff or a Manager opens the design review queue.

#### Preconditions

1. A custom design request is awaiting review and is not Accepted or Rejected.
2. The reviewer is signed in as Staff or Manager and does not own the request.
3. The request contains at least one valid description, reference image, or saved ring design.

#### Postconditions

1. The request status is Accepted, Rejected, or Clarification Required with a recorded explanation.
2. Accepted and Rejected decisions are final.
3. Accepting a request creates no payment; quotation and payment follow in the custom-order flow.

#### Normal Flow

1. Staff or Manager opens the design review queue.
2. Staff or Manager selects a request to review.
3. System displays the description, reference images, safe Member context, and any saved-design snapshot.
4. Staff or Manager checks design feasibility and selects Accept or Reject.
5. For acceptance, Staff or Manager may enter an optional note and confirms the quote inputs; for rejection or clarification, the actor enters the mandatory explanation and confirms.
6. System checks the reviewer's role, request state, and version.
7. System records the decision with actor, role, version, timestamp, and audit data.
8. System updates the request status so the Member can view the outcome.

#### Alternative Flows

4a. Reviewer selects Request Clarification → System sets status to Clarification Required until Member revises and returns it to Need Review.

#### Exception Flows

1a. Actor is not Staff or Manager → System denies access; Admin has technical and audit access only.

5a. Explanation is missing for rejection or clarification, or any note exceeds 2,000 characters → System rejects the decision.

6a. Request was already decided or changed → System returns a conflict and asks the reviewer to reload.

#### Business Rules

BR-36-01, BR-36-02, BR-36-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-36-01 | Only Staff and Managers may decide a request, within their approved feature scope; backend authorization is authoritative. Rejection and clarification require a non-empty explanation; acceptance may use the system's default explanation. |
| BR-36-02 | A reviewer cannot review their own request, and a decision applies only to the current version of an undecided request. |
| BR-36-03 | Feasibility, review outcome and quotation are decided by people only; no AI result is required or used. |
