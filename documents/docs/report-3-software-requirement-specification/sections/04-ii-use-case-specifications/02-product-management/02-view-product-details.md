### 2.2 View Product Details

#### Primary Actors

Guest, Member.

#### Secondary Actors

Cloud Storage Service, which serves product images; Workshop Management, which owns the shared branch list; Loyalty and Promotion Management, which supplies active product promotions for display; Review Management, which supplies the public customer reviews of the product.

#### Description

As a catalogue visitor, I want to view a product's images, description, per-size price and available quantity and its customer reviews, and as a Member choose a size, a branch and a quantity, so that I can pass an exact product variant to the shopping cart or go straight to checkout.

This use case includes the following sub-flows:

- View Product Details
- View Customer Reviews

#### Preconditions

1. The platform is available. No sign-in is required to view a product.
2. The actor has opened the product from the catalogue, a saved link or a promotion card.

#### Normal Flow

**View Product Details**

1. The actor opens the product detail page; the page starts at the top.
2. The system checks that the product is still publicly eligible under BR-10-01.
3. The system displays the product name, description, category, images with alt text, the VND price range (or a single price when all shown sizes have the same price) in the main price box, and every size that has an applied price as a grid of cells (5 per row on wide screens) with no size selected in advance. With two or more images, Previous image and Next image buttons, the arrow keys or a swipe change the large image, with an "n / total" counter. When an automatic discount applies, the price after the largest discount is shown with the original price struck through; the price after the best voucher for one item is then shown with the label "Price after voucher {code}", and "· at {branches}" for a voucher limited to some branches (MSG72), with promotion labels, under UC-47 View and Save Vouchers.
4. The system retrieves the active branches from the shared branch list and lists every active branch that sells the product (at least one size of the product is at that branch; a size not yet assigned to any branch counts at every active branch). The list is not filtered by the selected size. A branch where no size can be bought is shown as "sold out" and cannot be selected; "Choose a branch" is only a placeholder, not a selectable entry.
5. The actor selects a size and a branch, in any order. A size cell can be selected when the size is on sale and its available quantity at the selected branch, or at any branch when no branch is selected, is above 0. When the actor changes to a branch that does not have the selected size, the size is cleared.
6. When a size is selected, the main price box shows the price of that size instead of the range; clearing the size shows the range again. When a branch is selected, the price after voucher is recalculated with only the vouchers usable at that branch. Next to the quantity selector the system shows "{count} available" for the selected size; with no size selected it shows "Choose a size to see how many are left".
7. The actor sets the quantity with − and + from 1 to the smaller of the available quantity of the selected size and 99; the quantity is reduced to that limit when the size or branch changes. With no size selected, the quantity stays at 1 and + is disabled.
8. Optionally, the actor selects Size conversion chart under the size grid. The system opens a dialog with a title, a description, the shop's size chart image and a measuring guide by diameter and by circumference, in the language currently selected (Vietnamese or English). The shop can replace this content without a code change.
9. The actor selects Add to cart or Buy Now.
10. The system passes the size variant, branch and quantity to UC-52 Add Product to Cart, which checks sign-in and purchase conditions. For Add to cart, on success the actor is taken to the cart. For Buy now, the same cart request is sent and the system then opens UC-54 Checkout Retail Order with only the cart line just added.

**View Customer Reviews**

1. On the product detail page, the system reads the public reviews of the product from Review Management and shows the Customer Reviews block.
2. The system shows the overall rating of all public reviews of the product, which does not change with the star filter: the actual average rounded to one decimal place, the number of stars for the rounded average and the number of reviews.
3. The actor optionally sorts by Newest, Highest rated or Lowest rated, and filters by 1 to 5 stars; selecting the selected star again, or All, clears the filter.
4. The system lists up to 50 matching reviews, each shown with the review card of Review Management.

#### Alternative Flows

**Step 2 — Product is no longer public**

The system shows that the product is not available and offers a link back to the catalogue. It does not reveal internal product data.

**Step 3 — Product data cannot be loaded**

The system displays a load error with a retry option (MSG05).

**Step 3 — Promotions cannot be loaded**

The page shows the normal price without an error.

**Step 4 — Branch source is unavailable**

