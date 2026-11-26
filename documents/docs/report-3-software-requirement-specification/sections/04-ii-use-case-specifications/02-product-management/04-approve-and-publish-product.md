### 2.4 Approve and Publish Product

| Use Case ID | UC-15 | Use Case Name | Approve and Publish Product |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager, Staff | Secondary Actors | Account Management |

#### Description

As a Manager, I want to decide pending products and prices, apply approved prices, and publish or hide products so that customers see only Manager-approved products and prices.

#### Trigger

A Manager opens Products to review or Prices to review, or selects Change price, Apply price, Publish, or Hide; a Staff member selects Apply price.

#### Preconditions

1. Actor is signed in with an active Manager account; Staff may only apply approved prices, with product-management permission.
2. For approval, a pending product review or price proposal exists and is valid.
3. For Change price, the product has been published before and the size has no pending proposal.
4. For applying a price, the size has a valid, unapplied approved decision; a new product also has an approved review.
5. For publishing or hiding, the product exists and is not deleted.

#### Postconditions

1. The pending item is Approved with a final price per size, or Rejected with a reason; decisions are never rewritten.
2. An applied price is used only for new views, carts, and checkouts; accepted orders and valid holds keep their prices and deadlines.
3. On a published product, a size receiving its first applied price becomes publicly visible on the next read.
4. A published product appears in the public catalogue while all eligibility conditions hold.
5. A hidden product is excluded from public reads and new holds while kept in management with its history.

#### Normal Flow

**Approve Product and Price**

1. Manager opens Products to review or Prices to review on the Product Management List.
2. Manager selects a pending product review or pending price proposal.
3. System displays submitted content, images, proposed prices, submitter, branch scope, and decider history.
4. Manager accepts each proposed price or enters an adjusted positive whole VND amount.
5. Manager confirms the approval decision.
6. System verifies the item is still pending and is the current version.
7. System records the Approved decision, audits it, confirms (MSG22), and reloads list counts.

**Apply Approved Price**

1. Actor opens the product and selects a size with an approved price.
2. System displays the current applied price and the approved price.
3. Actor selects Apply price.
4. System verifies the decision is valid, matches the size and terms, and equals the approved amount.
5. System sets the approved price as applied, records previous/new prices, decision, actor, and time, and confirms (MSG24).

**Publish Product**

1. Manager opens an unpublished product and selects Publish or Show again.
2. System applies approved prices not yet applied, then checks active category, valid content, usable image, valid review, and required applied prices.
3. System sets the product to Published, records an audit entry, and confirms (MSG26).

**Publish Now as Manager**

1. Manager selects Publish now on a never-published product in Draft or Approved review status.
2. System sends missing submission (UC-14), approval, price application, and publication sequentially.
3. System verifies conditions at each step and confirms publication (MSG26).

**Change Price as Manager**

1. Manager selects Change price on a published product size with no pending proposal.
2. Manager enters the new positive whole VND selling price and keeps "Apply right after approving" ticked.
3. Manager selects Save.
4. System sends proposal creation, submission (UC-14), approval, and price application as sequential commands.
5. System confirms completion (MSG22, MSG24) and marks the previous applied round as Superseded.

**Hide Product**

1. Manager opens a published product and selects Hide.
2. System sets the product to Unpublished, excludes it from public reads, and blocks new holds.
3. System records an audit entry and confirms (MSG26).

#### Alternative Flows

2a. Review is Rejected → List shows See reason; Initial review shows the reason without a publish button for Staff revision (UC-14).

2b. Several pending sizes are decided together → Manager approves or rejects multiple published-product proposals at once with one shared rejection reason.

3a. Submitter or decider name cannot be read → System displays "Account #{id}" (or "System" for seed data) and loads the rest.

4a. Manager rejects the item → Manager enters a 1–2,000 character reason; System sets the item to Rejected (MSG22).

4b. Manager reviews own submission → System allows Manager to approve or reject their own product or proposal.

5a. "Apply right after approving" stays ticked → System runs Apply Approved Price immediately after Step 7 (MSG24); clearing it leaves the price unapplied.

Y1a. Actor selects Apply approved prices for several sizes → System applies each size as its own separate action.

Y1b. Apply is sent right after a Manager's approval, creation, or Publish now → Steps 1–3 are skipped; System applies the newly approved version directly.

Y1c. New product review is approved with some sizes unapplied → Staff gets a button in the Initial review section to apply only those sizes; a Manager applies them per size or through Publish or Publish now.

P2a. Product was published before and is hidden → System republishes without a new content review if at least one size has an applied price.

C1a. Size has a pending Staff proposal → Change price is hidden; Manager decides the pending proposal via the approval form.

H2a. Product has valid holds or accepted orders → Valid holds and accepted orders keep their prices and deadlines without cancellation.

