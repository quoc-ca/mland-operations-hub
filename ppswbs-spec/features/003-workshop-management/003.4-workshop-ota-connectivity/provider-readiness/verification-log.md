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

## 7. OpenAPI Contract Drift & Documentation Verification (`OtaConnectivityOpenApiIT`, `OtaFinancialEvidenceControllerIT`, `OtaAddonControllerIT`, `OtaReconciliationApiTest`, `OtaVoucherFallbackServiceTest`)

- **Command**:
  ```bash
  mvn -f ppswbs_backend/pom.xml test -Dtest=OtaConnectivityOpenApiIT,OtaFinancialEvidenceControllerIT,OtaAddonControllerIT,OtaReconciliationApiTest,OtaVoucherFallbackServiceTest,OtaConnectivityModuleTest
  ```
- **Verification points (T053)**:
  - **OpenAPI 3.1.0 Contract Metadata**: Verified against `contracts/ota-connectivity.openapi.yaml`; base server URL is strictly `/api/v1`.
  - **Route Inventory & Alignment**: All 14 operational REST paths verified with exact HTTP methods, parameter models, and Spring controller mappings across `OtaChannelController`, `OtaReconciliationController`, `OtaFinancialEvidenceController`, and `OtaAddonController`.
  - **Response Status Codes & Errors**: Success (200, 201, 202) and typed error statuses (400, 401, 403, 404, 409, 422) adhere to `ApiError` (`code`, `message`, `correlationId`).
  - **Strict Request & Schema Safety**: All request/response schemas enforce `additionalProperties: false` via `OtaStrictRequest`; unknown fields return HTTP 400.
  - **Enum Fidelity**: 100% enum agreement for providers, mapping states, commercial statuses, 12 readiness checks, work item statuses, resolution options, customer payment states, payout states, and add-on types/statuses.
  - **Privacy & Redaction**: Zero exposure of provider tokens, API keys, passwords, credentials, raw signatures, QR secrets, or customer PII (credit cards, phone, personal email) in contracts, schemas, or runtime responses.
  - **Shared-voucher Incident Fallback**: Verified server-safe audit correlation without automatic check-in.
- **Total Tests**: 67/67 passed (9 + 21 + 19 + 9 + 9).

---

## 7. Persistent Publication, Retry, Resync & Authorization Verification (T050)

- **Command**:
  ```bash
  mvn -f ppswbs_backend/pom.xml test "-Dtest=edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaInventorySyncIT,edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaSyncDeliveryPersistenceIT,edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaSyncDeliveryServiceTest,edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.security.OtaAuthorizationIT"
  ```
- **Environment**: isolated, generated MySQL schemas created from `WORKSHOP_OTA_TEST_JDBC_URL`; the shared `ppswbs_workshop_test_20261029` schema was not reset or modified destructively.
- **Result**: **23/23 passed, 0 failures, 0 errors**.
- **Verification points**:
  - Persistent Spring Modulith publication replayed after injected consumer failure and application-context restart without duplicate logical outbound changes.
  - Duplicate/older source versions were ignored; source-version gaps triggered affected-mapping suspension and authoritative resync.
  - Retry attempts and retry exhaustion were persisted; exhaustion created reconciliation work without mutating Operations capacity, tickets, or direct-Mland availability.
  - Confirmed resync restored preparation synchronization only after the authoritative snapshot and mapping state were accepted.
  - Manager/global and Staff/location-scoped authorization boundaries remained enforced.

## 8. Summary of All Verified Tests

| Test Suite Class | Tests Run | Pass | Fail | Description |
|---|---|---|---|---|
| `OtaConnectivityModuleTest` | 2 | 2 | 0 | Spring Modulith module & facade dependency rules |
| `OtaConnectivityOpenApiIT` | 9 | 9 | 0 | OpenAPI 3.1.0 contract drift, schema constraints & controller alignment |
| `OtaAuthorizationIT` | 6 | 6 | 0 | Canonical role & location-scoped security boundaries |
| `OtaTicketDeliveryGateTest` | 6 | 6 | 0 | Shared voucher vs individual ticket scan gate |
| `OtaInventoryAdapterContractTest` | 13 | 13 | 0 | Neutral provider adapter contract & serialization |
| `OtaProviderEventControllerIT` | 6 | 6 | 0 | Authenticated provider event callback transport & safety |
| `OtaBookingIT` | 6 | 6 | 0 | Booking idempotency, payment evidence & N-ticket issuance |
| `OtaCancellationIT` | 6 | 6 | 0 | Request vs confirmed cancellation lifecycle & idempotency |
| `OtaCancellationCallbackSecurityIT` | 3 | 3 | 0 | Invalid event routing, reconciliation queue & PII hygiene |
| `OtaFinancialEvidenceIT` | 2 | 2 | 0 | Separated channel-payment vs supplier payout on MySQL |
| `OtaFinancialEvidenceControllerIT` | 21 | 21 | 0 | Payout reconciliation REST endpoints, stages, disputes & redaction |
| `OtaAddonBillingIT` | 3 | 3 | 0 | Free engraving, consent rejection & paid add-on isolation |
| `OtaAddonSettlementContractTest` | 5 | 5 | 0 | Settlement facade contract & Staff location authorization |
| `OtaAddonControllerIT` | 19 | 19 | 0 | Add-on quote, acceptance & direct-bill REST controller integration |
| `OtaReconciliationApiTest` | 9 | 9 | 0 | Reconciliation work item listing, resolve action & idempotency |
| `OtaVoucherFallbackServiceTest` | 9 | 9 | 0 | Shared voucher incident fallback authorization, audit & replay |
| `OtaCancellationAdapterContractTest` | 13 | 13 | 0 | Provider cancellation/refund adapter contract, signature, allowlist & fail-closed reconciliation |
| **Total Verified** | **132** | **132** | **0** | **100% Passing** |

## 9. Provider Cancellation Adapter Verification (T038)

- **Date**: 2026-11-01
- **Command**:
  ```bash
  mvn -f ppswbs_backend/pom.xml test "-Dtest=edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaCancellationAdapterContractTest,edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationIT,edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaCancellationCallbackSecurityIT"
  ```
- **Result**: **22/22 passed, 0 failures, 0 errors**.
- **Verification points**:
  - `KlookCancellationAdapter` and `GetYourGuideCancellationAdapter` map valid test payloads (`CANCEL_REQUESTED`, `CANCEL_CONFIRMED`, `REFUNDED`) into domain `OtaCancellationEventCommand`.
  - Signature header (`X-Ota-Signature`) verification enforces opaque credential reference; invalid signatures reject with `UNAUTHORIZED` without leakages.
  - Strict default-deny rejects payloads containing forbidden fields (credit card, customer password, raw email/phone PII) with `ALLOWLIST_VIOLATION`.
  - Missing external booking reference fails closed with `VALIDATION_ERROR`.
  - Unmapped or unknown provider event actions return `Unmapped` without guessing semantics and route safely to `INBOUND_EVENT` reconciliation work items, without executing automatic cancellation or refund side-effects.
  - Direct customer cancellation policy in 003.3 remains non-refundable, isolated, and untouched.
