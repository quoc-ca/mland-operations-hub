### 2.1 Browse Product Catalogue

#### Primary Actors

Guest, Member.

#### Secondary Actors

Cloud Storage Service — supplies catalogue media; provider selection is deferred to the plan; approved catalogue-media access and retention rules apply.

#### Description

As a public catalogue visitor, I want to browse published retail ring products, search and combine supported filters, and compare configured variants' prices and availability before a separate purchase journey (UC11).

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
4. The system validates the submitted criteria; search/filter rules follow confirmed A-01/A-09.
5. The system selects matching products and returns them in the configured display order.
6. The system displays shared name, description/summary, category, usable images, and distinguishable variants with their applied approved prices/currencies and availability. Price summaries and product-level availability follow confirmed A-09.
7. The actor selects a configured size/engraving combination to view that variant's identity/options, exact current price/currency, and availability.
8. The actor changes/clears criteria or continues to the separate purchase journey. Subsequent public reads and checkout revalidate current catalogue state.

#### Alternative Flows

**Step 2 — Catalogue retrieval fails**

The system displays a safe retrieval error and permits retry. It does not label the failure as an empty catalogue or reveal protected content.

**Step 4 — Invalid or unsupported criteria**

The system identifies the invalid criterion and permits correction on the catalogue page. Under confirmed A-01, this includes negative price bounds, minimum above maximum, unknown/inactive category, or unsupported filters. Resume at Step 3; no product data changes.

**Step 5 — Empty catalogue or no matching products**

The system displays an empty-result message and allows the actor to change or clear criteria at Step 3.

**Steps 5 and 8 — Product unpublished or category inactive since an earlier view**

The system excludes the product from the new public read. A prior view or stale cart does not permit a new hold or bypass checkout revalidation; recorded purchase history is preserved.

**Step 6 — No available variant quantity**

The product remains visible if otherwise eligible, with an unavailable indication. A sold-out variant does not replace or invalidate another variant's price/availability; zero quantity is not a publication blocker.

**Step 6 — Catalogue images become unusable externally**

Under confirmed A-11, loss of all usable images excludes the product from public reads without changing its publication flag/history. Transient media failure receives safe fallback/retry guidance. When usable images return, public eligibility is restored if the product remains published, its category is active, and other prerequisites still hold.

**Step 7 — Unknown combination or free-text engraving**

The system does not create/select an arbitrary variant. The actor may choose only an existing configured public variant and resumes at Step 7.

#### Business Rules

BR-11-01, BR-11-02, BR-11-03, BR-11-04, BR-11-05, BR-11-06, BR-11-07, BR-11-08

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-11-01 | All five platform roles may browse without authentication. Only published products in active categories satisfying public-content prerequisites are returned; no additional Product-active or Variant-publication state is introduced. Confirmed D-02/D-03. |
| BR-11-02 | Product is the shared parent. Each predefined size/engraving combination has a separate stable variant identity, applied approved price/currency, and quantity. Customers cannot create variants or submit free-text retail engraving. Confirmed D-01/D-07; values are Mland-provided configuration data under D-06, including “Không khắc” (No engraving); the initial list is still required. |
| BR-11-03 | Browsing is read-only and creates no purchase records, holds, or frozen prices. Only applied approved prices are public; proposals or approved-but-unapplied prices and protected drafts remain private. Confirmed D-11. |
| BR-11-04 | Confirmed requirement (A-01): search name/description using trimmed case-insensitive substring matching; combine supported category, inclusive variant price bounds, and availability with AND; order by name then stable product identity. Clearing criteria restores eligible public results. |
| BR-11-05 | Confirmed requirement (A-09): use a common-currency price range, or one price when equal; display the exact selected-variant price. Combined price/availability criteria must match the same variant. Media is shared across variants. Only positive whole-VND sale prices are supported (D-04); no decimal rounding or currency conversion is performed. |
| BR-11-06 | Zero quantity does not prevent public display. Availability is variant-specific without substitution. Confirmed A-02/A-09 require subtracting active holds from that variant's remaining-unsold quantity and treating a product as available if any variant is available; these do not promise physical stock. |
| BR-11-07 | Price changes/unpublish apply immediately to new checkout; earlier browsing provides no purchase protection. Only already accepted valid holds retain frozen terms within original deadlines under D-08; UC11 never creates such protection. |
| BR-11-08 | Confirmed requirement (A-11): externally losing all usable images suppresses public reads without rewriting publication/history. Images are limited to 10 per product, 5 MB each, JPEG/PNG/WebP, with 1–200-character alternative text; public/draft access follows product authority. Approved retention and browser/accessibility/performance targets are defined in the overview (D-04/D-05); provider selection is deferred to the plan. |
