### 2.1 Browse Product Catalogue

| Use Case ID | UC-10 | Use Case Name | Browse Product Catalogue |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | Cloud Storage Service, Loyalty and Promotion Management |

#### Description

As a catalogue visitor, I want to search, filter, and sort published products so that I can compare prices and availability or quick-add items to my cart.

#### Trigger

A Guest or Member opens the Product Catalogue page.

#### Preconditions

1. The platform is available; no sign-in is required.
2. The catalogue contains zero or more public products.

#### Postconditions

1. The actor sees matching public products, an empty state, or error guidance.
2. Drafts, review records, unapplied prices, and held quantities are never exposed.
3. Browsing creates no cart, order, payment, hold, voucher, or point transaction.
4. Displayed card prices are for viewing only; checkout recalculates the final amount.

#### Normal Flow

**Browse Product Catalogue**

1. Actor opens the Product Catalogue page.
2. System retrieves eligible published products with an active category, usable image, and applied price.
3. Actor enters search keywords, category, price range, purchasable filter, and sort order.
4. System trims the keyword and validates all search criteria.
5. System matches criteria with AND logic and sorts results by default or chosen order.
6. System displays 20 product cards per page with image, name, price, stock status, and cart button.
7. System shows discounted prices and voucher labels (MSG72) on cards where promotions apply.
8. Actor pages through results, updates criteria, opens a product (UC-11), or selects the cart button.

**Quick Add to Cart**

1. Actor selects the cart button on a product card.
2. System opens a Quick add box with size grid, branch list, quantity selector, and action buttons.
3. Actor selects a size, branch, and quantity, then selects Add to cart.
4. System passes the size variant, branch, and quantity to UC-52 Add Product to Cart.
5. System closes the box and displays a cart confirmation with a link (MSG55).

#### Alternative Flows

2a. Active branch list cannot be read → Catalogue loads; branches with stock rows on sale count as active until cart/checkout rechecks.

5a. No matching products found → System displays an empty-result message (MSG06) and lets Actor adjust criteria.

7a. All sizes are out of stock → Product stays visible as unavailable unless the purchasable filter is active.

7b. Promotions cannot be loaded → Card displays the normal price without an error.

Q3a. Actor selects Buy now in Quick add → System adds the item via UC-52 and opens checkout as in UC-11.

Q3b. Guest selects Add to cart or Buy now → System redirects Guest to sign in and returns to the product page with choices kept.

#### Exception Flows

2b. Catalogue retrieval fails → System shows a load error with retry (MSG05) rather than an empty catalogue.

4a. Search or filter criteria are invalid → System rejects invalid values, identifies the criterion (MSG07), and returns to Step 3.

7c. Product image fails to load → System shows a fallback image; products losing all usable images are excluded from public reads.

8a. Product is hidden, deleted, or category deactivated → System excludes the product from the next public read.

Q2a. Branch source is unavailable → System disables Add to cart and Buy now and offers a retry (MSG36).

Q3c. Size is stopped from sale or out of stock → System dims the size and prevents selection (MSG35).

Q3d. Size or branch is not selected → System prompts Actor to select a size or branch (MSG37) without sending a request.

Q4a. Cart rejects the item → System displays the cart error message and keeps the Quick add box open.

#### Business Rules

GBR-26, GBR-31, GBR-33, BR-10-01, BR-10-02, BR-10-03, BR-10-04, BR-10-05, BR-10-06, BR-10-07, BR-10-08

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG05, MSG06, MSG07, MSG35, MSG36, MSG37, MSG55, MSG72

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
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
