### 2.2 Manage Products

| Use Case ID | UC-12 | Use Case Name | Manage Products |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | Cloud Storage Service, Workshop Management |

#### Description

As an authorized catalogue maintainer, I want to create products manually or from Excel, update them, manage stock and sale status, and soft-delete them so that catalogue data stays accurate without altering accepted orders.

#### Trigger

A Staff or Manager with product-management permission opens the Product Management List or Import from Excel.

#### Preconditions

1. Actor is signed in with an active Staff or Manager account and product-management permission.
2. For an update or delete, the product exists and is not deleted.
3. For creating a product or changing stock, a Staff member has at least one assigned active branch.
4. For an Excel import, at least one active category and one active branch within Actor's scope exist.

#### Postconditions

1. A new or imported product is saved as Unpublished with a Draft review record, SKUs, and branch stock rows.
2. Sizes added later exist only at the adding branch and follow UC-14 and UC-15 for pricing.
3. Valid updates are saved and audited; before the first publication, content changes and new sizes reset the review to Draft.
4. Hiding, stopping sale, price changes, or deletion never cancel valid holds, accepted orders, or history.
5. Failed, rejected, or unauthorized operations leave the previous state unchanged and are audited.

#### Normal Flow

**Find Products in the Management List**

1. Actor opens the Product Management List.
2. System displays status tabs with counts matching current search, category, and branch filters.
3. Actor selects a status tab and enters search keywords or category and branch filters.
4. System lists 20 non-deleted products per page, showing branch-stopped labels (MSG66) or empty-tab messages (MSG67).
5. Actor opens a product's details, publishes/hides as Manager (UC-15), or toggles branch sale as Staff.

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
4. System sends submission (UC-14), then approval, price application, and optional publication (UC-15) sequentially.
5. System confirms the result of the final completed step.

**Update Product Content and Images**

1. Actor opens a product in Product Management Details.
2. System displays product details, SKUs, review history, prices, images, and per-branch stock.
3. Actor updates name, description, category, or images within size, format, count, and alt-text limits.
4. System verifies the latest product version and validates all changes.
5. System saves changes, records an audit entry, and confirms (MSG10); pre-publication edits reset review to Draft.

**Manage Branch Stock**

1. Actor selects Add branch or chooses an existing selling branch.
2. Actor enters quantities and optional new sizes with proposed prices for a new branch, updates a size quantity, or toggles branch sale status.
3. System verifies the branch is within Actor's scope.
4. System checks that any new quantity is not below valid holds at that branch.
5. System saves the change, records an audit entry, and confirms (MSG10).

**Add Sizes at a Branch**

1. Actor selects Add sizes on a selling branch tab.
2. Actor selects new sizes (5–29), enters quantities, and enters prices for sizes new to the product.
3. System verifies branch scope, active branch status, latest version, and all row values.
4. System saves new stock rows and SKUs in one operation and records an audit entry.
5. On a product published before, System sends Staff's new size prices for approval (UC-14), and a Manager's prices through approval and optional application (UC-15).
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

**Import Products from Excel**

1. Actor opens Import from Excel and selects active branches within scope.
2. Actor downloads the `.xlsx` template containing Group code, details, size, price, and branch quantity columns.
3. Actor fills in the template rows and uploads the `.xlsx` file.
4. System validates file type, size, row count, columns, and all row rules without creating data.
5. System displays a 60-minute preview with product/variant counts, duplicate-name warnings (MSG56), and validation summary (MSG31).
6. Actor confirms the import.
7. System revalidates the file against the latest data and active categories.
8. System creates all products atomically as Unpublished drafts with SKUs and branch stock, and logs an audit entry.
9. System displays the created product codes and SKUs (MSG34).

**Attach Images to Imported Products**

1. System offers to attach images to the newly imported products on the result screen.
2. Actor selects images, verifies auto-matched products by code or name, and edits alt text.
3. System validates image types, 5,000,000-byte limit, 10-image cap, and alt text before sending.
4. Actor selects Save; System saves images per product as in Update Product Content and Images.
5. System confirms image save results for each product (MSG57).

