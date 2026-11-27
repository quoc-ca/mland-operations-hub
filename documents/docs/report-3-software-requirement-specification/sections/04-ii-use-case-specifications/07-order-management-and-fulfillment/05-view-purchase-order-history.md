### 7.3 View Purchase Order History

| Use Case ID | UC-56 | Use Case Name | View Purchase Order History |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | None |

#### Description

As a Member, I want to view my own past and ongoing retail orders so that I can understand recorded purchase and shop progress without changing orders.

#### Trigger

A Member opens Purchase Order History.

#### Preconditions

1. The platform is available.
2. The Member is authenticated with verified identity, has an active account, and holds current authority.
3. The Member may have zero or more retail orders; no reference alone authorizes access.

#### Postconditions

1. The Member sees their own historical and current order summaries or an appropriate empty/error result.
2. No order, payment, hold, quantity, or fulfilment state changes; retired personal-data boundaries remain intact.

#### Normal Flow

1. Member opens Purchase Order History.
2. System rechecks account authority and restricts retrieval to the owning Member.
3. System retrieves past and ongoing retail order summaries, payment facts, and fulfilment steps.
4. System displays orders newest-first across the approved status tabs with hold countdowns and received times.
5. Member selects an order to open UC57 View Order Details.

#### Alternative Flows

3a. Member has no retail orders → System displays an empty-history state with purchase guidance without fabricating orders.

3b. Catalogue changed, legacy snapshot, or retired recipient data → System keeps recorded historical facts, leaves missing legacy options unknown, and hides retired recipient data.

5a. Member selects Thanh toán, Hủy đơn, or Mua lại → System routes to UC-54 payment, cancels the unpaid order, or adds available rings back to the cart (UC53).

5b. Member selects Đánh giá or Xem đánh giá on a delivered/picked-up order → System opens the review screen for unreviewed products (UC75) or displays existing reviews.

#### Exception Flows

2a. Invalid/revoked authority or another Member's reference → System denies access without exposing private history.

3c. Retrieval failure or undefined filter/paging option → System displays a safe read error/retry result while retaining newest-first ordering and approved tabs.

5c. Review status cannot be read → System still loads the order list, keeps Đánh giá available, and rechecks line eligibility upon review submission.

#### Business Rules

BR-56-01, BR-56-02, BR-56-03, BR-56-04, BR-56-05, BR-56-06, BR-56-07

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-56-01 | Only the active owning Member views retail purchase history; internal roles receive no customer impersonation grant. |
| BR-56-02 | History is read-only and shows recorded purchase, payment, hold, internal fulfilment and recorded GHTK shipment facts without GHTK provider tracking. |
| BR-56-03 | Preserve accepted quantities, prices, discounts and totals; the product name, SKU and size shown on an order are read from the current catalogue when it can be read and fall back to the accepted ones otherwise; missing legacy options remain unknown. |
| BR-56-04 | Sort newest first by creation time then stable identity, grouped in the tabs Tất cả, Chờ thanh toán, Chờ lấy hàng, Chờ giao hàng, Chờ nhận tại cửa hàng, Đã giao and Đã hủy; after payment the list opens the tab of that order. |
| BR-56-05 | Retire carrier recipient/contact/address values 30 days after original actual handoff; five-year minimized evidence does not retain those values. |
| BR-56-06 | Recheck ownership and record safe read/rejection outcomes; knowing an order reference grants no access. |
| BR-56-07 | Đánh giá and Xem đánh giá appear only on the Member's own picked-up or delivered order; each order line is reviewed at most once, and eligibility is rechecked when the review is submitted. |
