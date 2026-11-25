### 3.3 Create Workshop Booking

#### Primary Actors

Guest, Member

#### Secondary Actors

Payment Gateway, Mail Gateway

#### Description

As a Guest or Member, I want to put one or more workshop packages with their sessions and participant counts into a cart and confirm it, so that the system holds the seats and creates a booking group with one ticket per person.

#### Preconditions

1. The platform is available.
2. The actor has selected a published workshop package that is active at the chosen branch.
3. The required sessions are open for booking and have enough capacity.
4. A Guest provides both a contact email and a contact phone. A Member may use verified profile information.

#### Postconditions

- A booking group and one ticket booking per person are created, all or nothing.
- The seats of every line are held for 12 minutes while payment is pending.
- A group whose booking-time collection is 0 VND is confirmed immediately without VNPay.
- A group with an amount to collect stays unconfirmed until UC 22 Pay Workshop Deposit succeeds.

#### Normal Flow

**Create Workshop Booking**

1. The actor selects a published package.
2. The actor selects the branch, the complete required session set, and the participant count, and adds the line to the cart or books it now.
3. The actor may add further lines to the cart.
4. The system quotes the cart with each ticket price and booking-time collection; the quote is valid for 30 minutes. A Member may apply one voucher and loyalty points.
5. The actor enters or confirms the contact details and accepts the non-refundable condition shown.
6. The actor confirms the cart.
7. The system validates and holds every line in one transaction and creates the booking group and its tickets.
8. The system directs the actor to UC 22 Pay Workshop Deposit, or confirms the group when nothing has to be collected.

#### Alternative Flows

**Step 2 — Package not active at the branch**

The system refuses the line and asks the actor to choose another branch or package.

**Step 4 — Quote expired, already used, or changed**

The system holds nothing and asks the actor to quote the cart again.

**Step 6 — Contact details are missing or invalid**

The system displays validation errors and keeps the cart editable.

**Step 7 — A line lacks capacity or eligibility**

The system reports the failing lines, holds nothing, and keeps the cart editable.

**Step 8 — Payment is not verified before the hold expires**

The group is not confirmed and the held seats are released. Late payment evidence is handled by UC 22.

#### Business Rules

BR-17-01, BR-17-02, BR-17-03, BR-17-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-17-01 | A booking line requires a package, its complete required-session set, and a participant count. |
| BR-17-02 | All lines are validated and held together; if one fails, nothing is held. |
| BR-17-03 | Each ticket collects the smaller of its final price and 200,000 VND at booking time. The remaining amount is collected later and is not multiplied by the number of sessions. |
| BR-17-04 | The group has one 12-minute hold; editing or retrying payment never extends it. |
