# Implementation Plan: Customer Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-11-14
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.1-workshop-booking`
**Status**: Approved — updated 2026-11-14 to the implemented group/ticket model and re-approved the same day by Hồ Hoàng Quốc (owner Feature 003) in session.
(Previous status 2026-11-05: "Draft design — not approved for implementation"; the
2026-11-05 single-booking design is kept for history at the end of this file.)
**Spec**: [spec.md](spec.md) (FR-030–FR-052a are the current direct-booking requirements)
**Tasks**: [tasks.md](tasks.md) Phases 8–10 (T089–T107)
**Research baseline**: [research.md](research.md) and [REBASE-NOTES.md](REBASE-NOTES.md)

## Summary

003.1 is the direct-Mland customer booking core. Since the 2026-11-12 and
2026-11-13 decisions (spec FR-030–FR-051) a direct Guest or Member booking is
**only a booking group**: a cart of one or more package lines that, when
confirmed, creates one booking group and one ticket booking per person. One
person is a group with one ticket (FR-043, FR-044). The single
`WorkshopBooking` flow (one Package per booking, `ceil(post/2)` deposit,
rejection of every late payment) is removed for direct bookings (FR-046,
V132/V133) and survives only for OTA bookings received through 003.4.

The implemented flow is:

1. **Cart quote** (`POST /api/v1/workshop-cart-quotes`, Guest or Member):
   re-reads each line's published Package and sessions through
   `WorkshopOperationsFacade`, previews benefits with Feature 006, allocates the
   programme discount and points per ticket (FR-033, FR-034), computes each
   ticket's booking-time collection (FR-035) and the group payment mode
   (FR-036). Every cart, Guest included, gets the largest applicable automatic
   workshop discount unless a Member picks a voucher, and the response states
   which programme applied in `appliedDiscount` (`NONE`/`AUTO`/`VOUCHER`; T105).
   A quote holds no capacity or benefit.
2. **Group hold** (`POST /api/v1/workshop-booking-groups`): accepts the quote
   once, holds every line in one transaction all or none (refusal lists
   `failingLines`, FR-031), reserves the group's one Loyalty transaction, and
   snapshots each ticket's price. `holdExpiresAt` is 12 minutes after the hold
   (`workshop.booking.group-hold-minutes`, default 12) and `paymentDeadlineAt`
   is 2 minutes earlier, i.e. a 10-minute payment window (FR-032).
3. **Group payment**: a `VNPAY` group pays once for the whole group through
   `POST …/{groupCode}/payment-attempts` (link expiry = `paymentDeadlineAt`,
   QueryDR at minutes 10 and 11, never extending the hold); an
   `INTERNAL_FREE` (0 VND) or `INTERNAL_DEFERRED` (group collection below the
   5,000 VND VNPay floor) group is confirmed inside group creation (FR-036).
4. **Per-ticket confirmation** (FR-038): verified evidence commits every hold
   through Operations, sets each ticket's financial status from its snapshot,
   settles the Loyalty benefits once, opens one group Magic Link and publishes
   one `MlandTicketConfirmedEvent` per ticket so that 003.3 opens one OPEN folio
   per ticket. Expiry releases the whole group (FR-039).
5. **Late-payment review** (FR-040/FR-040a): signed evidence for an expired
   group is evaluated against the five conditions and either re-confirmed
   automatically (`LATE_PAYMENT_RECONFIRMED`) or queued for a Manager
   (CONFIRM only when conditions 4/5 alone failed; REFUND hands a 100% bank
   refund to 003.3).
6. **Recipients and QR** (FR-041): the buyer (or Staff after verifying the
   buyer) may send a ticket to a recipient; a change rotates that ticket's QR
   through 003.2.
7. **Recovery routes** (FR-047–FR-049): owning-Member group read and list,
   Guest group lookup, group Magic-Link exchange, pending-hold release,
   confirmation resend and Manager Magic-Link incidents.
8. **Public discounted price** (FR-052/FR-052a): backend part done in T105;
   the five public frontend surfaces are T106.

003.1 still stops at ticket issuance, delivery, recovery and the per-ticket
facts other features read; check-in and attendance (003.2), folios,
cancellation, refund and changes (003.3) and OTA (003.4) are outside it.

## Technical Context

| Area | Design baseline |
| --- | --- |
| Runtime | Java 21, Spring Boot REST, Spring Modulith, JPA/Spring Data, MySQL and append-only Flyway migrations (group model V113–V133). |
| Client | Separate React + Vite client using versioned REST APIs; no backend-rendered booking UI. |
| Identity and authorization | Firebase bearer tokens prove identity only. Account Management supplies canonical `MEMBER`, `STAFF`, `MANAGER`, or `ADMIN` role, active status and `activeLocationIds`; Booking applies its own support-action rules against the group line's canonical location. Guest access uses one stored contact proof of the group, a protected group Magic Link, or (for a recipient change) the single-use `buyerVerificationReference` of an OTP to the original contact email. |
| State | Persisted group/line/ticket/snapshot/hold/payment/review state; one group `holdExpiresAt` (12 min) and `paymentDeadlineAt` (10 min); booking-critical local work uses one MySQL transaction; the unquoted cart lives only in the browser (FR-045); no new global session/token store. |
| Payment | Shared `paymentintegration` ingress verifies VNPay once and routes redacted evidence by immutable merchant reference; a group attempt's evidence goes to `GroupPaymentEvidenceService`, anything else is refused by the single-booking path. |
| Benefits | Feature 006 cart contract (006 T098, D-010) plus automatic workshop discount (006 FR-044, D-013, D-014); 003.1 allocates per ticket with `TicketBenefitAllocator`. |
| Verification | Unit/integration (MySQL for schema and late payment), contract (`WorkshopBookingContractIT`), concurrency, callback/idempotency, migration and Spring Modulith boundary verification. |
| Unknowns | VNPay integration values/environments (T005/T046), Manager alert recipient source (FE-001, T094), automatic-discount behaviour of late-payment condition 3 (FR-040), and production rollout approval. |

### Approved React route boundary

The React + Vite client owns `/workshops` (browse), `/workshops/:packageId`
(detail/session selection), `/workshops/:packageId/book` ("Đặt ngay" one-line
quote and confirmation), `/workshop-booking/cart` (multi-line cart, FR-045),
`/workshop-booking/lookup` (Guest/Member recovery), and
`/workshop-booking/access#token` (group Magic-Link exchange). Browser return
renders at `/workshop-booking/:groupCode/payment-return`; it reads only current
server-confirmed group state and never promotes redirect parameters to payment
evidence. These routes are client routes, not Java REST endpoints or SSR pages.
*(Updated 2026-11-14 from the T102/T103 implementation; the 2026-11-05 list used
`:bookingCode` and had no cart route.)*

