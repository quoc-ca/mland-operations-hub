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
3. The system retrieves past and ongoing retail order summaries with recorded order codes, product images, payment/hold facts, internal fulfilment progress and the recorded GHTK shipment step.
4. The system presents newest-first order history in the tabs Tất cả, Chờ thanh toán, Chờ lấy hàng, Chờ giao hàng, Chờ nhận tại cửa hàng, Đã giao and Đã hủy, restricted to the owning Member. An unpaid VNPay order shows a countdown to its hold deadline; a received order shows its received time.
5. The Member selects an own order to continue to UC57 View Order Details, or uses an eligible action: Thanh toán (UC55), Hủy đơn for an unpaid order, or Mua lại, which adds the rings to the cart again (UC53). A delivered or picked-up order also offers Đánh giá, which opens one review screen for every product of the order not reviewed yet (UC-011.1), or Xem đánh giá once all of them are reviewed.

#### Alternative Flows

**Step 2 — Invalid/revoked authority or another Member's reference**

Deny without exposing private history. Internal branch reads belong to the scoped UC57/support boundary, not a grant to browse all Member histories.

**Step 3 — No retail orders**

Display an empty-history result with safe purchase guidance; do not fabricate orders or report a retrieval error as empty.

**Steps 3 and 4 — Retrieval failure or undefined filter/paging option**

Provide a safe read error/retry result. Retain the newest-first ordering and the approved tabs.

**Step 5 — Review status cannot be read**

The order list still loads; Đánh giá stays available and the review screen rechecks which lines are eligible and already reviewed.

**Steps 3 and 4 — Catalogue changes, legacy snapshots or retired recipient data**

Keep recorded historical facts instead of current catalogue replacements. Do not guess legacy variant options or reveal retired recipient/contact/address data.

#### Business Rules

BR-56-01, BR-56-02, BR-56-03, BR-56-04, BR-56-05, BR-56-06, BR-56-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-56-01 | Only the active owning Member views retail purchase history; internal roles receive no customer impersonation grant. |
| BR-56-02 | History is read-only and shows recorded purchase, payment, hold, internal fulfilment and recorded GHTK shipment facts without GHTK provider tracking. |
| BR-56-03 | Preserve accepted historical names/options/quantities/prices/discounts/totals; missing legacy options remain unknown. |
| BR-56-04 | Sort newest first by creation time then stable identity, grouped in the tabs Tất cả, Chờ thanh toán, Chờ lấy hàng, Chờ giao hàng, Chờ nhận tại cửa hàng, Đã giao and Đã hủy; after payment the list opens the tab of that order. |
| BR-56-05 | Retire carrier recipient/contact/address values 30 days after original actual handoff; five-year minimized evidence does not retain those values. |
| BR-56-06 | Recheck ownership and record safe read/rejection outcomes; knowing an order reference grants no access. |
| BR-56-07 | Đánh giá and Xem đánh giá appear only on the Member's own picked-up or delivered order; each order line is reviewed at most once, and eligibility is rechecked when the review is submitted. |
