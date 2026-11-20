# FE-08 Verification Evidence

**Date created**: 2026-11-13
**Author**: Thinh
**Last updated by**: Thinh

This file records evidence that was actually run. A prototype, mock label or
browser return is not production evidence. Open items are listed in tasks.md
(T056–T058).

## 2026-11-13 — D-011 settlement parts and branch discount

Environment: Windows, Java 21, MySQL 8.4 in Docker (`fe08-vnpay-test-db`, port
33377), backend profile `local` for the browser run, `ppswbs_test` for the
MySQL suites. Code on `develop` at `a3187b73`.

| Check | Command or steps | Result |
| --- | --- | --- |
| Backend unit tests | `mvnw test -Dtest=RetailOrderQueryServiceTest,RetailBranchOrderStateTest,RetailPaymentMethodsTest,LocalCheckoutCancellationServiceTest,RetailDiscountAllocationTest` | 18 tests, 0 failures |
| MySQL integration | `mvnw test -Dtest=RetailCatalogueMySqlIntegrationTest,RetailBranchHoldIT` with `PM_TEST_JDBC_*` | BUILD SUCCESS |
| Migration | Backend start on `ppswbs_dev` | Schema v124, application started |
| Browser, multi-branch cash | Cart with one item at MLAND Hoan Kiem (890.000 đ) and one at MLAND Cau Giay (150.000 đ), voucher and points, store pickup, cash | Two branch orders; payable 761.634 đ and 128.366 đ, total discount 150.000 đ with no rounding drift |
| Browser, Staff collects one branch | Staff confirms 761.634 đ at Hoan Kiem | Order ML261113D0785C6F handed over, change 0 đ |
| Browser, Member cancels the other branch | Member cancels ML26111352406CFC | Order cancelled; the other branch order stays completed |

Mode: local profile with real FE-02 catalogue and real FE-06 preview; the
non-local acceptance path (FE-06 parts, `recordAcceptedSale`) is covered by the
MySQL suites. GHTK shipment creation is a mock carrier label.

## 2026-11-20 — Order stages, payment link and GHTK partner portal

Environment: Windows, Java 21, MySQL in Docker (`ppswbs_dev`, schema V188),
backend without the `retail-vnpay` profile and `VNPAY_ENABLED=true`, VNPay
sandbox through ngrok. Code on `develop` at `16e540e6` plus the Session 2026-11-20
carrier portal (T079), which stays local until FE-01 adds the role; the role
`CARRIER` was added only locally for the test (T080).

| Check | Command or steps | Result |
| --- | --- | --- |
| Backend unit tests | `mvnw test` on the FE-08 tests including CarrierShipmentServiceTest and StaffRetailShipmentServiceTest | 26 tests, 0 failures |
| Frontend tests | `npx vitest run` on the retail-order tests including carrier.test.jsx and orderStages.test.js | 22 tests passed |
| Migration | Backend start on `ppswbs_dev` | V188 applied, application started |

StaffRetailShipmentServiceTest.fullLifecycleReachesDelivered was failing on
`develop` because it skipped `READY_FOR_CARRIER`; it now walks PREPARING →
READY_FOR_CARRIER → HANDED_TO_CARRIER → DELIVERED.

The test runs above were made after T079 and before T078 (received time,
V189) and the cash-order redirect; those two still need a test run.

## Still to record

- Full backend suite, frontend suite and module checks on one commit (T056).
- The carrier portal with a real FE-01 `CARRIER` account (T080).
- QS-01–QS-27 walkthrough with the timed QS-03 checkout review (T057).
- Final security and scope review (T058).
