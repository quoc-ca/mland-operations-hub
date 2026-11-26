### 2.1 Browse and View Products

| Use Case ID | UC-10 | Use Case Name | Browse and View Products |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | Cloud Storage Service, Workshop Management, Loyalty and Promotion Management, Review Management |

#### Description

As a catalogue visitor, I want to find published products and view their details, prices, availability and reviews so that I can choose a size and add it to my cart.

#### Trigger

A Guest or Member opens the Product Catalogue page or a product link.

#### Preconditions

1. The platform is available; no sign-in is required to browse or view products.
2. The catalogue contains zero or more public products.

#### Postconditions

1. The actor sees matching public products or one product's public details and reviews, an empty state, or error guidance.
2. Drafts, review records, unapplied prices, and held quantities are never exposed.
3. The selected size, branch, and quantity are handed to UC-52; no hold, order, payment, voucher, or point record is created here.
4. Displayed prices are for viewing only; checkout recalculates the final amount.
5. Reviews are read-only; Product Management does not store, moderate, or edit them.

#### Normal Flow

**Browse Product Catalogue**

1. Actor opens the Product Catalogue page.
2. System retrieves eligible published products with an active category, usable image, and applied price.
3. Actor enters search keywords, category, price range, purchasable filter, and sort order.
4. System trims the keyword and validates all search criteria.
5. System matches criteria with AND logic and sorts results by default or chosen order.
6. System displays 20 product cards per page with image, name, price, stock status, and cart button.
7. System shows discounted prices and voucher labels (MSG72) on cards where promotions apply.
8. Actor pages through results, updates criteria, opens a product, or selects the cart button.

**View Product Details**

1. Actor opens the product detail page at the top.
2. System verifies the product is publicly eligible under BR-10-01.
3. System displays product name, description, category, images, price range, and a grid of priced sizes.
4. System shows automatic discount prices and best voucher price with branch labels (MSG72).
5. System retrieves and lists active branches selling the product from Workshop Management.
6. Actor selects a purchasable size and an active branch in any order.
7. System updates the price box, branch voucher price, and available quantity for the selection.
8. Actor sets the quantity between 1 and the smaller of available quantity and 99.
9. Actor selects Add to cart.
10. System sends the size, branch, and quantity to UC-52 Add Product to Cart and opens the cart.

**Quick Add to Cart**

1. Actor selects the cart button on a product card.
2. System opens a Quick add box with size grid, branch list, quantity selector, and action buttons.
3. Actor selects a size, branch, and quantity, then selects Add to cart.
4. System passes the size variant, branch, and quantity to UC-52 Add Product to Cart.
5. System closes the box and displays a cart confirmation with a link (MSG55).

**View Customer Reviews**

1. System loads public reviews for the product from Review Management.
2. System displays the overall average rating, star count, and total review count.
3. Actor sorts reviews by Newest, Highest rated, or Lowest rated, and filters by stars.
4. System displays up to 50 matching reviews using Review Management review cards.

#### Alternative Flows

2a. Active branch list cannot be read → Catalogue loads; branches with stock rows on sale count as active until cart/checkout rechecks.

5a. No matching products found → System displays an empty-result message (MSG06) and lets Actor adjust criteria.

7a. All sizes are out of stock → Product stays visible as unavailable unless the purchasable filter is active.

7b. Promotions cannot be loaded → Card displays the normal price without an error.

D4a. Promotions cannot be loaded → Page displays the normal price without an error.

D6a. Actor opens the Size conversion chart → System opens a dialog with the shop's size chart image and measuring guide in the active language.

D6b. Actor returns from sign-in → System restores the previously chosen size, branch, and quantity.

D9a. Actor selects Buy now → System sends the same cart request via UC-52 and opens UC-54 Checkout Retail Order with only that line.

D9b. Guest selects Add to cart or Buy now → System redirects Guest to sign in and returns to the product page with choices kept.

D9c. Signed-in Staff, Manager, Admin or Carrier views the page → System shows size, branch and quantity but hides Add to cart and Buy now.

Q2b. Signed-in Staff, Manager, Admin or Carrier opens Quick add → System shows size, branch and quantity but hides Add to cart and Buy now.

Q3a. Actor selects Buy now in Quick add → System adds the item via UC-52 and opens checkout as on the product page.

Q3b. Guest selects Add to cart or Buy now → System redirects Guest to sign in and returns to the product page with choices kept.

R2a. Product has no public reviews → System shows "No reviews yet" with 5 empty stars and no rating score (MSG60).

R2b. Viewer is a Guest or a signed-in non-Member role → System shows the public reviews without a Write a review button and without asking Review Management for the viewer's reviewable purchases.

R4a. No review matches the star filter → System shows a distinct empty-filter state while keeping the filter active.

#### Exception Flows

2b. Catalogue retrieval fails → System shows a load error with retry (MSG05) rather than an empty catalogue.

4a. Search or filter criteria are invalid → System rejects invalid values, identifies the criterion (MSG07), and returns to Step 3.

7c. Product image fails to load → System shows a fallback image; products losing all usable images are excluded from public reads.

8a. Product is hidden, deleted, or category deactivated → System excludes the product from the next public read.

