#### 4.2.3 Product Management Details

![](assets/screens/49-product-management-details.png)

This screen allows Staff and Managers to:

- View the product's state, category, first publication time and version.
- Edit the content (name, category, description) and images.
- Review, per branch, each size's SKU, quantity at the branch, total at all branches, held quantity, applied price and sale state.
- Update quantities per size or for several sizes at once, stop or resume sales of a size or at a branch, add a selling branch and add sizes at a selling branch.
- Propose prices, submit them for review and apply approved prices; as a Manager, change the price of a published product in one step (see Price Proposal Form).
- As Staff, submit a never-published product for initial review; as a Manager, publish, hide or show the product again; delete the product.

Staff outside the product's branches see a read-only view. With more than 3 sizes, the Sizes and quantities and Price proposals sections start collapsed unless a size needs action.

| Field Name | Description |
| --- | --- |
| Status summary | Read-only state, category, first publication time and version with its content version. |
| Content | Product name, category and description; **Edit** opens inline editing with **Save** / **Cancel**. |
| Images | Current images; **Edit** opens the image editor. |
| Branch tabs | Select the branch whose quantities are shown and changed; **Stop selling at this branch** / **Resume selling at this branch** change sales of the whole product there only. An inactive branch is shown as "Branch #{id} (branch no longer active)" without sale buttons; only a Manager can change its quantities. |
| Sizes and quantities | Per size: SKU, quantity at the branch with **Edit**, total at all branches, held quantity, applied price and sale state with **Stop selling size** / **Sell again**; Staff get these two buttons only when assigned to every active branch selling the product. Quantity cannot fall below the held quantity. A size the branch does not sell shows "Not sold at this branch". |
| Edit quantities of several sizes | Bulk quantity editor for one branch that adds to (**Add**) or sets (**Set to**) the quantity of several sizes. |
| Add a selling branch | Choose an active branch the product is not sold at, enter the quantity of each existing size and optionally tick new sizes with their quantity and price. |
| Add sizes | On a selling branch's tab: choose sizes the branch does not have; an existing size of the product needs only a quantity, a new size also needs a price (proposed price for Staff, selling price for a Manager). Hidden when the branch already has 25 sizes. |
| Price proposals | Per-size price history and price actions, described in Price Proposal Form. |
| Initial review | For a never-published product: Staff use **Submit for review** to lock the submitted content for Manager review. The Manager chooses **Approve**, enters each size's approved price (or applies a "Shared approved price (₫)" to chosen sizes), keeps or clears "Apply the price right after approving" and uses **Approve the whole review**, or chooses **Reject** with a required reason. Approved prices not yet applied can then be applied by Staff or a Manager. **Review history** lists the review revisions. A Manager sees this section only for a Staff review that is pending or rejected. |
| Publication | Manager only: **Publish product** / **Hide product**, or **Publish now**, which sends the missing review, approval and price steps. The first publication needs an applied price for every size; a product published before can be shown again when at least one size has an applied price. Staff see that publication is done by a Manager. |
| Delete product | Requires confirmation and cannot be undone. |
| Back to list | Returns to Product Management List. |
