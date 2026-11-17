#### 7.2.1 My Retail Orders

![](assets/screens/24-my-retail-orders.png)

This screen allows Members to:

- Browse the Member's own retail order history.
- View order references, creation times, frozen totals and payment/fulfilment states.
- Open an order's details.

| Field Name | Description |
| --- | --- |
| Order value / total | Read-only amount; indicative for a cart and frozen after accepted checkout. Sample amounts are not operational prices. |
| State | Read-only system facts; cart viewing alone creates no hold or accepted order. |
| Payment | Read-only verified status; a browser return cannot mark paid. |
| Item / variant | Shows product and exact configured variant; accepted-order identity and options remain frozen. |
| Quantity / line amount / subtotal | Integer quantities and whole-VND amounts; calculate from applicable terms rather than mockup examples. |
| Continue | Performs only the eligible next action for this screen and current order/payment state. |
| Return-URL notice | Explains that payment confirmation comes from verified gateway evidence, not the browser redirect. |
