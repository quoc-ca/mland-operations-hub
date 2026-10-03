## 1. Scope and Purpose

V1 supports workshop booking for Guests and Members, ring design/consultation, workshop deposit-payment records, and the assessment of a customer-designed ring's difficulty and estimated price. It also supports Member retail of catalogue rings without warehouse inventory management.

The public catalogue displays each published product and its manually maintained available-to-sell quantity at the time it is viewed; that quantity is not a real-time guarantee. Only Members may use the cart and create retail orders. A Manager maintains products, prices, and available-to-sell quantities, while Staff and Managers record an auditable customer pickup or carrier handoff after payment.

When a Member views a cart, unavailable or invalid lines are removed and reported. Checkout revalidates the complete cart and creates no partial order. It snapshots each order-item price and holds all required quantities for 15 minutes; an unpaid or expired hold is released. A verified full-payment confirmation from the Payment Gateway completes the retail sale and deducts the held quantities. Payment Gateway provider and contract remain `TBD`.

A Member may collect a paid order at the shop or provide recipient and delivery-address details for that order. Carrier fees are outside the order and paid separately to the carrier. The shop's recorded delivery responsibility ends at the carrier handoff; any carrier API/provider decision remains `TBD`.

V1 does not manage warehouse locations, serials, lots, stock movements, procurement, or an inventory ledger. It does not guarantee real-time displayed quantities, support Guest retail checkout, store an address book, calculate shipping quotes, track shipments, handle delivery failures or returns, or provide cancellation, refund, accounting, or payment-reconciliation workflows. Customers seeking cancellation or refund must contact the shop directly.
