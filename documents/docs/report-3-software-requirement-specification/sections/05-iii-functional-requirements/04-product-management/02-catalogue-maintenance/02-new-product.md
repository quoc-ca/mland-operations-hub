#### 4.2.2 New Product

![](assets/screens/48-new-product.png)

This screen allows Staff and Managers to:

- Enter the product name, active category and description, with a warning when products with the same name already exist.
- Select the branches that sell the product.
- Choose 1–25 sizes individually or as a range, and enter a price for each size and a quantity for each size at each branch.
- Add up to 10 images with alternative text and order them.
- As Staff, create an unpublished draft whose prices remain proposals for Manager review.
- As a Manager, create the product with its prices applied immediately, either unpublished or published at once.

| Field Name | Description |
| --- | --- |
| Product name | Required text input; "{count} product(s) already have this name" lists links to them without blocking the save. |
| Category | Required selection of an active category; when none exists, **Open the categories page** opens Product Categories. |
| Description | Required multi-line text. |
| Selling branches | Checkboxes for the active branches within the user's scope; the price is shared by all branches while quantities are entered per branch. |
| Sizes, proposed prices and quantities | Size buttons plus From size / To size, **Add range**, **Select all** and **Clear**, with a shared value applied to the chosen sizes. Saved sizes cannot be changed or removed later; new sizes can be added later at a selling branch from Product Management Details. |
| Price per size | "Proposed price (₫)" for Staff; "Selling price (₫)" for a Manager, applied on creation without a review step. |
| Quantity per branch | Nonnegative integer quantity of each size at each selected branch, with − / + buttons. |
| Images | **Add images (n/10)**, image description, move up/down and **Remove**; a Manager must add at least one image. |
| After creating | Manager only: "Not published yet" (saved with prices applied, hidden from customers) or "Publish now". |
| Create product / Cancel | Creates the product and opens its management details, or abandons the form. |
