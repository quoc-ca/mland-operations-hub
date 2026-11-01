# Workshop OTA Connectivity Verification Log

**Date updated**: 2026-11-01  
**Author / Responsible**: Hồ Hoàng Quốc  
**Scope**: Verification evidence for Feature 003.4 (Workshop OTA Connectivity) across Modulith architecture, authorization, provider adapters, inbound callbacks, booking idempotency, cancellation lifecycle, financial evidence, and add-on billing.

---

## 1. Spring Modulith Architecture & Hygiene (`OtaConnectivityModuleTest`)

- **Command**:
  ```bash
  mvn -f ppswbs_backend/pom.xml test -Dtest=OtaConnectivityModuleTest
  ```
- **Result**:
  - `otaConnectivityIsRecognizedAsModule`: Passed. `otaconnectivity` is recognized as a clean Spring Modulith module.
  - `crossModuleDependenciesUseOnlyNamedFacadeTypes`: Passed. All dependencies from `otaconnectivity` to other modules (`workshopoperations`, `accountmanagement`) use only public facade interfaces (`WorkshopOperationsFacade`, `AccountFacade`).
- **Total Tests**: 2/2 passed.

---

## 2. Authorization & Security Boundaries (`OtaAuthorizationIT`)

- **Command**:
  ```bash
  mvn -f ppswbs_backend/pom.xml test -Dtest=OtaAuthorizationIT
  ```
- **Verification points**:
  - Manager business & finance actions: global scope.
  - Staff add-on actions: strictly location-scoped.
  - Provider signature authentication: independent from Mland user sessions; credentials/secrets strictly redacted.
- **Total Tests**: 6/6 passed.

---

## 3. Provider Adapters & Ticket Delivery Scan Gate (`OtaTicketDeliveryGateTest`, `OtaInventoryAdapterContractTest`)

- **Command**:
  ```bash
  mvn -f ppswbs_backend/pom.xml test -Dtest=OtaTicketDeliveryGateTest,OtaInventoryAdapterContractTest
  ```
- **Verification points**:
  - Proved that shared booking voucher mode is strictly rejected for both Klook and GetYourGuide.
  - Individual ticket delivery requires both QR code support and verified scan proof at the door.
  - Inbound and outbound adapter mapping contracts adhere to allowlist and provider specifications.
- **Total Tests**: 19/19 passed (6 + 13).

---

## 4. Inbound Booking & Idempotency (`OtaProviderEventControllerIT`, `OtaBookingIT`)

- **Command**:
  ```bash
  mvn -f ppswbs_backend/pom.xml test -Dtest=OtaProviderEventControllerIT,OtaBookingIT
  ```
- **Verification points**:
  - Callback contract validation: missing/invalid signature returns 401 Unauthorized; malformed/negative inputs return 400 Bad Request; no sensitive body/credential leakage.
  - Full channel-payment verification: accepted amount >= retail/target-net and currency match are strictly required before booking confirmation or ticket issuance.
  - Partial or currency-mismatched payment returns safe 422 rejection with zero tickets issued and opens an `INBOUND_EVENT` reconciliation work item.
  - N-participant ticket issuance: one scannable Operations ticket issued per participant entitlement.
  - Duplicate external reference replay: exactly idempotent; returns prior booking and tickets without duplicate Mland booking or duplicate capacity hold.
  - Competing capacity: Operations capacity rejection cleanly returns REJECTED with zero tickets and no orphaned records.
- **Total Tests**: 12/12 passed (6 + 6).

---

## 5. Cancellation & Refund Lifecycle (`OtaCancellationIT`, `OtaCancellationCallbackSecurityIT`)

- **Command**:
  ```bash
  mvn -f ppswbs_backend/pom.xml test -Dtest=OtaCancellationIT,OtaCancellationCallbackSecurityIT
  ```
- **Verification points**:
  - Cancellation request transitions booking to `CANCELLATION_REQUESTED` and records `REFUND_REQUESTED` payment evidence without invoking Operations (never releases capacity or revokes tickets).
  - Confirmed cancellation transitions booking to `CANCELLED`, records `REFUNDED` payment evidence, and invokes `WorkshopOperationsFacade.applyVerifiedRefundConsequence` exactly once.
  - Duplicate confirmed cancellation is idempotent and does not invoke Operations a second time.
  - Out-of-order or unknown booking cancellation safely routes to open `INBOUND_EVENT` reconciliation work items.
  - Inbound event persistence boundary contains no raw payloads, secrets, passwords, or customer PII.
- **Total Tests**: 9/9 passed (6 + 3).

---

## 6. Financial Evidence & Add-on Billing (`OtaFinancialEvidenceIT`, `OtaAddonBillingIT`, `OtaAddonSettlementContractTest`)

- **Command**:
  ```bash
  mvn -f ppswbs_backend/pom.xml test -Dtest=OtaFinancialEvidenceIT,OtaAddonBillingIT,OtaAddonSettlementContractTest
  ```
- **Verification points**:
  - Customer payment on channel (`PAID_ON_CHANNEL`), duplicate external reference constraint, and append-only refund progression (`REFUND_REQUESTED` -> `PARTIALLY_REFUNDED` -> `REFUNDED`) verified on MySQL schema.
  - Customer channel payment remains completely separated and isolated from Mland supplier payout positions (`NOT_ELIGIBLE` -> `ACCRUED`) and payout ledger entries.
  - Free laser engraving is recorded with zero charge (`RECORDED_FREE`), non-zero charges are strictly rejected.
  - Paid add-ons require customer acceptance before creating direct Settlement bills.
  - Settlement direct bills maintain complete financial isolation from the OTA booking.
- **Total Tests**: 10/10 passed (2 on MySQL + 3 + 5).

---

## 7. Summary of All Verified Tests

| Test Suite Class | Tests Run | Pass | Fail | Description |
|---|---|---|---|---|
| `OtaConnectivityModuleTest` | 2 | 2 | 0 | Spring Modulith module & facade dependency rules |
| `OtaAuthorizationIT` | 6 | 6 | 0 | Canonical role & location-scoped security boundaries |
| `OtaTicketDeliveryGateTest` | 6 | 6 | 0 | Shared voucher vs individual ticket scan gate |
| `OtaInventoryAdapterContractTest` | 13 | 13 | 0 | Neutral provider adapter contract & serialization |
| `OtaProviderEventControllerIT` | 6 | 6 | 0 | Authenticated provider event callback transport & safety |
| `OtaBookingIT` | 6 | 6 | 0 | Booking idempotency, payment evidence & N-ticket issuance |
| `OtaCancellationIT` | 6 | 6 | 0 | Request vs confirmed cancellation lifecycle & idempotency |
| `OtaCancellationCallbackSecurityIT` | 3 | 3 | 0 | Invalid event routing, reconciliation queue & PII hygiene |
| `OtaFinancialEvidenceIT` | 2 | 2 | 0 | Separated channel-payment vs supplier payout on MySQL |
| `OtaAddonBillingIT` | 3 | 3 | 0 | Free engraving, consent rejection & paid add-on isolation |
| `OtaAddonSettlementContractTest` | 5 | 5 | 0 | Settlement facade contract & Staff location authorization |
| **Total Verified** | **52** | **52** | **0** | **100% Passing** |

