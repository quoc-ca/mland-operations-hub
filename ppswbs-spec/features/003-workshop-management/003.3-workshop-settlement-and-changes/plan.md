# Implementation Plan: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-11-17
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Branch**: `003.3-workshop-settlement-and-changes` | **Spec**: [spec.md](./spec.md)

## Summary

Every active canonical Manager has global authority for Manager-authorized
003.3 actions, including policy reads/revisions *(refund-policy reads and
revisions superseded 2026-11-15: the Mland-cancellation refund policy and its
routes are removed by T139/T140; FR-043 always refunds 100%)* and cross-location booking
changes. Staff-authorized actions remain location-scoped; Member ownership and
explicitly Admin-only actions remain unchanged. There is no central Manager tier.

003.3 owns direct-Mland post-workshop settlement: exactly one final bill for one
booking/package, accepted additional charges, a short-lived final-payment
checkout attempt, verified final payment before package-output handover,
non-refundable customer cancellation decision, Manager-mediated required-session change, and
Mland-caused cancellation remedy. It also owns the separate Mland shop add-on
bill of an OTA booking (D12) and reports folio outcomes to 003.1 so Booking can
set `COMPLETED`/`CANCELLED`/`NO_SHOW` and remedy `REFUND_PENDING`/`REFUNDED`
(D13/D14). Direct customer cancellation records a
non-refundable deposit decision and does not invoke VNPay refund/QueryDR. It is
not an item/session finance system, cart,
OTA payment system, capacity owner, or normal check-in system.
*(Superseded 2026-11-12/14 for direct bookings, see "Per-ticket settlement":
there is one folio per ticket booking, not one bill per booking; cancellation,
changes and the Mland remedy are per ticket; refunds go by bank, never by
VNPay.)*

## Technical Context

| Topic | Direction |
| --- | --- |
| Platform | Java 21/Spring Boot REST backend and a separate React + Vite client; no SSR. |
| Module | Spring Modulith; only explicit owner facades cross business modules. |
| Persistence | MySQL/JPA and append-only Flyway migrations when implementation is authorized. |
| Identity | Firebase bearer token proves identity; Account Management supplies canonical role/status, ownership and location facts. Active Manager authority is global for Manager actions; Staff actions use canonical `workshop_location.id` and `activeLocationIds`. |
| Payment | VNPay only. Shared `paymentintegration` owns provider credentials, checkout URL generation, provider attempt expiry and payment/IPN evidence. Direct customer-deposit cancellation never calls the refund/QueryDR protocol; OTA refunds remain external to this flow. Final-payment checkout uses the server-derived outstanding VND amount and expires after 10 minutes. Browser Return URL is display-only. *(Superseded 2026-11-12/14, see "Per-ticket settlement": the booking-wide 10-minute final-payment checkout gives way to a `VNPAY` folio collection per ticket (FR-049); payment is also by cash or direct transfer (FR-031); no VNPay Refund or refund-QueryDR is used for Mland money (FR-038).)* |
| Contract | Versioned `/api/v1` REST contract in `contracts/workshop-settlement.openapi.yaml`. *(Superseded 2026-11-12/14: per-ticket routes are in `contracts/workshop-settlement-ticket.openapi.yaml`; see "Per-ticket settlement" and "Public REST Design".)* |
| Validation | Module-boundary, authorization, lifecycle, idempotency, facade, contract, migration-upgrade and recovery evidence. |

## Constitution Check

| Gate | Result | Evidence / condition |
| --- | --- | --- |
| Module integrity | PASS | 003.3 consumes only the named 003.1/003.2 facades and owns only settlement records/cases. |
| Identity and sensitive data | PASS with FE-001 dependency | Firebase is identity-only; server rechecks canonical account facts plus Settlement's location/resource action rules. No contact proof, raw token, QR payload, provider secret or raw callback is retained in audit. |
| REST and frontend | PASS | The OpenAPI contract defines resource routes, authorization alternatives, idempotency, safe projections and typed errors. React consumes REST only. |
| State, failure and idempotency | PASS | Client commands retain idempotent outcomes; verified provider transaction/reference deduplicates IPN/QueryDR. Cases have explicit lifecycle and correlation. |
| Refund account numbers at rest (FR-040, T097) | PASS (code, 2026-11-13) | `RefundAccountCipher` encrypts each number with AES-GCM and a fresh nonce; only the mask `****1234` is stored beside it and kept after `REFUNDED` (ciphertext dropped). The number never goes to email, logs or audit. |
| Refund account key secret channel | PASS with deployment condition | The 256-bit key comes only from the environment variable `WORKSHOP_REFUND_ACCOUNT_KEY` (base64); it is never committed, logged or kept in `.env` under version control. Each environment must supply it through its approved secret channel. ~~Key rotation is not designed (Open).~~ Decided 2026-11-15 (spec FR-040): no in-system rotation in V1; when the key is replaced, every request still holding a ciphertext has it deleted (mask kept), returns to `PENDING_ACCOUNT` and the payer gets a new account link, as an audited operational step (task T137). |
| Audited account-number reveal | PASS (code, 2026-11-13) | `GET /api/v1/workshop-refund-requests/{refundId}/account-number` is Manager-only, audited on every read (`REFUND_ACCOUNT_REVEALED`) and served `Cache-Control: no-store`; it is the one route that returns the number in clear. |
| Anonymous refund-account link route | PASS (code, 2026-11-13) | `POST /api/v1/refund-account-links/{token}` is `permitAll` in `SecurityConfiguration` and allow-listed in `IdentityGateFilter`; the single-use token (7 days, resend revokes) is the credential, kept in the URL fragment of the frontend page, stored only as a hash, never returned or logged; 404 unknown, 410 used/revoked/expired; the response never echoes the number. |
| Data evolution | PASS with rollout condition | Only dev/test without retained booking/customer data may reset after preflight. Data-bearing environments require append-only preservation migration, upgrade test, recovery/rollback evidence. |

## Ownership and Internal Boundaries

```text
paymentintegration ─ verified final-payment/OTA shop add-on (SHOP_ADDON) evidence (refund evidence: legacy, no longer produced) ─► 003.3
003.1 Booking ─ confirmed financial projection / FULLY_PAID conclusion ─► 003.3
003.2 Operations ─ readiness / atomic replacement / verified-refund effect *(OTA-only/legacy since 2026-11-15; a ticket cancellation is the trigger)* ─► 003.3
FE-001 Account ─ current authorization, ownership and location scope ─► 003.3
VNPay ─ signed IPN / QueryDR evidence ─► paymentintegration
003.3 ─ reference-only post-commit settlement notification handoff ─► Feature 007
```

*(2026-11-15: the first arrow formerly read "final-payment/refund/OTA shop
add-on evidence". `REFUND` evidence is legacy: VNPay Refund and refund-QueryDR
are not used and their code was removed (bc39429c, T128; spec FR-038, FR-009c).)*