#### Alternative Flows

F2a. Tab counts fail to load → Management list still loads with tabs shown without counts.

C2a. Another product already has the same name → Form displays a duplicate-name warning with links (MSG56) without blocking save.

B1a. Product has no branch yet after the data migration → Actor assigns its first branch; the previous quantities are prefilled and moved to that branch.

B2a. Manager updates quantity at a deactivated branch → System allows quantity-only edits at "Branch #{id}" while blocking Stop/Resume sale.

I8a. Import confirmation is sent twice → System avoids duplicates and returns the completed import result.

P1a. Actor skips attaching images → Actor exits the result screen and adds images later on the product detail page.

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

I1a. Selected import branch is outside Actor's scope → System rejects the request (MSG01).

I4a. File is invalid, oversized, over 500 rows, or columns mismatch → System rejects the file (MSG30).

I5a. Rows contain validation errors → System lists errors with row, column, value, and reason (MSG32), blocks Confirm, and offers a neutralized error report.

I6a. Actor does not confirm → System creates no product, code, or SKU.

I7a. Preview expired (> 60 minutes) or data changed → System rejects confirmation, creates nothing, and prompts revalidation (MSG33).

I8b. Import save or audit fails → System rolls back all products, SKUs, and audits and reports a safe error (MSG04).

P4a. Image upload fails for one product → Other products stay unaffected; System reports per-product results (MSG57) and allows retry.

#### Business Rules

GBR-15, GBR-16, GBR-17, GBR-24, GBR-33, GBR-34, BR-12-01, BR-12-02, BR-12-03, BR-12-04, BR-12-05, BR-12-06, BR-12-07, BR-12-08, BR-12-09, BR-12-10, BR-12-11, BR-12-12, BR-12-13, BR-12-14, BR-12-15, BR-12-16, BR-12-17, BR-12-18

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG03, MSG04, MSG08, MSG09, MSG10, MSG11, MSG12, MSG13, MSG14, MSG15, MSG16, MSG17, MSG27, MSG30, MSG31, MSG32, MSG33, MSG34, MSG54, MSG56, MSG57, MSG62, MSG63, MSG64, MSG65, MSG66, MSG67, MSG73

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| GBR-15 | A product is created with 1–25 sizes from 5 to 29, each with a fixed SKU; saved sizes cannot be changed or removed, but a new size may be added later at one selling branch. Quantity is kept per size at each branch and the price is shared by all branches. |
| GBR-16 | Hiding, stopping sale of a size or at a branch, changing price or deleting a product never cancels valid holds, changes accepted orders or removes history; deletion is a soft delete. |
| GBR-17 | Excel import creates draft products under the same rules as manual creation, validates the whole file first, is all or nothing, takes quantities per selected branch within the actor's scope, and its preview expires after 60 minutes. |
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
| BR-12-12 | Import is another way of creating drafts: it uses the same validation, code and SKU rules and the same result as manual creation, and never submits, approves, prices or publishes a product. Import only creates new products; it does not add sizes to existing ones. |
| BR-12-13 | The import file must be a valid macro-free `.xlsx`, at most 5,000,000 bytes and 500 data rows; only the first sheet is read, cells are read as data, and formulas and external links are not executed. |
| BR-12-14 | The template columns are fixed, with one quantity column per selected branch; rows with the same Group code must have the same name, description and category and together form one product of 1–25 distinct sizes. The selected branches must be within the actor's scope. |
| BR-12-15 | Imported numbers must match the manual-creation ranges exactly; values such as `12.5` or `1e3` are not rounded or converted. |
| BR-12-16 | The system validates the whole file and shows a preview before creating anything; the import is all or nothing, is revalidated on confirmation, and a preview expires after 60 minutes. A duplicate product name is only a warning. |
| BR-12-17 | Import confirmation is idempotent, and each import is audited with the actor, file name, row, product and variant counts, created codes, time and result, without storing the file content. |
| BR-12-18 | The import file contains no images. Attaching images after an import is only a way of entering them: each product uses the normal image operation, a product that fails does not affect the others, and no product is submitted for approval automatically. |
