### 2.2 View Product Details

#### Primary Actors

Guest, Member.

#### Secondary Actors

Cloud Storage Service, which serves product images; Workshop Management, which owns the shared branch list; Loyalty and Promotion Management, which supplies active product promotions for display.

#### Description

As a catalogue visitor, I want to view a product's images, description and per-size price and availability, and as a Member choose a size and branch, so that I can pass an exact product variant to the shopping cart.

#### Preconditions

1. The platform is available. No sign-in is required to view a product.
2. The actor has opened the product from the catalogue, a saved link or a promotion card.

#### Normal Flow

**View Product Details**

1. The actor opens the product detail page.
2. The system checks that the product is still publicly eligible under BR-10-01.
3. The system displays the product name, description, category, images with alt text, and the configured sizes with their applied VND prices and availability labels. When an automatic discount applies to a size, the price after the discount is shown with the original price struck through, with promotion labels, under UC-47 View and Save Vouchers.
4. The system retrieves the active branches from the shared branch list.
5. The actor selects a configured size; the system shows the exact price and availability of that size.
6. The system lists only the active branches that sell the selected size and have it available. A branch that sells the size but has none available may be shown as "Out of stock" and cannot be selected. No internal quantity, held quantity or stopped branch is shown.
7. The actor selects a branch and a quantity, then selects Add to cart.
8. The system passes the size variant, branch and quantity to UC-52 Add Product to Cart, which checks sign-in and purchase conditions. On success, the actor is taken to the cart.

#### Alternative Flows

**Step 2 — Product is no longer public**

The system shows that the product is not available and offers a link back to the catalogue. It does not reveal internal product data.

**Step 3 — Product data cannot be loaded**

The system displays a load error with a retry option (MSG05).

**Step 3 — Promotions cannot be loaded**

The page shows the normal price without an error.

**Step 4 — Branch source is unavailable**

The system reports that the branch list is temporarily unavailable (MSG36), disables Add to cart and offers a retry, while the product details remain viewable. It does not show an empty branch list as a real result.

**Step 5 — Selected size is stopped from sale or out of stock**

The size stays visible with its label but cannot be added to the cart (MSG35). The actor may choose another size.

**Step 6 — No branch has the selected size available**

The size cannot be added to the cart at any branch (MSG35). The actor may choose another size.

**Step 7 — Size or branch not selected**

The system asks the actor to select a size and a branch before adding to the cart (MSG37).

**Step 8 — Guest selects Add to cart**

The Guest is sent to sign in and then returned to the product page with the selected size, branch and quantity kept. Product Management creates no cart, order or hold.

**Step 8 — The cart rejects the item**

The system shows the message returned by the cart use case, for example when the available quantity at the branch has just reached 0.

#### Postconditions

- The actor sees the current public details of the product.
- When the Member adds the item, the selected variant, branch and quantity are handed to the cart; no hold, order, voucher or point record is created by this use case.

#### Business Rules

BR-11-01, BR-11-02, BR-11-03, BR-11-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-11-01 | Only sizes configured for the product can be selected; customers cannot create variants or request engraving. |
| BR-11-02 | A size that is stopped from sale stays visible with its label but cannot be added to the cart; an out-of-stock size does not hide the product. |
| BR-11-03 | The Member must choose a branch before adding to the cart; only active branches that sell the selected size and have it available can be chosen. If the shared branch list cannot be verified, the system returns a temporary error and blocks Add to cart instead of showing an empty list. |
| BR-11-04 | A price shown on the page, including a price after an automatic discount, does not guarantee purchase; the cart and checkout recheck the current price and eligibility. |
