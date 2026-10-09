#### 4.2.3 Product Management Details

![](assets/screens/49-product-management-details.png)

This screen allows authorized catalogue maintainers to:

- Maintain permitted product fields, images and configured variants under the shared limits.
- Update nonnegative remaining-unsold quantity without reducing it below active held quantity or changing used variant options.
- Apply an unchanged Manager-approved price through a separate authorized action.
- Publish or unpublish explicitly after applicable checks; zero quantity alone does not block publication.
- Receive a rejection for stale edits or changes breaking published prerequisites, while preserving accepted history and valid holds.

| Field Name | Description |
| --- | --- |
| Record code / product or proposal | Read-only stable target references; product identity and price-proposal identity must remain distinguishable. |
| Applied price | Shows the currently effective Manager-approved price, separately from the proposed amount. |
| State | Displays publication/proposal facts; an illustrative held label must not create a separate parent-product hold lifecycle. |
| Create / Propose / Save Draft / Send for Review | Enable only actions appropriate to the current screen and authority; proposal submission does not apply or publish a price. |
| Approval notice | Explains that Manager review, authorized price application and publication are separate actions. |
