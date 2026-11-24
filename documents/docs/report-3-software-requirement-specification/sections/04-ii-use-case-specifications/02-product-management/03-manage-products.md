### 2.3 Manage Products

#### Primary Actors

Staff, Manager with product-management permission.

#### Secondary Actors

Cloud Storage Service, where product images are stored; Workshop Management, which owns the shared branch list.

#### Description

As an authorized catalogue maintainer, I want to create draft products, find them by status, keep their content, images, sizes and stock at each branch up to date, control the sale of each size and at each branch, and delete products that are no longer sold, so that the catalogue data stays valid without changing accepted orders.

This use case includes the following sub-flows:

- Find Products in the Management List
- Create Draft Product
- Create Product as Manager
- Update Product Content and Images
- Manage Branch Stock
- Add Sizes at a Branch
- Update Quantity and Sale Status per Size
- Update Quantity of Several Sizes
- Delete Product

#### Preconditions

1. The actor is signed in through the shared Account Management sign-in, the account is active, and the current role is Staff or Manager with product-management permission.
2. For an update or delete, the product exists and has not been deleted.
3. For a stock change, the branch is an active branch from the shared branch list maintained by Workshop Management (a Manager may also change the quantity at an inactive branch), and a Staff account has at least one assigned branch.

#### Normal Flow

**Find Products in the Management List**

1. The actor opens the Product Management List. A Staff member with one assigned branch sees the list filtered on that branch; a Manager or a Staff member with several branches sees all branches.
2. The system shows the status tabs All, Drafts, Products to review, Rejected, Ready to publish, Published, Hidden and Prices to review, each with the number of products (for Prices to review, the number of prices waiting for a decision) that match the current search, category and branch. Each non-deleted product belongs to exactly one status tab, and the tab that needs the actor's action is highlighted when its count is above 0.
3. The actor selects a tab and optionally searches by product code, name or SKU and filters by category or branch. Filters apply as soon as they change (the search about 300 ms after typing stops or on Enter), return to the first page and are kept in the page address; Clear filters removes them.
4. The system lists 20 products per page in a fixed order; deleted products are not listed. Each row shows the product code and name, category, the total stock at the filtered branch (or at all branches) with the available quantity when it is lower, and the status tab name; a SKU match shows the matching size, and with a branch filter a product stopped at that branch shows "Selling stopped at this branch" (MSG66). A tab without products shows its own empty message (MSG67).
5. A Manager can publish, hide or show the product again from the row (UC-17). A Staff member, while the list is filtered on one of their active branches where the product is sold, can select Stop selling at branch or Resume selling at branch, as in Manage Branch Stock. Any actor can open the product's details.

**Create Draft Product**

1. The actor opens the Product Management List and selects New product, which opens the New Product screen.
2. The actor enters the name (1–200 characters), description (1–5,000 characters), an active category, 1–25 distinct sizes from 5 to 29, and for each size a proposed price (positive whole VND). The actor selects at least one active branch and enters, for each selected branch, a quantity for every size (whole number, 0 or more; default 0; each quantity field also has − and + buttons that change it by 1 and never below 0). The branch selector shows only active branches, and a Staff can select only assigned branches. Images are optional for a draft.
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

1. The actor finds the product as in Find Products in the Management List and opens it in Product Management Details.
2. The system displays the product, its sizes and SKUs, review status, proposed, approved and applied prices, publication status, images and, for each size at each branch, the remaining, held and available quantity and the sale status at that branch. For each price proposal and review version it also shows who submitted it and when, the submitter's branches at that time, and who decided it and when (see UC-14 and UC-15). A Staff sees every branch, but edit buttons outside the Staff's scope are hidden or disabled.
3. The actor changes the name, description or category, or adds, replaces, reorders or removes images (JPEG, PNG or WebP, up to 10 images, each at most 5,000,000 bytes, with alt text of 1–200 characters).
4. The system checks that the data is still the latest version and validates the changes.
5. The system saves the changes, records an audit entry and confirms (MSG10).
6. If the product has never been published, the change invalidates any submitted or approved version and returns the review record to Draft; the actor must submit it again under UC-14. If the product has been published before, valid content and image changes apply directly without a new review.

**Manage Branch Stock**

