# Implementation Plan: Customer Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-10-26
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.1-workshop-booking`
**Status**: Draft design — not approved for implementation
**Spec**: [spec.md](spec.md)
**Research baseline**: [research.md](research.md) and [REBASE-NOTES.md](REBASE-NOTES.md)

## Summary

003.1 is the direct-Mland customer booking core. One booking purchases exactly
one package/course, with no cart of unrelated packages. Basic Ring selects one
common workshop session. Direct-Mland Silverclay selects exactly two common
sessions at one branch, ordered by time; both reservations are committed or
neither exists. Each participant consumes one place in every selected session,
and every package shares that session's single capacity pool.

The booking freezes `participant_count × price_per_participant`, the resulting
post-promotion amount, `ceil(post_promotion_amount / 2)` deposit, and exact
remaining amount. It creates one deposit invoice and no participant identity
records.
Valid, idempotent gateway evidence — never a browser redirect — confirms the
booking and issues one QR ticket per participant. A Silverclay ticket admits
one seat in both selected sessions. It is not identity-bound. 003.1 stops at
ticket issuance/delivery and authorized lookup.

## Technical Context

| Area | Design baseline |
| --- | --- |
| Runtime | Java 21, Spring Boot REST, Spring Modulith, JPA/Spring Data, MySQL and append-only Flyway migrations. |
| Client | Separate React + Vite client using versioned REST APIs; no backend-rendered booking UI. |
| Identity and authorization | Firebase bearer tokens prove identity only. Account Management supplies canonical `MEMBER`, `STAFF`, `MANAGER`, or `ADMIN` role, active status and location scope; Booking applies its own support-action rules against booking branch/resource scope. Guest access uses booking-contact proof or a protected Magic Link. |
| State | Persisted booking/hold/payment/ticket state; 15-minute holds and 10-minute payment links; booking-critical local work uses one MySQL transaction; no new global session/token store. |
| Payment | Shared `paymentintegration` ingress verifies VNPay once and routes redacted evidence by immutable merchant-reference target; 003.1 consumes only `DEPOSIT` evidence through its facade. |
| Verification | Unit/integration, contract, concurrency, callback/idempotency, migration, and Spring Modulith boundary verification. |
| Unknowns | VNPay integration values/environments, QueryDR/refund configuration, and production rollout approval. The 003 facade types and shared ingress boundary are defined in the shared internal contract. |

### Approved React route boundary

The React + Vite client owns `/workshops` (browse), `/workshops/:packageId`
(detail/session selection), `/workshops/:packageId/book` (contact, pending
booking, and payment), `/workshop-booking/lookup` (Guest/Member recovery), and
`/workshop-booking/access#token` (Magic-Link exchange). Browser return renders
at `/workshop-booking/:bookingCode/payment-return`; it reads only current
server-confirmed state and never promotes redirect parameters to payment
evidence. These routes are client routes, not Java REST endpoints or SSR pages.

## Constitution Check

| Gate | Result | Plan response |
| --- | --- | --- |
| Module and data ownership | PASS | `workshopbooking` owns booking aggregate records and accesses other features only through approved facades/events. |
| Separate frontend/REST | PASS | Contract is JSON `/api/v1`; Magic Link resolves to client-readable data, never server-rendered HTML. |
| Identity and sensitive data | PASS | No participant identity, OTP, raw gateway payload, raw Magic-Link secret, or unnecessary PII is exposed/logged. |
| Data evolution | CONDITIONAL | Reset is permitted only for dev/test environments with no booking/customer data requiring retention after preflight. Any staging, production, or data-bearing environment needs an append-only preservation migration plus upgrade and recovery/rollback evidence. |
| Evidence and concurrency | PASS | Atomic multi-session hold, payment callback idempotency, late-payment rejection, and capacity races have explicit verification scenarios. |
| Explicit decisions | CONDITIONAL | The shared baseline fixes the role vocabulary and the approved [003 internal facade contract](../contracts/workshop-interfeature-facades.md) fixes cross-feature shapes. Public HTTP remains a separate approved contract. |

## Module boundaries and flows

