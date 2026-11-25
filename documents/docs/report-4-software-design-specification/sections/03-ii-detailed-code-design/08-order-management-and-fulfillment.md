## 8. Order Management and Fulfillment (FE-08)

**Implementation status:** Ready-ring retail and fulfilment slices exist. Custom-order fulfilment is consumed through its approved boundary and is not merged into the retail cart.

### 8.1 Class Diagram

{{r4-cd-fe08-order-fulfilment width=100% align=center}}

The module owns Member cart, grouped checkout, branch order, order items, pickup and the permitted Staff-recorded carrier handoff. GHTK is limited to the approved fee-query boundary; live carrier tracking is outside V1.

### 8.2 Sequence Diagram — Add Ready Ring, Revalidate Cart and Checkout

{{r4-sd-fe08-retail-checkout width=100% align=center}}

### 8.3 Sequence Diagram — Verify VNPay Payment and Settle Stock

{{r4-sd-fe08-retail-payment width=100% align=center}}

### 8.4 Sequence Diagram — Pickup or Record Carrier Handoff

{{r4-sd-fe08-fulfilment-handoff width=100% align=center}}