1. The actor selects Add branch, or selects a branch the product is already sold at.
2. To add a branch, the actor selects an active branch the product is not sold at yet, enters a quantity for every existing size (default 0) and may tick new sizes with their quantity and price, as in Add Sizes at a Branch. To change existing stock, the actor enters a new quantity for one size at that branch, or selects Stop sale at branch or Resume sale at branch.
3. The system checks, at the time of the request, that the branch is within the actor's scope: a Manager may act at every active branch; a Staff only at branches currently assigned to the account.
4. For a quantity change, the system checks that the new quantity is not lower than the quantity held by valid holds at that branch, using the latest hold data. Stock at other branches is not affected.
5. The system saves the change, records an audit entry and confirms (MSG10). At a branch where sale is stopped, the product keeps its quantities and history but customers cannot select that branch for it; other branches keep selling.

**Add Sizes at a Branch**

1. On the tab of a branch the product is sold at, the actor selects Add sizes. The button is not shown when the branch already has 25 sizes.
2. The actor chooses one or more sizes from 5 to 29 that the branch does not have yet. For a size the product already has, the actor enters only the quantity at this branch; for a new size, the actor also enters a price (a proposed price for a Staff, a selling price for a Manager).
3. The system checks the actor's branch scope, that the branch is active, the latest product version and every row.
4. The system saves all rows in one operation: a new size gets its SKU, is on sale and has no applied price yet; only this branch gets a stock row for it, and other branches do not sell it until they add their own quantity. The system records an audit entry.
5. For a product that has been published before, the screen then sends the price of each new size as its own command: for a Staff, the proposal is submitted for approval (UC-14) and the size cannot be bought until a Manager approves and applies the price; for a Manager, the proposal is submitted, approved at the entered price (UC-15) and, when "Apply the price right after approving" stays ticked, applied (UC-16). For a product that has never been published, the price of a new size joins the initial review; a Pending, Approved or Rejected review returns to Draft and must be submitted again.
6. The system confirms which sizes were added and the state of their prices (MSG64). A size of the product without a stock row at the branch being viewed shows "Not sold at this branch" (MSG65).

**Update Quantity and Sale Status per Size**

1. The actor selects a size of the product.
2. The actor enters a new quantity for the size at a branch, or selects Stop sale or Resume sale for the size.
3. The system checks that the new quantity is not lower than the quantity currently held by valid holds at that branch, using the latest hold data.
4. For Stop sale or Resume sale by a Staff, the system checks that every active branch that sells the size is assigned to the Staff; inactive branches are not counted. For Resume sale, the system also checks the size data and price; it does not publish the product.
5. The system saves the change, records an audit entry and confirms (MSG10). A stopped size keeps its quantity at every branch, shows a label and cannot be added to a cart at any branch; other sizes are not affected.

**Update Quantity of Several Sizes**

1. The actor opens Edit quantities of several sizes for one branch and selects sizes one by one or as a size range.
2. The actor enters one number and chooses Add (added to the remaining quantity, for a restock) or Set to (replaces it), then may still change any row.
3. The system checks every changed size as in step 3 above before sending anything; only sizes whose quantity changes are saved.
4. The system saves each size as its own change with its own audit entry; a size that fails does not affect the others. It confirms with the number of sizes saved (MSG10), or lists the sizes that failed and why (MSG54).

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

**Branch Stock Step 2 — Branch has been deactivated**

Stock that remains at an inactive branch is shown as "Branch #{id}" with a note that the branch is no longer active, and has no Stop sale or Resume sale button. Only a Manager can change its quantity, for example to count stock or set it to 0 when the branch closes, through the per-branch or the per-size quantity change. Adding an inactive branch, changing the sale status there, or any change there by a Staff is rejected as an unknown branch (MSG08). If the branch source cannot answer, the request is not rejected for this reason. Customers still cannot buy at an inactive branch.

**Update Step 3 — Actor tries to change or remove a size**

The system rejects the change (MSG13). Saved sizes and SKUs cannot be changed or removed, including in drafts and hidden products; new sizes are added through Add Sizes at a Branch.

**Add Sizes Step 3 — Size already exists at the branch**

The system rejects the whole request (MSG62) when a chosen size already has stock at this branch, or a new size of an Add branch request already exists on the product, and creates nothing.

**Add Sizes Step 3 — Product is not sold at the branch, or the branch is inactive**

The system rejects the request (MSG63) when the product has no stock at the branch, or treats an inactive branch as unknown (MSG08), and creates nothing.

**Add Sizes Step 3 — Invalid row**

The system identifies the invalid row (MSG08), such as a size outside 5–29, a size chosen twice, a new size without a price or a price sent for an existing size, and creates nothing.

**Add Sizes Step 5 — A price step fails**

