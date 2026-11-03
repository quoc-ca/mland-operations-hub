# T040 blocker — direct Mland add-on after finalization

T040 remains open. The current contracts do not define the direct-Mland separate-bill command required when a direct booking's final bill is already `FINALIZED`.

## Verified repository facts

- `POST /api/v1/workshop-bookings/{bookingCode}/final-bill/charges` only appends to an `OPEN` final bill.
- The existing direct-bill facade and `settlement_addon_bills` persistence are OTA-channel specific and require a channel booking/quote reference.
- Reusing that OTA path for a direct Mland booking would mix channel payment/payout facts and violate the isolation rule.

## Contract required before implementation

The owner must approve the endpoint and DTOs, direct-booking origin guard, Staff/Manager authorization, idempotency key, separate-bill lifecycle, payment owner/evidence handoff, and customer-safe read projection. Once approved, implementation and integration tests can proceed without inventing a payment flow.
