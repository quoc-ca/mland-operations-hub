### 8.1 UC-21 Update Shopping Cart

#### UC Name

UC-21 Update Shopping Cart

#### Primary Actors

Member

#### Secondary Actors

None

#### Description

As a Member, I want to add a product size from a chosen branch to my cart and adjust its lines so that I can decide what to buy.

#### Preconditions

- The Member is signed in with an active account.
- The product is published and the size is on sale at the chosen branch.

#### Normal Flow

***Update Shopping Cart***

1. The Member opens a product's details page.
2. The Member selects a size, a branch and a quantity.
3. The Member selects Thêm vào giỏ hàng.
4. The system checks that the size is on sale and available at that branch.
5. The system adds the line to the cart with its branch and quantity.
6. The system opens the cart with the lines grouped by branch and none ticked.
7. The Member changes the quantity of a line.
8. The system checks the new quantity against the limits and the branch stock.
9. The system saves the quantity and recalculates the indicative prices of the ticked lines.
10. The system confirms the cart has been updated by showing the current lines and totals.

#### Alternative Flows

***Step 2.1 Size or Branch Is Not Chosen***

1. The system informs the Member that a size or a branch must be chosen (MSG37).
2. The system sends no request.
3. The use case continues at step 2.

***Step 3.1 Member Selects Mua Ngay***

1. The system adds the line to the cart as in steps 4 and 5.
2. The system opens the checkout with only that line ticked.
3. The use case continues in UC-22 Checkout Retail Order.

***Step 3.2 Member Is Not Signed In***

1. The system opens the sign-in page with a return link to the product.
2. The use case ends without changing any cart.

***Step 4.1 Size Is No Longer Sold or Stock Is Short***

1. The system refuses the line and shows the quantity left at the branch (MSG72).
2. The use case continues at step 2.

***Step 5.1 Same Size and Branch Already in the Cart***

1. The system adds the quantity to the existing line instead of creating a new line.
2. The use case continues at step 6.

***Step 5.2 Cart Limit Is Reached***

1. The system refuses the line because the cart already has 50 lines or the size would exceed 99 units (MSG285).
2. The use case ends without changing the cart.

***Step 7.1 Member Removes a Line***

1. The Member selects Xóa on a line, or on all ticked lines.
2. The system removes the lines from the cart.
3. The use case continues at step 10.

***Step 7.2 Member Ticks Lines***

1. The Member ticks one line, all lines of a branch or every line.
2. The system previews the best automatic discount, voucher and points for the ticked lines.
3. The use case continues at step 10.

***Step 8.1 Quantity Is Invalid***

1. The system shows that the quantity must be a whole number from 1 to 99 within the branch stock (MSG72).
2. The system restores the saved quantity of the line.
3. The use case continues at step 7.

***Step 9.1 Cart Cannot Be Saved***

1. The system informs the Member that the result could not be confirmed (MSG04).
2. The system keeps the last saved cart.
3. The use case continues at step 7.

#### Postconditions

- The cart holds the added, changed or removed lines, each with its own branch.
- No stock, voucher or points are held by the cart.
- Prices in the cart remain indicative until checkout.

#### Business Rule

BR-21, BR-28, BR-33, BR-36, BR-153, BR-154
