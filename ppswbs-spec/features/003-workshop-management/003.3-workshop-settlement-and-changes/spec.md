# Feature Specification: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-29
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.3-workshop-settlement-and-changes`

**Status**: Draft — not implemented

**Input**: Confirmed workshop-settlement, change, and direct-booking cancellation
decisions. This spec retains the feature baseline locally so a later retirement
of the shared `research.md` does not remove its behavioural decisions.

## Feature 003 authorization decision (2026-10-29)

Every active canonical `MANAGER` has global location scope for Manager-authorized
settlement and change actions, consistent with 003.1, 003.2 and 003.4. Manager
authorization does not depend on `activeLocationIds`; Staff actions remain
location-scoped, Member ownership and booking-specific safeguards remain in
force, and explicitly Admin-only technical actions remain Admin-only. There is
no Central Manager tier.

## Scope

This feature owns direct-Mland actions after a workshop booking is confirmed:
post-package charges and final bills, final payment and release control,
Manager-approved deferred payment, Manager-mediated change of one or more
future required sessions,
customer-requested whole-booking cancellation/refund, and Mland-caused
package cancellation remedies.

It consumes booking/deposit evidence from 003.1 and authoritative session,
capacity, ticket, attendance, and cancellation-handoff outcomes from 003.2. It
does not create sessions, perform normal check-in, provide customer self-service
rescheduling, or process OTA payments/refunds.

## Clarifications

### Session 2026-10-27

- Q: Is a Workshop Branch a separate authorization/resource identity from a
  Workshop Location? → A: No. Both terms refer to the same canonical physical
  resource identified by `workshop_location.id`. Every booking/session/catalogue
  scope and applicable audit location uses that identity directly, as do Account
  Management `activeLocationIds`. Public terminology is `location`/`locationId`;
  no separate branch ID, branch-to-location mapping, or local location copy is
  introduced.

### Session 2026-10-24

- Q: What is the customer refund unit? → A: Only the whole direct booking;
  customers cannot self-service cancel/refund a session, ticket, or participant.
- Q: What is the direct-booking refund policy? → A: Against the earliest session
  start: 100% of paid deposit at least 72 hours before; 50% from 24 to under 72
  hours; 0% below 24 hours, for no-show, or after any ticket check-in.

### Session 2026-10-25

- Q: What is the normal settlement point for a multi-session package? → A: One
  final bill applies to the whole booking/package. Staff/Manager finalizes
  assessed charges and collects final payment only after its final required
  session; release is permitted only after that bill is fully paid. Mland grants
  no default credit.
- Q: Can a customer reschedule a multi-session package online? → A: No. The
  customer contacts the shop. An authorized Manager may replace one or both
  future required sessions, only if the resulting complete package/session set
  remains valid and capacity is available for every replacement; the outcome is
  atomic.
- Q: What happens if Mland cancels a required session and a replacement is not
  possible or is declined? → A: Mland first offers an equivalent replacement.
  If that cannot be completed, it processes an audited refund of the whole
  booking/package; V1 has no session-scoped partial refund or credit/voucher.
- Q: What amount is refunded for an unresolved Mland-caused package
  cancellation? → A: Refund 100% of every successfully collected payment for
  the booking/package, including the deposit and any final payment, even if an
  earlier required session was attended.
- Q: When may a revised Mland-caused-cancellation refund policy apply? → A: The
  V1 default is 100% of every successfully collected booking/package payment.
  Mland may revise the policy for bookings confirmed after its effective time;
  each confirmed booking retains the policy snapshot accepted at confirmation.
- Q: How are additional material or service charges added? → A: A customer may
  add a fixed-price catalogue add-on allowed for the package/location; it enters
  the final bill at its displayed price. A variable material or service charge
  becomes part of the bill only after Staff/Manager confirms it. Customers never
  enter arbitrary prices.
- Q: Which V1 channel provides accepted final-payment evidence? → A: VNPay only.
  A customer may pay at the counter through the VNPay link/QR, but settlement
  requires valid signed VNPay IPN or QueryDR evidence; browser return and a
  manually recorded counter payment do not settle the bill.
- Q: Which feature executes a required-session change? → A: 003.3 requests one
  booking-level change containing the complete replacement session set. 003.2,
  as capacity and ticket-entitlement owner, validates the complete result and
  applies all changes or none; it preserves the persistent ticket QR.
- Q: Who may revise the Mland-caused-cancellation refund policy? → A: Any
  active Manager may manage the policy globally. All Manager-authorized actions
  in Feature 003 use global location scope; booking snapshots remain immutable.

### Session 2026-10-26

- Q: How may a customer submit a whole-booking cancellation request? → A: An
  authenticated Member uses verified ownership. A Guest submits the booking code
  with the stored contact phone or email again at request time. Magic Link may
  lead to the cancellation screen but remains read-only and is not sufficient
  cancellation authority.
- Q: Which internal actor performs routine settlement? → A: Active Staff with
  applicable booking-location scope may create/confirm eligible charges and
  finalize a final bill. Manager-only authority remains required for deferred
  payment, required-session change, Mland-caused remedy/refund exception, and
  prospective refund-policy revision.
- Q: At what REST scope are final bill, charge, and finalization exposed? → A:
  The one final bill is a booking-scoped resource. Its charges and finalization
  commands are exposed in the context of that final bill, not as a shared or
  ticket-scoped bill.
- Q: At what REST scope are Manager change, remedy, and policy-revision actions
  exposed? → A: Required-session change and Mland-caused remedy are
  booking-scoped commands. Prospective refund-policy revision is a separate
  central resource because it applies only to bookings confirmed after its
  effective time across Mland locations.
- Q: How do public state-changing REST commands handle retries and responses?
  → A: Each requires an `Idempotency-Key`; a same-key, same-content retry returns
  the original outcome. A response contains the current safe settlement
  projection, or a typed safe correlation-linked error, never unnecessary PII,
  token, QR payload, secret, or raw gateway payload.
- Q: How are settlement actions represented in public REST? → A: Settlement
  actions create or update an auditable business resource. Final bill and charge
  are resources; customer cancellation, required-session change, and
  Mland-caused remedy create a case with its own reference and lifecycle.
- Q: How does Settlement hand an approved final-payment link, its one reminder,
  or a settlement/refund outcome to Feature 007 without coupling money truth to
  email delivery? → A: In the same local transaction that accepts the Settlement
  business outcome, 003.3 creates an immutable notification handoff reference.
  After commit it publishes an at-least-once reference-only event. Feature 007
  deduplicates that reference, obtains the immutable delivery payload through
  the named Settlement facade, and owns provider send/retry logs. A handoff or
  provider failure never reverses final-bill, payment, refund, case, or Booking
  `FULLY_PAID` state.
- Q: Which public REST routes and methods expose settlement resources? → A:
  Final bill uses `GET/POST /workshop-bookings/{bookingCode}/final-bill`; charge
  and finalization use `POST` under its final-bill context; customer cancellation,
  required-session change, deferred payment, and Mland remedy each use a booking-
  scoped `POST` case collection. Prospective policy uses
  `GET/POST /workshop-refund-policies`.
- Q: What minimum input opens a customer cancellation case? → A: Booking code is
  in the route. An authenticated Member sends no additional ownership proof; a
  Guest supplies exactly one stored contact phone or email. Neither may submit a
  refund amount, ticket/session selection, or Magic Link as cancellation authority.
- Q: What minimum input opens a required-session-change case? → A: Booking code
  is in the route; an authorized Manager supplies the complete target required-
  session reference set and a reason. It supplies no capacity seat count,
  ticket/QR, price, or partial replacement mapping; 003.2 validates/applies the
  complete result atomically.
- Q: What minimum input opens a deferred-payment case? → A: An authorized Manager
  provides only a reason. The server derives the current final-bill outstanding
  balance and the fixed 24-hour expiry; it accepts no caller-selected amount,
  expiry, or payment-link URL.
- Q: What minimum input opens a Mland-caused remedy case that proceeds to refund?
  → A: An authorized Manager supplies a reason and `replacementOutcome` of
  `UNAVAILABLE` or `DECLINED`. The server derives the retained policy snapshot
  and refund amount; it accepts no amount, ticket/session selection, or QR input.
- Q: What minimum input creates a prospective refund-policy revision? → A: The
  active Manager supplies only `effectiveAt` and
  `wholeBookingRefundPercent`. The server requires a future effective time and a
  valid percentage; the policy applies only prospectively and never rewrites an
  existing booking snapshot.
- Q: What lifecycle and safe projection do settlement cases use? → A: Every case
  uses `OPEN` then `RESOLVED`, `REJECTED`, or `FAILED`; refund/deferred cases may
  additionally use `PENDING_PROVIDER`, and only deferred payment may use
  `EXPIRED`. The safe projection includes case reference, type, status, booking
  code and correlation reference; policy/amount outcome is included only for an
  authorized caller.
- Q: How do fixed add-on and variable charge flow? → A: A customer supplies only
  `catalogueAddOnReference`; the server validates package/location and creates the
  accepted charge at its catalogue snapshot price. Only Staff/Manager supplies a
  variable description, amount, and reason; that one audited command confirms
  the variable charge. Customers have no variable-charge flow.
- Q: How does VNPay evidence enter settlement? → A: HTTPS signed IPN is provider
  ingress and receives a VNPay acknowledgement; server-to-server QueryDR
  reconciles missing, late, or ambiguous IPN. Both match reference, amount, and
  currency and deduplicate by provider transaction/reference. Browser return is
  display-only and has no settlement route.
- Q: How are REST errors and client/provider retries distinguished? → A: Client
  commands use `Idempotency-Key`: same key/same content returns original result;
  changed reuse returns `409 IDEMPOTENCY_CONFLICT`. VNPay IPN/QueryDR deduplicates
  by verified provider transaction/reference and safely acknowledges duplicates.
  Errors use `400` malformed input, `401` invalid/missing authentication, `403`
  authority denial, `404` missing/not-visible booking, `409` conflict, and `422`
  valid input but business ineligibility.
- Q: Can a created settlement case be read again after a reload or retry? → A:
  Yes. Each booking-scoped case has a `caseReference` and a corresponding `GET`
  route beneath the same booking. The server returns only the safe case
  projection permitted to the requesting actor; it does not expose raw payment,
  token, QR, or unnecessary contact data.
- Q: May a customer read the final bill and case status for their own booking? →
  A: Yes. A Member proves authenticated ownership; a Guest proves possession of
  the booking code plus one stored contact phone or email on each read. The
  response is read-only and limited to the customer-safe final-bill/case
  projection, without raw gateway evidence or internal data.
- Q: How is an offered replacement linked to an Mland-caused cancellation and a
  possible remedy? → A: The Manager uses the existing required-session-change
  case with the complete replacement set and the 003.2
  `cancellationHandoffReference`. A remedy case for replacement `UNAVAILABLE` or
  `DECLINED` must reference that same handoff; a free-text reason alone is not
  sufficient.
- Q: Which public ingress accepts VNPay IPN in V1? → A: VNPay calls shared
  technical `paymentintegration` at `GET
  /api/v1/integrations/payments/vnpay/events` with its signed query parameters.
  After verification and idempotent processing, Mland responds with the VNPay
  JSON acknowledgement fields `RspCode` and `Message`. QueryDR remains an
  outbound Mland-to-VNPay reconciliation call; browser Return URL is display-only.
- Q: Which component owns an IPN shared by deposit and settlement flows? → A:
  Technical `paymentintegration` owns the one HTTPS `GET` ingress. It verifies
  the checksum once, deduplicates delivery, resolves the immutable
  owner-qualified merchant reference, and dispatches redacted verified evidence
  to 003.1 for `DEPOSIT` or 003.3 for `FINAL_PAYMENT`/`REFUND`. It never
  broadcasts, guesses an owner, or changes booking/settlement state itself.
- Q: Which refund percentage and VND rounding rule does a prospective policy
  revision use? → A: An active Manager supplies a whole-number percentage from
  `0` through `100`. The system computes the refund as
  `floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND.
