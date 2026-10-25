# Research: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Decisions

### Final-bill payment link

**Decision**: A Manager-approved Workshop Final Bill link is valid for 24 hours
and sends at most one reminder. Deposit links remain valid for 10 minutes with
their existing 15-minute capacity hold.

**Rationale**: Final settlement occurs after service and holds no capacity. The
exception remains narrow, audited, and does not create default credit.

**Rejected alternative**: A 10-minute final-bill link would force repeated
manual reissue without improving seat/capacity protection.

### Authorization

**Decision**: Require active actor, branch scope, and one settlement capability:
`workshop.settlement.defer`, `workshop.settlement.reschedule`, or
`workshop.settlement.refund`.

**Rationale**: Account/personnel owns grants and role-code mapping, so this
feature must not infer `Manager = OWNER`.

### Ownership and recovery

**Decision**: Settlement owns financial evidence and change cases; Booking owns
deposit evidence; Operations owns capacity, ticket and attendance state.

**Rationale**: Facade-only cross-module calls preserve a single source of truth.
Provider outcomes are idempotent; refund consequences occur only after verified
refund and are recoverable through audited retry.
