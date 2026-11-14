# Policy Decision: Direct Booking Open Folio and OTA Financial Separation

**Date created**: 2026-11-05
**Date updated**: 2026-11-05
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Status and scope

**Owner-confirmed cross-feature policy.** This decision is the source of truth
for the direct-booking monetary lifecycle spanning 003.1 Workshop Booking,
003.2 Workshop Operations, and 003.3 Workshop Settlement and Changes. It also
defines the financial boundary with 003.4 Workshop OTA Connectivity.

This document records the approved business policy. It does not by itself
claim that all backend, frontend, migrations, or acceptance tests have already
been aligned.

## Update 2026-11-12 (owner decisions) — read before the sections below

These decisions replace the matching rules below for new bookings; the older
text is kept for history. Details: 003.1 FR-030–FR-043 and 003.3 FR-030–FR-045.

| Topic | Rule below | Now |
| --- | --- | --- |
| Folio granularity | One folio per booking | One OPEN folio per ticket booking (one person) inside a booking group. |
| Booking-time collection | Deposit `ceil(post/2)` | Per ticket: 0 → free; 1–200,000 → full price; > 200,000 → 200,000; group total 1–4,999 → deferred to checkout. One VNPay payment per group. |
| Verified collection | Only signed VNPay IPN/QueryDR; counter payments not accepted | VNPay (signed evidence), cash confirmed by Staff, or direct transfer confirmed by `workshop.payment.transfer.confirm` after seeing the money; several collections and mixed methods per folio; pending collections do not count. |
| Refunds | VNPay full/partial refund verified by QueryDR (Mland remedy, overcharge correction) | Bank transfer outside the system only: account collection link, Manager approval, transfer by `workshop.refund.execute`, Manager confirms `REFUNDED` after reconciliation. No VNPay Refund call. |
| Customer cancellation | Whole booking | Per ticket; no refund of the booking-time collection; points returned only when cancelled ≥ 24 h before the ticket's first session; voucher use restored once when the whole group ended by valid or Mland cancellation. |
| Mland remedy | Whole booking | Per affected ticket; 100% of the ticket's collected money by bank refund; voucher use restored once even for a partial Mland cancellation. |
| Late payment | Never confirms | Automatic re-confirmation under five conditions, otherwise Manager decision or bank refund (003.1 FR-040). |

Unchanged: booking-time money is non-refundable on customer self-cancellation
or no-show; add-ons only while the folio is OPEN; nothing changes after
`FINALIZED` except the Manager overcharge correction (now refunded by bank); OTA
financial isolation.

## Definitions

| Term | Meaning |
| --- | --- |
| Direct booking | A workshop booking created on an Mland-owned channel, rather than through an OTA. |
| Open folio | The single mutable financial aggregate for a direct booking while it remains open. It contains the immutable base-package charge, accepted on-site add-ons, verified collections, and the computed outstanding balance. |
| Verified collection | A payment backed by trusted evidence from its payment owner. In V1 this is only a signed, verified VNPay IPN or QueryDR result; a browser return or a manually recorded counter payment is not a verified collection. |
| Finalized folio (`FINALIZED`) | A direct-booking folio completed at checkout with `balanceDue = 0`. Its financial lines and totals are immutable. |
| Closed folio (`CLOSED`) | A direct-booking folio ended by ordinary cancellation, full no-show, or a verified Mland-caused remedy refund, at the verified collected (and refunded) amount. Immutable; never grants Loyalty earn. |
| OTA booking | A booking whose base workshop ticket was sold and collected by an OTA. |

## Confirmed policy

### Direct booking: payment choice and open folio

1. At direct-booking acceptance, the customer pays only the deposit:
   `ceil(postPromotionAmount / 2)` whole VND. Paying the full base-package price
   at booking time is not offered (owner decision 2026-11-05). The deposit
   amount and the remaining amount are frozen at acceptance and are immutable.
2. A direct booking owns exactly one open folio from deposit confirmation
   (verified deposit) until checkout or terminal closure (owner decision D1,
   2026-11-05). A pending booking that expires unpaid never has a folio.
3. The base-package price is captured as an immutable booking-time line in the
   folio. The remaining base amount and any accepted add-ons are collected at
   checkout.
4. Every collection is append-only payment evidence. The folio derives:
   - total amount = base-package amount + accepted add-on amount;
   - paid amount = sum of verified collections;
   - balance due = total amount - paid amount.
5. A booking is confirmed after its deposit is verified (`DEPOSIT_PAID`). It is
   `FULLY_PAID` only after checkout collections bring the balance due to zero.

### Direct booking: on-site add-ons and checkout

1. Staff or Manager records an on-site add-on only after the applicable
   customer consent/evidence required by the add-on contract.
2. Each accepted paid add-on is appended to the same open folio. It is not a
   separate direct-booking bill.
3. Checkout calculates the current balance due from the open folio. A final
   collection is required only when that balance is positive.
4. The folio may transition to FINALIZED only after verified collections equal
   its final total. The workshop output may be handed over only under the
   associated completion/handover rules.
5. Once FINALIZED, the folio and its add-ons cannot be edited, removed, or
   added. Corrections require a separately authorized financial remedy flow;
   they must not mutate finalized history in place.
