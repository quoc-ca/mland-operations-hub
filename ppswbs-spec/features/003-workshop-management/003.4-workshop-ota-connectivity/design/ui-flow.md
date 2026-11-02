# UI Flow Design: 003.4 Workshop OTA Connectivity

**Date created**: 2026-11-02
**Date updated**: 2026-11-02
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Scope and sources

- Business rules: `spec.md` (channels, mappings, price terms, publication, callbacks, reconciliation, payout, shop add-ons).
- Transport contract: `contracts/ota-connectivity.openapi.yaml`.
- Current implementation reference: `ppswbs_frontend/src/ota-connectivity/`.

## Actors, routes, and authorization

| Actor | Route | Access rule |
| --- | --- | --- |
| Admin | `/ota-connectivity/channels` | Manages technical channel connection/credential lifecycle; credentials are never rendered back. |
| Manager | `/ota-connectivity/channels`, `/ota-connectivity/reconciliation`, `/ota-connectivity/finance` | Global Feature 003 business scope for mapping, publication, reconciliation and payout evidence. |
| Staff | `/ota-connectivity/add-ons` | Location-scoped shop add-on actions only; no channel credential or payout authority. |

## Screen and interaction flows

| Screen / flow | Workflow/API reference | Required states and recovery |
| --- | --- | --- |
| Channel list/configuration | `GET/POST /ota-channels` | Loading; no channels; credential redaction; connection failure; Admin-only technical actions. |
| Mapping editor | `/ota-channels/{channelId}/mappings`, `PUT /ota-channel-mappings/{mappingId}` | Draft/sales-closed/active states; optimistic version conflict returns 409 and reload; never change identity while outbound work is queued/retrying. |
| Price terms | `POST /ota-channel-mappings/{mappingId}/price-terms` | Show OTA-confirmed evidence requirement; immutable approved snapshot; rejected/pending evidence remains visible in history. |
| Sync/publication | `/sync`, `/sales-close`, `/booking-enable` | Show queued/retrying/succeeded/failed; idempotent replay; no success claim until provider evidence is recorded. |
| Reconciliation work items | `GET/POST /ota-reconciliation-work-items` | Filter by state/category; resolve with evidence; duplicate key returns existing result; stale version is a conflict. |
| Financial evidence | `GET /ota-channel-bookings/{channelBookingId}/financial-evidence` | Manager read; redact customer PII, card data and credentials; distinguish channel payment from payout state. |
| Payout reconciliation | `POST /ota-channel-bookings/{channelBookingId}/payout-reconciliation` | Evidence lifecycle; only matching reference/net/currency reaches `PAID_OUT`; mismatch opens dispute and keeps current state. |
| Shop add-on quote | `POST /ota-channel-bookings/{channelBookingId}/add-on-quotes` | Staff location scope; laser engraving is zero-charge; paid option requires positive VND amount and item code; no OTA checkout. |
| Shop add-on acceptance/bill | `/ota-addon-quotes/{quoteId}/acceptance`, `/direct-bill` | Consent required before paid direct bill; replay same consent/bill is idempotent; changed replay conflicts; free engraving never creates a bill. |

## Invariants captured for regeneration

1. OTA customers pay the base package in the OTA checkout; Mland does not create a direct deposit/final-payment attempt for that base package.
2. OTA cancellation/refund evidence is channel-owned; Mland direct VNPay refund is not used for OTA-collected money.
3. Manager is globally scoped for OTA business operations; Admin owns technical credentials; Staff is location-scoped for shop add-ons.
4. Credentials, raw provider payloads, card data and unnecessary PII are never shown in the UI or logged.
5. Add-ons happen at the Mland shop and are financially separate from OTA booking facts; laser engraving is free, paid options use a separate direct Mland bill.
6. Callback/reconciliation/payout actions are idempotent and never treat browser state as verified provider evidence.

## Session, data handling, and accessibility

- Clear channel-management state after Admin logout; do not persist credentials in local storage.
- Preserve safe draft mapping fields on validation errors, but invalidate stale versions after a conflict.
- Payout and financial screens must use accessible tables, explicit currency/amount labels, and text status descriptions rather than color alone.

## Open decisions and dependencies

- **DRAFT**: exact Figma revision/node IDs and provider-specific copy for Klook/GetYourGuide readiness states.
- **Dependency**: live provider evidence is not replaced by mock/simulator results; the UI must label sandbox/test evidence clearly.
