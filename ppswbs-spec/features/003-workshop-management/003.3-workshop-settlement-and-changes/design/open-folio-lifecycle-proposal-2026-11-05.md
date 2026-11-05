# Open-folio lifecycle boundaries — proposal

**Date created**: 2026-11-05
**Date updated**: 2026-11-05
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Status**: **APPROVED — 2026-11-05 by Hồ Hoàng Quốc (owner of Feature 003,
including 003.1–003.4).** D1–D5 below are decided. The approved boundaries
are recorded in
[`workshop-interfeature-facades.md`](../../contracts/workshop-interfeature-facades.md),
which is authoritative for them. This note does not select concrete Java
types, physical schema, Flyway version, or locking, and does not mark any task
complete.

## Problem

The [open-folio policy](../../direct-booking-open-folio-policy.md) requires one
direct-booking folio from deposit confirmation until checkout or terminal
cancellation/no-show closure. The current 003.3 design creates the final bill
only after 003.2 reports every required session as `ATTENDANCE_FINALIZED`
(`POST /final-bill`). Three lifecycle points therefore have no boundary:

1. opening the folio and recording the deposit as its first collection;
2. closing it on an Operations-final no-show;
3. closing it on an accepted ordinary customer cancellation.

## Design constraints (existing, unchanged)

- Facade rule 5: an event never decides a financial conclusion. An event may
  only trigger the owner, which then reads authoritative facts through a facade
  query and decides in its own transaction.
- 003.3 already consumes `MlandBookingConfirmedEvent(bookingReference,
  confirmedAt)` after commit (003.1 T061) to snapshot the booking-time policy.
- 003.3 already reads `ConfirmedBookingFinancialProjectionQuery` and
  `VerifiedBookingDepositPaymentsQuery` from `WorkshopBookingFacade`.
- Operations is authoritative for attendance and no-show facts; Settlement must
  not read Operations entities.
- OTA bookings have no direct folio and are out of scope.

## Proposed boundaries

### P1 — Open folio at deposit confirmation (reuses existing boundaries)

- Trigger: the existing `MlandBookingConfirmedEvent` consumer in 003.3.
- In one Settlement transaction, idempotent per `bookingReference`:
  1. read `ConfirmedBookingFinancialProjection` (frozen post-promotion amount,
     deposit, remaining amount);
  2. read `VerifiedBookingDepositPaymentsProjection`;
  3. create the one OPEN folio with the immutable base line and append the
     verified deposit as the first collection.
- No new 003.1 command or event is needed. Replay of the event returns the
  existing folio.
- Consequence: `POST /final-bill` would no longer create the bill; it would
  read or no-op on the existing folio (OpenAPI change required).

### P2 — Close folio on final no-show (one new 003.2 query + one event)

- New `WorkshopOperationsFacade` query, proposed name
  `BookingAttendanceOutcomeQuery(bookingReference)` →
  `BookingAttendanceOutcome(terminal, outcome)`, where `outcome` is one of
  `ATTENDED`, `NO_SHOW`, or `PARTIAL` (exact values to approve). `NO_SHOW`
  follows decision D2: every ticket on every required session is a final
  `NO_SHOW`; `PARTIAL` proceeds to normal checkout.
- Trigger: a reference-only post-commit event from 003.2, published once per
  booking when booking-wide attendance becomes terminal (every required session
  `ATTENDANCE_FINALIZED` and the existing 24-hour Manager correction window
  closed), name `BookingAttendanceFinalizedEvent(bookingReference,
  finalizedAt)`. Publishing at window close (corrected 2026-11-05) prevents a
  folio from being `CLOSED` before a permitted correction.
- Settlement, on that event, reads the query; when `terminal = true` and
  `outcome = NO_SHOW`, it closes the OPEN folio at the verified collected
  amount in one idempotent transaction: no refund, no VNPay QueryDR/refund, no
  receivable. Otherwise it does nothing.

### P3 — Close folio on accepted ordinary cancellation (no new boundary)

- In the existing customer-cancellation flow (003.3 FR-015), when Operations
  accepts the whole-booking cancellation and the case becomes `RESOLVED`,
  close the folio at the verified collected amount in the same Settlement
  transaction.
- A Mland-caused remedy keeps its separate refund flow and is not affected.

## Owner decisions (2026-11-05)

| ID | Decision | Consequence |
| --- | --- | --- |
| D1 | **Decided:** open the folio at deposit confirmation on `MlandBookingConfirmedEvent` (P1). `POST /final-bill` no longer creates a bill; it returns the existing folio. | Public OpenAPI behaviour changes; existing T013/T019 code and its frontend caller must be realigned under 003.3 T087. |
| D2 | **Decided 2026-11-05 (owner):** a booking-level no-show means every ticket on every required session has a final `NO_SHOW`. Partial attendance (at least one ticket attended any required session) is not a booking no-show and continues to normal checkout for the full package price plus accepted add-ons. | Resolved; P2 `outcome = NO_SHOW` uses this rule. |
| D3 | **Decided:** P2 as proposed — reference-only `BookingAttendanceFinalizedEvent(bookingReference, finalizedAt)` plus `BookingAttendanceOutcomeQuery(bookingReference)` → `BookingAttendanceOutcome(terminal, outcome: ATTENDED \| NO_SHOW \| PARTIAL)`, owned by 003.2. | New 003.2 task (T088); 003.3 consumes the event idempotently and decides only from the query. |
| D4 | **Decided:** a separate terminal state `CLOSED` for cancellation/no-show closure at the verified collected amount; `FINALIZED` remains checkout with `balanceDue = 0`. Both lock the folio. | New folio state in persistence and projections; Loyalty earn is never triggered by `CLOSED`. |
| D5 | **Decided:** append-only Flyway backfill creates one OPEN folio for each existing confirmed direct booking from its frozen amounts and verified deposit receipts. | Requires MySQL empty-database and data-bearing upgrade tests; no reset, no edit of applied migrations; idempotent with the P1 consumer. |

