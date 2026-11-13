### 2.3 Manage Products

#### Primary Actors

Staff, Manager with product-management permission.

#### Secondary Actors

Cloud Storage Service, where product images are stored; Workshop Management, which owns the shared branch list.

#### Description

As an authorized catalogue maintainer, I want to create draft products, keep their content, images and stock at each branch up to date, control the sale of each size and at each branch, and delete products that are no longer sold, so that the catalogue data stays valid without changing accepted orders.

This use case includes the following sub-flows:

- Create Draft Product
- Create Product as Manager
- Update Product Content and Images
- Manage Branch Stock
- Update Quantity and Sale Status per Size
- Update Quantity of Several Sizes
- Delete Product

#### Preconditions

1. The actor is signed in through the shared Account Management sign-in, the account is active, and the current role is Staff or Manager with product-management permission.
2. For an update or delete, the product exists and has not been deleted.
3. For a stock change, the branch is an active branch from the shared branch list maintained by Workshop Management, and a Staff account has at least one assigned branch.

#### Normal Flow

**Create Draft Product**

1. The actor opens Product Management and selects New product.
2. The actor enters the name (1–200 characters), description (1–5,000 characters), an active category, 1–25 distinct sizes from 5 to 29, and for each size a proposed price (positive whole VND). The actor selects at least one active branch and enters, for each selected branch, a quantity for every size (whole number, 0 or more; default 0). A Staff can select only assigned branches. Images are optional for a draft.
3. The system validates all data.
4. The system assigns a stable numeric product code and one read-only SKU per size in the format `SP{product code}-S{size}`, for example `SP000001-S09`.
5. The system saves the product as Unpublished with a Draft review record, creates one stock row per size at each selected branch, marks each size as on sale, and records an audit entry. The product is not approved, priced or published automatically.
6. The system shows the product code and SKUs (MSG09).

**Create Product as Manager**

1. A Manager opens New product. The price of each size is labelled Selling price, at least one image is required, and the Manager chooses After creation: Unpublished (default; the price is applied but customers do not see the product) or Publish now.
2. The Manager enters the data as in Create Draft Product and saves.
3. The system creates the product as in Create Draft Product steps 3–6.
4. The screen then sends, one after another, the submission (UC-14), the approval with the entered prices (UC-15), the application of the price of each size (UC-16) and, if chosen, the publication (UC-17). Each step is its own command with its own record and audit entry, and the system checks every condition again.
5. The system confirms the result of the last step. Staff do not have this option and submit the product for approval under UC-14.

**Update Product Content and Images**

1. The actor finds the product in the management list, searching by product code, name or SKU and filtering by category, publication status or branch, 20 products per page, newest first; deleted products are not listed. A SKU match shows the matching size. The actor opens the product.
2. The system displays the product, its sizes and SKUs, review status, proposed, approved and applied prices, publication status, images and, for each size at each branch, the remaining, held and available quantity and the sale status at that branch.
3. The actor changes the name, description or category, or adds, replaces, reorders or removes images (JPEG, PNG or WebP, up to 10 images, each at most 5,000,000 bytes, with alt text of 1–200 characters).
4. The system checks that the data is still the latest version and validates the changes.
5. The system saves the changes, records an audit entry and confirms (MSG10).
6. If the product has never been published, the change invalidates any submitted or approved version and returns the review record to Draft; the actor must submit it again under UC-14. If the product has been published before, valid content and image changes apply directly without a new review.

**Manage Branch Stock**

1. The actor selects Add branch, or selects a branch the product is already sold at.
2. To add a branch, the actor selects an active branch the product is not sold at yet and enters a quantity for every size (default 0). To change existing stock, the actor enters a new quantity for one size at that branch, or selects Stop sale at branch or Resume sale at branch.
3. The system checks, at the time of the request, that the branch is within the actor's scope: a Manager may act at every branch; a Staff only at branches currently assigned to the account.
4. For a quantity change, the system checks that the new quantity is not lower than the quantity held by valid holds at that branch, using the latest hold data. Stock at other branches is not affected.
5. The system saves the change, records an audit entry and confirms (MSG10). At a branch where sale is stopped, the product keeps its quantities and history but customers cannot select that branch for it; other branches keep selling.

**Update Quantity and Sale Status per Size**

1. The actor selects a size of the product.
2. The actor enters a new quantity for the size at a branch, or selects Stop sale or Resume sale for the size.
3. The system checks that the new quantity is not lower than the quantity currently held by valid holds at that branch, using the latest hold data.
4. For Resume sale, the system checks the size data and price; it does not publish the product.
5. The system saves the change, records an audit entry and confirms (MSG10). A stopped size keeps its quantity at every branch, shows a label and cannot be added to a cart at any branch; other sizes are not affected.

**Update Quantity of Several Sizes**

1. The actor opens Edit quantities of several sizes for one branch and selects sizes one by one or as a size range.
2. The actor enters one number and chooses Add (added to the remaining quantity, for a restock) or Set to (replaces it), then may still change any row.
3. The system checks every changed size as in step 3 above before sending anything; only sizes whose quantity changes are saved.
4. The system saves each size as its own change with its own audit entry; a size that fails does not affect the others. It confirms with the number of sizes saved (MSG10), or lists the sizes that failed and why.

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

**Create Step 2 — Another product already has the same name**

