### 8.5 View Purchase Order History

#### Primary Actors

Member.

#### Secondary Actors

None.

#### Description

As a Member, I want to view my own past and ongoing retail orders so that I can understand recorded purchase and shop progress without changing orders (UC47).

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

Provide a safe read error/retry result. Retain the approved 20/max 50 paging, newest-first ordering and creation-date/payment/fulfilment filters (D08-a/A-09).

**Steps 3 and 4 — Catalogue changes, legacy snapshots or retired recipient data**

Keep recorded historical facts instead of current catalogue replacements. Do not guess legacy variant options or reveal retired recipient/contact/address data.

#### Business Rules

BR-47-01, BR-47-02, BR-47-03, BR-47-04, BR-47-05, BR-47-06

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-47-01 | UC47 is own-history read access for the active Member; Guest/internal business roles receive no customer impersonation grant (D-01). |
| BR-47-02 | Show recorded retail purchase/payment/hold/internal-fulfilment facts; no courier tracking, delivery outcome or new payment/order mutation. |
| BR-47-03 | Preserve accepted historical names/options/quantity/prices/discounts/totals. Legacy missing variant options remain unknown; never infer mappings. |
| BR-47-04 | Confirmed behavior (A-09): Order history is newest first by creation time, with stable order identity as tie-breaker; default 20 orders/page, maximum 50. Filters cover creation date, payment state and fulfilment state, always within current authority. Quality targets follow approved D08-b–d; no achieved performance is claimed. |
| BR-47-05 | Retired carrier recipient/contact/address data is absent/irreversibly obscured after 30 days from actual original handoff. Five-year minimized payment/audit history does not preserve those personal values. |
| BR-47-06 | Protected access and failed/rejected read evidence remain safe and purpose-appropriate; a reference is not authorization (D-01; FR-023). |
