# Data Model: Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-11-14
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Status**: Updated 2026-11-14 to the implemented group/ticket model (migrations
V113–V133); owner re-approval pending. *(Previous status: "Draft design — schema
names and migrations require approval before implementation".)* The current
direct-booking model is the section "Booking group and ticket bookings" below;
the single-`WorkshopBooking` sections that follow it here are history for direct
bookings and still describe OTA bookings (003.4).

## Ownership and aggregate *(single `WorkshopBooking`: superseded for direct bookings by FR-044, 2026-11-13; still the OTA model)*

`WorkshopBooking` is one direct-Mland commercial aggregate for one package. It
is not a cart and does not own session configuration, check-in, attendance,
settlement, refund, reschedule, or OTA facts.

```text
WorkshopBooking (1) ── (1) BookingInvoice
        ├── (0..N) MemberBookingQuote (single-use acceptance proof)
        │             └── (N) PaymentAttempt / (N) PaymentEventReceipt
         ├── (Package-required set) BookingSessionCommitment ── WorkshopSession (003.2)
        ├── (N) BookingTicket ── (1..2) TicketSessionEntitlement
        └── (0..N) NotificationIntent / (0..N) MagicLinkAccess
```

## Entities and invariants

| Entity | Owned data | Required invariant |
| --- | --- | --- |
| WorkshopBooking | booking code; frozen Package selection snapshot; contact email/phone snapshot; optional Member owner reference; participant count; service/financial status; expiry | Exactly one Package and one contact. The snapshot comes only from 003.2's published-Package boundary; `participant_count` is positive and determines ticket count. |
| MemberBookingQuote | quote reference; owning Member; package ID/name/price snapshot; ordered session UUID references with location/date facts; participant count; requested benefit selection; Loyalty terms digest and amount breakdown; `expiresAt`; acceptance state and accepted booking reference | A quote is bound to its authenticated Member and exact selection and expires 30 minutes after preview. The preview exposes `expiresAt` and reserves no capacity, voucher quota, or points. Acceptance at or after expiry returns `409 QUOTE_EXPIRED` without creating a booking or reserving capacity/benefits. Before expiry, acceptance reloads authoritative Operations facts and Loyalty revalidates terms in the same transaction that reserves all session capacity and held benefits. A quote is single-use; changed terms require a fresh preview and explicit customer confirmation. |
| BookingSessionCommitment | booking; opaque UUID `sessionReference`; chronological display ordinal; canonical location snapshot; held/committed seat count | The accepted commitment set is decided atomically by 003.2 from the selected Package rules. Booking does not retain a numeric session ID or independently authorize session count, location or slot gap. |
| CapacityHold | 003.2 capacity-facade hold reference and safe outcome snapshot for a booking-session commitment | 003.2 is authoritative for the hold. Booking retains only the reference/status needed for its own pending, expiry and release behavior; one requested set is reserved/released atomically and exactly once. |
| BookingInvoice | booking; frozen package price/participant count; frozen post-promotion amount; deposit; remaining amount | `package_total = participant_count × price_per_participant`; `deposit = ceil(post_promotion_amount / 2)` and `remaining = post_promotion_amount − deposit`. All three monetary snapshots are whole VND and immutable after acceptance. |
| PaymentAttempt / PaymentEventReceipt | booking/invoice; safe provider references; attempt outcome; unique provider event identity | Only valid, matching, non-duplicate provider evidence settles the invoice. `FAILED` belongs to an attempt, not booking state. |
| BookingTicket | booking; delivery-safe ticket identity/QR reference and ticket code received from `WorkshopOperationsFacade`; delivery state | A booking-side delivery projection, not the authoritative Operations ticket. Exactly N tickets are available only after confirmation; no participant identity/contact is stored. |
| TicketSessionEntitlement | `WorkshopOperationsFacade` delivery-safe view; booking-session commitment | A booking-side delivery view of each 003.2 ticket/session entitlement. A multi-session ticket (currently Wax Casting) has one entitlement for each required session; 003.2 owns entitlement validation and check-in/attendance state on that set. Customer reads obtain the current projection from 003.2, so this copy never becomes the authority for a replacement/refund schedule. |
| MagicLinkAccess | booking; protected token hash; issued/expiry/revocation lifecycle state; delivery-value scrub timestamp | The hash is used for lookup and lifecycle enforcement. The raw bearer URL is the sole persistence exception and may exist only in the protected render-ready notification snapshot for the matching handoff. When expired or revoked, the URL is scrubbed from all associated snapshots by the one-minute cleanup sweep and `delivery_value_scrubbed_at` records completion. Access is reusable until the latest required-session end or revocation; rotation revokes the old link and creates its replacement for the stored booking-contact email. |
| NotificationIntent | booking; immutable notification reference, kind, correlation reference, and render-ready delivery snapshot | Created transactionally with confirmed booking delivery, accepted resend, or accepted Magic-Link rotation. Its post-commit event carries references only; Feature 007 may retrieve one payload only through the internal facade after the matching handoff. The raw bearer URL is scrubbed from every snapshot no later than the next one-minute cleanup sweep after expiry/revocation; the remaining snapshot is immutable. The URL is excluded from events, business audit, and application/provider logs. Feature 007 owns delivery attempts/retries as operational logs. Delivery failure cannot reverse the accepted Booking outcome. |
| BookingAuditEvidence | safe actor type or identity reference when available; booking code; applicable canonical location; action; outcome; correlation reference; timestamp; action reason where required; separate Manager reason where required | Covers customer pending-hold release, accepted self-service resend, every internal resend, Magic-Link revoke/rotate, and Manager late-payment `RECORD`/`ESCALATE` follow-up. Customer release stores the required reason in `action_reason`; Manager-only reasons remain in `manager_reason`. It excludes contact proof, raw Magic Link, QR, token material and unnecessary PII. |
| BookingLatePaymentEvidence | booking reference/code, immutable merchant reference, verified evidence reference, optional provider transaction reference, exact VND amount, verification timestamp | One deduplicated row for signed/verified deposit success received after capacity release. It is reconciliation evidence only; it does not confirm the booking, reserve capacity, issue tickets, or retain raw provider payload/checksum. |
| BookingDepositReconciliation | one row per deposit attempt; lifecycle status, next/last QueryDR time, short verified evidence reference | Durable post-expiry QueryDR candidate. The scheduler runs outside the original booking hold, never reopens it, and exposes unresolved rows older than 24 hours in a daily Manager list. No gateway payload, signature, secret, or contact data is stored. |

## Lifecycle

```text
PENDING_PAYMENT + UNPAID
  ├─ verified deposit event → CONFIRMED + DEPOSIT_PAID
  ├─ hold expiry → EXPIRED + UNPAID
  └─ customer release while active → CANCELLED + UNPAID

CONFIRMED + DEPOSIT_PAID
  ├─ idempotent final-balance-resolved conclusion from 003.3 → financial status FULLY_PAID
  ├─ RecordBookingRefundStatusCommand(REFUND_PENDING) from 003.3 (Mland remedy) → REFUND_PENDING
  └─ RecordBookingRefundStatusCommand(REFUNDED) from 003.3 → REFUNDED

CONFIRMED (service status), on RecordBookingServiceOutcomeCommand from 003.3 (D13)
  ├─ folio FINALIZED → COMPLETED
  ├─ folio CLOSED by customer cancellation or Mland remedy → CANCELLED
  └─ folio CLOSED by full no-show → NO_SHOW
```

Each conclusion changes only the named Booking status, exactly once and
idempotently. Booking retains a safe correlation/reference and does not receive
raw VNPay/final-payment detail, calculate final balance, or decide attendance,
cancellation or refund outcomes. It becomes `FULLY_PAID` only after 003.3 has
verified the complete final balance; this feature has no deferred-payment
exception. A post-`FINALIZED` overcharge correction changes no Booking status.

Only the listed transitions belong to 003.1. Final payment, refund execution,
reschedule, `CHECKED_IN`, no-show determination, and attendance finalization are
outside it (owner decisions D13/D14, 2026-11-05).

## Validation and concurrency

- Booking obtains the frozen published-Package selection snapshot and sale
  eligibility only through `WorkshopOperationsFacade`; no direct access to
  003.2 persistence is permitted. It sends `packageReference`, the complete
  UUID session-reference set and participant count to the atomic reserve
  command; Operations returns the safe Package/capacity refusal outcome.
- Member preview calls Loyalty `previewBenefits` using Package price and all
  authoritative selected-session facts, then persists the immutable quote
  selection and returned `termsDigest`. Guest booking remains available without
  Loyalty benefits. Member accept sends only `quoteRef` plus contact details;
  the server binds the quote to the authenticated Member and does not accept
  client-supplied prices, discounts, point amounts or session facts.
- In Member accept, Booking revalidates the stored quote against current
  Operations facts, reserves capacity for the complete session set, then calls
  `reserveAcceptedTransaction` in the same MySQL transaction. The returned
  `postPromotionAmount` is frozen into the invoice; Booking computes
  `deposit = ceil(post / 2)` and `remaining = post - deposit`. A failure in
  either boundary rolls back both capacity and Loyalty holds.
- A verified deposit settles held benefits. Earned points are granted only
  after every required session is completed and the invoice is fully paid.
  Expiry releases held benefits; an eligible cancellation restores benefits;
  a final no-show finishes earn eligibility without reversing consumed
  benefits. These calls use the source outcome version and proof references
  from Booking/Operations/Settlement, never raw gateway payloads.
- 003.2 atomically reserves the requested participant count in every selected
  session or creates no hold. It commits the complete active set or commits
  none, and releases active holds idempotently; `SALES_PAUSED` rejects new holds
  while preserving unexpired existing holds to their deadline.
- Booking preserves chronological display ordinals only after Operations accepts
  the selected set. Operations owns enforcement of the Package's distinctness,
  location, same-day slot-gap and foundry-working-date rules. Wax Casting
  requires at least two Monday–Friday local dates strictly between its sessions;
  Saturday/Sunday are excluded and slot/time does not affect the calculation.
- All packages consume the one shared capacity pool per session. A multi-session
  booking consumes N seats in every selected session.
- Create, capacity reservation and expiry/release have one all-or-nothing
  business outcome through the synchronous 003.2 `WorkshopOperationsFacade`. Each
  booking-critical command shares one local MySQL transaction across Booking's
  own writes and that capacity call; gateway communication is outside it. Only
  after a successful commit does Booking call the separate ticket-projection
  facade. Provider-event receipt uniqueness and command idempotency prevent
  duplicate confirmation or release. Events may run only after a local Booking
  state transition and cannot determine capacity or confirmation.
- Before internal resend, Magic-Link incident handling, or late-payment follow-up,
  Mland rechecks canonical active account facts and booking's own support-action
  rule against booking-location scope, using the same `workshop_location.id` as Account `activeLocationIds`.
  A failed check has no booking/payment/ticket/capacity side effect and records
  only the required minimal denied-attempt audit evidence.

## Booking group and ticket bookings (decision 2026-11-12, spec FR-030–FR-043)

~~Planned model, not yet implemented (tasks Phase 8).~~ *(updated 2026-11-14)*
Implemented by T089–T105 in migrations V113–V128 and V132/V133 (table list
below, confirmed against the migration files). It replaces "one booking = one
package with `participant_count` tickets and one invoice" for every direct
booking (FR-044).

```text
BookingGroup (1) ── (1..N) TicketBooking ── (1..2) TicketSessionEntitlement
     │                     ├── (1) TicketPriceSnapshot (allocations, collection kind)
     │                     ├── (0..1) TicketRecipient (+ change history)
     │                     └── (1) OPEN folio in 003.3
     ├── (1) GroupQuote (cart lines, recorded Loyalty policy, voucher use)
     ├── (N) GroupPaymentAttempt / receipt (one VNPay payment for the group)
     └── (0..N) LatePaymentReview
```

| Entity | Owned data | Required invariant |
| --- | --- | --- |
| BookingGroup | group code; buyer (Member account or Guest contact); shared contact snapshot; group status (`PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CANCELLED`); one `holdExpiresAt` (12 min); payment link expiry (`holdExpiresAt − 2 min`); group collection amount and mode (`VNPAY`, `INTERNAL_FREE`, `INTERNAL_DEFERRED`); voucher use reference | All tickets are held, confirmed, expired or released together until confirmation; afterwards each ticket has its own lifecycle. One voucher use per group. Group collection = sum of ticket collections; VNPay only when ≥ 5,000 VND. |
| TicketBooking | ticket booking code; group; package and session-set snapshot; service/financial status; QR/Magic Link reference; one-change counter used by 003.3 | One person per ticket. A multi-session package is one ticket. Cancel/change/refund of one ticket never changes another ticket implicitly. |
| TicketPriceSnapshot | package price; voucher allocation; allocated points and point discount; final price; collected at booking; collection kind (`FREE`, `FULL`, `DEPOSIT`, `DEFERRED_BELOW_VNPAY_MINIMUM`); remaining; disclosed non-refundable condition; recorded Loyalty policy revision | Immutable after confirmation. Allocations use largest remainder in whole VND / whole points (ties: higher price, then cart order); voucher allocation ≤ ticket price; ticket at 0 after voucher gets no points. Collection: 0 → 0; 1–200,000 → full; > 200,000 → 200,000; whole group 1–4,999 → all deferred to checkout. |
| TicketRecipient | ticket; recipient email (encrypted or masked in reads); active QR generation; change count; change history (old/new masked email, actor, channel, time) | Optional. A recipient change revokes the old QR and issues a new one; same-email resend keeps the QR. No change after check-in; at most 3 online changes per ticket. Only the buyer or Staff after verifying the buyer may change it. |
| LatePaymentReview | group; verified evidence reference; arrival time; evaluated conditions (capacity, start ≥ 2 h, recomputed amount, ≤ 60 min, no duplicate booking); outcome (`AUTO_RECONFIRMED`, `PENDING_MANAGER`, `MANAGER_CONFIRMED`, `REFUND_REQUESTED`) | One review per verified evidence. Auto re-confirmation is atomic for the whole group and idempotent across IPN/QueryDR. Manager confirmation is allowed only when only the 60-minute or duplicate-booking condition failed. |

`BookingInvoice` keeps its meaning for single-ticket history; for new bookings
the per-ticket `TicketPriceSnapshot` and the 003.3 folio replace the group-level
`deposit = ceil(post / 2)` invariant.

### Implemented tables (V113–V128, verified 2026-11-14 against `ppswbs_backend/src/main/resources/db/migration`)

| Table (migration) | Key columns | Constraints / notes |
| --- | --- | --- |
| `booking_groups` (V113) | `group_code` (unique), `buyer_member_account_id` (null for a Guest), `contact_email`, `contact_phone`, `group_status`, `payment_mode`, `group_collection_vnd`, `hold_expires_at`, `payment_deadline_at`, `voucher_use_reference`, `loyalty_policy_revision`, `confirmed_at`, `version` | `group_status` ENUM `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CANCELLED`; `payment_mode` ENUM `VNPAY`, `INTERNAL_FREE`, `INTERNAL_DEFERRED`; `ck_bg_vnpay_floor` (VNPAY only when collection ≥ 5,000), `ck_bg_internal_zero` (internal modes collect 0). |
| `booking_group_lines` (V114) | `group_id`, `line_index`, `line_reference` (unique), `package_id`, `package_name_snapshot`, `price_per_participant_vnd`, `ticket_count` | Unique (`group_id`, `line_index`); `ticket_count ≥ 1`. |
| `booking_group_line_sessions` (V114) | `line_id`, `ordinal`, `session_reference` (UUID), `location_reference`, `scheduled_start`, `hold_reference`, `hold_status` | `hold_status` ENUM `RESERVED`, `COMMITTED`, `RELEASED`; the hold belongs to the line (every ticket of the line takes one seat in each session). |
| `workshop_cart_quotes` (V114, V118) | `quote_ref` (unique), `buyer_member_account_id`, `lines_json`, `gross_vnd`, `voucher_discount_vnd`, `points_used`, `point_discount_vnd`, `final_total_vnd`, `group_collection_vnd`, `payment_mode`, `quote_state` (`OPEN`/`ACCEPTED`), `accepted_group_code`, `expires_at`; V118 adds `benefit_selection_json`, `eligible_ticket_indexes_json`, `point_value_vnd`, `terms_digest`, `loyalty_policy_revision` | `ck_wcq_amounts` (final = gross − voucher − points discount; collection ≤ final), same VNPay floor/internal-zero checks as groups, `ck_wcq_point_value` (points used ⇒ point value > 0). Holds no capacity. |
| `ticket_bookings` (V113, V115) | `ticket_code` (unique), `group_id`, `line_index`, `participant_ordinal`, `package_id`, `package_name_snapshot`, `ticket_status`, `financial_status`, `customer_changes_used`; V115 adds `operations_ticket_reference`, `qr_reference` (both unique), `confirmed_at` | `ticket_status` ENUM `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`; `financial_status` ENUM `UNPAID`, `DEPOSIT_PAID`, `FULLY_PAID`, `REFUND_PENDING`, `REFUNDED`; `ck_tb_qr_pairing` (Operations reference and QR set together). |
| `ticket_price_snapshots` (V113) | `ticket_id` (unique), `package_price_vnd`, `voucher_discount_vnd`, `points_used`, `point_discount_vnd`, `final_price_vnd`, `collected_at_booking_vnd`, `collection_kind`, `remaining_vnd`, `non_refundable_disclosed`, `loyalty_policy_revision` | `collection_kind` ENUM `FREE`, `FULL`, `DEPOSIT`, `DEFERRED_BELOW_VNPAY_MINIMUM`; `ck_tps_amounts` (final = price − discounts; collected + remaining = final) and `ck_tps_kind` (FR-035 bands: FREE 0; FULL 1–200,000 collects all; DEPOSIT ≥ 200,001 collects 200,000; DEFERRED collects 0). |
| `ticket_recipients` (V113) | `ticket_id` (unique), `recipient_email`, `change_count`, `qr_generation` | `change_count ≥ 0`, `qr_generation ≥ 1`. |
| `ticket_recipient_changes` (V113, V127) | `ticket_id`, `old_email_masked`, `new_email_masked`, `actor_type`, `actor_reference`, `channel`, `changed_at`; V127 adds `change_kind`, `qr_rotated`, `counted_online`, `reason`, `correlation_reference`, `idempotency_key` (unique) | `change_kind` `FIRST_SEND`/`CHANGE`; `actor_type` `MEMBER`/`GUEST`/`STAFF`/`MANAGER`; `channel` `ONLINE`/`SHOP`. Emails stored masked. |
| `booking_buyer_verifications` (V127) | `verification_reference` (unique), `group_id`, `status`, `otp_hash`, `otp_salt`, `failed_attempts`, `otp_expires_at`, `buyer_verification_reference` (unique), `reference_expires_at`, `consumed_at` | `status` `PROOF_MISMATCH`, `PENDING`, `VERIFIED`, `CONSUMED`, `LOCKED`, `SUPERSEDED`; `failed_attempts` 0–5; only a salted OTP hash is stored. |
| `group_payment_attempts` (V113) | `group_id`, `attempt_reference`, `merchant_reference` (both unique), `amount_vnd`, `provider_create_date`, `payment_url_snapshot`, `attempt_status`, `expires_at` | `attempt_status` ENUM `PENDING`, `SUCCESS`, `FAILED`, `EXPIRED`; `ck_gpa_amount` (≥ 5,000). |
| `booking_group_magic_links` (V115) | `group_id`, `token_hash` (unique), `lifecycle_status`, `expires_at`, `revoked_at` | `lifecycle_status` ENUM `ACTIVE`, `REVOKED`, `EXPIRED`; one group link, expiring with the last session. |
| `booking_group_notification_intents` (V127) | `notification_reference` (unique), `group_id`, `ticket_id` (nullable), `notification_kind`, `correlation_reference`, `delivery_snapshot`, `snapshot_expires_at` | Feature 007 pulls the snapshot through `WorkshopBookingNotificationFacade`; snapshots holding an OTP are scrubbed at expiry. |
| `late_payment_reviews` (V113, V128) | `group_id`, `verified_evidence_reference` (unique), `amount_vnd`, `arrived_at`, `failed_conditions`, `outcome`, `decided_by_account_id`, `decision_reason`, `decided_at`, `result_due_at`; V128 adds `review_reference` (unique), `earliest_session_at`, `decision`, `refund_request_reference`, `correlation_reference`, `version` | `outcome` ENUM `AUTO_RECONFIRMED`, `PENDING_MANAGER`, `MANAGER_CONFIRMED`, `REFUND_REQUESTED`; `decision` `CONFIRM`/`REFUND`; queue index (`outcome`, `earliest_session_at`, `result_due_at`). |

Related tables owned by other features: `ticket_qr_generations` (V126, 003.2,
revoked QRs on recipient change) and `settlement_final_bills.group_reference`
(V116, 003.3 per-ticket folio).

**Programme discount on the quote (T105, 2026-11-14).** `voucher_discount_vnd`
on `workshop_cart_quotes` and `ticket_price_snapshots` keeps its name but
carries whichever programme discount applied — the automatic workshop discount
or the Member's voucher (006 FR-015: one or the other). No column was added;
the quote response reports the programme in `appliedDiscount`
(`NONE`/`AUTO`/`VOUCHER`). A cart with no applicable automatic discount keeps an
empty benefit snapshot and reserves nothing. Consequence: late-payment
condition 3 fails a group with an automatic discount (spec FR-040, open owner
decision).

## Migration boundary

**Update 2026-11-12:** only dev/test data exists, so the group/ticket model is
introduced by a schema migration plus reseed; no booking conversion is needed.

**Update 2026-11-13 (spec FR-051, tasks T101).** A direct booking is only a
booking group, so the tables of the removed single direct flow go in two
append-only migrations:

- **V132** deletes every non-OTA `workshop_bookings` row (its cascading rows,
  plus `booking_late_payment_evidence` and `booking_deposit_reconciliations`
  first, whose keys do not cascade) and every `member_workshop_booking_quotes`
  row. OTA bookings (`booking_code LIKE 'OTA-%'`) are kept.
- **V133** drops the tables no entity maps any more:
  `booking_ticket_session_entitlements`, `booking_tickets`,
  `booking_capacity_holds`, `booking_magic_links`,
  `booking_notification_intents`, `booking_deposit_reconciliations`.
- **Kept with their tables:** `workshop_bookings` and
  `booking_session_commitments` (an OTA booking is still one of these);
  `booking_invoices`, `booking_payment_attempts`, `booking_payment_receipts`,
  `booking_late_payment_evidence` and `member_workshop_booking_quotes`, which
  the 003.3 booking-wide settlement and the Loyalty single-booking source read
  until the open 003.3 decision of 2026-11-13; `booking_audit_evidence` and
  `booking_idempotency_records`, shared with the group flow.

An OTA booking has no invoice, ticket row, folio, Magic Link or notification
intent, so dropping those tables does not touch it.

~~This document does not authorize a migration.~~ *(updated 2026-11-14: the group
migrations V113–V128 and V132/V133 are already applied in dev/test, FR-043/FR-051;
this document records them and authorizes no further migration.)* Existing applied migrations stay
unchanged. Dev/test reset requires a preflight confirming no booking/customer
data needs retention. Staging, production, or any data-bearing environment needs
an append-only preservation migration and upgrade/recovery evidence.
