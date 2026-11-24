### 6.4 Manage Automatic Discounts

#### Primary Actors

Manager.

#### Secondary Actors

Product Management, which supplies products and size variants for the scope; Workshop Management, which supplies workshop packages for the scope.

#### Description

This use case allows a Manager to create, view, edit, pause and resume automatic discounts for retail products and for workshop packages. An automatic discount needs no code, has no use limits and always allows loyalty points. A product automatic discount has no branch limit. A workshop automatic discount is limited only by package, with no branch, date or session limit, because its discounted price is shown publicly before the customer chooses a branch and a session; it applies to every customer booking directly on the website, including Guests.

This use case includes the following sub-flows:

- Create Automatic Discount
- View, Edit, Pause or Resume Automatic Discount

#### Preconditions

1. The Manager is signed in, the account is active, and the current role is Manager.

#### Normal Flow

**Create Automatic Discount**

1. The Manager opens Promotion Management and selects New automatic discount for products or for workshops.
2. The Manager enters the name, start and end time, scope, discount type (fixed amount or percentage), discount value, maximum discount for a percentage, and minimum eligible amount (for products) or minimum package fee (for workshops). A fixed amount has no separate maximum discount field; the system stores the cap equal to the amount.
   - For products, the scope is products and/or specific size variants such as `SP000001-S09`. In the product picker, the Manager can filter by status (All, Public, Not public yet or hidden) and select Choose all on this page or Clear all on this page; these act only on the products shown on the current page.
   - For workshops, the scope is one or more workshop packages.
3. The system validates the data and scope.
4. The system saves the automatic discount, records a change log and confirms (MSG38).

**View, Edit, Pause or Resume Automatic Discount**

1. The Manager filters the program list and opens an automatic discount.
2. The system shows its conditions, time, used count and total discount given, and warns if products in its scope are hidden, stopped from sale or deleted (MSG44). The used count and total discount of a workshop automatic discount include Guest bookings.
3. The Manager edits the allowed conditions, pauses the discount, or resumes a paused discount.
4. The system validates the change and saves it; changes apply only to transactions accepted afterwards (MSG43).

#### Alternative Flows

**Create Step 3 — Invalid data**

The system rejects missing fields, a start time that is not before the end time, a fixed amount or maximum discount that is not a positive whole VND amount, a percentage outside greater than 0 to 100%, or a negative minimum amount (MSG08). For a fixed amount, a maximum discount sent with the request that differs from the amount is rejected.

**Create Step 3 — Invalid scope**

The system rejects a product or size that does not exist, a size that does not belong to a selected product, or a soft-deleted product, and a product discount request that includes a branch (MSG40). Unpublished, hidden products and stopped sizes can still be selected. A workshop discount without any package, or with a branch, date or session, is rejected (MSG40); an empty package scope does not mean every package.

**Edit Step 3 — Changing the scope**

The system rejects scope changes after creation (MSG41).

**Edit Step 3 — Changing a fixed discount amount**

The cap is set again equal to the new amount. An earlier fixed discount whose cap is lower than its amount keeps working as before, and the screen shows the amount actually taken off, the smaller of the amount and the cap.

**Edit Step 3 — Resuming a paused discount**

The discount is resumed with the same history and counts; it is not extended. New transactions still check the current time and scope.

**Any step — Actor is not a Manager or the session has expired**

The system rejects the request (MSG01, MSG02). Admin, Staff, Member and Guest cannot manage programs. Promotion Management pages are shown only to the Manager role: a signed-in Member, Staff, Admin or Carrier who opens one directly is shown the not-found page before the page loads any data, and the server still checks every request.

**Any save — Save failure or lost response**

The system reports success only after a confirmed result and does not create a duplicate (MSG04).

**Any save — Request is rejected**

When a create, edit, pause or resume request is rejected, for example for permission, stale data, invalid data or limits, the system records the rejected attempt with the actor, the request path containing only identifiers, the error code and the names of the rejected fields, without values, tokens or personal data.

#### Postconditions

- A valid automatic discount is available to eligible new orders or direct workshop bookings during its time.
- Orders and bookings that were already accepted keep their recorded discount.
- Holiday promotions are created as ordinary programs with their own time, not as an extra discount layer.

#### Business Rules

BR-45-01, BR-45-02, BR-45-03, BR-45-04, BR-45-05

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-45-01 | Only a Manager may create, edit, pause or resume automatic discounts. |
| BR-45-02 | Automatic discounts apply to retail products or to workshop packages, need no code, have no total or per-Member use limits and always allow loyalty points. Product automatic discounts have no branch limit; workshop automatic discounts have no branch, date or session limit and apply to every direct website customer, including Guests. |
| BR-45-03 | A product scope is a set of products and/or specific size variants; when both are selected, each size must belong to a selected product. A workshop scope is one or more packages. The scope cannot change after creation. |
| BR-45-04 | When several automatic discounts apply to an order or booking, the one with the largest discount is used, and the earliest created one wins a tie. The automatic discount cannot be declined; a voucher chosen by the customer is added on top of it and calculated on the amount after it. |
| BR-45-05 | A maximum discount is entered only for a percentage discount; for a fixed amount, the cap is stored equal to the amount. |