`workshopbooking` owns booking, booking-session commitment, frozen invoice,
payment attempt/receipt, booking-contact delivery state, Magic-Link record and
notification intent. It synchronously reads only a 003.2 sale-eligibility snapshot
(customer-selectability, sale state, branch, scheduled start/end and remaining
capacity), and calls the exposed 003.2-owned `WorkshopOperationsFacade`. It does not
configure sessions, assign staff, read Operations entities, check in, or
finalize attendance. After capacity commits, it calls that facade's
delivery-safe ticket-projection operation for customer delivery; it never derives
attendance. On every authorized booking/Magic-Link read, it obtains the current
delivery-safe projection again, so a later 003.3/003.2 replacement or verified
refund consequence cannot leave the customer seeing an old schedule.
003.3 consumes frozen deposit/base-price evidence for later settlement, refund
and reschedule. It returns only an idempotent final-balance-resolved conclusion
when final settlement or an authorized audited deferred-payment resolution is
complete; Booking alone records `FULLY_PAID` from that conclusion and never
receives raw final-payment evidence or calculates the final balance. 003.4 owns
OTA completely. For internal booking support,
Mland rechecks canonical active account facts and Booking's support-action rule
against booking-branch scope at
action time. Assigned Staff or branch-responsible Managers may resend the
confirmation; only the responsible Manager may revoke or rotate a Magic Link.
The `ADMIN` role alone grants neither action.

Create flow: validate package/contact/participant count and selected sessions;
enforce Basic Ring = one session or Silverclay = two distinct sessions at the
same branch; order Silverclay sessions; reject adjacent same-day sessions;
atomically reserve `participant_count` places in every session and persist the
pending booking, invoice and expiry in one local MySQL transaction. Any
validation/capacity failure rolls back the whole operation. Capacity is always
the one shared pool of a session.

Payment flow: start/retry only while the hold is active. The provider result
must authenticate, match the frozen reference/amount/currency, and be accepted
only once. After gateway evidence has been validated, one local MySQL
transaction asks 003.2 to commit the complete active hold set and confirms the
Booking state; if any expected hold is absent or expired, nothing commits and
Booking remains unconfirmed. On success, Booking obtains the delivery-safe projection for N
tickets and transactionally stores an immutable notification handoff. After
commit, it publishes a reference-only event to Feature 007. Feature 007
deduplicates that reference, obtains the immutable render-ready payload only
through `WorkshopBookingFacade`, and owns provider send/retry logs. Neither a
handoff nor provider-delivery failure can reverse the Booking outcome.
Failure leaves `PENDING_PAYMENT/UNPAID`; expiry/release requests release of all
active holds exactly once. A signed success after hold expiry never confirms;
only the responsible Manager may inspect safe evidence and record or escalate a
follow-up, without restoring capacity, issuing tickets, or creating a new
payment link.
Events occur only after Booking's local state transition for non-critical
notification; an event never determines the capacity result or whether Booking
confirms payment.

## Contract and state design

The public OpenAPI contract defines package browse; package-and-branch-scoped
session discovery over a requested time range; create; payment start;
contact-proof and Member-owned lookup; Magic-Link exchange/read; pending-hold
release; confirmation resend; Manager Magic-Link incidents; and Manager
late-payment follow-up. The shared VNPay ingress is documented separately and
is never a 003.1 business controller. Check-in, cancellation/refund,
reschedule, final payment, and OTA routes remain outside 003.1.

