#### 7.2.2 Retail Order Details

![Retail Order Details mockup](assets/screens/25-retail-order-details.png)

This screen allows Members to:

- View the Member's frozen order branch, variants/options, quantities, discounts and amounts.
- Read payment, hold and internal fulfilment states separately.
- After verified full payment and before preparation, choose or edit Pickup or manual GHTK fulfilment.
- For GHTK, provide name, phone, address and an optional note; fulfilment changes do not move the processing branch or reprice the order.
- View recorded pickup or handoff facts without courier tracking.

| Field Name | Description |
| --- | --- |
| Order value / total | Read-only amount; indicative for a cart and frozen after accepted checkout. Sample amounts are not operational prices. |
| State | Read-only system facts; cart viewing alone creates no hold or accepted order. |
| Payment | Read-only verified status; a browser return cannot mark paid. |
| Item / variant | Shows product and exact configured variant; accepted-order identity and options remain frozen. |
| Quantity / line amount / subtotal | Integer quantities and whole-VND amounts; calculate from applicable terms rather than mockup examples. |
| Continue | Performs only the eligible next action for this screen and current order/payment state. |
| Return-URL notice | Explains that payment confirmation comes from verified gateway evidence, not the browser redirect. |
