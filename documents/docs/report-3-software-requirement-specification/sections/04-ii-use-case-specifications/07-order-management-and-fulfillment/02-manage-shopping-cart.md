### 7.2 Manage Shopping Cart

| Use Case ID | UC-53 | Use Case Name | Manage Shopping Cart |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | None |

#### Description

As a Member, I want to view my own cart, change quantities, remove lines, and tick the lines I want to buy so that I can review current purchase intent before checkout.

#### Trigger

A Member opens Shopping Cart or selects Mua lại from Purchase Order History.

#### Preconditions

1. The platform is available.
2. The Member is authenticated with verified identity, has an active account, and holds current authority.
3. The Member may have an empty cart; ownership is required for all cart reads and changes.

#### Postconditions

1. The Member sees their current cart or an empty/error result; accepted edits update only that cart.
2. Existing order snapshots, holds, and payments remain unaffected; cart changes create no purchase effects.

#### Normal Flow

1. Member opens Shopping Cart.
2. System checks ownership authority and retrieves the Member's cart.
3. System displays distinct items, quantities, and indicative prices grouped by branch, with no line ticked.
4. Member ticks lines to preview eligible automatic discounts and vouchers per covered line.
5. Member updates a line quantity.
6. System rechecks authority and validates the edit against current variant data and availability.
7. System saves the edit, records attempt evidence, and displays the revised cart.
8. Member proceeds to UC54 Checkout Retail Order with the ticked lines.

#### Alternative Flows

1a. Cart opened from Mua lại → System adds the past order's rings to the cart, skips rings already in the cart or unavailable, ticks only newly added lines, lists skipped rings, and resumes at step 5.

2a. Cart is empty → System displays an empty-cart result with catalogue guidance without creating an order or hold.

3a. Catalogue price or availability changed → System displays current indicative values and changed eligibility without claiming old-price or hold protection, requiring review before checkout.

5a. Member removes a line → System validates ownership, removes the line, records evidence, and displays the revised cart.

#### Exception Flows

2b. Another Member's cart or suspended/revoked account → System denies the request without revealing protected cart information or changing it.

6a. Invalid quantity or unsupported variant → System rejects nonpositive, fractional, or unavailable quantities (limit 50 lines, 1–99 per variant) and unsupported variants with correction guidance; Member resumes at step 5 or removes the line.

7a. Retrieval/save failure or stale conflicting edit → System reports a safe error, rejects stale edits, reloads current values, and creates no order, payment, or hold.

#### Business Rules

BR-53-01, BR-53-02, BR-53-03, BR-53-04, BR-53-05, BR-53-06

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-53-01 | Only the active owning Member may read/edit the cart; support access grants no impersonation or mutation. |
| BR-53-02 | Cart values represent current intent; checkout revalidates every line before freezing purchase terms or reserving quantities. A ticked line shows its own share of the previewed automatic discount and voucher, never a share of a discount that does not cover it; loyalty points are shown only on the cart total. |
| BR-53-03 | Use configured variants, at most 50 lines and positive integer quantities totaling at most 99 per variant, subject to logical availability. |
| BR-53-04 | Cart edits never change accepted orders/holds or include later additions in earlier purchases. |
| BR-53-05 | Reject stale conflicts; after payment remove only unchanged purchased lines and preserve later edits/additions. |
| BR-53-06 | Record each attempt and its actual outcome under the shared audit policy. |
