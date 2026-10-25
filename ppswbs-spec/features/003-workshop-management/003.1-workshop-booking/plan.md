# Implementation Plan: Customer Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-10-25
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

The booking freezes `participant_count × price_per_participant` in even VND,
creates one 50% deposit invoice, and creates no participant identity records.
Valid, idempotent gateway evidence — never a browser redirect — confirms the
booking and issues one QR ticket per participant. A Silverclay ticket admits
one seat in both selected sessions. It is not identity-bound. 003.1 stops at
ticket issuance/delivery and authorized lookup.

## Technical Context

| Area | Design baseline |
| --- | --- |
| Runtime | Java 21, Spring Boot REST, Spring Modulith, JPA/Spring Data, MySQL and append-only Flyway migrations. |
| Client | Separate React + Vite client using versioned REST APIs; no backend-rendered booking UI. |
| Identity | Firebase verifies identity; Mland authorizes Member ownership. Guest access uses booking-contact proof or a protected Magic Link. |
| State | Persisted booking/hold/payment/ticket state; 15-minute holds and 10-minute payment links; no new global session/token store. |
| Payment | VNPay adapter boundary; signed gateway result is authoritative and payment handling is idempotent. |
| Verification | Unit/integration, contract, concurrency, callback/idempotency, migration, and Spring Modulith boundary verification. |
| Unknowns | Provider callback details, concrete cross-feature facade boundary types, Magic-Link client exchange/recovery, internal-role mapping, and production rollout approval. |

## Constitution Check

| Gate | Result | Plan response |
| --- | --- | --- |
| Module and data ownership | PASS | `workshopbooking` owns booking aggregate records and accesses other features only through approved facades/events. |
| Separate frontend/REST | PASS | Contract is JSON `/api/v1`; Magic Link resolves to client-readable data, never server-rendered HTML. |
| Identity and sensitive data | PASS | No participant identity, OTP, raw gateway payload, raw Magic-Link secret, or unnecessary PII is exposed/logged. |
| Data evolution | CONDITIONAL | Dev/test reset was approved only for old state data; any production/customer-data change needs an append-only preservation migration and rollout evidence. |
| Evidence and concurrency | PASS | Atomic multi-session hold, payment callback idempotency, late-payment rejection, and capacity races have explicit verification scenarios. |
| Explicit decisions | CONDITIONAL | The proposed HTTP and facade shapes below are design drafts until their owners approve them; role mapping is not inferred. |

## Module boundaries and flows

`workshopbooking` owns booking, booking-session commitment, frozen invoice,
payment attempt/receipt, booking-contact delivery state, Magic-Link record and
notification intent. It reads only a 003.2 sale-eligibility snapshot
(customer-selectability, sale state, branch, scheduled start/end and remaining
capacity), and calls the 003.2-owned semantic capacity boundary. It does not
configure sessions, assign staff, read Operations entities, check in, or
finalize attendance. After capacity commits, it uses the 003.2 delivery-safe
ticket projection for its customer delivery; it never derives attendance.
003.3 consumes frozen deposit/base-price evidence for later settlement, refund
and reschedule; 003.4 owns OTA completely.

Create flow: validate package/contact/participant count and selected sessions;
enforce Basic Ring = one session or Silverclay = two distinct sessions at the
same branch; order Silverclay sessions; reject adjacent same-day sessions;
atomically reserve `participant_count` places in every session and persist the
pending booking, invoice and expiry. Any validation/capacity failure rolls back
the whole operation. Capacity is always the one shared pool of a session.

Payment flow: start/retry only while the hold is active. The provider result
must authenticate, match the frozen reference/amount/currency, and be accepted
only once. Success asks 003.2 to commit the complete active hold set; if any
expected hold is absent or expired, nothing commits and Booking remains
unconfirmed. On success, Booking obtains the delivery-safe projection for N
tickets, stores notification intent and emits a durable downstream handoff.
Failure leaves `PENDING_PAYMENT/UNPAID`; expiry/release requests release of all
active holds exactly once.

## Contract and state design

The draft OpenAPI contract defines public browse/detail, create, payment start,
inbound payment event, contact-proof lookup, Member-owned lookup, Magic-Link
exchange/read, and pending-hold release. It intentionally excludes staff resend,
check-in, cancellation/refund, reschedule, final payment, and OTA routes.

Booking service states are `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, and
`CANCELLED`; financial states are `UNPAID` and `DEPOSIT_PAID` here. `FAILED`
belongs to a payment attempt. `FULLY_PAID`, refund, attendance and ticket-use
states are reserved/owned downstream. A client Idempotency-Key protects create,
payment-start and release commands; provider event identity protects callbacks.

Magic Link is a one-time opaque secret delivered to the booking-contact email.
Only a protected hash and expiry/revocation state are persisted. The proposed
client exchange returns a short-lived read-only booking access result; exact
credential transport, retention cleanup and frontend recovery remain approval
dependencies, not an authorization to invent a shared auth system.

## Data, migration, and operational constraints

The model in [data-model.md](data-model.md) adds a booking-to-session commitment
instead of a cart/item model. Its delivery projection represents one 003.2
ticket per participant and its entitled sessions, allowing one Silverclay QR to
be checked independently in either session without storing a participant
identity. Monetary snapshots are integer VND, with even `price_per_participant`.

No existing applied migration may be edited. Development/test reset is allowed
only after environment preflight. Production or real-customer data requires a
separate append-only migration, preservation test, rollback/recovery plan, and
owner approval. Deployment topology, origins, provider credentials, monitoring
ownership, SLOs, and role mapping remain outside this plan's decision authority.

## Verification plan

Use [quickstart.md](quickstart.md) to verify: one-session Basic Ring; two-session
Silverclay with shared capacity; all-or-nothing failure; same-branch/ordering/
same-day-gap rules; 50% exact VND deposit; valid/invalid/duplicate/late gateway
events; ticket issuance and lookup; Magic-Link revocation; hold expiry; and
authorization-safe lookup. Implementers must also evidence MySQL migration
coverage, API contract validation, and `ApplicationModules.verify()`.

## Design risks and handoff conditions

The deliberately rejected shortcut is a generic cart or a separate Silverclay
slot/capacity type: both contradict the approved one-package booking and shared
slot model while adding avoidable allocation complexity. The remaining blockers
are not product decisions to guess:

1. **Resolved semantically by 003.2:** Booking reads the sale-eligibility
   snapshot; reserve/commit/release are all-or-nothing; `SALES_PAUSED` rejects
   new holds while preserving unexpired ones; and ticket delivery uses only the
   safe ticket/session-entitlement projection. Concrete facade boundary types
   remain an approval dependency, not an implementation detail to invent.
2. Payment-provider contract, callback authentication and recovery rules need
   provider-approved detail before implementation.
3. Magic-Link client transport, expiry cleanup, revocation recovery and frontend
   ownership require an approved shared credential/state contract.
4. Production migration/rollout and internal support role mapping require their
   respective owners' approval.

This plan is ready to inform task drafting only after those dependencies are
represented as approved contracts or explicit blocked tasks; it does not itself
approve code, schema migration, deployment, or the proposed public API names.
