# T040 blocker — direct Mland add-on after finalization

> **Superseded — 2026-11-05.** The owner-approved
> [open-folio policy](../../direct-booking-open-folio-policy.md) forbids adding,
> changing, or removing an add-on after a direct folio is `FINALIZED`. The
> "separate direct-Mland add-on bill after finalization" contract below is no
> longer valid business behaviour; its `direct-addon-bills` operations are
> marked deprecated in the settlement OpenAPI, and T040 is redefined as the
> exceptional post-finalized correction flow. Its design was approved on
> 2026-11-05 as decisions D7–D10 (spec FR-004c; see
> `open-folio-lifecycle-proposal-2026-11-05.md`): Manager-only overcharge
> refund via VNPay partial refund, reusing the repurposed create/read routes;
> an undercharge is absorbed by Mland, so the payment-attempt route is removed. This record is kept unchanged as historical evidence of
> what was implemented.

T040 implementation is now present; this note is retained as the original blocker and handoff record. Release acceptance still requires an isolated MySQL/Flyway integration run for the new migration and verified payment-evidence path.

## Verified repository facts

- `POST /api/v1/workshop-bookings/{bookingCode}/final-bill/charges` only appends to an `OPEN` final bill.
- The existing direct-bill facade and `settlement_addon_bills` persistence are OTA-channel specific and require a channel booking/quote reference.
- Reusing that OTA path for a direct Mland booking would mix channel payment/payout facts and violate the isolation rule.

## Implemented contract

The implemented contract provides separate create/read/payment-attempt routes, direct-booking origin guard, Staff/Manager authorization, consent/idempotency, lifecycle `OPEN -> PENDING_PAYMENT -> PAID`, payment owner `SHOP_ADDON`, and verified signed IPN/QueryDR evidence. OTA channel-booking add-on persistence and payout facts are not reused or mutated.
