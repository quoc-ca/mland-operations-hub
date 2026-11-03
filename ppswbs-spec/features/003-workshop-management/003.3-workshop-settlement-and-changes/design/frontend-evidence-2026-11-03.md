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
