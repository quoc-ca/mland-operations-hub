### 2.1 Browse Product Catalogue

#### Primary Actors

Guest, Member.

#### Secondary Actors

Cloud Storage Service, which serves product images.

#### Description

As a catalogue visitor, I want to search, filter and sort published ring products so that I can find products and compare their current prices and availability before choosing one to view or buy.

#### Preconditions

1. The platform is available. No sign-in is required.
2. The catalogue may contain zero or more public products; an empty catalogue does not prevent browsing.

#### Normal Flow

**Browse Product Catalogue**

1. The actor opens the Product Catalogue page.
2. The system retrieves products that are published, not deleted, belong to an active category, have at least one usable image and have an applied approved price for every size.
3. The actor optionally enters a keyword, selects a category, enters a price range, selects Purchasable only, and chooses a sort order.
4. The system trims the keyword and validates the criteria.
5. The system matches the keyword as a case-insensitive substring of the product name or description and combines all criteria with AND. When a price range and Purchasable only are both used, the same size must satisfy both.
6. The system sorts the results by name A–Z then product identity by default, or by lowest price, highest price or newest, and returns 20 products per page.
7. The system displays each product's name, summary, category, image and VND price range, or a single price when all displayed sizes have the same price.
8. The actor changes the page, changes or clears the criteria while keeping the others, or selects a product to continue to UC-11 View Product Details.

#### Alternative Flows

**Step 2 — Catalogue retrieval fails**

The system displays a load error with a retry option (MSG05). It does not present the failure as an empty catalogue.

**Step 4 — Invalid or unsupported criteria**

The system rejects a keyword longer than 200 characters, a negative price, a minimum price above the maximum, an unknown or inactive category or an unsupported criterion, identifies the invalid criterion (MSG07) and resumes at Step 3.

**Step 5 — No matching products**

The system displays an empty-result message (MSG06) and allows the actor to change or clear the criteria at Step 3.

**Step 7 — All sizes are out of stock**

The product stays visible with an unavailable indication unless Purchasable only is selected. Zero quantity does not unpublish the product.

**Step 7 — Image cannot be loaded**

The system shows a safe image fallback with retry guidance. If a product loses all usable images, it is excluded from public reads without changing its publication flag, and it reappears when a usable image and the other conditions return.

**Step 8 — Product hidden, deleted or category deactivated since an earlier view**

The system excludes the product from the next public read. An earlier page view does not grant the right to buy.

#### Postconditions

- The actor sees matching public products, or an empty or error state with recovery guidance.
- Drafts, review records, proposed prices, approved-but-unapplied prices and hold data are not exposed.
- No cart, order, payment, hold, voucher or point transaction is created.

#### Business Rules

BR-10-01, BR-10-02, BR-10-03, BR-10-04, BR-10-05, BR-10-06

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-10-01 | Browsing requires no sign-in and returns only products that are published, not deleted, in an active category, with at least one usable image and an applied approved price for every size. Eligibility is checked on every read. |
| BR-10-02 | The keyword is trimmed, limited to 200 characters and matched as a case-insensitive substring of the name or description; keyword, category, inclusive price bounds and Purchasable only are combined with AND. |
| BR-10-03 | A product is purchasable when at least one size is on sale, not held beyond its quantity, and meets all publication conditions; a combined price and purchasable filter must be satisfied by the same size. |
| BR-10-04 | The default sort is name A–Z then product identity; price sorts use the lowest price among the sizes that are displayed or match the filters; newest uses the creation time, not price changes or republishing. |
| BR-10-05 | Results are paged at 20 products per page and the current keyword and filters are kept while paging or sorting. |
| BR-10-06 | Browsing creates no cart, order, payment, hold, voucher or point record and never exposes internal review or pricing data. |
