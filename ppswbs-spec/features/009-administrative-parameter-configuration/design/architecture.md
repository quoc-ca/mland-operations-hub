# Architecture Design: FE-09 Admin Ring Difficulty UI

**Date created**: 2026-11-26
**Date updated**: 2026-11-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status:** DRAFT/PROPOSED.

## Ownership and production units

The backend policy, Admin controller, service, DAO/entity and evaluation remain
in Feature-012. FE-09 adds only a frontend slice under
`ppswbs_frontend/src/features/admin-ring-difficulty/` [proposed — FE-09 spec],
reusing the existing React/Vite router, auth guard, HTTP client and bilingual
message conventions [existing — frontend repository]. No new library or shared
key/value configuration service is proposed.

| Proposed path/unit | Responsibility | Draft public interface | Source |
| --- | --- | --- | --- |
| `routes.jsx` / `adminRingDifficultyRoutes` | Register ADMIN-only `/admin/ring-difficulty`; mask other signed-in roles before mounting | route export | FE-09 spec §Actors |
| `api/ringDifficultyAdminApi.js` / `ringDifficultyAdminApi` | Call Feature-012 active, history, preview and publish REST operations | `getActive`, `listVersions`, `preview`, `publish` | Admin OpenAPI |
| `application/useRingDifficultyPolicy.js` / hook | Hold candidate separately from effective version, preserve edits on error, keep preview reference only for the matching candidate/policy/catalogue revision, manage command key | `load`, `edit`, `preview`, `publish`, `reload` | FE-09 spec §User journey |
| `ui/RingDifficultyPolicyPage.jsx` / page | Show version, grouped scores, coefficients, thresholds, standard examples, exhaustive active-model impact, confirmation and audit history | page component | FE-09 spec §User journey |
| `messages.js` / messages | Vietnamese/English labels, validation and recovery text | message export | Existing i18n convention |

The direction is page → hook → API client → Feature-012 Admin controller →
Feature-012 policy service. The UI never imports a Feature-012 repository or
keeps an authoritative score calculation. The proposed route and REST shape
require Tech Lead approval; the existing FE-012 customer Figma frames do not
approve this new Admin screen.

## Data flows and recovery

- Read: page → hook → client `getActive`/`listVersions` → Feature-012 read API
  → effective values/history; auth failure prevents protected rendering.
- Preview: local candidate + optional non-personal examples → `preview` →
  server-side standard examples, exhaustive model-default impact and, if no
  model blocks, a 15-minute proof reference → page; no policy write or Member read.
- Publish: confirmation → `publish` with expected version, matching proof and one command key
  → Feature-012 atomic transaction → new version; stale/conflict preserves local
  candidate and offers reload/reconcile.

The exact response and error types are owned by the Admin OpenAPI. A policy
storage outage keeps the previous effective version and displays retry without
claiming publication succeeded.
