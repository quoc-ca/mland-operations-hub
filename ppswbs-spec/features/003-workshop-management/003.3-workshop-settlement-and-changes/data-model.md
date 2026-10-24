# Data Model: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Ownership Map

| Data | Owner | Constraint |
| --- | --- | --- |
| Booking, frozen price, deposit evidence | Workshop booking | Settlement consumes facade projections only. |
| Session, capacity, ticket, attendance | Workshop operations | Settlement requests atomic effects through its facade. |
| Capability and branch assignment | Account/personnel | Authorization truth; never copied. |
| Bills, charges, exceptions, refunds, reschedules | Workshop settlement | Immutable financial/audit history. |

## Entities

### FinalItemBill

One bill per item: item/booking references, frozen base amount, allocated deposit,
charges, reschedule increase, paid/outstanding amount, settlement and release state.
Original price and accepted payment evidence are immutable.

### ChargeLine and Adjustment

Charge has bill reference, amount/currency, description, assessment actor/time and
audit reference. Correction creates a linked void/adjustment; it never overwrites
the original charge.

### DeferredPaymentException

Contains bill, approved amount, expiry, reminder state, actor, reason and safe
provider reference. At most one active exception exists per bill; reissue is a
new audited action after expiry or terminal failure.

### RefundCase

Has BOOKING or WORKSHOP_ITEM scope, policy snapshot where applicable, amount,
gateway reference and REFUND_PENDING, REFUNDED or FAILED outcome. Only REFUNDED
permits Operations ticket revocation and capacity release.

### RescheduleRecord

Stores item, source/target session, ticket references, participant count, price
difference, actor/reason/outcome and audit reference. Success requires Operations'
atomic capacity/ticket transfer.

## State and Integrity

Verified final payment changes an open bill to paid exactly once. An expired
deferred exception retains the item and outstanding balance. A refund remains
pending until verified provider outcome; it cannot restore a booking for use after
failure. Repeated/out-of-order callbacks must return the recorded result.
