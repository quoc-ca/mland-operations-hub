### 3.1 UC-08 Book Workshop

#### UC Name

UC-08 Book Workshop

#### Primary Actors

Guest, Member

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Guest or Member, I want to choose workshop packages, their sessions and the number of participants so that the system holds the seats and creates one ticket per person.

#### Preconditions

- At least one workshop package is published and sold at an active branch.
- The chosen sessions are open for sale and have free seats.
- The actor is not signed in, or is signed in with the Member role.

#### Normal Flow

***Book Workshop***

1. The actor opens the workshop package list.
2. The system shows the published packages with the price after the best automatic discount and voucher.
3. The actor opens a workshop package.
4. The system shows the package details, its selling branches and the open sessions with seats left.
5. The actor selects a branch, the complete required session set and the participant count.
6. The actor selects Book now.
7. The system quotes each ticket with its price, booking-time amount, remaining balance and payment type.
8. The system shows that the booking-time payment is not refunded on self-cancellation or no-show (MSG154).
9. The actor provides the contact email and phone number.
10. The actor confirms the booking.
11. The system validates the quote and holds every line of the cart in one transaction.
12. The system creates the booking group with one ticket per person and starts the 12-minute hold.
13. The system confirms that the seats are held and shows the payment countdown (MSG156).

#### Alternative Flows

***Step 5.1 Session Selection Is Incomplete***

1. The system informs the actor that the session set or the participant count is not valid for the package (MSG147).
2. The use case continues at step 5.

***Step 6.1 Actor Adds the Line to the Cart***

1. The actor selects Add to cart.
2. The system stores the line in the cart of this browser and informs the actor (MSG146).
3. The actor repeats steps 3 to 5 for other packages, up to 10 lines.
4. The actor opens the cart and requests the quote.
5. The use case continues at step 7.

***Step 7.1 Member Applies a Voucher or Points***

1. The Member selects one voucher, enters a number of points, or both.
2. The system quotes the cart again with the automatic discount, then the voucher, then the points.
3. The system shows why a voucher or the points cannot be used (MSG47, MSG48, MSG49).
4. The use case continues at step 8.

***Step 7.2 Guest Asks for a Voucher or Points***

1. The system refuses the voucher and the points for a Guest (MSG153).
2. The system keeps only the automatic discount in the quote.
3. The use case continues at step 8.

***Step 7.3 Package Is Not Sold at the Chosen Branch***

1. The system informs the actor that the package is no longer sold at that branch (MSG148).
2. The system holds nothing and keeps the details already entered.
3. The use case continues at step 5.

***Step 7.4 Fee After Discounts Is Negative***

1. The system refuses the quote because the amount after discounts is negative (MSG51).
2. The use case continues at step 7.1.

***Step 8.1 Group Collects Less Than 5,000 VND Online***

1. The system labels every ticket as paid at the counter or free.
2. The system shows the amount to pay at the counter at checkout instead of the non-refundable notice (MSG155).
3. The use case continues at step 9.

***Step 9.1 Contact Details Are Invalid***

1. The system shows the error under the email or phone field (MSG151).
2. The use case continues at step 9.

***Step 11.1 Quote Has Expired or Changed***

1. The system refuses a quote older than 30 minutes, already used, or with a changed discount or points (MSG150, MSG50).
2. The system holds nothing.
3. The use case continues at step 7.

***Step 11.2 A Line Has Not Enough Seats***

1. The system lists the lines that have not enough seats (MSG149).
2. The system holds nothing and keeps the cart editable.
3. The use case continues at step 5.

***Step 11.3 A Required Service Is Unavailable***

1. The system informs the actor that the request cannot be processed now (MSG152).
2. The system holds nothing and keeps the cart and contact details.
3. The use case ends without creating a booking group.

***Step 13.1 Nothing Is Collected Online***

1. The system confirms the group at once without VNPay because its booking-time collection is below 5,000 VND.
2. The system issues each ticket's QR code and the group Magic Link.
3. The system sends the confirmation email with every ticket through the Mail Gateway.
4. The use case ends with the booking group confirmed.

#### Postconditions

- One booking group and one ticket per person are created, all or nothing.
- The seats, the voucher use and the points are held until the hold ends.
- Each ticket keeps its price, discount share, booking-time amount and payment type.
- The group waits for UC-09 Pay Workshop Deposit unless nothing is collected online.

#### Business Rule

BR-01, BR-02, BR-04, BR-09, BR-16, BR-17, BR-18, BR-25, BR-26, BR-80, BR-81, BR-82