- Q: Who may select a fixed-price catalogue add-on, and until when? → A: An
  owning Member may use authenticated ownership and a Guest may use the booking
  code plus one stored contact phone or email on each request. Both may select
  only an eligible catalogue add-on before the final bill is finalized; Magic
  Link remains read-only.
- Q: Who may read the central refund-policy revision resource? → A: Any active
  Manager may read all policy revisions. Guest/Member may
  read only the retained policy snapshot and refund outcome permitted for their
  own booking; they cannot read central revision history.

## Business Rules

- A confirmed direct booking freezes its post-promotion amount, deposit, and
  remaining amount. Deposit is `ceil(post_promotion_amount / 2)` whole VND and
  remaining amount is the exact difference. The one deposit applies to the one
  booking/package without allocating it across sessions or rewriting original
  payment evidence.
- A final booking balance equals the remaining frozen package amount, approved
  additional material/service charges, less the paid deposit. Replacing required
  sessions within the same package never changes the frozen package price.
- A customer may add a fixed-price catalogue add-on permitted for the package and
  location; its displayed price becomes a final-bill charge. A variable material
  or service charge becomes a final-bill charge only after Staff/Manager confirms
  it. Customers cannot enter an arbitrary charge or price.
- Charge, payment, void, and adjustment records are immutable evidence. A
  correction is an authorized void or adjustment with audit, not an edit to
  historical evidence.
