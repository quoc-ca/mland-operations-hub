#### 8.3.2 Final Amount Review / Approval

![Final Amount Review / Approval mockup](assets/screens/71-final-amount-review-approval.png)

This screen allows Managers to:

- Review the exact Staff proposal, remaining base amount and actual surcharges.
- Approve the matching final amount before releasing its payment request.
- Return the proposal to Staff for revision and review resubmitted terms.
- Require renewed approval when the amount or reviewed terms change.

| Field Name | Description |
| --- | --- |
| Booking / Member / package / state | Read-only review-context columns; show only records within the reviewer's scope. |
| Open File | Opens the selected authorized request and its retained evidence. |
| Assessment / final price | Generic input requiring screen-specific meaning; a design assessment must not silently become an approved order balance. |
| Return / Approve | Decision controls requiring current action authority and matching reviewed terms; they cannot mark payment paid. |
| Manager-final-approval notice | Applies to final custom amount. Staff may still perform an eligible design review within the separate review threshold. |
