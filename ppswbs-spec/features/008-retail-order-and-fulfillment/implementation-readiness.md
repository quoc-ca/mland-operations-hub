# FE-08 Implementation Readiness

**Date created**: 2026-10-26
**Date updated**: 2026-10-30
**Author**: Thinh
**Last updated by**: Thinh

## Approved development slices: Member cart and local mock checkout

The first backend slice implements US1 (the Member retail cart). The second
approved slice implements a demonstrable, persisted US2 checkout under the
`local` Spring profile. Its FE-02 catalogue/stock, FE-06 benefit, VNPay, and
GHTK inputs are deterministic development substitutes, not live integrations.
Non-local profiles fail closed until their owning facades are merged.

The local checkout creates branch orders grouped under one checkout and keeps
immutable price, benefit, and fulfilment snapshots. It may expose a mock
payment redirect and a locally verified payment outcome, but it must never
present that outcome as a VNPay transaction, redeem a real voucher or point
balance, create a real GHTK label, or accept a browser-declared payment
success. Replacing an adapter later must preserve the REST and persistence
semantics of this slice.

## Module and boundary mapping

| Concern | Approved decision for this slice |
| --- | --- |
| FE-08 module | `features.readyringsales`, detected as an independent Spring Modulith module. |
| Member identity | The local adapter accepts only a positive `X-Debug-Account-Id` for the approved local demo slice. Outside `local`, FE-08 consumes the authenticated `CurrentAccountActor` issued through `accountmanagement::facade` and allows only an `ACTIVE` `MEMBER`; client-supplied account identifiers are not trusted. The local header is not a production access path. |
| Catalogue eligibility | `readyringsales` calls its local `RetailCataloguePort` substitute. The non-local cart adapter uses FE-02 `readSaleFacts` without a catalogue lock; the cart response includes indicative product facts, while persisted cart ownership and lines remain unchanged. Checkout acceptance must revalidate through `withLockedSaleContext`. |
| Loyalty, payment, GHTK | Local checkout uses deterministic substitutes only in the `local` profile. Outside `local`, a pickup preview uses FE-02 and FE-06 and stores an FE-08 quote for 15 minutes; real benefit reservation and acceptance are not implemented. Payment and GHTK adapters remain unavailable outside `local`. |
| Location identity | Every cart line stores `locationId BIGINT`, matching the account-management `location_id` type. No location foreign key is created because catalogue/location ownership is outside FE-08. |

## V13 physical schema and concurrency design

`V13__retail_cart.sql` is the append-only migration that creates only these
FE-08-owned records:

| Table | Ownership and constraints |
| --- | --- |
| `retail_carts` | UUID-string primary key; `member_account_id` foreign key to `accounts`; one cart per Member through a unique key; JPA optimistic `version`; creation/update timestamps. |
| `retail_cart_lines` | UUID-string primary key; owning `cart_id` foreign key; canonical FE-02 numeric `variant_id BIGINT`; `location_id BIGINT`; quantity constrained to 1-99; unique `(cart_id, variant_id, location_id)`; creation/update timestamps. |

Cart commands update the cart aggregate version. A conflicting concurrent
write is returned as a typed conflict rather than silently overwriting a cart.
The service validates the maximum of 50 lines and the maximum total quantity of
99 for the same variant across all locations inside the command transaction.
Cart edits never reserve inventory, redeem benefits, create payment attempts,
or create retail orders.

## V14 local checkout schema and concurrency design

`V14__retail_mock_checkout.sql` is an append-only migration for the approved
local checkout slice. It owns checkout facts only; it does not create a real
provider shipment or a cross-feature voucher/points ledger.

| Table | Ownership and constraints |
| --- | --- |
| `retail_grouped_checkouts` | UUID-string primary key; owning Member; unique member/idempotency key; cart version snapshot; aggregate merchandise, shipping, discount and payable snapshots; payment state and initiation timestamp; optimistic version. |
| `retail_orders` | UUID-string primary key; owning grouped checkout; one immutable branch order per `location_id`; payment and fulfilment state; per-branch money snapshots; unique `(grouped_checkout_id, location_id)`. |
| `retail_order_items` | UUID-string primary key; owning retail order; immutable canonical FE-02 numeric `variant_id BIGINT`, quantity and server-priced unit/line total snapshots. |
| `retail_checkout_holds` | UUID-string primary key; one hold bundle per grouped checkout; local status/deadline/expiry facts only. It represents no real inventory or benefit reservation. |

Checkout acceptance locks the owned cart row through a repository lock query,
validates exactly one submitted fulfilment choice for every cart location, then
persists the full checkout aggregate in one transaction. It accepts one
idempotency key only when the same Member owns it; a repeat returns the
existing checkout, while a key owned by another Member is not visible.

The local mock prices every eligible cart line deterministically and never
trusts a client price, fee, discount, payment state, or carrier reference.
The local payment adapter records `PAYMENT_INITIATED` before producing its
server-side mock verification result. This is a development demo mechanism;
it is replaced by verified VNPay evidence before production.

## Deferred migration allocation

| Migration | Deferred scope | Gate |
| --- | --- | --- |
| V24 | Convert empty local/test FE-08 `variant_id` columns from legacy text to canonical FE-02 `BIGINT`; the migration fails rather than deleting or coercing data | Run `scripts/local-reset-retail-demo.sql` only against an approved local/test database before migration |
| V25 | FE-08 stable per-variant guard rows and per-checkout variant holds | FE-08 lazy-creates each missing guard inside the FE-02-locked `withGuard` path using an approved atomic MySQL provision-and-lock primitive |
| V26 | Durable FE-08 checkout quotes and per-location/item snapshots | Thinh selected V26; coordinate with other branches and verify shared database history before deployment |
| Unallocated | Durable shipping quotes and accepted GHTK recipient/selection snapshots | Approve a migration number, FE-02 shipping-profile boundary and real quote contract verification |
| Unallocated | Shipments, carrier status events, custody evidence and recipient retention | Approve a migration number, GHTK callback/security and Staff/Manager delegation design |

Shipping and shipment migrations remain unallocated and unauthorized by this document.
