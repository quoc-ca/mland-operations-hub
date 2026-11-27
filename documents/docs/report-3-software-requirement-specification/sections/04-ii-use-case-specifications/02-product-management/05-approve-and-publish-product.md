### 2.5 UC-44 Approve and Publish Product

#### UC Name

UC-44 Approve and Publish Product

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to accept, adjust or reject pending products and price proposals and publish approved products so that customers see only approved products and prices.

#### Preconditions

- The actor is signed in with an active Manager account.
- A pending product review or price proposal exists.
- The product is not deleted.

#### Normal Flow

***Approve New Product Review***

1. The Manager opens the Products to review tab of the Product Management List.
2. The system lists the products whose current review is pending.
3. The Manager opens a product and its Initial review section.
4. The system shows the submitted content, images, proposed price of each size, submitter, submitter branches and history.
5. The Manager accepts each proposed price or enters an adjusted amount.
6. The Manager keeps Apply the price right after approving ticked and selects Approve.
7. The system checks that the review is still pending and current.
8. The system records the approved decision with its prices and an audit entry.
9. The system applies each approved price as a separate audited action.
10. The system confirms that the review is approved and the prices are applied (MSG22, MSG24).

#### Alternative Flows

***Step 1.1 Manager Decides a Price Proposal of a Published Product***

1. The Manager opens the Prices to review tab or a size of the product.
2. The system shows the applied price, the proposed price and the submitter.
3. The Manager accepts or adjusts the amount and selects Approve.
4. The use case continues at step 7.

***Step 1.2 Manager Decides Several Sizes Together***

1. The Manager chooses several pending sizes and selects Approve or Reject with one shared reason.
2. The system records each size as its own decision.
3. The system lists each failed size with its reason (MSG53).
4. The use case ends.

***Step 1.3 Manager Publishes an Approved Product***

1. The Manager selects Publish on a product in the Ready to publish tab.
2. The system applies any approved price not yet applied.
3. The system checks the active category, content, usable image, approved review and an applied price for every size.
4. The system publishes the product and records an audit entry.
5. The system confirms that the product is published (MSG26).
6. The use case ends.

***Step 1.4 Manager Uses Publish Now on a Draft Product***

1. The Manager selects Publish now on a never-published product whose review is a draft or approved.
2. The system sends the missing submission, approval, price application and publication as separate audited commands.
3. The system confirms that the product is published (MSG26).
4. The use case ends.

***Step 1.5 Review Was Submitted by Staff***

1. The system hides Publish now and asks the Manager to decide the review first (MSG145).
2. The use case continues at step 3.

***Step 1.6 Publication Condition Is Not Met***

1. The system keeps the product unpublished and names each unmet condition (MSG25).
2. The use case ends.

***Step 1.7 A Publish Now Step Fails***

1. The system keeps the finished steps.
2. The system reports the failed step so that Publish now can resume (MSG04).
3. The use case ends.

***Step 4.1 Submitter Name Cannot Be Read***

1. The system shows the account number instead of the name.
2. The use case continues at step 5.

***Step 5.1 Manager Rejects the Item***

1. The Manager selects Reject and enters a reason.
2. The system checks that the reason has 1–2,000 characters (MSG21).
3. The system records the rejected decision and an audit entry.
4. The system confirms the rejection (MSG22).
5. The use case ends with the reason visible to Staff.

***Step 5.2 Approved Amount Is Invalid***

1. The system marks an amount that is zero, negative or not a whole number (MSG08).
2. The use case continues at step 5.

***Step 6.1 Manager Applies the Price Later***

1. The Manager clears Apply the price right after approving.
2. The system records the approved decision without applying the price (MSG22).
3. The use case ends.

***Step 6.2 Manager Cancels the Decision***

1. The system records no decision.
2. The use case ends.

***Step 7.1 Item Is No Longer Pending or Current***

1. The system rejects a decision on an edited, decided or deleted item (MSG23).
2. The use case ends without a decision.

***Step 7.2 Actor Is Not a Manager***

1. The system rejects a decision or publication by a Staff member (MSG01).
2. The use case ends without a change.

***Step 9.1 Price Application Fails***

1. The system keeps the approved decision.
2. The system reports the size that is not applied (MSG04).
3. The Manager applies the remaining prices later.
4. The use case ends.

#### Postconditions

- The item is approved with a final price per size or rejected with a reason; decisions are never rewritten.
- An applied price affects only new views, carts and checkouts.
- A published product is visible to customers while it stays eligible.
- The decider is recorded with each decision.

#### Business Rule

BR-11, BR-13, BR-29, BR-30, BR-68, BR-69, BR-73, BR-78
