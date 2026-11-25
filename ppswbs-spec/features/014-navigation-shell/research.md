# Research: Manager Home

**Date created**: 2026-11-18
**Date updated**: 2026-11-18
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

This file records what the codebase already offers for each card (surveyed 2026-11-18 on branch `feature/003-2-us7-audit-review`; paths are relative to `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/` for backend and `ppswbs_frontend/src/` for frontend) and the decisions taken from it. "Count" means the number a card shows (spec FR-004).

## 1. What each source offers today

| # | Card | Existing read | "Waiting" meaning today | Count today | Verdict |
| --- | --- | --- | --- | --- | --- |
| 1 | Late payments | `GET /api/v1/late-payment-reviews` (`features/workshopbooking/controller/LatePaymentReviewController.java:32`) | Default status `PENDING_MANAGER` | Plain list, uncapped, no total; list length is the count | READY |
| 2 | Refund queue | `GET /api/v1/workshop-refund-requests?status=` (`features/workshopsettlement/controller/RefundRequestController.java:122`) | Four `PENDING_*` statuses; with no status the list also holds `REFUNDED` and `CLOSED_UNREACHABLE` | List only; the unfiltered list is not the waiting set | NEEDS_COUNT (owner defines "waiting") |
| 3 | Change requests | `GET /api/v1/workshop-change-requests` (`features/workshopsettlement/controller/TicketChangeController.java:114`) | Default `PENDING`; a Manager sees all locations | Plain list, uncapped | READY |
| 4 | Cash-close differences | none (only `POST` close and `POST /{id}/difference-confirmations`) | State `PENDING_MANAGER_CONFIRMATION` | No list or count exists | NEW_READ |
| 5 | OTA reconciliation | `GET /api/v1/ota-reconciliation-work-items` (`features/otaconnectivity/api/OtaReconciliationController.java:30`) | Statuses `OPEN`, `IN_PROGRESS`, `RESOLVED`, `ESCALATED`; no status returns resolved items too | Wrapper list, uncapped | NEEDS_COUNT (owner defines "needing attention") |
| 6 | Design review | `GET /api/v1/design-review-requests` (`designreview/web/DesignReviewController.java:17`) | `NEED_REVIEW`; a Manager sees all (Staff only up to 3,000,000 VND) | Wrapper `{items}`, uncapped | READY (for a Manager) |
| 7 | Review moderation | `GET /api/v1/review-moderation` (`features/reviewmanagement/controller/ReviewModerationController.java:40`) | **No "awaiting" status exists.** Review status is only `PUBLIC`, `HIDDEN`, `REMOVED`; the screen lists everything | Paged, `totalElements` counts all reviews | NOT_A_QUEUE (card dropped, D3) |
| 8 | Today's sessions | `GET /api/v1/workshop-sessions?from=&to=&locationId=&state=` (`features/workshopoperations/controller/WorkshopSessionController.java:52`) | `from`/`to` are plain dates on `workshopDate`; cancelled sessions included unless `state` is given | Plain list: session count and cancelled count are derivable | READY for the counts |
| 8b | Check-in progress | per-session `GET /workshop-sessions/{id}/attendance-roster` | n/a | `SessionResponse` has no checked-in or expected counts; a roster per session is N+1 calls | NEW_READ |
| 8c | Branch picker | `GET /api/v1/workshop-locations` | Lists the locations visible to the actor (a Manager sees all) | Plain list of locations | READY |
| 9 | Revenue | `GET /api/v1/executive-reports?periodType=MONTH` (`features/reporting/controller/ExecutiveReportController.java:31`) | Same call the dashboard makes (`features/manager-dashboard/ManagerDashboardPage.jsx:89`) | `totals.totalRevenue` (string, VND, may be null); `status` is `COMPLETE`, `EMPTY`, `PARTIAL` or `UNAVAILABLE`; `quality[]`; `developmentDemo`; HTTP 503 when every source is down | READY |
| 10a | Recent events, Operations | `GET /api/v1/workshop-audit/operations-events?outcome=DENIED` and `?action=WORKSHOP_PACKAGE_AVAILABILITY_CHANGED` | n/a | `{items, nextCursor}` newest first; default window 7 days; Manager only | READY |
| 10b | Recent events, Settlement | `GET /api/v1/workshop-audit/settlement-records` | Denials are rows whose `actionCode` starts with `DENIED_` | No outcome filter and the action filter is an exact match, so denials cannot be selected server-side | NEEDS_FILTER |

## 2. Decisions