| Concern | Owner | 003.3 rule |
| --- | --- | --- |
| Confirmed booking, frozen price/deposit, `FULLY_PAID` | 003.1 | Read/update only through `WorkshopBookingFacade`. |
| Sessions, capacity, entitlements, attendance, cancellation state | 003.2 | Read/change only through `WorkshopOperationsFacade`. |
| Final bill, charge, payment/refund/change case and settlement audit | 003.3 | Authoritative owner. |
| Canonical account role/status, ownership and location scope | FE-001 | Recheck facts at every protected command; Settlement applies its own action rules and never infers authority from display roles or claims. |
| Delivery | Feature 007 | Verified settlement/refund notification handoff is persisted with accepted state; its post-commit reference event lets Feature 007 read the facade payload, deduplicate and retry. Recipient/content expire after 30 days. Payload contains no payment-action link and delivery cannot settle a bill or refund. |

Workshop Branch is not a second resource in this feature: every booking,
session, catalogue and audit scope is the canonical `workshop_location.id`.
For Staff actions Settlement compares it directly with `activeLocationIds`;
Manager-authorized actions are global. It stores neither a separate branch
identifier nor a branch-to-location mapping.

The concrete owner operations and boundary types are fixed by the shared
[003 internal facade contract](../contracts/workshop-interfeature-facades.md).
The facade is synchronous for booking-critical conclusions. Events are emitted
only after a local commit for delivery/downstream handoff; events never decide
capacity, final payment, refund, or Booking `FULLY_PAID`.

## Settlement Design

### Financial invariant

One confirmed direct booking/package has at most one final bill. It receives the
frozen post-promotion amount, accepted deposit and frozen remaining amount from
003.1. It never recalculates voucher, points, deposit or remaining amount. Its
amount due is the frozen remaining amount plus accepted charges, less verified
final-payment/refund consequences as applicable. *(Superseded 2026-11-12 by
spec FR-030: one folio per ticket booking; see "Per-ticket settlement".)*

