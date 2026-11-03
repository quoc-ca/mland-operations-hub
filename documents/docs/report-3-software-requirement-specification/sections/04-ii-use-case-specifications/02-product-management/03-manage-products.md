### 2.3 Manage Products

#### Primary Actors

Staff, Manager with product-management permission.

#### Secondary Actors

Cloud Storage Service, where product images are stored.

#### Description

As an authorized catalogue maintainer, I want to create draft products, keep their content, images and quantities up to date, control the sale of each size and delete products that are no longer sold, so that the catalogue data stays valid without changing accepted orders.

This use case includes the following sub-flows:

- Create Draft Product
- Update Product Content and Images
- Update Quantity and Sale Status per Size
- Delete Product

#### Preconditions

1. The actor is signed in through the shared Account Management sign-in, the account is active, and the current role is Staff or Manager with product-management permission.
2. For an update or delete, the product exists and has not been deleted.

#### Normal Flow

**Create Draft Product**

1. The actor opens Product Management and selects New product.
2. The actor enters the name (1–200 characters), description (1–5,000 characters), an active category, 1–25 distinct sizes from 5 to 29, and for each size a proposed price (positive whole VND) and a quantity (whole number, 0 or more). Images are optional for a draft.
3. The system validates all data.
4. The system assigns a stable numeric product code and one read-only SKU per size in the format `SP{product code}-S{size}`, for example `SP000001-S09`.
5. The system saves the product as Unpublished with a Draft review record, marks each size as on sale, and records an audit entry. The product is not approved, priced or published automatically.
6. The system shows the product code and SKUs (MSG09).

**Update Product Content and Images**

1. The actor opens an existing product.
2. The system displays the product, its sizes and SKUs, review status, proposed, approved and applied prices, publication status and images.
3. The actor changes the name, description or category, or adds, replaces, reorders or removes images (JPEG, PNG or WebP, up to 10 images, each at most 5,000,000 bytes, with alt text of 1–200 characters).
4. The system checks that the data is still the latest version and validates the changes.
5. The system saves the changes, records an audit entry and confirms (MSG10).
6. If the product has never been published, the change invalidates any submitted or approved version and returns the review record to Draft; the actor must submit it again under UC-14. If the product has been published before, valid content and image changes apply directly without a new review.

**Update Quantity and Sale Status per Size**

1. The actor selects a size of the product.
2. The actor enters a new quantity, or selects Stop sale or Resume sale.
3. The system checks that the new quantity is not lower than the quantity currently held by valid holds, using the latest hold data.
4. For Resume sale, the system checks the size data and price; it does not publish the product.
5. The system saves the change, records an audit entry and confirms (MSG10). A stopped size keeps its quantity, shows a label and cannot be added to a cart; other sizes are not affected.

**Delete Product**

1. The actor selects Delete on a product.
2. The system asks for confirmation (MSG15).
3. The actor confirms.
4. The system soft-deletes the product, records the actor and time, removes it from the catalogue and the normal management list, and confirms (MSG16). SKUs and historical decisions are kept and never reused.

#### Alternative Flows

**All sub-flows — Actor is not authorized or the session has expired**

The system rejects the request without exposing internal product data (MSG01, MSG02) and records the rejected attempt according to the audit policy. Admin, Guest and Member cannot manage products.

**Create Step 3 / Update Step 4 — Invalid data**

The system identifies each invalid field (MSG08), such as a name or description out of range, an inactive category, a duplicate or out-of-range size, a non-positive price or a negative quantity, and saves nothing.

**Update Step 3 — Actor tries to add or change sizes**

The system rejects the change (MSG13). Sizes are fixed after the product is saved, including drafts and hidden products.

**Update Step 3 — Image is invalid or upload fails**

The system rejects an image that breaks the type, size, count or alt-text rules (MSG11). If storage fails, the system keeps the previous images, saves no broken link and allows a retry (MSG12).

**Update Step 3 — Actor removes the last usable image of a published product**

The system rejects the removal (MSG27).

**Update Step 4 / Quantity Step 3 — Data changed by another user**

The system rejects the stale change, asks the actor to reload and review the latest data (MSG03), and overwrites nothing.

**Quantity Step 3 — Quantity is lower than the held quantity**

The system rejects the new quantity and shows the held quantity (MSG14).

**Delete Step 3 — Actor cancels**

The system changes nothing.

**Any step after delete — Request targets a deleted product**

The system rejects editing, submitting, approving, applying a price, publishing, resuming sale or adding to a cart, including through an old link or code (MSG17).

**Any save — Save, audit or response failure**

The system keeps the previous state and saves no partial product, variant, image or audit record. If the response is lost, the actor checks the saved state before retrying; a retry of the same request does not create a duplicate product or SKU (MSG04).

#### Postconditions

- A valid new product exists as Unpublished with a Draft review record and one SKU per size.
- Valid updates are saved and audited; content changes before the first publication require a new review.
- Hiding, stopping sale, changing price or deleting does not cancel valid holds, change accepted orders or remove history.
- Failed or unauthorized operations leave the previous state unchanged.

#### Business Rules

BR-12-01, BR-12-02, BR-12-03, BR-12-04, BR-12-05, BR-12-06, BR-12-07, BR-12-08, BR-12-09, BR-12-10

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-12-01 | Only active Staff or Manager accounts with product-management permission may create, update or delete products; Admin manages accounts and roles but not products. |
| BR-12-02 | Name is 1–200 characters and description 1–5,000 characters after trimming; each product has one active category; proposed prices are positive whole VND and quantities are whole numbers of 0 or more. |
| BR-12-03 | A product has 1–25 distinct whole sizes from 5 to 29, one variant per size; sizes cannot be added or changed after the product is saved, and there are no engraving or customer-defined variants. |
| BR-12-04 | The product code is unique, stable and padded to at least 6 digits; each SKU `SP{product code}-S{size}` is read-only, unique, never changes and is never reused after deletion. |
| BR-12-05 | A new product is saved as Unpublished with a Draft review record and is never approved, priced or published automatically. |
| BR-12-06 | A product has at most 10 JPEG, PNG or WebP images, each at most 5,000,000 bytes, with alt text of 1–200 characters and a unique position; a failed image operation keeps the previous images. |
| BR-12-07 | Before the first publication, any content, image or proposed-price change invalidates the submitted or approved version and returns it to Draft; after the first publication, content and image changes apply directly. |
| BR-12-08 | Quantity is shared across all branches per size and cannot be set below the quantity held by valid holds; stop and resume sale apply to one size and do not change publication. |
| BR-12-09 | Delete is a confirmed soft delete; a deleted product cannot be edited, reviewed, priced, published, resumed or added to a cart, and its SKUs and history are kept. |
| BR-12-10 | Every change checks the latest version, saves all or nothing, is idempotent on retry and is audited with actor, object, version, change, time and result for 5 years. |