- The completed booking/package output is releasable only when its one final
  balance is fully paid through valid signed VNPay IPN or QueryDR evidence,
  except for an authorized deferred-payment exception. A booking is `FULLY_PAID`
  only when that final balance is settled or resolved by an audited exception;
  browser return and a manually recorded counter payment do not settle it.
- A successful required-session change transfers the affected session
  entitlements and capacity commitments through 003.2 as one booking-level
  all-or-none result. It retains the booking deposit and preserves each
  participant's persistent ticket/QR across the booking's resulting required
  sessions. For Silverclay, 003.2 MUST validate the complete resulting pair as
  same-location, distinct and chronologically ordered; if on the same day, the two
  slots MUST have at least one slot between them.
- A whole-booking refund revokes unused tickets and releases future capacity only
  after the gateway confirms the refund. A failed refund does not restore the
  booking for use.
- When Mland cancels a required session, it MUST offer an equivalent replacement
  first. If the replacement is unavailable or declined, its remedy is an audited
  refund governed by the booking's Mland-caused-cancellation policy snapshot.
  The V1 default is 100% of every successfully collected booking/package
  payment; V1 does not allocate a partial refund or credit to one session.
- Any active Manager may administer prospective Mland-caused-cancellation refund
  policy changes globally and resolve individual cases. Booking policy snapshots
  remain immutable.