## Constitution Check

| Gate | Result | Plan response |
| --- | --- | --- |
| Module and data ownership | PASS | `workshopbooking` owns groups, lines, ticket bookings, price snapshots, holds references, group payment attempts, late-payment reviews, recipients, buyer verifications, Magic Links and notification intents; it reaches 003.2, 003.3, 006 and FE-001 only through their facades/events. |
| Separate frontend/REST | PASS | Contract is JSON `/api/v1` ([workshop-booking.openapi.yaml](contracts/workshop-booking.openapi.yaml), 14 group paths); the group Magic Link resolves to client-readable data, never server-rendered HTML. |
| Identity and sensitive data | PASS | No participant identity, OTP (only salted hash), raw gateway payload, raw Magic-Link secret or unmasked recipient email in audit; contact proof never travels in a URL. |
| Data evolution | PASS (dev/test) | Only dev/test data exists (FR-043). V113–V131 add the group model; V132 deletes direct single bookings and V133 drops tables only they used. No applied migration was edited. Any data-bearing environment still needs its own preservation plan. |
| Evidence and concurrency | PASS | All-or-none group hold, one group payment, IPN/QueryDR idempotency, late-payment review with one winner in a capacity race, and confirmation replay are covered by `WorkshopCartBookingTest`, `GroupConfirmationTest`, `GroupPaymentQueryDrRecoveryTest`, `LatePaymentReviewIT`; the full MySQL group flow is T097 (open). |
| Explicit decisions | CONDITIONAL | Decisions of 2026-11-12/13/14 are recorded in spec.md. Decided 2026-11-14: Manager alert source waits for FE-001 (no interim list); late-payment condition 3 recomputes an automatic discount. Decided 2026-11-14: group-creation `Idempotency-Key` aligns to 16–128 (T107). |

