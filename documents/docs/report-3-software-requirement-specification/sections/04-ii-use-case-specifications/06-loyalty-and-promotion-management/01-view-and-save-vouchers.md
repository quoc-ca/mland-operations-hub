### 6.1 UC-05 View and Save Vouchers

#### UC Name

UC-05 View and Save Vouchers

#### Primary Actors

Guest, Member, Staff, Manager, Admin, Carrier

#### Secondary Actors

None

#### Description

As any visitor, I want to see voucher cards, their conditions and discounted prices so that, as a Member, I can save vouchers to use later.

#### Preconditions

- At least one voucher is running and has a published product or package in scope.
- Saving a voucher requires a signed-in Member with an active account.

#### Normal Flow

***View and Save Vouchers***

1. The actor opens the Voucher hub.
2. The system lists the running vouchers that must be saved, as voucher cards.
3. The system shows each card's scope, discount, cap, minimum amount, branches, expiry, point rule and code (MSG68).
4. The actor selects a scope tab for products or workshops.
5. The system shows only the vouchers of that scope.
6. The actor opens the Conditions of a voucher.
7. The system shows the offer and the published products, packages and branches it covers.
8. The Member selects Save voucher.
9. The system claims one use of the voucher for the Member.
10. The system adds the voucher to My vouchers.
11. The system confirms that the voucher has been saved (MSG45).

#### Alternative Flows

***Step 1.1 Actor Views Offers on a Product Page***

1. The actor opens a product card or the Product Details page.
2. The system takes the largest automatic discount, then the best usable voucher, off one unit of each size.
3. The system shows the reduced price, the original price struck through and up to two voucher labels (MSG70).
4. The actor selects a branch on Product Details or in the Quick add box.
5. The system recalculates the price with only the vouchers usable at that branch.
6. The use case ends.

***Step 1.2 Actor Views Offers on a Workshop Package Page***

1. The actor opens a workshop package card or the package details page.
2. The system takes the largest workshop automatic discount, then the best workshop voucher, off one ticket.
3. The system shows the reduced price, the original price struck through and the voucher labels (MSG70).
4. The use case ends.

***Step 1.3 Promotions Cannot Be Read for a Product or Package Page***

1. The system shows the normal price without any error.
2. The use case ends.

***Step 2.1 No Voucher to Save***

1. The system informs the actor that there are no vouchers to save right now (MSG339).
2. The use case ends.

***Step 2.2 Voucher List Cannot Be Loaded***

1. The system informs the actor that the data could not be loaded (MSG05).
2. The system offers Retry.
3. The use case ends.

***Step 3.1 Voucher Has No Use Left***

1. The system greys out the card as fully claimed and offers no Save (MSG46).
2. The use case continues at step 4.

***Step 7.1 Voucher Is No Longer Public***

1. The system informs the actor that the offer does not exist or is no longer public (MSG338).
2. The use case ends.

***Step 7.2 Voucher Is Public and Needs No Saving***

1. The system states that the code needs no saving and is picked at checkout (MSG67).
2. The use case ends.

***Step 8.1 Guest Wants to Save a Voucher***

1. The system shows Sign in to save instead of Save (MSG44).
2. The Guest signs in as a Member.
3. The use case continues at step 8.

***Step 8.2 Signed-in Actor Is Not a Member***

1. The system states that only members can save vouchers and offers no Save (MSG57).
2. The use case ends.

***Step 8.3 Member Has Already Saved the Voucher***

1. The system shows the voucher as saved, with a link to My vouchers.
2. The system claims no further use.
3. The use case ends with the voucher already in My vouchers.

***Step 9.1 No Use Left or Member Has Used Their Use***

1. The system refuses the save and shows the reason (MSG69).
2. The use case ends without saving the voucher.

***Step 9.2 Save Is Not Confirmed***

1. The system informs the Member that the result could not be confirmed (MSG04).
2. The system offers Retry with the same request.
3. The use case continues at step 9.

***Step 11.1 Member Opens My Vouchers***

1. The Member opens My vouchers.
2. The system lists the Member's own saved vouchers with their current availability.
3. The Member filters by scope and by the Valid or Expired / Unusable tab.
4. The system shows each voucher's state, such as paused, expired or no uses left (MSG46).
5. The Member selects Use now on a usable voucher.
6. The system opens the product list or the workshop list.
7. The use case ends.

***Step 11.2 Member Has No Saved Voucher***

1. The system informs the Member that no voucher has been saved yet (MSG340).
2. The use case ends.

#### Postconditions

- A saved voucher appears once in the Member's My vouchers.
- Saving a Must-be-saved voucher claims one of its total uses for the Member.
- Displayed prices are indicative; checkout and booking calculate the final amount.

#### Business Rule

BR-22, BR-23, BR-26, BR-32, BR-195, BR-196
