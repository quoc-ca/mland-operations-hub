### 2.1 UC-03 Browse and View Products

#### UC Name

UC-03 Browse and View Products

#### Primary Actors

Guest, Member, Staff, Manager, Admin, Carrier

#### Secondary Actors

Cloud Storage Service (Cloudinary)

#### Description

As a Guest, Member or other signed-in user, I want to search published products and view their prices, availability per size and branch, and reviews so that I can choose a ring to buy.

#### Preconditions

- No sign-in is required to browse or view products.
- The catalogue is available.

#### Normal Flow

***Browse and View Products***

1. The actor opens the product catalogue.
2. The system shows the published products, 20 per page, with image, name, price and stock status.
3. The actor enters a keyword, category, price range, purchasable filter and sort order.
4. The system validates the criteria and lists the matching products.
5. The system shows each card's price after the best automatic discount and voucher (MSG70).
6. The actor opens a product.
7. The system shows the images, description, category, priced sizes, offers and customer reviews.
8. The system lists the active branches that sell the product.
9. The actor selects a size, a branch and a quantity.
10. The system shows the price for the chosen branch and the available quantity.
11. The actor selects Add to cart.
12. The system adds the size, branch and quantity to the Member's cart.
13. The system opens the cart with the added item.

#### Alternative Flows

***Step 2.1 Catalogue Cannot Be Loaded***

1. The system shows a load error with a retry option (MSG05).
2. The use case ends without showing an empty catalogue.

***Step 4.1 Search Criteria Are Invalid***

1. The system marks the invalid criterion, such as a minimum price above the maximum (MSG07).
2. The system sends no search.
3. The use case continues at step 3.

***Step 4.2 No Product Matches the Criteria***

1. The system shows an empty result (MSG06).
2. The use case continues at step 3.

***Step 5.1 Promotions Cannot Be Read***

1. The system shows the normal price without an error.
2. The use case continues at step 6.

***Step 6.1 Actor Uses Quick Add on a Product Card***

1. The actor selects the cart button on a product card.
2. The system opens the Quick add box with sizes, branches and quantity.
3. The actor selects a size, a branch and a quantity, then selects Add to cart.
4. The system adds the item to the cart and keeps the actor on the list (MSG54).
5. The use case ends with the item in the cart.

***Step 7.1 Product Is No Longer Public***

1. The system shows that the product does not exist or is no longer public (MSG138).
2. The system offers a link back to the catalogue.
3. The use case ends.

***Step 7.2 Product Has No Public Review***

1. The system shows five empty stars and no score (MSG59).
2. The use case continues at step 8.

***Step 7.3 Actor Opens the Size Conversion Chart***

1. The system opens the size chart image and measuring guide.
2. The system shows that no chart exists yet if none has been uploaded (MSG58).
3. The use case continues at step 9.

***Step 8.1 Branch List Is Unavailable***

1. The system shows that the branch list is temporarily unavailable (MSG36).
2. The system disables Add to cart and Buy now until a retry succeeds.
3. The use case ends without adding to the cart.

***Step 9.1 Size Cannot Be Bought***

1. The system dims a size that is sold out or not on sale (MSG35).
2. The system prevents the actor from selecting it.
3. The use case continues at step 9.

***Step 11.1 Size or Branch Is Not Selected***

1. The system asks the actor to select a size or a branch (MSG37).
2. The system sends no request.
3. The use case continues at step 9.

***Step 11.2 Guest Selects Add to Cart***

1. The system sends the Guest to sign in.
2. The system returns to the product page with the chosen size, branch and quantity kept.
3. The use case continues at step 11.

***Step 11.3 Signed-in Actor Is Not a Member***

1. The system hides Add to cart and Buy now for Staff, Manager, Admin and Carrier.
2. The use case ends after viewing the product.

***Step 11.4 Member Selects Buy Now***

1. The system adds the item to the cart with the same request as Add to cart.
2. The system opens the checkout with only that cart line.
3. The use case ends.

***Step 12.1 Cart Rejects the Item***

1. The system shows the error returned by the cart, such as a quantity above the branch stock (MSG72).
2. The use case continues at step 9.

#### Postconditions

- The actor has seen only public products, sizes with an applied price and available quantities.
- Displayed prices are for viewing only; checkout recalculates the final amount.
- No order, hold, payment, voucher or point record is created by browsing.

#### Business Rule

BR-21, BR-23, BR-28, BR-30, BR-64, BR-65
