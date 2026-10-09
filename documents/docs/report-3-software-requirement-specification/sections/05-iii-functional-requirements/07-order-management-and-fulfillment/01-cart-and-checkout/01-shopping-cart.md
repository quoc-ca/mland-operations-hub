#### 7.1.1 Shopping Cart

![Shopping Cart mockup](assets/screens/17-shopping-cart.png)

This screen allows Members to:

- Review the Member's own cart and its current indicative prices.
- Update positive integer quantities, totaling at most 99 per variant across at most 50 lines, subject to availability.
- Remove cart items and receive validation or stale-edit feedback.
- Proceed to whole-cart checkout after reviewing any corrected cart contents.

| Field Name | Description |
| --- | --- |
| Order value / total | Read-only amount; indicative for a cart and frozen after accepted checkout. Sample amounts are not operational prices. |
| State | Read-only system facts; cart viewing alone creates no hold or accepted order. |
| Payment | Read-only verified status; a browser return cannot mark paid. |
| Item / variant | Shows product and exact configured variant; accepted-order identity and options remain frozen. |
| Quantity / line amount / subtotal | Integer quantities and whole-VND amounts; calculate from applicable terms rather than mockup examples. |
| Continue | Performs only the eligible next action for this screen and current order/payment state. |
| Return-URL notice | Explains that payment confirmation comes from verified gateway evidence, not the browser redirect. |