The form shows "{N} products already have this name" with a link to each one (MSG56). Names are compared after trimming, collapsing extra spaces and ignoring case; Vietnamese accents still count. The warning does not block saving, and it is not shown if the check cannot be loaded.

**Manager Create Step 4 — A later step fails**

The product that was saved is kept. The detail page reports the step that failed, and Publish now continues from the missing step.

**Create Step 2 / Branch Stock Step 3 — Branch is outside the actor's scope**

The system rejects the request (MSG01) and changes nothing, even when the request is sent directly to the API. A Staff with no assigned branch cannot create products or change stock. A Staff can view the products and stock of every branch, but can change content, images and proposed prices only for a product sold at one or more of the Staff's assigned branches.

**Create Step 3 — No branch selected, or the branch is inactive or unknown**

The system identifies the branch field as invalid (MSG08) and creates nothing.

**Branch Stock Step 2 — Branch is already added**

The system rejects the request (MSG08) and creates no duplicate stock row. A stock row is never deleted; stopping sale at the branch is used instead of removing it.

**Update Step 3 — Actor tries to add or change sizes**

The system rejects the change (MSG13). Sizes are fixed after the product is saved, including drafts and hidden products.

**Update Step 3 — Image is invalid or upload fails**

The system rejects an image that breaks the type, size, count or alt-text rules (MSG11). If storage fails, the system keeps the previous images, saves no broken link and allows a retry (MSG12).

**Update Step 3 — Actor removes the last usable image of a published product**

The system rejects the removal (MSG27).

**Update Step 4 / Quantity Step 3 / Branch Stock Step 4 — Data changed by another user**

The system rejects the stale change, asks the actor to reload and review the latest data (MSG03), and overwrites nothing.

**Quantity Step 3 / Branch Stock Step 4 — Quantity is lower than the held quantity**

The system rejects the new quantity and shows the quantity held at that branch (MSG14).

**Delete Step 3 — Actor cancels**

The system changes nothing.

**Any step after delete — Request targets a deleted product**

The system rejects editing, submitting, approving, applying a price, publishing, resuming sale or adding to a cart, including through an old link or code (MSG17).

**Any save — Save, audit or response failure**

The system keeps the previous state and saves no partial product, variant, image or audit record. If the response is lost, the actor checks the saved state before retrying; a retry of the same request does not create a duplicate product or SKU (MSG04).

#### Postconditions

- A valid new product exists as Unpublished with a Draft review record, one SKU per size and one stock row per size at each selected branch.
- Valid updates are saved and audited; content changes before the first publication require a new review.
- Hiding, stopping sale, changing price or deleting does not cancel valid holds, change accepted orders or remove history.
- Failed or unauthorized operations leave the previous state unchanged.

#### Business Rules

BR-12-01, BR-12-02, BR-12-03, BR-12-04, BR-12-05, BR-12-06, BR-12-07, BR-12-08, BR-12-09, BR-12-10, BR-12-11

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-12-01 | Only active Staff or Manager accounts with product-management permission may create, update or delete products; Admin manages accounts and roles but not products. |
| BR-12-02 | Name is 1–200 characters and description 1–5,000 characters after trimming; each product has one active category; proposed prices are positive whole VND and quantities are whole numbers of 0 or more. |
| BR-12-03 | A product has 1–25 distinct whole sizes from 5 to 29, one variant per size; sizes cannot be added or changed after the product is saved, and there are no engraving or customer-defined variants. |
| BR-12-04 | The product code is unique, stable and padded to at least 6 digits; each SKU `SP{product code}-S{size}` is read-only, unique, never changes and is never reused after deletion. |
| BR-12-05 | A new product is saved as Unpublished with a Draft review record and the create command never approves, prices or publishes it. A Manager's create screen may send the submission, approval, price application and publication as separate commands, each checked and audited on its own; a failure in a later step does not undo the created product. Product names do not have to be unique; a duplicate name only shows a warning. |
| BR-12-06 | A product has at most 10 JPEG, PNG or WebP images, each at most 5,000,000 bytes, with alt text of 1–200 characters and a unique position; a failed image operation keeps the previous images. |
| BR-12-07 | Before the first publication, any content, image or proposed-price change invalidates the submitted or approved version and returns it to Draft; after the first publication, content and image changes apply directly. |
| BR-12-08 | Quantity is kept per size at each branch and cannot be set below the quantity held by valid holds at that branch; the total of a size is for display only. Stop and resume sale of a size apply at every branch; stop and resume sale at a branch apply to the whole product at that branch; neither changes publication. Prices are shared by all branches. Editing several sizes at once is only a way of entering values: each size is still saved and audited on its own. |
| BR-12-09 | Delete is a confirmed soft delete; a deleted product cannot be edited, reviewed, priced, published, resumed or added to a cart, and its SKUs and history are kept. |
| BR-12-10 | Every change checks the latest version, saves all or nothing, is idempotent on retry and is audited with actor, object, version, change, time and result for 5 years. |
| BR-12-11 | A product is sold at one or more active branches from the shared branch list and has exactly one stock row per size at each of those branches; stock rows are never deleted. A Manager manages stock at every branch; a Staff only at branches currently assigned to the account, checked by the system at each request. A customer can buy a size only at a branch where the product and size are on sale, the branch is active and the available quantity is above 0. |
