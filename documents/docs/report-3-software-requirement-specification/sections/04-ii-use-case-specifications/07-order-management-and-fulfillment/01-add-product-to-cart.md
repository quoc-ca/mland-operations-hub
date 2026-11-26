### 7.1 Add Product to Cart

| Use Case ID | UC-52 | Use Case Name | Add Product to Cart |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | None |

#### Description

As a Member, I want to add an existing configured retail variant and quantity to my own cart so that I can prepare a purchase without reserving stock or freezing a price.

#### Trigger

A Member selects a configured product variant in the public catalogue and submits Add to Cart.

#### Preconditions

1. The platform is available.
2. The Member is authenticated with verified identity, has an active account, and holds current authority.
3. The Member can select a configured product variant; eligibility and quantity are checked on submission.

#### Postconditions

1. The accepted selection appears in the Member's own cart, or the rejected attempt receives safe guidance.
2. No order, payment, hold, stock deduction, or frozen purchase price is created.

#### Normal Flow

1. Member opens the public product catalogue and selects a configured size/engraving variant.
2. System displays the variant's identity, options, current approved whole-VND price, and logical availability.
3. Member enters a positive integer quantity and submits Add to Cart.
4. System rechecks account authority, variant eligibility, applied price, and requested quantity.
5. System saves the selection to the Member's cart, merging only matching variant/term identities.
6. System records attempt evidence and displays the updated cart with indicative prices.

#### Alternative Flows

3a. Member chooses Mua Ngay instead of Add to Cart → System adds the line and opens UC54 Checkout Retail Order for that one line with the best eligible voucher preselected.

5a. Variant or price/term identity differs from existing cart lines → System keeps the lines distinct; cart terms remain indicative until checkout.

6a. Member chooses Mua Ngay and the add is refused only because the cart already holds that branch's stock → System opens UC54 Checkout Retail Order with the existing cart line; if unreadable, System shows the stock-limit popup.

#### Exception Flows

2a. Unknown variant, free-text retail engraving, or ineligible/unpublished product → System rejects the selection with safe reselection guidance and creates no variant or purchase record; resume at step 1.

4a. Unauthorized access or nonpositive, fractional, or unavailable quantity → System denies unauthorized access or rejects invalid quantities (limit 50 cart lines and 1–99 per variant across all lines); Member corrects quantity at step 3.

4b. Cart quantity plus requested quantity exceeds branch stock → System refuses the add, leaves the cart unchanged, and shows a centred popup with one Đồng ý button ("Bạn đã có {n} sản phẩm này trong giỏ hàng. Không thể thêm số lượng đã chọn vào giỏ hàng vì sẽ vượt quá số lượng còn tại chi nhánh ({available})." or "Chi nhánh chỉ còn {available} sản phẩm này."); at max selectable quantity, the buy panel shows "Số lượng bạn chọn đã đạt mức tối đa của sản phẩm này". Resume at step 3.

5b. Save fails or concurrent cart edit conflicts → System reports a safe failure, rejects stale conflicting edits, reloads current cart values for review, and creates no partial purchase effects.

#### Business Rules

BR-52-01, BR-52-02, BR-52-03, BR-52-04, BR-52-05, BR-52-06, BR-52-07, BR-52-08

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-52-01 | Only the active owning Member may add cart items; internal roles cannot act by customer impersonation. |
| BR-52-02 | Cart items select existing configured size/engraving variants at applied approved positive whole-VND prices; arbitrary variants and free-text engraving are rejected. |
| BR-52-03 | Cart limits are 50 lines and integer quantity 1–99 per variant across all lines, subject to logical availability; cart items create no hold. |
| BR-52-04 | Keep variants/terms distinct; merge only the same variant with matching terms. Cart prices remain indicative. |
| BR-52-05 | Recheck current catalogue eligibility on submission; stale browsing/cart data grants no old-price or post-unpublish hold protection. |
| BR-52-06 | Reject stale conflicting edits and reload current cart values for review. |
| BR-52-07 | Record each attempt and its actual outcome under the shared audit policy. |
| BR-52-08 | A refused add states the branch stock and how many of that variant the cart already holds there, in a popup with one Đồng ý button. Mua Ngay for a variant the cart already holds up to the branch stock opens checkout with the existing line instead of refusing. |
