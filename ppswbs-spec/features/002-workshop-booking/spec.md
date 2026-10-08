# Feature Specification: Customer Workshop Booking Lifecycle

**Feature Branch**: `002-workshop-booking`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Create the first customer-facing Workshop Booking lifecycle: browse workshop packages, create a booking, pay the deposit, and look up booking details."

## Scope and source alignment

This feature defines the first customer-facing Workshop Booking slice for Guests
and Members. It covers the journey from discovering a published workshop package
through creating a pending booking, paying the required deposit, and looking up
the resulting booking.

The source use cases are:

- `01-browse-workshop-packages.md`
- `02-view-workshop-package-details.md`
- `03-create-workshop-booking.md`
- `04-pay-workshop-deposit.md`
- `05-look-up-booking-details.md`

Workshop schedule configuration, slot capacity configuration, staff assignment,
participant check-in, post-confirmation completion/no-show processing, holiday
exceptions, and external synchronization are explicitly deferred to later
features. This feature consumes published package,
schedule, session, capacity, and supported-material information; it does not own
their operational configuration workflows.

## Clarifications

### Session 2026-10-07

#### Approved resolution for cancellation and state scope

The earlier cancellation-deadline and paid-booking cancellation decisions are
superseded. In this feature, `CANCELLED` is used only when a Guest or Member
explicitly releases an active `PENDING_PAYMENT` hold. The hold is released
without refund processing and no QR ticket is issued. Post-confirmation
cancellation, refund, and Staff/Owner operational cancellation are deferred to
a later feature. The implemented state enum is therefore
`PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, and `CANCELLED`; `CHECKED_IN`,
`COMPLETED`, and `NO_SHOW` remain reserved states.

- Q: Booking should use which business and financial states? → A: Service status and financial payment status are separate. The agreed service statuses are `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CHECKED_IN`, `COMPLETED`, `CANCELLED`, and `NO_SHOW`; booking financial statuses are `UNPAID`, `DEPOSIT_PAID`, `FULLY_PAID`, `REFUND_PENDING`, and `REFUNDED`. `FAILED` is reserved for `PaymentAttempt`.
- Q: Is email confirmation required before payment? → A: No. The booking does not require email confirmation before payment; after successful payment, the system generates the QR ticket and sends the confirmation notification.
- Q: Who owns the OTP step shown during payment? → A: The Payment Gateway owns OTP or equivalent payment authorization; Workshop Booking does not issue, store, or verify the OTP. It only processes the gateway's authenticated payment result.
- Q: Does the External Registration System integration belong to this feature? → A: No. This feature records or emits the confirmed-booking handoff for a later synchronization feature; external slot deduction, retry and reconciliation are out of scope.
- Superseded clarification: post-confirmation cancellation, cancellation deadlines, refunds, and Staff/Owner operational cancellation are deferred to a later feature.
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
- Q: What should the confirmation email contain? → A: It contains both the QR Check-in ticket directly and a Magic Link to the read-only booking details.
- Q: What should the QR Check-in payload contain? → A: It contains only a signed opaque token/reference; it contains no PII, payment data, raw Magic Link token, or other sensitive payload.
- Q: When does the QR Check-in ticket expire? → A: It remains valid until the associated workshop ends and may be revoked when required.
- Q: What happens to the QR when a confirmation email is resent? → A: Reuse the same active QR/token; resending email does not create a new ticket or revoke the existing one.
- Q: Who may request a confirmation email resend? → A: Guest, Member, and authorized Staff/Owner may request a resend under their respective access rules; operational sends are audited.
- Q: What happens if confirmation email delivery fails after payment? → A: The booking remains `CONFIRMED`; the system stores a notification retry intent and retries automatically. Email delivery is not a condition for booking confirmation.
- Q: How should automatic confirmation-email retry stop? → A: Retry at most three times with backoff, then mark the notification for authorized Staff/Owner follow-up.

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

As a Guest or Member, I want to select a package, location, date, session, and
participant information so that the system can prepare my deposit payment.

**Why this priority**: Creating a valid pending booking is the core value of the
feature and provides the transaction context for payment.

**Independent Test**: A visitor can select an available package and session,
submit valid contact and participant details, and receive a pending booking with
an exact deposit amount and temporary capacity hold.

**Acceptance Scenarios**:

1. **Given** a published package and a session with configured remaining
   capacity, **When** the visitor submits valid booking details, **Then** the
   system creates one payment-pending booking and package invoice.
2. **Given** a Member has verified profile details, **When** the Member confirms
   them for the booking, **Then** the system uses the applicable profile data
   without requiring duplicate entry.
3. **Given** the visitor is a Guest, **When** required contact and participant
   details are missing or invalid, **Then** the system identifies the fields to
   correct and creates no booking.
4. **Given** the visitor is a Member, **When** the booking form opens, **Then**
   email and phone are auto-filled from the Member profile and remain editable
   for booking on behalf of another person.
5. **Given** the requested participant count exceeds remaining capacity, **When**
   the visitor submits the booking, **Then** the system rejects it and asks the
   visitor to choose another session or reduce the participant count.
6. **Given** a supported ring-design path is selected, **When** the design request
   is accepted, **Then** the request and its outcome are associated with the
   pending booking.

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
   selected package price.
2. **Given** a valid signed payment confirmation with the expected booking
   reference and amount, **When** the system receives it within the allowed
   window, **Then** it records one deposit transaction, sets payment status to
   `DEPOSIT_PAID`, and confirms the booking.
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
3. **Given** a confirmed booking has a QR check-in ticket, **When** the visitor
   views the booking, **Then** the ticket is available for later check-in.
4. **Given** a Guest has a confirmed booking, **When** the Guest submits the
   booking code and either the contact phone or contact email used during
   booking, **Then** the system
   displays the authorized booking details, branch, session status, and QR ticket.
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
   are displayed, **Then** the system shows the QR ticket and booking/branch
   details without a payment countdown.

### Edge Cases

- A package becomes unpublished or unavailable while a visitor is completing a
  booking; the system must revalidate it before creating the booking.
- A selected session has no configured capacity; it cannot accept a booking.
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
- An invalid, expired, revoked, or reused Magic Link must not reveal booking
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
- **FR-003**: The system MUST allow a Guest or Member to select a published
  package, location, date, session, participant count, and required contact and
  participant information before submitting a booking.
- **FR-003a**: Guest booking creation MUST require a valid contact email and
  contact phone. Member booking forms MUST auto-fill both fields from the Member
  profile, allow edits for booking on behalf of another person, and persist the
  submitted values as immutable booking contact snapshots.
- **FR-004**: The system MUST revalidate package availability, session
  availability, configured capacity, participant count, and required information
  at booking submission time.
- **FR-005**: The system MUST reject a booking when the selected session has no
  configured capacity or insufficient remaining capacity.
- **FR-006**: The system MUST create at most one customer-facing pending booking
  for a single accepted booking submission and must not leave an incomplete
  booking when creation fails.
- **FR-007**: The system MUST create a package invoice with payment-pending
  status and calculate the required deposit as 50% of the selected package price.
- **FR-008**: The system MUST hold the requested capacity temporarily for a
  pending booking for no longer than 15 minutes.
- **FR-009**: The system MUST release the temporary capacity hold when valid
  deposit confirmation is not received before the hold expires.
- **FR-010**: The system MUST support the configured workshop deposit payment
  flow and validate the payment signature, booking reference, amount, status, and
  duplicate-event state before settlement.
- **FR-010a**: The Payment Gateway MUST own payment authorization steps such as
  QR scanning or OTP entry; the Workshop Booking system MUST NOT capture, store,
  or independently validate gateway OTP values.
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
- **FR-014**: The system MUST generate a booking code and QR check-in ticket
  after successful deposit confirmation.
- **FR-014a**: The confirmation email MUST include the QR Check-in ticket
  directly and a protected Magic Link to the read-only booking details.
- **FR-014b**: The QR Check-in ticket MUST contain only a signed opaque
  token/reference and MUST NOT contain PII, payment data, raw Magic Link tokens,
  or secrets.
- **FR-014c**: The QR Check-in ticket MUST remain valid until the associated
  workshop ends and MUST support revocation without changing the booking's
  historical payment evidence.
- **FR-014d**: Resending a confirmation email MUST reuse the same active QR
  ticket and MUST NOT create a duplicate ticket or revoke the existing ticket.
- **FR-014e**: Guest and Member resend requests MUST require the corresponding
  lookup or ownership proof, while Staff/Owner resend requests MUST be
  authorized and audited; all resend paths MUST be rate-limited and safe.
- **FR-015a**: A confirmation notification failure MUST NOT roll back a valid
  booking confirmation or deposit settlement; the system MUST retain a safe
  retry intent and retry delivery automatically.
- **FR-015b**: Automatic confirmation-notification delivery MUST retry at most
  three times with backoff, then produce a safe operational follow-up outcome
  for authorized Staff/Owner without changing booking/payment state.
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
  details and QR ticket until the associated workshop ends; the token MUST be
  stored only as a hash, MUST expire automatically when the workshop ends, and
  MUST be revocable.
- **FR-017b1**: The confirmation notification and Magic Link MUST be sent to the
  submitted booking contact email for both Guest and Member bookings; the Member
  profile email MUST NOT override an edited booking contact email.
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
  a QR check-in ticket.
- **FR-017g**: A confirmed booking projection MUST show the QR ticket and
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
- **FR-023**: After a booking is confirmed, the system MUST record a durable
  handoff for the later External Registration System synchronization without
  making the external system a prerequisite for local booking confirmation.
- **FR-024**: The system MUST preserve the financial-status vocabulary needed by
  later cancellation/refund features without implementing refund processing in
  this feature.

### Key Entities

- **Workshop Package**: A published customer-selectable workshop offering with
  description, price, participant constraints, and supported options.
- **Workshop Session**: A selectable location/date/session combination that can
  accept participants when operational capacity is configured.
- **Workshop Booking**: A customer's request for one package/session and its
  participants, with a lifecycle from pending payment to confirmed or expired.
- **Package Invoice**: The financial record containing the package price and
  required deposit state for a booking.
- **Payment Transaction**: A record of a payment attempt or verified payment
  notification, including safe provider reference and idempotency evidence.
- **Capacity Hold**: A temporary reservation of participant capacity associated
  with a pending booking and an expiry time.
- **Design Request**: An optional supported ring-design choice and its outcome
  associated with a booking.
- **Booking Ticket**: A booking code and QR representation available after
  confirmation for later check-in.
- **Payment Status**: The financial state of a booking, including unpaid,
  deposit paid, fully paid, failed, refund pending, or refunded.

## Success Criteria

### Measurable Outcomes

- **SC-001**: At least 95% of valid customer booking attempts reach a clear
  pending-payment or confirmed outcome without manual operator intervention.
- **SC-002**: 100% of confirmed bookings have exactly one accepted deposit
  transaction, payment status `DEPOSIT_PAID`, and a retrievable booking code.
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
