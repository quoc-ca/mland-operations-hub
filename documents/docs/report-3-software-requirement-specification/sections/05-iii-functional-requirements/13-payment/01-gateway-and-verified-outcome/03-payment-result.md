#### 13.1.3 Payment Result

![Payment Result mockup](assets/screens/35-payment-result.png)

This screen allows eligible customers to:

- View the system-verified payment outcome, including pending, success, failure, expiry or exception.
- Open the relevant booking or order details and any eligible next action.
- Receive a confirmed result only from matching signed gateway evidence or eligible QueryDR; browser return and duplicate evidence cannot create another sale.

| Field Name | Description |
| --- | --- |
| Order value / total | Read-only amount; indicative for a cart and frozen after accepted checkout. Sample amounts are not operational prices. |
| State | Read-only system facts; cart viewing alone creates no hold or accepted order. |
| Payment | Read-only verified status; a browser return cannot mark paid. |
| Item / variant | Shows product and exact configured variant; accepted-order identity and options remain frozen. |
| Quantity / line amount / subtotal | Integer quantities and whole-VND amounts; calculate from applicable terms rather than mockup examples. |
| Continue | Performs only the eligible next action for this screen and current order/payment state. |
| Return-URL notice | Explains that payment confirmation comes from verified gateway evidence, not the browser redirect. |