**Open-folio policy (2026-11-05)**: per
[direct-booking-open-folio-policy.md](../direct-booking-open-folio-policy.md),
the "final bill" above is the direct booking's one OPEN folio. Policy requires
it to exist from 003.1 deposit confirmation (deposit as first verified
collection) until checkout or terminal closure; `balanceDue = totalAmount −
paidAmount`. Ordinary cancellation/no-show closes it as `CLOSED` at the
verified collected amount with no refund or VNPay QueryDR/refund; a verified
Mland-caused remedy refund also closes it as `CLOSED` (D11); a post-`FINALIZED`
overcharge uses the Manager folio correction (D7–D10, FR-004c). Owner decisions D1–D5
(2026-11-05, see `../contracts/workshop-interfeature-facades.md` "Direct-booking
open-folio lifecycle") supersede the paragraph below: Settlement opens the
folio on `MlandBookingConfirmedEvent` with the verified deposit as first
collection; `POST /final-bill` returned the existing folio *(route removed 2026-11-15, T106/T121/T123/T124)*; `ATTENDANCE_FINALIZED`
readiness gates only finalization; Operations' `BookingAttendanceFinalizedEvent`
plus `BookingAttendanceOutcomeQuery` drive `CLOSED` on a full no-show; accepted
customer cancellation sets `CLOSED`; an append-only Flyway backfill opens
folios for existing confirmed bookings. Physical schema and Flyway version are
chosen during 003.3 T087.

The final bill is available only after 003.2 reports every required session as
`ATTENDANCE_FINALIZED`. On verified final-balance resolution, 003.3 invokes
`WorkshopBookingFacade`; 003.1 alone changes booking state to `FULLY_PAID`.
*(Superseded 2026-11-14/15: the conclusion is per ticket, `recordTicketFinalBalanceResolved(ticketCode, …)` (T106(b), BE-DESIGN-0006), and T121 removed the booking-level `RecordFinalBalanceResolvedCommand` branch.)*

### Charges and bill access

Customers may view eligible fixed-price catalogue add-ons for the booking
package/location, but cannot select or create a charge. At the workshop, an
active Staff member scoped to the booking location or a global Manager records
customer consent and the server derives the immutable price from the catalogue
snapshot. A direct booking appends only to its OPEN folio; after `FINALIZED` no
add-on may be added, changed, or removed, and only the exceptional correction
flow (T040) applies. An OTA booking always uses a separate direct-Mland shop
add-on bill (003.4 `/ota-addon-quotes` flow) and never mutates OTA payment or
payout facts; free laser engraving is an OTA-only entitlement with no bill.

Staff in the booking location or any active Manager may create/confirm variable material or service
charges. The command has description, VND amount and reason and produces audit
evidence. A customer cannot submit a variable price/name. Final-bill reads return
a customer-safe projection only after Member ownership or Guest contact proof.

### Cases and lifecycle

Customer cancellation, required-session change and Mland-caused remedy are
booking-scoped, auditable cases. A case moves from
`OPEN` to `RESOLVED`, `REJECTED`, or `FAILED`; only a gateway-waiting refund may
be `PENDING_PROVIDER`. Each case is read through its booking and `caseReference`, never by
ticket/session. *(Superseded 2026-11-12/15: cancellation, change and Mland
remedy are per ticket (spec FR-041–FR-045a) and the booking-scoped case routes
were removed (T106, T123, T124); `PENDING_PROVIDER` now means "awaits verified
refund evidence" (FR-022b1, decided 2026-11-14).)*

For a required-session replacement, Manager supplies the whole target session
set and reason. 003.2 validates/applies every capacity and entitlement change or
none, preserving ticket QR. For Mland-caused replacement, the change carries the
authoritative `cancellationHandoffReference`; an `UNAVAILABLE` or `DECLINED`
remedy references the same handoff. A remedy refund uses the booking's retained
policy snapshot, not a caller amount. *(Superseded 2026-11-15 by FR-043: a
remedy refund is always 100% of the money collected for the ticket; no policy
snapshot is used. Snapshot capture and the policy are removed by T139/T140.)*

Any active Manager may read or revise global refund-policy history. A
revision has a future effective time, integral percentage `0..100`, applies only
to later-confirmed bookings, and computes its snapshot refund as
`floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND for a
Mland-caused remedy. Direct customer cancellation has no refund percentage: the
deposit is forfeited and the booking is cancelled by Operations.
*(The refund-policy history, revisions and percentage above are superseded
2026-11-15 by FR-043 and removed by T139 (backend) and T140 (frontend); see
spec FR-021a–FR-021e.)*

### Payment and retry boundaries

Every client-originated state-changing REST command requires `Idempotency-Key`.
Same key plus semantically identical input returns the original safe result;
changed reuse returns `409 IDEMPOTENCY_CONFLICT`. Each booking-critical command
has one local MySQL transaction for its owned state and audit outcome. Gateway
calls are outside that transaction.

The shared `paymentintegration` boundary owns VNPay IPN `GET
/api/v1/integrations/payments/vnpay/events`, validates signature, resolves the
immutable owner-qualified merchant reference, deduplicates provider delivery,
and dispatches redacted evidence to exactly one owner facade. 003.3 receives
only final-payment, refund, and (for an OTA booking's separate shop add-on
bill) `SHOP_ADDON` evidence and checks bill/refund reference, amount and
currency before its local idempotent outcome. QueryDR is outbound Mland-to-VNPay
reconciliation only. Browser Return URL has no settlement route and never
confirms a bill.

Outbound Merchant API requests are owned by `paymentintegration` and exposed to
business code only through typed technical ports in `common.payment`, avoiding
a Modulith cycle between the inbound dispatcher and 003.3. Each operation uses
an opaque caller operation reference as its idempotency key; the operation row
and a generated VNPay RequestId are committed before network I/O. Reusing the
same operation reference with the same request returns its retained outcome;
changed content conflicts. No automatic network retry is performed. A timeout,
transport failure, or provider response `99` is retained as `UNKNOWN`; the
caller must start a distinct QueryDR operation to reconcile. A refund response
`00` means the refund request was accepted, not that funds were returned. Only
a signature-verified QueryDR result can be presented as reconciliation evidence
to 003.3. Its original `vnp_CreateDate` is supplied as the transaction date;
unknown outcomes never cause an automatic second refund.
*(Superseded 2026-11-15 by spec FR-038 and commit bc39429c: the outbound
Merchant API is now used only for QueryDR of a payment
(`VnpayMerchantOperationsPort.queryTransaction`; in 003.3 by
`FolioGatewayReconciliationScheduler` for a pending `VNPAY` folio collection,
T109). The refund request, its `00`
acceptance semantics and refund-QueryDR were removed; refunds are bank
transfers outside the system. "Refund" evidence from `paymentintegration`
(above) is legacy and no longer produced.)*

## Per-ticket settlement (Phase 8; decisions 2026-11-12, 2026-11-13, 2026-11-14)

Every direct booking is now a 003.1 booking group with one ticket booking per
person (003.1 FR-044–FR-051, 003.1 numbering), and V132 removed every direct
`WorkshopBooking`. Settlement therefore works per ticket (spec FR-030–FR-051):

- **Folio:** one OPEN folio per ticket, opened from `MlandTicketConfirmedEvent`
  and stored in `settlement_final_bills` with the ticket code as its reference
  (T093). Balance is computed in one place (`FolioBalanceService`); counter
  collections, finalization, handover exception, debt and cash day close are
  T094–T096.
- **Routes (FR-048–FR-051, T106):** `/api/v1/workshop-tickets/{ticketCode}/folio*`
  in [`contracts/workshop-settlement-ticket.openapi.yaml`](./contracts/workshop-settlement-ticket.openapi.yaml)
  replace the booking-wide `final-bill*`, `folio-corrections*` and the code
  routes `/workshop-folios/*`. The checkout VNPay payment is a `VNPAY` folio
  collection. The booking-wide routes were removed in the code (T106, T121,
  T123, T124; contract published 2026-11-15); 003.1 T100 removes its legacy
  facade operations.
- **Owner boundaries:** ticket context from 003.1 `getTicketFolioContext`;
  buyer proof for changes through 003.1 `consumeBuyerProof` (MANDATORY);
  ticket reference resolution through `findTicketBookingByOperationsReference`;
  attendance through 003.2 `TicketAttendanceFinalizedEvent` and
  `getTicketAttendanceOutcome` (see `../contracts/workshop-interfeature-facades.md`).
- **Authority and proof:** Manager global; Staff by the session assignment of
  the ticket's group line; Member by bearer and group ownership; a Guest reads
  with one contact proof in a request body and changes with the single-use
  `buyerVerificationReference`. *(V1, owner decisions 2026-11-12/13: the
  codebase has roles, not named permissions, so `workshop.payment.transfer.confirm`
  and `workshop.refund.execute` are held by a Manager; transfer confirmation
  and every refund transition are Manager-only until FE-001 grants them per
  person.)*
- **Money out:** every Mland refund (Mland remedy, overcharge correction, late
  payment, excess money, OTA add-on overcharge) goes through the bank refund
  lifecycle (FR-038–FR-040, T097/T098/T104); no VNPay refund is used for Mland
  money. No add-on and no reopening after `FINALIZED`; a closed cash day stays
  locked (FR-050).
- **Loyalty:** earn, cancellation restore and no-show are sent per ticket as
  006 `WORKSHOP_TICKET_*` outcomes with `partRef` = the 003.1 ticket reference
  (BE-DESIGN-0002, T093/T105).

### State classification (Constitution V, added 2026-11-15)

Every per-ticket state below is owned by 003.3 and stored in MySQL (Flyway
tables as named in `data-model.md`); no state lives only in memory, in the
browser or in an external queue. The React client holds response state only
and recovers by re-reading the server resource.

| State | Storage (MySQL) | Lifecycle | Consistency | Recovery / expiry |
| --- | --- | --- | --- | --- |
| Ticket folio and its collections | `settlement_final_bills` (ticket code as reference; handover and debt columns, V117) and `settlement_folio_collections` | Folio `OPEN` → `FINALIZED` or `CLOSED`; collection `PENDING_VERIFICATION` → `CONFIRMED`/`FAILED`/`VOIDED` | Folio row locked (`PESSIMISTIC_WRITE`) while a balance is assigned; unique (`method`, `external_reference`); `Idempotency-Key` per command | A pending `VNPAY` part is reconciled by `FolioGatewayReconciliationScheduler` (signed IPN or QueryDR, every 5 min for at most 24 h after the checkout expires, T109 *(superseded 2026-11-15, FR-034/CHK016: 24 h from the attempt creation, then automatic `FAILED` as `SYSTEM`; see "Checklist follow-up of 2026-11-15", T149)*); other parts wait for a Manager confirmation or failure |
| Refund request and account link | `settlement_refund_requests`, `settlement_refund_account_links` | `PENDING_ACCOUNT` → `PENDING_APPROVAL` → `PENDING_TRANSFER` → `PENDING_RECONCILIATION` → `REFUNDED`, or `CLOSED_UNREACHABLE` (reopenable); link `ACTIVE` → `USED`/`REVOKED`/`EXPIRED` | Request row locked for transitions; unique (`reason`, `source_reference`) and `request_reference`; token stored only as a unique hash; `Idempotency-Key` per transition | Link valid 7 days, a resend revokes the old one; reminders on days 3 and 7; a Manager may close after 30 days and reopen; key replacement deletes ciphertexts and returns requests to `PENDING_ACCOUNT` (FR-040, T137) |
| Ticket change request | `settlement_ticket_change_requests` (V153) | `PENDING` → `APPROVED`/`REJECTED`/`EXPIRED` | Row locked for a decision; unique `request_reference` and (`ticket_code`, `idempotency_key`); one `PENDING` per ticket | `expires_at` = earliest start of the session being changed and of any requested target session (FR-045a; the code still uses the ticket's first session, fix T130); stale requests are marked `EXPIRED` when read |
| Cash day close | `settlement_cash_day_closes` | `PENDING_MANAGER_CONFIRMATION` → `CLOSED`, or `CLOSED` at once without a difference | Unique (`branch_id`, `business_day`); row lock; `Idempotency-Key` stored with replay (T120) | A closed day is never edited (FR-036, FR-050); cash after the close (the submission of the counted amount) counts for the next day; a wrong cash entry of a closed day is corrected by a Manager cash reversal on the current day (proposed `settlement_cash_reversals`, T144) |
| Mland remedy offer | `settlement_ticket_mland_remedies` (V138, V152) | `PENDING_ANSWER` → `MOVED` or `REFUND` | Unique `remedy_reference`; `Idempotency-Key` on the answer; buyer proof consumed in the same transaction | No answer by `answerDeadlineAt` (24 h before the latest offered session) means refund (FR-043) |
| Absorbed undercharge record (FR-057, planned) | proposed `settlement_absorbed_undercharges` (not yet migrated, T135) | `ACTIVE` → `VOIDED` (once) | Unique `undercharge_reference`; unique (`ticket_code`, `idempotency_key`) for the record command; the void uses its own `void_idempotency_key`; content immutable after insert (aligned with `data-model.md` 2026-11-15) | No expiry; a mistaken record is voided and a new one recorded |

Stateless: the transfer/VNPay/booking-time reconciliation list (FR-056, T133) is read-only: `settlement_folio_collections` + `settlement_evidence_conclusions` (EXCESS) + 003.1 facade `listBookingTimePayments` (BOOKING_TIME); it writes nothing and has
no lifecycle.

### Additions of 2026-11-15 (spec Clarifications "Session 2026-11-15")

Decided by Hồ Hoàng Quốc on 2026-11-15 and tracked in `tasks.md` Phase 10:

- FR-056 read-only Manager transfer/VNPay reconciliation list: backend T133,
  frontend T134 (planned routes in `contracts/workshop-settlement-ticket.openapi.yaml`, `x-status: planned`).
- FR-057 absorbed-undercharge record: backend T135 (new migration), frontend
  T136 (planned routes in the same contract).
- FR-045a expiry fix in `TicketChangeService.requestExpiry` and FR-044 24-hour window fix (`TicketChangeService`, `GroupTicketChangeService`): T130.
- Follow-up tasks of the same date: T141 (FE send `fromSessionReferences`), T142 (verify the Mland-cancellation voucher rule), T143 (acceptance scenarios, spec User Stories 11-13).
- FR-040 refund-account key replacement as an audited operational step: T137.
- C2 cash-close naming (`branchId`/`businessDay` → `locationId`/`workingDate`
  in every route, parameter and schema): backend T131, frontend T132.
- SC-015 WCAG 2.2 AA and browser verification of every 003.3 screen: T138.
- Removal of the unused Mland-cancellation refund policy (FR-043, FR-021a–e superseded 2026-11-15): T139 (BE), T140 (FE).
- Recorded as done: VNPay Refund removal (T128) and the migration renumber
  incident with the rule never to edit or renumber an applied migration
  (T129).

### Checklist follow-up of 2026-11-15 (`checklists/payment-error-access.md`)

Decided by Hồ Hoàng Quốc on 2026-11-15 (spec "Checklist follow-up"); tracked in
`tasks.md` Phase 11 (T144–T172). Tables, columns, routes and codes named below
are **proposed** until approved (`ppswbs-spec/AGENT.md` §3); the decisions
themselves are final in the spec.

| Topic (spec) | Design | Tasks |
| --- | --- | --- |
| (a) Cash reversal entry (FR-036, FR-053, CHK014) | Manager-only `POST …/folio/collections/{collectionId}/cash-reversals` (proposed), reason and `Idempotency-Key`; writes a row in the proposed table `settlement_cash_reversals` (negative `amount_vnd`, `cash_business_day` = current Vietnam working day, `collection_id` unique). The original collection and the closed `settlement_cash_day_closes` row are never edited. `FolioBalanceService` subtracts the reversal; `CashDayCloseService` expected cash adds a day's reversals. Refused for a `FINALIZED`/`CLOSED` folio, for money under a refund request, and for a day not yet closed (use the FR-053 void). Whole-collection reversal only is proposed. | T144, T145 |
| (b) "Next day" cut-off (FR-036, CHK013) | The cut-off is the submission of the counted amount, not the Manager's confirmation; any cash (and any reversal) recorded after it gets `cash_business_day` = next working day while the close is `PENDING_MANAGER_CONFIRMATION`. Closer = active Staff or Manager whose location scope contains the branch, no session-assignment rule (CHK024). | T147, T160 |
| (c) Auto-`FAILED` of an unreconciled VNPay collection (FR-034, CHK016) | `FolioGatewayReconciliationScheduler` marks a `PENDING_VERIFICATION` `VNPAY` part `FAILED` as `SYSTEM` once 24 h have passed since the attempt was created and QueryDR reports no successful payment (an unavailable or inconclusive QueryDR is retried). *(Supersedes the state-classification text "every 5 min for at most 24 h after the checkout expires": the 24 h run from creation, T109 to be aligned.)* Money signed later follows the T110 excess path to an `EXCESS_OR_DUPLICATE_MONEY` refund. End-to-end proof needs the QueryDR sandbox (T005, T035); until then only a fake-transport test. | T149 |
| (d) Refund failure → `PENDING_ACCOUNT` (FR-039, FR-040, CHK005/006/021) | One service operation in `BankRefundService` (proposed transition `RETURN_TO_PENDING_ACCOUNT`, Manager, reason) that, in one transaction, records the reason, deletes the ciphertext (mask kept), voids the approval, revokes the old link and sends a new one. A bank return, a statement mismatch found at `PENDING_RECONCILIATION`, or a Manager refusal all use it; no terminal rejection state exists. The key replacement of T137 uses the same rule: per-request transaction, approval void, idempotent rerun (touches only requests still holding ciphertext). | T146, T137 |
| (e) Account entry confirmation (FR-040, CHK005/006) | The customer link page and the counter form show bank, number and holder name back and require an explicit confirmation (proposed request flag `accountDetailsConfirmed`, `422 ACCOUNT_CONFIRMATION_REQUIRED` when false). No bank-name lookup in V1. The transfer-time holder-name comparison is removed. | T150, T151 |
| (f) Counter-entry authority (FR-040, CHK025) | Counter entry: Manager, or active Staff whose location scope contains the ticket's location and who is assigned to a session the ticket holds (the FR-032 rule, `requireManagerOrAssignedStaff`); a group-level `LATE_PAYMENT_NOT_RECONFIRMED` request is never entered at the counter. | T152 |
| (g) Voucher restoration (FR-042, FR-043, CHK011/034) | 003.3 triggers once through the shared facade (`workshop-interfeature-facades.md`, "Voucher and points restoration"); the voucher-and-points owner restores only while the voucher is valid, idempotent per `groupCode`, reason `EXPIRED` otherwise; 003.3 stores the returned outcome and tells the buyer; the refund by bank is unaffected. Extends T142. *(Decided 2026-11-15, CHK010, Hồ Hoàng Quốc: the trigger fires when the Mland-cancelled ticket's refund request is opened, not at `REFUNDED`; a moved ticket never triggers it.)* | T155 |
| (h) FR-057 cap (CHK009) | Service-level check under the folio lock: record ≤ folio total and Σ `ACTIVE` records of the ticket ≤ folio total, `422`. Extends T135. | T156 |
| (i) `FINALIZED` → `CLOSED` only by Mland-cancel refund (FR-043, CHK004) | The only transition out of `FINALIZED`; every other event leaves it unchanged. It closes when the refund is `REFUNDED` and nothing is pending *(clarified 2026-11-15, O-2 decided; supersedes "when the refund request is opened")*; applies to any session reaching `CANCELLATION_REQUESTED`, including an `IN_PROGRESS` interruption. | T157 |
| (j) Pending collection does not block Mland cancel (FR-034, CHK003) | The cancellation opens the refund; a Manager confirms or fails the pending part; a later confirmation is routed to the excess refund; the folio closes only when nothing is pending. | T158 |
| (k) Token scrub (FR-012a, CHK030) | The account-link token in the stored notification payload is cleared when the link is used, revoked or expired; the rest of the payload keeps the 30-day retention. | T159 |
| (l) Closer and role tests (FR-036, SC-016, SC-017) | Closer authority test; Staff-`403` contract test for every Manager-only operation; reminders/close/reopen test. | T160, T161, T162 |
| (m) Refund cap across reasons (FR-038, CHK002) | A new ticket-based request takes collected for the ticket (excess kept on the folio included) minus all its other `REFUNDED`/open requests, computed and inserted under the same ticket folio lock as FR-034; remainder 0 returns the existing request; the Mland-cancellation path (FR-043) uses the same remainder. | T170 |
| (n) Account link idempotency (FR-022a, CHK019, decided by Hồ Hoàng Quốc) | The anonymous route takes `Idempotency-Key`; a record scoped to the token hash with a keyed fingerprint of the request (never the account), removed 30 days after link expiry; same key and content replays `204`, changed content `409`. Table `settlement_refund_account_link_idempotency` (proposed, see `data-model.md`; columns `token_hash`, `idempotency_key`, `request_fingerprint`, `outcome`, `created_at`, `purge_after`; unique (`token_hash`, `idempotency_key`)). | T169 |
| (o) Status codes (FR-022d, CHK018) | `401` on every operation accepting `bearerAuth` (invalid token only on guest-or-Member routes); receipt-image `413`/`415`/`503` and lookup `429` are the documented extras. | T171 |

**Open decisions (settle in this plan before the dependent task is built):**

- **O-1 Optional receipt image on the refund transfer proof (FR-039, CHK007).**
  Decided in the spec: the bank reference (text) stays required, the image is
  optional, visible only to a Manager and the transferring actor, and follows
  the request lifecycle. **Not decided: file types, size limit, storage
  location (database / file store / object store), retention period, malware
  or content checks, and the upload mechanism.** No number is proposed here;
  the owner (Hồ Hoàng Quốc) and the platform storage facts must settle them
  first (task T153 records the decision, then T154 builds BE/FE). Until then the
  contract field `proofImageReference` is a placeholder (`x-status:
  pending-plan-decision`) and sensitive-data rules D13 (minimisation, access,
  retention) are unmet for the image.
  *(The paragraph above is kept for history; superseded by the decision below.)*
  **DECIDED 2026-11-15 by Hồ Hoàng Quốc (spec FR-039, Checklist follow-up;
  CHK007).** The bank reference text stays required, the image stays optional.
  Names of tables, routes, properties and codes below are **proposed**.
  1. *File types*: JPEG, PNG, WebP only, validated as feature 002 product images
     (002 spec FR-008; backend `ImageContentInspector`): the declared
     Content-Type, the file signature (magic bytes) and a readable image header
     must all agree; the file name extension is never trusted. PDF is refused
     (staff take a screenshot). Refusal: `415` (proposed code
     `RECEIPT_IMAGE_TYPE_UNSUPPORTED`).
  2. *Size and count*: at most 5,000,000 bytes (`413`, proposed
     `RECEIPT_IMAGE_TOO_LARGE`; the global multipart limit in
     `application.properties` is already 5,000,000 B per file with lazy
     resolution, so 003.3 needs its own typed `413` handler) and at most one
     image per refund transfer evidence (`409`, proposed
     `RECEIPT_IMAGE_ALREADY_ATTACHED`).
  3. *Storage and access*: Cloudinary, `resource_type=image`,
     `type=authenticated`, through a 003.3-owned adapter (a
     `SettlementReceiptStoragePort` in the settlement module, proposed),
     because the 002 module exposes no upload facade; it follows the pattern of
     `CloudinaryMediaStorageAdapter` without calling it. Same Cloudinary account
     and variables `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`,
     `CLOUDINARY_API_SECRET`; separate folder from its own property (proposed
     `SETTLEMENT_RECEIPT_CLOUDINARY_FOLDER`, default
     `ppswbs/settlement-receipts`), not `CLOUDINARY_FOLDER`, which 002 uses
     with default `ppswbs/catalogue`. MySQL stores metadata only (proposed table
     `settlement_refund_receipt_images`, see `data-model.md`), never the bytes.
     Mechanism: two steps — upload (`POST
     /workshop-refund-requests/{refundId}/receipt-images`, multipart,
     `Idempotency-Key`, allowed while the request is `PENDING_TRANSFER`), then
     `RECORD_TRANSFER` carries the returned `proofImageReference` and attaches
     it in the same transaction as the transfer record. The provider URL is
     never returned: `GET /workshop-refund-requests/{refundId}/receipt-image`
     checks access first, then the backend reads the object with a signed URL
     and streams it same-origin with `Cache-Control: no-store` and
     `X-Content-Type-Options: nosniff`. Access = the actors who may act on the
     refund transfer (FR-039: a Manager and the transferring actor). In V1
     every refund transition is Manager-only (FR-039, owner decision
     2026-11-13: the Manager holds `workshop.refund.execute`), so upload and
     stream are Manager-only; Staff, who may only enter the account at the
     counter and resend the link (FR-040, FR-038a, contract `…/account`,
     `…/account-link-resends`), get `403`. Missing credentials or an
     unreachable provider give `503` (proposed
     `RECEIPT_STORAGE_UNAVAILABLE`, like 002 `MEDIA_DEPENDENCY_UNAVAILABLE`);
     `RECORD_TRANSFER` with the bank reference and no image still succeeds.
  4. *Retention* *(fix A16, 2026-11-16: replaces "V1 never deletes an attached image automatically")*: an ATTACHED image is not deleted before 5 years after
     the request reaches `REFUNDED` or `CLOSED_UNREACHABLE` (job T166, after V1);
     until then it follows the refund record (including after `RETURN_TO_PENDING_ACCOUNT`,
     where it stays with the earlier transfer evidence). Only an image uploaded but never attached
     is removed after 24 hours by a scheduled cleanup, same rule as 002
     (`CatalogueMediaCleanupService.NEVER_ATTACHED_GRACE`).
  ~~**Still open (follow-up, not a number):** the final retention/deletion period
  of attached receipt images is deferred to an accounting decision; owner
  Hồ Hoàng Quốc. Until then D13 retention for the image reads "kept with the
  refund record".~~ *(superseded 2026-11-15)*
  **Retention decided 2026-11-15 (Hồ Hoàng Quốc):** an attached image file is
  deleted 5 years after its refund request reaches `REFUNDED` or
  `CLOSED_UNREACHABLE` (signed destroy, row → `DELETED`); the row keeps the
  bank reference snapshot, checksum, content type, size, uploader and
  timestamps. Basis: supporting evidence, not the accounting voucher (the bank
  statement is kept by accounting); 5 years = minimum for records not used
  directly for bookkeeping (Luật Kế toán 2015 / Nghị định 174/2016), to be
  confirmed by accounting; if accounting uses the in-app image as its voucher
  the period becomes 10 years. V1 builds no deletion job (first image due
  about 2031); T154 only keeps the terminal timestamp needed to compute the
  due date; the job is T166 (after V1). Tasks: T153 (decision, done
  2026-11-15), T154 (build), T166 (deletion job).
- **O-2 Folio close timing for a Mland cancellation of a `FINALIZED` folio.**
  The spec says the refund request is opened and the folio moves to `CLOSED`;
  T113 (done) closes a folio only when the remedy's refund is `REFUNDED`. The
  owner confirms which applies to an `OPEN` folio and whether `FINALIZED` closes
  at opening (T157).
  **DECIDED 2026-11-15 (spec FR-043, FR-039):** a folio, `OPEN` or `FINALIZED`,
  moves to `CLOSED` only once the Mland-cancel refund is `REFUNDED` and nothing
  is pending, consistent with done task T113; FR-043 applies to any session
  reaching `CANCELLATION_REQUESTED`, including an `IN_PROGRESS` interruption.
  The question above is kept for history; T157 needs no further owner answer.

### Approval exception and step-by-step workflows (A15, decided 2026-11-16)

**Exception (option B, Hồ Hoàng Quốc):** the owner approved the part already built; there is no `approval.md` and no separate design pack for 003.3. This plan, the spec Clarifications and the tasks are the approval record. The workflows below are short step lists for four tasks.

- **T137 key replacement:** (1) the Admin replaces `WORKSHOP_REFUND_ACCOUNT_KEY` and restarts the service; (2) the Admin calls the route with a reason and an Idempotency-Key; (3) for each request still holding ciphertext, in its own transaction: delete the ciphertext (mask kept), set `PENDING_ACCOUNT`, void the approval, revoke the link, send a new link, write one audit record; (4) a rerun touches nothing already handled; (5) payers re-enter numbers and a Manager approves again. The Admin runs the technical step only; approvals stay with Managers.
- **T146 return to `PENDING_ACCOUNT`:** (1) a bank return, a statement mismatch or a Manager refusal calls one service operation; (2) in one transaction record the reason, delete the ciphertext (mask kept), void the approval, revoke the old link and send a new one; (3) the refund right is unchanged and there is no terminal rejection state; (4) a repeated call changes nothing. `REFUNDED` is final in V1 (a late bank return after it is handled outside the system).
- **T167 handoff to 003.2:** (1) after each per-ticket resolution (replacement accepted, refund request opened, or a ticket with no collected money whose answer or default is recorded and whose folio closes); (2) in the same transaction check whether every affected ticket of the `SettlementCancellationCase` is resolved; (3) if so call `completeCancellation` with a deterministic `operationReference` per case; (4) a `REJECTED` outcome is audited and retried; (5) a handoff with no affected ticket completes on receipt.
- **T170 refund cap:** (1) take the ticket folio lock; (2) compute collected (excess kept on the folio included) minus the sum of the ticket's other requests that are `REFUNDED` or open (`CLOSED_UNREACHABLE` counts as open); (3) a remainder of 0 creates nothing and returns the existing request; (4) otherwise insert the request for the remainder under the same lock; (5) the Mland-cancellation path uses the same remainder.

## Public REST Design

The contract is the normative transport detail in
[`contracts/workshop-settlement.openapi.yaml`](./contracts/workshop-settlement.openapi.yaml)
for the routes still served booking-wide (OTA add-on bills, refund policies
*(refund policies removed by T139, 2026-11-15; FR-043, FR-021a–FR-021e superseded)*
and settlement case list; the booking-wide final-bill, charges and folio-correction
routes were removed 2026-11-15, T106/T121/T123/T124) and
[`contracts/workshop-settlement-ticket.openapi.yaml`](./contracts/workshop-settlement-ticket.openapi.yaml)
for every per-ticket route (see "Per-ticket settlement" above).

| Resource group | Public route family | Authority / proof |
| --- | --- | --- |
| Final bill | `/api/v1/workshop-bookings/{bookingCode}/final-bill` | Member ownership or Guest contact proof for read-only projection; Staff/Manager for settlement actions. *(Removed 2026-11-15, T106/T121/T123/T124; replaced by the per-ticket folio routes.)* |
| Charges | `/final-bill/charges` | Staff in booking-location scope or any active Manager globally; customer routes cannot create charge lines. *(Removed 2026-11-15, T106/T121/T123/T124; replaced by the per-ticket folio routes.)* |
| Settlement cases | `/.../{case-collection}` and `/{caseReference}` | Customer cancellation/read by ownership/proof; Manager for required-session change and Mland remedy. *(Booking-scoped case collections removed 2026-11-15, T106/T123/T124; replaced by the per-ticket cancellation, change and Mland-remedy routes. The Manager list `GET /workshop-settlement-cases` is still served.)* |
| Refund policy | `/api/v1/workshop-refund-policies` | Any active Manager, global scope. *(Removed by T139 (backend) and T140 (frontend), decided 2026-11-15: FR-043 always refunds 100%; FR-021a–FR-021e superseded; T139 will mark the routes in the contract removed.)* |
| Per-ticket folio and collections | `/api/v1/workshop-tickets/{ticketCode}/folio`, `…/folio/collections`, `…/folio/lines/{lineId}/voids`, `…/folio/collections/{collectionId}/voids`, `…/folio/corrections/{correctionReference}`, `…/folio-lookups` | Member owner, Manager or assigned Staff; Guest by one contact proof in the body (FR-048). |
| Per-ticket cancellation, change and Mland remedy | `/workshop-tickets/{ticketCode}/cancellations`, `…/changes`, `…/change-allowances`, `…/mland-remedy`, `…/mland-remedy/answers` | Buyer proof (Member bearer or `buyerVerificationReference`); Manager for allowances. |
| Refund requests and account | `/workshop-refund-requests`, `…/{refundId}/transitions`, `…/account`, `…/account-link-resends`, `/refund-account-links/{token}` | Manager (every transition in V1); Staff counter entry per FR-040; anonymous single-use link. |
| Reconciliation list *(planned, T133)* | `GET /workshop-reconciliation-collections` (methods `BANK_TRANSFER`, `VNPAY`, `BOOKING_TIME`; `BOOKING_TIME` depends on 003.1 T150) | Manager only (FR-056). |
| Absorbed undercharge *(planned, T135)* | `POST …/folio/absorbed-undercharges`, `…/{underchargeReference}/voids`, `GET /workshop-absorbed-undercharges` | Manager only (FR-057). |
| Refund-account key replacement *(planned, T137)* | `POST /workshop-refund-account-key-replacements` | Admin only (FR-040); Manager and Staff get `403`. |
| Refund receipt images *(planned, T154)* | `POST /workshop-refund-requests/{refundId}/receipt-images`, `GET …/receipt-image` | Manager in V1 (FR-039, plan O-1). |
| Cash reversal *(planned, T144)* | `POST …/folio/collections/{collectionId}/cash-reversals` | Manager only (FR-036). |
| VNPay ingress | Shared integration contract | `paymentintegration` owns the signed GET; this feature never owns the provider route. |

All client write routes declare `Idempotency-Key`, and all responses use a safe
projection or typed, correlation-linked `Problem`. The contract maps malformed
input to `400`, missing/invalid authentication to `401`, authority denial to
`403`, absent/not-visible resource to `404`, conflict/idempotency misuse to
`409`, and business ineligibility to `422`.

## State and Data Design

`data-model.md` describes conceptual records except for the approved
customer-cancellation/refund persistence contract: append a Flyway migration
with dedicated cancellation-snapshot and refund-allocation tables; allow one
customer-cancellation case per booking using a unique booking reference; link
each snapshot to its settlement case; enforce one allocation per case plus
original merchant payment reference and a unique provider operation reference.
*(Superseded for direct Mland money 2026-11-12 by "Per-ticket settlement"
above (spec FR-038–FR-040, T098/T104): the VNPay refund allocation dispatch,
the immediate and every-five-minutes-for-24-hours refund QueryDR and the
Merchant Admin investigation below no longer run for Mland money; an
allocation is routed to a bank refund request (`PENDING_BANK_REFUND`) and is
verified only by the Manager's confirmed transfer. Only an unreconciled legacy
gateway refund (code `99`) is still reconciled first. The original text is
kept for history.)* *(Superseded 2026-11-15 by Hồ Hoàng Quốc: the code-`99`
sentence is withdrawn. VNPay Refund and refund-QueryDR code was removed
(bc39429c) and V155 dropped the VNPay refund columns and the
`ix_settlement_refund_polling` index of `settlement_refund_allocations`
(4e2875a0); no such refund exists in any local database, spec FR-039.)*
*(Superseded 2026-11-15, T128: no VNPay Refund or refund-QueryDR exists any more; the next paragraph is history.)*
Persist all allocations before dispatch, call paymentintegration only after the
Settlement transaction commits, and use its operation journal as the provider
idempotency boundary. QueryDR runs immediately after a refund is accepted and
every five minutes for up to 24 hours when the refund transaction number is
known. Unknown outcomes without a refund transaction number remain pending for
manual Merchant Admin investigation; the application never marks them complete
without signed matching QueryDR evidence and never resends a refund. No test/live
database reset is permitted. Other
records retain only the schema decisions explicitly represented by existing
migrations. A React client holds only response state and must recover by
re-reading the safe resource/case projection.

## Validation and Rollout

- Contract-test every documented route, proof/authorization alternative,
  `Idempotency-Key` replay, typed error and safe projection.
- Test valid, duplicate, mismatched, unsigned, late and out-of-order VNPay
  evidence; a browser Return URL must never settle state.
- Test facade-only module boundaries with `ApplicationModules.verify()` and
  contract tests for all-or-none replacement/refund consequence.
- Test Guest/Member proof, location authority, inactive/out-of-scope denial and
  no-side-effect failure paths.
- Test preservation migration on an upgrade copy plus recovery/rollback evidence
  before data-bearing deployment. Do not reset staging/production data.
- Test concurrent cancellation requests with different idempotency keys for the
  same booking; the unique booking claim must prevent a second case/allocation.
  *(Superseded 2026-11-12/15: cancellation is per ticket (FR-041); the
  per-ticket idempotency and all-or-none group tests of T099/T103 replace
  this booking test.)*

## Readiness and Dependencies

| Dependency | Status | Planning consequence |
| --- | --- | --- |
| 003.1 confirmed booking projection and `FULLY_PAID` conclusion | Concrete facade contract | 003.3 consumes only the typed owner boundary. |
| 003.2 readiness, replacement, refund consequence and cancellation handoff | Concrete facade contract | 003.3 consumes only typed owner outcomes. |
| FE-001 account facts/scope | Approved dependency assumption | Implementation consumes canonical role/status, ownership and location scope; Settlement owns its action rules and does not edit FE-001 or use display roles/claims as authority. |
| VNPay merchant credential, configured callback URL, QueryDR access for payments *(refund access no longer needed: no VNPay Refund, FR-038, 2026-11-15)* | External configuration/evidence blocker (T005) | Protocol and fake-backed adapter can be implemented locally. Live requests remain disabled until the shared [VNPay configuration preflight](../contracts/vnpay-configuration-preflight.md) is satisfied. |
| Settlement-to-007 notification handoff | Defined cross-feature boundary | Durable handoff, reference-only event and payload facade are fixed in the shared facade contract. Recipient/content are retained up to 30 days; payment-action links are not part of settlement notifications. Feature 007 provider configuration remains external; Settlement commits independently. |
| Shared VNPay ingress/dispatch | Defined cross-feature boundary | Canonical route and dispatch rules live in `../contracts/vnpay-payment-ingress.openapi.yaml` and the facade contract; merchant credentials/configuration remain blocked. |
| 003.1 T150, read-only facade "list booking-time payments by day and location" (R11) | Not done | Blocks T133 and T134 (BOOKING_TIME rows); the rest of FR-056 can be built without it. |
| 006 T110, voucher restoration operation | Not done | Real call of T155 and T163 needs it; T155 tests use a fake facade. |
| 003.2 `completeCancellation` and T122 | Not done (no 003.3 service calls it) | T167 calls it; 003.2 T122 verifies the transition afterwards. |

## Changes across locations (Phase 14, proposed 2026-11-17)

**Spec:** "Changes across locations", FR-058–FR-063, SC-021–SC-023, Q1–Q7 decided or withdrawn
(Clarifications "Session 2026-11-17"). **Source:** 003.2 spec FR-037–FR-046, 003.2 plan
Phase 18 "Cross-feature impact", 003.2 `design/architecture.md` and `design/workflows.md`
(WF-06, WF-07, WF-08) and `../contracts/workshop-interfeature-facades.md`. **Status:
DRAFT/PROPOSED.** 003.3 is an *exclusion* in the 003.2 `approval.md` (partial approval of
2026-11-17); this plan is the 003.3 side that approval asks for and is not approved yet.
Tasks: Phase 14, T182–T193 in `tasks.md`. Code written in this phase is in 003.3 only; the
003.2 changes are 003.2 T144–T162, the 003.1 change of `getTicketFolioContext` is a 003.1
task.

**Boundary (Modulith):** 003.3 reaches Operations only through
`WorkshopOperationsFacade` (`activeLocationsForPackage`, `listTicketChangeOptions`,
`replaceTicketSessions`, `findEquivalentReplacementSessions`, new reason
`PACKAGE_NOT_AVAILABLE_AT_LOCATION`) and Booking only through `WorkshopBookingFacade`
(`getTicketFolioContext`). No new module dependency and no cycle; 003.3 stores no
availability and never filters candidates by location. `ModulithArchitectureTests` stays
the check (T193).

**Design (proposed):**

1. **Options.** `TicketChangeOptionsApiService.build` already maps 003.2's
   `TicketChangeOptions` to `ChangeOptionDto`; once 003.2 T154 drops the old-location pin
   the list widens by itself. 003.3 adds `locationName` to `ChangeOptionDto` and to the pair
   members (`SessionSeatsDto`), and the OpenAPI schemas `ChangeOption`/`SessionSeats`
   (FR-058). `remainingCapacity` and `remainingRescheduleQuota` are already returned
   (FR-063); no new field.
2. **Cross-location change (decided 2026-11-17, "option 2").** No Manager rule and no new refusal code. `TicketChangeService` and the option rows treat a target at another location like any other target: the Staff scope rule (`authorization.requireManagerOrAssignedStaff`, ticket current location in scope and an assignment) stays the only Staff check, and availability and the one-location rule come from 003.2. What 003.3 adds is an audit record when the target location differs from `TicketFolioContext.locationReference` (actor, from, to, time). Checked 2026-11-17: `SettlementBusinessAudit` suffices with no schema change: `aggregate_type` `TICKET_CHANGE`, `aggregate_reference` the ticket code, `action_code` `TICKET_LOCATION_CHANGED`, `actor_reference`, `correlation_reference`, and `reason_reference` (500 characters) carrying `from=<locationId>;to=<locationId>`; `created_at` gives the time. **Divergence to clear first:** 003.2 FR-042 and its `approval.md` still say a Manager is needed, so 003.2 must be amended before implementation.
3. **Refusal map.** `replaceSessions` already returns any 003.2 refusal other than
   `RESCHEDULE_QUOTA_EXHAUSTED` as `409` with the refusal name; `PACKAGE_NOT_AVAILABLE_AT_LOCATION`
   and `LATE_ARRIVAL_WINDOW_EXPIRED` need only the contract (`responses` and error enum)
   and a test, not new code, plus the approval recheck of FR-059(d): the queue approval
   path (`TicketChangeService` approve, step "applied now") passes through the same
   `replaceSessions`, so the recheck is free; the test proves a refused approval leaves
   the request `PENDING` and consumes no allowance (to verify, not assumed).
4. **Window by date.** Replace the two `end + LATE_ARRIVAL_WINDOW_DAYS × 24 h` computations
   (`TicketChangeOptionsApiService.build` `lateDeadline`; `TicketChangeService` queue expiry)
   with one helper that returns the instant at which the 7th calendar day after the
   replaced session's end date ends in `Asia/Ho_Chi_Minh` (the start of day 8, exclusive).
   Same rule as 003.2 `TicketReplacementRules.checkLateArrivalWindow`, which is the
   authority; the helper must not be a second source of truth, so a test compares them on
   the day boundary. For an advance change the helper also bounds the option list
   (003.2 already omits targets past it).
5. **Money and location.** No new code is expected: `getTicketFolioContext` is read per
   request, so a collection, charge, add-on or final bill created after the move takes the
   new branch once 003.1 derives it from the current session. `branch_id` of existing
   collections is immutable. The risk is a place that copies the location earlier:
   `SettlementTicketChangeRequest.locationReference` (set when queued) and any list that
   joins through the booking line. T186 reviews each reader (queue visibility, cash close,
   FR-056 rows) and adds tests; it changes code only where a stale copy is found.
6. **Old-location add-ons (decided 2026-11-17, Q3/Q4).** No schema change: a read-only Manager route finds the ticket's `CATALOGUE_ADD_ON` charges, reads the catalogue add-on location through the 003.2 facade from `CATALOGUE-ADDON:<ref>`, and lists those that differ from the ticket's current location.
7. **Mland offer.** `TicketMlandRemedyService` already stores the facade's candidate list;
   it adds the location name to what is shown (stored in the offer or read at render; to
   decide in T188 with the offer table, no schema change if read from the facade at
   render). The answer re-asks the facade (unchanged), which now drops a candidate whose
   pair became `INACTIVE`.

**Not changed:** price, voucher scope rule (a cross-location move can change voucher scope;
the existing 006 scope check on the new session set stays and its price-increase
confirmation applies), refund and bank-refund branch handling, OTA tickets (not
supported by customer change), the 24-hour advance rule.

**Design challenge (2026-11-17):** (a) "Money collected keeps its branch" holds for
`settlement_folio_collections` but not for rows that read their branch at query time (FR-056
`EXCESS`, `BOOKING_TIME`): decided 2026-11-17 (Q5): keep the branch of receipt. (b) 003.2 and 003.3 compute the 7-day window separately
today and already disagree (calendar date vs 168 h); the same-rule test in T185 prevents
drift. (c) A stale queued request: the approval path must recheck availability, location
rule and window because the pair, the Manager and the date can all change after queuing.
(d) Staff of the old location lose access at the moment of the move; a Staff member with a
queued request loses sight of it if the queue is filtered by the stored location (T186).
(e) The 006 voucher scope check reads the location of the new sessions, so a move can raise
the price with a confirmation; 006 T110 (restoration) is a different path and is done by
Hồ Hoàng Quốc himself (delegated by Đặng Thị Khương), relevant again when T155-type
restoration code is touched, not here.

**Readiness:**

| Dependency | Status | Consequence |
| --- | --- | --- |
| 003.2 T149 (`PACKAGE_NOT_AVAILABLE_AT_LOCATION`), T151 (`locationName`), T153, T154, T155 | Open, in the backend session (003.2 Phase 18) | T183–T185, T188 compile and test against fakes first; real behaviour after them. |
| 003.1 task: `getTicketFolioContext` derives location from the current session | Open, in the 003.1 session (number not yet given) | T186 and SC-022 cannot pass for real before it. |
| 003.2 FR-042 and `approval.md` amended (no Manager for another location) | Open | Blocks T184 until 003.2 is amended. |
| 003.2 `approval.md` excludes 003.3 | Open | No approval for 003.3 yet: the whole phase stays `DRAFT/PROPOSED`, not ready for implementation. |

## Design Challenge Review

The main risk is pretending a complete REST contract makes payment integration
ready. It does not: without an approved VNPay merchant configuration and actual
callback/QueryDR access (refund access is no longer needed since 2026-11-15,
FR-038), provider adapter work cannot be verified. The
design therefore keeps that work explicitly blocked while retaining a testable
semantic contract. A second risk is letting delivery failure alter money/capacity;
post-commit delivery isolation avoids that. No new framework, queue, state store,
locking mechanism, role mapping, provider secret, migration version or database
schema is assumed.

## Next Gate

*(Superseded 2026-11-16, fix A30: current status. T001–T127 are done except T005 and T035 (blocked on VNPay merchant configuration); Phase 10 (T128–T143) and Phase 11 (T144–T172) are open, except T153 (done) and T148 (merged into T137); plan decisions O-1 and O-2 are decided; the tasks exist in `tasks.md`. The gate text below is kept as history.)* This design is ready for `$speckit-tasks`. The resulting task set must replace
legacy item/session-bill and browser-confirmation work, mark the provider/
notification configuration dependencies blocked, and implement the shared VNPay
ingress before either business facade consumes payment evidence.

**Re-check 2026-11-14 (re-approved the same day by Hồ Hoàng Quốc, owner Feature 003, in session):** the State and Data
Design, Technical Context and Constitution Check were annotated for the
per-ticket model (superseded VNPay refund dispatch and QueryDR, 10-minute
final-payment checkout and single-contract statements; new rows for the
encrypted refund account, its key, the audited reveal and the anonymous link
route). These notes do not approve the plan; the open owner decisions listed
in spec (FR-022b1 `PENDING_PROVIDER`, FR-039 different recipient, the missing
operations after FR-045) remain open. *(Superseded 2026-11-15: all three were
decided by Hồ Hoàng Quốc on 2026-11-14 — `PENDING_PROVIDER` kept, meaning
"awaits verified refund evidence" (FR-022b1); the different-recipient
confirmation withdrawn (FR-039); the missing operations became the
Manager-only routes FR-052–FR-055, published by T123 and given screens by
T125.)*
