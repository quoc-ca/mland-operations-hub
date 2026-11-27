### 2.3 UC-32 Manage Products

#### UC Name

UC-32 Manage Products

#### Primary Actors

Staff, Manager

#### Secondary Actors

Cloud Storage Service (Cloudinary)

#### Description

As a Staff member or Manager, I want to create, import and maintain products, their images, applied prices, sale status and branch stock so that the catalogue stays accurate.

#### Preconditions

- The actor is signed in with an active Staff or Manager account.
- A Staff member has at least one assigned active branch to create products or change stock.
- At least one active category exists to create or import a product.
- For a change, the product exists and is not deleted.

#### Normal Flow

***Create Draft Product***

1. The actor opens the Product Management List and selects New product.
2. The system shows the product form with active categories and the branches in the actor's scope.
3. The actor enters the name, description and category.
4. The actor selects 1–25 sizes from 5 to 29 and enters a proposed price for each size.
5. The actor selects the selling branches and enters the quantity of each size at each branch.
6. The actor selects Create product.
7. The system validates the content, sizes, prices, branches and quantities.
8. The system assigns a product code and one read-only SKU per size.
9. The system saves the product as unpublished with a draft review, branch stock rows and an audit entry.
10. The system confirms that the draft product has been created (MSG09).

#### Alternative Flows

***Step 1.1 Actor Finds a Product in the Management List***

1. The actor selects a status tab and enters a keyword, category or branch filter.
2. The system lists 20 matching non-deleted products per page with the tab counts.
3. The system shows an empty-tab text when the tab has no product (MSG65).
4. The system marks a product stopped at the filtered branch (MSG64).
5. The use case ends after viewing the list.

***Step 1.2 Actor Imports Products from Excel***

1. The actor selects Import from Excel and chooses active branches within the actor's scope.
2. The actor downloads the template, fills in the rows and uploads the file.
3. The system validates the whole file without creating any data.
4. The system shows a preview valid for 60 minutes with counts and duplicate-name warnings (MSG31, MSG55).
5. The actor selects Confirm import and confirms the dialog.
6. The system revalidates the file and creates every product as a draft in one transaction.
7. The system lists the created product codes (MSG34).
8. The use case continues at step 1.3.

***Step 1.3 Actor Attaches Images to Imported Products***

1. The actor picks images for the imported products and enters alt text.
2. The actor selects Save images.
3. The system stores the images of each product separately in Cloudinary.
4. The system reports the products saved and the products that failed (MSG56).
5. The use case ends.

***Step 1.4 Actor Updates Content or Images***

1. The actor opens a product and edits the name, description, category or images.
2. The system validates the change and stores new images in Cloudinary.
3. The system saves the change and records an audit entry (MSG10).
4. The system returns a never-published product's review to draft.
5. The use case ends.

***Step 1.5 Actor Applies an Approved Price***

1. The actor selects Apply approved price on a size, or on several sizes.
2. The system checks that each approved decision is still valid for that size and amount.
3. The system sets the approved amount as the applied price and records the previous price.
4. The system confirms that the price has been applied (MSG24).
5. The use case ends.

***Step 1.6 Actor Changes the Quantity of a Size***

1. The actor enters a new quantity of a size at a branch, or for several sizes at one branch.
2. The system checks that each quantity is not below the units held at that branch.
3. The system saves each size separately with an audit entry (MSG10).
4. The use case ends.

***Step 1.7 Actor Stops or Resumes Sale***

1. The actor selects Stop or Resume sale for a size, or for the product at one branch.
2. The system saves the sale status without changing quantity or publication (MSG10).
3. The use case ends.

***Step 1.8 Actor Adds a Selling Branch or Sizes***

1. The actor selects Add a selling branch or Add sizes on a branch tab.
2. The actor enters quantities and, for each size new to the product, a proposed price.
3. The system saves the stock rows, new SKUs and price proposals in one transaction.
4. The system confirms the added sizes and their price state (MSG63).
5. The use case ends.

***Step 1.9 Actor Deletes a Product***

1. The actor selects Delete product.
2. The system asks the actor to confirm the deletion (MSG15).
3. The actor confirms the deletion.
4. The system soft-deletes the product, keeps its SKUs and history, and records an audit entry.
5. The system confirms the deletion (MSG16).
6. The use case ends.

***Step 1.10 Manager Creates a Product Without Review***

