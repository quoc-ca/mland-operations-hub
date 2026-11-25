### 2.5 Submit Product or Price Proposal

#### Primary Actors

Staff, Manager with product-management permission.

#### Secondary Actors

None.

#### Description

As an authorized catalogue maintainer, I want to submit a new product with its proposed prices, or a new price for an existing size, for Manager approval so that only approved data can be applied and published.

This use case includes the following sub-flows:

- Submit New Product for Approval
- Submit Price Proposal for an Existing Size
- Submit Price Proposals for Several Sizes

#### Preconditions

1. The actor is signed in, the account is active, and the current role is Staff or Manager with product-management permission.
2. The product exists and has not been deleted.
3. A Staff can submit only for a product sold at one or more of the Staff's assigned branches.

#### Normal Flow

**Submit New Product for Approval**

1. A Staff opens a product that has never been published and whose review record is Draft or Rejected. A Manager does not use this step by hand: the Manager's create screen and Publish now send the submission automatically (UC-12, UC-17).
2. The actor selects Submit for approval.
3. The system checks the latest version, an active category, valid content, at least one usable image and a proposed price for every size.
4. The system saves one Pending review record linked to the submitted version and to one proposal per size, together with a snapshot of the submitter's branch scope at the time of submission, records an audit entry and confirms (MSG20).

**Submit Price Proposal for an Existing Size**

1. The actor opens a size in the Price proposals section of a product and selects Propose a new price, which opens the Price Proposal Form, or, in the Prices to review tab of the Product Management List (Prices to Review screen), selects Propose a new price on a rejected price or Submit price on a draft not sent. For a product that has been published before, a Manager changes a price with Change price instead (UC-15).
2. The actor enters a positive whole VND amount and saves it as Draft or submits it.
3. The system checks that the size has no other pending proposal.
4. The system saves the proposal with its own proposal code, version, product, size, amount, submitter and status and, when it is submitted, a snapshot of the submitter's branch scope at that time, records an audit entry and confirms (MSG20). The current applied price stays in use while the proposal is pending, and a submitted proposal of a published product appears in the Prices to review tab.

**Submit Price Proposals for Several Sizes**

1. The actor opens Edit proposed prices of several sizes on the Price Proposal Form and selects sizes one by one or as a size range.
2. The actor enters one shared positive whole VND amount and may still change any row; sizes left empty are not changed.
3. The actor selects Save as draft or, after the first publication, Save and submit.
4. For each size, the system revises the current Draft or Pending proposal (a Pending one returns to Draft) or creates a new proposal, then submits it when asked, as in the sub-flow above. Each size is its own proposal; a size that fails does not affect the others. The system confirms with the number of sizes (MSG20), or lists the sizes that failed and why (MSG54).

#### Alternative Flows

**All sub-flows — Actor is not authorized or the session has expired**

The system rejects the request (MSG01, MSG02).

**New Product Step 3 — Prerequisite missing**

The system names the missing prerequisite, such as no usable image, an inactive category or a size without a proposed price (MSG18), and keeps the record in Draft. The screen checks the prerequisites it knows before sending, marks each section that lacks something in red with what to do, scrolls to the first one and lists them next to Submit for approval; the system still checks again when the request is sent.

**New Product Step 3 — Same content already pending**

The system does not ask the actor to submit the same version twice and keeps the existing Pending record.

**Price Proposal Step 3 — Size already has a pending proposal**

The system blocks a second independent proposal for the same size (MSG19). The actor edits the current proposal instead, which creates a new version, invalidates the previous version and is submitted again. Concurrent requests cannot create two pending proposals for one size.

**All sub-flows — Product is sold only at branches outside the Staff's scope**

The Staff can view the product but cannot submit (MSG01).

**Price Proposal Step 2 — Invalid amount**

The system rejects a zero, negative or non-whole amount (MSG08).

**Submission — Branch names cannot be read**

If only the branch names cannot be read from the shared branch list, the submission still succeeds; the snapshot keeps the branch identifiers without names and the screen shows "Branch #{id}". If the submitter's branch scope itself cannot be read, the submission is rejected in the same way as when the account check is unavailable, and no empty snapshot is saved.

**Price Proposal Step 4 — Proposal saved but submission fails**

When a new price entered on a rejected row or for a new size is saved but cannot be submitted, the draft is kept under Drafts not sent in the Prices to review tab and the error is shown.

**Any step — Data changed by another user, save failure or lost response**

The system rejects the stale request (MSG03) or reports that the result is not confirmed (MSG04); a retry does not create a duplicate review record or proposal.

#### Postconditions

- The submitted product version is Pending in the Products to review tab, or the price proposal is Pending in the Prices to review tab or with the initial review, and visible to the Manager in UC-15.
- A proposed price is never used as a selling price.
- Each size has at most one current pending proposal.

#### Business Rules

GBR-14, GBR-32, BR-14-01, BR-14-02, BR-14-03, BR-14-04, BR-14-05, BR-14-06

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| GBR-14 | Product review, price and publication are separate states; a proposed price is applied only after a Manager approves it and it is applied as a separate action. |
| GBR-32 | Each submission records the submitter's branch scope at that time, and this snapshot is never rewritten; it is shown for information only. |
| BR-14-01 | A new product can be submitted only with the latest version, an active category, valid content, at least one usable image and a proposed price for every size. |
| BR-14-02 | Each proposal keeps its own code, version, product, size, terms, amount, submitter and status, and a proposed price is never used as the selling price. |
| BR-14-03 | Each size has at most one current pending proposal, including those submitted with a new product; editing it creates a new version and invalidates the old one. |
| BR-14-04 | A rejected product version may be edited and submitted again as a new version; a rejected price cannot be applied. |
| BR-14-05 | Submitting a new product by hand applies to Staff only, and only for a product sold at one or more of the Staff's assigned branches; a Manager's create screen, Publish now and Change price send the submission as a separate command. Prices are shared by all branches. |
| BR-14-06 | Each submission of a price proposal or product review records, in the same transaction, the submitter's branch scope at that time: "All branches" for a Manager, or the Staff's assigned branches with their names at that time. The snapshot is never rewritten when assignments, branch names or branch status change later; a resubmission creates a new snapshot. Versions submitted before snapshots existed show "Not recorded", and a system actor shows "System". |
