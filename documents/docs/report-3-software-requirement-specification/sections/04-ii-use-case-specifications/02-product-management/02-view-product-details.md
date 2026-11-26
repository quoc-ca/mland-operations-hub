### 2.2 View Product Details

| Use Case ID | UC-11 | Use Case Name | View Product Details |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | Cloud Storage Service, Workshop Management, Loyalty and Promotion Management, Review Management |

#### Description

As a catalogue visitor, I want to view a product's details, per-size prices, branch availability, and reviews so that I can select a variant and add it to cart or buy now.

#### Trigger

A Guest or Member opens a product from the catalogue, a saved link, or a promotion card.

#### Preconditions

1. The platform is available; no sign-in is required to view a product.
2. The actor opened a product link from the catalogue, saved link, or promotion card.

#### Postconditions

1. The actor sees the current public details and public reviews of the product.
2. Selected variant, branch, and quantity are handed to UC-52; no hold, order, payment, voucher, or point record is created here.
3. Reviews are read-only; Product Management does not store, moderate, or edit them.

#### Normal Flow

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

**View Customer Reviews**

1. System loads public reviews for the product from Review Management.
2. System displays the overall average rating, star count, and total review count.
3. Actor sorts reviews by Newest, Highest rated, or Lowest rated, and filters by stars.
4. System displays up to 50 matching reviews using Review Management review cards.

#### Alternative Flows

4a. Promotions cannot be loaded → Page displays the normal price without an error.

6a. Actor opens the Size conversion chart → System opens a dialog with the shop's size chart image and measuring guide in the active language.

6b. Actor returns from sign-in → System restores the previously chosen size, branch, and quantity.

9a. Actor selects Buy now → System sends the same cart request via UC-52 and opens UC-54 Checkout Retail Order with only that line.

9b. Guest selects Add to cart or Buy now → System redirects Guest to sign in and returns to the product page with choices kept.

R2a. Product has no public reviews → System shows "No reviews yet" with 5 empty stars and no rating score (MSG60).

R4a. No review matches the star filter → System shows a distinct empty-filter state while keeping the filter active.

#### Exception Flows

2a. Product is no longer public → System shows an unavailable message and offers a link back to the catalogue.

3a. Product data cannot be loaded → System displays a load error with a retry option (MSG05).

5a. Branch source is unavailable → System reports temporary unavailability (MSG36), disables Add to cart and Buy now, and offers retry.

6c. Size is stopped from sale or out of stock → System dims the size as "Sold out" or "Not on sale" (MSG35) and blocks selection.

6d. Size chart image is missing → Dialog displays that no size chart image is available yet (MSG59).

9c. Size or branch is not selected → System prompts Actor to select a size or branch (MSG37) without sending a request.

10a. Cart rejects the item → System displays the message returned by UC-52.

10b. Buy now cannot find the new cart line → System opens the cart instead of checkout.

R1a. Reviews fail to load → Review block displays a loading error with a Retry option (MSG05).

#### Business Rules

GBR-26, GBR-31, GBR-33, BR-11-01, BR-11-02, BR-11-03, BR-11-04, BR-11-05, BR-11-06

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG05, MSG35, MSG36, MSG37, MSG59, MSG60, MSG72

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| GBR-26 | The product page shows the price after the largest automatic discount and then the best voucher for one item, labelled "Price after voucher {code}" ("· at {branch}" for a branch-limited voucher), and the available quantity per size; the price is recalculated for the chosen branch, never replaces the checkout quote, and a failure to read promotions never blocks the page. |
| GBR-31 | Buy now sends the same add-to-cart request as Add to cart and then opens the checkout with only that cart line; Product Management never creates an order or calculates the amount to pay. |
| GBR-33 | After a product's first publication, a size without an applied price is not shown on the page, not counted in the price range and cannot be added to the cart; it appears once its approved price is applied. |
| BR-11-01 | Only sizes configured for the product and with an applied price can be selected; customers cannot create variants or request engraving. |
| BR-11-02 | A size that is stopped from sale stays visible with its label but cannot be added to the cart; an out-of-stock size does not hide the product. |
| BR-11-03 | The Member must choose a size and a branch, in any order, before adding to the cart. The branch list contains the active branches that sell the product and is not filtered by size; a branch where no size can be bought cannot be chosen, and a size can be chosen only when it is on sale and available at the selected branch, or at any branch when none is selected. If the shared branch list cannot be verified, the system returns a temporary error and blocks Add to cart and Buy now instead of showing an empty list. |
| BR-11-04 | A price shown on the page, including a price after an automatic discount, and the available quantity are for display only; the cart and checkout recheck the current price, quantity and eligibility. The available quantity is the remaining quantity minus valid holds; the quantity before holds and held quantities are never shown. |
| BR-11-05 | The quantity is chosen from 1 to the smaller of the available quantity of the selected size and 99. Buy now sends the same cart request as Add to Cart and then opens the checkout with only the new cart line; Product Management never creates an order or calculates the amount to pay. |
| BR-11-06 | The Customer Reviews block reads public reviews from Review Management only. The overall rating covers all public reviews of the product regardless of the star filter; with no review, no score is shown. At most 50 reviews are listed, and all text of the block is available in Vietnamese and English. |