## Module boundaries and flows

`workshopbooking` synchronously reads each line's published-Package snapshot
and sale eligibility from `WorkshopOperationsFacade`, asks it to reserve,
commit or release a whole group's capacity (`ReserveGroupCapacityCommand`,
all or none), and to rotate a ticket's QR on a recipient change. It reads
Feature 006 benefits through `LoyaltyFacade` (cart preview, one
`reserveAcceptedTransaction` per group, settle/release outcomes). It reads
FE-001 account facts for authorization. It publishes
`MlandTicketConfirmedEvent` (per ticket, consumed by 003.3
`TicketConfirmedFolioConsumer`), `LatePaymentReviewQueued` and reference-only
notification events that Feature 007 resolves through
`WorkshopBookingNotificationFacade`. For 003.3 it exposes
`findTicketBookingByOperationsReference` (T099) and, decided 2026-11-14,
`getTicketFolioContext` and `consumeBuyerProof` (T107, see
[../contracts/workshop-interfeature-facades.md](../contracts/workshop-interfeature-facades.md)).

**Cart quote flow.** Validate lines; re-read published Package price and
sessions; preview with 006 (`VOUCHER` stays a voucher; otherwise `AUTO` with the
requested points; a Guest is previewed as `TrustedSourceContext.GUEST`, and a
Guest voucher/points request is refused with `CART_BENEFITS_NEED_A_MEMBER`
before 006 is called); allocate the programme discount and points per ticket by
largest remainder; compute `collectionKind` per ticket and `paymentMode` for the
group; persist the quote with its lines, selection, eligible ticket indexes,
point value, terms digest and policy revision (V114/V118). The response reports
`appliedDiscount`; `voucherDiscountVnd` carries whichever programme discount
applied (T105).

**Group creation flow.** Under a row lock accept the quote once
(`QUOTE_EXPIRED`, `QUOTE_ALREADY_ACCEPTED`, `QUOTE_CHANGED`), hold every line
through Operations all or none, make one Loyalty reservation for the group
(refuse `QUOTE_CHANGED` if the accepted discount or points differ), create the
group, lines, line sessions with hold references, one ticket booking and one
immutable price snapshot per person, all in one MySQL transaction, protected by
`Idempotency-Key`. An internal group (`INTERNAL_FREE`/`INTERNAL_DEFERRED`) is
confirmed in the same call.

**Group payment flow.** One attempt for the group collection (≥ 5,000 VND),
expiring at `paymentDeadlineAt`; a retry returns the live attempt and never
extends the hold. Verified evidence is routed by the group attempt's merchant
reference and is idempotent. `GroupPaymentQueryDrRecoveryScheduler` queries at
minutes 10 and 11 only when no verified terminal IPN exists and accepts only a
signed, successful, exactly matching answer.

**Confirmation flow.** Commit every line's holds (Operations issues the
tickets), store each ticket's Operations reference and QR, set ticket financial
status from its snapshot (`FULLY_PAID`/`DEPOSIT_PAID`/`UNPAID`), settle the
group's Loyalty transaction once (`WORKSHOP_DEPOSIT_ACCEPTED`), open one group
Magic Link expiring with the last session, queue the buyer confirmation email,
and publish one `MlandTicketConfirmedEvent` per ticket after commit. Replays
return the stored outcome. `GroupHoldExpiryScheduler` releases an unpaid group
once and releases its benefits (`UNPAID_EXPIRY_CONFIRMED`).

**Late-payment flow.** One review per payment attempt (IPN and QueryDR share
it). Conditions 1 (session capacity only; no channel quota in V1) to 5 are
evaluated; automatic re-confirmation holds fresh capacity all or none and is a
new acceptance. Otherwise the review enters the Manager queue
(`GET /late-payment-reviews`, ordered by earliest session), the payer gets the
"payment arrived late" email with the FR-040a deadline, and a Manager decides
CONFIRM (conditions 4/5 only, 1–3 rechecked) or REFUND through
`LatePaymentRefundPort` (003.3 opens a `LATE_PAYMENT_NOT_RECONFIRMED` bank
refund in `PENDING_ACCOUNT`; never VNPay Refund). Condition 3 fails for any
group with a voucher, points or programme discount (decision 2026-11-13; T105
known effect for automatic discounts — owner decision open, see spec FR-040).
The Manager alert email is wired to `ManagerAlertRecipientSource`, whose
FE-001 source is still open (T094).

