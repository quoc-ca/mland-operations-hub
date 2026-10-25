# Feature Specification: Customer Workshop Booking Core

**Date created**: 2026-10-06
**Date updated**: 2026-10-25
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.1-workshop-booking`
**Status**: Draft — blocked from implementation pending rebase decisions
**Input**: Customer-facing workshop booking lifecycle; the current aggregate
model is defined in [REBASE-NOTES.md](REBASE-NOTES.md).
**Governing baseline**: [spec-general.md](../../../spec-general.md) and the
project constitution. The React + Vite frontend consumes versioned REST APIs;
the Java backend does not render application UI.

## Scope and source alignment

This feature defines the first customer-facing Workshop Booking slice for Guests
and Members. It owns one booking for one selected workshop package/course,
capacity holds for that package's required session(s), attendance-ticket
issuance, deposit payment, and booking lookup. It does not provide a generic
cart for unrelated workshop packages.

> **Rebase required — 2026-10-24.** The original V1 requirements below were written
> for a single-session booking with a single `participant_count`. The V1 model
> is a **booking** for one workshop package/course; the package may include one
> or more required sessions, while each actual attendee has one independent
> **attendance ticket** and QR. V1 does not provide a generic multi-package
> cart. Pricing is strictly per participant; a shared-item/co-maker price is
> out of scope. The legacy data model, API
> contract, plan, and tasks in this directory must be rebased before they guide
> any further implementation. See [REBASE-NOTES.md](REBASE-NOTES.md) and
> [REVIEW-2026-10-24.md](REVIEW-2026-10-24.md). No legacy requirement in this
> file authorizes server-rendered UI, a single-session data model, or code work.

The source use cases are:

- `01-browse-workshop-packages.md`
- `02-view-workshop-package-details.md`
- `03-create-workshop-booking.md`
- `04-pay-workshop-deposit.md`
- `05-look-up-booking-details.md`

Workshop schedule configuration, slot capacity configuration, staff assignment,
participant check-in, attendance finalization/no-show processing, post-session
settlement, customer cancellation/refund, manual rescheduling, and external OTA
synchronization are explicitly deferred to later features. This feature consumes
published package, session, capacity, and supported-material information; it
does not own their operational configuration workflows.

Workshop ring-design request, analysis, review, and outcome are a future,
separately owned workflow. They are not a create-booking input, aggregate state,
or acceptance path for 003.1 V1. A later feature may reference a booking only
through an approved boundary.

## Clarifications

### Session 2026-10-07

#### Approved resolution for cancellation and state scope

The earlier cancellation-deadline and paid-booking cancellation decisions are
superseded. In this feature, `CANCELLED` is used only when a Guest or Member
explicitly releases an active `PENDING_PAYMENT` hold. The hold is released
without refund processing and no QR ticket is issued. Post-confirmation
cancellation, refund, and internal operational cancellation are deferred to
a later feature. The implemented state enum is therefore
`PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, and `CANCELLED`; `CHECKED_IN`,
`COMPLETED`, and `NO_SHOW` remain reserved states.

