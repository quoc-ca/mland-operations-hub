#### 4.1.2 Product Details

![](assets/screens/09-product-details.png)

This screen allows visitors and Members to:

- View the product's images, category, description and stock state.
- See the price range, or the selected size's price, after automatic discounts and the best voucher; the price is recalculated for the selected branch.
- Review the offers that apply to this product, open a voucher's conditions, and save a voucher that must be saved.
- Select a size, a branch and a quantity in any order, see the available quantity, then add the size to the cart or buy it immediately.
- Open the size conversion chart and read public customer reviews for the product.

Only sizes with an applied price are shown.

| Field Name | Description |
| --- | --- |
| Breadcrumb / name | Read-only path to the product's category and the product name. |
| Image gallery | Product images with thumbnails and alternative text; with two or more images, **Previous image** / **Next image**, arrow keys or a swipe change the large image, with an "n / total" counter. |
| Price | Applied Manager-approved price range, or the selected size's price, after the largest automatic discount with the struck-through original, then the best voucher for one item labelled "Price after voucher {code}" ("· at {branches}" for a voucher limited to some branches). Once a branch is selected, only vouchers usable at that branch are counted. Displayed prices remain indicative until checkout. |
| Offers on this product | Lists automatic discounts and eligible vouchers with their conditions; **View conditions** opens a public voucher's details and **View and save code** opens a voucher that must be saved. |
| Size | Grid of the shown sizes, none selected in advance; a size that is not on sale or has no available quantity at the selected branch (or at any branch when none is selected) is dimmed and cannot be chosen. **Size conversion chart** opens the size guide. |
| Branch | Branches selling the product, not filtered by size; a branch where no size can be bought is shown as "sold out" and cannot be chosen. "Choose a branch" is only a placeholder. |
| Quantity | − / + stepper from 1 to the smaller of the available quantity of the selected size and 99, with "{count} available"; without a size it shows "Choose a size to see how many are left". |
| Add to cart / Buy now | Adds the size to the Member's cart, or adds it and opens checkout with only that cart line; a visitor is asked to sign in. Both buttons are hidden for a signed-in Staff, Manager, Admin or Carrier, because the cart is Member-only. A missing size or branch is reported and nothing is sent. Neither action creates a hold. |
| Customer reviews | Public review score, Newest / Highest rated / Lowest rated sort and star filter for this product; "No reviews yet" when there is none. Only a signed-in Member sees the entry to write a review; other viewers only read. |
