### 2.1 Browse Product Catalogue

#### Primary Actors

Guest, Member.

#### Secondary Actors

Cloud Storage Service, which serves product images; Loyalty and Promotion Management, which supplies active product promotions for display.

#### Description

As a catalogue visitor, I want to search, filter and sort published ring products so that I can find products and compare their current prices and availability before choosing one to view or buy, or add one to the cart directly from the list.

This use case includes the following sub-flows:

- Browse Product Catalogue
- Quick Add to Cart

#### Preconditions

1. The platform is available. No sign-in is required.
2. The catalogue may contain zero or more public products; an empty catalogue does not prevent browsing.

#### Normal Flow

**Browse Product Catalogue**

1. The actor opens the Product Catalogue page.
2. The system retrieves products that are published, not deleted, belong to an active category, have at least one usable image and have at least one size with an applied approved price. Sizes without an applied price are not shown and are not counted in prices, filters or sorting.
3. The actor optionally enters a keyword, selects a category, enters a price range, selects Only products you can buy, and chooses a sort order.
4. The system trims the keyword and validates the criteria.
5. The system matches the keyword as a case-insensitive substring of the product name or description and combines all criteria with AND. When a price range and Only products you can buy are both used, the same size must satisfy both.
6. The system sorts the results by name A–Z then product identity by default, or by lowest price, highest price or newest, and returns 20 products per page.
7. The system displays each product on a product card with its image, name, VND price range (or a single price when all displayed sizes have the same price), an in-stock or out-of-stock label and a cart button. When an automatic discount applies, the card shows the price after the largest discount with the original price struck through; when a voucher lowers the price of one item further, the card shows the price after the best voucher labelled "Price after voucher {code}", with "· at {branches}" for a voucher limited to some branches (MSG72), and promotion labels, under UC-47 View and Save Vouchers.
8. The actor changes the page, changes or clears the criteria while keeping the others, selects a product to continue to UC-11 View Product Details, or selects the cart button.

**Quick Add to Cart**

1. The actor selects the cart button on a product card.
2. The system opens a Quick add box on the list with the same buy panel and rules as UC-11 View Product Details: the size grid, the branch list, the quantity selector with the available quantity, and the Add to cart and Buy now buttons. The price of the selected size is shown under the size grid.
3. The actor selects a size, a branch and a quantity, in any order, and selects Add to cart.
4. The system passes the size variant, branch and quantity to UC-52 Add Product to Cart.
5. On success, the system closes the box and shows that the item was added, with a link to the cart (MSG55); the actor stays on the list. If the actor selects Buy now instead, the item is added in the same way and the checkout opens as in UC-11.

#### Alternative Flows

**Step 2 — Catalogue retrieval fails**

The system displays a load error with a retry option (MSG05). It does not present the failure as an empty catalogue.

**Step 2 — The list of active branches cannot be read**

The catalogue still loads. Branches that have stock rows on sale are treated as active when availability and Only products you can buy are calculated, so a branch that has just been deactivated may still be counted; the cart and checkout check again.

**Step 4 — Invalid or unsupported criteria**

The system rejects a keyword longer than 200 characters, a negative price, a minimum price above the maximum, an unknown or inactive category or an unsupported criterion, identifies the invalid criterion (MSG07) and resumes at Step 3.

**Step 5 — No matching products**

The system displays an empty-result message (MSG06) and allows the actor to change or clear the criteria at Step 3.

**Step 7 — All sizes are out of stock**

The product stays visible with an unavailable indication unless Only products you can buy is selected. Zero quantity does not unpublish the product.

**Step 7 — Image cannot be loaded**

The system shows a safe image fallback with retry guidance. If a product loses all usable images, it is excluded from public reads without changing its publication flag, and it reappears when a usable image and the other conditions return.

**Step 7 — Promotions cannot be loaded**

The card shows the normal price without an error.

**Step 8 — Product hidden, deleted or category deactivated since an earlier view**

The system excludes the product from the next public read. An earlier page view does not grant the right to buy.

**Quick Add Step 3 — Guest selects Add to cart or Buy now**

After a size and a branch are chosen, the system sends the Guest to sign in and then returns to the product detail page with the choices kept.

**Quick Add Step 2 — Branch source is unavailable**

The system disables Add to cart and Buy now and offers a retry (MSG36).

**Quick Add Step 3 — Size cannot be bought**

A size that is stopped from sale, or has no available quantity at the selected branch (or at any branch when no branch is selected), is dimmed and cannot be selected (MSG35). A product that cannot be bought at any branch cannot be added.

**Quick Add Step 3 — Size or branch not selected**

The system asks the actor to select a size or a branch (MSG37) and sends no request.

**Quick Add Step 4 — The cart rejects the item**

The system shows the message returned by the cart use case and keeps the box open.

#### Postconditions

- The actor sees matching public products, or an empty or error state with recovery guidance.
- Drafts, review records, proposed prices, approved-but-unapplied prices, quantities before holds and held quantities are not exposed.
- Browsing creates no cart, order, payment, hold, voucher or point transaction; Quick add only hands the item to the cart use case.
- A price shown on a card is for viewing only; the amount paid is calculated again at checkout.

#### Business Rules

GBR-26, GBR-31, GBR-33, BR-10-01, BR-10-02, BR-10-03, BR-10-04, BR-10-05, BR-10-06, BR-10-07, BR-10-08

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| GBR-26 | Product cards show the price after the largest automatic discount and then the best voucher for one item, labelled "Price after voucher {code}" ("· at {branch}" for a branch-limited voucher); the display never replaces the checkout quote, and a failure to read promotions never blocks the page. |
| GBR-31 | Buy now sends the same add-to-cart request as Add to cart and then opens the checkout with only that cart line. |
| GBR-33 | After a product's first publication, a size without an applied price is not shown publicly, not counted in public prices or filters and cannot be added to the cart; the product stays public. |
| BR-10-01 | Browsing requires no sign-in and returns only products that are published, not deleted, in an active category, with at least one usable image and at least one size with an applied approved price; only sizes with an applied price are returned. Eligibility is checked on every read. |
| BR-10-02 | The keyword is trimmed, limited to 200 characters and matched as a case-insensitive substring of the name or description; keyword, category, inclusive price bounds and Only products you can buy are combined with AND. |
| BR-10-03 | A product is purchasable when at least one size can be bought at one or more branches: the size is on sale and on sale at that branch, the branch is active, the available quantity there is above 0 and all publication conditions are met. A combined price and purchasable filter must be satisfied by the same size. |
| BR-10-04 | The default sort is name A–Z then product identity; price sorts use the lowest price among the sizes that are displayed or match the filters; newest uses the creation time, not price changes or republishing. |
| BR-10-05 | Results are paged at 20 products per page and the current keyword and filters are kept while paging or sorting. |
| BR-10-06 | Browsing creates no cart, order, payment, hold, voucher or point record and never exposes internal review or pricing data, quantities before holds, held quantities or branches where sale is stopped. Only the available quantity is returned, for display only. |
| BR-10-07 | Quick add uses the same buy panel, size, branch and quantity rules and the same cart request as UC-11; the product card is shared with other pages, such as the home page, and shows promotion prices only for viewing. |
| BR-10-08 | If the list of active branches cannot be read, public reads do not fail: branches with stock rows on sale are treated as active, and the cart and checkout recheck availability. |
