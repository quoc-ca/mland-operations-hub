#### 13.1.1 Payment Gateway Redirect

![](assets/screens/33-payment-gateway-redirect.png)

This screen allows eligible customers to:

- Review the eligible payment reference, amount and original deadlines.
- Continue to the configured gateway for the selected retail, workshop or custom-order payment.
- Return to the relevant details or result screen without extending deadlines or reviving an expired hold.

| Field Name | Description |
| --- | --- |
| Order value / total | Read-only amount; indicative for a cart and frozen after accepted checkout. Sample amounts are not operational prices. |
| State | Read-only system facts; cart viewing alone creates no hold or accepted order. |
| Payment | Read-only verified status; a browser return cannot mark paid. |
| Item / variant | Shows product and exact configured variant; accepted-order identity and options remain frozen. |
| Quantity / line amount / subtotal | Integer quantities and whole-VND amounts; calculate from applicable terms rather than mockup examples. |
| Continue | Performs only the eligible next action for this screen and current order/payment state. |
| Return-URL notice | Explains that payment confirmation comes from verified gateway evidence, not the browser redirect. |
