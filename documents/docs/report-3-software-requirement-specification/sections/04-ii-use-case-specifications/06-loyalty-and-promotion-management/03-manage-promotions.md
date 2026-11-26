### 6.3 Manage Promotions

| Use Case ID | UC-45 | Use Case Name | Manage Promotions |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | Product Management, Workshop Management |

#### Description

As a Manager, I want to create, monitor, edit, pause, and resume automatic discounts and vouchers for products and workshops so that customers receive the intended promotions.

#### Trigger

A Manager opens the Promotion List to create or manage an automatic discount or a voucher.

#### Preconditions

1. The Manager is signed in, the account is active, and the role is Manager.

#### Postconditions

1. A valid automatic discount or voucher applies to eligible new orders or direct workshop bookings during its period.
2. Each voucher has a unique, immutable code that is never reused.
3. Accepted orders and bookings keep the discount and conditions recorded at acceptance.

#### Normal Flow

**Create Automatic Discount**

1. Manager opens the Promotion List and selects New automatic discount.
2. System opens the Promotion Form.
3. Manager enters name, period, scope, discount type, value, percentage cap, and minimum amount.
4. System validates the entered data and scope.
5. System saves the automatic discount, records a change log, and confirms (MSG38).

**Create Voucher**

1. Manager selects New product voucher or New workshop voucher on the Promotion List.
2. System opens the Promotion Form and generates an `ML` voucher code.
3. Manager enters name, period, scope, discount settings, usage limits, point rule, and distribution.
4. System validates the data, scope, and code uniqueness.
5. System saves the voucher, records a change log, and confirms with the code (MSG38).

**View, Edit, Pause or Resume a Promotion**

1. Manager filters the Promotion List and opens a promotion in Promotion Details.
2. System displays conditions, distribution, period, limits, usage counts, total discount given, and unavailable-product warnings (MSG44).
3. Manager edits allowed conditions, pauses the promotion, or resumes a paused promotion.
4. System validates and saves the change for future transactions (MSG43).

#### Alternative Flows

V2a. Branch in product voucher scope becomes inactive → System keeps the voucher active and displays a warning.

V2b. Workshop booking using a voucher is validly cancelled → System excludes the booking from counts unless the voucher already expired.

V3a. Manager changes a fixed discount amount → System resets the cap equal to the new amount.

V3b. Manager resumes a paused promotion → System keeps its code, history, and counts without extending its period.

#### Exception Flows

1a. Actor is not a Manager, session expired, or save failed → System rejects the request or reports an unconfirmed result without a duplicate (MSG01, MSG02, MSG04).

4a. Required fields, time range, amount, percentage, or minimum is invalid → System rejects the request (MSG08).

4b. Product, size, workshop package, branch, or session scope is invalid → System rejects the request (MSG40).

C4a. Generated voucher code already exists → System regenerates up to 3 times; persistent collision fails (MSG39).

V3c. Manager attempts to change the code, scope, or distribution → System rejects the change (MSG41).

V3d. New voucher limit is below used, held, and saved counts → System rejects the limit and shows current counts (MSG42).

#### Business Rules

BR-45-01, BR-45-02, BR-45-03, BR-45-04, BR-45-05, BR-45-06, BR-45-07, BR-45-08, BR-45-09, BR-45-10

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG04, MSG08, MSG38, MSG39, MSG40, MSG41, MSG42, MSG43, MSG44

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-45-01 | Only a Manager may create, edit, pause or resume automatic discounts and vouchers; Admin has no promotion permission. |
| BR-45-02 | Automatic discounts apply to retail products or to workshop packages, need no code, have no total or per-Member use limits and always allow loyalty points. Product automatic discounts have no branch limit; workshop automatic discounts have no branch, date or session limit and apply to every direct website customer, including Guests. |
| BR-45-03 | A product scope of an automatic discount or voucher is a set of products and/or specific size variants; when both are selected, each size must belong to a selected product. Unpublished or hidden products and sizes stopped from sale may be selected; soft-deleted products and sizes are rejected. An automatic discount for workshops covers one or more packages; a workshop voucher sets package, branch and session, each either All or exactly one item. The scope cannot change after creation. |
| BR-45-04 | When several automatic discounts apply to an order or booking, the one with the largest discount is used, and the earliest created one wins a tie. The automatic discount cannot be declined; a voucher chosen by the customer is added on top of it and calculated on the amount after it. |
| BR-45-05 | Discount amounts and caps are positive whole VND; a cap is entered only for a percentage, and for a fixed amount it is stored equal to the amount. Percentages are greater than 0 up to 100%, minimum amounts are whole VND of 0 or more, and use limits are positive whole numbers. |
| BR-45-06 | Voucher codes are generated by the system, unique across all vouchers after trimming and ignoring case, never changed and never reused. |
| BR-45-07 | A voucher's code, scope, including the branch scope of a product voucher, and distribution cannot change after creation; total and per-Member limits cannot be lowered below the current used plus held uses, and, for a Must-be-saved voucher, the total limit also counts the saved but unused uses. A Must-be-saved voucher allows one use per Member. |
| BR-45-08 | Pausing, resuming, editing or expiry never changes discounts already accepted; resuming keeps the same code, history and counts and does not extend the promotion. |
| BR-45-09 | The Promotion Form and the edit form check each field as soon as its value changes, with the same rules as on save; target selection errors and "nothing has changed" still appear only on save. |
| BR-45-10 | The branch scope of a product voucher is optional and defaults to All branches; every selected branch must exist and be active when saving. Once branches are selected, the product finder suggests products sold at those branches, and a size not sold at any selected branch can still be saved but the form shows a warning. Automatic discounts have no branch scope. |