Booking service states are `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, and
`CANCELLED`; financial states are `UNPAID` and `DEPOSIT_PAID` for deposit flow.
`FULLY_PAID` is recorded by Booking exactly once only from 003.3's idempotent
final-balance-resolved conclusion; 003.1 does not implement final settlement.
`FAILED` belongs to a payment attempt. Refund, attendance and ticket-use states
remain downstream-owned. A client Idempotency-Key protects create,
payment-start and release commands; provider event identity protects callbacks.

Magic Link is a reusable bearer credential for the read-only booking/ticket
projection until the associated workshop ends or the link is revoked. Only a
protected hash and safe lifecycle state are persisted. Rotation immediately
revokes the old link, creates a replacement for the submitted booking-contact
email and queues delivery; delivery failure never restores the old link.
Guest booking-code plus stored-phone lookup remains the recovery route. The
email opens a React route with the opaque value in the URL fragment; the client
immediately submits it by HTTPS POST, clears the fragment, and retains only the
allowed projection in memory. Each later opening exchanges again. This does not
authorize a Member login, shared session, cookie, or persistent client
credential storage.

Business audit records cover accepted self-service resend, every internal
resend, Magic-Link revoke/rotate, and Manager late-payment follow-up. They hold
only the actor type or identity reference when available, booking code,
applicable branch, action, outcome, correlation reference and timestamp; a
revoke/rotate and late-payment follow-up also have their required Manager
reason. Contact proof, raw Magic Link, QR, and token data are excluded.
Notification delivery attempts and retries are operational logs, not business
audit records.

## Data, migration, and operational constraints

The model in [data-model.md](data-model.md) adds a booking-to-session commitment
instead of a cart/item model. Its delivery projection represents one 003.2
ticket per participant and its entitled sessions, allowing one Silverclay QR to
be checked independently in either session without storing a participant
identity. Monetary snapshots are integer VND, with even `price_per_participant`.

No existing applied migration may be edited. Development/test reset is allowed
only after preflight confirms no booking/customer data requires retention. Any
staging, production, or data-bearing environment requires a separate append-only
preservation migration, upgrade test and recovery/rollback plan. Deployment
topology, origins, provider credentials, monitoring
ownership and SLOs remain outside this plan's decision authority. The FE-001
note is the approved dependency for the fixed role vocabulary and Mland-owned
account/scope checks, pending synchronization into `spec-general.md` and
`APIs.md`.

## Verification plan

Use [quickstart.md](quickstart.md) to verify: one-session Basic Ring; two-session
Silverclay with shared capacity; all-or-nothing failure; same-branch/ordering/
same-day-gap rules; odd/even post-promotion amount with rounded-up deposit and
exact remaining amount; valid/invalid/duplicate/late gateway
events; ticket issuance and lookup; Magic-Link revocation; hold expiry; and
authorization-safe lookup. The approved frontend E2E suite must prove that a
visitor reaches booking entry from the workshop listing in no more than three
primary interactions. It additionally verifies authorization denial without
side effects, resend rate limits, replacement-link delivery failure, and the
separation of business audit evidence from delivery logs. Implementers must also
evidence MySQL migration coverage, API contract validation, and
`ApplicationModules.verify()`.

## Design risks and handoff conditions

The deliberately rejected shortcut is a generic cart or a separate Silverclay
slot/capacity type: both contradict the approved one-package booking and shared
slot model while adding avoidable allocation complexity. The remaining blockers
are not product decisions to guess:

1. **Resolved semantically and by interaction mode:** Booking synchronously
   obtains sale eligibility and reserve/commit/release outcomes from the 003.2
   `WorkshopOperationsFacade`; `SALES_PAUSED` rejects new holds while preserving unexpired
   ones. After a successful capacity commit it obtains only the safe
   ticket/session-entitlement projection from its ticket-projection operation.
   Each booking-critical command shares one local MySQL transaction across
   Booking and the synchronous facade call; gateway
   communication remains outside that transaction. The named facade operations,
   boundary types, outcomes, and idempotency rules are fixed in the shared
   [003 internal facade contract](../contracts/workshop-interfeature-facades.md).
2. Real VNPay callback integration, QueryDR recovery, and refund adapter access
   need provider/environment-approved inputs before enablement. The complete
   non-secret handoff checklist is
   [vnpay-configuration-preflight.md](../contracts/vnpay-configuration-preflight.md);
   the business semantics are already specified and can use a fake adapter.
3. The rollout policy is decided, but each data-bearing environment still needs
   its preservation migration, upgrade/recovery evidence and accountable release
   owner. FE-001's role and account-scope decisions are approved but must be
   synchronized into shared documents before implementation handoff.

This plan is ready to inform task drafting only after those dependencies are
represented as approved contracts or explicit blocked tasks; it does not itself
approve code, schema migration, deployment, or the proposed public API names.
