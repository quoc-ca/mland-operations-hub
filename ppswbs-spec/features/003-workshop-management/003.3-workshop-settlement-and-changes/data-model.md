# Data Model: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-11-16
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
(checkout, `balanceDue = 0`; sole exception: `FINALIZED` → `CLOSED` when a Mland-cancel refund is `REFUNDED`, FR-043) or `OPEN` → `CLOSED` at the verified collected
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
- **Current state (corrected 2026-11-14, re-check of the earlier 2026-11-14 alignment, which named the wrong enum):** the FR-022b1 case lifecycle in code is `SettlementCaseLifecycleStatus` (`OPEN`, `PENDING_PROVIDER`, `RESOLVED`, `REJECTED`, `FAILED`) on `SettlementCase.lifecycle_status`, so Decision (a) above stands. `SettlementCaseStatus` (`OPEN`, `RESOLVED`, `REJECTED`, `FAILED`) is a different enum: the status of the 003.2 session-cancellation handoff record `SettlementCancellationCase`, not used for corrections. `FolioCorrectionService` creates a `FOLIO_CORRECTION` case as `PENDING_PROVIDER`; since T098 (V123) its refund allocation goes to the bank refund flow and waits as `PENDING_BANK_REFUND` (allocation lifecycle) while the case stays `PENDING_PROVIDER`, and the Manager's confirmed transfer (`MlandBankRefundConfirmedEvent`) verifies the allocation and resolves the case (`RESOLVED`). No VNPay refund or QueryDR is used for Mland money. **Decided 2026-11-14 (Hồ Hoàng Quốc, spec FR-022b1; formerly "Open (owner decision needed)"):** `PENDING_PROVIDER` is kept and means "awaits verified refund evidence"; enum value, stored strings and behaviour are unchanged. A ticket folio correction (T106, spec FR-050) uses the same record with `booking_reference` = the ticket code.
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
`OPEN`, `RESOLVED`, `REJECTED` or `FAILED`; only a case that awaits verified
refund evidence can be `PENDING_PROVIDER` *(wording changed 2026-11-15 from
"a gateway-waiting refund")*. There is no deferred-payment case or `EXPIRED` case state.
*(Re-checked 2026-11-14: in code this is `SettlementCaseLifecycleStatus`. Since
T098/T104 Mland-money cases wait for the bank refund, not the gateway, yet are
still created as `PENDING_PROVIDER`; **Decided 2026-11-14 (Hồ Hoàng Quốc, spec
FR-022b1; formerly "Open (owner decision needed)"):** keep `PENDING_PROVIDER`
for them, meaning "awaits verified refund evidence".)*

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

**Refund allocation, current model (rewritten 2026-11-15; spec FR-038–FR-040,
commits bc39429c and 4e2875a0):** a refund allocation is a child row of its
settlement case (`MLAND_CANCELLATION_REMEDY`, `FOLIO_CORRECTION`,
`LATE_PAYMENT_REFUND`, `ADDON_REFUND`) in `settlement_refund_allocations`. It
names the original payment the money came from and is returned only by bank
transfer through a refund request (Refund request record below); no VNPay
Refund or refund-QueryDR call exists. Lifecycle in code: `PREPARED` (written
before dispatch) → `PENDING_BANK_REFUND` (handed to a bank refund request,
`bank_refund_reference` set) → `VERIFIED` (the Manager confirmed that request
`REFUNDED`, `MlandBankRefundConfirmedEvent`); a case resolves only when every
nonzero allocation is `VERIFIED`. The CHECK constraint `ck_settlement_refund_lifecycle`
(V125) still also admits the legacy values `ACCEPTED`, `UNKNOWN` and `REJECTED`,
which no code writes any more.

Current columns (V45 as altered by V123, V151 and V155): `id`,
`allocation_reference` (unique), `case_reference` (FK to `settlement_cases`),
`original_payment_reference`, `original_provider_transaction_number`
(nullable since V151), `original_provider_create_date` (nullable since V151),
`original_amount_vnd`, `allocated_amount_vnd`, `currency_code`,
`operation_reference` (unique), `lifecycle_status`, `bank_refund_reference`
(V123, indexed `idx_sra_bank_refund`), `created_at`, `updated_at`; unique
(`case_reference`, `original_payment_reference`). V155 dropped
`provider_refund_transaction_number`, `provider_response_code`,
`provider_transaction_type`, `provider_transaction_status`,
`provider_accepted_at`, `last_query_at` and the index
`ix_settlement_refund_polling`. The `original_*` columns stay because a bank
refund still points to the customer's original payment.

