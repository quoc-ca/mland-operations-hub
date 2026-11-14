# Data Model: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-11-14
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

> Business ownership and invariants are normative. The approved cancellation
> persistence is recorded below; unrelated conceptual records do not define
> unlisted physical schema or locking behavior.

## Ownership Map

| Information | Owner | 003.3 constraint |
| --- | --- | --- |
| Confirmed booking, frozen financial projection, booking contact | 003.1 | Projection/authorization proof only; 003.3 never mutates Booking directly. |
| Sessions, capacity, ticket entitlements, attendance and cancellation state | 003.2 | Semantic facade outcomes only. |
| Canonical account role/status, ownership and location scope | FE-001 | Current account facts only. Settlement grants active Managers global authority for Manager actions; Staff actions compare the booking/session canonical `workshop_location.id` directly with `activeLocationIds`. |
| Final bill, charge, settlement evidence, case, policy snapshot and audit | 003.3 | Authoritative owner. |

## Conceptual Records

### Final Booking Bill

Exactly one final bill belongs to one direct booking/package. It retains the
booking reference; frozen post-promotion amount, accepted deposit and frozen
remaining amount supplied by 003.1; accepted charges; amount due; customer-safe
status; verified payment/refund conclusion; and redacted audit/correlation
references. It is not split by workshop session, participant, ticket or legacy
item and never recalculates voucher/point/deposit effects.

**Open-folio policy (2026-11-05)**: this record is the direct booking's one
OPEN folio. Required conceptual facts: immutable base line (frozen
post-promotion amount), accepted add-on/charge lines, append-only verified
collections starting with the 003.1 deposit, derived `paidAmount` and
`balanceDue = totalAmount − paidAmount`, and a lifecycle `OPEN` → `FINALIZED`
(checkout, `balanceDue = 0`) or `OPEN` → `CLOSED` at the verified collected
amount on ordinary cancellation/full no-show (no refund, no receivable), or
after every Mland-caused remedy refund allocation is verified (D11). Lines
and totals are immutable once `FINALIZED` or `CLOSED`. Per owner decisions
D1/D4/D5 (2026-11-05) the folio is created idempotently when Settlement
consumes `MlandBookingConfirmedEvent`, at most one exists per booking, and an
append-only Flyway backfill creates OPEN folios for existing confirmed direct
bookings. Physical columns, constraints and Flyway version are chosen during
003.3 T087.

**0 VND booking folio (2026-11-12, 003.1 FR-007a1)**: a confirmed direct
booking whose frozen post-promotion and accepted deposit amounts are both 0 gets
an OPEN folio with base 0, collected 0 and balance 0 without any verified
deposit payment. A paid booking without a verified deposit still gets no folio.
Add-ons, attendance and checkout then follow the normal OPEN-folio rules; a
zero balance finalizes internally.

**Loyalty processing marker (backfilled 2026-11-12, V97, ade3df0)**: nullable
`loyaltyProcessedAt` is set once the Loyalty outcome for the folio is decided
(earn granted, no-show final, or benefits restored), so a decided folio is not
rescanned. The Loyalty outcome scheduler is the single trigger and retry; no
Loyalty call runs inside the cancellation, remedy, finalize or attendance
transactions, and a Loyalty failure rolls back only the processor run for the
next retry.

### Folio Correction (T040, owner decisions D7–D10, 2026-11-05)

A separate immutable record linked to one `FINALIZED` direct folio. It covers
only an overcharge (an undercharge is absorbed by Mland and never recorded as
a collection). It holds the whole-VND refund amount, required Manager reason,
approving Manager reference, per-original-payment refund allocations (oldest
first, capped by each payment's unrefunded amount), their refund operations and
verified QueryDR conclusions, idempotency and audit references. It never changes the
folio's lines, totals or state. *(Superseded 2026-11-12 by spec FR-038/FR-050
and T098: "refund operations and verified QueryDR conclusions" are now the
linked bank refund request (Refund request record below) and its Manager
confirmation; no VNPay refund or QueryDR.)*