| D6 | **Decided:** a `PARTIAL` booking earns Loyalty points like `ATTENDED` once its folio is `FINALIZED` with zero balance; `WORKSHOP_NO_SHOW_FINAL` only for a full no-show. Replaces the earlier "any ticket no-show → no earn" rule. | Updates `006-loyalty-promotion/contracts/workshop-booking-integration.md` and the existing `WorkshopLoyaltyOutcomeProcessor` behaviour (003.3 T089). |

### T040 post-FINALIZED correction decisions (owner, 2026-11-05)

| ID | Decision | Consequence |
| --- | --- | --- |
| D7 | **Scope (revised 2026-11-05):** the correction flow handles only overcharge (refund to customer). An undercharge found after `FINALIZED` is absorbed by Mland and never collected, preserving the no-deferred-collection rule. A new purchase after checkout is not a correction. | One correction record type (overcharge refund); the finalized folio stays immutable. The earlier "both directions" choice is withdrawn. |
| D8 | **Authority:** only an active Manager (global scope) opens and approves a correction, with a required reason and business audit. | Staff cannot open corrections; no separate approval state is needed. |
| D9 | **Overcharge refund:** VNPay partial refund through the existing `paymentintegration` adapter (T037/T038), allocated oldest verified payment first and capped by each payment's unrefunded amount (same rule as the Mland-caused remedy), confirmed only by signed QueryDR. | Live refunds remain gated by T005; uses the existing `REFUND` evidence kind. |
| D10 | **Endpoints (revised 2026-11-05):** the deprecated `direct-addon-bills` create and read operations and their code are repurposed as the folio-correction resource; the `payment-attempts` operation is removed because no undercharge is collected. | Rename/redefine routes, DTOs, controller and Staff UI under T040; drop the "add-on after finalize" behaviour and the direct `SHOP_ADDON` payment attempt. |

### Mland-caused remedy closure (owner, 2026-11-05)

| ID | Decision | Consequence |
| --- | --- | --- |
| D11 | After a Mland-caused remedy refund (FR-020/FR-021) is verified by signed QueryDR for every allocation, the folio moves `OPEN` → `CLOSED`, recording collected and refunded amounts. This applies to a full or policy-partial refund. | No add-on after remedy; `CLOSED` never grants Loyalty earn; a pending/unknown refund keeps the folio `OPEN`. |

### Cross-file ownership and status decisions (owner, 2026-11-05)

| ID | Decision | Consequence |
| --- | --- | --- |
| D12 | 003.3 owns the separate Mland shop add-on bill of an OTA booking and its `SHOP_ADDON` collection; 003.4 owns the shop quote, customer consent and all OTA channel facts. | Policy ownership table and 003.3 FR-001/Scope/Out of Scope carry this single OTA exception; matches T036 and the existing code. |
| D13 | Booking service status after folio closure is set only by 003.1, on a new idempotent Settlement conclusion: `FINALIZED` → `COMPLETED`; `CLOSED` by customer cancellation or Mland remedy → `CANCELLED`; `CLOSED` by full no-show → `NO_SHOW`. | New `WorkshopBookingFacade` command `RecordBookingServiceOutcomeCommand`; 003.1 implements `COMPLETED`/`NO_SHOW` and post-confirmation `CANCELLED` (003.1 T088). |
| D14 | During a Mland-caused remedy refund 003.1 sets financial status `REFUND_PENDING` when Settlement dispatches the first allocation and `REFUNDED` when every allocation is verified. A post-`FINALIZED` overcharge correction does not change booking financial status. | New `WorkshopBookingFacade` command `RecordBookingRefundStatusCommand`; 003.1 T088. |
| D15 | A Mland-caused remedy of a Member booking emits Loyalty `VALID_WORKSHOP_CANCELLATION` (`restoreCancelledWorkshopBenefits` then `finishIneligibleEarn`). | 006 workshop integration contract updated; implemented with 003.3 T089. |
| D16 | Every OTA-origin booking gets free laser engraving at the shop, recorded at zero charge with no bill (003.4 FR-019). | Policy text aligned to 003.4. |
| D17 | A paid shop add-on for an OTA booking uses the active 003.2 catalogue add-on for the package/location; the server derives the price, as for direct bookings. Staff/Manager cannot type an amount. | 003.4 OpenAPI `PAID_OPTION` takes `catalogueAddOnReference` instead of `itemCode`/`amount`; `OtaAddonPage.jsx` and the backend quote service change (003.4 T055). |
| D18 | In-scope Staff or any active Manager (global) may create, accept and bill an OTA shop add-on quote, consistent with direct-booking add-ons. | 003.4 contract/spec actor rule changes from Staff-only; authorization tests change (003.4 T055). |

## Follow-up artefact changes (applied 2026-11-05 unless noted)

- `contracts/workshop-interfeature-facades.md`: add P2 query/event; record P1
  and P3 behaviour; remove the corresponding "Open-folio policy gaps" bullets.
- `003.3 spec.md`, `plan.md`, `data-model.md`, OpenAPI: folio creation point,
  `CLOSED` state, `POST /final-bill` behaviour.
- `003.2` spec/tasks: the new query and post-commit event.
- `003.3 tasks.md` T087: implementation and MySQL acceptance per P1–P3.