**Recipient flow.** `PUT /workshop-tickets/{ticketCode}/recipient` with buyer
proof (Member bearer + ownership; Guest OTP → single-use
`buyerVerificationReference`; Staff/Manager with location scope and reason).
A first send keeps the QR; a change rotates it through 003.2 and counts once
online (max 3); masked audit (V127).

**Recovery flow.** Group read for the owning Member, Member list (30 newest,
expired only within 24 h), Guest lookup with one contact proof, group Magic-Link
exchange, pending-hold release (refused once a payment attempt exists), resend
reusing the active link, and Manager revoke/rotate (FR-047–FR-049).

**Removal of the single flow.** T100 removed the single-booking controllers,
services, entities and their tests; V132 deleted direct `workshop_bookings` rows
and V133 dropped tables only they used. The facade operations the 003.3
booking-wide routes still call stay until 003.3 T106 lands (spec FR-046 note).

Events occur only after Booking's local state transition; an event never
determines capacity or whether a group confirms.

## Contract and state design

The public contract is [workshop-booking.openapi.yaml](contracts/workshop-booking.openapi.yaml)
(14 group paths, T104/T105). `workshop-booking-cart.openapi.yaml` is an empty
pointer. The shared VNPay ingress is documented separately.

- Group status: `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CANCELLED`.
- Ticket status: `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CANCELLED`,
  `COMPLETED`, `NO_SHOW`; ticket financial status `UNPAID`, `DEPOSIT_PAID`,
  `FULLY_PAID`, `REFUND_PENDING`, `REFUNDED` (V113).
- Collection kind per ticket: `FREE`, `FULL`, `DEPOSIT`,
  `DEFERRED_BELOW_VNPAY_MINIMUM`; group payment mode `VNPAY`, `INTERNAL_FREE`,
  `INTERNAL_DEFERRED`.
- Late-payment review outcome: `AUTO_RECONFIRMED`, `PENDING_MANAGER`,
  `MANAGER_CONFIRMED`, `REFUND_REQUESTED`; decision `CONFIRM`/`REFUND` (V128).
- `Idempotency-Key` protects every command (16–128 characters; group creation
  was aligned to 16–128 by the decision of 2026-11-14 and the controller now
  refuses anything outside it, T107).

The group Magic Link (one per group) follows the single-booking rules carried
over by FR-048: hash persisted, raw URL only in the protected notification
snapshot for Feature 007, scrubbed on revoke/rotate and by a one-minute expiry
sweep, fragment-only in the browser.

## Data, migration, and operational constraints