*(Superseded 2026-11-15 by the current model above; the two paragraphs below
describe the removed VNPay refund lifecycle (`ACCEPTED`/`UNKNOWN`, refund
transaction number, QueryDR polling every five minutes for 24 hours, Merchant
Admin investigation) and are kept for history only.)*

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
*(Allocation part superseded 2026-11-15: see "Refund allocation, current
model" above; the refund transaction number and provider response facts were
dropped by V155 and no provider refund call is made.)*

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

> **Superseded 2026-11-15 for tickets (spec FR-043; FR-021a–FR-021e).** A
> Mland cancellation of a ticket always refunds 100% of the money collected for
> it; the policy revision and the booking snapshot below are no longer
> consulted. Task T139 removes the code and drops the tables
> `settlement_refund_policy_revisions`, `settlement_refund_policy_snapshots`
> and `settlement_booking_mland_refund_policy_snapshots` (names as mapped by the
> entities) with a NEW migration (next free version at implementation time, checked against develop, T129), never by editing an applied one;
> T140 removes the frontend use. The text is kept for history.

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

Implemented (tasks T093–T125, 2026-11-15), except the absorbed undercharge
record (FR-057, proposed, T135) and the reconciliation list (FR-056, T133)
added 2026-11-15. Each record belongs to one ticket
folio (003.1 ticket booking) unless stated.

