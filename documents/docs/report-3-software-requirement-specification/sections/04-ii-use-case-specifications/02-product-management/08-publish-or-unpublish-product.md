### 2.8 Publish or Unpublish Product

| Use Case ID | UC-17 | Use Case Name | Publish or Unpublish Product |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to publish eligible products, hide published products, or show hidden products again so that customers see only products ready for sale.

#### Trigger

A Manager selects Publish, Publish now, Show again, or Hide on a product.

#### Preconditions

1. Actor is signed in with an active Manager account.
2. The product exists and is not deleted.

#### Postconditions

1. A published product appears in the public catalogue while all eligibility conditions hold.
2. An unpublished product is excluded from public reads and new holds while kept in management with its history.

#### Normal Flow

**Publish Product**

1. Manager opens an unpublished product and selects Publish or Show again.
2. System checks active category, valid content, usable image, valid review, and required applied approved prices.
3. System sets the product to Published, records an audit entry, and confirms (MSG26).

**Publish Now as Manager**

1. Manager selects Publish or Publish now on a never-published product in Draft or Approved review status.
2. System sends missing submission (UC-14), approval (UC-15), price application (UC-16), and publication sequentially.
3. System verifies conditions at each step and confirms publication (MSG26).

**Unpublish Product**

1. Manager opens a published product and selects Hide.
2. System sets the product to Unpublished, excludes it from public reads, and blocks new holds.
3. System records an audit entry and confirms (MSG26).

#### Alternative Flows

P2a. Product was published before and is hidden → System republishes without a new content review if at least one size has an applied price.

A3a. Product is already in the requested state → System makes no second state change and reports the current state.

U2a. Product has valid holds or accepted orders → Valid holds and accepted orders keep their prices and deadlines without cancellation.

#### Exception Flows

A1a. Staff attempts to publish, hide, or show a product → System rejects the request (MSG01) and logs it; Staff must stop/resume branch sale (UC-12).

A1b. Actor is unauthorized, data changed, or save failed → System rejects the request or preserves previous state (MSG01, MSG03, MSG04).

P2b. Publication condition is not met → System keeps the product unpublished, names the unmet condition (MSG25), and logs the attempt.

N1a. Review is Pending or Rejected → Publish now is unavailable; list shows Review first (UC-15) or See reason.

N2a. A step in Publish now fails → Completed steps stay saved; System reports the failed step so Publish now can resume.

#### Business Rules

GBR-14, GBR-16, GBR-33, BR-17-01, BR-17-02, BR-17-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG03, MSG04, MSG25, MSG26

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| GBR-14 | Only a Manager publishes, hides or shows a whole product again; the first publication requires an applied price for every size, while a product published before can be published again when at least one size has an applied price. |
| GBR-16 | Hiding a product never cancels valid holds, changes accepted orders or removes history. |
| GBR-33 | After the first publication, a size without an applied price does not remove the product from publication but is not shown publicly. |
| BR-17-01 | Publishing is a separate Manager action and requires a not-deleted product, an active category, valid content and at least one usable image; a new product also needs an approved and valid review record and an applied approved price for every size, and a product published before needs an applied approved price for at least one size. |
| BR-17-02 | Review status, price status and publication status are separate; approval or price application never publishes a product automatically. A Manager's Publish now only sends the missing steps as separate commands, and is not available for a Pending or Rejected review. |
| BR-17-03 | Unpublishing blocks public reads and new holds only; it does not cancel valid holds, change accepted orders or remove history, and a repeated request creates no extra state change. |
