### 2.6 Approve Product and Price

#### Primary Actors

Manager.

#### Secondary Actors

Account Management, which supplies the names of submitters and deciders.

#### Description

As a Manager, I want to review pending products and price proposals and accept, adjust or reject them, and change the price of a published product in one step, so that only prices I decide can be applied to the catalogue.

This use case includes the following sub-flows:

- Approve Product and Price
- Change Price as Manager

#### Preconditions

1. The actor is signed in, the account is active, and the current role is Manager.
2. For an approval, a pending product review record or price proposal exists and is still valid; for Change price, the product has been published before.

#### Normal Flow

**Approve Product and Price**

1. The Manager opens the Products to review tab (reviews of never-published products) or the Prices to review tab (price proposals of published products, the Prices to Review screen) of the Product Management List. A product whose review is Pending shows Review first instead of a one-click publish button.
2. The Manager selects a pending item, or decides a pending price directly on its row in the Prices to review tab with the Price Proposal Review form. On the product detail page, the Initial review section is shown to a Manager only while a review submitted by Staff is Pending, or when the review was Rejected (with its reason).
3. The system displays the submitted product content, images and the proposed price of each size. For each price proposal version (including in the table for several sizes) and each review version, current and past, it shows the submitter and submission time, the submitter's branches at submission (UC-14), and the Manager who decided and the decision time once decided. A Draft that was never submitted shows no submitter.
4. For each size, the Manager accepts the proposed price or enters an adjusted final price as a positive whole VND amount; one shared final price can be filled in for the selected sizes.
5. The Manager confirms the decision.
6. The system checks that the item is still pending and is the current version.
7. The system records the decision with the Manager, time, version and decision inputs, sets the item to Approved, records an audit entry and confirms (MSG22). The decision itself does not apply or publish the price. In the Prices to review tab, the list and the counts are reloaded; a rejected price is shown under the Rejected sub-filter until the size gets a new proposal.

**Change Price as Manager**

1. On a size of a product that has been published before and has no pending proposal, the Manager selects Change price, either in the Price proposals section or from a rejected row of the Prices to review tab.
2. The Manager enters the new selling price as a positive whole VND amount and keeps or clears "Apply the price right after approving", which is ticked by default.
3. The Manager selects Save.
4. The screen sends, one after another: the creation of a proposal (or a new version of the current Draft), its submission (UC-14), the approval at exactly the entered price and, if ticked, the application (UC-16). Each step is its own command with its own record and audit entry.
5. The system confirms the result (MSG22, MSG24). The price history shows a new round with the Manager as submitter and decider, and the previous applied round as Superseded.

#### Alternative Flows

**Step 4 — Manager rejects**

The Manager selects Reject and enters a reason of 1–2,000 characters after trimming. The system sets the item to Rejected with the reason (MSG22). A missing or too long reason is rejected (MSG21).

**Step 5 — Manager keeps "Apply the price right after approving" ticked**

This option is selected by default. After step 7, the system applies each approved price as a separate apply action under UC-16, with its own record (MSG24). If applying fails, the decision is kept and a retry applies only the sizes not yet applied. If the Manager clears the option, the approved price waits to be applied under UC-16.

**Step 2 — Review is Rejected**

The product list shows See reason. The Initial review section shows the reason and no publish button; Staff edit the product and submit a new version under UC-14.

**Step 2 — Several pending sizes decided together**

After the first publication, the Manager can approve or reject several pending price proposals at once; a rejection uses one shared reason. Each size is still its own decision; a size that fails does not affect the others; partial results are reported (MSG54).

**Step 4 — Manager reviews own submission**

The Manager may approve or reject their own product or proposal. When a Manager creates a product or uses Publish now, the screen sends the approval with the entered prices itself (UC-12, UC-17); it is still a separate, audited decision.

**Step 3 — A person's name cannot be read**

If the account has no full name, cannot be read or no longer exists, the system shows "Account #{id}" and still shows the rest of the page. A system actor, such as seed data, is shown as "System".

**Step 5 — Manager cancels**

No decision is recorded.

**Step 6 — Item is no longer pending or valid**

If the item was edited, already decided or the product was deleted, the system rejects the decision and asks the Manager to reload (MSG23).

**Change Price Step 1 — Size has a pending proposal from Staff**

Change price is not offered; the Manager decides the pending proposal with the approval form, where the approved price can be adjusted, so that the Staff proposal is not withdrawn.

**Change Price Step 4 — A step fails**

The steps already completed are kept. The screen reloads and reports that the change is not finished (MSG68); the Manager continues from the current state with the existing submit, approve or apply buttons.

**Any step — Actor is not a Manager or the session has expired**

The system rejects the request (MSG01, MSG02). Staff and Admin cannot decide.

**Step 7 — Save failure or lost response**

The system records no partial decision (MSG04); a retry does not create a second decision.

#### Postconditions

- The pending item is Approved with a final price per size, or Rejected with a reason.
- Historical decisions are never rewritten; editing after a decision requires a new version and a new decision.
- The approved price is applied under UC-16, right after the decision when the Manager chose so, otherwise later.

#### Business Rules

GBR-14, GBR-32, BR-15-01, BR-15-02, BR-15-04, BR-15-05, BR-15-06

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| GBR-14 | Review, price and publication are separate states; a Manager's Change price sends the proposal, submission, approval and application as separate audited commands and never approves a pending Staff price in one click. |
| GBR-32 | The submitter, the submitter's branches at submission and the decider are shown for information only and never change permissions. |
| BR-15-01 | Only a Manager may approve, adjust or reject a product or price, including the Manager's own submission. |
| BR-15-02 | A decision applies only to a pending item that is still the current version; a rejection requires a reason of 1–2,000 characters. |
| BR-15-04 | Each decision records the Manager, time, version and inputs; approval does not apply the price or publish the product. The Manager may choose to apply the approved price right after the decision, which is a separate apply action under UC-16. |
| BR-15-05 | A review submitted by Staff that is Pending is never approved by a one-click publish action; the Manager reviews it in the Initial review section, where each size price can be changed or the review rejected. |
| BR-15-06 | The submitter, submitter's branches at submission and decider are shown for information only and do not change permissions or approval and apply conditions. Names are read from Account Management at the time of reading (full name only, otherwise "Account #{id}"); no email, sign-in identifier or token is shown, and these fields are never returned by public reads. Staff and Manager with internal read access see the same information. |