- OTA customer payments, refunds, commission, payout, and reconciliation are
  outside this feature.

## Confirmed Decisions

### Session 2026-10-24

- A final-payment link is a 24-hour exception only for a Manager-approved
  Workshop Final Bill. Deposit/payment-hold links remain valid for 10 minutes
  and their associated holds last at most 15 minutes. The final-payment link
  may send at most one reminder before expiry; expiry never automatically renews
  the link, releases the package output, or erases the balance. A new link requires an
  audited authorized action.
- The system MUST NOT infer `Manager = OWNER`. Account Management supplies the
  canonical fixed role, current status and active location-scope facts; 003.3
  applies its own server-side Settlement action rules. Active Staff may perform
  routine charge/final-bill actions only within active location scope; any active
  Manager globally may perform deferred payment/link issue or
  reissue, required-session changes, and refund exceptions/Mland-caused remedy.
  An inactive actor loses these authorities immediately. Display role labels,
  Firebase claims and retired role codes never grant authority.
- Any active Manager may use the global Mland-caused-cancellation refund-policy
  editor. This does not introduce a role code or alter the Account Management
  role mapping; the feature checks active authorization and records the policy's
  effective time for future bookings.

## User Scenarios and Testing

### User Story 1 — Staff settles a completed booking/package (P1)

After a booking's final required session is completed, Staff needs to finalize
charges, present the final bill for VNPay payment, and determine whether the
package output can be released.

**Independent Test**: With a confirmed direct booking whose final required
session has finished, Staff can finalize charges and record one valid payment.
The package output becomes releasable only when its balance is zero;
failed/duplicate evidence changes nothing.

1. **Given** an eligible completed booking/package, **When** valid final payment is
   confirmed, **Then** immutable bill/payment evidence is recorded and release is
   permitted.
2. **Given** an outstanding balance, **When** Staff attempts release without a
   Manager exception, **Then** release is rejected.
3. **Given** a finalized bill, **When** Staff finds an error, **Then** only an
   audited void/adjustment corrects it; original evidence remains.
4. **Given** a permitted fixed-price catalogue add-on, **When** the customer
   selects it, **Then** it is visible in the final bill at its displayed price;
   a variable charge appears only after Staff/Manager confirmation.

### User Story 2 — Customer cancels a whole booking (P1)

Guest or Member needs a clear direct-booking cancellation/refund result without
being offered a session/ticket-level refund.

**Independent Test**: An eligible unstarted booking receives one whole-booking
request, snapshots its policy result, and changes tickets/capacity only after
confirmed refund.

1. **Given** all tickets are unused and earliest session is at least 72 hours
   away, **When** the customer requests cancellation, **Then** 100% of paid
   deposit is requested as refund.
2. **Given** earliest session is 24 to under 72 hours away, **Then** 50% is
   requested; below 24 hours, no-show, or checked-in ticket rejects the normal
   customer refund path.
