#### 3.2.2 New Product

![](assets/screens/live/48-new-product.png)

This screen allows Staff and Managers to:

- Enter the product name, active category and description, with a warning when products with the same name already exist.
- Select the branches that sell the product.
- Choose 1–25 sizes from 5 to 29, individually or as a range, and enter a price for each size and a quantity for each size at each branch.
- Add up to 10 images with alternative text and order them.
- As Staff, create an unpublished draft whose prices remain proposals for Manager review.
- As a Manager, create the product with its prices applied immediately, either unpublished or published at once.

Staff can choose only their assigned branches; a Manager can choose every active branch. A user with no assigned branch cannot create a product.

**Field Description**

| Field Name | Description |
| --- | --- |
| Product name | Required text input of at most 200 characters, empty by default; "{count} product(s) already have this name:" lists links to them without blocking the save. |
| Category | Required; options: the active categories. Default: "Choose a category" (none). When none exists, **Open the categories page** opens Product Categories. |
| Description | Required multi-line text of at most 5,000 characters. |
| Selling branches | Checkboxes for the active branches within the user's scope, with their address; none ticked by default, unless only one branch is available, which is ticked. The price is shared by all branches while quantities are entered per branch. |
| Sizes, proposed prices and quantities | Size buttons 5–29 plus **From size** / **To size**, **Add range**, **Select all** and **Clear**, none chosen by default. A shared price and quantity can be applied to the chosen sizes with **Apply**. Saved sizes cannot be changed or removed later; new sizes can be added later from Product Management Details. |
| Price per size | "Proposed price (₫)" for Staff; "Selling price (₫)" for a Manager, applied on creation without a review step. |
| Quantity per branch | "Quantity at {branch}": a nonnegative integer quantity of each size at each selected branch, with − / + buttons. |
| Images | **Add images (n/10)** (JPEG, PNG or WebP, at most 5,000,000 bytes each), a required image description of at most 200 characters, move up/down and **Remove**; a Manager must add at least one image. |
| After creating | Manager only. Options: "Not published yet (saved with prices applied, hidden from customers)", "Publish now". Default: Not published yet. |
| Create product / Cancel | **Create product** creates the product and opens its management details; it reads **Create and publish** when a Manager chooses "Publish now". **Cancel** returns to Product Management List. |
