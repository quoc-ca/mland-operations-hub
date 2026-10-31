# Data Model: Retail Order and Fulfillment

This document defines logical ownership for FE-08. It does not authorize a
database migration. Physical table names, indexes, locking clauses, and
Flyway ordering must be confirmed in the implementation tasks against the
current database baseline.

## Ownership split

| Logical record | Owner | Main consumers |
| --- | --- | --- |
| Fixed ready-ring shipping rule | `readyringsales` | GHTK quotation and fulfilment |
| Member cart and cart line | `readyringsales` | Member checkout; each line records its selected processing branch |
| Grouped checkout | `readyringsales` | Member payment and branch-order grouping |
| Retail order and order item snapshot | `readyringsales` | payment, fulfilment, Member order view |
| Checkout hold | `readyringsales` plus owning promotion/loyalty facades | expiry/release worker |
| Payment attempt and verified evidence | `payments` | `readyringsales` through payment event/facade |
| Fulfilment selection and recipient snapshot | `readyringsales` until accepted, then fulfilment projection | Member and Staff operations |
| GHTK shipment | `fulfilment` | status reconciliation and handoff |
| Carrier status event | `fulfilment` | normalized current-status projection |
| Preparation and handoff evidence | `fulfilment` | Staff/Manager and Member order projection |
| Notification intent | owning business module/shared notification capability | mail worker |

## Logical records

### ReadyRingShippingRule

- Fixed chargeable weight: 50 grams per ordered ring, including packaging
- Branch parcel weight: total ordered-ring quantity in that branch multiplied
  by 50 grams
- No Member, Staff, product, or variant override in V1

The rule is owned by FE-08 and is applied server-side when requesting a GHTK
quote or creating its accepted shipment snapshot. It replaces an FE-02
product-shipping-profile dependency for V1.

### RetailCart and CartLine

- Member owner
- Product/variant reference
- Positive integer quantity
- Processing branch selected when the line was added
- Indicative price display data
- Last edit/version timestamp

Cart edits never create a retail order, payment attempt, sold quantity, or
benefit redemption. One cart contains at most 50 lines, and total quantity for
one variant across all lines is an integer from 1 through 99 subject to current
logical availability.

### GroupedCheckout

- Grouped checkout identifier and immutable checkout command key
- Member owner
- Branch-order membership and combined subtotal/shipping/payable totals
- One payment state and one payment deadline
- Payment lifecycle state: `ACCEPTED_UNPAID`, `PAYMENT_INITIATED`, `PAID`,
  `FAILED`, `EXPIRED`, `CANCELLED`, or `EXCEPTION`, with
  `paymentInitiatedAt` recorded when the server initiates VNPay
- Complete grouped quote and benefit snapshot

The grouped checkout is the Member-facing payment unit. It must not replace
branch-specific order ownership or fulfilment state.

Benefit selection stored with an accepted checkout uses the FE-06 canonical
shape: `choice` (`NONE`, `AUTO`, or `VOUCHER`), `voucherCode` only for
`VOUCHER`, and non-negative `requestedPoints`. API monetary and point values
are decimal strings; quantities remain bounded integers.

### RetailOrder

- Mland order identifier and immutable checkout command key
- Member and processing branch references
- Lifecycle/payment state and fulfilment state kept separately
- Frozen currency, subtotal, discount, shipping charge, and payable amount
- Checkout accepted time, payment-link deadline, and hold deadline
- Accepted product/variant and benefit snapshots
- Safe audit/correlation references

One complete branch order is accepted for each branch group with its item
snapshots and holds. The grouped checkout is accepted atomically: it creates
all branch orders and their complete hold bundle or none. A paid branch order
remains paid if GHTK creation is pending or failing.

### OrderItem

- Retail order reference
- Product/variant identity snapshot
- Configuration/option snapshot where applicable
- Quantity, unit price, line amount
- Server-calculated branch chargeable-weight snapshot used for the accepted
  quote (50 grams per ordered ring, including packaging)

Later catalogue changes must not alter the order snapshot.

### CheckoutHold

- Retail order reference
- Held quantity/benefit references
- Created and expires-at timestamps
- Released/converted outcome and reason

The maximum unpaid hold is 15 minutes. A payment link is valid for at most 10
minutes. An authoritative unpaid payment failure or expiry releases held
quantities and benefits and restores the source cart; late or ambiguous payment
evidence is recorded for manual review and does not reopen the hold automatically.

### FulfilmentSelection

- Retail order reference
- Method: `PICKUP` or `GHTK`
- Recipient name, phone, address and optional note while retention applies
- Pickup location snapshot when applicable
- Accepted server-issued GHTK quote identifier, components and total
- Branch-specific shipping fee shown in the grouped checkout
- Quote timestamp and provider correlation

