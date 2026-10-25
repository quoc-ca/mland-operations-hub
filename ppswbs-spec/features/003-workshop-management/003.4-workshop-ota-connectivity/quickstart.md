# Quickstart Validation: Workshop OTA Connectivity

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Purpose

This is an evidence-oriented validation flow for implementation and provider onboarding. It does not authorize a production OTA connection. Use a provider sandbox or an adapter simulator until the readiness record is complete; do not place a real booking or put secrets in fixtures/logs.

## Prerequisites

- The 003.1 booking, 003.2 operations, and 003.3 settlement/change facades are available in the test environment.
- An approved test workshop session uses `PER_PERSON` pricing and has known capacity; no group/co-maker/base-plus-extra scenario is used.
- Test actors have explicitly granted branch/capability permissions. A separate Admin test actor owns provider connection actions.
- The provider adapter simulator can capture outbound messages and submit authenticated, redacted representative callbacks. A real sandbox is used only after credential and callback approval.

## Validation sequence

1. Create a draft mapping for a candidate provider and associate an approved workshop/session offer. Confirm that no booking can be accepted in draft.
2. Enable preparation synchronization and change schedule/availability/sales state. Confirm a durable outbound change and correlated delivery attempt are created, with no secret or raw customer data exposed.
3. Replay the same publication request and verify one logical source version is not published as multiple business changes. Simulate a transient failure, then a successful retry, and retain both attempts as evidence.
4. Simulate retry exhaustion. Verify the mapping becomes sales-closed/suspended, new OTA capacity commitments are blocked, and a reconciliation work item is visible only to the authorized scope.
5. Run an authorized full re-sync. Verify booking remains closed until the adapter confirms the required mapping/sales state and an auditable resolution is recorded.
6. Complete a provider readiness record using sandbox evidence: product mapping, callback auth, test booking, one Mland ticket per participant, and individual ticket scan. Attempt booking enablement without one item and verify rejection.
7. After all provider gates are approved, submit a booking for `N` participants. Verify exactly `N` Mland tickets, one capacity reservation, linked external references, and no direct provider write to Operations tables.
8. Replay the same provider event and submit an out-of-order update. Verify the original booking/tickets/capacity are unchanged and the replay is audited.
9. Exercise cancellation/refund evidence, a provider payout update, and a paid direct-Mland add-on. Verify these create distinct histories; a browser return or callback receipt alone does not settle money.
10. Simulate a booking delivered after sales-close only when the provider's written rule is available. Verify the configured rule, capacity result, and operator evidence. Without that evidence, this test is a release blocker.

## Required evidence and test suites

Record the mapping/provider, environment, safe correlation IDs, timestamp, actor scope, expected/actual result, and reviewer for every scenario. Automated coverage must include module boundaries, Flyway empty/upgrade migrations, authorization/branch scope, callback authentication, payload redaction, duplicate/out-of-order events, inventory failure closure, confirmed re-sync, ticket-per-participant issuance, cancellation handling, and separated financial evidence. Run the backend's Maven test suite and Modulith verification after implementation; frontend validation must exercise only documented REST contracts.

## Release stop conditions

Do not enable a provider for booking if any of the following is missing or failing: commercial/onboarding approval, credential/callback verification, ticket-and-scan proof, cancellation/refund agreement, payout semantics, late-booking/capacity rule, adapter contract tests, or the failure-close and reconciliation scenario. Preparation synchronization may remain sales-closed while those gaps are resolved.