A3a. Product is already in the requested publication state → System makes no second state change and reports the current state.

#### Exception Flows

A1a. Actor lacks the required role or session expired → System rejects the request (MSG01, MSG02) and logs it.

A1b. Staff attempts to publish, hide, or show a product → System rejects the request (MSG01); Staff must stop/resume branch sale (UC-12).

2c. One or more sizes fail in a batch decision → Valid sizes succeed; System reports partial results (MSG54).

4c. Rejection reason is missing or exceeds 2,000 characters → System rejects the decision (MSG21).

5b. Manager cancels → System records no decision.

5c. Applying approved price after approval fails → Approval decision is kept; retry applies only unapplied sizes.

6a. Item was edited, already decided, or product deleted → System rejects the decision and prompts reload (MSG23).

7a. Save fails or response is lost → System records no partial decision (MSG04); retry creates no duplicate.

Y1d. One or more sizes fail in batch apply → Valid sizes succeed; System reports partial results (MSG54).

Y4a. Decision was invalidated, superseded, or product deleted → System rejects the request (MSG23).

Y4b. Submitted amount differs from the approved amount → System rejects the request; amount cannot be altered during application.

Y5a. Save fails or response is lost → System keeps the previous price (MSG04); retry does not apply twice.

P2b. Publication condition is not met → System keeps the product unpublished, names the unmet condition (MSG25), and logs the attempt.

N1a. Review is Pending or Rejected → Publish now is unavailable; list shows Review first or See reason.

N2a. A step in Publish now fails → Completed steps stay saved; System reports the failed step so Publish now can resume.

C4a. A step in Change price fails → Completed steps stay saved; System reports the unfinished change (MSG68) for continuation.

A3b. Data changed concurrently or save failed → System rejects the request or preserves previous state (MSG03, MSG04).

#### Business Rules

GBR-14, GBR-16, GBR-32, GBR-33, BR-15-01, BR-15-02, BR-15-03, BR-15-04, BR-15-05, BR-15-06, BR-15-07, BR-15-08, BR-15-09, BR-15-10, BR-15-11

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG03, MSG04, MSG21, MSG22, MSG23, MSG24, MSG25, MSG26, MSG54, MSG68

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| GBR-14 | Review, price and publication are separate states. An approved price is applied as a separate action, even when sent right after the approval. A Manager's Change price sends the proposal, submission, approval and application as separate audited commands and never approves a pending Staff price in one click. Only a Manager publishes, hides or shows a whole product again. |
| GBR-16 | Changing a price or hiding a product never cancels valid holds, changes accepted orders or removes history. |
| GBR-32 | The submitter, the submitter's branches at submission and the decider are shown for information only and never change permissions. |
| GBR-33 | After the first publication, a size without an applied price does not remove the product from publication but is not shown publicly; it appears once its approved price is applied. |
| BR-15-01 | Only a Manager may approve, adjust or reject a product or price, including the Manager's own submission. |
| BR-15-02 | A decision applies only to a pending item that is still the current version; a rejection requires a reason of 1–2,000 characters. |
| BR-15-03 | Each decision records the Manager, time, version and inputs; approval does not apply the price or publish the product. The Manager may choose to apply the approved price right after the decision, which is a separate apply action. |
| BR-15-04 | A review submitted by Staff that is Pending is never approved by a one-click publish action; the Manager reviews it in the Initial review section, where each size price can be changed or the review rejected. |
| BR-15-05 | The submitter, submitter's branches at submission and decider are shown for information only and do not change permissions or approval and apply conditions. Names are read from Account Management at the time of reading (full name only, otherwise "Account #{id}"); no email, sign-in identifier or token is shown, and these fields are never returned by public reads. Staff and Manager with internal read access see the same information. |
| BR-15-06 | Applying a price is a separate action that accepts only a valid approved decision for the same size and terms, at exactly the approved amount; it may be requested right after the approval but always keeps its own record. |
| BR-15-07 | An older request cannot overwrite a newer applied price, and applying records the previous and new price, decision, actor and time. |
| BR-15-08 | A new applied price affects only new views and checkouts; it never changes accepted orders or valid holds, and a pending new price does not remove the current applied price. |
| BR-15-09 | Publishing is a separate Manager action and requires a not-deleted product, an active category, valid content and at least one usable image; a new product also needs an approved and valid review record and an applied approved price for every size, and a product published before needs an applied approved price for at least one size. |
| BR-15-10 | Review status, price status and publication status are separate; approval or price application never publishes a product automatically. A Manager's Publish now only sends the missing steps as separate commands, and is not available for a Pending or Rejected review. |
| BR-15-11 | Hiding blocks public reads and new holds only; it does not cancel valid holds, change accepted orders or remove history, and a repeated request creates no extra state change. |
