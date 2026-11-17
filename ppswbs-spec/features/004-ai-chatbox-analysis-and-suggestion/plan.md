# Implementation Plan: Feature-004 Independent AI Design Workspace

**Date created**: 2026-10-21
**Date updated**: 2026-11-05
**Author**: Ngô Tiến Đạt
**Last updated by**: Ngô Tiến Đạt

## Summary

Feature-004 is an independent React/Vite frontend and Spring backend capability.
The current frontend baseline is implemented under `src/features/designreview/`.
It does not depend on Workshop Booking or Payment.

## Confirmed frontend baseline

- React 19, Vite 7 and `react-router` 7.
- Shared HTTP, Firebase session, i18n and UI primitives.
- Real routes; no hash-route prototype fallback.
- Local feature state; no new state library, UI kit or form library.
- Backend is authoritative for permission, ownership, review and pricing state.
- Guest uses Basic AI Support only; Member owns Design Requests.
- Description-only, image-only and combined Design Requests are supported.

## Delivery slices

1. Keep the independent routes and remove booking assumptions from frontend behavior.
2. Maintain Basic AI Support with prompt validation, advisory scope and recovery states.
3. Maintain Member Design Workspace with image consent, validation and idempotent multipart submission.
4. Maintain owner-safe result/status display and Staff/Manager review operations.
5. Maintain Manager price suggestion and Admin technical-audit projection without auto-publication.
6. Normalize the feature folder toward `ui/application/domain/infrastructure` only in a separately scoped refactor.

## Backend dependencies and blockers

- OpenAPI and current backend DTO/status/body shapes are not fully synchronized.
- No frontend role bootstrap contract exists; route guards are navigation aids only.
- Backend currently keeps compatibility with `ROLE_OWNER` for Manager behavior.
- Image analysis is synchronous in the current request; the frontend must use the returned state and must not invent polling.
- Provider adapter is deterministic/mock-like rather than verified live Gemini integration.
- `consentPurpose` requires backend validation hardening.
- Fresh backend integration and migration evidence remain separate release gates.

## Verification

The frontend scope requires feature tests for API mapping, auth transport, multipart
payload, idempotency, loading, empty, validation, forbidden, rate-limit, timeout,
stale-version and retry-preserves-input states, followed by `npm test` and
`npm run build` from `ppswbs_frontend`.
