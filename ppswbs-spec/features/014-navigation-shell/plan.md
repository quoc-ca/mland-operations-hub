# Implementation Plan: Manager Home
> **Renamed 2026-11-25:** this folder was `013-manager-home`; it is now `014-navigation-shell`. The spec covers the navigation shell only; the Manager Home dashboard blocks moved into `010-manager-dashboard` (FR-034 to FR-038). This file keeps the earlier Manager Home history and was not rewritten; only the folder path was updated.

**Date created**: 2026-11-18
**Date updated**: 2026-11-18
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Branch**: `014-navigation-shell` (spec only; no git branch created) | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `ppswbs-spec/features/014-navigation-shell/spec.md`

**Status**: DRAFT/PROPOSED. No design approval is recorded in an `approval.md`.

## Summary

Turn the Manager's landing page from a fixed menu into a "what needs me today" page: waiting-work cards with counts, a compact today strip, one revenue card that opens the dashboard, a few recent audit events, and a short row of shortcuts. The page reads and links only.

Approach (from [research.md](research.md)): a new frontend slice reads each owner's data through the owner's own reads and shows each card independently. No new backend module and no table. Cards that already have a usable list ship first (late payments, change requests, design review, session counts, revenue, Operations audit events); the rest appear when their owner adds a small count read (R-1 to R-6). A queue without a count is hidden, as clarified in the spec.

## Technical Context

**Language/Version**: JavaScript/JSX on React 19 and Vite (existing); backend Java 21 / Spring Boot, only where an owner adds a count read.

**Primary Dependencies**: Existing only: `react-router`, the shared `useHttp` client and `useResource` hook. No new library.

**Storage**: None. No table, column or migration (see [data-model.md](data-model.md)).

**Testing**: Vitest and Testing Library for the page (`npm test`); for each added read, JUnit and a MySQL integration test; `ModulithArchitectureTests` for backend module boundaries.

**Target Platform**: Browser, client-side rendering; the page is usable at 375 px width.

**Project Type**: Web application (React + Vite frontend, Spring Boot backend).

**Performance Goals**: A Manager can tell whether anything waits within 5 seconds of load (spec SC-001). The page opens at most eight reads in parallel, each independent.

**Constraints**: Read-only; no timer refresh (load on open and explicit refresh); no new cross-module dependency; the server decides every permission; Vietnam time (UTC+7) for "today" and the 24-hour window.

**Scale/Scope**: One page, up to six queue cards, one strip, one revenue card, five recent rows, four shortcuts. The existing lists are uncapped; V1 counts them on the client, which is acceptable until an owner supplies a count (risk below).

## Constitution Check

*Gate before Phase 0 research and re-checked after Phase 1 design.*

| Principle | Status | Note |
| --- | --- | --- |
| Backend is a Spring Modulith modular monolith, one-way dependencies, facades only | Pass | No new module and no new dependency. Each owner adds its read inside its own module. |
| No raw SQL, append-only Flyway migrations, one canonical owner per concept | Pass | No schema change. "Waiting" is defined once by the owner and used by both the count and the list. |
| Firebase proves identity, MySQL decides authority; the server authorizes | Pass | Frontend visibility is never the control (spec FR-001, FR-014). |
| REST versioned, plural kebab-case, typed safe errors with correlation id | Pass | Proposed reads follow it ([contracts/reads.md](contracts/reads.md)). |
| Feature designs define loading, empty, validation, success, failure, navigation and session-recovery states | Pass | Spec FR-005, FR-006, FR-018 and the edge cases. |
| Frontend: vertical slice under `src/features/<capability>/`, features talk through public ports and never import each other's private code, UI never calls `fetch` directly, server DTOs mapped at the infrastructure boundary | Pass | The new slice has its own infrastructure port (D8). |
| No global state library by default | Pass | State is local to the page. |
| No secrets or avoidable personal data in the UI or logs | Pass | Counts and short audit rows only; the Settlement reason text is never read ([data-model.md](data-model.md)). |

Re-check after design: unchanged; no violation to justify.

## Project Structure

### Documentation (this feature)

```text
ppswbs-spec/features/014-navigation-shell/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── reads.md
├── checklists/requirements.md
└── spec.md
```

