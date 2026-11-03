# Feature 003.2 frontend administration evidence

## Scope

The frontend administration surface is implemented in `ppswbs_frontend`:

- `/operations/admin?tab=locations` — location administration
- `/operations/admin?tab=slots` — slot administration
- `/operations/admin?tab=sessions` — session administration
- `/operations/package-add-ons` — package/location add-on catalogue, separate from OTA add-ons

The operations administration page uses the approved Manager operations layout and keeps
authorization, lifecycle state, and validation decisions server-side. The UI supplies an
`Idempotency-Key` for writes and the Manager REST controllers now pass it to the durable
Operations-owned ledger for package, add-on, location, slot and session commands. The backend
replays an equivalent completed response, rejects changed semantic payload/scope with typed
`409 IDEMPOTENCY_CONFLICT`, and returns typed `409 IDEMPOTENCY_IN_PROGRESS` for an active claim.
The package add-on
catalogue is scoped by package and location. It uses the Manager-only inventory endpoint
`GET /api/v1/workshop-package-management/packages/{packageId}/locations/{locationId}/add-ons`,
which deliberately returns `DRAFT`, `ACTIVE`, and `RETIRED`; the public add-on endpoint remains
limited to customer-selectable `ACTIVE` records.

Acceptance coverage for the three administration surfaces is now explicit:

- Location administration lists existing locations and creates a new location through the
  server contract. The current contract has no location PATCH/retire operation, so the UI does
  not invent an edit action.
- Slot administration lists slots for the selected location, creates slots, and activates or
  deactivates an existing slot with `PATCH /api/v1/workshop-slots/{slotId}`.
- Session administration lists sessions, creates sessions, and requests lifecycle changes with
  `POST /api/v1/workshop-sessions/{sessionId}/state-transitions` using the contract states
  `PUBLISHED`, `SALES_PAUSED`, and `CANCELLED`.
- All three surfaces retain loading, empty, error and Manager authorization handling; the server
  remains authoritative for validation and allowed state transitions. Empty list responses render
  an explicit empty-state message instead of an empty table. Durable replay protection is covered
  by backend unit/contract/MockMvc evidence and isolated MySQL migration/ledger evidence.
  T087 is complete after the 2026-11-04 MySQL run on the disposable schema
  `ppswbs_t087_mysql_20261104` (10/10 tests passed).
- Package add-on inventory uses two intentional reads: the customer/public catalogue calls the
  public package/location endpoint and therefore sees only `ACTIVE` entries; the Manager catalogue
  calls `/api/v1/workshop-package-management/packages/{packageId}/locations/{locationId}/add-ons`
  and can reload `DRAFT`, `ACTIVE`, and `RETIRED`. The management endpoint is read-only; writes
  remain on the canonical authenticated Operations add-on routes.

## Verification

From `ppswbs_frontend`:

```text
npm test -- --run src/workshop-operations/test/packageApi.test.js
```

Result on 2026-11-03: **9/9 tests passed**. Coverage includes the published/managed package
client, location/slot/session administration endpoints, operation-key-carrying session creation,
slot activation/deactivation, session lifecycle transitions, the Manager-only package/location
add-on inventory, and add-on create and lifecycle transition requests.

```text
npm test -- --run
```

Result on 2026-11-03: **15 test files passed, 181 tests passed**.

```text
npm run build
```

Result on 2026-11-03: **Vite build succeeded**. Vite emitted only the existing chunk-size
warning; no compilation errors occurred.

From the repository root:

```text
mvn -f ppswbs_backend/pom.xml '-Dtest=OperationsRestIdempotencyServiceTest,WorkshopAddOnControllerTest,WorkshopPackageContractIT' test
```

Result on 2026-11-03: **14/14 tests passed**. This covers semantic replay, changed-command
conflict, active-claim handling, failed-claim retry, an HTTP MockMvc request carrying the
`Idempotency-Key`, and the package contract header assertions.

```text
mvn -q -f ppswbs_backend/pom.xml '-Dtest=WorkshopOperationsMySQLIT' test
```

Result on 2026-11-04: **10/10 tests passed** against the disposable MySQL schema
`ppswbs_t087_mysql_20261104`. Flyway applied V1–V90 and the T087 test verified the global
unique `uq_fci_operation_reference` index. The schema was retained for evidence and was not
dropped or reset.

## Figma/design note

The implementation reuses the approved Manager operations layout. No claim is made that a
new Figma frame was created by this evidence file; node-level Figma mapping must be recorded
separately if the design review requires distinct Location, Slot, or Package add-on frames.