**Physical Schema & Decision (chosen 2026-11-05 during T040)**:
- **Decision (a)**: Reuse `SettlementCase` with case type `FOLIO_CORRECTION` and the 5-state lifecycle (`OPEN`, `PENDING_PROVIDER`, `RESOLVED`, `REJECTED`, `FAILED`), which is `SettlementCaseLifecycleStatus` in code (re-checked 2026-11-14).
- Dedicated snapshot table `settlement_folio_corrections` (`id`, `correction_reference`, `case_reference`, `booking_reference`, `bill_reference`, `amount_vnd`, `currency_code`, `reason`, `manager_actor_reference`, `created_at`, `updated_at`).
- Per-original-payment refund allocations are persisted in `settlement_refund_allocations` linked by `case_reference`, dispatched and reconciled through `WorkshopRefundService` without applying cancellation consequences (folio remains `FINALIZED`, booking remains `COMPLETED`, Loyalty earn and tickets untouched).
- Deprecated `settlement_direct_addon_bills` draft is retired and replaced by Flyway migration `V98__settlement_folio_corrections.sql`.
- **Current state (corrected 2026-11-14, re-check of the earlier 2026-11-14 alignment, which named the wrong enum):** the FR-022b1 case lifecycle in code is `SettlementCaseLifecycleStatus` (`OPEN`, `PENDING_PROVIDER`, `RESOLVED`, `REJECTED`, `FAILED`) on `SettlementCase.lifecycle_status`, so Decision (a) above stands. `SettlementCaseStatus` (`OPEN`, `RESOLVED`, `REJECTED`, `FAILED`) is a different enum: the status of the 003.2 session-cancellation handoff record `SettlementCancellationCase`, not used for corrections. `FolioCorrectionService` creates a `FOLIO_CORRECTION` case as `PENDING_PROVIDER`; since T098 (V123) its refund allocation goes to the bank refund flow and waits as `PENDING_BANK_REFUND` (allocation lifecycle) while the case stays `PENDING_PROVIDER`, and the Manager's confirmed transfer (`MlandBankRefundConfirmedEvent`) verifies the allocation and resolves the case (`RESOLVED`). No VNPay refund or QueryDR is used for Mland money. **Open (owner decision needed, spec FR-022b1):** `PENDING_PROVIDER` no longer means "awaiting gateway evidence" for Mland money; keep it as "awaiting verified refund evidence" or retire it. A ticket folio correction (T106, spec FR-050) uses the same record with `booking_reference` = the ticket code.
  - *(Superseded text of the earlier 2026-11-14 alignment, kept for history; wrong enum:)* the code's `SettlementCaseStatus` has four states (`OPEN`, `RESOLVED`, `REJECTED`, `FAILED`); there is no `PENDING_PROVIDER` case state. Since T098 (V123) a correction's refund allocation goes to the bank refund flow and waits as `PENDING_BANK_REFUND` (allocation lifecycle), the case stays `OPEN`, and the Manager's confirmed transfer resolves it.

### Charge Evidence

An immutable accepted charge records its final-bill context, source category,
VND amount/currency, acceptance/confirmation state and audit linkage. A customer
charge is only an allowed package/location catalogue add-on at its server-derived
catalogue snapshot price returned by 003.2 Operations for the exact published
package and active canonical location; Settlement retains the selected
description, whole-VND amount and currency as immutable charge evidence. A
variable material/service charge has internal description, amount, reason and
Staff/Manager confirmation. A correction is an audited void/adjustment record,
never an overwrite, and is allowed only while the folio is `OPEN`; after
`FINALIZED` only a Folio Correction (overcharge refund) applies. Legacy charges created before description retention may
have no customer-visible description.

**Operator confirmation evidence (backfilled 2026-11-12, V103, 9a5c877)**: each
catalogue add-on or variable charge stores `consentReference`
(server-generated `CONSENT-<id>`), `consentRecordedBy` (acting account) and
`consentRecordedAt`. It evidences the operator's statement that the customer
agreed, not the customer's own consent.

### Settlement Case

Every cancellation, required-session-change and Mland-caused
remedy has one opaque case reference, booking reference, type, lifecycle state,
reason, safe correlation reference and redacted audit linkage. States are
`OPEN`, `RESOLVED`, `REJECTED` or `FAILED`; only a gateway-waiting refund can be
`PENDING_PROVIDER`. There is no deferred-payment case or `EXPIRED` case state.
*(Re-checked 2026-11-14: in code this is `SettlementCaseLifecycleStatus`. Since
T098/T104 Mland-money cases wait for the bank refund, not the gateway, yet are
still created as `PENDING_PROVIDER`; **Open (owner decision needed, spec
FR-022b1):** keep or retire `PENDING_PROVIDER` for them.)*

