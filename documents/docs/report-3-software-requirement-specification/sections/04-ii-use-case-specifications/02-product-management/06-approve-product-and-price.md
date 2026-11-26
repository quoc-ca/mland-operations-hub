### 2.6 Approve Product and Price

| Use Case ID | UC-15 | Use Case Name | Approve Product and Price |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | Account Management |

#### Description

As a Manager, I want to review and decide pending products and price proposals, and change published prices directly, so that only Manager-approved prices can be applied.

#### Trigger

A Manager opens Products to review or Prices to review, or selects Change price on a published size.

#### Preconditions

1. Actor is signed in with an active Manager account.
2. For approval, a pending product review or price proposal exists and is valid.
3. For Change price, the product has been published before and the size has no pending proposal.

#### Postconditions

1. The pending item is Approved with a final price per size, or Rejected with a reason.
2. Historical decisions are never rewritten; later edits require a new version and decision.
3. Approved prices are applied under UC-16 immediately if selected, otherwise later.

#### Normal Flow

**Approve Product and Price**

1. Manager opens Products to review or Prices to review on the Product Management List.
2. Manager selects a pending product review or pending price proposal.
3. System displays submitted content, images, proposed prices, submitter, branch scope, and decider history.
4. Manager accepts each proposed price or enters an adjusted positive whole VND amount.
5. Manager confirms the approval decision.
6. System verifies the item is still pending and is the current version.
7. System records the Approved decision, audits it, confirms (MSG22), and reloads list counts.

**Change Price as Manager**

1. Manager selects Change price on a published product size with no pending proposal.
2. Manager enters the new positive whole VND selling price and keeps "Apply right after approving" ticked.
3. Manager selects Save.
4. System sends proposal creation, submission (UC-14), approval, and application (UC-16) as sequential commands.
5. System confirms completion (MSG22, MSG24) and marks the previous applied round as Superseded.

#### Alternative Flows

2a. Review is Rejected → List shows See reason; Initial review shows the reason without a publish button for Staff revision (UC-14).

2b. Several pending sizes are decided together → Manager approves or rejects multiple published-product proposals at once with one shared rejection reason.

3a. Submitter or decider name cannot be read → System displays "Account #{id}" (or "System" for seed data) and loads the rest.

4a. Manager rejects the item → Manager enters a 1–2,000 character reason; System sets the item to Rejected (MSG22).

4b. Manager reviews own submission → System allows Manager to approve or reject their own product or proposal.

5a. "Apply right after approving" stays ticked → System runs UC-16 immediately after Step 7 (MSG24); clearing it leaves the price unapplied.

C1a. Size has a pending Staff proposal → Change price is hidden; Manager decides the pending proposal via the approval form.

#### Exception Flows

A1a. Actor is not a Manager or session expired → System rejects the request (MSG01, MSG02).

2c. One or more sizes fail in a batch decision → Valid sizes succeed; System reports partial results (MSG54).

4c. Rejection reason is missing or exceeds 2,000 characters → System rejects the decision (MSG21).

5b. Manager cancels → System records no decision.

5c. Applying approved price after approval fails → Approval decision is kept; retry applies only unapplied sizes.

6a. Item was edited, already decided, or product deleted → System rejects the decision and prompts reload (MSG23).

7a. Save fails or response is lost → System records no partial decision (MSG04); retry creates no duplicate.

C4a. A step in Change price fails → Completed steps stay saved; System reports the unfinished change (MSG68) for continuation.

#### Business Rules

GBR-14, GBR-32, BR-15-01, BR-15-02, BR-15-04, BR-15-05, BR-15-06

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG04, MSG21, MSG22, MSG23, MSG24, MSG54, MSG68

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| GBR-14 | Review, price and publication are separate states; a Manager's Change price sends the proposal, submission, approval and application as separate audited commands and never approves a pending Staff price in one click. |
| GBR-32 | The submitter, the submitter's branches at submission and the decider are shown for information only and never change permissions. |
| BR-15-01 | Only a Manager may approve, adjust or reject a product or price, including the Manager's own submission. |
| BR-15-02 | A decision applies only to a pending item that is still the current version; a rejection requires a reason of 1–2,000 characters. |
| BR-15-04 | Each decision records the Manager, time, version and inputs; approval does not apply the price or publish the product. The Manager may choose to apply the approved price right after the decision, which is a separate apply action under UC-16. |
| BR-15-05 | A review submitted by Staff that is Pending is never approved by a one-click publish action; the Manager reviews it in the Initial review section, where each size price can be changed or the review rejected. |
| BR-15-06 | The submitter, submitter's branches at submission and decider are shown for information only and do not change permissions or approval and apply conditions. Names are read from Account Management at the time of reading (full name only, otherwise "Account #{id}"); no email, sign-in identifier or token is shown, and these fields are never returned by public reads. Staff and Manager with internal read access see the same information. |
