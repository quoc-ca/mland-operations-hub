### 8.1 Add Product to Cart

#### Primary Actors

Member.

#### Secondary Actors

None.

#### Description

As a Member, I want to add an existing configured retail variant and quantity to my own cart so that I can prepare a purchase without reserving stock or freezing a price (UC43).

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

The system denies unauthorized access and rejects nonpositive/fractional or unavailable requested quantities. At most 50 cart/order lines and integer quantity 1–99 per configured variant, aggregated across all same-variant lines regardless of distinct terms; current logical availability remains mandatory. (D04-a). The Member may correct quantity at Step 3 when authorized.

**Step 5 — Different variant or different price/term identity**

Keep the lines distinct. Only the same configured variant with matching price/snapshot terms may merge; cart terms remain indicative and will be revalidated at checkout.

**Step 5 — Save fails or concurrent cart edit conflicts**

Report a safe failure without false success or partial purchase effects. Reject stale conflicting edits and reload current values for review (confirmed A-02). Preserve the accepted cart and provide retry/review guidance.

#### Business Rules

BR-43-01, BR-43-02, BR-43-03, BR-43-04, BR-43-05, BR-43-06, BR-43-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-43-01 | Only the owning active Member may add retail cart items; Guest, Staff, Manager and Admin cannot purchase/edit a Member cart by impersonation (D-01). |
| BR-43-02 | Use a stable configured variant identity with predefined size/engraving, its applied approved positive whole-VND price and quantity. No customer-created variants or free-text engraving (Product Management; FR-003). |
| BR-43-03 | Quantity is a positive integer subject to logical availability; maximum 50 lines/cart and aggregate quantity 99 per variant apply (D04-a). A cart line creates no reservation or physical-stock guarantee. |
| BR-43-04 | Distinct variants remain distinct. Same-variant selections merge only when price/snapshot terms also match; a displayed cart price is not a protected purchase snapshot. |
| BR-43-05 | Submission reassesses current catalogue eligibility. Earlier browsing/cart views confer no old-price protection or right to a new hold after unpublish. |
| BR-43-06 | Confirmed behavior (A-02): reject stale conflicting cart edits with review/reload guidance; this behavior is confirmed. |
| BR-43-07 | Retain safe normally append-only actor/source, target, action/time, actual SUCCESS/REJECTED/FAILED outcome and reason, with permitted references/context. Rejected or failed attempts are not success; exclude credentials, raw tokens and avoidable personal data. Business audit follows five-year retention without extending recipient-data retention. |