The added sizes are kept. A Staff member's price that could not be submitted stays under Drafts not sent in the Prices to review tab; for a Manager, the steps already done are kept. The system reports the size whose price is not finished (MSG73), and the actor carries on in the Price proposals section.

**Find Step 2 — Tab counts cannot be loaded**

The list still loads and the tabs are shown without counts.

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
- Sizes added later exist only at the branch where they were added, with their prices following UC-14 to UC-16.
- Valid updates are saved and audited; content changes before the first publication require a new review.
- Hiding, stopping sale, changing price or deleting does not cancel valid holds, change accepted orders or remove history.
- Failed or unauthorized operations leave the previous state unchanged.

#### Business Rules

GBR-15, GBR-16, GBR-24, GBR-33, GBR-34, BR-12-01, BR-12-02, BR-12-03, BR-12-04, BR-12-05, BR-12-06, BR-12-07, BR-12-08, BR-12-09, BR-12-10, BR-12-11

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| GBR-15 | A product is created with 1–25 sizes from 5 to 29, each with a fixed SKU; saved sizes cannot be changed or removed, but a new size may be added later at one selling branch. Quantity is kept per size at each branch and the price is shared by all branches. |
| GBR-16 | Hiding, stopping sale of a size or at a branch, changing price or deleting a product never cancels valid holds, changes accepted orders or removes history; deletion is a soft delete. |
| GBR-24 | Quantity and sale status are kept per size at each branch; a Staff member manages stock only at assigned active branches and changes content, images and proposed prices only of products sold at one of them. |
| GBR-33 | After a product's first publication, a size without an applied price, such as a newly added size, does not remove the product from publication but is not shown publicly or sold until its approved price is applied. |
| GBR-34 | Each non-deleted product belongs to exactly one management status tab; price changes of published products are followed in the Prices to review tab, and tab counts follow the current search, category and branch filters. |
| BR-12-01 | Only active Staff or Manager accounts with product-management permission may create, update or delete products; Admin manages accounts and roles but not products. |
| BR-12-02 | Name is 1–200 characters and description 1–5,000 characters after trimming; each product has one active category; proposed prices are positive whole VND and quantities are whole numbers of 0 or more. |
| BR-12-03 | A product is created with 1–25 distinct whole sizes from 5 to 29, one variant per size; saved sizes cannot be changed or removed, a size the product does not have yet can be added at a selling branch, and there are no engraving or customer-defined variants. |
| BR-12-04 | The product code is unique, stable and padded to at least 6 digits; each SKU `SP{product code}-S{size}` is read-only, unique, never changes and is never reused after deletion. |
| BR-12-05 | A new product is saved as Unpublished with a Draft review record and the create command never approves, prices or publishes it. A Manager's create screen may send the submission, approval, price application and publication as separate commands, each checked and audited on its own; a failure in a later step does not undo the created product. Product names do not have to be unique; a duplicate name only shows a warning. |
| BR-12-06 | A product has at most 10 JPEG, PNG or WebP images, each at most 5,000,000 bytes, with alt text of 1–200 characters and a unique position; a failed image operation keeps the previous images. |
| BR-12-07 | Before the first publication, any content, image or proposed-price change invalidates the submitted or approved version and returns it to Draft; after the first publication, content and image changes apply directly. |
| BR-12-08 | Quantity is kept per size at each branch and cannot be set below the quantity held by valid holds at that branch; the total of a size is for display only. Stop and resume sale of a size apply at every branch; stop and resume sale at a branch apply to the whole product at that branch; neither changes publication. Prices are shared by all branches. Editing several sizes at once is only a way of entering values: each size is still saved and audited on its own. |
| BR-12-09 | Delete is a confirmed soft delete; a deleted product cannot be edited, reviewed, priced, published, resumed or added to a cart, and its SKUs and history are kept. |
| BR-12-10 | Every change checks the latest version, saves all or nothing, is idempotent on retry and is audited with actor, object, version, change, time and result for 5 years. |
| BR-12-11 | A product is sold at one or more active branches from the shared branch list and has at most one stock row per size at each of those branches: a branch added later gets a row for every existing size, while a size added later has a row only at the branch where it was added; stock rows are never deleted. A Manager manages stock at every active branch and may only change the quantity at an inactive branch; a Staff manages stock only at active branches currently assigned to the account, checked by the system at each request, and stops or resumes a size only when every active branch selling it is assigned. A Staff member stops or resumes selling at their own branch instead of hiding the product. The branch selector shows only active branches. A customer can buy a size only at a branch where the product and size are on sale, the branch is active and the available quantity is above 0. |
