### 2.2 UC-30 Submit Product or Price Proposal

#### UC Name

UC-30 Submit Product or Price Proposal

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member or Manager, I want to submit a new product or a new size price for approval so that only Manager-approved data is sold.

#### Preconditions

- The actor is signed in with an active Staff or Manager account.
- The product exists and is not deleted.
- A Staff member submits only for a product sold at one of the assigned branches.

#### Normal Flow

***Submit New Product for Approval***

1. The Staff member opens a never-published product whose review is a draft.
2. The Staff member selects Submit for review.
3. The system checks the category, images and proposed prices of every size.
4. The system asks the Staff member to confirm the submission (MSG139).
5. The Staff member confirms the submission.
6. The system checks that the product is still the latest version.
7. The system saves a pending review with the submitter's branch snapshot and records an audit entry.
8. The system confirms that the review has been submitted to a Manager (MSG20).

#### Alternative Flows

***Step 1.1 Actor Proposes a New Price for a Size***

1. The actor opens a size of a published product and selects Propose a new price.
2. The actor enters a positive whole VND amount and selects Submit price.
3. The system checks that the size has no other pending proposal.
4. The system saves the pending proposal with the submitter's branch snapshot and records an audit entry.
5. The system confirms the submission and names the Prices to review tab (MSG20).
6. The use case ends with the proposal waiting for a Manager.

***Step 1.2 Actor Proposes Prices for Several Sizes***

1. The actor opens Edit proposed prices of several sizes and chooses sizes or a size range.
2. The actor enters a shared amount and adjusts single rows.
3. The actor selects Save and submit.
4. The system saves and submits each size as its own proposal.
5. The system confirms the number of sizes submitted (MSG20).
6. The use case ends.

***Step 1.3 Actor Saves a Price as a Draft***

1. The actor selects Only save drafts instead of submitting.
2. The system saves the proposal as a draft that no Manager sees yet.
3. The system lists it under Drafts not sent in the Prices to review tab.
4. The use case ends without a submission.

***Step 1.4 Actor Revises a Pending Proposal***

1. The actor selects Revise proposed price on a pending proposal.
2. The system creates a new draft version and withdraws the pending one.
3. The system shows that the proposal must be submitted again.
4. The use case continues at step 1.1.2.

***Step 1.5 Manager Sends the Submission Through Another Screen***

1. The Manager creates a product, selects Publish now or selects Change price.
2. The system sends the submission as a separate audited command (UC-32, UC-44, UC-45).
3. The use case ends.

***Step 3.1 Submission Prerequisite Is Missing***

1. The system lists the missing items and marks the related sections (MSG18).
2. The system sends nothing and keeps the review as a draft.
3. The use case continues at step 1.

***Step 3.2 Proposed Amount Is Invalid***

1. The system marks the amount that is zero, negative or not a whole number (MSG08).
2. The use case continues at step 1.1.2.

***Step 3.3 Size Already Has a Pending Proposal***

1. The system rejects the second proposal (MSG19).
2. The actor revises the current proposal instead.
3. The use case continues at step 1.4.1.

***Step 4.1 Staff Member Cancels the Confirmation***

1. The system closes the confirmation without sending anything.
2. The use case ends.

***Step 4.2 Some Sizes Fail in a Several-Size Submission***

1. The system saves the valid sizes.
2. The system lists each failed size with its reason (MSG53).
3. The use case ends with the failed sizes kept for a retry.

***Step 6.1 Product Changed Meanwhile***

1. The system rejects the submission and offers to load the latest version (MSG03).
2. The use case continues at step 1.

***Step 6.2 Actor Lacks Permission***

1. The system rejects a Staff member whose product is sold only at other branches (MSG01).
2. The use case ends without a submission.

***Step 7.1 Result Is Not Confirmed***

1. The system reports that the result is unknown (MSG04).
2. The actor retries with the same request, which creates no duplicate.
3. The use case continues at step 7.

#### Postconditions

- The submitted review or price proposal is pending and visible to a Manager.
- A proposed price is never used as a selling price.
- Each size has at most one pending price proposal.
- The submitter's branch snapshot is stored and never rewritten.

#### Business Rule

BR-11, BR-29, BR-66, BR-67, BR-68