3. **Given** refund is pending or fails, **Then** tickets/capacity do not change
   until confirmed and the booking is not restored for use after failure.

### User Story 3 — Manager changes future required sessions (P2)

Manager needs to replace one or both future required sessions without leaving a
multi-session package invalid or overbooking a target session.

**Independent Test**: Compatible replacement sessions with capacity update all
affected session entitlements and capacity atomically; the persistent ticket QR
continues to work for every resulting entitled session.

1. **Given** a booking with eligible future required sessions and compatible
   replacement capacity, **When** Manager approves a one- or two-session change,
   **Then** ticket entitlement, capacity, deposit, frozen package price, and audit outcomes
   succeed as one result.
2. **Given** insufficient/incompatible target capacity, **Then** no session,
   ticket, or bill change persists.

### User Story 4 — Manager remedies Mland-caused package disruption (P2)

When Mland cannot provide a required session, Manager needs an audited package
remedy that does not invent a separate per-session financial unit.

**Independent Test**: After replacement is unavailable or declined, Manager
creates a whole-booking refund; tickets/capacity change only after its verified
outcome.

## Functional Requirements

- **FR-001**: The system MUST apply this feature only to direct Mland workshop
  bookings and MUST exclude OTA payment/refund/payout/reconciliation.
- **FR-002**: The system MUST authorize protected actions on the server using
  active Mland account status, canonical role, and feature-owned location and
  resource action rule;
  client/provider claims and display labels MUST NOT grant authority.
- **FR-002a**: Active Staff with booking-location scope MAY create or confirm an
  eligible charge and finalize a final bill. Deferred payment, required-session
  change, Mland-caused remedy/refund exception, and prospective refund-policy
  revision remain Manager-only actions under their applicable authority.
- **FR-003**: The system MUST retain immutable, redacted audit evidence for
  settlement, release exception, deferred payment, reschedule, refund, void,
  adjustment, and Mland-caused remedy.
- **FR-004**: The system MUST preserve immutable charge, payment, void, and
  adjustment records for each final bill.
- **FR-004a**: The system MUST accept a customer-selected charge only for a
  fixed-price catalogue add-on permitted for the booking's package and location,
  at its displayed price. A variable material or service charge MUST require
  Staff/Manager confirmation before it enters the final bill; customers MUST NOT
  create arbitrary charge names or prices.
- **FR-004b**: `POST /workshop-bookings/{bookingCode}/final-bill/charges` MUST
  accept only `catalogueAddOnReference` from a customer and MUST derive the
  accepted price from the permitted package/location catalogue snapshot. An owning
  Member uses authenticated ownership; a Guest provides the booking code and one
  stored contact phone or email at each request. Customer catalogue selection
  MUST be rejected after final-bill finalization. A variable charge MUST be
  submitted only by Staff/Manager with description, amount, and reason; that
  audited command confirms it. No customer variable-charge command exists.
- **FR-005**: The system MUST calculate one booking/package final balance from
  frozen remaining amount, paid deposit evidence, and approved charges. It MUST
  NOT recalculate a frozen voucher, point, post-promotion amount, deposit, or
  remaining amount. A required-session change within that package MUST NOT alter
  its frozen package price.
- **FR-006**: The system MUST allow charge finalization only after the booking's
  final required session is completed, recording authorizing actor and time.
- **FR-006a**: The public REST contract MUST expose the one final bill in its
  booking context. Charge creation/confirmation and finalization commands MUST
  operate in that final-bill context; no final bill is ticket-scoped or shared
  by multiple bookings.
- **FR-006b**: `GET /workshop-bookings/{bookingCode}/final-bill` MUST disclose a
  customer-safe, read-only final-bill projection to an authenticated Member
  with booking ownership or a Guest who supplies the booking code and exactly one
  stored contact phone or email for that request. The projection MAY include the
  customer-visible charge lines, due/paid/refunded amounts, and bill status, but
  MUST exclude raw gateway evidence and internal-only data.
- **FR-007**: The system MUST prevent package-output release before final payment except
  through an authorized audited deferred-payment exception.
- **FR-008**: The system MUST set booking `FULLY_PAID` only when its one final
  balance is settled or resolved by an audited exception.
- **FR-009**: The system MUST accept final-payment evidence only from valid
  signed VNPay IPN or QueryDR, matched to the final bill's reference, amount and
  currency. It MUST process that evidence idempotently and reject invalid,
  mismatched, unsigned, duplicate, late, browser-return-only, or manually
  recorded counter-payment evidence without settling twice.
