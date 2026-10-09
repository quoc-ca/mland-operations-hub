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
6. The system records safe attempt evidence and displays the updated cart with current indicative prices.

#### Alternative Flows

**Steps 2 and 4 — Unknown variant or stale/ineligible catalogue selection**

The system rejects unknown combinations, free-text retail engraving, unpublished/ineligible products or unapplied/unapproved prices. It provides safe reselection guidance; no variant or purchase record is invented. Resume at Step 1.

**Step 4 — Missing ownership/account authority or invalid quantity**

The system denies unauthorized access and rejects nonpositive/fractional or unavailable requested quantities. Allow at most 50 cart lines and quantity 1–99 per variant across all lines, subject to logical availability. The Member may correct quantity at Step 3 when authorized.

**Step 5 — Different variant or different price/term identity**

Keep the lines distinct. Only the same configured variant with matching price/snapshot terms may merge; cart terms remain indicative and will be revalidated at checkout.

**Step 5 — Save fails or concurrent cart edit conflicts**

Report a safe failure without false success or partial purchase effects. Reject stale conflicting edits and reload current values for review. Preserve the accepted cart and provide retry/review guidance.

#### Business Rules

BR-43-01, BR-43-02, BR-43-03, BR-43-04, BR-43-05, BR-43-06, BR-43-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-43-01 | Only the active owning Member may add cart items; internal roles cannot act by customer impersonation. |
| BR-43-02 | Cart items select existing configured size/engraving variants at applied approved positive whole-VND prices; arbitrary variants and free-text engraving are rejected. |
| BR-43-03 | Cart limits are 50 lines and integer quantity 1–99 per variant across all lines, subject to logical availability; cart items create no hold. |
| BR-43-04 | Keep variants/terms distinct; merge only the same variant with matching terms. Cart prices remain indicative. |
| BR-43-05 | Recheck current catalogue eligibility on submission; stale browsing/cart data grants no old-price or post-unpublish hold protection. |
| BR-43-06 | Reject stale conflicting edits and reload current cart values for review. |
| BR-43-07 | Record each attempt and its actual outcome under the shared audit policy. |
