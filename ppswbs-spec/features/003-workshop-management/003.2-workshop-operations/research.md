# Research: Workshop Operations Design

**Date created**: 2026-10-24
**Date updated**: 2026-10-26
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Decision 1 — Operational ownership

**Decision:** `workshopoperations` owns branch, slot, session, capacity hold,
attendance ticket, QR/ticket-code verification and attendance history.
`workshopbooking` owns the commercial booking, payment, invoice and ticket
delivery. Booking calls an Operations facade; Operations never calls Booking.

**Why:** Capacity and check-in need one transactional source of truth. If both
modules own a session or ticket, a reservation and a scan can disagree or create
a circular module dependency. The old code has precisely this misleading
starting point: session and a booking-level ticket live in Booking. It must be
treated as legacy, not extended.

## Decision 2 — Cross-module contracts

**Decision:** Use named facades only. Booking requests a capacity hold,
confirms/releases it and requests ticket issue after the relevant commercial
state is reached. Booking then reads a delivery-safe ticket view to send the
buyer QR codes. Operations consumes account/personnel decisions through a
separate facade: canonical actor role, active status and location scope.
Operations then applies its own action rules with the target branch and explicit
Staff-session assignment.

**Why:** A facade preserves module ownership, allows the account module to
change its account model later, and makes Module/Modulith tests meaningful. It
also avoids assuming that a legacy `Member` record is a Staff or Manager record.

## Decision 3 — Capacity consistency

**Decision:** Capacity is measured in attendance tickets. A transaction locks
the affected session/commitment rows while creating, confirming, expiring or
releasing a hold. A reduction is rejected below confirmed tickets plus unexpired
holds.

**Why:** Application-only “check then save” logic oversells under simultaneous
checkout requests. JPA pessimistic locking or an equivalent optimistic-version
retry is required; the concrete repository implementation may select either,
but it must preserve the same atomic invariant.

## Decision 4 — Individual ticket security

**Decision:** One attendance ticket represents one person. QR carries an opaque
reference; ticket code is unique and typed fallback. The persisted value must
not expose internal IDs or customer data. QR/ticket verification has the same
session, branch, time, status and actor checks as a scan.

**Why:** A booking can contain tickets for different slots or branches. A
booking code therefore locates a commercial record only; it cannot select an
individual person for normal check-in.

## Decision 5 — Time and finalisation

**Decision:** Schedule fields are stored as instants and evaluated in
`Asia/Ho_Chi_Minh`. `check_in_opens_at` defaults to 60 minutes before start and
can be explicitly overridden. It does not close automatically. An authorised
operator manually finalises the whole session after the actual workshop end;
remaining unchecked tickets become `NO_SHOW`.

**Why:** The shop permits late arrivals but needs a definite point to close
attendance. A manual end supports workshops that finish early or use their
30-minute buffer.

## Decision 6 — Audit and sensitive data

**Decision:** Operations writes immutable audit entries for state transitions,
assignments, lead handovers, overrides, corrections and cancellation requests.
They contain actor, action, timestamp, resource, standard reason code and only
safe metadata; reason codes that require a note/evidence reference enforce it.

**Why:** Check-in exceptions and finalisation corrections affect a guest's
access and later settlement. Audit must be append-only and cannot rely on an
editable session note. Shared audit utilities may be technical plumbing, but
the operation and policy stay in this module.

## Decision 7 — Legacy migration

**Decision:** Add the next available Flyway migration; never edit an applied
migration or reuse a development reset for production. Preserve legacy source
references and require an operator-approved mapping/reissue run before moving
existing live bookings into individual tickets.

**Why:** Legacy data may have a single booking ticket despite a participant
count. Automatically splitting, revoking or reissuing those tickets can deny
entry to a paid customer. Migration safety is a release gate, not permission to
invent a customer-facing policy.

## Critical Design Review

The tempting shortcuts below are rejected:

- **Keep sessions/tickets in Booking:** this creates two owners for capacity and
  forces Operations to reach into Booking during check-in.
- **Put the new aggregate in `common`:** common infrastructure must not own
  workshop business rules.
- **Trust the legacy global security configuration:** every new route must make
  canonical server-side account facts plus an Operations action rule; a
  client-provided branch or user id
  is not authority.
- **Reuse one ticket per booking:** it contradicts individual QR check-in and
  permits the wrong person to be marked present.
- **Silently transform historic tickets:** it hides a customer-impacting
  migration behind schema work. It must be reviewed and auditable.

These choices intentionally add explicit contracts and migration work, but they
remove the much higher risk of oversell, cross-branch access, and irreconcilable
attendance history.
