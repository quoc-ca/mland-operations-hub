# Quickstart Verification Evidence: Workshop Settlement and Changes

**Date created**: 2026-11-01
**Date updated**: 2026-11-01
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

This document records the actual non-production verification results for
[quickstart.md](./quickstart.md) on the dedicated MySQL test database
`ppswbs_workshop_test_20261029`. All tests executed without database reset, truncation,
or data deletion, adhering to the project integrity policies.

## 1. Environment & Target Database

- **Database URL**: `jdbc:mysql://localhost:3306/ppswbs_workshop_test_20261029?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- **User**: `root`
- **Database Engine**: MySQL 8.0.43 Community Server
- **Flyway History**: Retained intact through migration V53 (no clean/drop/reset performed).
- **Execution Environment**: Java 21, Spring Boot 3.4.x, Spring Modulith 1.3.x.

## 2. Test Execution Command

```powershell
$env:WORKSHOP_OPERATIONS_TEST_JDBC_URL = 'jdbc:mysql://localhost:3306/ppswbs_workshop_test_20261029?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:WORKSHOP_OPERATIONS_TEST_JDBC_USER = 'root'
$env:WORKSHOP_OPERATIONS_TEST_JDBC_PASSWORD = 'sa123456'
mvn -f ppswbs_backend/pom.xml test "-Dtest=WorkshopSettlementContractIT,SettlementPaymentEvidenceIT,MlandCancellationRemedyIT,CustomerCancellationIT,RequiredSessionChangeIT,WorkshopSettlementModuleTest,WorkshopSettlementEvidenceConsumerTest,CustomerBookingCancellationServiceTest,FinalBookingBillServiceTest,MlandCancellationRemedyServiceTest,RequiredSessionChangeServiceTest,SettlementDtoValidationTest,SettlementIdempotencyServiceTest,SettlementNotificationEventDispatcherTest,SettlementNotificationHandoffServiceTest,SettlementNotificationPayloadFacadeTest,VariableChargeServiceTest,WorkshopRefundServiceTest,WorkshopSettlementAuthorizationServiceTest"
```

## 3. Test Suite Results Summary

| Test Suite Class | Tests Run | Failures | Errors | Skipped | Status | Scope / Scenario Verified |
|---|---|---|---|---|---|---|
| `WorkshopSettlementContractIT` | 3 | 0 | 0 | 0 | **PASS** | 13 OpenAPI route mappings, mutating POST idempotency headers, typed boundaries. |
| `SettlementPaymentEvidenceIT` | 3 | 0 | 0 | 0 | **PASS** | Shared ingress verified payment evidence, owner-qualified dispatch, zero raw payload. |
| `MlandCancellationRemedyIT` | 5 | 0 | 0 | 0 | **PASS** | Replacement-first, handoff mismatch rejection, unavailable/declined remedy, floor-VND partial refund. |
| `CustomerCancellationIT` | 7 | 0 | 0 | 0 | **PASS** | Eligible direct cancellation is handed to Operations and resolves with `DIRECT_DEPOSIT_FORFEITED`; ineligible requests are rejected; no refund allocation, VNPay refund, or QueryDR dispatch. |
| `RequiredSessionChangeIT` | 5 | 0 | 0 | 0 | **PASS** | Real MySQL operations session replacement, gap policy, QR preservation, entitlement projection. |
| `WorkshopSettlementModuleTest` | 2 | 0 | 0 | 0 | **PASS** | Spring Modulith module boundaries, named interfaces, allowed facade dependencies. |
| `WorkshopSettlementEvidenceConsumerTest` | 5 | 0 | 0 | 0 | **PASS** | Final payment evidence processing, idempotency replay, amount matching. |
| `CustomerBookingCancellationServiceTest` | 7 | 0 | 0 | 0 | **PASS** | Cancellation case authorization, Guest proof, Member ownership, refund calculation. |
| `FinalBookingBillServiceTest` | 4 | 0 | 0 | 0 | **PASS** | One bill per package, add-on charges, zero-balance finalization enforcement. |
| `MlandCancellationRemedyServiceTest` | 3 | 0 | 0 | 0 | **PASS** | Handoff reference validation, policy revision snapshots, refund allocations. |
| `RequiredSessionChangeServiceTest` | 5 | 0 | 0 | 0 | **PASS** | Target session validation, complete set requirement, authorization. |
| `SettlementDtoValidationTest` | 3 | 0 | 0 | 0 | **PASS** | Bean Validation constraints on case requests, refund commands, and charge DTOs. |
| `SettlementIdempotencyServiceTest` | 3 | 0 | 0 | 0 | **PASS** | SHA-256 semantic fingerprinting, concurrent lease acquisition, conflict rejection. |
| `SettlementNotificationEventDispatcherTest` | 2 | 0 | 0 | 0 | **PASS** | Post-commit event publishing without sensitive recipient or credential leaks. |
| `SettlementNotificationHandoffServiceTest` | 2 | 0 | 0 | 0 | **PASS** | Deduplication, immutable handoff payload construction, delivery failure resilience. |
| `SettlementNotificationPayloadFacadeTest` | 2 | 0 | 0 | 0 | **PASS** | Reference-only delivery payload resolution for Feature 007 consumption. |
| `VariableChargeServiceTest` | 3 | 0 | 0 | 0 | **PASS** | Staff/Manager variable charge creation, reason auditing, pre-finalization lock. |
| `WorkshopRefundServiceTest` | 6 | 0 | 0 | 0 | **PASS** | Mland-remedy refund dispatch and QueryDR verification only; not used by direct customer deposit cancellation. |
| `WorkshopSettlementAuthorizationServiceTest` | 2 | 0 | 0 | 0 | **PASS** | Staff location scope, global Manager authority, denied audit without side effects. |
| **Total** | **72** | **0** | **0** | **0** | **BUILD SUCCESS** | **100% Pass Rate** |

## 4. Quickstart Scenario Alignment Matrix

- **Scenario 1 (One bill per package)**: Verified by `FinalBookingBillServiceTest` and `WorkshopSettlementContractIT`. Exactly one bill is maintained per booking reference; session/ticket/participant bills do not exist.
- **Scenario 2 (Customer-safe reads and charges)**: Verified by `CustomerCancellationIT`, `VariableChargeServiceTest`, and `SettlementDtoValidationTest`. Member ownership and Guest contact proof permit safe access; arbitrary prices and post-finalization mutations are rejected.
- **Scenario 3 (Finalization and client retries)**: Verified by `FinalBookingBillServiceTest`, `SettlementIdempotencyServiceTest`, and `WorkshopSettlementContractIT`. Non-zero balances cannot be finalized; replay returns cached outcome; changed semantic reuse produces `409 IDEMPOTENCY_CONFLICT`.
- **Scenario 4 (VNPay evidence & QueryDR)**: Verified by `SettlementPaymentEvidenceIT`, `WorkshopSettlementEvidenceConsumerTest`, and `WorkshopRefundServiceTest`. Verified `FINAL_PAYMENT` evidence settles outstanding balance; signature/amount mismatches do not dispatch.
- **Scenario 5 (No credit and output handover)**: Verified by `FinalBookingBillServiceTest`. Both Staff and Manager are rejected if attempting finalization with an outstanding balance.
- **Scenario 6 (Non-refundable direct customer cancellation)**: Verified by `CustomerCancellationIT` and `CustomerBookingCancellationServiceTest`. Eligible future whole-booking cancellation is handed to Operations and resolved without refund; ineligible/Operations-rejected requests remain rejected.
- **Scenario 7 (Replacement and Mland remedy)**: Verified on MySQL by `RequiredSessionChangeIT` (5/5) and `MlandCancellationRemedyIT` (5/5). Complete target session sets are required; QR reference is preserved; matching `cancellationHandoffReference` is strictly required.
- **Scenario 8 (Policy and authorization)**: Verified by `MlandCancellationRemedyIT` and `WorkshopSettlementAuthorizationServiceTest`. Future revisions affect only subsequent confirmed bookings; floor-VND rounding rule applies.
- **Scenario 9 (Notification isolation)**: Verified by `SettlementNotificationEventDispatcherTest` and `SettlementNotificationHandoffServiceTest`. Replay creates no duplicate delivery request; no recipient PII or payment secrets are logged or published.
- **Scenario 10 (Data-bearing rollout)**: Verified by isolated MySQL execution on `ppswbs_workshop_test_20261029`. No shared rows were truncated, dropped, or reset.

