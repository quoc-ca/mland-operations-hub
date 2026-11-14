### 2.6 Approve Product and Price

#### Primary Actors

Manager.

#### Secondary Actors

AI Chatbox Analysis and Suggestion, which may provide an advisory price suggestion.

#### Description

As a Manager, I want to review pending products and price proposals and accept, adjust or reject them so that only prices I decide can be applied to the catalogue.

#### Preconditions

1. The actor is signed in, the account is active, and the current role is Manager.
2. A pending product review record or price proposal exists and is still valid.

#### Normal Flow

**Approve Product and Price**

1. The Manager opens the list of pending reviews and proposals. In the product list, a product whose review is Pending shows a Review to approve link instead of a one-click publish button.
2. The Manager selects a pending item. On the product detail page, the Initial review section is shown to a Manager only while a review submitted by Staff is Pending, or when the review was Rejected (with its reason).
3. The system displays the submitted product content, images and the proposed price of each size, and any AI price suggestion marked as advisory.
4. For each size, the Manager accepts the proposed price or enters an adjusted final price as a positive whole VND amount; one shared final price can be filled in for the selected sizes.
5. The Manager confirms the decision.
6. The system checks that the item is still pending and is the current version.
7. The system records the decision with the Manager, time, version and decision inputs, sets the item to Approved, records an audit entry and confirms (MSG22). The decision itself does not apply or publish the price.

#### Alternative Flows

**Step 4 — Manager rejects**

The Manager selects Reject and enters a reason of 1–2,000 characters after trimming. The system sets the item to Rejected with the reason (MSG22). A missing or too long reason is rejected (MSG21).

**Step 5 — Manager keeps "Apply price right after approval" selected**

This option is selected by default. After step 7, the system applies each approved price as a separate apply action under UC-16, with its own record (MSG24). If applying fails, the decision is kept and a retry applies only the sizes not yet applied. If the Manager clears the option, the approved price waits to be applied under UC-16.

**Step 2 — Review is Rejected**

The product list shows a View reason link. The Initial review section shows the reason and no publish button; Staff edit the product and submit a new version under UC-14.

**Step 2 — Several pending sizes decided together**

After the first publication, the Manager can approve or reject several pending price proposals at once; a rejection uses one shared reason. Each size is still its own decision; a size that fails does not affect the others.

**Step 4 — Manager reviews own submission**

The Manager may approve or reject their own product or proposal. When a Manager creates a product or uses Publish now, the screen sends the approval with the entered prices itself (UC-12, UC-17); it is still a separate, audited decision.

**Step 3 — No AI suggestion is available**

The Manager can still decide manually. The AI suggestion never decides or blocks a decision.

**Step 5 — Manager cancels**

No decision is recorded.

**Step 6 — Item is no longer pending or valid**

If the item was edited, already decided or the product was deleted, the system rejects the decision and asks the Manager to reload (MSG23).

**Any step — Actor is not a Manager or the session has expired**

The system rejects the request (MSG01, MSG02). Staff and Admin cannot decide.

**Step 7 — Save failure or lost response**

The system records no partial decision (MSG04); a retry does not create a second decision.

#### Postconditions

- The pending item is Approved with a final price per size, or Rejected with a reason.
- Historical decisions are never rewritten; editing after a decision requires a new version and a new decision.
- The approved price is applied under UC-16, right after the decision when the Manager chose so, otherwise later.

#### Business Rules

BR-15-01, BR-15-02, BR-15-03, BR-15-04, BR-15-05

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-15-01 | Only a Manager may approve, adjust or reject a product or price, including the Manager's own submission. |
| BR-15-02 | A decision applies only to a pending item that is still the current version; a rejection requires a reason of 1–2,000 characters. |
| BR-15-03 | AI price suggestions are advisory only; their absence does not block a valid manual decision. |
| BR-15-04 | Each decision records the Manager, time, version and inputs; approval does not apply the price or publish the product. The Manager may choose to apply the approved price right after the decision, which is a separate apply action under UC-16. |
| BR-15-05 | A review submitted by Staff that is Pending is never approved by a one-click publish action; the Manager reviews it in the Initial review section, where each size price can be changed or the review rejected. |
