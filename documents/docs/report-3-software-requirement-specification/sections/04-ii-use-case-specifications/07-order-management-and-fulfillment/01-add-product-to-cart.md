### 7.1 Add Product to Cart

#### Primary Actors

Member.

#### Secondary Actors

None.

#### Description

As a Member, I want to add an existing configured retail variant and quantity to my own cart so that I can prepare a purchase without reserving stock or freezing a price.

#### Preconditions

1. The platform is available.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The actor can select a configured product variant; current eligibility and quantity are checked during submission.

#### Postconditions

- The accepted selection appears in the Member's own cart, or the rejected/failed attempt receives safe guidance.
- No order, payment, hold, stock deduction or frozen purchase price is created.

#### Normal Flow

**Add Product to Cart**

1. The Member opens the public product catalogue and selects a configured size/engraving variant.
2. The system displays the selected variant's identity/options, current applied approved whole-VND price and logical availability.
3. The Member enters a positive integer quantity and submits Add to Cart.
4. The system rechecks current account/ownership authority, the configured variant, publication/active-category/public-content eligibility, applied price and requested quantity.
5. The system saves the selection to the Member's cart, retaining distinct variant/term identities and applying only permitted same-variant merging.
6. The system records safe attempt evidence and displays the updated cart with current indicative prices. When the Member chose Mua Ngay instead of Add to Cart, the system adds the line and opens UC54 Checkout Retail Order for that one line, with the best eligible voucher preselected.

#### Alternative Flows

**Steps 2 and 4 — Unknown variant or stale/ineligible catalogue selection**

The system rejects unknown combinations, free-text retail engraving, unpublished/ineligible products or unapplied/unapproved prices. It provides safe reselection guidance; no variant or purchase record is invented. Resume at Step 1.

**Step 4 — Missing ownership/account authority or invalid quantity**

The system denies unauthorized access and rejects nonpositive/fractional or unavailable requested quantities. Allow at most 50 cart lines and quantity 1–99 per variant across all lines, subject to logical availability. The Member may correct quantity at Step 3 when authorized.

**Step 4 — Requested quantity exceeds the branch stock**

The system counts the same variant already in the Member's cart at that branch. When that count plus the requested quantity exceeds the branch's available quantity, the system refuses the add, leaves the cart unchanged and shows the reason in a centred popup with one Đồng ý button: "Bạn đã có {n} sản phẩm này trong giỏ hàng. Không thể thêm số lượng đã chọn vào giỏ hàng vì sẽ vượt quá số lượng còn tại chi nhánh ({available})." or, when the cart holds none, "Chi nhánh chỉ còn {available} sản phẩm này." When the chosen quantity already equals the most the Member can choose, the buy panel shows "Số lượng bạn chọn đã đạt mức tối đa của sản phẩm này" under the quantity. Resume at Step 3.

**Step 6 — Mua Ngay when the cart already holds the branch stock**

When the Member chose Mua Ngay and the add is refused only because the cart already holds that variant at that branch, the system opens UC54 Checkout Retail Order with the existing cart line instead of showing the popup. If that line cannot be read, the system shows the popup of the previous flow.

**Step 5 — Different variant or different price/term identity**

Keep the lines distinct. Only the same configured variant with matching price/snapshot terms may merge; cart terms remain indicative and will be revalidated at checkout.

**Step 5 — Save fails or concurrent cart edit conflicts**

Report a safe failure without false success or partial purchase effects. Reject stale conflicting edits and reload current values for review. Preserve the accepted cart and provide retry/review guidance.

#### Business Rules

BR-52-01, BR-52-02, BR-52-03, BR-52-04, BR-52-05, BR-52-06, BR-52-07, BR-52-08

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