A customer cancellation has at most one case per booking, including when the
case is rejected. Its dedicated snapshot is unique by booking and links to the
generic case by unique case reference. A new idempotency key cannot open a second
case or refund; same-key replay returns the retained safe outcome.

A required-session-change case retains only the complete target session-reference
set and the typed Operations outcome. An Mland-caused replacement/change and its
`UNAVAILABLE`/`DECLINED` remedy retain the same authoritative
`cancellationHandoffReference`. The case owns neither capacity, attendance,
ticket entitlement nor QR material.

### Payment Evidence and Output Handover

Settlement evidence retains only the safe provider transaction/reference,
matching outcome, correlation and idempotency conclusion required for
reconciliation. Signed, matched VNPay IPN/QueryDR is required; invalid,
mismatched, replayed, browser-return-only or late evidence cannot settle twice.
No raw signed payload, signature, secret, card data or browser token is retained.
Verified final-payment evidence also retains the provider transaction number and
the immutable PAY URL creation timestamp needed to identify/refund that exact
original payment. Booking supplies verified deposits through its named facade;
Settlement does not reach into Booking storage for payment rows.

Each refund allocation is an immutable child operation of its settlement case
and identifies one original payment, provider transaction number, original
provider create date, currency, allocated amount, operation reference and
provider lifecycle (`PREPARED`, `ACCEPTED`, `UNKNOWN`, `VERIFIED`, or
`REJECTED`). The operation is persisted before a provider call. `ACCEPTED` and
`UNKNOWN` do not resolve the case. Retries reuse the same operation reference;
an unknown provider outcome is reconciled before any new call. A case resolves
only when every nonzero child operation has verified refund evidence.

For VNPay, a refund response with `vnp_ResponseCode=00` is request acceptance
only. The provider refund transaction number returned by that signed response
identifies the refund allocation for QueryDR reconciliation. A refund is
`VERIFIED` only from a signed QueryDR response with API `vnp_ResponseCode=00`,
matching merchant reference and refund transaction number, matching refund
transaction type (`02` full or `03` partial), exact allocated amount, and
`vnp_TransactionStatus=00`. Transaction statuses `05` and `06` remain pending;
`09` is rejected; missing, mismatched, or otherwise nonterminal evidence remains
unknown/pending and cannot trigger ticket/capacity consequences. After a refund
request is accepted with its provider transaction number, Settlement queries
immediately and polls QueryDR every five minutes for at most 24 hours. `05` and
`06` stay pending; no refund request is resent. An `UNKNOWN` request with no
provider refund transaction number stays `PENDING_PROVIDER` for manual VNPay
Merchant Admin investigation. Manual investigation or a screenshot is not
verified payment evidence and cannot resolve the case; only signed matching
QueryDR evidence can do so.

The append-only cancellation migration adds
`settlement_customer_cancellation_snapshots` and
`settlement_refund_allocations`. The snapshot retains an opaque snapshot
reference, unique case reference, unique booking reference, policy revision and
percent, earliest session reference/start, verified paid-deposit total,
calculated refund total, VND currency, request time and eligibility outcome.
The allocation table retains an opaque allocation reference, case reference,
original merchant reference and provider transaction facts, original create
date, original and allocated whole-VND amounts, currency, stable provider
operation reference, optional refund transaction number, provider lifecycle and
redacted response/correlation facts. It has a unique key on case plus original
merchant reference and a unique provider operation reference. Payment
integration's operation journal is the provider-call idempotency boundary. An
allocation is committed before dispatch; provider calls occur after the local
transaction; `UNKNOWN` is reconciled and never automatically resent.

The final bill remains unpaid until signed, matching VNPay evidence confirms the
complete remaining balance. No role can waive collection, defer the balance,
create a payment reminder, or reissue a deferred-payment link. Package output is
withheld until the balance is verified as paid. The final-payment checkout
uses one immutable `FINAL_PAYMENT` owner per attempt in `paymentintegration`.
Settlement requests the exact server-derived outstanding amount in VND with an
idempotency reference; each provider URL expires 10 minutes after creation. A
pending or unknown attempt blocks another attempt. After expiry, a new attempt
is allowed only after QueryDR confirms that the previous attempt did not
succeed. A URL expiry alone never proves non-payment; late signed success and
ambiguous outcomes remain subject to verified ingress/QueryDR evidence. This is
a collection step during settlement, not a credit period.

