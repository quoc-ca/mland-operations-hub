### 7.1 Manage Shopping Cart

| Use Case ID | UC-53 | Use Case Name | Manage Shopping Cart |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | None |

#### Description

As a Member, I want to add retail variants to my own cart, change quantities, remove lines, and tick the lines I want to buy so that I can prepare a purchase before checkout.

#### Trigger

A Member submits Add to Cart or Mua Ngay on a product, opens Shopping Cart, or selects Mua lại from Purchase Order History.

#### Preconditions

1. The platform is available.
2. The Member is authenticated with verified identity, has an active account, and holds current authority.
3. The Member may have an empty cart; ownership is required for all cart reads and changes.

#### Postconditions

1. Accepted additions and edits update only the Member's own cart; rejected attempts receive safe guidance.
2. No order, payment, hold, stock deduction, or frozen purchase price is created.
3. Existing order snapshots, holds, and payments remain unaffected.

#### Normal Flow

1. Member selects a configured size variant, branch, and quantity, then submits Add to Cart.
2. System rechecks account authority, variant eligibility, approved price, and requested quantity.
3. System saves the line, merging only matching variant identities, and records attempt evidence.
4. System displays the cart grouped by branch with indicative prices and no line ticked.
5. Member ticks lines to preview eligible automatic discounts and vouchers per covered line.
6. Member updates a line quantity.
7. System validates the edit against current variant data and availability.
8. System saves the edit, records attempt evidence, and displays the revised cart.
9. Member proceeds to UC-54 Checkout and Pay for Retail Order with the ticked lines.

#### Alternative Flows

1a. Member opens Shopping Cart directly → System checks ownership, retrieves the cart, and resumes at step 4.

1b. Member chooses Mua Ngay instead of Add to Cart → System adds the line and opens UC-54 for that one line with the best eligible voucher preselected.

1c. Cart opened from Mua lại → System adds the past order's rings, skips rings already in the cart or unavailable, ticks only new lines, lists skipped rings, and resumes at step 4.

3a. Variant or price identity differs from existing cart lines → System keeps the lines distinct; cart terms remain indicative until checkout.

3b. Mua Ngay is refused only because the cart already holds that branch's stock → System opens UC-54 with the existing cart line.

4a. Cart is empty → System displays an empty-cart result with catalogue guidance.

4b. Catalogue price or availability changed → System displays current indicative values and changed eligibility without old-price or hold protection.

6a. Member removes a line → System validates ownership, removes the line, records evidence, and displays the revised cart.

#### Exception Flows

1d. Unknown variant, free-text retail engraving, or unpublished product → System rejects the selection with reselection guidance and creates no purchase record.

2a. Unauthorized access, another Member's cart, or suspended account → System denies the request without revealing or changing protected cart information.

2b. Nonpositive, fractional, or unavailable quantity → System rejects it (50 cart lines, 1–99 per variant across all lines); Member corrects it at step 1 (MSG74).

2c. Cart quantity plus requested quantity exceeds branch stock → System leaves the cart unchanged and shows a popup with one Đồng ý button stating the branch stock and the quantity already in the cart (MSG74).

7a. Invalid quantity or unsupported variant → System rejects the edit with correction guidance; Member resumes at step 6 or removes the line (MSG74).

8a. Retrieval or save failure, or stale conflicting edit → System reports a safe error, rejects the stale edit, reloads current values, and creates no order, payment, or hold.

#### Business Rules

BR-53-01, BR-53-02, BR-53-03, BR-53-04, BR-53-05, BR-53-06, BR-53-07, BR-53-08

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG74

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-53-01 | Only the active owning Member may add to, read, or edit the cart; internal roles cannot act by customer impersonation. |
| BR-53-02 | Cart items select existing configured size/engraving variants at applied approved positive whole-VND prices; arbitrary variants and free-text engraving are rejected. |
| BR-53-03 | Cart limits are 50 lines and integer quantity 1–99 per variant across all lines, subject to logical availability; cart items create no hold. |
| BR-53-04 | Keep variants and terms distinct; merge only the same variant with matching terms. Cart prices remain indicative, and checkout revalidates every line before freezing terms. |
| BR-53-05 | A ticked line shows its own share of the previewed automatic discount and voucher, never a share of a discount that does not cover it; loyalty points are shown only on the cart total. |
| BR-53-06 | Cart edits never change accepted orders or holds. Stale conflicting edits are rejected and reloaded; after payment only unchanged purchased lines are removed. |
| BR-53-07 | A refused add states the branch stock and how many of that variant the cart already holds there, in a popup with one Đồng ý button. Mua Ngay for a variant the cart already holds up to the branch stock opens checkout with the existing line instead of refusing. |
| BR-53-08 | Record each attempt and its actual outcome under the shared audit policy. |
