### 6.4 Manage Automatic Discounts

| Use Case ID | UC-45 | Use Case Name | Manage Automatic Discounts |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | Product Management, Workshop Management |

#### Description

As a Manager, I want to create, view, edit, pause, and resume automatic discounts so that eligible retail products and workshops receive automatic promotions.

#### Trigger

A Manager opens the Promotion List to create or manage an automatic discount.

#### Preconditions

1. The Manager is signed in, the account is active, and the role is Manager.

#### Postconditions

1. A valid automatic discount is active for eligible new orders or direct workshop bookings during its period.
2. Previously accepted orders and bookings keep their recorded discount.

#### Normal Flow

**Create Automatic Discount**

1. Manager opens the Promotion List and selects New automatic discount.
2. System opens the Promotion Form.
3. Manager enters name, period, scope, discount type, value, percentage cap, and minimum amount.
4. System validates the entered data and scope.
5. System saves the automatic discount, records a change log, and confirms (MSG38).

**View, Edit, Pause or Resume Automatic Discount**

1. Manager filters the Promotion List and opens an automatic discount in Promotion Details.
2. System displays conditions, period, used count, total discount given, and unavailable-product warnings (MSG44).
3. Manager edits allowed conditions, pauses the discount, or resumes a paused discount.
4. System validates and saves the change for future transactions (MSG43).

#### Alternative Flows

Edit 3a. Manager changes a fixed discount amount → System resets the cap equal to the new amount.

Edit 3b. Manager resumes a paused discount → System resumes it with existing history and counts without extending its period.

#### Exception Flows

1a. Actor is not a Manager or session expired → System denies access and logs the rejected attempt (MSG01, MSG02).

Create 4a. Required fields, time range, amount, percentage, or minimum is invalid → System rejects the request (MSG08).

Create 4b. Product, size, or workshop package scope is invalid → System rejects the request (MSG40).

Edit 3a. Manager attempts to change the scope → System rejects the scope change after creation (MSG41).

Save 5a. Save fails or response is lost → System reports unconfirmed result without creating a duplicate (MSG04).

#### Business Rules

BR-45-01, BR-45-02, BR-45-03, BR-45-04, BR-45-05

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG04, MSG08, MSG38, MSG40, MSG41, MSG43, MSG44

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-45-01 | Only a Manager may create, edit, pause or resume automatic discounts. |
| BR-45-02 | Automatic discounts apply to retail products or to workshop packages, need no code, have no total or per-Member use limits and always allow loyalty points. Product automatic discounts have no branch limit; workshop automatic discounts have no branch, date or session limit and apply to every direct website customer, including Guests. |
| BR-45-03 | A product scope is a set of products and/or specific size variants; when both are selected, each size must belong to a selected product. A workshop scope is one or more packages. The scope cannot change after creation. |
| BR-45-04 | When several automatic discounts apply to an order or booking, the one with the largest discount is used, and the earliest created one wins a tie. The automatic discount cannot be declined; a voucher chosen by the customer is added on top of it and calculated on the amount after it. |
| BR-45-05 | A maximum discount is entered only for a percentage discount; for a fixed amount, the cap is stored equal to the amount. |