- Q: Booking should use which business and financial states? → A: Service status and financial payment status are separate. The agreed service statuses are `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CHECKED_IN`, `COMPLETED`, `CANCELLED`, and `NO_SHOW`; booking financial statuses are `UNPAID`, `DEPOSIT_PAID`, `FULLY_PAID`, `REFUND_PENDING`, and `REFUNDED`. `FAILED` is reserved for `PaymentAttempt`.
- Q: Is email confirmation required before payment? → A: No. The booking does not require email confirmation before payment; after successful payment, the system generates one QR ticket for each purchased participant seat and sends the confirmation notification.
- Q: Who owns the OTP step shown during payment? → A: The Payment Gateway owns OTP or equivalent payment authorization; Workshop Booking does not issue, store, or verify the OTP. It only processes the gateway's authenticated payment result.
- Q: Does the External Registration System integration belong to this feature? → A: No. This feature records or emits the confirmed-booking handoff for a later synchronization feature; external slot deduction, retry and reconciliation are out of scope.
- Superseded clarification: post-confirmation cancellation, cancellation deadlines, refunds, and internal operational cancellation are deferred to a later feature.
- Q: Which lifecycle states are implemented now? → A: This feature manages the booking through `CONFIRMED`, plus `CANCELLED` only for explicit release of an active pre-payment hold. `CHECKED_IN`, `COMPLETED`, and `NO_SHOW` remain domain states for later features and are not implemented in this feature.
- Q: What happens after a failed payment attempt? → A: The booking remains `PENDING_PAYMENT` with financial status `UNPAID` while the hold is active, and the visitor may retry until the hold expires.
- Q: Does cancellation/refund belong to this feature? → A: Post-confirmation cancellation and refund are deferred to a later feature. This feature supports only explicit Guest/Member release of an active `PENDING_PAYMENT` hold, which changes the booking to `CANCELLED` without refund processing.
- Q: Which lookup methods belong to this feature? → A: Guest self-service lookup by booking code plus contact phone and a post-payment email Magic Link are included. In-store Staff/POS QR check-in and manual customer verification are deferred to a later check-in feature. Members use authenticated ownership lookup.
- Q: What proof may a Guest use for self-service lookup? → A: The Guest must provide the booking code and either the normalized contact phone or the normalized contact email captured for that booking.
- Q: When does a post-payment Magic Link expire? → A: The Magic Link remains valid until the associated workshop ends, then expires automatically.
- Q: How should the existing email-confirmation code be handled? → A: Remove it from the current Workshop Booking flow. Post-payment confirmation notification and Magic Link replace pre-payment email confirmation.
- Q: Must legacy booking data be preserved during state migration? → A: No for the current development/test database; it may be reset and recreated with the new state model. This decision does not authorize resetting production or real customer data.
- Q: Where is a failed payment state recorded? → A: `FAILED` belongs to `PaymentAttempt`; the booking remains `PENDING_PAYMENT` with financial status `UNPAID` while its hold is active and can be retried.
- Q: How should the booking status enum be migrated in the resettable development/test database? → A: Replace the legacy enum with `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, and `CANCELLED` for this feature. `CHECKED_IN`, `COMPLETED`, and `NO_SHOW` remain reserved and are not transitioned here.
- Q: May a Guest look up a pending booking? → A: Yes. A pending lookup is a resume-payment journey with a remaining-hold countdown and no QR ticket; a confirmed lookup is a read-only booking/ticket journey with QR details and no countdown.
- Q: What status applies when a Guest releases an active pending hold? → A: Add `CANCELLED` for an explicit customer release before payment; release the hold immediately and do not issue a QR ticket. This is not post-confirmation cancellation/refund.
- Q: What should the Guest see after releasing a pending hold? → A: The cancelled booking remains viewable without payment or QR actions and offers a new-workshop booking action.
- Q: May a Member retry payment for a pending booking? → A: Yes. An authenticated Member may resume or retry payment for a booking owned by that Member; the state-specific projection is the same as for a Guest.
- Q: Which contact fields are required when creating a booking? → A: Guests must provide both email and phone. Members receive both fields auto-filled from their profile and may edit them when booking for another person; the booking stores the submitted contact snapshot.
- Q: Who receives the post-payment Magic Link? → A: Both Guest and Member bookings send the Magic Link to the submitted contact email snapshot, including when a Member books for another person.
- Q: How are tickets issued for a multi-participant booking? → A: One booking has one aggregate invoice and one deposit payment, but successful confirmation issues exactly `participant_count` independent QR tickets. Until a later participant-registration feature exists, the submitted booking contact receives the ticket set and may distribute it to the group.
- Q: Does V1 collect an identity or contact method for every ticket holder? → A: No. V1 uses `BOOKING_CONTACT_RECEIVES_ALL_TICKETS`: it collects only the booking contact and `participant_count`, then delivers the full ticket set and Magic Link to that contact. Each ticket is independently usable at later check-in but is not assigned to a participant identity. `EACH_PARTICIPANT_RECEIVES_TICKET`, participant email delivery/claim, and ticket-transfer workflows are explicitly deferred to a separate feature.
- Q: What should the confirmation email contain? → A: It contains the complete QR Check-in ticket set directly and a Magic Link to the read-only booking details.
- Q: What should the QR Check-in payload contain? → A: It contains only a signed opaque token/reference; it contains no PII, payment data, raw Magic Link token, or other sensitive payload.
- Q: How is a later Staff check-in performed when QR scan is unavailable? → A: QR is the primary ticket credential; an individual `ticket_code` is the manual fallback. A `booking_code` only retrieves the booking/ticket set and cannot itself check in an unspecified ticket. Any future override must select a remaining ticket and be audited.
- Q: Who may perform a later ticket check-in or override? → A: Staff may perform normal QR/`ticket_code` check-in under `workshop.checkin`. Only Manager or specially authorized Staff with `workshop.checkin.override` may override a ticket exception; the override needs a reason and audit evidence. This is a later check-in feature, not a transition implemented by feature 002.
- Q: When may a later ticket check-in occur? → A: Each session has a Manager-configurable check-in window. Normal Staff check-in is allowed only within that window; an outside-window check-in needs authorized override and audit evidence. This is configuration for a later check-in feature, not a transition implemented by feature 002.
- Q: How are later no-shows recorded? → A: The system does not mark a ticket `NO_SHOW` automatically when the check-in window closes. Staff/Manager finalize attendance after the session; remaining unchecked tickets become `NO_SHOW` and the booking becomes `COMPLETED`. Any correction after finalization requires Manager-authorized audit. This is a later attendance feature, not a transition implemented by feature 002.
- Q: When does the QR Check-in ticket expire? → A: It remains valid until the associated workshop ends and may be revoked when required.
- Q: What happens to the QR when a confirmation email is resent? → A: Reuse the same active ticket set; resending email does not create, revoke or rotate any ticket.
- Q: Who may request a confirmation email resend? → A: Guest and Member use
  their respective lookup or ownership proof. Staff assigned to the booking
  branch and Managers responsible for that branch may resend; operational sends
  are audited.
- Q: What happens if confirmation email delivery fails after payment? → A: The booking remains `CONFIRMED`; the system stores a notification retry intent and retries automatically. Email delivery is not a condition for booking confirmation.
- Q: How should automatic confirmation-email retry stop? → A: Retry at most
  three times with backoff, then mark the notification for follow-up by Staff
  assigned to the booking branch or Managers responsible for that branch.

### Session 2026-10-25

#### Approved resolution for V1 participant pricing

**Decision owner: Hồ Hoàng Quốc.** 003.1 V1 supports `PER_PERSON` pricing only.
The frozen total for a booking item is its selected `participant_count` multiplied
by the package's frozen price per participant. A participant is one purchased
seat and receives one independent ticket after valid payment confirmation.

- Q: Does V1 retain `BASE_PLUS_EXTRA` or a selectable pricing model? → A: No.
  `BASE_PLUS_EXTRA`, `base_price`, included-participant counts, extra-person
  prices, and a `pricing_model` selection are out of scope for V1.
- Q: How is a two-session package such as Silverclay priced? → A: Its displayed
  per-participant package price covers every required session. For four
  participants, the total is four times that price; the booking holds four
  participant seats in each required session and issues four tickets after a
  valid payment confirmation.
- Q: Is a rare co-maker request an online material add-on? → A: No. A material
  add-on does not create a participant, ticket, or capacity commitment. A rare
  request for several people to make one physical item is handled manually by
  Staff outside the V1 customer booking flow.
- Q: Does V1 provide a cart for unrelated workshop packages? → A: No. One
  booking purchases one package/course only. A package may contain one or more
  required sessions; a Silverclay two-session package is one purchase, not two
  cart items or two independent bookings.
- Q: Which packages are available through an OTA in V1? → A: Only basic ring
  packages completed in one session. The two-session Silverclay package is
  direct-Mland only; OTA does not select, hold, or sell its session pair.
- Q: How does a direct-Mland Silverclay booking select its two sessions? → A:
  The customer selects exactly two distinct published, customer-selectable
  workshop sessions. Sessions use the same common slot concept as other
  workshop packages; the earlier session is automatically treated as session 1
  and the later session as session 2. Both capacity holds succeed or the booking
  is not created.
- Q: How are QR tickets issued for a confirmed Silverclay booking? → A: Each
  participant receives one independent QR ticket that is valid for both selected
  Silverclay sessions. Later attendance is recorded separately for each
  ticket/session pair; this feature does not implement check-in.
- Q: May the two selected Silverclay sessions belong to different branches? →
  A: No. In V1, both selected sessions must belong to the same branch.
- Q: What minimum separation applies between the two selected Silverclay
  sessions? → A: If both sessions are on the same local calendar day, at least
  one scheduled workshop slot must occur between them. Sessions on different
  local calendar days are valid even when the first is the final slot on one day
  and the second is the first slot on the next day.
- Q: With Silverclay, may a customer combine any two sessions at booking? → A:
  Yes. The customer may combine any two published, customer-selectable workshop
  sessions. A session is always a common workshop slot; V1 has no
  Silverclay-only slot type or preconfigured session-pair/cohort requirement.
- Q: Must the same physical person attend both sessions under one Silverclay
  ticket? → A: No. In V1, a ticket reserves one seat in both sessions but is
  not identity-bound or verified against a physical attendee. The booking
  contact receives and is responsible for distributing the ticket set.
- Q: When Basic Ring and Silverclay select the same workshop session, do they
  share its capacity pool? → A: Yes. Each participant seat reserved by either
  package consumes one place from the same configured session capacity.
- Q: How is a 50% deposit handled if the booking total is an odd VND amount? →
  A: V1 permits publication only of an even-VND price per participant, so every
  booking total and its 50% deposit are exact whole-VND amounts.
- Q: What maximum gap applies between two selected Silverclay sessions? → A:
  None in V1. The sessions may be any distance apart while each remains
  published and customer-selectable, provided they meet the same-branch and
  minimum-separation rules.

- Q: When is signed QueryDR used if a valid IPN has not been processed? → A:
  V1 runs a bounded automatic QueryDR recovery while the payment hold remains
  active and only for an attempt without a terminal IPN outcome. A QueryDR
  success still passes all normal booking, amount, currency, signature,
  idempotency and capacity-commit validation; browser return is never evidence.

- Q: Which VNPAY signature policy applies in V1? → A: V1 accepts only the
  approved VNPAY HMAC-SHA512 checksum configuration. It verifies the checksum
  before any booking or capacity processing; missing or invalid checksum is
  rejected and cannot confirm a booking.

- Q: How are duplicate and out-of-order valid payment results handled? → A:
  An accepted success is terminal. Repeated IPN/QueryDR success is acknowledged
  idempotently without another settlement, capacity commit or ticket delivery;
  a failure result arriving later cannot downgrade the confirmed booking and is
  retained only as safe payment-attempt evidence.

- Q: What payment evidence is retained for idempotency and reconciliation? → A:
  Store only the redacted minimum: merchant booking/payment reference, VNPAY
  transaction reference/number when supplied, amount, currency, outcome/status
  codes, provider/received time and correlation/idempotency evidence. Do not
  retain raw IPN/QueryDR payload, checksum or signing secret.

### Session 2026-10-25

- Q: How do 003.1, 003.2, and 003.3 interact for booking-critical work? → A:
  Each owning feature exposes its own synchronous, in-process facade and
  explicit boundary types. Reserve, commit, release, settlement-readiness,
  required-session replacement, refund consequence, booking financial
  projection, and `FULLY_PAID` conclusion use those facades because their
  caller needs an immediate all-or-none answer. Events occur only after the
  local transaction commits for notification, OTA, or other downstream work;
  they never decide booking confirmation or capacity. No caller accesses another
  feature's controller, HTTP DTO, entity, repository, or internal service.
- Q: How granular is the inter-feature facade surface? → A: V1 exposes one
  owner-specific facade per feature: `WorkshopOperationsFacade` (003.2),
  `WorkshopBookingFacade` (003.1), and `WorkshopSettlementFacade` (003.3).
  Each facade has explicit immutable command/query boundary types; it is not a
  shared `WorkshopManagementFacade` and does not expose split repository-like
  facades for every internal concern.
- Q: How are repeated inter-feature commands made safe? → A: Every command with
  a business side effect carries a caller-supplied opaque `operationReference`
  that identifies exactly one business action. A retry uses the same reference
  and semantically identical command; the owner returns the original outcome.
  Reusing a reference with different command content is rejected safely.
  Correlation reference remains available for trace/audit and is not a substitute
  for idempotency.
- Q: How do facades report a business refusal? → A: Each command returns an
  immutable typed outcome. `ACCEPTED` represents the completed all-or-none
  business result; `REJECTED` carries an approved safe reason such as
  `INSUFFICIENT_CAPACITY`, `SALE_NOT_ELIGIBLE`, `HOLD_EXPIRED`, `NOT_READY`, or
  `INVALID_REPLACEMENT`. Exceptions are reserved for unexpected technical
  failure and must not be used to encode normal business refusals.
- Q: Which inter-feature operations are public in V1? → A: Only the minimum
  owner operations in the shared
  [003 internal facade contract](../contracts/workshop-interfeature-facades.md)
  are public: Operations owns eligibility, hold, ticket-projection, settlement-
  readiness, replacement, refund-consequence and cancellation-completion;
  Booking owns confirmed-financial projection, final-balance conclusion and
  ticket redelivery; Settlement owns cancellation-handoff creation/resolution.
  Any other cross-feature access is prohibited until separately approved.

- Q: When should bounded automatic QueryDR recovery run during the payment
  hold? → A: For a payment attempt without a terminal IPN outcome, run it at
  payment-link expiry (minute 10) and once one minute before the 15-minute hold
  expiry (minute 14). It never runs after hold expiry and never treats browser
  return as payment evidence.

- Q: How does Booking become `FULLY_PAID` after final settlement? → A: 003.3
  sends 003.1 only an idempotent semantic conclusion that the booking's final
  balance is resolved, with a safe correlation/reference. 003.1, as the Booking
  owner, changes its financial status to `FULLY_PAID` exactly once. It receives
  no raw VNPay payload or final-payment detail and does not verify final payment
  or calculate final balance. The conclusion may follow verified final payment
  or an authorized audited deferred-payment resolution; merely creating or
  sending a deferred-payment link is not such a conclusion.

- Q: Should the post-payment Magic Link be reusable or single-use? → A:
  Reusable until the associated workshop/package ends or the link is revoked.
  It grants only read-only access to that booking and its ticket set; it never
  authenticates a Member or grants Member account access.

- Q: What happens to an active Magic Link when the post-payment booking
  confirmation email is resent? → A: Resend reuses the existing active Magic
  Link. Revocation or rotation is a separate authorized incident-response
  action; resending neither creates another active link nor changes QR tickets.

- Q: How can a booking contact recover booking access after losing access to
  the submitted email? → A: Use Guest lookup with the booking code and stored
  contact phone to open the same read-only booking/ticket view. V1 neither
  changes the contact email automatically nor sends a Magic Link to a new email.

- Q: Is a valid Magic Link alone sufficient to open the allowed booking and
  ticket projection? → A: Yes. It is a bearer credential for that read-only
  projection until expiry or revocation, not Member authentication. The booking
  contact may forward it; suspected exposure is handled by authorized revoke or
  rotation rather than another proof on every use.

- Q: What is the minimum data visible through a valid Magic Link? → A: Booking
  code, booking/deposit status, package, branch, required-session schedule,
  participant count, and QR tickets/ticket codes only. It excludes contact
  email/phone, VNPay reference, detailed monetary amounts, Staff, attendance,
  and audit evidence.

- Q: Who may resend a post-payment booking confirmation email in V1? → A:
  Guest and Member use their existing self-service proof. Staff assigned to the
  booking branch and Managers responsible for that branch may resend it with
  audit evidence. Resend does not change the booking, payment, ticket set,
  contact email, or active Magic Link.

- Q: Does V1 include internal Magic Link incident handling? → A: Yes. V1
  includes both revoke and rotate for a suspected exposed Magic Link. Rotate
  revokes the existing link and creates a replacement link for the submitted
  booking-contact email; it does not change the booking, payment, ticket set,
  contact email, or capacity.

- Q: Who may revoke or rotate a Magic Link in V1? → A: Only a Manager
  responsible for the booking branch. Staff may resend a confirmation email but
  may not revoke or rotate its Magic Link; the `ADMIN` role alone does
  not grant booking-incident authority.

- Q: Do Magic Link revoke and rotate require a reason and audit evidence? → A:
  Yes. Manager supplies a reason; audit records actor reference, booking code,
  branch, action, reason, outcome, and correlation reference. It does not
  retain a raw Magic Link, token, or QR payload.

- Q: Who handles a signed VNPay success received after hold expiry in V1? → A:
  Only a Manager responsible for the booking branch may view the safe evidence
  and record or escalate follow-up. The Manager may not confirm or reactivate
  the booking, restore hold/capacity, issue tickets, or create a payment link.
  Refund, reschedule, and final settlement remain in 003.3.

- Q: What happens if an internal actor is locked, inactive, loses entitlement,
  or loses booking-branch scope before the action is submitted? → A: Server
  rechecks authorization at action time, denies without side effects, returns a
  safe error, and records a minimal denied-attempt audit for sensitive internal
  booking actions.

- Q: How are resend booking-confirmation rate limits defined in V1? → A:
  Server-enforced limits are configurable and separate for self-service and
  internal resend. A rejected request sends no email and creates no ticket or
  Magic Link; it returns a safe error. Operational approval selects the limits.

- Q: After a Manager rotates a Magic Link because of suspected exposure, does
  the system immediately send the replacement to the booking-contact email? →
  A: Yes. Rotation revokes the old link, creates a replacement, and queues its
  immediate delivery to the submitted booking-contact email. A delivery failure
  does not restore the old link; notification retry and Guest booking-code plus
  stored-phone recovery remain available.

- Q: Which resend and late-payment follow-up actions require audit evidence in
  V1? → A: Every internal resend, Magic Link revoke/rotate, and Manager
  late-payment follow-up is audited; self-service resend creates minimal audit
  evidence. An audit holds only actor type or identity reference when available,
  booking code, applicable branch, action, outcome, correlation reference, and
  timestamp. It excludes contact proof, raw Magic Link, QR, and token material.
  Notification delivery attempts and retries are separate operational logs, not
  business audit evidence.

- Q: When a customer opens a booking Magic Link, how does the React client pass
  the bearer value and retain the resulting access? → A: The email opens a React
  booking route with the opaque value in the URL fragment. React immediately
  sends it by HTTPS POST to obtain the read-only projection, clears the fragment,
  and retains only that projection in memory. Each later link opening exchanges
  again; expired or revoked access offers Guest booking-code plus stored-phone
  recovery. This creates no Member login, shared session, cookie, or persistent
  client credential storage.

- Q: Which environments may reset legacy booking data during the 003.1 rollout?
  → A: Only development/test environments with no booking or customer data that
  must be retained may reset after preflight. Staging, production, or any
  environment holding booking/customer data must use an append-only preservation
  migration, upgrade test, and recovery/rollback plan.

- Q: How does 003.1 obtain booking-critical capacity outcomes from 003.2? →
  A: Through the exposed synchronous 003.2 boundary for sale eligibility,
  reserve, commit, release, and the delivery-safe ticket/session-entitlement
  projection. Events are permitted only after local confirmation for
  non-critical notification or downstream handoff; they do not decide capacity
  or booking confirmation.

## User Scenarios & Testing

### User Story 1 - Discover a workshop package (Priority: P1)

As a Guest or Member, I want to browse published workshop packages and view a
package's details so that I can decide what workshop to book.

**Why this priority**: A customer cannot begin a booking without a valid,
published package and its current price and participation information.

**Independent Test**: A visitor can browse available published packages, open one
package, and see enough information to decide whether to continue to booking.

**Acceptance Scenarios**:

1. **Given** published packages exist, **When** the visitor browses workshops,
   **Then** the system displays only packages available for customer selection.
2. **Given** a package is selected, **When** the visitor opens its details,
   **Then** the system displays its description, price, participant constraints,
   supported design/material information, and available booking entry point.
3. **Given** a package is unpublished or unavailable, **When** the visitor tries
   to open it, **Then** the system does not allow a new booking from that package.

---

### User Story 2 - Create a pending workshop booking (Priority: P1)

As a Guest or Member, I want to select a package/course and participant count
so that the system can prepare my deposit payment and reserve every session
required by that package.

**Why this priority**: Creating a valid pending booking is the core value of the
feature and provides the transaction context for payment.

**Independent Test**: A visitor can select an available package/course, submit
valid booking-contact details and a participant count, and receive a pending
booking with an exact deposit amount and temporary capacity hold in every
required session.

**Acceptance Scenarios**:

1. **Given** a published package/course and every required session has
   configured remaining capacity, **When** the visitor submits valid booking
   details, **Then** the system creates one payment-pending booking and package
   invoice.
2. **Given** a Member has verified profile details, **When** the Member confirms
   them for the booking, **Then** the system uses the applicable profile data
   without requiring duplicate entry.
3. **Given** the visitor is a Guest, **When** required booking-contact details or
   participant count are missing or invalid, **Then** the system identifies the
   fields to correct and creates no booking.
4. **Given** the visitor is a Member, **When** the booking form opens, **Then**
   email and phone are auto-filled from the Member profile and remain editable
   for booking on behalf of another person.
5. **Given** the requested participant count exceeds remaining capacity, **When**
   the visitor submits the booking, **Then** the system rejects it and asks the
   visitor to choose another session or reduce the participant count.

---

### User Story 3 - Pay the workshop deposit (Priority: P1)

As a Guest or Member, I want to pay the required workshop deposit so that my
booking is confirmed and I receive a booking reference and check-in ticket.

**Why this priority**: Payment confirmation is the business transition from a
temporary pending booking to a confirmed booking.

**Independent Test**: A pending booking can be paid through the configured
payment flow and becomes confirmed only after a valid, signed, non-duplicate
payment confirmation is received.

**Acceptance Scenarios**:

1. **Given** a pending booking with an active capacity hold, **When** the visitor
   starts payment, **Then** the system requests the deposit equal to 50% of the
   frozen total for the selected participant seats.
2. **Given** a valid signed payment confirmation with the expected booking
   reference and amount, **When** the system receives it within the allowed
   window, **Then** it records one deposit transaction, sets payment status to
   `DEPOSIT_PAID`, confirms the booking, and issues one independent QR ticket
   for every purchased participant seat.
3. **Given** payment authorization fails or the visitor cancels at the payment
   gateway while the hold is active, **When** the visitor retries payment,
   **Then** the system keeps the booking `PENDING_PAYMENT` with financial status
   `UNPAID` until success or hold expiry.
4. **Given** a browser return redirect without an approved signed confirmation,
   **When** the system receives the redirect, **Then** it does not confirm the
   booking.
5. **Given** a duplicate payment event, **When** the system receives it, **Then**
   it does not create a second transaction or change the settled result.
6. **Given** payment is invalid, failed, pending, or expired, **When** the result
   is processed, **Then** the booking remains unconfirmed and the temporary hold
   is released when its expiry is reached.

---

### User Story 4 - Look up a booking (Priority: P2)

As a Guest or Member, I want to look up my booking so that I can review its
status, workshop information, payment state, and check-in ticket.

**Why this priority**: Lookup provides recovery and confidence after booking or
payment, especially for Guests who do not have Member history.

**Independent Test**: A visitor with valid lookup information can retrieve the
appropriate booking summary without exposing another customer's booking.

**Acceptance Scenarios**:

1. **Given** a valid booking lookup request, **When** the visitor submits it,
   **Then** the system displays the booking status, package, schedule, participant
   summary, payment state, and available ticket information.
2. **Given** lookup information does not identify a booking, **When** the visitor
   submits it, **Then** the system returns a safe not-found outcome without
   revealing whether another booking exists.
3. **Given** a confirmed booking has participant QR tickets, **When** the visitor
   views the booking, **Then** exactly one ticket per purchased participant seat
   is available for later individual check-in.
4. **Given** a Guest has a confirmed booking, **When** the Guest submits the
   booking code and either the contact phone or contact email used during
   booking, **Then** the system
   displays the authorized booking details, branch, session status, and ticket set.
5. **Given** a confirmation email contains a valid Magic Link, **When** the Guest
   opens it, **Then** the system displays the authorized booking details without
   requiring manual lookup input.
6. **Given** a Member is authenticated, **When** the Member opens booking history
   or a booking code belonging to the Member, **Then** the system verifies
   ownership and displays only that Member's booking.
7. **Given** a Guest looks up a `PENDING_PAYMENT` booking before hold expiry,
   **When** the booking details are displayed, **Then** the system shows the
   remaining hold countdown and a resume-payment action, but does not show a QR
   check-in ticket.
8. **Given** a Guest looks up a `CONFIRMED` booking, **When** the booking details
   are displayed, **Then** the system shows the ticket set and booking/branch
   details without a payment countdown.

### Edge Cases

- A package becomes unpublished or unavailable while a visitor is completing a
  booking; the system must revalidate it before creating the booking.
- A required session has no configured capacity; the package cannot accept a
  booking.
- A Silverclay customer selects the same session twice, or one of the two
  selected sessions becomes unavailable: the system rejects the selection and
  creates neither a pending booking nor a partial capacity hold.
- A Silverclay customer selects sessions from different branches: the system
  rejects the selection and creates neither a pending booking nor a partial
  capacity hold.
- A Silverclay customer selects two adjacent slots on the same local calendar
  day: the system rejects the selection and creates neither a pending booking
  nor a partial capacity hold. A final slot on one day followed by a first slot
  on the next day remains valid.
- Capacity is consumed concurrently by another booking; the system must not
  create a booking that exceeds the configured capacity.
- The visitor abandons or cancels before submission; no booking, invoice, or
  capacity hold is created.
- Booking creation fails after validation; no incomplete customer-facing booking
  is left behind.
- The payment link expires after 10 minutes or the capacity hold expires after at
  most 15 minutes; late confirmation must not confirm the booking.
- Cancellation and refund are deferred to a later feature and are not performed
  by this booking lifecycle.
- No-show processing is deferred to a later feature; this feature does not mark
  a booking `NO_SHOW` or apply no-show financial policy.
- A payment notification has an invalid signature, wrong amount, wrong booking
  reference, or repeated event; it must not settle the invoice twice.
- Confirmation notification delivery fails after payment; the booking remains
  confirmed and the failure is available for operational retry.
- A lookup request is malformed or attempts to access another booking; the system
  returns a safe error without exposing sensitive data.
- An invalid, expired, or revoked Magic Link must not reveal booking
  details; the token is stored only as a protected hash and is never logged.
- A pending booking lookup after hold expiry must not offer resume payment or
  display a QR ticket.
- Staff/POS in-store QR scanning, phone/name fallback lookup, identity-document
  checking, and `CHECKED_IN` state transitions are deferred to a later feature.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST display only published and customer-selectable
  workshop packages in the customer browsing journey.
- **FR-002**: The system MUST display the selected package's current price,
  description, participant constraints, supported design/material information,
  and booking availability information.
- **FR-003**: The system MUST allow a Guest or Member to select one published
  package/course, its offered required-session set, participant count, and
  required booking contact information before submitting a booking. It MUST NOT
  provide a generic cart for unrelated packages or require, infer, or persist a
  separate name or contact method for each participant in V1.
- **FR-003a**: Guest booking creation MUST require a valid contact email and
  contact phone. Member booking forms MUST auto-fill both fields from the Member
  profile, allow edits for booking on behalf of another person, and persist the
  submitted values as immutable booking contact snapshots.
- **FR-003b**: For a direct-Mland Silverclay package, the system MUST require
  exactly two distinct published, customer-selectable workshop sessions at the
  same branch at booking submission. A session is a common workshop slot, not a
  Silverclay-only slot type, and V1 MUST NOT require a preconfigured
  session-pair or cohort. The system MUST order the selected sessions
  chronologically as session 1 and session 2. If both selected sessions are on
  the same local calendar day, at least one scheduled workshop slot MUST occur
  between them; sessions on different local calendar days have no further
  minimum-gap requirement. V1 has no maximum gap between the two sessions while
  each remains published and customer-selectable.
- **FR-004**: The system MUST revalidate package availability, every required
  session's availability and configured capacity, participant count, and
  required information at booking submission time.
- **FR-004a**: All packages selecting the same workshop session MUST consume the
  same configured session-capacity pool. Each participant seat reserved by a
  Basic Ring or Silverclay booking consumes one place from that pool.
- **FR-005**: The system MUST reject a booking when any required session has no
  configured capacity or insufficient remaining capacity; it MUST leave no
  partial booking or capacity hold.
- **FR-006**: The system MUST create at most one customer-facing pending booking
  for a single accepted booking submission and must not leave an incomplete
  booking when creation fails.
- **FR-007**: The system MUST create one payment-pending package invoice per
  booking, freeze the total booking amount as the selected participant count
  multiplied by the selected package's price per participant, and calculate the
  required deposit as 50% of that total booking amount.
- **FR-007a**: V1 MUST permit publication only of an even-VND price per
  participant, so that the frozen booking total and its 50% deposit are exact
  whole-VND amounts; V1 MUST NOT apply a deposit-rounding rule.
- **FR-008**: The system MUST hold the requested capacity temporarily in every
  required session for a pending booking, for no longer than 15 minutes.
- **FR-009**: The system MUST release the temporary capacity hold when valid
  deposit confirmation is not received before the hold expires.
- **FR-010**: The system MUST support the configured workshop deposit payment
  flow and validate the payment signature, booking reference, amount, status, and
  duplicate-event state before settlement.
- **FR-010a**: The Payment Gateway MUST own payment authorization steps such as
  QR scanning or OTP entry; the Workshop Booking system MUST NOT capture, store,
  or independently validate gateway OTP values.
- **FR-010b**: While the payment hold remains active, the system MUST run
  bounded automatic signed QueryDR recovery only for a payment attempt with no
  terminal IPN outcome. A QueryDR result MUST pass the same signature, booking
  reference, amount, currency, status, idempotency and capacity-commit
  validation as IPN before it can confirm a booking.
- **FR-010d**: For a payment attempt with no terminal IPN outcome, automatic
  QueryDR recovery MUST run at payment-link expiry (minute 10) and once one
  minute before the 15-minute hold expires (minute 14). It MUST NOT run after
  hold expiry or treat a browser return as payment evidence.
- **FR-010c**: V1 MUST accept only the approved VNPAY HMAC-SHA512 checksum
  configuration. It MUST verify checksum validity before reading a callback or
  QueryDR result as payment evidence; a missing or invalid checksum MUST be
  rejected without booking, capacity or ticket side effects.
- **FR-011**: The system MUST confirm a booking only after one valid, signed,
  non-duplicate deposit payment confirmation is accepted, and MUST set its
  financial status to `DEPOSIT_PAID`.
- **FR-012**: The system MUST NOT treat a browser return redirect alone as valid
  payment confirmation.
- **FR-012a**: A failed or cancelled payment attempt MUST leave the booking in
  `PENDING_PAYMENT` with financial status `UNPAID` while its 15-minute hold is
  active, and the visitor MUST be allowed to retry payment within that window.
- **FR-013**: The system MUST record payment outcomes idempotently so repeated
  payment events do not create duplicate transactions or duplicate settlement.
- **FR-013a**: After an accepted successful payment result, the booking MUST
  remain confirmed. A later failure result MUST NOT downgrade it; a duplicate
  success MUST be acknowledged without a second payment settlement, capacity
  commit, ticket delivery, or notification intent.
- **FR-013b**: A signed success result received after the related hold has
  expired MUST NOT confirm or reactivate the booking, restore capacity, issue a
  ticket, or start a new payment link. The system MUST retain only safe
  reconciliation evidence. Only a Manager responsible for the booking branch
  may view that evidence and record or escalate follow-up; the Manager MUST NOT
  change booking confirmation, capacity, ticket issuance, or payment-link
  state. Any Manager follow-up action MUST create safe business audit evidence;
  delivery or provider-processing attempts remain operational logs. Refund,
  reschedule, and final settlement remain owned by 003.3.
- **FR-013c**: For payment idempotency and reconciliation, the system MUST
  retain only merchant booking/payment reference, VNPAY transaction
  reference/number when supplied, amount, currency, outcome/status codes,
  provider/received time and correlation/idempotency evidence. It MUST NOT
  retain raw IPN/QueryDR payload, checksum or signing secret.
- **FR-014**: The system MUST generate a booking code and exactly
  `participant_count` independent QR check-in tickets after successful deposit
  confirmation. One booking/invoice/payment transaction settles the whole ticket
  set; each ticket represents one purchased participant seat.
- **FR-014f**: For a confirmed Silverclay booking, each issued participant QR
  ticket MUST be valid for both selected sessions. Later check-in and attendance
  state are per ticket/session pair and remain owned by 003.2.
- **FR-014g**: V1 Silverclay tickets MUST reserve one seat in both selected
  sessions but MUST NOT be bound to or used to verify an individual participant
  identity. The booking contact receives the ticket set and is responsible for
  its distribution.
- **FR-014a**: The confirmation email MUST be delivered only to the submitted
  booking contact in V1 and include the complete QR ticket set directly and a
  protected Magic Link to the read-only booking details. The contact may share
  tickets independently; no participant-specific delivery is performed.
- **FR-014b**: The QR Check-in ticket MUST contain only a signed opaque
  token/reference and MUST NOT contain PII, payment data, raw Magic Link tokens,
  or secrets.
- **FR-014c**: Each QR Check-in ticket MUST remain valid until the associated
  workshop ends and MUST support revocation without changing the booking's
  historical payment evidence.
- **FR-014d**: Resending a confirmation email MUST reuse the same active ticket
  set and MUST NOT create, revoke or rotate any ticket.
- **FR-014e**: Guest and Member resend requests MUST require the corresponding
  lookup or ownership proof. Staff assigned to the booking branch and Managers
  responsible for that branch MUST be authorized and audited before resending.
  Resend MUST NOT change the booking, payment, ticket set, contact email, or
  active Magic Link. Server-enforced resend limits MUST be configurable and
  separate for self-service and internal resend. A rejected request MUST send
  no email and create no ticket or Magic Link, and MUST return a safe error.
  Every accepted self-service resend MUST create minimal safe business audit
  evidence; delivery attempts and retries remain operational logs.
- **FR-015a**: A confirmation notification failure MUST NOT roll back a valid
  booking confirmation or deposit settlement; the system MUST retain a safe
  retry intent and retry delivery automatically.
- **FR-015b**: Automatic confirmation-notification delivery MUST retry at most
  three times with backoff, then produce a safe operational follow-up outcome
  for Staff assigned to the booking branch or Managers responsible for that
  branch, without changing booking/payment state.
- **FR-015**: The system MUST send or queue a confirmation notification after
  successful booking confirmation and retain a safe operational outcome when
  delivery fails.
- **FR-016**: The system MUST allow a Guest or Member to look up an eligible
  booking and display its status, package, schedule, participant summary, payment
  state, and available ticket information.
- **FR-017**: The system MUST prevent an unauthorized or ambiguous lookup from
  exposing another customer's booking or sensitive personal information.
- **FR-017a**: The Guest self-service lookup MUST require the booking code and
  either the normalized contact phone or normalized contact email captured for
  that booking; at least one contact proof must match.
- **FR-017b**: After successful payment confirmation, the system MUST send a
  protected Magic Link that grants read-only access to the corresponding booking
  details and complete ticket set until the associated workshop ends; the token MUST be
  stored only as a hash, MUST be reusable until it expires or is revoked, MUST
  expire automatically when the workshop ends, and MUST be revocable. It MUST
  act as a bearer credential only for that projection and MUST NOT authenticate
  a Member or grant Member account access. A valid link alone MUST be sufficient
  to open the allowed projection; suspected exposure is handled by authorized
  revocation or rotation, not an additional proof on every use.
- **FR-017b1**: The confirmation notification and Magic Link MUST be sent to the
  submitted booking contact email for both Guest and Member bookings; the Member
  profile email MUST NOT override an edited booking contact email.
- **FR-017b2**: Resending the post-payment booking confirmation email MUST reuse
  the existing active Magic Link. It MUST NOT create an additional active link,
  revoke or rotate the existing link, or change any QR ticket. Only a separate
  authorized incident-response action may revoke or rotate the link.
- **FR-017b3**: If the booking contact no longer has access to the submitted
  contact email, Guest lookup using the booking code and stored contact phone
  MUST provide the same read-only booking and ticket view. V1 MUST NOT
  automatically change the contact email or send a Magic Link to a new email.
- **FR-017b4**: A valid Magic Link projection MUST contain only booking code,
  booking/deposit status, package, branch, required-session schedule,
  participant count, and QR tickets/ticket codes. It MUST NOT contain contact
  email or phone, VNPay reference, detailed monetary amounts, Staff,
  attendance, or audit evidence.
- **FR-017b10**: For every authorized Guest lookup, Member lookup, or Magic
  Link read, the system MUST obtain the current delivery-safe ticket/session-
  entitlement projection from 003.2. The displayed branch and required-session
  schedule MUST therefore reflect a successful later replacement or verified
  refund consequence. A booking-side delivery copy is not authoritative for
  entitlement or schedule and MUST NOT be used to display a stale session.
- **FR-017b5**: V1 MUST provide authorized internal Magic Link incident
  handling. Only a Manager responsible for the booking branch may revoke or
  rotate a Magic Link; Staff and the `ADMIN` role alone MUST NOT have
  that authority. Revoke MUST make the existing Magic Link unusable
  immediately. Rotate MUST revoke the existing link and create a replacement
  link only for the submitted booking-contact email, and MUST queue its
  immediate delivery. A replacement-delivery failure MUST NOT restore the
  revoked link; it follows the notification retry and safe operational
  follow-up rules. Neither action may change the booking, payment, ticket set,
  contact email, or capacity.
- **FR-017b6**: Magic Link revoke or rotate MUST require a Manager-provided
  reason and MUST record audit evidence containing actor reference, booking
  code, branch, action, reason, outcome, and correlation reference. The audit
  evidence MUST NOT retain a raw Magic Link, token, or QR payload.
- **FR-017b8**: Each required business audit event in this feature MUST contain
  only actor type or identity reference when available, booking code, applicable
  branch, action, outcome, correlation reference, and timestamp; a
  Manager-provided reason is additionally required for Magic Link revoke or
  rotate. It MUST NOT retain contact proof, raw Magic Link, QR, or token
  material. Notification delivery attempts and retries MUST be retained only as
  separate operational logs, not business audit evidence.
- **FR-017b9**: A booking Magic Link email MUST open a React booking route with
  its opaque bearer value in the URL fragment. The React client MUST immediately
  submit that value by HTTPS POST to obtain only the allowed read-only booking/
  ticket projection, then clear the fragment and retain only the projection in
  memory. It MUST NOT create a Member login, shared server session, cookie, or
  persistent client credential store. Each later link opening MUST exchange the
  value again. An expired or revoked value MUST reveal no booking data and MUST
  offer Guest booking-code plus stored-phone recovery without logging the value.
- **FR-017b7**: Before any internal resend, Magic Link incident action, or
  late-payment follow-up action, the server MUST recheck actor authorization,
  active account state, entitlement, and booking-branch scope. If any check
  fails, it MUST deny the action without side effects, return a safe error, and
  record a minimal denied-attempt audit for the sensitive internal action.
- **FR-017e**: The current booking flow MUST NOT require or invoke the legacy
  pre-payment email-confirmation classes, endpoints, token states, or email gate.
- **FR-017c**: An authenticated Member MUST access bookings through Member
  ownership authorization rather than the Guest lookup proof.
- **FR-017c1**: An authenticated Member MUST be allowed to resume or retry
  deposit payment only for an owned `PENDING_PAYMENT` booking with an active
  hold, using the same payment and state rules as a Guest.
- **FR-017d**: In-store Staff/POS check-in, fallback lookup by name/phone, and
  identity-document verification MUST remain outside this feature.
- **FR-017f**: A pending booking projection MUST show only payment-resume data,
  including remaining hold time and a resume-payment action; it MUST NOT expose
  any QR check-in ticket.
- **FR-017g**: A confirmed booking projection MUST show the complete ticket set and
  booking/branch details, MUST NOT show a payment countdown, and MUST NOT expose
  a resume-payment action.
- **FR-017h**: A Guest or authorized Member MUST be able to release an active
  pending hold from the lookup journey; the release MUST not create a confirmed
  booking or QR ticket. The system MUST append a redacted audit event containing
  the actor type/verified identity reference when available, a required reason,
  booking code, outcome, and correlation ID; it MUST NOT record contact proofs,
  raw tokens, or unnecessary PII.
- **FR-017i**: After a Guest releases a pending hold, the booking view MUST offer
  a safe action to start a new workshop booking and MUST NOT offer payment retry
  for the cancelled booking.
- **FR-018**: The system MUST validate all customer input and return safe,
  typed, correlation-linked errors without exposing stack traces, credentials,
  raw payment payloads, secrets, or unnecessary PII.
- **FR-019**: The system MUST preserve the ownership boundary of the workshop
  booking module; schedule configuration, capacity configuration, staff
  assignment, check-in processing, and external synchronization remain outside
  this feature's customer-facing scope.
- **FR-019a**: For booking-critical integration with 003.2, the system MUST use
  its exposed synchronous boundary to read sale eligibility, reserve participant
  capacity across every required session all-or-nothing, commit the complete
  active hold set all-or-nothing, release active holds idempotently, and obtain
  the delivery-safe ticket/session-entitlement projection after commit. It MUST
  NOT read 003.2 entities or repositories. Events MAY be used only after a
  local state transition for non-critical notification or downstream handoff and
  MUST NOT decide capacity or booking-confirmation outcome.
- **FR-020**: The system MUST represent service lifecycle status separately from
  financial payment status and MUST support only the current feature's
  transitions for pending payment, confirmation, expiry, and explicit release
  of a pending hold. `CHECKED_IN`, `COMPLETED`, and `NO_SHOW` are reserved for
  later features and MUST NOT be implemented here.
- **FR-021**: The system MUST restore the held participant capacity when a
  pending booking expires, exactly once.
- **FR-022**: The system MUST preserve the financial-status vocabulary needed by
  later workshop lifecycle features, including `FULLY_PAID`, without implementing
  final-payment settlement in this feature.
- **FR-022a**: On an idempotent semantic final-balance-resolved conclusion from
  003.3, the Booking owner MUST change its financial status to `FULLY_PAID`
  exactly once and retain only a safe correlation/reference. It MUST NOT receive
  raw VNPay payload or final-payment detail, verify final payment, calculate the
  final balance, or treat creation/delivery of a deferred-payment link as final
  settlement.
- **FR-024**: The system MUST preserve the financial-status vocabulary needed by
  later cancellation/refund features without implementing refund processing in
  this feature.
- **FR-025**: A 003.1 rollout MAY reset only a development/test environment that
  has no booking or customer data requiring retention, after environment
  preflight. Staging, production, or any environment holding such data MUST use
  an append-only preservation migration with upgrade-test and recovery/rollback
  evidence; implementation MUST NOT edit an applied migration or reset that
  environment as a shortcut.

### Key Entities

- **Workshop Package**: A published customer-selectable workshop offering with
  description, price, participant constraints, and supported options.
- **Workshop Session**: A location/date/session combination that is required by
  a package/course and can accept participants when operational capacity is
  configured.
- **Workshop Booking**: A customer's request for one package/course and its
  participants. The package may have one or more required sessions; the booking
  has a lifecycle from pending payment to confirmed or expired.
- **Package Invoice**: The financial record containing the package price and
  required deposit state for a booking.
- **Payment Transaction**: A record of a payment attempt or verified payment
  notification, including safe provider reference and idempotency evidence.
- **Capacity Hold**: A temporary reservation of participant capacity associated
  with a pending booking and an expiry time.
- **Design Request**: An optional supported ring-design choice and its outcome
  associated with a booking.
- **Booking Ticket**: One independently scannable QR representation of a
  purchased participant seat, available after booking confirmation. A confirmed
  booking has exactly `participant_count` tickets. For Silverclay, each ticket
  is valid for both selected sessions; later attendance remains per
  ticket/session pair.
- **Payment Status**: The financial state of a booking, including unpaid,
  deposit paid, fully paid, failed, refund pending, or refunded.

## Success Criteria

### Measurable Outcomes

- **SC-001**: At least 95% of valid customer booking attempts reach a clear
  pending-payment or confirmed outcome without manual operator intervention.
- **SC-002**: 100% of confirmed bookings have exactly one accepted deposit
  transaction, payment status `DEPOSIT_PAID`, a retrievable booking code, and
  exactly one active ticket for each purchased participant seat.
- **SC-003**: 100% of invalid, duplicate, mismatched, or unsigned payment
  notifications leave the booking unconfirmed and do not settle the invoice.
- **SC-004**: 100% of expired pending bookings release their temporary capacity
  within the defined expiry process and cannot be confirmed by a late payment.
- **SC-005**: A visitor can browse a package and reach its booking entry point in
  no more than three primary interaction steps from the workshop listing.
- **SC-006**: At least 90% of successful booking lookups return the correct
  booking summary and status on the first valid lookup attempt.
- **SC-007**: No acceptance-path verification or production log contains a
  password, raw authentication token, payment secret, raw signed payload, or
  unnecessary sensitive personal data.

## Assumptions

- Existing authentication and Member profile capabilities are reused; this
  feature does not create a new identity system.
- Guest booking remains a supported public journey and does not require email
  confirmation before payment in this feature.
- Published packages, sessions, capacity, supported materials, and supported
  design paths are supplied by existing or future workshop-operations workflows.
- The configured payment provider and notification gateway are dependencies;
  provider credentials and secrets are supplied only through deployment
  configuration and are never part of this feature's source artifacts.
- The initial customer-facing UI must remain usable as a server-rendered form and
  link flow when enhancement scripts are unavailable.
- Participant check-in consumes the generated booking code and QR ticket but is
  implemented by a later feature.
- Post-confirmation cancellation/refund, check-in, no-show handling, workshop
  completion, final payment, and approved additional fees are deferred to later
  features.
- The exact database baseline and provider-specific contract details are selected
  in the implementation plan, not invented in this specification.
