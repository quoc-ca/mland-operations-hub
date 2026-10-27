# Quickstart Validation: Workshop OTA Connectivity

**Date created**: 2026-10-24
**Date updated**: 2026-10-27
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Purpose

This is an evidence-oriented validation flow for implementation and provider onboarding. It does not authorize a production OTA connection. Use a provider sandbox or an adapter simulator until the readiness record is complete; do not place a real booking or put secrets in fixtures/logs.

## Prerequisites

- The 003.1 booking, 003.2 operations, and 003.3 settlement/change facades are available in the test environment.
- An approved Basic Ring test workshop session uses `PER_PERSON` pricing and has known capacity; no group/co-maker/base-plus-extra scenario is used. Silverclay is direct-Mland only and has no OTA mapping in V1.
- Test actors have active canonical Account Management role/status/location facts and satisfy the applicable OTA Connectivity action rule for the target location. A separate Admin test actor owns provider connection actions.
- The provider adapter simulator can capture outbound messages and submit authenticated, redacted representative callbacks. A real sandbox is used only after credential and callback approval.

## Validation sequence

1. Create a draft mapping for a candidate provider and associate an approved workshop/session offer. Confirm that no booking can be accepted in draft.
2. Enable preparation synchronization and cause an Operations schedule,
   availability, or sale-state change. Confirm 003.4 receives only the approved
   schedule snapshot or availability/sale-state snapshot. Confirm each has the
   same per-session source-version sequence, contains no customer, booking,
   payment, ticket-detail, or OTA-mapping data, and that sellable availability
   already includes confirmed tickets and active unexpired direct-Mland holds.
   Verify the input reaches 003.4 as a persistent internal Spring Modulith
   publication recorded with the Operations change, not as a broker message or
   a provider call inside the Operations transaction.
3. Replay the same Operations source change and submit an older one across either
   snapshot kind. Verify 003.4 creates no second logical publication. Simulate a
   source-version gap or uncertain order and verify it suspends only that mapping
   and requests an authoritative 003.2 resync before it claims current inventory
   or schedule. Simulate a 003.4 consumer failure/restart and verify replay of
   the internal publication creates no second logical outbound sync change.
4. After a current source change is accepted, simulate a transient provider failure and then a successful retry. Retain both attempts as evidence without changing Operations capacity or sale state.
5. Pause and then resume sales in Operations. Verify 003.4 publishes normal
   closed/zero availability and then the reopened snapshot without suspending a
   valid mapping. Separately simulate retry exhaustion. Verify only the affected
   OTA mapping becomes sales-closed/suspended, new OTA capacity commitments are
   blocked, and a reconciliation work item is visible only to the authorized
   scope. Verify direct-Mland booking remains available according to Operations
   state.
6. Run an authorized full re-sync from the authoritative Operations snapshot.
   Verify it returns the affected session's complete current snapshot at its
   existing source version rather than creating a new Operations business
   change. Verify booking remains closed until the adapter confirms the required
   mapping/sales state and an auditable resolution is recorded.
7. Complete a provider readiness record using sandbox evidence: product mapping, callback auth, test booking, one Mland ticket per participant, and individual ticket scan. Attempt booking enablement without one item and verify rejection.
8. After all provider gates are approved, submit a booking for `N` participants. Verify exactly `N` Mland tickets, one capacity reservation, linked external references, and no direct provider write to Operations tables.
9. Replay the same provider event and submit an out-of-order update. Verify the original booking/tickets/capacity are unchanged and the replay is audited.
10. Exercise cancellation/refund evidence, a provider payout update, and a paid direct-Mland add-on. Verify these create distinct histories; a browser return or callback receipt alone does not settle money.
11. Simulate a booking delivered after sales-close only when the provider's written rule is available. Verify the configured rule, capacity result, and operator evidence. Without that evidence, this test is a release blocker.

## Required evidence and test suites

Record the mapping/provider, environment, safe correlation IDs, timestamp, actor scope, expected/actual result, and reviewer for every scenario. Automated coverage must include module boundaries, Flyway empty/upgrade migrations, authorization/location scope, callback authentication, payload redaction, persistent internal Spring Modulith publication replay after consumer failure/restart, duplicate/older/gapped Operations schedule and availability source changes in the one per-session version sequence, authoritative resync, normal sales pause/resume publication, inventory failure closure that does not affect Operations, confirmed re-sync, ticket-per-participant issuance, cancellation handling, and separated financial evidence. Run the backend's Maven test suite and Modulith verification after implementation; frontend validation must exercise only documented REST contracts.

## Release stop conditions

Do not enable a provider for booking if any of the following is missing or failing: commercial/onboarding approval, credential/callback verification, ticket-and-scan proof, cancellation/refund agreement, payout semantics, evidence that provider acknowledgement/retry honors Mland's reject-without-capacity late-booking rule, adapter contract tests, or the failure-close and reconciliation scenario. Preparation synchronization may remain sales-closed while those gaps are resolved.
