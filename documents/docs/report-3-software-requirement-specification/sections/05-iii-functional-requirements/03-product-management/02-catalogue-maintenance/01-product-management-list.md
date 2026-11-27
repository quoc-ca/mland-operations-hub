#### 3.2.1 Product Management List

![](assets/screens/live/47-product-management-list.png)

This screen allows Staff and Managers to:

- Browse managed products by product code, name or SKU, category and branch, in status tabs that show how many products each holds.
- See each product's code, category, stock and status, with the tabs that need the viewer's action highlighted: Products to review and Prices to review (waiting prices) for a Manager, Rejected and unsent price drafts for Staff, and Ready to publish for both; rejected prices are not highlighted.
- Open product creation, the Excel import or a product's management details.
- As a Manager, publish, hide or show a product again directly, or open a submitted product for review.
- As Staff, stop or resume selling a product at their own branch.

A Staff member with one assigned branch sees the list filtered on that branch by default; other users see all branches. The selected tab and filters are kept when the page is shared or reopened.

**Field Description**

| Field Name | Description |
| --- | --- |
| Product catalogue / Categories | Page header tabs switching between this list and Product Categories. Default: Product catalogue. |
| Bulk import | Page header button that opens Import Products from Excel. |
| New product | Page header button that opens New Product. |
| Search | Text input (up to 200 characters), empty by default, matched against product code, name or SKU. It is applied about 300 ms after typing stops or on Enter; a SKU match shows "Matches SKU {sku} (size {size})". |
| Category | Options: All, then every category (an inactive one is marked "(Inactive)"). Default: All. Applied as soon as it changes. |
| Branch | Options: All branches, then every active branch. Default: the Staff member's branch when they have exactly one, otherwise All branches. Applied as soon as it changes. |
| Clear filters | Shown while a filter differs from the default; clears the search and category and returns the branch to its default. |
| Status tabs | Options: All, Drafts, Products to review, Rejected, Ready to publish, Published, Hidden, Prices to review. Default: All. Each non-deleted product is in exactly one status tab; Prices to review is described in Prices to Review. Counts follow the current search, category and branch; a tab without products shows its own empty message, for example "No draft." |
| Product name | Link to the management details, with the stable product code (for example SP000009). |
| Category | The product's category. |
| Stock | Total remaining quantity of all sizes at the filtered branch, or at all branches, with "Available {count}" when held quantities make the available quantity lower. |
| State | Name of the product's status tab; with a branch filter, "Selling stopped at this branch" when the product is stopped there. |
| Actions | Manager: **Publish** for a never-published product that is not under review (it first completes the missing submission, approval at the proposed prices and price application), **Hide**, **Show again** for a hidden product, **Review first** for a submitted review and **See reason** for a rejected one. Staff: **Stop selling at branch** / **Resume selling at branch** only while the list is filtered on one of their active branches where the product is sold, and **See reason**. **Details** is shown next to Publish, Hide, Show again and the branch actions, and alone when the row has no action. A failed action shows its reason on the row. |
| Empty state | "No product matches the filters." when a filter is active. |
| Pager | **Previous**, a page selector and **Next**. |