The system reports that the branch list is temporarily unavailable (MSG36), disables Add to cart and Buy now and offers a retry, while the product details remain viewable. It does not show an empty branch list as a real result.

**Step 5 — Size cannot be bought**

A size that is stopped from sale or has no available quantity is dimmed, cannot be selected and is labelled "Sold out" or "Not on sale" (MSG35). The actor may choose another size or branch.

**Step 5 — Selection restored after sign-in**

When the actor returns from sign-in, the size, branch and quantity chosen before are selected again.

**Step 8 — Size chart image is missing**

The dialog shows that there is no size chart image yet (MSG59).

**Step 9 — Size or branch not selected**

Add to cart and Buy now stay available, except while the branch source is unavailable or a request is being sent. If no size that can be bought is selected, the system asks the actor to select a size; if no branch is selected, it asks the actor to select a branch (MSG37). No request is sent.

**Step 10 — Guest selects Add to cart or Buy Now**

After a size and a branch are selected, the Guest is sent to sign in and then returned to the product page with the selected size, branch and quantity kept. Product Management creates no cart, order or hold.

**Step 10 — The cart rejects the item**

The system shows the message returned by the cart use case, for example when the available quantity at the branch has just reached 0.

**Step 10 — Buy Now cannot find the new cart line**

If the cart line just added is not found in the cart response, the system opens the cart instead of the checkout.

**Reviews Step 1 — Reviews are loading or cannot be loaded**

The block shows a loading state, or an error with a Retry option (MSG05).

**Reviews Step 2 — The product has no public review**

The block shows "No reviews yet" with 5 empty stars and no rating score (MSG60).

**Reviews Step 4 — No review matches the star filter**

The block shows an empty state that is different from the no-review state and keeps the filter so that the actor can change it.

#### Postconditions

- The actor sees the current public details and public reviews of the product.
- When the Member adds the item or selects Buy now, the selected variant, branch and quantity are handed to the cart; no hold, order, payment, voucher or point record is created by this use case.
- Reviews are only read; Product Management does not store, moderate or edit them.

#### Business Rules

GBR-26, GBR-31, GBR-33, BR-11-01, BR-11-02, BR-11-03, BR-11-04, BR-11-05, BR-11-06

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| GBR-26 | The product page shows the price after the largest automatic discount and then the best voucher for one item, labelled "Price after voucher {code}" ("· at {branch}" for a branch-limited voucher), and the available quantity per size; the price is recalculated for the chosen branch, never replaces the checkout quote, and a failure to read promotions never blocks the page. |
| GBR-31 | Buy now sends the same add-to-cart request as Add to cart and then opens the checkout with only that cart line; Product Management never creates an order or calculates the amount to pay. |
| GBR-33 | After a product's first publication, a size without an applied price is not shown on the page, not counted in the price range and cannot be added to the cart; it appears once its approved price is applied. |
| BR-11-01 | Only sizes configured for the product and with an applied price can be selected; customers cannot create variants or request engraving. |
| BR-11-02 | A size that is stopped from sale stays visible with its label but cannot be added to the cart; an out-of-stock size does not hide the product. |
| BR-11-03 | The Member must choose a size and a branch, in any order, before adding to the cart. The branch list contains the active branches that sell the product and is not filtered by size; a branch where no size can be bought cannot be chosen, and a size can be chosen only when it is on sale and available at the selected branch, or at any branch when none is selected. If the shared branch list cannot be verified, the system returns a temporary error and blocks Add to cart and Buy now instead of showing an empty list. |
| BR-11-04 | A price shown on the page, including a price after an automatic discount, and the available quantity are for display only; the cart and checkout recheck the current price, quantity and eligibility. The available quantity is the remaining quantity minus valid holds; the quantity before holds and held quantities are never shown. |
| BR-11-05 | The quantity is chosen from 1 to the smaller of the available quantity of the selected size and 99. Buy now sends the same cart request as Add to Cart and then opens the checkout with only the new cart line; Product Management never creates an order or calculates the amount to pay. |
| BR-11-06 | The Customer Reviews block reads public reviews from Review Management only. The overall rating covers all public reviews of the product regardless of the star filter; with no review, no score is shown. At most 50 reviews are listed, and all text of the block is available in Vietnamese and English. |
