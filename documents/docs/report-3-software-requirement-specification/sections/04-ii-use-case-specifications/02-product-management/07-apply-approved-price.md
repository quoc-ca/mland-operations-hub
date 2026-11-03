### 2.7 Apply Approved Price

#### Primary Actors

Staff, Manager with product-management permission.

#### Secondary Actors

None.

#### Description

As an authorized catalogue maintainer, I want to apply an approved price to a size as a separate action so that the selling price changes only when I choose, without affecting orders that were already accepted.

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
| BR-16-01 | Applying a price is a separate action that accepts only a valid approved decision for the same size and terms, at exactly the approved amount. |
| BR-16-02 | An older request cannot overwrite a newer applied price, and applying records the previous and new price, decision, actor and time. |
| BR-16-03 | A new applied price affects only new views and checkouts; it never changes accepted orders or valid holds, and a pending new price does not remove the current applied price. |
