### 2.3 Manage Products

| Use Case ID | UC-12 | Use Case Name | Manage Products |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | Cloud Storage Service, Workshop Management |

#### Description

As an authorized catalogue maintainer, I want to create, update, manage stock and sale status, and soft-delete products so that catalogue data stays accurate without altering accepted orders.

#### Trigger

A Staff or Manager with product-management permission opens the Product Management List.

#### Preconditions

1. Actor is signed in with an active Staff or Manager account and product-management permission.
2. For an update or delete, the product exists and is not deleted.
3. For a stock change, the branch is active in Workshop Management and Staff has at least one assigned branch.

#### Postconditions

1. A new product is saved as Unpublished with a Draft review record, SKUs, and branch stock rows.
2. Sizes added later exist only at the adding branch and follow UC-14 to UC-16 for pricing.
3. Valid updates are saved and audited; pre-publication content changes reset the review to Draft.
4. Hiding, stopping sale, price changes, or deletion never cancel valid holds, accepted orders, or history.
5. Failed or unauthorized operations leave the previous state unchanged.

#### Normal Flow

**Find Products in the Management List**

1. Actor opens the Product Management List.
2. System displays status tabs with counts matching current search, category, and branch filters.
3. Actor selects a status tab and enters search keywords or category and branch filters.
4. System lists 20 non-deleted products per page, showing branch-stopped labels (MSG66) or empty-tab messages (MSG67).
5. Actor opens a product's details, publishes/hides as Manager (UC-17), or toggles branch sale as Staff.

**Create Draft Product**

1. Actor selects New product on the Product Management List.
2. Actor enters name, description, active category, 1–25 sizes (5–29), proposed prices, branches, and quantities.
3. System validates all submitted product, size, price, and branch data.
4. System assigns a unique product code and one read-only SKU per size.
5. System saves the product as Unpublished with a Draft review record, stock rows, and an audit entry.
6. System displays the created product code and SKUs (MSG09).

**Create Product as Manager**

1. Manager opens New product, enters product details, at least one image, selling prices, and post-creation publication choice.
2. Manager saves the new product.
3. System creates the draft product as in Create Draft Product steps 3–6.
4. System sends submission (UC-14), approval (UC-15), price application (UC-16), and optional publication (UC-17) sequentially.
5. System confirms the result of the final completed step.

**Update Product Content and Images**

1. Actor opens a product in Product Management Details.
2. System displays product details, SKUs, review history, prices, images, and per-branch stock.
3. Actor updates name, description, category, or images within size, format, count, and alt-text limits.
4. System verifies the latest product version and validates all changes.
5. System saves changes, records an audit entry, and confirms (MSG10); pre-publication edits reset review to Draft.

**Manage Branch Stock**

1. Actor selects Add branch or chooses an existing selling branch.
2. Actor enters quantities for a new branch, updates a size quantity, or toggles branch sale status.
3. System verifies the branch is within Actor's scope.
4. System checks that any new quantity is not below valid holds at that branch.
5. System saves the change, records an audit entry, and confirms (MSG10).

**Add Sizes at a Branch**

1. Actor selects Add sizes on a selling branch tab.
2. Actor selects new sizes (5–29), enters quantities, and enters prices for sizes new to the product.
3. System verifies branch scope, active branch status, latest version, and all row values.
4. System saves new stock rows and SKUs in one operation and records an audit entry.
5. System submits, approves, and optionally applies new size prices per UC-14 to UC-16.
6. System confirms added sizes and price states (MSG64); unstocked sizes at a branch show MSG65.

**Update Quantity and Sale Status per Size**

1. Actor selects a product size.
2. Actor enters a new branch quantity or selects Stop sale or Resume sale for the size.
3. System checks that the new quantity is not below valid holds at that branch.
4. System verifies Staff branch assignment across all active selling branches for size sale-status changes.
5. System saves the change, records an audit entry, and confirms (MSG10).

**Update Quantity of Several Sizes**

1. Actor opens Edit quantities of several sizes for one branch and selects sizes.
2. Actor enters a value and chooses Add or Set to, adjusting individual rows as needed.
3. System validates every changed size against valid holds before sending.
4. System saves and audits each changed size individually and confirms the saved count (MSG10).

**Delete Product**

1. Actor selects Delete on a product.
2. System requests confirmation (MSG15).
3. Actor confirms deletion.
4. System soft-deletes the product, records actor and time, removes it from public/management lists, and confirms (MSG16).

#### Alternative Flows

F2a. Tab counts fail to load → Management list still loads with tabs shown without counts.

C2a. Another product already has the same name → Form displays a duplicate-name warning with links (MSG56) without blocking save.

B2a. Manager updates quantity at a deactivated branch → System allows quantity-only edits at "Branch #{id}" while blocking Stop/Resume sale.

#### Exception Flows

A1a. Actor is unauthorized or session expired → System rejects the request without exposing internal data (MSG01, MSG02) and logs it.

C2b. Branch is outside Actor's scope → System rejects the request (MSG01) and makes no changes.

C3a. No branch selected, or branch is inactive/unknown → System flags the branch field as invalid (MSG08) and creates nothing.

C3b. Submitted product, size, price, or quantity is invalid → System flags invalid fields (MSG08) and saves nothing.

M4a. A later Manager creation step fails → Saved product is kept; detail page reports the failed step for continuation.

U3a. Actor attempts to change or remove a saved size → System rejects the change (MSG13).

U3b. Image breaks validation rules or upload fails → System rejects invalid images (MSG11) or keeps previous images on storage failure (MSG12).

U3c. Actor removes the last usable image of a published product → System rejects the removal (MSG27).

B2b. Branch is already added → System rejects the request (MSG08) and creates no duplicate stock row.

B2c. Inactive branch change is attempted by Staff or status toggled → System rejects it as an unknown branch (MSG08).

S3a. Size already exists at the branch → System rejects the whole request (MSG62) and creates nothing.

S3b. Product is not sold at the branch, or branch is inactive → System rejects the request (MSG63, MSG08) and creates nothing.

S3c. Size row is invalid → System identifies the invalid row (MSG08) and creates nothing.

S5a. A price step fails after adding sizes → Added sizes are kept; System reports the unfinished size (MSG73) for follow-up.

U4a. Data was modified concurrently by another user → System rejects the stale change and prompts reload (MSG03).

Q3a. New quantity is below held quantity → System rejects the update and displays the held quantity (MSG14).

E4a. One or more sizes fail in batch edit → Valid sizes are saved; System lists failed sizes and reasons (MSG54).

D3a. Actor cancels deletion → System makes no changes.

D4a. Request targets a soft-deleted product → System rejects all edits, reviews, pricing, publication, or cart actions (MSG17).

A5a. Save, audit, or response fails → System preserves previous state; idempotent retry avoids duplicate products or SKUs (MSG04).

#### Business Rules

GBR-15, GBR-16, GBR-24, GBR-33, GBR-34, BR-12-01, BR-12-02, BR-12-03, BR-12-04, BR-12-05, BR-12-06, BR-12-07, BR-12-08, BR-12-09, BR-12-10, BR-12-11

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG03, MSG04, MSG08, MSG09, MSG10, MSG11, MSG12, MSG13, MSG14, MSG15, MSG16, MSG17, MSG27, MSG54, MSG56, MSG62, MSG63, MSG64, MSG65, MSG66, MSG67, MSG73

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
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
