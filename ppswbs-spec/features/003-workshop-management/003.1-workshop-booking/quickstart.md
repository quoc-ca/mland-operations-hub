# Quickstart Validation: Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-10-26
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

This is a future implementation-validation guide, not evidence that code exists.
Read [spec.md](spec.md), [data-model.md](data-model.md), and the draft
[OpenAPI contract](contracts/workshop-booking.openapi.yaml) first.
Keep observed test results in the approved CI report/artifact; do not append
per-run evidence to this guide.

## Preconditions

1. Use a verified non-production environment with the approved Firebase and
   VNPay test arrangements described in
   [VNPay configuration preflight](../contracts/vnpay-configuration-preflight.md);
   never use production customer data.
   If legacy booking/customer data is present, use an approved append-only
   preservation migration and upgrade/recovery evidence instead of resetting it.
2. Seed one published Basic Ring package and one direct-Mland Silverclay package
   with a valid VND price per participant; include a promotion case whose
   post-promotion amount is odd VND.
3. Create published common workshop sessions in one branch with a shared
   capacity pool. Provide two non-adjacent same-day slots and two slots across
   different days.

## Core scenarios

1. Call session discovery with one package, one branch, and valid `from`/`to`
   range. Verify it returns only customer-safe eligible-session snapshots, then
   run the approved frontend journey from `/workshops` through
   `/workshops/:packageId` to `/workshops/:packageId/book`. Verify it requires
   no more than three primary interactions using user-visible controls; retain
   the result in the approved CI report/artifact.
2. Create a Basic Ring booking for N participants and one session. Verify one
   pending booking, one invoice at `N × price_per_participant`, exact 50% VND
   deposit, and an N-seat hold in that session.
3. Create a Silverclay booking for N participants and two valid same-branch
   sessions. Verify chronological ordinals, N held seats in both shared pools,
   one invoice and no cart/multiple booking records.
4. Attempt a duplicate session, different branch, adjacent same-day pair, full
   second session, or concurrent last-seat race. Verify no partial booking or
   hold persists. Verify final-slot-today/first-slot-tomorrow is accepted. Put
   one selected session in `SALES_PAUSED` and verify no new hold is created,
   while a pre-existing unexpired hold remains eligible to commit. Verify the
   Booking flow receives each sale-eligibility and hold outcome synchronously
   from 003.2 `WorkshopOperationsFacade`; a later event cannot change that
   outcome. Inject a local persistence failure during creation and verify that
   neither the booking nor any session hold commits.
5. Start payment, submit a valid signed matching gateway event, and verify one
   confirmed booking, one accepted deposit, and an N-ticket delivery-safe
   projection from 003.2 `WorkshopOperationsFacade`. For Silverclay,
   each ticket projection carries both session entitlements. Verify Booking
   neither receives attendance state nor derives ticket entitlement. Browser
   return alone must not confirm payment. Inject a local persistence failure
   during the post-validation commit and verify neither hold nor booking becomes
   confirmed. Verify the successful local transaction creates one immutable
   reference-only notification handoff; duplicate Feature 007 consumption does
   not create a second delivery request and delivery failure does not reverse
   booking/payment/ticket state.
6. Resubmit the same provider event and send invalid, mismatched, and late
   events. Verify no duplicate settlement/tickets and no confirmation after hold
   expiry. Verify failed attempts remain retryable before expiry. Verify
   `/workshop-booking/:bookingCode/payment-return` displays current server state
   only and cannot confirm payment from browser-return parameters.
7. Test Guest lookup using code plus exactly one matching contact proof, Member
   ownership lookup, and protected Magic-Link access. Verify pending projections
   contain no ticket; confirmed projections contain the full ticket set. Verify
   a Magic Link opens `/workshop-booking/access` with its opaque value in the fragment,
   immediately exchanges it by HTTPS POST, clears the fragment, and retains only
   the allowed projection in memory. Verify expired/revoked access shows no
   booking data and offers code-plus-stored-phone recovery.
8. Release a pending hold and let another expire. Verify all relevant session
   capacity is returned once, no QR is issued, and later payment cannot resume.
9. Exercise Guest/Member self-service resend with valid proof and an assigned
   Staff or branch-responsible Manager resend. Verify rate limits are enforced
   separately, an accepted request does not change booking/payment/ticket/contact
   or Magic Link state, and each required business audit record is safe and
   complete. Verify delivery retries appear only in operational delivery logs.
10. With a responsible Manager, revoke and rotate a Magic Link. Verify revoke is
   immediate; rotate revokes the old link, queues replacement delivery only to
   the existing booking-contact email, and a delivery failure never restores the
   old link. Verify Guest booking-code plus stored-phone lookup remains usable.
   Verify resend and rotation each create a distinct immutable notification
   handoff without putting recipient, QR/ticket credential, or Magic-Link value
   in the published event.
11. Submit a signed success after hold expiry. Verify it cannot confirm the
    booking, restore capacity, issue tickets, or create another payment link.
    Verify only the branch-responsible Manager can record/escalate a safe audited
    follow-up. Attempt the same internal actions with inactive, unentitled, or
    wrong-branch actors and verify safe denial without business side effects.
12. Supply the approved idempotent final-balance-resolved conclusion from 003.3
    for a confirmed deposit-paid booking. Verify Booking changes only its
    financial status to `FULLY_PAID` once, retains only safe correlation/reference
    evidence, and does not receive raw final-payment data or calculate final
    balance. Replay the conclusion and verify no second transition. Verify a
    deferred-payment-link send alone cannot set `FULLY_PAID`.

## Required evidence

- API contract/validation/error tests, including safe unauthorised lookup.
- MySQL empty-baseline and approved upgrade/preservation migration evidence.
- Concurrent capacity-hold and idempotent payment-event tests.
- Authorization, scope and audit-evidence tests for internal resend, Magic-Link
  incident handling and late-payment follow-up, plus separate delivery-log tests.
- Spring Modulith `ApplicationModules.verify()` evidence.
- React client loading, validation, success, failure and Magic-Link recovery
  evidence in the approved frontend implementation environment.

**Verification exception**: No commands are run by this document because this
repository scope is specification/design only. Manual verification above becomes
executable only after the listed provider, facade and migration dependencies are
approved and a frontend implementation environment is available.
