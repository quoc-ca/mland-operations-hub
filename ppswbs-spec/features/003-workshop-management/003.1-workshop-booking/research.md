# Research: Direct-Mland Workshop Booking Core

**Date created**: 2026-10-25
**Date updated**: 2026-10-25
**Feature Branch**: `003.1-workshop-booking`
**Status**: Research rebase complete — input for clarification; not an implementation plan or approval to code.

## Purpose, authority, and status of this research

This file makes the business baseline for the customer-facing, **direct-Mland** workshop-booking core self-contained before the shared [`research.md`](../../../research.md) is eventually retired. It does not move operation, settlement, change, refund, or OTA work into this feature.

| Source | Use in this research |
| --- | --- |
| [FE-03 — Workshop Management](../../../research.md#fe-03): “Chính sách giá theo số người”, “Chú thích — suất làm và vé check-in”, “Khám phá package và tạo booking”, “Thanh toán cọc, xác nhận và tra cứu”, “Mô hình ticket cá nhân”, and “Quyền sở hữu và phân phối ticket” | Inherited business baseline for the direct-Mland V1 booking flow. The source calls several rules “Đề xuất đã chọn”; the 2026-10-25 owner decision below supersedes its former `BASE_PLUS_EXTRA` rule for 003.1 V1. |
| [General spec](../../../spec-general.md) §§1–4 | React + Vite is a separate REST client; Firebase proves identity while Mland authorizes; a valid signed payment result and a shared 10-minute payment-link/workshop-hold deadline are constraints for this booking flow. |
| [Project constitution](../../../.specify/memory/constitution.md) principles I–V | Modulith ownership/facade rules, REST DTO/OpenAPI boundary, data/security/evidence constraints, and explicit-decision discipline. |
| [REBASE-NOTES.md](REBASE-NOTES.md) | Confirms that the old one-session booking aggregate must be replaced by booking → package/course → required sessions → attendance tickets. |
| 003.2, 003.3, and 003.4 specifications | Define the ownership boundary recorded below. They do not approve new 003.1 APIs, schemas, migrations, roles, or frontend behavior. |

## Direct-Mland V1 scope

003.1 owns the customer journey to browse eligible published workshop packages, create a pending booking for one selected package/course, hold capacity in every session required by that package, pay the deposit, receive the ticket set/Magic Link notification, and retrieve an authorized booking projection. V1 has no generic cart or booking containing unrelated packages. A Guest may create and look up a booking using the required contact proof; a Member may use authenticated ownership and may book for another person using a submitted contact snapshot.

This feature does not configure sessions, consume tickets at check-in, finalize attendance, collect post-workshop balances, process refunds/reschedules, or connect an OTA.

## Inherited decisions for the booking aggregate

### Booking, package/course, required sessions, and attendance ticket

**Fact — FE-03, “Chú thích — suất làm và vé check-in” and “Khám phá package và tạo booking”:**

```text
Booking (commercial order, payer/contact, one aggregate deposit)
└── One selected workshop package/course
    ├── Required session × one or more
    └── Attendance ticket × people attending the package
```

- A booking is one commercial/payment aggregate with one booking contact, one selected package/course, and one deposit payment. It cannot contain unrelated packages or operate as a generic cart.
- A package/course declares the required session set. A one-session package has one required session; a multi-session package such as Silverclay has every declared session as one purchased entitlement.
- An attendance ticket is one person’s independent admission/check-in right. It has its own opaque QR/ticket identity; it is not a separate commercial order or payment obligation.
- Capacity, later check-in, and later no-show are counted per ticket/session; price, deposit, and later remaining balance belong to the booking/package.

### Pricing and deposit

**Superseding decision — 2026-10-25, Hồ Hoàng Quốc:** 003.1 V1 supports
per-participant pricing only. A package has one valid price per participant,
and the frozen booking amount is the selected participant count multiplied by
that price. `BASE_PLUS_EXTRA`, shared-item/co-maker pricing, and a
`pricing_model` selection do not belong to V1.

- Packages provide their valid participant minimum/maximum and one valid price
  per participant.
- A rare request for several people to make one physical item is a Staff manual
  exception outside the V1 booking flow. It is not a customer-facing material
  add-on, because an additional person would affect ticket and capacity facts.
- The deposit is one half of the frozen post-promotion booking amount, rounded
  up to whole VND: `ceil(post_promotion_amount / 2)`. The remaining amount is
  the exact difference. One deposit invoice/payment exists per booking; a
  package's displayed per-participant price covers all of its required sessions.
- FE-006 owns voucher/point calculation. It must provide the post-promotion
  amount before 003.1 accepts the booking; 003.1 freezes the resulting amount,
  deposit and remaining amount together and never recalculates a later discount.
- Later material/service charges and the remaining balance are outside 003.1; their ownership belongs to 003.3.

### Atomic creation, holds, and pending state

**Fact — FE-03, “Khám phá package và tạo booking”; General spec §4:** on one booking submission, the system revalidates package, session, capacity, and required input together and creates, as one business result:

- a `PENDING_PAYMENT` booking with financial state `UNPAID`;
- its selected package/course, required-session set, and frozen price information;
- one attendance ticket for every participant of the package;
- one aggregate 50% deposit invoice; and
- a capacity hold for each ticket in its assigned session, sharing the payment-link deadline and lasting no more than 10 minutes.

The creation/hold outcome is all-or-nothing: if any required session is closed, full, invalid, or loses a capacity race, 003.1 must not leave a booking or capacity reservation. Session availability is calculated from confirmed tickets and unexpired ticket holds in that session, never from booking-row count.

### Deposit payment, confirmation, and tickets

**Fact — FE-03, “Thanh toán cọc, xác nhận và tra cứu”; General spec §4:**

- The payment link expires after at most 10 minutes. Browser return/redirect is display-only and never confirms payment.
- 003.1 changes a booking to `CONFIRMED`/`DEPOSIT_PAID` only after an authentic, non-duplicate gateway result matching the booking reference, amount, and currency. The provider owns OTP/payment authorization; 003.1 neither stores nor verifies OTP.
- Confirmation commits the ticket/session capacity and issues one QR ticket per purchased participant. QR payloads contain no PII or payment data.
- Failed/cancelled payment attempts leave the booking `PENDING_PAYMENT/UNPAID` while its hold remains active; expiry changes it to `EXPIRED` and rejects continued payment. A Guest/Member may explicitly release an active pending hold to `CANCELLED`; this produces no QR and is not a confirmed-booking cancellation or refund.

### Ticket delivery and authorized lookup

**Inherited decision — FE-03, “Quyền sở hữu và phân phối ticket”; “Thanh toán cọc, xác nhận và tra cứu”:** V1 uses `BOOKING_CONTACT_RECEIVES_ALL_TICKETS`.

- The submitted booking contact receives the confirmation notification, the Magic Link, and the complete ticket/QR set. The contact may distribute tickets to the group.
- V1 does not request, infer, or persist each participant’s name/email, and does not add ticket claim/transfer/direct-delivery behavior.
- The confirmation flow creates a read-only booking Magic Link and a delivery intent. Delivery failure does not reverse an accepted confirmation; it creates retry/follow-up work without exposing secret/payment payloads.
- A Guest looks up a booking with its code and the stored email or phone. A Member may access only an owned booking through verified identity. Pending lookup shows remaining-hold/resume-payment state with no QR; confirmed lookup shows the ticket set grouped by session with no payment countdown.

## Ownership boundaries

| Feature | Owner | 003.1 boundary |
| --- | --- | --- |
| **003.1 Workshop Booking** | Direct-Mland booking aggregate, contact snapshot, workshop-item price/deposit snapshot, ticket issuance, ticket/session holds, aggregate deposit evidence, customer lookup, Magic Link/delivery intent. | Produces confirmed direct-booking, ticket, capacity-commitment, and deposit evidence needed downstream. |
| **003.2 Workshop Operations** | Session/branch operation, capacity configuration, Staff assignment, ticket check-in, exceptions, attendance finalization/no-show. | 003.1 consumes eligible sale/session/capacity information but does not configure or check in a session. The future cross-feature facade/event contract remains to be designed. |
| **003.3 Workshop Settlement and Changes** | Post-session charges/final bills, final payment/release control, refund, reschedule, and Mland-caused cancellation remedies. | 003.1 freezes base price and records allocated deposit; it does not settle the remaining balance, change a confirmed booking, or refund it. |
| **003.4 Workshop OTA Connectivity** | Klook/future OTA availability, booking, ticket, and reconciliation integration. | 003.1 is direct-Mland only. The initial OTA catalogue is limited to basic ring packages completed in one session; the two-session Silverclay package is direct-Mland only. Any future group-pricing or co-maker behaviour needs separate 003.4 design and approval; it is not a V1 booking capability. |

## Reconciliation with legacy 003.1 artefacts

The current `spec.md`, `plan.md`, `data-model.md`, `quickstart.md`, task list, and OpenAPI are historical/rebase-blocked artefacts. Their one-package, one-session, booking-level `participant_count` model is not compatible with the aggregate described above. The legacy plan/task/OpenAPI also contain server-rendered UI assumptions that conflict with the separate React + Vite frontend baseline in `spec-general.md` and the constitution.

These findings do **not** approve a replacement API, database schema, migration, or frontend design. They establish the source material that the next clarification/design artifacts must preserve.

## Open questions and dependencies for clarification

| ID | Unknown or dependency | Impact if unresolved | Required next action |
| --- | --- | --- |
| R-01 | Formal REST request/response shape for one package/course booking, including how its required session set and participant count are represented. | The replacement OpenAPI, DTOs, validation, and persistence mapping cannot be finalized. The pricing/aggregate business rule above is already inherited; this is a contract-design task, not a request to re-decide it. | Clarify and record an approved contract design. |
| R-02 | Upgrade/rollout path for existing one-session booking/hold/invoice/ticket data in each environment. Current evidence permits development/test reset only; it does not authorize production/customer-data reset. | Migration and release tasks must remain blocked; applied migrations cannot be changed or silently bypassed. | Obtain a preservation/upgrade decision and evidence by environment. |
| R-03 | React Magic-Link client flow: token transport/exchange, expiry/revocation recovery, and frontend owner/repository boundary. | The old server-rendered Magic-Link route is incompatible with the current frontend baseline; client/UI/security contract cannot be invented. | Clarify the client/backend contract and recovery behavior. |
| R-04 | Public cross-feature contract through which 003.1 reads sale eligibility/session capacity from 003.2 and exposes confirmed ticket/capacity evidence downstream. | Facade/event direction, transaction boundaries, and integration verification cannot be designed safely. | Define the owning facade/event contract during plan design; do not use internal entities across feature boundaries. |
| R-05 | Account Management's published authorization facade is an external implementation dependency. The shared baseline fixes the role vocabulary as `MEMBER`, `STAFF`, `MANAGER`, and `ADMIN`; 003.1 consumes current authorization/scope decisions and does not map roles itself. | Internal resend, Magic Link revoke/rotate, and late-payment follow-up must call the published Account boundary at command time. The FE-001 provider implementation is outside 003.1 scope. | Treat the Account boundary as available for 003 design and implementation planning; verify the provider facade during integration without editing FE-001. |
| R-06 | Exact retention/storage mechanics for Magic-Link token material are not selected by the inherited business behavior. | A plan cannot select hashing, persistence/expiry cleanup, audit retention, or recovery behavior by assumption. | Clarify a consistent shared credential/state contract before implementation. |

## Assumption challenge

The tempting shortcut is to add a generic cart merely because some packages span more than one session. That confuses two different needs: purchasing unrelated packages and enrolling in one multi-session course. V1 needs only the latter. The lowest-complexity credible path is one booking for one package/course, with atomic capacity holds for all of its required sessions and one ticket per participant; 003.2–003.4 remain separate owners.

## Ready inputs for `speckit-clarify`

- Inherited direct-Mland V1 aggregate, pricing, hold, payment, ticket, delivery, lookup, and ownership boundaries are documented above with source references.
- `R-01` through `R-06` are the remaining questions/dependencies to classify as decided, deferred, or blocking for the replacement 003.1 specification.
- No endpoint, DTO, migration, role mapping, or frontend behavior is approved by this research alone.
