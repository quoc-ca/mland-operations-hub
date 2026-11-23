### 2.8 Publish or Unpublish Product

#### Primary Actors

Manager.

#### Secondary Actors

None.

#### Description

As a Manager, I want to publish a product that meets all conditions, hide a published product or show a hidden product again, so that customers see only products that are ready to sell. Because publication applies at every branch, Staff do not publish or hide; they stop or resume selling at their own branch (UC-12).

This use case includes the following sub-flows:

- Publish Product
- Publish Now as Manager
- Unpublish Product

#### Preconditions

1. The actor is signed in, the account is active, and the current role is Manager.
2. The product exists and has not been deleted.

#### Normal Flow

**Publish Product**

1. The actor opens an unpublished product and selects Publish (product list) or Publish product (product detail); for a hidden product the list shows Show again.
2. The system checks the latest state: an active category, valid content and at least one usable image; for a product that has never been published, an approved and still valid review record and an applied approved price for every size; for a product that has been published before, an applied approved price for at least one size.
3. The system sets the product to Published, records an audit entry and confirms (MSG26). Zero quantity does not block publication. Sizes without an applied price stay hidden from customers until their price is applied.

**Publish Now as Manager**

1. A Manager selects Publish (product list) or Publish now (product detail) on a never-published product whose review is Draft or Approved (the Drafts or Ready to publish tab).
2. The screen sends, one after another, only the steps that are still missing: the submission (UC-14), the approval with the current proposed prices (UC-15), the application of each approved price (UC-16) and the publication. Each step is its own command with its own record and audit entry.
3. The system checks every condition at each step and confirms the result (MSG26).

**Unpublish Product**

1. The actor opens a published product and selects Hide (product list) or Hide product (product detail).
2. The system sets the product to Unpublished, removes it from public reads and blocks new holds, while keeping it in Product Management.
3. The system records an audit entry and confirms (MSG26).

#### Alternative Flows

**Publish Step 2 — Condition not met**

The system keeps the product unpublished, names the unmet condition (MSG25) and records the attempt. A proposed price, or an approved price that is not yet applied, does not meet the condition.

**Publish Now Step 1 — Review is Pending or Rejected**

A Pending review submitted by Staff is not approved by one click: the list shows Review first and the detail page points to the Initial review section (UC-15). A Rejected review shows See reason and no publish button.

**Publish Now Step 2 — A step fails**

The steps already completed are kept; the system reports the step that failed, and selecting Publish now again continues from the missing step.

**Publish Step 2 — Product was published before and is now hidden**

The product is not treated as new; it can be shown again without a new content review when at least one size has an applied approved price and the other conditions are met.

**Publish or Unpublish — Product is already in the requested state**

The system makes no second state change and reports the current state.

**Unpublish Step 2 — Product has valid holds or orders**

Valid holds and accepted orders keep their prices and deadlines and can still be paid; they are not cancelled.

**Any step — A Staff member tries to publish, hide or show a product again**

The system rejects the request, including when it is sent directly to the API, changes nothing (MSG01) and records the rejected attempt. The Staff member stops selling at their branch instead (UC-12).

**Any step — Actor is not authorized, data changed or save failed**

The system rejects the request or keeps the previous state (MSG01, MSG03, MSG04).

#### Postconditions

- A published product appears in the public catalogue while all conditions continue to hold.
- An unpublished product is excluded from public reads and new holds but is kept in management with its history.

#### Business Rules

GBR-14, GBR-16, GBR-33, BR-17-01, BR-17-02, BR-17-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| GBR-14 | Only a Manager publishes, hides or shows a whole product again; the first publication requires an applied price for every size, while a product published before can be published again when at least one size has an applied price. |
| GBR-16 | Hiding a product never cancels valid holds, changes accepted orders or removes history. |
| GBR-33 | After the first publication, a size without an applied price does not remove the product from publication but is not shown publicly. |
| BR-17-01 | Publishing is a separate Manager action and requires a not-deleted product, an active category, valid content and at least one usable image; a new product also needs an approved and valid review record and an applied approved price for every size, and a product published before needs an applied approved price for at least one size. |
| BR-17-02 | Review status, price status and publication status are separate; approval or price application never publishes a product automatically. A Manager's Publish now only sends the missing steps as separate commands, and is not available for a Pending or Rejected review. |
| BR-17-03 | Unpublishing blocks public reads and new holds only; it does not cancel valid holds, change accepted orders or remove history, and a repeated request creates no extra state change. |
