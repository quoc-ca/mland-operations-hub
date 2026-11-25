# Contract: Reads used by Manager Home

> **2026-11-25: OTA đã bị loại** (feature 003.4): rail còn 9 nhóm (bỏ `ota`), còn 5 hàng đợi có badge, read R-4 bị gỡ. ID giữ nguyên; phần còn nhắc OTA là lịch sử. Xem [quyết định loại OTA](../../003-workshop-management/ota-removal-2026-11-25.md).

> **Renamed 2026-11-25:** this folder was `013-manager-home`; it is now `014-navigation-shell`. The spec covers the navigation shell only; the Manager Home dashboard blocks moved into `010-manager-dashboard` (FR-034 to FR-038). This file keeps the earlier Manager Home history and was not rewritten; only the folder path was updated.

**Date created**: 2026-11-18
**Date updated**: 2026-11-18
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Research**: [research.md](../research.md)

Manager Home exposes no endpoint of its own. This file lists the reads it **consumes**, so each owner can see what the home relies on, and the **new reads proposed** to the owners. Existing reads are described only as far as the home depends on them; their own contracts stay the source of truth. A proposed read is a proposal until its owner approves it. All reads are `GET`, versioned under `/api/v1`, Manager-authorized on the server, and return the usual typed error with a correlation id.

## A. Existing reads consumed (no change asked of the owner)

| Card | Read | What the home takes from it |
| --- | --- | --- |
| Late payments | `GET /api/v1/late-payment-reviews` | Number of items in the default response (`PENDING_MANAGER`). |
| Change requests | `GET /api/v1/workshop-change-requests` | Number of items in the default response (`PENDING`). |
| Design review | `GET /api/v1/design-review-requests` | Number of items in `items` (a Manager sees all). |
| Today strip (sessions) | `GET /api/v1/workshop-sessions?from=<day>&to=<day>[&locationId=]` | Number of sessions, and how many have state `CANCELLED`. `<day>` is today in Vietnam time. |
| Revenue | `GET /api/v1/executive-reports?periodType=MONTH` (no branch filter) | `totals.totalRevenue`, `status`, `developmentDemo`; HTTP 503 means unavailable. The same call the dashboard makes. |
| Branch picker | `GET /api/v1/workshop-locations` | Branch ids and names the Manager may choose for the today strip. |
| Recent events (Operations) | `GET /api/v1/workshop-audit/operations-events?outcome=DENIED&from=<24h ago>&limit=5` and `...?action=WORKSHOP_PACKAGE_AVAILABILITY_CHANGED&from=<24h ago>&limit=5` | Time, actor, action of each row; merged client-side newest first, first five kept. |

The home never reads the free-text or metadata fields of audit rows.

## B. Reads proposed to the owners

Common rules for every proposed count:

- The number MUST equal the number of items the owning list shows for the same Manager when it is opened with the filter the card link carries (spec FR-004). The owner defines "waiting" once and uses that definition in both places.
- Response is a single whole number plus nothing else the home needs: `{ "waiting": <integer ≥ 0> }`.
- Manager only; any other caller gets the owner's existing refusal and no number.
- Read-only; writes no audit row.

| ID | Owner | Proposed read | Definition the owner decides | Proposal |
| --- | --- | --- | --- | --- |
| R-1 | 003.2 | `GET /api/v1/workshop-day-summaries?date=<yyyy-MM-dd>[&locationId=]` | Counts for one Vietnam day. "Expected" is proposed as the participants holding a valid ticket for the non-cancelled sessions of the day (not the sessions' capacity, which is what the dashboard's utilization uses); the owner confirms it. | Response `{ "date", "sessions", "cancelledSessions", "checkedIn", "expected" }`. `sessions` includes cancelled sessions; `checkedIn` and `expected` cover non-cancelled sessions only. |
| R-2 | 003.3 | Count of refund requests waiting | Which of `PENDING_ACCOUNT`, `PENDING_APPROVAL`, `PENDING_TRANSFER`, `PENDING_RECONCILIATION` mean the Manager has to act | Proposal: the four `PENDING_*` statuses; the card opens the refund queue filtered to the same set. |
| R-3 | 003.3 | Count of cash closes awaiting Manager confirmation | State `PENDING_MANAGER_CONFIRMATION` | The card opens the cash-close screen on its confirmation view. |
| R-4 | — | *(Removed 2026-11-25 with OTA.)* | — | — |
| R-5 | 003.3 | Denied-only filter on `GET /api/v1/workshop-audit/settlement-records` | Rows whose action code starts with `DENIED_` | Add an optional boolean filter for denied rows; the existing response is unchanged. |

## C. Rules for the home as a consumer

- A card is shown only when its read exists and returns a count that matches its list (spec Clarification 1). A missing read means a hidden card, not an error.
- A failed read affects only its own card (spec FR-006); the failure shows that card's retry and never a zero.
- No read is retried automatically and none is polled (spec FR-013).
- A refused read (HTTP 403) shows no data from that source and the shared access-denied handling applies (spec FR-014).

## D. Delivered routes (2026-11-18)

The owners' reads, as built (owner agreement and definitions: Hồ Hoàng Quốc, owner of Feature 003, 2026-11-18). The definition of "waiting" is the proposal in section B, unchanged. Each count equals the size of the list the card opens (FR-004). All are `GET`, read-only, write no audit row, and refuse a non-Manager without a number.

| ID | Route | Response | The list the card opens |
| --- | --- | --- | --- |
| R-1 | `GET /api/v1/workshop-day-summaries?date=&locationId=` (Manager only) | `{ date, sessions, cancelledSessions, checkedIn, expected }` | the session list for the same day and branch (`sessions`, `cancelledSessions`) |
| R-2 | `GET /api/v1/workshop-refund-requests/waiting-count` | `{ waiting }` | `GET /api/v1/workshop-refund-requests?waiting=true` (screen `/settlement/refunds?status=WAITING`) |
| R-3 | `GET /api/v1/workshop-cash-closes/awaiting-confirmation-count` | `{ waiting }` | `GET /api/v1/workshop-cash-closes?awaitingConfirmation=true` (Manager only), shown on the cash-close screen as `/settlement/cash-close?view=confirmation`, where a Manager can also confirm each difference (decision "làm luôn", Hồ Hoàng Quốc, 2026-11-18) |
| R-4 | *(Removed 2026-11-25 with OTA; the route no longer exists.)* | — | — |
| R-5 | `GET /api/v1/workshop-audit/settlement-records?denied=true` | the existing page | the same read |
