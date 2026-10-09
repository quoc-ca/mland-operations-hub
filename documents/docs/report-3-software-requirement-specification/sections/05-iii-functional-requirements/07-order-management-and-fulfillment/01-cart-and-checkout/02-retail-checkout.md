#### 7.1.2 Retail Checkout

![Retail Checkout mockup](assets/screens/18-retail-checkout.png)

This screen allows Members to:

- Select an active processing branch and review the entire cart.
- Apply at most one approved eligible voucher, then approved points, or continue without benefits.
- Review the positive whole-VND payable amount and submit the complete quote.
- Accept an order and exact-variant/benefit holds together or none; the original link lasts 10 minutes and hold 15 minutes.
- Review and resubmit changed or corrected terms before accepting checkout.

| Field Name | Description |
| --- | --- |
| Order value / total | Read-only amount; indicative for a cart and frozen after accepted checkout. Sample amounts are not operational prices. |
| State | Read-only system facts; cart viewing alone creates no hold or accepted order. |
| Payment | Read-only verified status; a browser return cannot mark paid. |
| Item / variant | Shows product and exact configured variant; accepted-order identity and options remain frozen. |
| Quantity / line amount / subtotal | Integer quantities and whole-VND amounts; calculate from applicable terms rather than mockup examples. |
| Continue | Performs only the eligible next action for this screen and current order/payment state. |
| Return-URL notice | Explains that payment confirmation comes from verified gateway evidence, not the browser redirect. |
