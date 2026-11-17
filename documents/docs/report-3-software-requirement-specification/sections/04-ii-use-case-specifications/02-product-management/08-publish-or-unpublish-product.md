### 2.8 Publish or Unpublish Product

#### Primary Actors

Staff, Manager with product-management permission.

#### Secondary Actors

None.

#### Description

As an authorized catalogue maintainer, I want to publish a product that meets all conditions, or hide a published product, so that customers see only products that are ready to sell.

This use case includes the following sub-flows:

- Publish Product
- Publish Now as Manager
- Unpublish Product

#### Preconditions

1. The actor is signed in, the account is active, and the current role is Staff or Manager with product-management permission.
2. The product exists and has not been deleted.

#### Normal Flow

**Publish Product**

1. The actor opens an unpublished product and selects Publish.
2. The system checks the latest state: for a product that has never been published, an approved and still valid review record; an active category; valid content; at least one usable image; and an applied approved price for every size.
3. The system sets the product to Published, records an audit entry and confirms (MSG26). Zero quantity does not block publication.

**Publish Now as Manager**

1. A Manager selects Publish (product list) or Publish now (product detail) on a product whose review is Draft or Approved.
2. The screen sends, one after another, only the steps that are still missing: the submission (UC-14), the approval with the current proposed prices (UC-15), the application of each approved price (UC-16) and the publication. Each step is its own command with its own record and audit entry.
3. The system checks every condition at each step and confirms the result (MSG26).

**Unpublish Product**

1. The actor opens a published product and selects Unpublish.
2. The system sets the product to Unpublished, removes it from public reads and blocks new holds, while keeping it in Product Management.
3. The system records an audit entry and confirms (MSG26).

#### Alternative Flows

**Publish Step 2 — Condition not met**

The system keeps the product unpublished, names the unmet condition (MSG25) and records the attempt. A proposed price, or an approved price that is not yet applied, does not meet the condition.

**Publish Now Step 1 — Review is Pending or Rejected**

A Pending review submitted by Staff is not approved by one click: the list shows Review to approve and the detail page points to the Initial review section (UC-15). A Rejected review shows View reason and no publish button.

**Publish Now Step 2 — A step fails**

The steps already completed are kept; the system reports the step that failed, and selecting Publish now again continues from the missing step.

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
| BR-17-02 | Review status, price status and publication status are separate; approval or price application never publishes a product automatically. A Manager's Publish now only sends the missing steps as separate commands, and is not available for a Pending or Rejected review. |
| BR-17-03 | Unpublishing blocks public reads and new holds only; it does not cancel valid holds, change accepted orders or remove history, and a repeated request creates no extra state change. |
