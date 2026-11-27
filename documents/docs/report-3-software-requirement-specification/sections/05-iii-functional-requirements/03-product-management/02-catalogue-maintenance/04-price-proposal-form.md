#### 3.2.4 Price Proposal Form

![](assets/screens/live/50-price-proposal-form.png)

The price proposal form is the **Price proposals** section of Product Management Details. It allows Staff and Managers to:

- Review each size's applied price and its full price history.
- Enter a new proposed price for one size, or a common or individual price for several sizes at once.
- Save proposals as drafts, then submit them to the Manager for review.
- Apply a Manager-approved price as a separate action.
- As a Manager, change the price of a size of a published product in one step.

Saving or submitting a proposal never changes the applied price or the publication state. Before the first publication, proposed prices are decided with the product's initial review instead of being submitted one by one.

**Field Description**

| Field Name | Description |
| --- | --- |
| Section heading | "Price proposals" with "{count} sizes need action" or "{count} sizes". |
| Edit proposed prices of several sizes | Shown with two or more sizes. Bulk editor with a size range, "Shared proposed price (₫)", and per size the applied price, current proposal, new proposal and difference. **Save and submit {count} sizes** (after the first publication) and **Only save {count} drafts**; before the first publication, **Save proposed prices of {count} sizes**. |
| Submit {count} draft sizes / Apply approved prices of {count} sizes | Shown after the first publication when two or more sizes have a draft, or an approved price not yet applied; each size is handled on its own. |
| Size heading | "Size N" and the price customers currently pay for it; with more than 3 sizes each size can be collapsed, and sizes with a draft, pending or approved-not-applied round start open. |
| Price history of size N | Per round: Revision (the current one marked "current"), one combined state (Applied, Approved, not applied yet, Pending, Draft, Rejected or Superseded), proposed amount, approved amount and rejection reason; "No price proposal yet." when empty. |
| Submitted / decided by | Who submitted and decided each round, the submitter's branches at submission and the times. |
| Propose a new price / Revise proposed price | Shown to Staff, and to a Manager only before the first publication or for a size with a pending proposal. Positive whole-VND amount; **Save** saves it as a draft and **Cancel** discards it. Revising a pending proposal returns it to draft. |
| Submit price | Submits the saved draft to the Manager; only after the product's first publication. |
| Change price | Manager only, for a product that has been published before (including a hidden one) and a size without a pending proposal. "New selling price (VND)" and "Apply the price right after approving" (ticked by default); **Save** creates, submits and approves the proposal, then applies it if ticked. If a step fails, the earlier steps are kept and the Manager continues from the current state. |
| Apply approved price {amount} | Applies an approved, not yet applied price to the size. |