The model is in [data-model.md](data-model.md) ("Booking group and ticket
bookings", implemented tables V113–V133). Monetary snapshots are integer VND.
No applied migration may be edited. Only dev/test data exists (FR-043);
environments were reseeded after V132/V133. Deployment topology, origins,
provider credentials, monitoring ownership and SLOs remain outside this plan's
decision authority.

## Verification plan

Evidence already recorded per task in [tasks.md](tasks.md) (T089–T105). The
remaining verification:

- **T097 (open):** MySQL integration of the full group flow — paid group, mixed
  free/paid group, 0 VND group, deferred group, capacity refusal of one line,
  expiry, late payment auto/manual paths, recipient change and QR revocation,
  plus the T105 automatic-discount cases (Guest cart with an automatic
  discount through confirmation, Member voucher replacing it, cart below the
  discount minimum, per-ticket remainder allocation, and the late-payment
  condition 3 effect).
- **T106 (open):** component tests of the five FR-052 surfaces.
- **T107 (open):** narrow tests of `getTicketFolioContext`/`consumeBuyerProof`
  and `ApplicationModules.verify()`.
- **T005/T046 (open):** real VNPay sandbox evidence; until then fake-provider
  and signed-fixture tests are the interim evidence (constitution
  manual-verification rule).
- Every change keeps `WorkshopBookingContractIT` (contract paths = controller
  mappings, no FR-046 path) and `ApplicationModules.verify()` green.
- The approved frontend E2E suite must still prove that a visitor reaches
  booking entry from the workshop listing in no more than three primary
  interactions (SC-005).

## Design risks and handoff conditions

1. **Manager alert recipients (T094) — closed 2026-11-14:** FE-001 provides
   `AccountFacade.listActiveManagerEmails()` and `AccountManagerAlertRecipientSource`
   reads it, so the Manager alert email is sent as well as queued in-app.
2. **Late-payment condition 3 and automatic discounts — decided 2026-11-14:**
   condition 3 recomputes the automatic workshop discount at review time and
   passes when it still applies; a group that used a voucher or points still
   fails it (spec FR-040, `LatePaymentReviewService`).
3. **Legacy facade operations (T100 remainder):** removal waits for 003.3 T106,
   which itself follows 003.3 T099–T101 and T105; T107 provides the facade
   operations 003.3 T099/T106 need.
4. **Real VNPay** needs the provider/environment inputs of
   [vnpay-configuration-preflight.md](../contracts/vnpay-configuration-preflight.md).
5. **Owner re-approval**: given by Hồ Hoàng Quốc (owner Feature 003) in session
   on 2026-11-14 for this updated plan. The approval covers the design recorded
   here; it does not by itself approve code, schema, deployment or contract
   changes, each of which still needs its own task and evidence.

---

## Superseded design of 2026-11-05 (history)

*(superseded by FR-030–FR-052a, 2026-11-12/13/14; kept for history. Read every
statement below through the sections above.)*

**Former summary.** 003.1 was the direct-Mland customer booking core. One
booking purchased exactly one Package, with no cart of unrelated Packages.
Wax Casting selected two sessions at one location; both reservations were
committed or neither existed. The booking froze
`participant_count × price_per_participant`, the post-promotion amount, a
`ceil(post_promotion_amount / 2)` deposit and the remaining amount, created one
deposit invoice, and on valid gateway evidence confirmed the booking and issued
one QR ticket per participant. *(superseded by FR-030, FR-035, FR-044)*

**Former Constitution Check.** Data evolution was CONDITIONAL (reset only for
dev/test after preflight); evidence and concurrency covered "atomic
multi-session hold, payment callback idempotency, late-payment rejection, and
capacity races". *(late-payment rejection superseded by FR-040/FR-040a)*

**Former flows.** Create: validate contact, reserve `participant_count` places
in every session through Operations, persist pending booking, invoice and
expiry in one transaction. Payment: start/retry only while the 10-minute hold
is active; commit the hold set and confirm on matching evidence; obtain the
ticket projection; publish a reference-only notification event. A signed
success after hold expiry never confirmed; only a Manager could record or
escalate a follow-up without restoring capacity. *(superseded by FR-031,
FR-032, FR-038, FR-040, FR-046)*

**Former contract and states.** Booking create, payment start, contact-proof
and Member lookup, Magic-Link exchange, pending-hold release, confirmation
resend, Manager Magic-Link incidents and Manager late-payment follow-up on
`/workshop-bookings/{bookingCode}`; booking states `PENDING_PAYMENT`,
`CONFIRMED`, `EXPIRED`, `CANCELLED`, plus `COMPLETED`/`NO_SHOW` from 003.3 (D13)
and financial `UNPAID`/`DEPOSIT_PAID`/`FULLY_PAID`/`REFUND_PENDING`/`REFUNDED`
(D14). *(routes removed by FR-046; the D13/D14 statuses remain on OTA
`WorkshopBooking` and on ticket bookings)*

**Former verification plan.** Quickstart checks of each one-session offer,
two-session Wax Casting, odd/even post-promotion amount with rounded-up deposit,
valid/invalid/duplicate/late gateway events, ticket issuance and lookup,
Magic-Link revocation, hold expiry and authorization-safe lookup, plus MySQL
migration coverage, API contract validation and `ApplicationModules.verify()`.
*(superseded by the verification plan above)*