**D1. Where the numbers come from.** Each owning feature supplies its own waiting count; Manager Home owns no data and gets no new backend module. For the cards that already have a plain, uncapped list whose default meaning is "waiting" (1, 3, 6), V1 counts that list on the client. For the others, the owner adds a small read that returns the count (section 3).
*Rationale:* spec FR-004 requires the card to equal the list; the owner is the only party that defines "waiting". A new aggregating backend module would depend on seven other modules and break the one-way dependency rule (constitution, backend modularity).
*Alternatives rejected:* (a) one backend "home summary" that calls every module's facade, because it couples seven modules to one screen and one slow or failing source would slow or fail all cards (spec FR-006 wants independence); (b) counting every list on the client for all seven, because items 4 and 7 have no list or no "waiting" meaning and items 2 and 5 have an unfiltered list that includes closed items.

**D2. Cards ship in waves (spec Clarification 1).** Wave 1 shows the cards that are READY: late payments, change requests, design review. The other queue cards appear only when their owner's count exists. A card whose count does not exist is not rendered at all.
*Rationale:* the clarification forbids both an "unavailable" placeholder and a zero for a not-shipped queue.

**D3. Review moderation card is dropped.** The review feature (011) has no state that marks a review as awaiting moderation. By owner decision (Hồ Hoàng Quốc, 2026-11-18) the home has no such card and does not wait for the 011 owner; there is no R-6.

**D4. Check-in progress needs one aggregate read.** The 003.2 owner adds `GET /api/v1/workshop-day-summaries` returning sessions, cancelled sessions, checked-in and expected counts for one Vietnam-time date and an optional branch. Until it exists the strip shows only the session and cancelled counts that the existing list already gives.
*Rationale:* a per-session roster call for every session today is N+1 and would break the independent-card and speed goals.

**D5. Revenue reuses the dashboard's own read.** The card calls the same executive-report read with `periodType=MONTH` and no branch filter, so the figure equals the dashboard's by construction (spec FR-009, SC-004). It reads `totals.totalRevenue`; `PARTIAL` and `UNAVAILABLE` and HTTP 503 are shown as the card's own notice, never as a guessed value; when `developmentDemo` is true the card carries the same demo marker the dashboard shows.
*Alternatives rejected:* a new lighter revenue summary; it would create a second definition of revenue.

**D6. Recent events.** Operations denials and package availability changes come from the Operations audit read (two small calls). Settlement denials need a filter the Settlement audit read does not have; the 003.3 owner adds a denied-only filter to that read. Until then the section shows the Operations events and states that the Settlement source is "not yet included" (spec FR-010); that wording is different from the "part of the data is missing" note, which is for a source that exists but failed to read.
*Rationale:* paging 100 Settlement rows and filtering by prefix on the client would drop denials when more than 100 rows exist in the window.

**D7. Time zone.** "Today" and the 24-hour window are computed in Vietnam time (UTC+7) on the client and sent as explicit dates or instants; the session list's plain date filter is used as is.

**D8. Where the page lives.** A new frontend feature slice under `src/features/managerhome/` owns the Manager page and its route `/manager/home`. The Staff home stays in `src/features/operatorhome/` unchanged. The slice reads other features' endpoints through its own small infrastructure port and maps the responses there; it imports no other feature's private code (constitution, frontend boundary). Its route is registered at the composition root (`ppswbs_frontend/src/app/router.jsx`), not inside another feature, and the old `/manager/home` entry is removed from `operatorhome` in the same change so one route serves the address.

## 3. Reads an owner must add

| ID | Owner | Read | Purpose | Blocks |
| --- | --- | --- | --- | --- |
| R-1 | 003.2 workshop operations | `GET /api/v1/workshop-day-summaries?date=&locationId=` | sessions, cancelled, checked-in, expected for one day | check-in progress (spec FR-007) |
| R-2 | 003.3 settlement | refund-queue waiting count | card 2 | card 2 |
| R-3 | 003.3 settlement | cash-close differences awaiting confirmation: count | card 4 | card 4 |
| R-4 | 003.4 OTA connectivity | reconciliation items needing attention: count | card 5 | card 5 |
| R-5 | 003.3 settlement | denied-only filter on `GET /api/v1/workshop-audit/settlement-records` | Settlement half of recent events | spec FR-010 Settlement part |

Exact route names for R-2 to R-4 are the owner's to choose; the response shape and rules the home needs are in [contracts/reads.md](contracts/reads.md).

## 4. Unknowns left open

- Which refund and OTA statuses count as "waiting" (R-2, R-4); proposed in the contract, to be confirmed by the 003.3 and 003.4 owners.
- Whether a Manager with several branches should see counts limited to their branches; today the sources return everything a Manager may see, and the plan keeps that.