D2a. Product is no longer public → System shows an unavailable message and offers a link back to the catalogue.

D3a. Product data cannot be loaded → System displays a load error with a retry option (MSG05).

D5a. Branch source is unavailable → System reports temporary unavailability (MSG36), disables Add to cart and Buy now, and offers retry.

D6c. Size is stopped from sale or out of stock → System dims the size as "Sold out" or "Not on sale" (MSG35) and blocks selection.

D6d. Size chart image is missing → Dialog displays that no size chart image is available yet (MSG59).

D9d. Size or branch is not selected → System prompts Actor to select a size or branch (MSG37) without sending a request.

D10a. Cart rejects the item → System displays the message returned by UC-52.

D10b. Buy now cannot find the new cart line → System opens the cart instead of checkout.

Q2a. Branch source is unavailable → System disables Add to cart and Buy now and offers a retry (MSG36).

Q3c. Size is stopped from sale or out of stock → System dims the size and prevents selection (MSG35).

Q3d. Size or branch is not selected → System prompts Actor to select a size or branch (MSG37) without sending a request.

Q4a. Cart rejects the item → System displays the cart error message and keeps the Quick add box open.

R1a. Reviews fail to load → Review block displays a loading error with a Retry option (MSG05).

#### Business Rules

GBR-26, GBR-31, GBR-33, BR-10-01, BR-10-02, BR-10-03, BR-10-04, BR-10-05, BR-10-06, BR-10-07, BR-10-08, BR-10-09, BR-10-10, BR-10-11, BR-10-12, BR-10-13, BR-10-14

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG05, MSG06, MSG07, MSG35, MSG36, MSG37, MSG55, MSG59, MSG60, MSG72

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| GBR-26 | Product cards and the product page show the price after the largest automatic discount and then the best voucher for one item, labelled "Price after voucher {code}" ("· at {branch}" for a branch-limited voucher). The product page also shows the available quantity per size and recalculates the price for the chosen branch. The display never replaces the checkout quote, and a failure to read promotions never blocks the page. |
| GBR-31 | Buy now sends the same add-to-cart request as Add to cart and then opens the checkout with only that cart line; Product Management never creates an order or calculates the amount to pay. |
| GBR-33 | After a product's first publication, a size without an applied price is not shown publicly, not counted in public prices or filters and cannot be added to the cart; the product stays public and the size appears once its approved price is applied. |
| BR-10-01 | Browsing requires no sign-in and returns only products that are published, not deleted, in an active category, with at least one usable image and at least one size with an applied approved price; only sizes with an applied price are returned. Eligibility is checked on every read. |
| BR-10-02 | The keyword is trimmed, limited to 200 characters and matched as a case-insensitive substring of the name or description; keyword, category, inclusive price bounds and Only products you can buy are combined with AND. |
| BR-10-03 | A product is purchasable when at least one size can be bought at one or more branches: the size is on sale and on sale at that branch, the branch is active, the available quantity there is above 0 and all publication conditions are met. A combined price and purchasable filter must be satisfied by the same size. |
| BR-10-04 | The default sort is name A–Z then product identity; price sorts use the lowest price among the sizes that are displayed or match the filters; newest uses the creation time, not price changes or republishing. |
| BR-10-05 | Results are paged at 20 products per page and the current keyword and filters are kept while paging or sorting. |
| BR-10-06 | Browsing creates no cart, order, payment, hold, voucher or point record and never exposes internal review or pricing data, quantities before holds, held quantities or branches where sale is stopped. Only the available quantity is returned, for display only. |
| BR-10-07 | Quick add uses the same buy panel, size, branch and quantity rules and the same cart request as the product page. Its main price shows the selected size's price, priced for the selected branch as on the product page, and returns to the product price when no size is selected. The product card is shared with other pages, such as the home page, and shows promotion prices only for viewing. |
| BR-10-08 | If the list of active branches cannot be read, public reads do not fail: branches with stock rows on sale are treated as active, and the cart and checkout recheck availability. |
| BR-10-09 | Only sizes configured for the product and with an applied price can be selected; customers cannot create variants or request engraving. |
| BR-10-10 | A size that is stopped from sale stays visible with its label but cannot be added to the cart; an out-of-stock size does not hide the product. |
| BR-10-11 | The Member must choose a size and a branch, in any order, before adding to the cart. The branch list contains the active branches that sell the product and is not filtered by size; a branch where no size can be bought cannot be chosen, and a size can be chosen only when it is on sale and available at the selected branch, or at any branch when none is selected. If the shared branch list cannot be verified, the system returns a temporary error and blocks Add to cart and Buy now instead of showing an empty list. |
| BR-10-12 | A price shown on the page, including a price after an automatic discount, and the available quantity are for display only; the cart and checkout recheck the current price, quantity and eligibility. The available quantity is the remaining quantity minus valid holds; the quantity before holds and held quantities are never shown. |
| BR-10-13 | The quantity is chosen from 1 to the smaller of the available quantity of the selected size and 99. |
| BR-10-14 | The Customer Reviews block reads public reviews from Review Management only. The overall rating covers all public reviews of the product regardless of the star filter; with no review, no score is shown. At most 50 reviews are listed, and all text of the block is available in Vietnamese and English. |