### Settlement Notification Handoff

Each customer-facing verified settlement/refund outcome may create one durable
notification handoff in the same local transaction as that accepted Settlement
outcome. Its post-commit event carries references only. Feature 007 reads the
render-ready delivery snapshot through the Settlement facade and owns provider
attempts, retries and delivery logs. The stored recipient and rendered content
are retained for at most 30 days after handoff. Payment-action links are not
included. The delivery snapshot is immutable until its 30-day retention ends.
Raw provider evidence and ticket/QR material
do not appear in the event or business audit. Delivery failure cannot reverse
the Settlement outcome.

The physical owner table is `settlement_notification_handoffs`: a unique
`notification_reference`, settlement and correlation references, notification
kind, recipient, rendered content, payload expiry, creation time, and nullable
event-dispatch time. Existing nullable action-link columns from the initial
append-only migration are legacy and are no longer mapped, written, or exposed.
Indexes support reference lookup, expiry cleanup, and retrying undispatched
reference events. Recipient/content are readable only through the named
Settlement delivery payload facade; the event dispatcher emits references only.

### Refund Policy Revision and Snapshot

A central policy revision has its future effective time and integral
`wholeBookingRefundPercent` in `0..100`. It applies only to bookings confirmed
after its effective time. Each confirmed booking retains an immutable applicable
policy snapshot and computes any Mland-caused refund as
`floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND. Existing
snapshots are never rewritten.

This policy is not consulted for ordinary customer cancellation. A direct
customer cancellation has a zero-refund snapshot, is handed to Operations for
whole-booking cancellation, and retains the deposit; no refund allocation or
VNPay QueryDR/refund operation is created. The policy remains for Mland-caused
remedy cases and other explicitly approved provider-remedy flows.

Booking publishes a durable `MlandBookingConfirmedEvent` containing only the
booking reference and committed confirmation timestamp in the same transaction
as the `CONFIRMED` transition. Settlement consumes it after commit and writes a
unique booking-time snapshot with the effective revision (or the explicit
initial 100% default). Duplicate delivery is idempotent; a different timestamp
for an already-snapshotted booking is rejected for reconciliation. A remedy
command fails closed while the durable confirmation snapshot is unavailable; it
never substitutes the latest policy at remedy time.

### Authorization, Audit and Idempotency Evidence

Protected command evidence records the actor reference when applicable, booking,
canonical location when applicable, action, reason, outcome, correlation reference and time.
Finalization never has a role-based unpaid-balance exception. Active Manager
authority is global across Feature 003, while Staff checks the booking's
canonical location scope. Business audit records only accepted or denied
commands and never records a waiver because no waiver operation exists.
It excludes contact proof, raw Magic Link, QR, Firebase token, provider secret,
raw callback, card data, OTP and unnecessary PII.

For each client-originated mutation, retained idempotency evidence connects an
opaque operation/key, semantic request identity, original safe outcome and
correlation reference. The same key/content returns that outcome; changed reuse
is a conflict. VNPay replay protection instead uses the verified provider
transaction/reference.

### OTA Shop Add-on Bill Payment and Refund (backfilled 2026-11-12, V99/V104, 6e2ca7a)

A separate direct-Mland shop add-on bill of an OTA booking (003.4) has its own
payment and refund records, isolated from OTA payment/payout facts:

- **Add-on bill**: also stores nullable `mlandBookingReference`, the Mland
  booking that holds the tickets, so Staff authority follows assignment to that
  booking's sessions. A legacy bill without it stays Manager-only for Staff.
- **Add-on payment attempt**: `attemptReference`, `billReference`,
  `channelBookingReference`, `operationReference`, `merchantReference`,
  `amountVnd`, `currencyCode`, `providerCreateDate`, optional payment URL
  snapshot, `lifecycleStatus` (`PENDING`, `UNKNOWN`, `VERIFIED_PAID`,
  `VERIFIED_UNPAID`, `EXPIRED`), `createdAt`, `expiresAt`. A registered payment
  owner kind lets the shared VNPay ingress route the verified outcome here.
- **Add-on refund**: `refundReference`, `caseReference`, `billReference`,
  `channelBookingReference`, `amountVnd`, `currencyCode` (VND), `reason`,
  `managerActorReference`, `createdAt`, `updatedAt`. Manager-only.

### Per-ticket settlement records (decision 2026-11-12, spec FR-030–FR-051)

Planned, not yet implemented (tasks Phase 8). Each record belongs to one ticket
folio (003.1 ticket booking) unless stated.

| Record | Key data | Invariants |
| --- | --- | --- |
| Ticket folio | ticket booking reference; base = ticket final price; booking-time collection (0 for `FREE`/deferred); lines; derived `paidAmount`/`balanceDue`; `OPEN`/`FINALIZED`/`CLOSED`; `loyaltyProcessedAt` | One per ticket. Free/deferred folio opens without payment evidence. |
| Folio collection | method (`VNPAY`, `CASH`, `BANK_TRANSFER`, `BOOKING_TIME`); amount; status (`PENDING_VERIFICATION`, `CONFIRMED`, `FAILED`, `VOIDED`); reference (VNPay transaction, bank transaction, cash receipt); payer; confirmer; cash received/change; times | Only `CONFIRMED` counts. VNPay only from signed evidence; cash by collecting Staff; transfer by `workshop.payment.transfer.confirm`. A bank reference or cash confirmation is used once. VNPay collection ≥ 5,000. |
| Handover exception | folio; Manager; reason; time | Only a Manager may allow product handover before verification. |
| Debt record | folio; amount; Manager; reason; open/closed and closing reason | Manager-only; no automatic reminder, deadline or booking block. |
| Cash day close | branch; working date (Vietnam time); system cash total; counted amount; difference and reason; closer; Manager confirmation of a difference | One per branch per working date. Cash after the close belongs to the next date. A closed date stays locked: a Manager correction never edits its confirmed cash and records any cash effect as an adjustment on the current working date (decision 2026-11-14, spec FR-050). |
| Refund request | ticket folio (nullable) and group; source = `RefundReason` (`MLAND_CANCELLATION`, `LATE_PAYMENT_NOT_RECONFIRMED`, `OVERCHARGE_AFTER_FINALIZED`, `EXCESS_OR_DUPLICATE_MONEY`, `OTA_ADDON_OVERCHARGE`; names aligned to the code 2026-11-14); amount; recipient (payer by default; another recipient needs the payer's recorded confirmation); status (`PENDING_ACCOUNT`, `PENDING_APPROVAL`, `PENDING_TRANSFER`, `PENDING_RECONCILIATION`, `REFUNDED`, `CLOSED_UNREACHABLE`); approver; transferring actor; bank reference and proof; reconciling Manager; times | Σ refunded + pending per ticket ≤ money collected for that ticket. Deduplicated create/confirm. Consequences apply only at `REFUNDED`. A legacy VNPay refund with code `99` is reconciled first. |
| Refund account | refund request; bank; account number (encrypted, masked after `REFUNDED`); holder name; payer confirmation when the recipient is not the payer — **withdrawn 2026-11-14 (Hồ Hoàng Quốc): no declaration and no payer-confirmation step; the refund goes to the account the payer enters and the holder name is never compared** *(earlier wording "when names differ" superseded by spec FR-040, decision 2026-11-13: the holder name is not compared with the payer; **Withdrawn 2026-11-14 (Hồ Hoàng Quốc):** there is no different-recipient declaration and no payer-confirmation step; the refund goes to the account the payer enters and the holder name is never compared (FR-039/FR-040))*; channel (link/counter); entering actor; link token hash, expiry (7 days), revocation; reminder times (days 3, 7) | Full number visible only to the approving Manager and the transferring actor; never in email, logs or audit. |
| Ticket cancellation | ticket; group request reference (one request may cancel several tickets of one group, all or none, decision 2026-11-14); requester (buyer/Staff for buyer); channel; buyer proof (Member owner, or consumed `buyerVerificationReference` id; never the OTP); request time; first-session start; 24-hour outcome (`VALID_CANCELLATION`, `LATE_CANCELLATION`); points returned; voucher restoration reference | Idempotent per request key. A group request commits every listed ticket or none. Voucher use restored at most once per group, only when every ticket ended by valid or Mland cancellation. |
| Mland ticket remedy | ticket; cancelled session; replacement offered; buyer answer and deadline (24 h before replacement start); outcome (`MOVED`, `REFUND`); refund request reference | Not counted as a customer change. Voucher restored once for the group even when partial. |
| Ticket change | ticket; type (`ADVANCE`, `LATE_ARRIVAL`, `MLAND`, `MANAGER_GRANTED`); from/to sessions; counted (yes/no); price effect (voucher allocation lost, balance increase); buyer confirmation; actor | At most one counted customer change per ticket unless a Manager grants more. Price never decreases. |

Permissions used here: `workshop.payment.transfer.confirm` and
`workshop.refund.execute` (Manager by default, grantable per person, never per
shift; **V1, decisions 2026-11-12/13:** the codebase has roles only, so both are
held by a Manager and per-person grants wait for FE-001); approving refunds, confirming `REFUNDED`, handover exceptions, debt
records and cash-close differences are Manager-only.

## Integrity Rules

1. At most one final bill exists for one direct booking/package. *(Superseded
   for direct bookings 2026-11-12 by rule 12: one folio per ticket booking.)*
2. Frozen financial amounts from 003.1 are never recalculated by 003.3.
3. Customers do not create charge lines. A Staff/Manager-confirmed catalogue
   add-on never supplies its own price: the server snapshots the eligible
   package/location price. A direct add-on appends only to the OPEN direct
   folio; a `FINALIZED` folio rejects add-on add/change/remove (correction only
   through the exceptional T040 flow). Every OTA shop add-on uses a separate
   direct-Mland bill isolated from OTA facts; free OTA laser engraving creates
   no bill.
4. A charge creates no participant, ticket, capacity seat or booking.
5. Valid matched VNPay evidence resolves a final bill/refund exactly once.
   *(Superseded for refunds of direct Mland money 2026-11-12 by rule 13: no
   VNPay refund exists any more; the payment half still holds for VNPay folio
   collections.)*
6. Operations replacement is complete-set and all-or-none; 003.3 persists no
   partial capacity/entitlement outcome.
7. *(Superseded for direct bookings 2026-11-12/14 by rules 14 and 15:
   cancellation and Mland remedy are per ticket; kept for history.)*
   Cancellation scope remains the whole booking/package. A Mland-caused remedy
   policy may produce a partial monetary refund; that aggregate is calculated
   once and allocated oldest original payment first, capped by each original
   payment. No ticket/participant/session-scoped cancellation is introduced.
   An eligible direct customer cancellation always has a zero-refund snapshot
   and is resolved as `DIRECT_DEPOSIT_FORFEITED` after Operations accepts; it is
   not rejected for having a zero refund. Only an ineligible or
   Operations-rejected request is `REJECTED` and leaves booking, ticket and
   capacity state unchanged. The workshop product is handed over only after its
   workshop session; requiring every required session to remain unstarted
   already prevents cancellation after product handover, so no second
   output-release state is needed in the cancellation snapshot.
8. A Mland-caused remedy is traceable to the same cancellation handoff as its
   replacement attempt.
9. Policy revisions are prospective and cannot rewrite a confirmed booking's
   snapshot.
10. A client idempotency key cannot produce two settlement effects or be reused
    for different semantic input.
11. A notification handoff is immutable and reference-only; duplicate delivery
    events cannot create a second delivery request or alter settlement truth.

Per-ticket rules (spec FR-030–FR-051, decisions 2026-11-12 to 2026-11-14):

12. Exactly one folio exists per ticket booking (FR-030), keyed by the ticket
    code; a ticket's charges, collections, refunds, finalization and
    corrections belong to that folio only and never change another ticket.
13. Direct Mland money is refunded only through the bank refund request
    lifecycle (FR-038–FR-040): no VNPay Refund or refund-QueryDR call; a
    refund's business consequences apply only at `REFUNDED`; the total
    refunded or pending for a ticket never exceeds the money collected for it;
    a legacy VNPay refund with code `99` is reconciled before any manual
    refund of the same money.
14. The buyer cancels chosen tickets (FR-041): one ticket, or several tickets of
    one group in one request that commits every listed ticket or none; each
    ticket keeps its own 24-hour outcome (FR-042); a customer cancellation
    refunds nothing of the booking-time collection.
15. A Mland cancellation affects only tickets with the cancelled session
    (FR-043) and refunds 100% of each affected ticket's collected money by
    bank (rule 13); the group's voucher use is restored at most once.