(`tasks.md` is produced later by `$speckit-tasks`.)

### Source code

Frontend (new slice; the Staff home stays where it is):

```text
ppswbs_frontend/src/features/managerhome/
├── routes.jsx                  # /manager/home
├── ui/
│   ├── ManagerHomePage.jsx     # layout, refresh, last-updated, sections in spec order
│   ├── WaitingWorkSection.jsx  # cards, dimming, "no outstanding work"
│   ├── TodayStrip.jsx          # sessions, cancelled, check-in progress, branch picker
│   ├── RevenueCard.jsx
│   ├── RecentEvents.jsx
│   ├── Shortcuts.jsx
│   └── messages.js             # Vietnamese and English labels
├── application/
│   ├── useHomeCards.js         # one independent load per card; hides cards with no read
│   ├── queues.js               # registry of the six queues: owning list target and count read (null until the owner delivers it)
│   └── vietnamTime.js          # today and the 24-hour window in Vietnam time (UTC+7)
├── infrastructure/
│   └── homeReads.js            # owner reads in section A and B of contracts/reads.md; maps DTOs
└── tests/
```

The Manager branch of `ppswbs_frontend/src/features/operatorhome/` (`OperatorHomePage.jsx` role `MANAGER`, the Manager part of `operatorSections.js`) is retired once the new route replaces it; the Staff home is untouched. The slice's route is registered in `ppswbs_frontend/src/app/router.jsx` (the composition root) and the old `/manager/home` entry is removed from `operatorhome/routes.jsx` in the same change. `ppswbs_frontend/src/shared/ui/OperationsSidebar.jsx` is unchanged.

Backend: only where an owner adds a read (R-1 to R-5): a controller method and a repository count in that owner's own `features/<owner>/` package, with its tests. No change to `common` and no new module.

**Structure Decision**: One new frontend feature slice plus small owner-side reads. This keeps ownership of "waiting" with the owners and avoids a backend module that would depend on seven others (research D1).

## Delivery order

0. **Design gate first (T001):** `$feature-design` for this feature, agreement of the owners to the reads in [contracts/reads.md](contracts/reads.md), and Tech Lead approval recorded in `approval.md`. No code task starts before it.
1. **Wave 1 (no backend change):** page shell, late payments, change requests, design review, session and cancelled counts, revenue, Operations audit events, shortcuts, refresh, last-updated, session recovery. The owner-less cards stay hidden.
2. **Wave 2 (owner reads):** R-1 check-in progress, R-5 Settlement denials, then R-2, R-3, R-4 as each owner delivers. Each new card appears only after its read is merged.
3. **Dropped:** review moderation. Feature 011 has no "awaiting moderation" concept, so there is no card and no R-6 (owner decision 2026-11-18).

## Risks and how the plan handles them

| Risk | Handling |
| --- | --- |
| The existing lists are uncapped, so counting on the client can move a lot of data once queues grow | Accepted for V1; each owner's count (R-2 to R-4) replaces list counting; late payments, change requests and design review can move to a count later without a change to the page. |
| "Waiting" differs between a count and its list | One definition per owner; the card link carries the same filter; an integration test per read checks count equals list size. |
| One slow source delays the page | Every card loads independently; the page never waits for all of them (spec FR-006, SC-003). |
| The Settlement audit read cannot select denials | Settlement half of recent events waits for R-5; until then the section states the Settlement source is "not yet included" (spec FR-010). |
| The revenue figure and the dashboard drift apart | The card calls the dashboard's own read, so they match by construction (spec SC-004). |
| Review moderation has no "waiting" state | Card dropped by owner decision 2026-11-18 (research D3). |

## Verification plan

Frontend: component tests for each section (counts, dimming, "no outstanding work", per-card failure and retry, hidden card, today strip with a cancelled session, branch choice affecting only the strip, revenue partial/unavailable/demo, recent events with one source missing, refresh and last-updated, Staff opening the Manager address, session loss), then `npm test` and `npm run build`. Backend, per owner read: JUnit, a MySQL integration test that count equals list size and that a non-Manager gets no number, and Modulith verification. Hand the quickstart scenarios to `ppswbs-testing/` for the browser run.

## Complexity Tracking

No constitution violation to justify.