- **FR-009a**: Signed VNPay IPN MUST be HTTPS provider ingress with a
  VNPay-compatible acknowledgement. Server-to-server QueryDR MAY reconcile a
  missing, late, or ambiguous IPN. Both sources MUST match reference, amount,
  and currency and deduplicate by provider transaction/reference; browser return
  MUST remain display-only and MUST NOT have a settlement-confirmation route.
- **FR-009b**: V1 VNPay IPN ingress MUST be `GET`
  `/api/v1/integrations/payments/vnpay/events` and accept only signed provider
  query parameters. The technical `paymentintegration` boundary owns this route:
  it verifies and deduplicates once, then routes redacted evidence by immutable
  owner-qualified merchant reference to exactly one business owner. Following
  processing, it returns the VNPay JSON acknowledgement fields `RspCode` and
  `Message`. QueryDR is an outbound server-to-server reconciliation call and has
  no inbound public route.
- **FR-009c**: 003.3 MUST consume redacted verified `FINAL_PAYMENT` or `REFUND`
  evidence only through `WorkshopSettlementFacade`; it MUST NOT expose or own a
  VNPay IPN HTTP route, raw callback payload, checksum verification, or routing
  of deposit evidence.
- **FR-010**: Deferred final payment MUST require Manager approval with reason,
  amount, expiry, and audit; it MUST retain the package output and create no
  default credit.
- **FR-011**: The system MUST send at most one pre-expiry reminder and MUST NOT
  automatically renew an expired request, release the package output, or erase
  its balance.
- **FR-012**: A Manager-approved final-payment link MUST be valid for 24 hours,
  send at most one reminder before expiry, and apply only to a Workshop Final
  Bill. Deposit/payment-hold links remain subject to the 10-minute/15-minute
  shared rule. A new final-payment link after expiry requires an authorized,
  audited action.
- **FR-012a**: `POST /workshop-bookings/{bookingCode}/deferred-payment-cases`
  MUST accept only an authorized Manager reason. The server MUST derive the
  outstanding final-bill amount and fixed 24-hour expiry; it MUST NOT accept a
  caller-selected amount, expiry, or payment-link URL.
- **FR-012b**: When an authorized deferred-payment link/reissue, its permitted
  reminder, or a customer-facing verified settlement/refund outcome is accepted,
  003.3 MUST persist one unique immutable settlement notification handoff in
  the same local transaction and publish its reference only after commit. The
  event MUST contain only notification reference, settlement/bill or case
  reference, notification kind, and correlation reference; it MUST NOT contain
  recipient address, payment-link value, raw gateway evidence, ticket/QR data,
  or unnecessary PII. Feature 007 obtains the immutable render-ready payload
  only through the approved 003.3 notification-delivery facade and owns
  idempotent provider delivery/retry and operational logs. Delivery failure
  MUST NOT reverse the accepted Settlement outcome.
- **FR-013**: Guest/authorized Member cancellation MUST be whole-booking only;
  session/ticket/participant self-service cancellation/refund is prohibited. An
  authenticated Member MUST pass ownership verification; a Guest MUST provide
  the booking code and stored contact phone or email at cancellation-request
  time. Magic Link is read-only and MUST NOT alone authorize cancellation.
- **FR-013a**: `POST /workshop-bookings/{bookingCode}/cancellation-cases` MUST
  accept no customer-selected financial or ticket/session input. An authenticated
  Member requires no additional ownership field; a Guest MUST provide exactly one
  stored contact phone or email as request-time proof.
- **FR-014**: Normal customer cancellation MUST require all tickets unused, no
  required session started and no package output released, plus policy
  eligibility.
- **FR-015**: The system MUST calculate paid-deposit refund against earliest
  session start in session/location timezone: 100% ≥72h, 50% ≥24h and <72h, else 0%.
- **FR-016**: It MUST snapshot policy, earliest-session reference, paid amount,
  computed amount, request time, and eligibility outcome for each request.
- **FR-017**: It MUST use `REFUND_PENDING` until gateway outcome is confirmed,
  then revoke unused tickets/release future capacity only on confirmed refund.
- **FR-018**: An authorized Manager MAY replace one or more future required
  sessions only with compatible sessions that have capacity for the booking's
  unchanged participant count. For a multi-session package, the complete
  resulting session set MUST remain valid; Silverclay's resulting pair MUST be
  same-location, distinct, chronologically ordered and, when on the same day,
  separated by at least one slot. Feature 003.3 MUST request the complete
  booking-level replacement set from 003.2 and MUST NOT directly read or change
  its capacity, ticket or session entities.
