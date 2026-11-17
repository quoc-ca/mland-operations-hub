### 7.5 View Purchase Order History

#### Primary Actors

Member.

#### Secondary Actors

None.

#### Description

As a Member, I want to view my own past and ongoing retail orders so that I can understand recorded purchase and shop progress without changing orders.

#### Preconditions

1. The platform is available.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The Member may have zero or more retail orders; no reference alone authorizes access.

#### Postconditions

- The Member sees own historical/current order summaries or an appropriate empty/error result.
- No order, payment, hold, quantity or fulfilment state changes. Historical accepted terms and retired personal-data boundaries are preserved.

#### Normal Flow

**View Purchase Order History**

1. The Member opens Purchase Order History.
2. The system rechecks current account authority and restricts retrieval to the owning Member.
3. The system retrieves past and ongoing retail order summaries with recorded references, payment/hold facts and internal fulfilment progress.
4. The system presents newest-first creation-time order history with stable identity tie-breaker, default 20/max 50 orders per page and creation-date/payment/fulfilment-state filters, restricted to the owning Member.
5. The Member selects an own order to continue to UC48 View Order Details.

#### Alternative Flows

**Step 2 — Invalid/revoked authority or another Member's reference**

Deny without exposing private history. Internal branch reads belong to the scoped UC48/support boundary, not a grant to browse all Member histories.

**Step 3 — No retail orders**

Display an empty-history result with safe purchase guidance; do not fabricate orders or report a retrieval error as empty.

**Steps 3 and 4 — Retrieval failure or undefined filter/paging option**

Provide a safe read error/retry result. Retain the approved 20/max 50 paging, newest-first ordering and creation-date/payment/fulfilment filters.

**Steps 3 and 4 — Catalogue changes, legacy snapshots or retired recipient data**

Keep recorded historical facts instead of current catalogue replacements. Do not guess legacy variant options or reveal retired recipient/contact/address data.

#### Business Rules

BR-47-01, BR-47-02, BR-47-03, BR-47-04, BR-47-05, BR-47-06

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-47-01 | Only the active owning Member views retail purchase history; internal roles receive no customer impersonation grant. |
| BR-47-02 | History is read-only and shows recorded purchase, payment, hold and internal fulfilment facts without courier tracking. |
| BR-47-03 | Preserve accepted historical names/options/quantities/prices/discounts/totals; missing legacy options remain unknown. |
| BR-47-04 | Sort newest first by creation time then stable identity; default 20/max 50 orders per page, filtered by creation date, payment or fulfilment state. |
| BR-47-05 | Retire carrier recipient/contact/address values 30 days after original actual handoff; five-year minimized evidence does not retain those values. |
| BR-47-06 | Recheck ownership and record safe read/rejection outcomes; knowing an order reference grants no access. |
