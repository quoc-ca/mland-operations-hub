#### 3.2.3 Product Management Details

![](assets/screens/live/49-product-management-details.png)

This screen allows Staff and Managers to:

- View the product's state, category, first publication time and version.
- Edit the content (name, category, description) and images.
- Review, per branch, each size's SKU, quantity at the branch, total at all branches, held quantity, applied price and sale state.
- Update quantities per size or for several sizes at once, stop or resume sales of a size or at a branch, add a selling branch and add sizes at a selling branch.
- Propose prices, submit them for review and apply approved prices; as a Manager, change the price of a published product in one step (see Price Proposal Form).
- As Staff, submit a never-published product for initial review; as a Manager, publish, hide or show the product again; delete the product.

Staff outside the product's branches see a read-only view. With more than 3 sizes, the Sizes and quantities section starts collapsed; the Price proposals section also starts collapsed unless a price waits for action. A deleted product shows "This product was deleted at {time}" and cannot be edited.

**Field Description**

| Field Name | Description |
| --- | --- |
| Back to list | Returns to Product Management List. |
| Status summary | Read-only state, category (marked "(Inactive)" when inactive), first publication time and version with its content version. |
| Content | Product name, category and description; **Edit** opens inline editing with **Save** / **Cancel**. Editing a never-published product sends its review back to draft. |
| Images | Current images, or "No images yet."; **Edit** opens the image editor with **Save** / **Cancel**. |
| Branch tabs | Select the branch whose quantities are shown and changed; a branch not selling the product is marked "Not for sale". **Stop selling at this branch** / **Resume selling at this branch** change sales of the whole product there only. An inactive branch is marked "(branch no longer active)" without sale buttons; only a Manager can change its quantities. |
| Sizes and quantities | Per size: SKU, quantity at the branch with **Edit**, total at all branches, held quantity, applied price and sale state with **Stop selling size** / **Sell again**; Staff get these two buttons only when assigned to every active branch selling the product. Quantity cannot fall below the held quantity. A size the branch does not sell shows "Not sold at this branch". |
| Edit quantities of several sizes | Bulk quantity editor for one branch. Options: **Add** (restock) or **Set to**; it applies a number to the chosen sizes and saves each changed size on its own. |
| Add a selling branch | Choose an active branch the product is not sold at, enter the quantity of each existing size (default 0) and optionally tick new sizes with their quantity and price; **Save branch** saves. A product without any branch shows **Assign a branch** instead, prefilled with the current quantities. |
| Add sizes | On a selling branch's tab: choose sizes the branch does not have; an existing size of the product needs only a quantity, a new size also needs a price (proposed price for Staff, selling price for a Manager). Hidden when the branch already has 25 sizes. |
| Price proposals | Per-size price history and price actions, described in Price Proposal Form. |
| Initial review | For a never-published product: Staff use **Submit for review** to lock the submitted content for Manager review. The Manager chooses **Approve**, enters each size's approved price (or applies a "Shared approved price (₫)" to chosen sizes), keeps or clears "Apply the price right after approving" and uses **Approve the whole review**, or chooses **Reject** with a required reason. After approval, Staff apply prices that are not applied yet with **Apply approved prices of {count} sizes**. **Review history** lists the review revisions. A Manager sees this section only for a Staff review that is pending or rejected. |
| Publication | A table of each size's proposed, approved and applied price ("Not applied" when none). Manager only: **Publish product** / **Hide product**, or **Publish now** for a never-published product, which completes the missing review, approval and price steps. The first publication needs an applied price for every size; a hidden product can be shown again when at least one size has an applied price. Staff see that publication is done by a Manager. |
| Delete product | Hidden for Staff outside the product's branches. Opens "Delete \"{name}\"?", which warns that a deleted product cannot be restored; history and placed orders are kept. |
