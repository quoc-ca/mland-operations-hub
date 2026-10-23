# Feature Specification: Workshop OTA Connectivity

**Feature**: `005-workshop-ota-connectivity`  
**Status**: Future draft — not implemented  
**Source**: FE-03 in `../../research.md`

## Scope

This feature connects Klook, GetYourGuide and future OTAs to Mland's workshop
availability, booking, ticket and reconciliation model. Mland remains the source
of truth for sessions and capacity. Each OTA has its own adapter; the core
workshop domain is not made dependent on one channel's contract.

## Initial boundaries

- Publish only `PER_PERSON` packages. Workshop items, co-maker pricing and
  `BASE_PLUS_EXTRA` stay direct-Mland only.
- Mland atomically validates capacity and creates one channel booking/ticket set
  from an idempotent OTA booking request.
- Every adapter validates inbound callbacks, deduplicates external events and
  uses retry/outbox plus a reconciliation queue for failures.
- OTA customer payment/refund is recorded separately from OTA payout owed to
  Mland. The ledger is append-only.
- Laser engraving is included free for every OTA booking. Other options are
  optional Mland direct add-ons: Staff quotes and obtains consent first, then
  records a separate Mland counter bill without changing OTA retail price,
  commission, payout or refund.

## Go-live gates

- End-to-end confirmation that a booking for N people creates and delivers N
  Mland-scannable tickets.
- Validated availability, duplicate booking, cancellation/refund callback,
  retry and reconciliation journeys for that OTA.
- Confirmed commercial terms for price semantics, fees/tax, currency/FX and
  payout. Klook remains `PENDING_COMMERCIAL_CONFIRMATION` until this is known.

## Functional requirements

- **FR-001**: The system MUST maintain an adapter boundary for each OTA and
  preserve one Mland source of truth for capacity and tickets.
- **FR-002**: The system MUST process repeated external requests idempotently
  without creating duplicate bookings, tickets or capacity deductions.
- **FR-003**: The system MUST keep channel customer payment, supplier payout and
  Mland direct OTA add-on bills financially separate.
- **FR-004**: The system MUST record authenticated external references and audit
  failed/reconciled events without retaining credentials in logs.

## Out of scope

Publishing an OTA before its commercial/onboarding terms and ticket delivery are
validated; OTA support for co-maker workshop items; individual participant PII,
transfer or direct ticket delivery.
