#### 10.1.2 Price Proposal Review

![](assets/screens/72-price-proposal-review.png)

This screen allows Managers to:

- Approve a pending retail price proposal at the proposed amount or at an adjusted approved price.
- Choose whether the approved price is applied immediately or applied later through a separate action.
- Reject the proposal with a reason, leaving the applied price unchanged.

The same form is used on a row of the Prices to review tab and in the Price proposals section of Product Management Details. When at least two sizes are pending, Product Management Details also shows **Prices waiting for a decision ({count} sizes)**, which decides several chosen sizes at once, each size as its own decision. Approval does not publish or hide the product; publication remains a separate action.

| Field Name | Description |
| --- | --- |
| Approved price (₫) | Positive whole-VND amount, prefilled with the proposed amount; the Manager may change it. |
| Apply the price right after approving | Ticked by default; when cleared, the approved price waits for **Apply approved price**. |
| Approve / Cancel | Records the approval with the Manager as decider, or closes the form without a decision. |
| Rejection reason | Required text shown after **Reject**; it is recorded in the size's price history and shown under the Rejected sub-filter of Prices to review. |
| Decision record | After a decision, the price history shows the approved amount, the combined state and the deciding Manager with the time. |
| Shared approved price (₫) | Several-size form: **Apply to {count} chosen sizes** copies it to the chosen sizes; **Reset to proposed prices** restores each size's proposed amount. |
| Size rows | Several-size form: checkbox, applied price, proposed amount, editable approved price and difference ("Same as proposed" when equal), with the submitter, the submitter's branches and the time. |
| Approve / Reject {count} chosen sizes | Several-size form: approves the chosen sizes (applying them when "Apply the price right after approving" is ticked) or rejects them with a shared reason; a failed size does not affect the others. |
