# Implementation Plan: Customer Workshop Booking Lifecycle

**Branch**: `002-workshop-booking` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Implement the customer-facing workshop booking slice: browse published packages,
create a temporary pending booking, start/retry deposit payment, accept one
valid payment result idempotently, confirm the booking, generate a QR ticket,
notify the customer, and look up the booking. The feature manages local booking
state through `CONFIRMED`; post-confirmation cancellation/refund, in-store
check-in, completion, no-show, final payment, and external registration
synchronization remain future features. A durable handoff record is in scope,
but its external processing is deferred. Guest self-service lookup and
post-payment Magic Link viewing are in scope.
Pending and confirmed lookup responses are separate projections: pending is
resume-payment only with countdown/no QR; confirmed is ticket/details only with
QR/no payment countdown.

The existing draft email-confirmation classes/endpoints and pre-payment flow
are removed from this feature flow. They are replaced by post-payment
notification containing the QR ticket and protected Magic Link; payment-gateway
OTP is not captured or verified by Workshop Booking.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.4.1, Spring Data JPA, Spring Modulith
1.3.1, Thymeleaf, htmx, Bean Validation, Flyway, Firebase Admin SDK, mail
gateway and a VNPay adapter.

**Storage**: MySQL with append-only Flyway migrations; H2 is supplemental only.
The current development/test booking data may be reset for this feature's state
model migration after an environment preflight. Production/real customer data
must not be reset under this decision.

**Testing**: JUnit 5, Spring Boot Test, MockMvc, MySQL migration/integration,
payment adapter contract tests and Spring Modulith verification.

**Target Platform**: Server-rendered web application plus versioned REST API.

**Project Type**: Modular monolith web application.

**Performance Goals**: Prevent overselling and duplicate settlement under
concurrency; exact p95 targets remain a planning input, not an invented promise.

**Constraints**: JPA only, no raw SQL; facade-only cross-module access; versioned
REST DTOs and OpenAPI; Thymeleaf forms/links work without htmx/JavaScript; never
log/store OTP, secrets, raw signed payloads or unnecessary PII.

**Scale/Scope**: Guest and Member booking for published packages and configured
sessions. Hold is 15 minutes; payment link is at most 10 minutes. Cancellation,
refund, later states `CHECKED_IN`, `COMPLETED`, `NO_SHOW`, external
synchronization and final payment are not implemented here.

## Constitution Check

| Gate | Result | Plan response |
|---|---|---|
| Module integrity/data ownership | PASS | `workshopbooking` owns its layers; facades are cross-module boundaries. |
| JPA/versioned migration | PASS | JPA persistence and append-only Flyway migration. |
| Identity/sensitive data | PASS | Firebase verifies identity; MySQL authorizes; gateway owns OTP. |
| Contract/progressive web | PASS | Versioned DTO/OpenAPI contracts and server-rendered fallback. |
| Evidence-based verification | PASS | State, capacity, callback, auth, migration and Modulith tests. |
| Spec-before-code | PASS | Spec → plan → tasks → analyze precede implementation. |
| Simplicity | PASS | External synchronization and later lifecycle states are deferred. |

## Project Structure

```text
ppswbs-spec/features/002-workshop-booking/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/workshop-booking.openapi.yaml

ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/
├── workshopbooking/{web,application,domain,infrastructure,facade}
├── payments/{facade,infrastructure}
└── common/

ppswbs_backend/src/main/resources/{db/migration,templates/workshop-bookings,static,i18n}
ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/{workshopbooking,payments}
```

## Design decisions

1. Remove the current email-confirmation classes/endpoints from this feature
   flow and reconcile enum names before adding the new flow; do not silently
   layer contradictory transitions.
2. Reset only verified development/test booking data before applying the new
   state model; never use this decision to reset production data.
3. Create booking and participant hold atomically under the session gate.
3. Failed payment attempts are recorded as `FAILED` on the attempt only; the
   booking keeps `PENDING_PAYMENT/UNPAID` while the hold is active.
4. Replace the legacy booking enum in verified development/test data with
   `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED` and `CANCELLED` for explicit
   pre-payment hold release; reserve later states.
5. Confirm only after a signed, matching, non-duplicate gateway result.
6. Record a durable handoff for later external synchronization after local
   confirmation; do not make that system a local confirmation prerequisite.

## Generated design artifacts

- [research.md](research.md)
- [data-model.md](data-model.md)
- [contracts/workshop-booking.openapi.yaml](contracts/workshop-booking.openapi.yaml)
- [quickstart.md](quickstart.md)

No constitution violation is proposed, so no complexity exception is required.
