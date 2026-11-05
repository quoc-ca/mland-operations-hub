# Feature 003.3 frontend evidence — 2026-11-03

**Date created**: 2026-11-03  
**Date updated**: 2026-11-03  
**Author**: Hồ Hoàng Quốc  
**Last updated by**: Hồ Hoàng Quốc

## Figma-to-route mapping

The Settlement frontend uses the existing Manager design nodes and keeps provider evidence behind backend APIs:

| Figma node | Frontend route | Acceptance coverage |
|---|---|---|
| `203:112` Settlement case detail | `/settlement/cases?tab=cases` | Lookup/detail for customer cancellation, required-session change, and Mland-caused remedy projections |
| `186:252` Settlement cases | `/settlement/cases?tab=cases` | Manager-only list with type/status filters plus safe case detail lookup |
| `186:302` Refund policy | `/settlement/cases?tab=policies` | List and create effective policy revisions |
| `186:352` OTA finance | `/ota-connectivity/finance` | Existing finance evidence/reconciliation UI |
| `186:402` OTA reconciliation | `/ota-connectivity/reconciliation` | Existing reconciliation work-item UI |
| `186:252` / final-payment variant | `/settlement` | Final bill, charges, finalization, payment attempt, expired/unknown recovery |

## Contract-aligned behavior

- Final payment uses the backend payment-attempt response. A payment URL is opened only when returned by the server; `UNKNOWN`/expired responses remain pending reconciliation and do not trigger an unapproved retry.
- Customer cancellation is recorded as a case and handed to Operations. The UI does not promise or initiate an automatic refund.
- Required session change submits `targetRequiredSessionReferences` exactly as defined by the settlement contract and allows an optional handoff reference.
- Mland-caused remedy is a separate action and is not conflated with ordinary customer cancellation.
- Error, loading, empty, unauthenticated, and backend-authorized states are rendered explicitly.

## Central list contract

`GET /api/v1/workshop-settlement-cases` is the Manager-only central list endpoint.
It returns safe projections ordered newest first and accepts exact `caseType`,
`status`, and bounded `limit` filters. Staff remains on booking-scoped flows
because `SettlementCase` does not carry a location scope.

## Verification

- Frontend build: `npm run build` from `ppswbs_frontend`.
- Backend compile: `mvn -f ppswbs_backend/pom.xml -DskipTests compile`.
- Settlement API behavior is exercised through the existing backend contract tests; no task marker is changed by this evidence note.

## Acceptance verification added on 2026-11-03

The frontend contract client now has explicit coverage for the four Settlement surfaces that
were previously only verified by route inspection:

| Surface | Route/API coverage | Safety acceptance |
|---|---|---|
| Case list/detail | `/settlement/cases?tab=cases`; central list plus cancellation, required-session-change and Mland-remedy detail lookups | Manager list filters are passed unchanged; identifiers are URL-encoded; projections remain backend-owned and do not expose PII |
| Required session change | `/settlement/cases?tab=change`; `POST .../required-session-change-cases` | The complete target session pair and handoff reference are sent in one idempotent command; atomicity and policy validation remain backend responsibilities |
| Refund policy | `/settlement/cases?tab=policies`; `GET/POST /api/v1/workshop-refund-policies` | Policy revisions use idempotency keys; ordinary direct customer cancellation remains non-refundable and does not invoke VNPay refund/QueryDR |
| Final payment recovery | `/settlement`; final bill, charges, finalization and `.../payment-attempts` | Recovery creates an idempotent payment attempt and displays only server-returned evidence; browser return is not treated as payment truth |

Automated evidence for the frontend contract wiring:

```text
npm test -- --run src/workshop-settlement/test/settlementApi.test.js
npm test -- --run
npm run build
git diff --check
```

The API test uses the application's injected HTTP transport, verifies exact paths, payloads,
query filters and idempotency keys, and does not fake backend responses in production code.
Live provider/VNPay evidence and browser screenshots remain separate from this contract test;
the backend integration evidence is recorded in `quickstart-verification-evidence.md`.

### Actual run results

- `npm test -- --run src/workshop-settlement/test/settlementApi.test.js`: **6/6 passed**.
- `npm test -- --run`: **187/187 passed** across 16 frontend test files.
- `npm run build`: **passed** (Vite build; existing chunk-size warning only).
- Targeted backend Settlement command (`FinalBookingSettlementIT`, refund service/policy,
  final-bill/payment and case-list tests): **25/25 passed**, `BUILD SUCCESS`.
