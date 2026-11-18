# Data Model: Manager Home

**Date created**: 2026-11-18
**Date updated**: 2026-11-18
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Manager Home stores nothing. There is no table, no column and no migration. It reads the owning features' data and shows a short-lived view in the browser. This file defines the view the page holds in memory and where each piece comes from.

## View model (in the browser only)

Each card is loaded on its own and is dropped on sign-out or session loss. Nothing is cached across visits.

| View | Fields | Source (owner) | Rules |
| --- | --- | --- | --- |
| **Waiting-work card** | `queue` (one of: late-payments, refund-queue, change-requests, cash-close-differences, ota-reconciliation, design-review, review-moderation), `state` (loading, ready, unavailable), `count` (whole number, only when ready), `target` (the owning list the card opens) | the owning feature (see research D1) | A card exists only if its queue has a count that matches its list (spec FR-004, Clarification 1). `count` is never shown when `state` is not ready. Zero is a valid ready count. |
| **Today strip** | `date` (Vietnam day), `branch` (all, or one branch the Manager may see), `sessions`, `cancelledSessions`, `checkedIn`, `expected`, `state` | Workshop operations (003.2) | `sessions` includes cancelled ones; `checkedIn` and `expected` cover non-cancelled sessions only (spec FR-007). `checkedIn` and `expected` are absent until read R-1 exists. |
| **Revenue figure** | `periodLabel` (current month), `totalRevenue` (VND, may be absent), `status` (complete, empty, partial, unavailable), `isDemo` | Manager Dashboard reads (010) | Equals the dashboard figure for the same period and scope. Absent or partial is shown as such, never as a guessed value. |
| **Recent event** | `time`, `actor` (id and name, or system), `kind` (refused attempt, package availability change), `source` (operations, settlement), `action` | Audit review (003.2 US7 and 003.3) | At most five, newest first, last 24 hours. Only what the audit read exposes; no personal data. |
| **Shortcut** | `label`, `target` | fixed list in the page | Fixed set; no data. |
| **Page status** | `lastUpdatedAt` (Vietnam time), per-section `state` | the page | Set on open and on explicit refresh; no timer. |

## Relationships and ownership

- A waiting-work card points to exactly one owning list; the list and its items belong to the owning feature and are never copied.
- The revenue figure and the recent events are read-only views of 010 and the audit review.
- The branch choice applies only to the Today strip (spec FR-008) and lives only for the visit.

## State changes

None. The page never writes (spec FR-012). The only state is the loading, ready and unavailable state of each section, which resets on every open or refresh.

## Sensitive data

The page shows counts and short audit rows only. It shows no customer name, e-mail, phone or free-text reason. The Settlement audit read returns a free-text reason that may hold personal data; Manager Home MUST NOT display or keep that field (it uses only action, time and actor).
