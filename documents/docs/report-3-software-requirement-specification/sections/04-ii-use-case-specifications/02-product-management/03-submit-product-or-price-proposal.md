### 2.3 Submit Product or Price Proposal

| Use Case ID | UC-14 | Use Case Name | Submit Product or Price Proposal |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | None |

#### Description

As an authorized catalogue maintainer, I want to submit a new product or a price proposal for Manager approval so that only approved data can be applied and published.

#### Trigger

A Staff member selects Submit for approval on a product, or a Staff or Manager selects Propose a new price on a size.

#### Preconditions

1. Actor is signed in with an active Staff or Manager account and product-management permission.
2. The product exists and is not deleted.
3. Staff may submit only for products sold at one or more assigned branches.

#### Postconditions

1. Submitted product version or price proposal is Pending and visible to a Manager in UC-15.
2. A proposed price is never used as a selling price.
3. Each size has at most one current pending proposal.

#### Normal Flow

**Submit New Product for Approval**

1. Staff opens an unpublished product in Draft or Rejected review status.
2. Staff selects Submit for approval.
3. System verifies latest version, active category, valid content, at least one usable image, and proposed prices.
4. System saves a Pending review record with a submitter branch-scope snapshot, audits it, and confirms (MSG20).

**Submit Price Proposal for an Existing Size**

1. Actor opens the Price Proposal Form for a size from product details or Prices to review.
2. Actor enters a positive whole VND amount and submits it.
3. System verifies that the size has no other pending proposal.
4. System saves the Pending proposal with a submitter branch-scope snapshot, audits it, and confirms (MSG20).

**Submit Price Proposals for Several Sizes**

1. Actor opens Edit proposed prices of several sizes and selects sizes.
2. Actor enters a shared positive whole VND amount and adjusts individual rows as needed.
3. Actor selects Save and submit.
4. System creates or revises each size's proposal, submits them, and confirms the count (MSG20).

#### Alternative Flows

N3a. Same product content is already pending → System keeps the existing Pending record without creating a duplicate.

N4a. Branch names cannot be read from Workshop Management → Submission succeeds using branch IDs ("Branch #{id}") in the snapshot.

P2a. Actor selects Save as draft → System saves the proposal as Draft without submitting or locking it.

S3a. Actor selects Save as draft → System saves all selected size proposals as Drafts without submitting.

#### Exception Flows

A1a. Actor is unauthorized or session expired → System rejects the request (MSG01, MSG02).

A1b. Product is sold only outside Staff's assigned branches → Staff can view the product but cannot submit (MSG01).

N3b. Submission prerequisite is missing → System lists missing items (MSG18), highlights sections, and keeps the record in Draft.

N4b. Submitter's branch scope cannot be read → System rejects the submission and saves no empty snapshot.

P2b. Proposed amount is zero, negative, or non-whole → System rejects the amount (MSG08).

P3a. Size already has a pending proposal → System blocks a second proposal (MSG19); Actor must edit the current proposal instead.

P4a. Proposal saved but submission fails → Draft is kept under Drafts not sent in Prices to review and the error is shown.

S4a. One or more sizes fail in batch submission → Valid sizes succeed; System lists failed sizes and reasons (MSG54).

A4a. Data changed concurrently, save failed, or response lost → System rejects stale requests (MSG03) or reports unconfirmed result (MSG04).

#### Business Rules

GBR-14, GBR-32, BR-14-01, BR-14-02, BR-14-03, BR-14-04, BR-14-05, BR-14-06

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG03, MSG04, MSG08, MSG18, MSG19, MSG20, MSG54

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| GBR-14 | Product review, price and publication are separate states; a proposed price is applied only after a Manager approves it and it is applied as a separate action. |
| GBR-32 | Each submission records the submitter's branch scope at that time, and this snapshot is never rewritten; it is shown for information only. |
| BR-14-01 | A new product can be submitted only with the latest version, an active category, valid content, at least one usable image and a proposed price for every size. |
| BR-14-02 | Each proposal keeps its own code, version, product, size, terms, amount, submitter and status, and a proposed price is never used as the selling price. |
| BR-14-03 | Each size has at most one current pending proposal, including those submitted with a new product; editing it creates a new version and invalidates the old one. |
| BR-14-04 | A rejected product version may be edited and submitted again as a new version; a rejected price cannot be applied. |
| BR-14-05 | Submitting a new product by hand applies to Staff only, and only for a product sold at one or more of the Staff's assigned branches; a Manager's create screen, Publish now and Change price send the submission as a separate command. Prices are shared by all branches. |
| BR-14-06 | Each submission of a price proposal or product review records, in the same transaction, the submitter's branch scope at that time: "All branches" for a Manager, or the Staff's assigned branches with their names at that time. The snapshot is never rewritten when assignments, branch names or branch status change later; a resubmission creates a new snapshot. Versions submitted before snapshots existed show "Not recorded", and a system actor shows "System". |
