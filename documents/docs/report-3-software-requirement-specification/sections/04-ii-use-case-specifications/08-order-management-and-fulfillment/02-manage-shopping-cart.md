### 8.2 Manage Shopping Cart

#### Primary Actors

Member.

#### Secondary Actors

None.

#### Description

As a Member, I want to view my own cart, change quantities and remove lines so that I can review current purchase intent before whole-cart checkout (UC44).

#### Preconditions

1. The platform is available.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The Member may have an empty cart; ownership is required for all cart reads and changes.

#### Postconditions

- The Member sees own current cart or an appropriate empty/error result; accepted edits update only that cart.
- Existing order snapshots/holds/payments remain unaffected; cart changes create no purchase effects.

#### Normal Flow

**Manage Shopping Cart**

1. The Member opens Shopping Cart.
2. The system checks current ownership/account authority and retrieves that Member's unexpired cart, applying the 30-day last-Member-edit retention rule.
3. The system displays distinct product/variant/options, quantities and current indicative prices/eligibility.
4. The Member changes a quantity or chooses a line to remove.
5. The system rechecks authority and validates the requested edit against current line/variant data and logical availability.
6. The system saves the accepted edit, records safe evidence and displays the revised cart and values.
7. The Member continues editing or proceeds to UC45 Checkout Retail Order.

#### Alternative Flows

**Step 2 — Empty cart**

Display an empty-cart result and catalogue guidance, including when cart retention has removed the cart. This is not a retrieval failure; no order/hold can be created from an empty cart.

**Steps 2 and 5 — Another Member's cart or suspended/revoked account**

Deny the request without revealing protected cart information or changing it. A cart reference alone grants no access.

**Steps 3 and 5 — Catalogue price or availability changed**

Show current indicative values and changed eligibility rather than claiming old-price/hold protection. Whole-cart invalid-line removal/reporting belongs to UC45; require explicit review/resubmission after correction or quote change and reject stale conflicts (confirmed A-01/A-02).

**Step 5 — Invalid quantity or unsupported variant**

Reject nonpositive/fractional or unavailable quantity and unsupported configured variants with correction guidance. At most 50 cart/order lines and integer quantity 1–99 per configured variant, aggregated across all same-variant lines regardless of distinct terms; current logical availability remains mandatory. (D04-a). The Member may resume at Step 4 or remove the line.

**Steps 2 and 6 — Retrieval/save failure or stale conflicting edit**

Distinguish failure from an empty cart or accepted edit, preserve established facts and provide safe retry/review guidance. Confirmed A-02 rejects stale conflicting edits; no order/payment/hold is created.

#### Business Rules

BR-44-01, BR-44-02, BR-44-03, BR-44-04, BR-44-05, BR-44-06

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-44-01 | Only the active owning Member may read/edit the cart; internal support grants do not permit impersonation or cart mutation (D-01). |
| BR-44-02 | Displayed cart values are current purchase intent, not frozen order terms or held quantities; checkout revalidates every line. |
| BR-44-03 | Keep exact variant/options identity; require positive integer quantities and reject unsupported combinations. At most 50 cart/order lines and integer quantity 1–99 per configured variant, aggregated across all same-variant lines regardless of distinct terms; current logical availability remains mandatory. (D04-a). |
| BR-44-04 | Cart removal/quantity changes do not reprice, release, revive or settle an accepted order/hold; later Member additions are not automatically part of an earlier order. |
| BR-44-05 | Confirmed behavior (A-02): reject stale conflicts and require review of changed terms. After paid, remove only purchased cart lines unchanged by the Member since checkout. Preserve any subsequently edited or added line entirely; never subtract purchased quantity from a later edit. Frozen orders remain independent of cart edits. Cart expires 30 days after the last Member edit without changing existing orders/holds/payment (A-07/D07-b). |
| BR-44-06 | Retain safe normally append-only actor/source, target, action/time, actual SUCCESS/REJECTED/FAILED outcome and reason, with permitted references/context. Rejected or failed attempts are not success; exclude credentials, raw tokens and avoidable personal data. Business audit follows five-year retention without extending recipient-data retention. |
