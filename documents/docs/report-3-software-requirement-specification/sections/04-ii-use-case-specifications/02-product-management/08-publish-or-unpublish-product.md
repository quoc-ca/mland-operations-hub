### 2.8 Publish or Unpublish Product

#### Primary Actors

Staff, Manager with product-management permission.

#### Secondary Actors

None.

#### Description

As an authorized catalogue maintainer, I want to publish a product that meets all conditions, or hide a published product, so that customers see only products that are ready to sell.

This use case includes the following sub-flows:

- Publish Product
- Unpublish Product

#### Preconditions

1. The actor is signed in, the account is active, and the current role is Staff or Manager with product-management permission.
2. The product exists and has not been deleted.

#### Normal Flow

**Publish Product**

1. The actor opens an unpublished product and selects Publish.
2. The system checks the latest state: for a product that has never been published, an approved and still valid review record; an active category; valid content; at least one usable image; and an applied approved price for every size.
3. The system sets the product to Published, records an audit entry and confirms (MSG26). Zero quantity does not block publication.

**Unpublish Product**

1. The actor opens a published product and selects Unpublish.
2. The system sets the product to Unpublished, removes it from public reads and blocks new holds, while keeping it in Product Management.
3. The system records an audit entry and confirms (MSG26).

#### Alternative Flows

**Publish Step 2 — Condition not met**

The system keeps the product unpublished, names the unmet condition (MSG25) and records the attempt. A proposed price, or an approved price that is not yet applied, does not meet the condition.

**Publish Step 2 — Product was published before and is now hidden**

The product is not treated as new; it can be republished without a new content review if the other conditions are met.

**Publish or Unpublish — Product is already in the requested state**

The system makes no second state change and reports the current state.

**Unpublish Step 2 — Product has valid holds or orders**

Valid holds and accepted orders keep their prices and deadlines and can still be paid; they are not cancelled.

**Any step — Actor is not authorized, data changed or save failed**

The system rejects the request or keeps the previous state (MSG01, MSG03, MSG04).

#### Postconditions

- A published product appears in the public catalogue while all conditions continue to hold.
- An unpublished product is excluded from public reads and new holds but is kept in management with its history.

#### Business Rules

BR-17-01, BR-17-02, BR-17-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-17-01 | Publishing is a separate action and requires a not-deleted product, an approved and valid review record for a new product, an active category, valid content, at least one usable image and an applied approved price for every size. |
| BR-17-02 | Review status, price status and publication status are separate; approval or price application never publishes a product automatically. |
| BR-17-03 | Unpublishing blocks public reads and new holds only; it does not cancel valid holds, change accepted orders or remove history, and a repeated request creates no extra state change. |