Recipient validation uses name 1–100 Unicode characters, a domestic mobile
number in 10-digit `0` form or equivalent `+84` form, address 10–500 Unicode
characters, and an optional note of at most 500 characters after trimming.

The snapshot becomes immutable after checkout acceptance. Recipient data is
deleted or irreversibly obscured 30 days after actual GHTK handoff, while
minimized custody/order evidence remains.

Before checkout acceptance, a quote request uses the current cart/branch and
recipient input but creates no order, hold or payment attempt. The React client
keeps a failed-request recipient draft only in current in-memory checkout state;
FE-08 does not persist it in browser storage. A successful quote projection has
an immutable identifier, branch, expiry and fee components. The client submits
the identifier, not a trusted fee. Acceptance revalidates the quote against its
Member, branch, cart version and recipient input; expiry or fee change requires
a new quote and explicit grouped-total reconfirmation.

### GhtkShipment

- Retail order and fulfilment references
- Mland partner order identifier
- Provider `label_id` and tracking identifier
- Creation state: `PENDING`, `CREATED`, `FAILED`, `RECONCILIATION_REQUIRED`
- Provider correlation and safe failure code
- Normalized current status and last status time
- Provider estimated delivery date when supplied
- Created/updated timestamps

The partner order identifier is immutable and is reused for safe create retry.
Provider credentials and raw provider payloads are not persisted.

### CarrierStatusEvent

- Shipment reference
- Provider event identity or safe deduplication fingerprint
- Provider status code and normalized customer-facing status
- Provider action time and received time
- Reason code projection, if safe and needed
- Duplicate/stale/accepted processing outcome

Out-of-order events do not regress the current normalized status. The Member
view exposes the latest normalized status, last update time, and estimated
delivery date when available, not the raw provider event history.

### FulfilmentEvidence

- Retail order/shipment reference
- Preparation or pickup/handoff milestone
- Authorized actor and branch scope
- Actual handoff time and carrier reference
- Safe verification/correlation evidence

Pickup and carrier handoff are idempotent terminal business effects.

## State model and invariants

```text
Checkout accepted
        |
ACCEPTED_UNPAID -- Member cancels --> Hold released / cart restored
        |
        +-- authoritative payment failure/15-minute expiry --> Hold released / cart restored
        +-- server persists VNPay initiation --> PAYMENT_INITIATED
                                                | verified full payment
Paid / fulfilment selected
        +-- Pickup --> Preparing --> Ready for pickup --> Picked up
        +-- GHTK --> Creating shipment --> Shipment created
                                      --> Preparing --> Handed to carrier
                                                     --> Carrier status updates
```

1. One accepted grouped checkout creates exactly one complete branch order per
   recorded branch group and one complete hold bundle, or none.
2. One grouped checkout has exactly one accepted payment outcome; that outcome
   applies to all of its branch orders.
3. One Mland partner order identifier maps to at most one GHTK shipment.
4. A shipment-create retry never changes the branch order's payment state or
   uses a new partner identifier.
5. A valid duplicate webhook is a no-op after the first accepted event.
6. A customer-facing order read is restricted to the owning Member; Staff and
   Manager reads are restricted by branch and delegated action scope.
7. Provider delivery failure, cancellation, or return status does not create an
   automatic refund, cancellation, or return workflow.
8. Production without approved GHTK access exposes pickup only for the affected
   branch; it never substitutes a mock or manual carrier workflow.
9. Quote failure for one branch does not silently change fulfilment, remove cart
   lines, or invalidate eligible choices for another branch.
10. Checkout acceptance reserves FE-06 benefits through `loyaltypromotion::facade` in
    the same transaction as all branch orders and source holds; either every
    source and benefit record commits or all roll back.
11. Verified full payment or approved zero-total acceptance settles benefits
    before granting earned points. An authoritative unpaid payment failure or
    expiry releases HELD benefits, marks pending earn ineligible, and restores
    the source cart. Duplicate outcomes are no-ops;
    ambiguous or conflicting outcomes remain unresolved and never release,
    consume, grant, or extend a benefit automatically.
12. The recipient draft and a shipping quote exist only for the current
    checkout session before acceptance. An immutable fulfilment/recipient
    snapshot is persisted only with accepted branch orders.
13. A Member cancellation is allowed only while the grouped checkout is
    `ACCEPTED_UNPAID`. The server transition to `PAYMENT_INITIATED` records
    `paymentInitiatedAt`; after that point, provider evidence is reconciled
    rather than self-cancelled. Cancellation releases the complete hold bundle
    and restores the source cart.

