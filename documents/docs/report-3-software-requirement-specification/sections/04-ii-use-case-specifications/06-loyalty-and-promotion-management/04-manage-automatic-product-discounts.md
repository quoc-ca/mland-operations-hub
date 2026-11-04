### 6.4 Manage Automatic Product Discounts

#### Primary Actors

- Manager

#### Secondary Actors

- Product Management, which supplies products and size variants for the scope

#### Description

This use case allows a Manager to create, view, edit, pause and resume automatic discounts for retail products. An automatic discount needs no code, has no branch limit and always allows loyalty points. There are no automatic discounts for workshops.

#### Preconditions

1. The Manager is signed in, the account is active, and the current role is Manager.

#### Normal Flow

**Create Automatic Discount**

1. The Manager opens Promotion Management and selects New automatic discount.
2. The Manager enters the name, start and end time, scope (products and/or specific size variants such as `SP000001-S09`), discount type (fixed amount or percentage), discount value, maximum discount and minimum eligible amount.
3. The system validates the data and scope.
4. The system saves the automatic discount, records a change log and confirms (MSG38).

**View, Edit, Pause or Resume Automatic Discount**

1. The Manager filters the program list and opens an automatic discount.
2. The system shows its conditions, time, used count and total discount given, and warns if products in its scope are hidden, stopped from sale or deleted (MSG44).
3. The Manager edits the allowed conditions, pauses the discount, or resumes a paused discount.
4. The system validates the change and saves it; changes apply only to transactions accepted afterwards (MSG43).

#### Alternative Flows

**Create Step 3 — Invalid data**

The system rejects missing fields, a start time that is not before the end time, a fixed amount or maximum discount that is not a positive whole VND amount, a percentage outside greater than 0 to 100%, or a negative minimum amount (MSG08).

**Create Step 3 — Invalid scope**

The system rejects a product or size that does not exist, a size that does not belong to a selected product, or a soft-deleted product (MSG40). Unpublished, hidden products and stopped sizes can still be selected.

**Edit Step 3 — Changing the scope**

The system rejects scope changes after creation (MSG41).

**Edit Step 3 — Resuming a paused discount**

The discount is resumed with the same history and counts; it is not extended. New transactions still check the current time and scope.

**Any step — Actor is not a Manager or the session has expired**

The system rejects the request (MSG01, MSG02). Admin, Staff, Member and Guest cannot manage programs.

**Any save — Save failure or lost response**

The system reports success only after a confirmed result and does not create a duplicate (MSG04).

#### Postconditions

- A valid automatic discount is available to eligible new orders during its time.
- Orders that were already accepted keep their recorded discount.
- Holiday promotions are created as ordinary programs with their own time, not as an extra discount layer.

#### Business Rules

- BR-45-01
- BR-45-02
- BR-45-03
- BR-45-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-45-01 | Only a Manager may create, edit, pause or resume automatic discounts. |
| BR-45-02 | Automatic discounts apply only to retail products, need no code, have no branch limit and always allow loyalty points. |
| BR-45-03 | The scope is a set of products and/or specific size variants; when both are selected, each size must belong to a selected product, and the scope cannot change after creation. |
| BR-45-04 | When several automatic discounts apply, the one with the largest discount is used, and the earliest created one wins a tie; choosing a voucher replaces the automatic discount. |
