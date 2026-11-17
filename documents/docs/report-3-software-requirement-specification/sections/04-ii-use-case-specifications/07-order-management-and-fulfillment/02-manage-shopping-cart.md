### 7.2 Manage Shopping Cart

#### Primary Actors

Member.

#### Secondary Actors

None.

#### Description

As a Member, I want to view my own cart, change quantities and remove lines so that I can review current purchase intent before whole-cart checkout.

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

Show current indicative values and changed eligibility rather than claiming old-price/hold protection. Whole-cart invalid-line removal/reporting belongs to UC45; require explicit review/resubmission after correction or quote change and reject stale conflicts.

**Step 5 — Invalid quantity or unsupported variant**

Reject nonpositive/fractional or unavailable quantity and unsupported configured variants with correction guidance. Allow at most 50 cart lines and quantity 1–99 per variant across all lines, subject to logical availability. The Member may resume at Step 4 or remove the line.

**Steps 2 and 6 — Retrieval/save failure or stale conflicting edit**

Distinguish failure from an empty cart or accepted edit, preserve established facts and provide safe retry/review guidance. Reject stale edits and reload current values; no order, payment or hold is created.

#### Business Rules

BR-44-01, BR-44-02, BR-44-03, BR-44-04, BR-44-05, BR-44-06

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-44-01 | Only the active owning Member may read/edit the cart; support access grants no impersonation or mutation. |
| BR-44-02 | Cart values represent current intent; checkout revalidates every line before freezing purchase terms or reserving quantities. |
| BR-44-03 | Use configured variants, at most 50 lines and positive integer quantities totaling at most 99 per variant, subject to logical availability. |
| BR-44-04 | Cart edits never change accepted orders/holds or include later additions in earlier purchases. |
| BR-44-05 | Reject stale conflicts; after payment remove only unchanged purchased lines, preserve later edits/additions, and expire cart 30 days after the last Member edit. |
| BR-44-06 | Record each attempt and its actual outcome under the shared audit policy. |
