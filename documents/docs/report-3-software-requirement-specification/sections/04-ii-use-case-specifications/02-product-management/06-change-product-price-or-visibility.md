### 2.6 UC-45 Change Product Price or Visibility

#### UC Name

UC-45 Change Product Price or Visibility

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to change a size's selling price directly and hide or show a whole product so that the catalogue reflects current business decisions.

#### Preconditions

- The actor is signed in with an active Manager account.
- The product has been published before and is not deleted.

#### Normal Flow

***Change Price of a Size***

1. The Manager opens the product and its Price proposals section.
2. The system shows each size with its applied price and price history.
3. The Manager selects Change price on a size.
4. The Manager enters the new selling price and keeps Apply the price right after approving ticked.
5. The Manager selects Save.
6. The system validates the amount.
7. The system creates and submits a price proposal for the size as separate audited commands.
8. The system approves the proposal at the entered amount as a separate audited command.
9. The system applies the approved price and marks the previous applied price as superseded.
10. The system confirms that the new selling price is in effect (MSG24).

#### Alternative Flows

***Step 1.1 Manager Hides a Published Product***

1. The Manager selects Hide on a published product.
2. The system unpublishes the product and records an audit entry.
3. The system removes the product from public reads and new holds, keeping sizes, prices and history.
4. The system confirms that the product is hidden (MSG26).
5. The use case ends.

***Step 1.2 Manager Shows a Hidden Product Again***

1. The Manager selects Show again on a hidden product.
2. The system checks the active category, content, usable image and at least one size with an applied price.
3. The system publishes the product without a new content review and records an audit entry.
4. The system keeps sizes without an applied price hidden from customers.
5. The system confirms that the product is published (MSG26).
6. The use case ends.

***Step 1.3 Product Is Already in the Requested State***

1. The system makes no second state change and shows the current state.
2. The use case ends.

***Step 1.4 Showing Again Condition Is Not Met***

1. The system keeps the product hidden and names each unmet condition (MSG25).
2. The use case ends.

***Step 3.1 Size Has a Pending Proposal***

1. The system hides Change price for that size.
2. The Manager decides the pending proposal instead (UC-44).
3. The use case ends.

***Step 4.1 Manager Applies the Price Later***

1. The Manager clears Apply the price right after approving.
2. The system stops after the approval and keeps the current applied price (MSG22).
3. The use case ends.

***Step 6.1 Amount Is Invalid***

1. The system marks an amount that is zero, negative or not a whole number (MSG08).
2. The use case continues at step 4.

***Step 7.1 Actor Is Not a Manager***

1. The system rejects hiding, showing again or approving by a Staff member (MSG01).
2. The use case ends without a change.

***Step 7.2 Product Was Deleted or Changed Meanwhile***

1. The system rejects a change to a deleted product (MSG17).
2. The system rejects a change based on an older version and offers to reload (MSG03).
3. The use case ends without a change.

***Step 9.1 A Change Price Step Fails***

1. The system keeps the steps finished before the error.
2. The system reloads the product and reports the unfinished change (MSG66).
3. The Manager carries on from the current state with the submit, approve or apply button.
4. The use case ends.

#### Postconditions

- The new price applies only to new views, carts and checkouts.
- Valid holds and accepted orders keep their price and deadline.
- A hidden product leaves public reads but stays in management with its history.
- Every step is recorded as its own audited command.

#### Business Rule

BR-11, BR-13, BR-30, BR-67, BR-68, BR-69, BR-73, BR-79
