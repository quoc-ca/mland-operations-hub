#### 4.2.1 Product Management List

![](assets/screens/47-product-management-list.png)

This screen allows Staff and Managers to:

- Browse managed products by product code, name or SKU, category and branch, in status tabs that show how many products each holds.
- See each product's code, category, stock and status, with the tab that needs the viewer's action highlighted.
- Open product creation, the Excel import or a product's management details.
- As a Manager, publish, hide or show a product again directly, or open a submitted product for review.
- As Staff, stop or resume selling a product at their own branch.

A Staff member with one assigned branch sees the list filtered on that branch by default; other users see all branches.

| Field Name | Description |
| --- | --- |
| Status tabs | All, Drafts, Products to review, Rejected, Ready to publish, Published, Hidden and Prices to review. Each non-deleted product is in exactly one status tab; Prices to review follows price changes of published products (see Prices to Review). Counts follow the current search, category and branch; a tab without products shows its own empty message. |
| Search | Text input matched against product code, name or SKU, applied about 300 ms after typing stops or on Enter; a SKU match shows "Matches SKU {sku} (size {size})". |
| Category / Branch | Applied as soon as they change and kept with the tab in the page URL; "All branches" lists every branch and **Clear filters** removes the filters. |
| Product name | Link to the management details, with the stable product code (for example SP000009). |
| Category | The product's category. |
| Stock | Total remaining quantity of all sizes at the filtered branch, or at all branches, with "Available {count}" when held quantities make the available quantity lower. |
| State | Name of the product's status tab; with a branch filter, "Selling stopped at this branch" when the product is stopped there. |
| Actions | Manager: **Publish** for a never-published draft or approved product, **Hide**, **Show again** for a hidden product, **Review first** for a submitted review and **See reason** for a rejected one. Staff: **Stop selling at branch** / **Resume selling at branch** only while the list is filtered on one of their active branches where the product is sold, and **See reason**. **Details** is shown next to Publish, Hide, Show again and the branch actions, and alone when the row has no action. |
| New product | Opens New Product. |
| Import Excel | Opens Import Products from Excel. |
| Products / Categories | Tabs switching between this list and Product Categories. |
| Pager | Moves between result pages. |
