# Quickstart Validation: Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-10-25
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

This is a future implementation-validation guide, not evidence that code exists.
Read [spec.md](spec.md), [data-model.md](data-model.md), and the draft
[OpenAPI contract](contracts/workshop-booking.openapi.yaml) first.

## Preconditions

1. Use a verified non-production environment with the approved Firebase and
   VNPay test arrangements; never use production customer data.
2. Seed one published Basic Ring package and one direct-Mland Silverclay package
   with an even VND price per participant.
3. Create published common workshop sessions in one branch with a shared
   capacity pool. Provide two non-adjacent same-day slots and two slots across
   different days.

## Core scenarios

1. Create a Basic Ring booking for N participants and one session. Verify one
   pending booking, one invoice at `N × price_per_participant`, exact 50% VND
   deposit, and an N-seat hold in that session.
2. Create a Silverclay booking for N participants and two valid same-branch
   sessions. Verify chronological ordinals, N held seats in both shared pools,
   one invoice and no cart/multiple booking records.
3. Attempt a duplicate session, different branch, adjacent same-day pair, full
   second session, or concurrent last-seat race. Verify no partial booking or
   hold persists. Verify final-slot-today/first-slot-tomorrow is accepted. Put
   one selected session in `SALES_PAUSED` and verify no new hold is created,
   while a pre-existing unexpired hold remains eligible to commit.
4. Start payment, submit a valid signed matching gateway event, and verify one
   confirmed booking, one accepted deposit, and an N-ticket delivery-safe
   projection from 003.2. For Silverclay, each ticket projection carries both
   session entitlements. Verify Booking neither receives attendance state nor
   derives ticket entitlement. Browser return alone must not confirm payment.
5. Resubmit the same provider event and send invalid, mismatched, and late
   events. Verify no duplicate settlement/tickets and no confirmation after hold
   expiry. Verify failed attempts remain retryable before expiry.
6. Test Guest lookup using code plus exactly one matching contact proof, Member
   ownership lookup, and protected Magic-Link access. Verify pending projections
   contain no ticket; confirmed projections contain the full ticket set.
7. Release a pending hold and let another expire. Verify all relevant session
   capacity is returned once, no QR is issued, and later payment cannot resume.

## Required evidence

- API contract/validation/error tests, including safe unauthorised lookup.
- MySQL empty-baseline and approved upgrade/preservation migration evidence.
- Concurrent capacity-hold and idempotent payment-event tests.
- Spring Modulith `ApplicationModules.verify()` evidence.
- React client loading, validation, success, failure and Magic-Link recovery
  evidence after the client contract/owner is approved.

**Verification exception**: No commands are run by this document because this
repository scope is specification/design only. Manual verification above becomes
executable only after the listed provider, facade, frontend and migration
dependencies are approved.
