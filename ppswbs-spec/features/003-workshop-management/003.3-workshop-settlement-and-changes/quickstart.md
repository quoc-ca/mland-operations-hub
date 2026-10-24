# Quickstart Validation: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Prerequisites

Use non-production data with a confirmed direct booking, completed attendance,
active branch-scoped settlement capabilities, and signed test payment callbacks.
Do not use production Firebase or payment-provider credentials.

## Validation Scenarios

1. Finalize a charge and accept final payment; replay its callback and verify one
   payment, one release outcome, and redacted audit correlation.
2. Attempt release with outstanding balance; expect denial. Approve deferred
   payment and verify retained item, one reminder, expiry and audited reissue.
3. Request whole-booking cancellation at at least 72 hours, 24–72 hours and below
   24 hours; verify 100%, 50%, and rejection. Tickets/capacity change only after
   verified refund outcome.
4. Reschedule an eligible item; verify atomic ticket/capacity transfer, old-QR
   revocation, replacement QR and price rule. Repeat with no capacity; no change.
5. Verify Mland-caused item remedy changes only that item after refund confirmation.

## Evidence

Keep contract responses, migration/module test results, authorization denials for
inactive/out-of-branch/missing-capability actors, state transitions and audit IDs.