- `git diff --check` for the changed Settlement files: **passed**. Git emitted only the
  existing LF-to-CRLF normalization warning for the modified JavaScript file.

## Rendered UI state evidence (2026-11-04)

| State | Route | Evidence |
|---|---|---|
| Empty case list with filters | `/settlement/cases?tab=cases` | Renders OPEN/PENDING_PROVIDER/RESOLVED/CANCELED filters and the empty state. *(2026-11-05 review: `CANCELED` is not a spec case state; spec uses `OPEN`, `PENDING_PROVIDER`, `RESOLVED`, `REJECTED`, `FAILED`. Realign under 003.3 T090.)* |
| Provider verification pending | `/settlement/cases?tab=cases` | Renders `PENDING_PROVIDER` without presenting the case as resolved. |
| Empty refund-policy history | `/settlement/cases?tab=policies` | Renders the policy surface without fabricated revisions. |
| Backend error | `/settlement/cases?tab=cases` | Renders correlation/retry error state and no fake empty-success list. |

```text
npm test -- --run src/workshop-settlement/test/settlementUiEvidence.test.jsx
Result: 1 test file passed, 4 tests passed, 0 failures.
```

The error-state test also fixed a UI defect: a failed case-list request no longer falls
through to the successful empty-list presentation.

Verification exception: screenshot capture was not completed because the configured browser
access host was unavailable (`browsers: []` from the CUA state). No screenshot is claimed.

## Direct Mland add-on billing evidence (2026-11-04)

> **Superseded — 2026-11-05.** Under the
> [open-folio policy](../../direct-booking-open-folio-policy.md) a `FINALIZED`
> direct folio accepts no add-on, so the flow below no longer matches approved
> behaviour. The evidence is kept as a historical record of the implemented UI;
> `StaffWorkshopAddonPanel` and the `direct-addon-bills` calls must be realigned
> under 003.3 T040/T087. OTA shop add-on evidence is unaffected.

The Staff workshop add-on screen now follows T040's finalized-bill contract:

- route: `/operations/sessions/:sessionId`;
- component: `StaffWorkshopAddonPanel`;
- create bill: `POST /api/v1/workshop-bookings/{bookingCode}/direct-addon-bills` with booking code, catalogue add-on reference, customer consent reference, and an idempotency key;
- payment: `POST .../direct-addon-bills/{billReference}/payment-attempts`, creating a separate SHOP_ADDON attempt with a ten-minute checkout expiry;
- the original final bill remains immutable;
- the UI never accepts amount/currency from the browser and never treats browser return as payment evidence; it waits for signed IPN/QueryDR verification.

Figma frame: `327:1083` (`STAFF · Workshop add-on · Direct Mland bill`) in `03 · Staff`.

```text
npm test -- --run src/workshop-operations/test/staffAddonPages.test.jsx
Result: 1 test file passed, 4 tests passed, 0 failures.
npm run build
Result: passed; existing Vite chunk-size warning only.
```

## Guest/member payment state evidence (2026-11-04)

| State | Route | Evidence |
|---|---|---|
| Provider success return | `/workshop-booking/:bookingCode/payment-return` | Signed provider return is displayed as informational until backend verification confirms the booking. |
| Provider rejection | `/workshop-booking/:bookingCode/payment-return` | Response-code rejection is shown as a typed payment failure; no confirmation is fabricated. |
| Network timeout | `/workshop-booking/:bookingCode/payment-return` | Lost response is rendered as retryable/unknown and does not create another payment attempt automatically. |
| Expired hold/payment | `/workshop-booking/:bookingCode/payment-return` and `/settlement` | Expired state shows recovery guidance without treating the payment as verified. |

```text
npm test -- --run \
  src/workshops/paymentReturnOutcome.test.js \
  src/workshops/workshops-payment-and-recovery.test.js \
  src/shared/payment/paymentRecoveryState.test.js \
  src/workshop-settlement/test/settlementUiEvidence.test.jsx
Result: 4 test files passed, 33 tests passed, 0 failures.
```

The browser automation host was unavailable during this run (`browsers: []`), so no
screenshot is claimed for these routes; the table is backed by rendered UI tests.