| Record | Key data | Invariants |
| --- | --- | --- |
| Ticket folio | ticket booking reference; base = ticket final price; booking-time collection (0 for `FREE`/deferred); lines; derived `paidAmount`/`balanceDue`; `OPEN`/`FINALIZED`/`CLOSED`; `loyaltyProcessedAt` | One per ticket. Free/deferred folio opens without payment evidence. |
| Folio collection | method (`VNPAY`, `CASH`, `BANK_TRANSFER`, `BOOKING_TIME`); *(R14, 2026-11-16: `BOOKING_TIME` on a folio collection is a per-ticket allocation, view only; the reconciliation list's `BOOKING_TIME` is the whole group payment, a different meaning.)* amount; status (`PENDING_VERIFICATION`, `CONFIRMED`, `FAILED`, `VOIDED`); reference (VNPay transaction, bank transaction, cash receipt); payer; confirmer; cash received/change; times | Only `CONFIRMED` counts. VNPay only from signed evidence; cash by collecting Staff; transfer by `workshop.payment.transfer.confirm`. A bank reference or cash confirmation is used once. VNPay collection ≥ 5,000. |
| Handover exception | folio; Manager; reason; time | Only a Manager may allow product handover before verification. |
| Debt record | folio; amount; Manager; reason; open/closed and closing reason | Manager-only; no automatic reminder, deadline or booking block. |
| Cash day close | branch; working date (Vietnam time); system cash total; counted amount; difference and reason; closer; Manager confirmation of a difference | One per branch per working date. Cash after the close belongs to the next date. A closed date stays locked: a Manager correction never edits its confirmed cash and records any cash effect as an adjustment on the current working date (decision 2026-11-14, spec FR-050). *(Superseded 2026-11-15, spec FR-036/FR-050: a correction refunds only by bank, has no cash effect, and no cash adjustment record exists. Partly superseded again later on 2026-11-15, CHK014: a cash collection recorded in error on a closed day while the folio is not `FINALIZED`/`CLOSED` is corrected by the Manager-only cash reversal entry below, dated the current working day; the closed day is still never edited.)* Table `settlement_cash_day_closes` stores the branch as `branch_id` and the date as `business_day`; the public contract names them `locationId` and `workingDate` (C2, decided 2026-11-15, tasks T131/T132). |
| Refund request | ticket folio (nullable) and group; source = `RefundReason` (`MLAND_CANCELLATION`, `LATE_PAYMENT_NOT_RECONFIRMED`, `OVERCHARGE_AFTER_FINALIZED`, `EXCESS_OR_DUPLICATE_MONEY`, `OTA_ADDON_OVERCHARGE`; names aligned to the code 2026-11-14); amount; recipient (payer by default; ~~another recipient needs the payer's recorded confirmation~~ *withdrawn 2026-11-14 by Hồ Hoàng Quốc, spec FR-039: no different-recipient step*); status (`PENDING_ACCOUNT`, `PENDING_APPROVAL`, `PENDING_TRANSFER`, `PENDING_RECONCILIATION`, `REFUNDED`, `CLOSED_UNREACHABLE`); approver; transferring actor; bank reference and proof; reconciling Manager; times | Σ refunded + pending per ticket ≤ money collected for that ticket, across every reason: a new ticket-based request takes collected minus the other requests that are `REFUNDED` or open (`CLOSED_UNREACHABLE` counts as open), computed and created under the ticket folio lock; remainder 0 creates nothing *(clarified 2026-11-15, CHK002, spec FR-038, T170)*; a group-level request (`LATE_PAYMENT_NOT_RECONFIRMED`, `EXCESS_OR_DUPLICATE_MONEY`) has no ticket, no cap and is entered only by the account link *(clarified 2026-11-15, CHK032)*; the voucher restoration of a Mland cancellation fires when the request is opened *(decided 2026-11-15, CHK010)*. Deduplicated create/confirm. Consequences apply only at `REFUNDED`, with one exception *(decided 2026-11-15, CHK004, spec FR-043)*: when Mland cancels a ticket whose folio is already `FINALIZED`, the refund request is opened ~~and the folio moves to `CLOSED`~~ and the folio moves to `CLOSED` only once that refund is `REFUNDED` and no collection of the ticket is still pending (the only transition out of `FINALIZED`; no other event reopens or closes a `FINALIZED` folio; task T157); a pending collection never blocks the cancellation (FR-034). *(Clarified 2026-11-15, owner confirmed option A (Hồ Hoàng Quốc), spec FR-043, plan (i)/O-2: the same `REFUNDED`-and-nothing-pending rule holds for an `OPEN` folio, consistent with done task T113; supersedes the earlier wording that the folio moves to `CLOSED` when the refund request is opened.)* ~~Open inconsistency (to confirm with the owner, T155): T113 closes an `OPEN` folio only when the remedy's refund reaches `REFUNDED`, while the spec text moves the `FINALIZED` folio to `CLOSED` when the request is opened; the data model keeps T113 for `OPEN` folios until the owner says otherwise.~~ *(Resolved 2026-11-15: owner confirmed option A, plan O-2 decided; the task is T157.)* The recorded proof is the bank reference (text, required) plus an optional receipt image (~~open decision, plan O-1~~ decided 2026-11-15, plan O-1; see the "Refund transfer receipt image" row). A failed transfer returns the request to `PENDING_ACCOUNT` (see the return row below). ~~A legacy VNPay refund with code `99` is reconciled first.~~ *(Withdrawn 2026-11-15 by Hồ Hoàng Quốc: VNPay Refund code removed, bc39429c/V155; spec FR-039.)* |
| Refund account | refund request; bank; account number (encrypted, masked after `REFUNDED`); holder name; payer confirmation when the recipient is not the payer — **withdrawn 2026-11-14 (Hồ Hoàng Quốc): no declaration and no payer-confirmation step; the refund goes to the account the payer enters and the holder name is never compared** *(earlier wording "when names differ" superseded by spec FR-040, decision 2026-11-13: the holder name is not compared with the payer; **Withdrawn 2026-11-14 (Hồ Hoàng Quốc):** there is no different-recipient declaration and no payer-confirmation step; the refund goes to the account the payer enters and the holder name is never compared (FR-039/FR-040))*; channel (link/counter); entering actor; link token hash, expiry (7 days), revocation; reminder times (days 3, 7) | Full number visible only to the approving Manager and the transferring actor; never in email, logs or audit. *(Decided 2026-11-15, CHK005/006/025: the entry shows bank, number and holder name back and needs an explicit confirmation; the earlier transfer-time holder-name comparison is superseded; a Staff counter entry needs an active Staff member assigned to a session of the ticket, or a Manager; a group-level request (late payment not re-confirmed, or excess or duplicate group money) is entered only by the account link; on a failed transfer or a key replacement the ciphertext is deleted and the mask kept.)* |
| Ticket cancellation | ticket; group request reference (one request may cancel several tickets of one group, all or none, decision 2026-11-14); requester (buyer/Staff for buyer); channel; buyer proof (Member owner, or consumed `buyerVerificationReference` id; never the OTP); request time; first-session start; 24-hour outcome (`VALID_CANCELLATION`, `LATE_CANCELLATION`); points returned; voucher restoration reference | Idempotent per request key. A group request commits every listed ticket or none. Voucher use restored at most once per group, only when every ticket ended by valid or Mland cancellation *(clarified 2026-11-15, spec FR-042/FR-043: a Mland cancellation restores it when its first ticket has its refund request opened, whatever the other tickets did)*. |
| Mland ticket remedy | ticket; cancelled session; replacement offered; buyer answer and deadline `answerDeadlineAt` (24 h before the LATEST offered session; a session is choosable only while it starts at least 24 h after the answer; aligned 2026-11-15 with spec FR-043); outcome (`MOVED`, `REFUND`); refund request reference | Not counted as a customer change. Voucher restored once for the group even when partial. |
| Mland remedy session options (V152, T112, BE-DESIGN-0008, decided 2026-11-14) | extends the Mland ticket remedy row: `replacement_options` (TEXT, nullable: the whole offered list of equivalent sessions with each start time) and `selected_replacement_session_reference` (VARCHAR(64), nullable: the session the buyer chose); `replacement_session_references` widens from 512 characters to TEXT | 003.2 returns ALL valid equivalent sessions (no cap); the buyer CHOOSES ONE and an accept answer must carry a reference from the offered list. A session is choosable only while it starts at least 24 h after the answer; the default refund fires at `answerDeadlineAt` = 24 h before the LATEST offered session. Rows written before V152 keep their values and are read from `replacement_session_references` and `replacement_start_at`. |
| Ticket change request (V153, T117, spec FR-045a, decided 2026-11-14) | table `settlement_ticket_change_requests`: `request_reference`, `idempotency_key`, `ticket_code`, `group_code`, `location_reference`, `change_type`, `target_session_references`, `from_session_references` (T130, planned; column nullable, existing `PENDING` requests keep their current expiry calculation, only new requests require it (R15); new migration, next free version at implementation time, checked against develop (T129; fix A35)), `blocked_rules`, `status` (`PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`), `price_increase_amount_vnd`, `buyer_confirmed_increase`, requester account/role/channel, `buyer_proof` (consumed when stored, kept for audit), `reason`, `expires_at`, decision reason/actor/time/`decision_idempotency_key`, `applied_change_reference`, `created_at`, `updated_at` | Stored instead of a refusal when an online change fails only on a rule a Manager may lift (quota, used allowance, 24 h window); the change route answers 202. `PENDING` moves to `APPROVED`, `REJECTED` or `EXPIRED`; the request expires when the old session starts *(superseded 2026-11-15, spec FR-045a: `expires_at` is the earliest of the start of the session being changed and the start of any requested target session; the ticket's other sessions never count; the code still uses the ticket's first session and is fixed by T130)*. Approval (Manager, reason, Idempotency-Key, audit) equals a FR-054 grant followed by one counted change. Unique per `request_reference` and per (`ticket_code`, `idempotency_key`). |
| Ticket change | ticket; type (`ADVANCE`, `LATE_ARRIVAL`, `MLAND`, `MANAGER_GRANTED`); from/to sessions; counted (yes/no); price effect (voucher allocation lost, balance increase); buyer confirmation; actor | At most one counted customer change per ticket unless a Manager grants more. Price never decreases. |
| Absorbed undercharge record (spec FR-057, decided 2026-11-15; **proposed, not yet migrated**, task T135) | proposed table `settlement_absorbed_undercharges`: `id`, `undercharge_reference` (unique), `ticket_code`, `bill_reference` (the ticket folio in `settlement_final_bills`), `location_reference` (BIGINT, as in V153), `amount_vnd` (BIGINT, CHECK > 0), `currency_code` (VND), `reason` (VARCHAR(1000), required), `recorded_by_account_id`, `recorded_at`, `idempotency_key`, `status` (`ACTIVE`, `VOIDED`), `void_reason`, `voided_by_account_id`, `voided_at`, `void_idempotency_key`, `created_at`; unique (`ticket_code`, `idempotency_key`) following V139/V153; index (`location_reference`, `recorded_at`, `status`) for the branch/period list | Only on a `FINALIZED` ticket folio (refused otherwise); Manager only; reason required; *(cap decided 2026-11-15, CHK009, FR-057)* `amount_vnd` may not exceed the ticket's folio total (price plus add-ons and variable charges, voided lines excluded) and the `ACTIVE` records of one ticket together may not exceed it either, checked in the service under the folio lock, `422` otherwise (the database CHECK stays `> 0`); immutable once saved except the one void (`ACTIVE` → `VOIDED`, reason, actor, time; a second void is refused); a voided record keeps its content and is excluded from totals; a correct amount is a new record; it collects nothing and changes no folio, line, balance, Loyalty earn, cash or cash close, and names no employee; audited on record and void. Exact column types and names are fixed by T135's migration. |
| Transfer/VNPay reconciliation list (spec FR-056, decided 2026-11-15, task T133) | **no new 003.3 table for the list** (the only schema change is the `branch_id` back-fill and index below; `BOOKING_TIME` data stays in 003.1): a read-only query over `settlement_folio_collections` filtered by `branch_id` (= `locationId`; decided 2026-11-15: `branch_id` is now filled for every method, not only cash — set at creation from the ticket folio context `locationReference`, and back-filled for existing transfer/VNPAY rows by a one-off Java job that calls the 003.1 facade `getTicketFolioContext` per ticket (decided 2026-11-16, A12; unresolved rows stay NULL and are listed in the evidence; no SQL across modules), with the index in a new migration (next free version at implementation time, checked against develop, T129; fix A35), T133), Vietnam working day, `method` in (`BANK_TRANSFER`, `VNPAY`, `BOOKING_TIME`; `BOOKING_TIME` rows are online booking-time VNPay payments read through a new read-only 003.1 facade operation, never stored in 003.3 and never calling VNPay; a successful-IPN row shows the amount received and counts in the total, an unverified row shows the requested amount as `PENDING_VERIFICATION` and is not counted as confirmed; decided 2026-11-16, A04); a `BOOKING_TIME` row belongs to the Vietnam day of its confirmation time (`CONFIRMED`) or of its attempt creation time (unverified or late-pending), its location by `locationIds[]` (decided 2026-11-16, option B, owner Hồ Hoàng Quốc: one row per payment, the sessions' locations of the group's tickets; the `locationId` filter returns every payment with at least one session at that location; the whole-system day total counts a payment once; per-location totals must not be summed, warn about double counting), 003.1 `CONFIRMED` maps to `CONFIRMED`, `UNVERIFIED` to `PENDING_VERIFICATION` and `LATE_PENDING_REVIEW` (money received, group not confirmed, awaiting review or refund) to `PENDING_VERIFICATION` with the reason note 'late payment under review'; neither of the last two counts in the confirmed total, and `confirmedBy` is null (confirmed by the system through the IPN) (R18/R19) and state (`CONFIRMED`, `PENDING_VERIFICATION`, `VOIDED` from the collection status; `EXCESS` from `settlement_evidence_conclusions` rows with conclusion `FOLIO_VNPAY_EXCESS`, decided 2026-11-15; `FAILED` is not listed), joined to the ticket folio for ticket and group code; row = one per payment: ticket code (nullable when a payment covers several tickets) plus `ticketCodes`, group code, amount, method, state (`status`, published as `state`), `excess_amount` and `refund_request_reference` (columns shown on a row for a transfer beyond the balance or a duplicate, null otherwise; fix A01, 2026-11-16, task T133), `external_reference`, confirming actor (`confirmed_by_account_id`) and `confirmed_at` | Writes nothing: no "reconciled" mark (R02, 2026-11-16: with `BOOKING_TIME` in the filter and 003.1 failing, the list answers `503 RECONCILIATION_SOURCE_UNAVAILABLE`, never a partial list.), no change to a collection, folio or cash close. Manager only. `cash_business_day` is set only for cash, so the working day of a transfer/VNPay row is derived from its time in Vietnam time: a `CONFIRMED` row belongs to the Vietnam day of `confirmed_at`, a `PENDING_VERIFICATION` row to the Vietnam day of its creation time (decided 2026-11-15, spec FR-056; earlier wording "T133 decides confirmed time vs. creation time for pending rows" superseded). |
| Cash reversal entry (spec FR-036, FR-053, decided 2026-11-15, checklist CHK014; **proposed, not yet migrated**, tasks T144/T145) | proposed table `settlement_cash_reversals`: `id`, `reversal_reference` (unique), `ticket_code`, `bill_reference` (the ticket folio in `settlement_final_bills`), `collection_id` (FK to the reversed `settlement_folio_collections` row, UNIQUE so one collection is reversed once), `branch_id` (= canonical `workshop_location.id`, same name as the collection and `settlement_cash_day_closes`), `amount_vnd` (BIGINT, CHECK < 0, = minus the collection's applied amount), `cash_business_day` (DATE, the current Vietnam working day when recorded; same name as the collection column), `original_cash_business_day` (DATE, the closed day of the reversed collection), `reason` (VARCHAR(1000), required), `recorded_by_account_id`, `recorded_at`, `idempotency_key`, `created_at`; unique (`ticket_code`, `idempotency_key`) following V139/V153; index (`branch_id`, `cash_business_day`) for the cash total. Alternative considered and not proposed: a negative-amount row inside `settlement_folio_collections` (breaks "only `CONFIRMED` counts" and the positive-amount checks). Whole-collection reversal only is the proposal; a partial reversal is not decided | Manager only; reason and `Idempotency-Key` required; audited with actor and time. Refused when the folio is `FINALIZED` or `CLOSED`, when a refund request exists for that money, when the collection is not `CASH`, is already `VOIDED`/reversed, or its cash day is not yet closed (that case is the FR-053 void). The closed day's `settlement_cash_day_closes` row and the original collection row are never edited; `FolioBalanceService` subtracts the reversal from the folio's collected amount (balanceDue is recomputed) and expected cash of the day `cash_business_day` = confirmed cash net of change + that day's reversals (negative). Cut-off: a reversal recorded after the counted amount of day D was submitted belongs to D+1 (FR-036). Exact names and types are fixed by the migration (never edit an applied one). |
| Cash close cut-off and next-day cash (spec FR-036, decided 2026-11-15, CHK013) | no new column: `settlement_cash_day_closes` already stores the submission time of the counted amount; a cash collection or reversal whose time is after that submission gets `cash_business_day` = next Vietnam working day, even while the close is `PENDING_MANAGER_CONFIRMATION`; the Manager's confirmation does not move the cut-off or change the submitted count | "Next day" = counted-amount submission, not the Manager's confirmation. The close stores the closer (`closed_by`, an active Staff or Manager whose location scope contains the branch; no session-assignment rule); only a Manager confirms a difference. Exact column of the submission time is checked in the code before the task is built (T147). |
| VNPay collection auto-`FAILED` (spec FR-034, decided 2026-11-15, CHK016) | no new table or column: a `PENDING_VERIFICATION` `VNPAY` row in `settlement_folio_collections` whose `created_at` is 24 h or more in the past and for which QueryDR reports no successful payment becomes `FAILED` with audit `FOLIO_GATEWAY_COLLECTION_AUTO_FAILED` (actor `SYSTEM`, proposed action name) | The 24 h run from the attempt creation time (the plan's earlier "24 h after the checkout expires" is superseded). Money signed after `FAILED` is accepted in full, stored as `FOLIO_VNPAY_EXCESS` evidence and routed to an `EXCESS_OR_DUPLICATE_MONEY` bank refund (T110 path), never lost. A QueryDR that is unavailable or inconclusive does not fail the row; it is retried. |
| Refund failure and return to `PENDING_ACCOUNT` (spec FR-039, FR-040, decided 2026-11-15, CHK005/006/021; **proposed**, tasks T146/T148) | extends `settlement_refund_requests` (proposed columns; exact names fixed by migration): `return_reason` / `returned_at` (last failure reason and time) and clearing of the approval fields (`approved_by_account_id`, `approved_at`, kept in audit); the encrypted number column is set to NULL and the mask kept; `settlement_refund_account_links` gets a new `ACTIVE` link and the earlier one `REVOKED` | A failed transfer (bank return, statement mismatch, Manager refusal) from `PENDING_APPROVAL`/`PENDING_TRANSFER`/`PENDING_RECONCILIATION` returns the request to `PENDING_ACCOUNT` in one transaction: reason recorded, ciphertext deleted, approval void, new link sent to the payer; no terminal rejection state and the entitlement to the refund never lapses. A key replacement (FR-040) applies the same rule to every request still holding ciphertext: the approval is void, a Manager approves again; one transaction per request, a rerun touches only requests still holding ciphertext. |
| Refund account entry confirmation (spec FR-040, decided 2026-11-15, CHK005/006; **proposed**, tasks T150/T151) | no stored column required: the submit request carries a proposed `accountDetailsConfirmed` flag that must be true; the audit of the entry records that the confirmation was given | The form shows bank, account number and holder name back before submit (customer link and Staff counter). No bank-name lookup in V1. The earlier transfer-time comparison of the holder name is superseded. |
| Refund transfer receipt image (spec FR-039, decided 2026-11-15, CHK007; ~~**OPEN decision, settled in plan O-1 before any build**~~ plan O-1 **decided 2026-11-15** by Hồ Hoàng Quốc; **proposed, not yet migrated**, task T154) | ~~not designed: optional attachment to the refund request's transfer step, visible only to a Manager and the transferring actor, following the request lifecycle. File types, size limit, storage location, retention period and whether it is a column or a separate table are **not decided and not invented here**~~ *(superseded 2026-11-15, O-1 decided)* proposed table `settlement_refund_receipt_images` (name and folder confirmed by Hồ Hoàng Quốc 2026-11-15, T154's migration may still adjust exact column names and types; modelled on 002's `catalogue_media_objects`, V15): `id` (CHAR(36)), `refund_request_id` (FK to `settlement_refund_requests.id`), `storage_key` (VARCHAR(255), unique; Cloudinary public_id in the proposed folder `ppswbs/settlement-receipts`, `type=authenticated`), `provider_asset_id`, `provider_version`, `checksum` (VARCHAR(64), SHA-256 of the bytes), `content_type` (CHECK in `image/jpeg`, `image/png`, `image/webp`), `byte_count` (BIGINT, CHECK BETWEEN 1 AND 5000000), `state` (proposed `UPLOADED`, `ATTACHED`, `DELETE_PENDING`, `DELETED`), `uploaded_by_account_id`, `upload_idempotency_key`, `created_at` (upload time), `attached_at`, `attached_by_account_id`, `attached_bank_transfer_reference` (snapshot of the `bank_transfer_reference` recorded with it, V122), `deleted_at`; unique (`uploaded_by_account_id`, `upload_idempotency_key`); index (`state`, `created_at`) for the cleanup; index (`refund_request_id`, `state`). No image bytes and no provider URL are stored | Optional: the bank reference (text) stays the required proof, and `RECORD_TRANSFER` succeeds without an image, also when image storage is unavailable. Upload only while the request is `PENDING_TRANSFER`, by an actor allowed to record the transfer (V1: Manager, FR-039). `RECORD_TRANSFER` attaches at most one `UPLOADED` image of the same request in its transaction (`UPLOADED` → `ATTACHED`); a second image for the same transfer evidence is refused (one `ATTACHED` row per recorded transfer, checked under the refund request lock; exact constraint fixed by the migration). Read only through the backend stream after the same authorization as the request. Retention *(fix A16, 2026-11-16: replaces "never deleted automatically in V1")*: an `ATTACHED` image is not deleted before 5 years after its refund request reaches `REFUNDED` or `CLOSED_UNREACHABLE` (job T166, after V1); until then the row and its object follow the refund record (also after `RETURN_TO_PENDING_ACCOUNT`); an `UPLOADED` row never attached for 24 hours is cleaned up (`DELETE_PENDING` → signed destroy → `DELETED`), same rule as 002. ~~The final retention period is an open accounting decision (owner Hồ Hoàng Quốc, plan O-1).~~ *(decided 2026-11-15, plan O-1)* An `ATTACHED` image object is destroyed 5 years after its refund request reaches `REFUNDED` or `CLOSED_UNREACHABLE` (`ATTACHED` → `DELETE_PENDING` → `DELETED`, `deleted_at` set); the row and its metadata are kept. The due date is computed from the request's terminal timestamp (use the existing column if the request already has one, otherwise T154 adds it); the deletion job is T166, after V1. Exact names and types are fixed by T154's migration (next free version at implementation time, checked against develop (T129); never edit an applied one). |
| Voucher restoration trigger (spec FR-042, FR-043, decided 2026-11-15, CHK011/034) | no 003.3-owned voucher or points data; the ticket cancellation / Mland remedy row keeps the restoration outcome returned by the voucher-and-points owner (restored, or not restored with reason `EXPIRED`) and the idempotency key `groupCode` | 003.3 only triggers the restoration through the shared facade (`workshop-interfeature-facades.md`, "Voucher and points restoration"); the owning module restores exactly once per `groupCode` and only while the voucher is still valid; an expired voucher is never extended; the refund by bank is unaffected. |
| Refund account link idempotency record (spec FR-022a, decided 2026-11-15, CHK019; **proposed, not yet migrated**, task T169; plan (n)) | proposed table `settlement_refund_account_link_idempotency`: `token_hash`, `idempotency_key`, `request_fingerprint` (keyed HMAC of the request, never the account number, holder name or token), `outcome`, `created_at`, `purge_after`; unique (`token_hash`, `idempotency_key`) | Scoped to the link token hash. Same key and same content replays `204` with no second write or email; changed content is `409 IDEMPOTENCY_CONFLICT`; a new key on a used, revoked or expired link stays `410 REFUND_ACCOUNT_LINK_NOT_USABLE`. `purge_after` = link expiry + 30 days; a sweep removes older rows. Exact types are fixed by T169's migration (next free version at implementation time, checked against develop, T129). |
| Notification payload token scrub (spec FR-012a, decided 2026-11-15, CHK030) | the stored refund-account email payload in the notification handoff keeps its 30-day retention, but the account link token field in it is cleared when the link is `USED`, `REVOKED` (resend, return to `PENDING_ACCOUNT`, key replacement) or `EXPIRED` | The token never outlives the link's usable period in the stored payload; the event still carries only references. Proposed mechanism: clear the token in the same transaction as the link state change plus a sweep for expiry. |

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
   snapshot. *(Superseded 2026-11-15 for tickets by spec FR-043: no policy or
   snapshot is consulted for a ticket refund; the policy tables are dropped by a
   new migration in T139.)*
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
    ~~a legacy VNPay refund with code `99` is reconciled before any manual
    refund of the same money~~ *(withdrawn 2026-11-15 by Hồ Hoàng Quốc: VNPay
    Refund and refund-QueryDR code removed (bc39429c), V155 dropped the VNPay
    refund columns; spec FR-039)*.
14. The buyer cancels chosen tickets (FR-041): one ticket, or several tickets of
    one group in one request that commits every listed ticket or none; each
    ticket keeps its own 24-hour outcome (FR-042); a customer cancellation
    refunds nothing of the booking-time collection.
15. A Mland cancellation affects only tickets with the cancelled session
    (FR-043) and refunds 100% of each affected ticket's collected money by
    bank (rule 13); the group's voucher use is restored at most once.
16. *(Decided 2026-11-15, checklist payment-error-access.md; reversal table and
    refund-return columns are proposed.)* Confirmed cash of a closed day is
    never edited; a wrong cash collection of a closed day is corrected only by a
    Manager cash reversal entry dated the current working day (refused for a
    `FINALIZED`/`CLOSED` folio or money under a refund request). The cut-off of
    the cash day is the submission of the counted amount (FR-036).
17. A `VNPAY` collection unreconciled for 24 hours and not paid per QueryDR is
    marked `FAILED` by the system; later money goes to an excess refund and is
    never lost (FR-034). A pending collection does not block a Mland
    cancellation (FR-034, FR-043); `FINALIZED` becomes `CLOSED` only through a
    Mland-cancellation refund (FR-043).
18. A failed bank refund, or a refund-account key replacement, returns the
    request to `PENDING_ACCOUNT`: ciphertext deleted (mask kept), approval void,
    a Manager approves again; the account entry needs an explicit confirmation
    (FR-039, FR-040). The account-link token is scrubbed from the stored
    notification payload once the link is not usable (FR-012a).
19. A voucher is restored only while still valid (otherwise reason `EXPIRED`,
    never extended); 003.3 triggers, the voucher owner restores once per
    `groupCode` (FR-042). An absorbed undercharge never exceeds the ticket's
    folio total, per record and per ticket in total (FR-057).