1. The Manager enters selling prices, at least one image and chooses Publish now or Not published yet.
2. The system creates the draft product as in steps 7–9.
3. The system sends submission, approval and price application as separate audited commands.
4. The system publishes the product when the Manager chose Publish now (MSG26).
5. The system confirms that the product is saved with prices applied (MSG140).
6. The use case ends.

***Step 1.11 Import File Is Rejected***

1. The system rejects a file that is not a valid template file (MSG30).
2. The system lists each invalid row with its column and reason (MSG32).
3. The system disables Confirm import.
4. The use case continues at step 1.2.2.

***Step 1.12 Import Preview Is Out of Date***

1. The system rejects a preview older than 60 minutes or changed data (MSG33).
2. The system creates no product.
3. The use case continues at step 1.2.2.

***Step 1.13 Image Is Rejected or Cannot Be Stored***

1. The system rejects an image that breaks the type, size, count or alt-text rule (MSG11).
2. The system keeps the previous images when Cloudinary fails (MSG12).
3. The use case continues at step 1.3.1.

***Step 1.14 Existing Size Is Changed or Removed***

1. The system rejects any change to a saved size or SKU (MSG13).
2. The use case continues at step 1.4.1.

***Step 1.15 Last Image of a Published Product Is Removed***

1. The system rejects removing the last usable image (MSG27).
2. The use case continues at step 1.4.1.

***Step 1.16 Approved Decision Is No Longer Valid***

1. The system rejects a decision that was superseded, invalidated or belongs to a deleted product (MSG23).
2. The use case ends without changing the price.

***Step 1.17 Quantity Is Below Held Units***

1. The system rejects the quantity and shows the held units (MSG14).
2. The use case continues at step 1.6.1.

***Step 1.18 Some Sizes Fail in a Several-Size Change***

1. The system saves the valid sizes and lists each failed size with its reason (MSG53).
2. The use case ends.

***Step 1.19 Size or Branch Already Exists***

1. The system rejects a size already at the branch or a new size already on the product (MSG61).
2. The system rejects a branch already selling the product (MSG143).
3. The system rejects sizes at a branch where the product is not sold (MSG62).
4. The use case continues at step 1.8.2.

***Step 1.20 Price Step of a New Size Fails***

1. The system keeps the added sizes and the finished steps.
2. The system names the size whose price is not finished (MSG71).
3. The use case ends.

***Step 1.21 Actor Cancels the Deletion***

1. The system closes the confirmation without changing data.
2. The use case ends.

***Step 1.22 A Later Creation Step Fails***

1. The system keeps the created product and the finished steps.
2. The system asks the Manager to carry on with Publish now (MSG144).
3. The use case ends.

***Step 1.23 Product Was Deleted or Changed Before a Change Is Saved***

1. The system rejects a change to a deleted product (MSG17).
2. The system rejects a change based on an older version and offers to reload (MSG03).
3. The use case ends without saving the change.

***Step 2.1 Staff Member Has No Assigned Branch***

1. The system informs the Staff member that no branch is assigned (MSG141).
2. The use case ends without creating a product.

***Step 3.1 Another Product Has the Same Name***

1. The system shows a warning with links to the products of the same name (MSG55).
2. The use case continues at step 4.

***Step 7.1 Entered Data Is Invalid***

1. The system marks each invalid field, such as an empty name or a size outside 5–29 (MSG08).
2. The system creates nothing.
3. The use case continues at step 3.

***Step 7.2 Branch Is Outside the Actor's Scope***

1. The system rejects a branch that is not assigned to the Staff member (MSG01).
2. The use case continues at step 5.

***Step 7.3 Branch Is Inactive or Unknown***

1. The system rejects the branch as no longer active (MSG142).
2. The use case continues at step 5.

***Step 9.1 Result Is Not Confirmed***

1. The system reports that the result is unknown (MSG04).
2. The actor retries the same request, which creates no duplicate product or SKU.
3. The use case continues at step 9.

#### Postconditions

- A new or imported product is unpublished with a draft review, SKUs and branch stock rows.
- Valid changes are saved and audited; failed changes leave the previous state.
- A deleted product keeps its SKUs and history.
- Holds and accepted orders keep their terms.

#### Business Rule

BR-12, BR-13, BR-14, BR-21, BR-30, BR-31, BR-68, BR-69, BR-70, BR-71, BR-72, BR-73, BR-74
