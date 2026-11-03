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

#### Preconditions

1. The actor is signed in, the account is active, and the current role is Staff or Manager with product-management permission.
2. The product exists and has not been deleted.

#### Normal Flow

**Submit New Product for Approval**

1. The actor opens a product that has never been published and whose review record is Draft or Rejected.
2. The actor selects Submit for approval.
3. The system checks the latest version, an active category, valid content, at least one usable image and a proposed price for every size.
4. The system saves one Pending review record linked to the submitted version and to one proposal per size, records an audit entry and confirms (MSG20).

**Submit Price Proposal for an Existing Size**

1. The actor opens a size of a product and selects Propose new price.
2. The actor enters a positive whole VND amount and saves it as Draft or submits it.
3. The system checks that the size has no other pending proposal.
4. The system saves the proposal with its own proposal code, version, product, size, amount, submitter and status, records an audit entry and confirms (MSG20). The current applied price stays in use while the proposal is pending.

#### Alternative Flows

**All sub-flows — Actor is not authorized or the session has expired**

The system rejects the request (MSG01, MSG02).

**New Product Step 3 — Prerequisite missing**

The system names the missing prerequisite, such as no usable image, an inactive category or a size without a proposed price (MSG18), and keeps the record in Draft.

**New Product Step 3 — Same content already pending**

The system does not ask the actor to submit the same version twice and keeps the existing Pending record.

**Price Proposal Step 3 — Size already has a pending proposal**

The system blocks a second independent proposal for the same size (MSG19). The actor edits the current proposal instead, which creates a new version, invalidates the previous version and is submitted again. Concurrent requests cannot create two pending proposals for one size.

**Price Proposal Step 2 — Invalid amount**

The system rejects a zero, negative or non-whole amount (MSG08).

**Any step — Data changed by another user, save failure or lost response**

The system rejects the stale request (MSG03) or reports that the result is not confirmed (MSG04); a retry does not create a duplicate review record or proposal.

#### Postconditions

- The submitted product version or price proposal is Pending and visible to the Manager in UC-15.
- A proposed price is never used as a selling price.
- Each size has at most one current pending proposal.

#### Business Rules

BR-14-01, BR-14-02, BR-14-03, BR-14-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-14-01 | A new product can be submitted only with the latest version, an active category, valid content, at least one usable image and a proposed price for every size. |
| BR-14-02 | Each proposal keeps its own code, version, product, size, terms, amount, submitter and status, and a proposed price is never used as the selling price. |
| BR-14-03 | Each size has at most one current pending proposal, including those submitted with a new product; editing it creates a new version and invalidates the old one. |
| BR-14-04 | A rejected product version may be edited and submitted again as a new version; a rejected price cannot be applied. |
