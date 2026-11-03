### 2.1 Browse Product Catalogue

#### Primary Actors

Guest, Member.

#### Secondary Actors

Cloud Storage Service.

#### Description

As a public catalogue visitor, I want to browse published retail ring products, search and combine supported filters, and compare configured variants' prices and availability before a separate purchase journey.

#### Preconditions

1. The platform is available and the actor can access the public catalogue without authentication.
2. The catalogue may contain zero or more configured products; an empty catalogue does not prevent browsing.

#### Postconditions

- The actor views matching public products and their configured variants, or an appropriate empty/error state with recovery guidance.
- Draft content, proposed prices, and internal hold records are not exposed.
- No cart, order, payment, hold, or purchase-price snapshot is created.
- The actor may continue to the separate Member purchase journey; browsing does not authorize Guest checkout or protect a stale selection.

#### Normal Flow

**Browse Product Catalogue**

1. The actor opens the Product Catalogue page.
2. The system retrieves products that are published, belong to an active category, and satisfy public-content prerequisites.
3. The actor optionally enters a keyword or applies supported filters.
4. The system validates the keyword and supported category, price and availability filters.
5. The system selects matching products and returns them in the configured display order.
6. The system displays shared name, description/summary, category, usable images, and distinguishable variants with their applied approved prices/currencies and availability. It shows a common-currency price range and marks the product available when any variant is available.
7. The actor selects a configured size/engraving combination to view that variant's identity/options, exact current price/currency, and availability.
8. The actor changes/clears criteria or continues to the separate purchase journey. Subsequent public reads and checkout revalidate current catalogue state.

#### Alternative Flows

**Step 2 — Catalogue retrieval fails**

The system displays a safe retrieval error and permits retry. It does not label the failure as an empty catalogue or reveal protected content.

**Step 4 — Invalid or unsupported criteria**

The system identifies the invalid criterion and permits correction on the catalogue page. This includes negative price bounds, minimum above maximum, unknown/inactive category, or unsupported filters. Resume at Step 3; no product data changes.

**Step 5 — Empty catalogue or no matching products**

The system displays an empty-result message and allows the actor to change or clear criteria at Step 3.

**Steps 5 and 8 — Product unpublished or category inactive since an earlier view**

The system excludes the product from the new public read. A prior view or stale cart does not permit a new hold or bypass checkout revalidation; recorded purchase history is preserved.

**Step 6 — No available variant quantity**

The product remains visible if otherwise eligible, with an unavailable indication. A sold-out variant does not replace or invalidate another variant's price/availability; zero quantity is not a publication blocker.

**Step 6 — Catalogue images become unusable externally**

Loss of all usable images excludes the product from public reads without changing its publication flag/history. Transient media failure receives safe fallback/retry guidance. When usable images return, public eligibility is restored if the product remains published, its category is active, and other prerequisites still hold.

**Step 7 — Unknown combination or free-text engraving**

The system does not create/select an arbitrary variant. The actor may choose only an existing configured public variant and resumes at Step 7.

#### Business Rules

BR-11-01, BR-11-02, BR-11-03, BR-11-04, BR-11-05, BR-11-06, BR-11-07, BR-11-08

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-11-01 | Browsing requires no authentication and returns only published products in active categories that meet public-content requirements. |
| BR-11-02 | Each predefined size/engraving variant has a stable identity, independent approved price and quantity; customers cannot create variants or submit free-text engraving. |
| BR-11-03 | Browsing creates no purchase or hold; drafts, proposed prices and approved-but-unapplied prices remain private. |
| BR-11-04 | Use trimmed, case-insensitive name/description substring search; combine category, inclusive price bounds and availability with AND; sort by name then product identity. |
| BR-11-05 | Show a common-currency price range or one equal price, plus the exact selected-variant price; combined price/availability filters must match the same variant. |
| BR-11-06 | Availability equals each variant’s remaining-unsold quantity minus active holds; any available variant makes the product available. Sold-out variants remain visible without physical-stock guarantees. |
| BR-11-07 | Earlier browsing protects no purchase terms; only accepted valid holds retain frozen prices within their original deadlines after price changes or unpublish. |
| BR-11-08 | Loss of all usable images hides public results without changing publication/history; restore visibility when media and other prerequisites recover. |
