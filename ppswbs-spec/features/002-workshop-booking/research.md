# Research: Customer Workshop Booking Lifecycle

## Decisions

1. **Payment authorization**: Payment Gateway owns QR/OTP authorization. The
   booking module only accepts authenticated provider results and never stores
   OTP or raw provider payloads.
2. **Two-axis state**: Service status and financial payment status are separate.
   Failed payment attempts keep `PENDING_PAYMENT/UNPAID` while the hold is active.
3. **Capacity**: Booking creation and participant hold are atomic under the
   session gate. Confirmation converts the hold; expiry releases it once.
4. **Callback safety**: Signed, matching, non-duplicate callbacks are required.
   Browser Return URLs never confirm payment.
5. **External boundary**: External Registration System synchronization is later;
   local confirmation records a durable handoff but does not wait for it.
6. **Existing code reconciliation**: Current email-confirmation classes and
   endpoints are removed from this feature flow; post-payment notification and
   Magic Link replace the pre-payment email gate. Enum names still require
   migration reconciliation.
7. **Lookup separation**: Guests use booking code plus normalized contact phone
   or a protected post-payment Magic Link; Members use authenticated ownership.
   Staff/POS check-in remains a later feature.
8. **Magic Link lifetime**: A post-payment Magic Link expires automatically at
   the associated workshop end time and is read-only/revocable.
9. **Legacy data**: Current development/test booking data may be reset before
   the new state model is applied. Production/customer data requires a separate
   preservation or migration decision and is not covered by this reset.
10. **Payment failure**: `FAILED` is an attempt-level outcome. The booking stays
   `PENDING_PAYMENT/UNPAID` while its hold is active, so retry remains possible.
11. **Booking enum**: Because development/test data is reset, replace the legacy
   booking enum with current feature states `PENDING_PAYMENT`, `CONFIRMED`,
   `EXPIRED` and `CANCELLED` for explicit pending-hold release; reserve later
   lifecycle states without transitions.

## Alternatives rejected

- Mland-managed OTP: duplicates gateway security and risks credential leakage.
- Booking confirmation on browser redirect: vulnerable to spoofing and replay.
- Distributed confirmation with the external registration system: adds failure
  coupling and is outside the approved feature boundary.

## Deferred for plan/task review

- Cancellation/refund feature boundary and its future provider contract.
- Exact Guest lookup proof.
- Provider refund contract and final-payment behavior.
- Exact performance targets.