- **FR-019**: Feature 003.2 MUST validate and apply the complete requested
  required-session change as all-or-none. On success it MUST atomically transfer
  affected ticket entitlements and capacity, retain the booking deposit and
  frozen package price, and preserve the persistent ticket QR for each resulting
  entitled session. On failure no part may persist.
- **FR-019a**: After a successful required-session replacement or verified
  refund consequence, this feature MUST rely on 003.2 as the source of the
  current ticket/session projection for existing 003.1 customer access. It
  MUST NOT copy a replacement schedule into Booking or treat Booking's initial
  delivery projection as authoritative.
- **FR-019b**: `POST /workshop-bookings/{bookingCode}/required-session-change-cases`
  MUST accept the complete target required-session reference set and a reason
  from an authorized Manager. When the change is an Mland-caused replacement, it
  MUST additionally accept the authoritative 003.2 `cancellationHandoffReference`.
  It MUST NOT accept caller-supplied capacity, ticket/QR, price, or partial
  replacement mapping.
- **FR-020**: When Mland cancels a required session, the system MUST offer an
  equivalent replacement before an authorized whole-booking/package refund.
- **FR-021**: If replacement is unavailable or declined, the authorized remedy
  MUST refund the amount required by the booking's Mland-caused-cancellation
  policy snapshot. The initial policy is 100% of every successfully collected
  booking/package payment, including deposit and final payment even if an
  earlier session was attended. A revised policy applies only to bookings
  confirmed after its effective time; V1 MUST NOT create a session-scoped
  partial refund, credit, voucher or separately settled session.
- **FR-021c**: `POST /workshop-bookings/{bookingCode}/mland-cancellation-remedy-cases`
  MUST accept an authorized Manager reason and `replacementOutcome` of
  `UNAVAILABLE` or `DECLINED` only, plus the authoritative 003.2
  `cancellationHandoffReference` used for the offered replacement. The server
  MUST derive the retained policy snapshot and refund amount; it MUST NOT accept
  caller-selected amount, ticket/session, or QR input.
- **FR-021a**: Any active Manager may create or revise the
  Mland-caused-cancellation refund policy for future bookings across all
  locations. The change MUST record an effective time and MUST NOT alter a policy
  snapshot retained by an already confirmed booking.
