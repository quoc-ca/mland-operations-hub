### 2.7 Apply Approved Price

| Use Case ID | UC-16 | Use Case Name | Apply Approved Price |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | None |

#### Description

As an authorized catalogue maintainer, I want to apply an approved price to a size as a separate action so that selling prices update only when chosen without affecting accepted orders.

#### Trigger

A Staff or Manager selects Apply price on a size with an unapplied approved price, or Manager triggers auto-apply from UC-12, UC-15, or UC-17.

#### Preconditions

1. Actor is signed in with an active Staff or Manager account and product-management permission.
2. The size has a valid, unapplied approved price decision.
3. For a new product, its review record is also approved and valid.

#### Postconditions

1. The new applied price is used only for new views, carts, and checkouts.
2. Prices and deadlines of accepted orders and valid holds remain unchanged.
3. On a published product, a size receiving its first applied price becomes publicly visible on the next read.

#### Normal Flow

1. Actor opens the product and selects a size with an approved price.
2. System displays the current applied price and the approved price.
3. Actor selects Apply price.
4. System verifies the decision is valid, matches the size and terms, and equals the approved amount.
5. System sets the approved price as applied, records previous/new prices, decision, actor, and time, and confirms (MSG24).

#### Alternative Flows

1a. Actor selects Apply approved prices for several sizes → System applies each size as its own separate action.

1b. Triggered automatically after Manager approval (UC-12, UC-15, UC-17) → Steps 1–3 are skipped; System applies the newly approved version directly.

1c. New product review is approved with some sizes unapplied → Initial review section offers a button to apply only those unapplied sizes.

#### Exception Flows

1d. Actor is unauthorized or session expired → System rejects the request at any step (MSG01, MSG02).

1e. One or more sizes fail in batch apply → Valid sizes succeed; System reports partial results (MSG54).

4a. Decision was invalidated, superseded, or product deleted → System rejects the request (MSG23).

4b. Submitted amount differs from the approved amount → System rejects the request; amount cannot be altered during application.

5a. Save fails or response is lost → System keeps the previous price (MSG04); retry does not apply twice.

#### Business Rules

GBR-14, GBR-16, GBR-33, BR-16-01, BR-16-02, BR-16-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG04, MSG23, MSG24, MSG54

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| GBR-14 | An approved price is applied as a separate action, even when it is sent right after the approval. |
| GBR-16 | Changing a price never cancels valid holds or changes accepted orders. |
| GBR-33 | After the first publication, a size without an applied price is not shown publicly; it appears once its approved price is applied. |
| BR-16-01 | Applying a price is a separate action that accepts only a valid approved decision for the same size and terms, at exactly the approved amount; it may be requested right after the approval but always keeps its own record. |
| BR-16-02 | An older request cannot overwrite a newer applied price, and applying records the previous and new price, decision, actor and time. |
| BR-16-03 | A new applied price affects only new views and checkouts; it never changes accepted orders or valid holds, and a pending new price does not remove the current applied price. |
