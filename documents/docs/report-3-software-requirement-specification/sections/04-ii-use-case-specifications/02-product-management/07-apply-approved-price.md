### 2.7 Apply Approved Price

#### Primary Actors

Staff, Manager with product-management permission.

#### Secondary Actors

None.

#### Description

As an authorized catalogue maintainer, I want to apply an approved price to a size as a separate action, either later or right after a Manager approves it, so that the selling price changes only when chosen, without affecting orders that were already accepted.

#### Preconditions

1. The actor is signed in, the account is active, and the current role is Staff or Manager with product-management permission.
2. The size has an approved price decision that is still valid and has not been applied. For a new product, its review record is also approved and still valid.

#### Normal Flow

**Apply Approved Price**

1. The actor opens the product and selects a size with an approved price.
2. The system shows the current applied price and the approved price.
3. The actor selects Apply price.
4. The system checks that the decision is still valid, matches the size and terms, and that the amount is exactly the approved amount.
5. The system sets the approved price as the applied price, records the previous and new price, the decision, the actor and the time, and confirms (MSG24).

#### Alternative Flows

**Step 1 — Several sizes at once**

The actor selects Apply approved prices of N sizes. The system applies each size as its own action; a size that fails does not affect the others.

**Step 1 — Applied right after approval**

When the Manager kept "Apply price right after approval" selected in UC-15, or when the Manager's create screen or Publish now runs (UC-12, UC-17), steps 1–3 are skipped and the system applies the version that was just approved.

**Step 1 — Review approved but some prices not applied yet**

When the review of a new product is approved and some sizes still have no applied price, the Initial review section offers a button that applies the approved prices of those sizes only.

**Step 4 — Decision is no longer valid**

The system rejects the request if the decision was invalidated, a newer price was already applied, or the product was deleted (MSG23).

**Step 4 — Amount differs from the approved amount**

The system rejects the request. The amount cannot be changed at the time of applying.

**Any step — Actor is not authorized or the session has expired**

The system rejects the request (MSG01, MSG02).

**Step 5 — Save failure or lost response**

The system keeps the previous price (MSG04); a retry does not apply the price twice.

#### Postconditions

- The new price is used only for new views, carts and checkouts.
- Prices and deadlines of orders and holds that were already accepted do not change.
- Applying a price does not publish the product.

#### Business Rules

BR-16-01, BR-16-02, BR-16-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-16-01 | Applying a price is a separate action that accepts only a valid approved decision for the same size and terms, at exactly the approved amount; it may be requested right after the approval but always keeps its own record. |
| BR-16-02 | An older request cannot overwrite a newer applied price, and applying records the previous and new price, decision, actor and time. |
| BR-16-03 | A new applied price affects only new views and checkouts; it never changes accepted orders or valid holds, and a pending new price does not remove the current applied price. |
