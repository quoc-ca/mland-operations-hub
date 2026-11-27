#### 3.1.1 Product Catalogue

![](assets/screens/live/08-product-catalogue.png)

This screen allows visitors and Members to:

- Browse published products in active categories as a card grid with a total result count.
- Search by product name or description and filter by category, inclusive price bounds and products that can be bought; filters are applied together with **Apply** and kept when the page is shared or reopened.
- Sort by default order, name A–Z, ascending or descending price, or newest.
- See on each card the price after automatic discounts and the best voucher, with the offer badges, without the card becoming a quote.
- Add a size to the cart through the Quick add box, or open the product's details.

The page title is **Explore the collection**. Only sizes with an applied price are shown and counted in the price, the price filter and the price sort.

**Field Description**

| Field Name | Description |
| --- | --- |
| Search | Text input ("Product name", up to 200 characters), empty by default, matched against product names and descriptions; empty input lists all public products. |
| Category | Options: All, then each active category. Default: All. |
| Sort by | Options: Default, Name A–Z, Price: low to high, Price: high to low, Newest. Default: Default. |
| Price from (₫) / Price to (₫) | Optional whole-VND bounds, empty by default; price and availability must match the same size. A negative, non-numeric or reversed range shows "A price is a whole number of 0 or more, and the minimum may not be above the maximum." |
| Only products you can buy | Checkbox, unticked by default, restricting results to products with a size that can be bought at one or more branches. |
| Apply | Applies all the criteria above together and returns to the first page. |
| Result count | "{count} products"; "No product matches these criteria." when nothing is found. |
| Added notice | After a quick add: "\"{name}\" was added to your cart." with **View cart**. |
| Product card | Image, category, name, price or price range after the largest automatic discount with the original price struck through, then the best voucher for one item labelled "Price after voucher {code}" ("· at {branches}" for a voucher limited to some branches), offer badges and In stock / Out of stock state. The image and name open Product Details. The price is for viewing only. |
| Add to cart icon | Opens the Quick add box with the same size grid, branch list, quantity selector, **Add to cart** and **Buy now** as Product Details; it does not create an order or hold. Its main price switches to the selected size's price, with discounts and vouchers for the selected branch as on Product Details, or as on the card when no branch is selected; clearing the size restores the product price. It is disabled when the product is out of stock. |
| Pager | **Previous** / **Next** with "Page {page}/{pages}". |