- **FR-021d**: `POST /workshop-refund-policies` MUST accept only `effectiveAt`
  and `wholeBookingRefundPercent` from an active Manager. The
  effective time MUST be future and the percentage MUST be a whole number from
  `0` through `100`; the revision MUST apply only to later-confirmed bookings
  and MUST NOT rewrite retained snapshots. The policy snapshot refund amount MUST
  be `floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND.
- **FR-021e**: `GET /workshop-refund-policies` MUST be available to any active
  Manager and MUST return the global policy revision history.
  A Guest or Member MUST instead receive only the retained policy snapshot and
  refund outcome permitted for their own booking through its final-bill/case
  projection.
- **FR-021b**: The public REST contract MUST expose required-session change and
  Mland-caused remedy as commands in the affected booking context. It MUST
  expose prospective refund-policy revision as a separate central resource, not
  as an operation scoped to one booking.
- **FR-022**: The system MUST distinguish service, final-bill, and gateway
  payment/refund states, return safe typed correlation-linked errors, and never
  expose raw tokens, payment payloads, secrets, or unnecessary PII.
- **FR-022a**: Every public REST command that changes settlement state MUST
  require an `Idempotency-Key`. A retry with the same key and semantically
  identical request MUST return the original outcome without repeating the
  command; a reused key with changed content MUST return a safe typed conflict.
  A successful command response MUST return the current safe settlement
  projection.
- **FR-022d**: Client-originated commands MUST use `Idempotency-Key`; identical
  same-key retry MUST return the original response and changed reuse MUST return
  `409 IDEMPOTENCY_CONFLICT`. VNPay IPN/QueryDR MUST instead deduplicate by the
  verified provider transaction/reference and safely acknowledge a duplicate.
  The public contract MUST return typed safe errors as `400` for malformed input,
  `401` for missing/invalid authentication, `403` for authority denial, `404` for
  missing/not-visible booking, `409` for conflict, and `422` for valid input that
  is business-ineligible.
- **FR-022b**: The public REST contract MUST represent final bill and charge as
  business resources. Customer cancellation, required-session change, and
  Mland-caused remedy MUST each create or update an auditable case resource with
  its own reference and lifecycle; they MUST NOT be untracked action-only
  operations.
- **FR-022b1**: Settlement cases MUST use `OPEN`, `RESOLVED`, `REJECTED`, or
  `FAILED`. Only cases awaiting gateway evidence MAY use `PENDING_PROVIDER`; only
  deferred-payment cases MAY use `EXPIRED`. A safe case projection MUST contain
  case reference, type, status, booking code, and correlation reference, and
  MUST disclose policy/amount outcome only to an authorized caller.
- **FR-022c**: The public REST contract MUST use the booking-scoped routes
  `GET/POST /workshop-bookings/{bookingCode}/final-bill`,
  `POST /workshop-bookings/{bookingCode}/final-bill/charges`, and
  `POST /workshop-bookings/{bookingCode}/final-bill/finalizations`. It MUST use
  booking-scoped `POST` case collections named `cancellation-cases`,
  `required-session-change-cases`, `deferred-payment-cases`, and
  `mland-cancellation-remedy-cases`, plus central
  `GET/POST /workshop-refund-policies`.
- **FR-022e**: The public REST contract MUST provide `GET`
  `/workshop-bookings/{bookingCode}/cancellation-cases/{caseReference}`,
  `/workshop-bookings/{bookingCode}/required-session-change-cases/{caseReference}`,
  `/workshop-bookings/{bookingCode}/deferred-payment-cases/{caseReference}`, and
  `/workshop-bookings/{bookingCode}/mland-cancellation-remedy-cases/{caseReference}`.
  Each route MUST return only the safe case projection authorized for the
  requesting actor: an owning Member uses authenticated ownership and a Guest
  supplies the booking code plus one stored contact phone or email for that
  request. The routes MUST NOT disclose raw gateway evidence, tokens, QR payloads,
  or unnecessary contact data.

## Key Entities

- **Booking Settlement**: The settlement view of one booking/package, with frozen
  package price, paid deposit, required-session completion and final state.
- **Final Booking Bill**: Remaining package amount, charge/adjustment lines,
  paid deposit, payments, and outstanding balance.
- **Charge Line**: Immutable final-bill charge with either a permitted
  customer-selected fixed-price catalogue source or a Staff/Manager-confirmed
  variable material/service source.
- **Deferred-Payment Exception**: Manager-approved time-bounded final-balance
  request while the shop retains the package output.
- **Refund Case**: Customer-requested or Mland-caused whole-booking/package
  direct-payment refund with policy snapshot, amount, gateway outcome, and
  consequences.
- **Reschedule Record**: Audited booking-level replacement of one or more future
  required sessions, including the prior/resulting session sets and the
  all-or-none 003.2 outcome.

## Success Criteria

- **SC-001**: 100% of acceptance-test bill corrections preserve original evidence
  and use a separate authorized void/adjustment.
- **SC-002**: 100% of release attempts with an outstanding balance are rejected
  unless a valid Manager exception exists.
- **SC-003**: 100% of invalid/duplicate/mismatched/unsigned/late payment or refund
  events do not settle the related bill/refund twice.
- **SC-004**: 100% of policy tests calculate the correct earliest-session refund
  percentage and no customer path refunds only a session/ticket/participant.
- **SC-005**: 100% of successful required-session-change tests transfer all and
  only affected capacity/ticket entitlements atomically while preserving the
  persistent QR and maintaining the resulting package/session-set invariants.

## Assumptions and Dependencies

- Feature 003.1 supplies confirmed direct bookings, frozen price, verified deposit
  evidence, and protected customer access.
- Feature 003.2 owns session/capacity/ticket/attendance states and cancellation
  handoffs; this feature must not duplicate normal check-in/configuration.
- The payment gateway is the source of verified final-payment/refund outcomes.
  Provider details and credentials belong to later approved plan/contracts.
- Payment/audit evidence follows the five-year baseline in `spec-general.md`;
  no card data is stored.
- Performance, availability, accessibility, deployment origins, and role-code
  mapping remain TBD. Account Management owns canonical role codes; this feature
  owns its Settlement action rules and must not infer them from display labels,
  Firebase claims or retired codes.

## Out of Scope

- OTA payment/refund, commission/payout/reconciliation, and OTA cancellation events.
- Session configuration, normal check-in/finalization, booking creation/deposit flow.
- Default credit, automatic link renewal, lower-price credit/refund, customer
  self-service reschedule or partial cancellation/refund.
- Accounting, tax, general ledger, debt collection, or general reconciliation.
