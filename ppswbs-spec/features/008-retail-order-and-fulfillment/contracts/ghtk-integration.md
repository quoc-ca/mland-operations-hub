# GHTK Integration Contract: FE-08

**Status**: Draft / mock-provider target

This document is the provider boundary companion to the client OpenAPI
contract. Automatic GHTK is approved V1 FE-08 scope. The production GHTK
account, callback authentication, quotas, prepaid/no-COD service mapping, and
partner approval are still go-live gates. If those gates are not met,
production fails closed to pickup with contact-Staff guidance and never uses
the mock adapter.

## Grouped checkout relationship

The Member pays one grouped checkout through VNPay, but GHTK remains scoped per
branch order. Each branch order receives its own fee quote, recipient snapshot,
partner order identifier, shipment, and normalized carrier status. The combined
VNPay amount is the sum of the branch-order item totals and the accepted GHTK
charges shown separately in checkout; one GHTK shipment is never shared across
branches.

## Authentication and transport

- Mland calls GHTK server-to-server over HTTPS.
- Provider token and partner source code are server-side configuration only.
- The documented outbound headers are `Token` and `X-Client-Source`.
- The webhook is an inbound provider callback. The configured callback URL and
  provider-approved authentication/hash mechanism must be validated before
  production exposure. Do not treat a query-string example as sufficient
  security evidence.
- Timeout, retry budget, correlation ID, and safe error mapping are owned by
  the adapter. Raw credentials and raw callback payloads are not logged.

## Operations

| Operation | Provider shape | Mland behavior |
| --- | --- | --- |
| Fee quote | `GET /services/shipment/fee` | Call before order creation using the authenticated Member's current cart/branch context, destination, weight in grams, declared value and approved service. Return a server-issued quote identifier bound to the Member, branch, cart version, recipient input, fee components and expiry. The quote request creates no order, hold or payment attempt, and checkout never trusts a client-supplied fee. |
| Create shipment | `POST /services/shipment/order` | Call after verified grouped payment for each paid GHTK branch order. Use that order's immutable Mland partner order ID. Store provider label/tracking identifiers exactly once. No COD collection. |
| Read status | `GET /services/shipment/v2/{TRACKING_ORDER}` | Use the stored label ID or partner ID to reconcile a missing/stale webhook. Normalize current status and estimate. |
| Status webhook | Provider `POST` to Mland callback | Validate required fields, deduplicate, reject stale regressions, persist normalized evidence, then acknowledge valid updates with HTTP 200. |

## Normalized status vocabulary

The adapter maps provider status IDs to a stable customer vocabulary:

| Customer status | Example provider status IDs | Meaning |
| --- | --- | --- |
| `CREATION_PENDING` | no label yet | Paid order waiting for shipment creation. |
| `ACCEPTED` | 1, 2 | Provider has not yet picked up the parcel. |
| `PICKED_UP` | 3, 12 | Parcel has been collected or entered the provider network. |
| `IN_TRANSIT` | 4 | Provider reports delivery routing/in progress. |
| `DELIVERED` | 5, 6 | Provider reports successful delivery/settlement state. |
| `DELAYED` | 8, 10 | Provider reports a delay or deferred movement. |
| `DELIVERY_FAILED` | 7, 9 | Pickup or delivery could not be completed. |
| `RETURNING` | 20 | Parcel is being returned. |
| `RETURNED` | 11, 13, 21 | Provider reports a returned/compensated outcome. |
| `CANCELLED` | -1 | Provider reports cancellation. |
| `UNKNOWN` | unrecognized | Preserve safe provider code for operations; do not expose raw payload. |

The exact mapping must be covered by contract tests and versioned if GHTK
changes status semantics. A provider status never automatically creates a
refund, cancellation, or return workflow in FE-08.

## Retry and idempotency

1. A fee quote timeout is retryable before payment; checkout must not accept an
   unquoted GHTK charge. The client preserves the cart and current in-memory
   recipient draft and may retry or explicitly select pickup for that branch.
2. A create timeout after payment preserves the paid order and shipment-pending
   state. Retry with the same partner order ID.
3. If the provider reports that the partner ID already exists, retrieve the
   existing shipment/status and reconcile rather than creating a new ID.
4. A webhook duplicate is a no-op after durable acceptance. An older action time
   cannot move the normalized current status backward.
5. Missing or stale webhook data triggers a controlled status GET rather than a
   second shipment creation.
6. An expired or changed quote is re-requested and the difference/new grouped
   total is shown for explicit Member reconfirmation before payment initiation.
7. Production without approved GHTK access returns a safe unavailable result,
   keeps pickup available, and never falls back to mock or manual shipment creation.

