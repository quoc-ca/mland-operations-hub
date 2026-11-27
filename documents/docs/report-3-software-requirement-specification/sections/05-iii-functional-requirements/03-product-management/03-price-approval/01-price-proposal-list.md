#### 3.3.1 Prices to Review

![](assets/screens/live/71-prices-to-review.png)

**Prices to review** is the tab of Product Management List that follows price changes of products that have been published before, one row per size. Prices of a never-published product are decided with its initial review and appear under the Products to review tab instead. The tab count is the number of prices waiting for a decision, for Staff and Managers alike. This screen allows Managers and Staff to:

- See, for each size, the product, the applied price and the proposed price, with who submitted it and when.
- As a Manager, approve or reject a waiting price directly on its row; the list and the counts reload after the decision.
- As Staff, follow submitted prices read-only, submit price drafts not sent yet, and propose a new price for a rejected one.
- See rejected prices with the deciding Manager, the time and the reason until the size gets a new proposal; the applied price stays unchanged.

Staff cannot submit or propose on rows of products not sold at their branches.

**Field Description**

| Field Name | Description |
| --- | --- |
| Sub-filters | Options: Waiting for review, Drafts not sent (Staff only), Rejected, each with its own count. Default: Waiting for review. Drafts not sent is highlighted for Staff when above 0; Waiting for review is highlighted for a Manager when above 0; Rejected is never highlighted. |
| Product name | Link to Product Management Details, with the product code. |
| Size | The size whose price is proposed. |
| Applied price | The price customers currently pay; it does not change while the proposal waits or after a rejection; "—" when the size has no applied price yet. |
| Proposed / Draft price | The amount proposed, or the amount of the unsent draft. |
| Submitted by / Rejected by / State | Staff member or Manager with the time; "Not sent" for a draft. |
| Decision (Waiting for review) | Manager: **Approve** / **Reject**, opening the Price Proposal Review form on the row. Staff: "Waiting for a Manager". |
| Actions (Drafts not sent) | **Submit price** sends exactly that draft to the Manager. |
| Reason (Rejected) | The rejection reason, with **Propose a new price** for Staff, which creates and submits a new proposal from the row, or **Change price** for a Manager, which opens the size in Product Management Details. |
| Empty state | "No price proposal waits for a decision.", "No price draft left unsent." or "No rejected price." |