6. Post-FINALIZED correction (owner decisions D7–D10, 2026-11-05): a separate,
   audited folio-correction record handles only an overcharge (money returned
   to the customer). Only an active Manager may open and approve it, with a
   required reason. The overcharge is returned by VNPay partial refund against
   the folio's verified payments, allocated oldest payment first and capped by
   each payment's unrefunded amount, and is complete only after signed QueryDR.
   An undercharge discovered after `FINALIZED` is absorbed by Mland: it is
   never collected, so the no-deferred-collection rule has no exception. The
   finalized folio, its lines, and Loyalty earn are never changed. A new
   purchase after checkout is not a correction.

### Direct booking: cancellation and no-show

1. Cancellation or no-show closes the folio at the amount actually verified as
   collected at that time. It does not leave an artificial receivable for an
   uncollected remainder.
2. Direct-booking deposits are non-refundable. A normal cancellation or
   no-show must not create a refund allocation or invoke VNPay QueryDR/refund.
3. An explicitly approved Mland-caused remedy remains a separate, audited
   exceptional process. It is not implied by ordinary cancellation or no-show.
   Once its refund is verified, the folio is closed as `CLOSED` with the
   collected and refunded amounts (owner decision D11, 2026-11-05).
4. A booking is a no-show only when every ticket on every required session has
   a final `NO_SHOW` (owner decision 2026-11-05). If at least one ticket
   attended any required session, the booking is not a no-show: it continues
   to normal checkout for the full package price plus accepted add-ons, and a
   Member booking earns Loyalty points as for full attendance once the folio is
   `FINALIZED` (owner decision D6, 2026-11-05).

### OTA financial isolation

1. The OTA collects 100 percent of the workshop-ticket/base-package price.
   Mland must not create a direct-booking folio or a VNPay collection for that
   OTA ticket price.
2. A paid add-on selected at the Mland shop for an OTA customer is an Mland
   add-on bill separate from the OTA ticket and settlement. Its price comes
   from the active 003.2 catalogue add-on for the package/location, derived by
   the server (D17); in-scope Staff or any active Manager may quote, record
   acceptance and bill it (D18).
3. Free laser engraving is included for every OTA-origin booking at the Mland
   shop, recorded at zero charge (owner decision D16, 2026-11-05; 003.4
   FR-019). It creates no bill.
4. OTA ticket refunds and payout adjustments are handled in the OTA/provider
   process; they do not use the direct-booking VNPay refund lifecycle.

## Required lifecycle invariants

| Event | Direct booking folio | OTA ticket / shop add-on |
| --- | --- | --- |
| Booking accepted (pending payment) | No folio yet; deposit and remaining amount are frozen by 003.1. | Do not create an Mland ticket folio. |
| Deposit verified | Create one OPEN folio with the immutable base line and the verified deposit as its first collection. | OTA owns ticket collection. |
| On-site paid add-on accepted | Append line to the same OPEN folio. | Create or update a separate Mland shop add-on bill. |
| Checkout | Verify final collection if balance is positive; then FINALIZE. | Settle any Mland shop add-on bill independently. |
| Finalized | Reject add-on mutation. | Preserve financial separation. |
| Overcharge found after FINALIZED | Manager folio correction refunds it via VNPay partial refund; folio stays `FINALIZED` and unchanged. An undercharge is absorbed by Mland. | Not applicable. |
| Cancellation/full no-show | Close as `CLOSED` at actual verified collection; no normal refund. | Follow provider/OTA process. |
| Mland-caused remedy refund verified | Close as `CLOSED` with collected and refunded amounts. | Not applicable. |

## Ownership boundaries

| Concern | Owning feature/module |
| --- | --- |
| Direct booking acceptance, deposit collection, base price snapshot | 003.1 Workshop Booking |
| Capacity, attendance, no-show and handover facts | 003.2 Workshop Operations |
| Booking service and financial status changes (`COMPLETED`, `CANCELLED`, `NO_SHOW`, `FULLY_PAID`, `REFUND_PENDING`, `REFUNDED`) on Settlement conclusions (D13/D14) | 003.1 Workshop Booking |
| Direct open folio, on-site add-ons, checkout, finalization and closure; the separate Mland shop add-on bill of an OTA booking and its `SHOP_ADDON` collection (D12) | 003.3 Workshop Settlement and Changes |
| OTA mapping, provider evidence, OTA finance/payout reconciliation, shop add-on quote and customer consent for an OTA booking | 003.4 Workshop OTA Connectivity |
| VNPay attempt, verified payment evidence, and exceptional provider remedy only | paymentintegration |

## Alignment required before implementation is declared complete

The following work must be represented in the downstream feature artifacts and
proved by integration tests before related tasks can be marked complete:

1. 003.1 must collect only the deposit at acceptance and preserve the
   immutable booking-time base-price/deposit evidence contract.
2. 003.3 must model and operate the one-open-folio lifecycle, including
   append-only collections, direct on-site add-ons, balance calculation,
   finalization, and terminal cancellation/no-show closure.
3. Existing designs that require a direct add-on bill only after a final bill is
   finalized must be replaced by this open-folio policy.
4. 003.4 must retain its separate OTA ticket and shop-add-on boundary.
5. Schema evolution, idempotency, authorization, audit evidence, and
   cross-module tests must prove that no duplicate collection, add-on, or
   finalization can occur.

