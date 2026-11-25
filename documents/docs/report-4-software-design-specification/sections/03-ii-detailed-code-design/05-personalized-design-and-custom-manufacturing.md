## 5. Personalized Design and Custom Manufacturing (FE-05)

**Implementation status:** The business flow is represented by Feature-012, Feature-004 and the custom-manufacturing/payment boundaries; production readiness must be verified against their current contracts.

### 5.1 Class Diagram

{{r4-cd-fe05-custom-manufacturing width=100% align=center}}

The design keeps ring configuration, design-request review, custom-order quote, payment proof and fulfilment as separate ownership boundaries. Custom orders do not use the ready-ring retail cart or a deposit.

Feature-012 is an independent design source, not a booking attachment mechanism. Once a design is saved, the Member can either follow the recommended workshop link or use the design snapshot as an idea/reference for a personally manufactured ring through Feature-004. Recommendation does not create an order. The custom product becomes purchasable only after human review, quote approval and one full payment; it does not use the ready-ring retail cart or deposit flow.

### 5.2 Sequence Diagram — Convert Accepted Request to Custom Order

{{r4-sd-fe05-create-custom-order width=100% align=center}}

### 5.3 Sequence Diagram — Manager Approves Quote and Member Pays in Full

{{r4-sd-fe05-full-payment width=100% align=center}}

### 5.4 Sequence Diagram — Fulfil Paid Custom Order

{{r4-sd-fe05-custom-fulfilment width=100% align=center}}

