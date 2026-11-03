# Feature 003.4 UI evidence — 2026-11-03

**Date created**: 2026-11-03  
**Date updated**: 2026-11-03  
**Author**: Hồ Hoàng Quốc  
**Last updated by**: Hồ Hoàng Quốc

## Figma source

The existing Figma file already contains the approved layouts used by the React/Vite implementation:

| UI | Figma node | Route |
|---|---:|---|
| Manager OTA finance / payout reconciliation | `186:352` | `/ota-connectivity/finance` |
| Manager OTA add-on catalogue / quote workflow | `186:202` | `/ota-connectivity/add-ons` |
| Staff workshop add-on counter flow | `188:102` | `/ota-connectivity/add-ons` (authorized staff variant) |

No duplicate frame was created. The implementation keeps the existing MLAND workspace shell and backend-authoritative state model from these frames.

## Verified UI states

### T044 — finance/reconciliation

- Signed-out state requires authentication before any finance request is sent.
- Loading state is rendered while financial evidence is read or a reconciliation command is pending.
- Empty evidence ledger is rendered without fabricating provider data.
- Evidence separates `customerPaymentState` from `payoutState` and renders only redacted DTO fields.
- Error state preserves the backend correlation id and supports reload/retry through the shared notice component.
- Stage recording uses the backend idempotent reconciliation command; the screen does not retry a write automatically.

### T049 — OTA add-on quote/acceptance/direct bill

- Signed-out state requires authentication.
- Quote creation renders `QUOTED`, acceptance renders `ACCEPTED`, and direct-bill completion renders the returned settlement bill reference.
- Laser engraving is displayed as free for OTA customers and does not render a bill action.
- Paid options require item code, description, amount and VND currency as defined by the API client.
- Busy state disables the active command button; backend errors are shown through the shared error notice.
- OTA add-on flow is explicitly separate from direct Mland booking add-ons and never initiates OTA checkout.

## Automated evidence

From `ppswbs_frontend`:

```text
npm test -- --run src/ota-connectivity/test/otaFinanceAddonPages.test.jsx
```

Result: 1 focused test file, **4 tests passed**: finance load/reconciliation, paid add-on quote/acceptance/direct-bill, free laser engraving without a bill action, and backend validation-error rendering without a fabricated quote.

```text
npm run build
```

Result: Vite production build passed. The existing chunk-size advisory is non-blocking.

The focused evidence is intentionally limited to the approved backend DTOs and does not claim live provider webhook compatibility.

The expanded UI acceptance was rerun on 2026-11-03:

```text
npm test -- --run src/ota-connectivity/test/otaFinanceAddonPages.test.jsx
```

Result: **4/4 passed**. The test proves the quote → acceptance → direct-bill path for a
paid option, the zero-charge laser path, and typed-error/empty-state behavior. It also
proves that the UI never calls the OTA checkout flow for shop add-ons.
