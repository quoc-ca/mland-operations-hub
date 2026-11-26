### 6.6 View and Save Vouchers

| Use Case ID | UC-47 | Use Case Name | View and Save Vouchers |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | — |

#### Description

As a Guest or Member, I want to view promotions and save eligible vouchers so that I can see discounted prices and use saved vouchers.

#### Trigger

A Guest or Member opens a product page, workshop package page, or the Voucher Store.

#### Preconditions

1. The platform is available.
2. Viewing requires no sign-in; saving vouchers requires a signed-in Member.

#### Postconditions

1. A saved voucher appears in the Member's My Saved Vouchers list.
2. Saving a Must-be-saved voucher claims one of its total uses for the Member.
3. Displayed product and package prices are advisory; checkout or booking quotes calculate the final price.

#### Normal Flow

**View and Save Vouchers**

1. Guest or Member opens a product page, workshop package page, or the Voucher Store.
2. System displays voucher cards with scope, discount, cap, minimum amount, validity, code, and status (MSG70).
3. Guest or Member opens Voucher Details for a voucher.
4. System displays the applicable scope, limits, and save eligibility (MSG69).
5. Member selects Save on a Must-be-saved voucher.
6. System validates availability, claims one use, adds it to My Saved Vouchers, and confirms (MSG46).
7. Member opens My Saved Vouchers to view saved vouchers by status and category.

**View Promotions on Product Pages**

1. Guest or Member views a product card or product details page.
2. System retrieves active product promotions matching each size.
3. System subtracts the largest applicable automatic discount for one unit.
4. System subtracts the best eligible public or saved voucher for one unit.
5. System displays the resulting price, original price struck through, and voucher labels (MSG72).
6. System recalculates the price after the actor selects a branch on product details.

**View Promotions on Workshop Package Pages**

1. Guest or Member views a workshop package card or package details page.
2. System retrieves active workshop promotions matching the package.
3. System subtracts the largest applicable workshop automatic discount for one ticket.
4. System subtracts the best eligible public or saved workshop voucher for one ticket.
5. System displays the resulting price, original price struck through, and voucher labels (MSG72).

#### Alternative Flows

2a. No product in the program's scope is published → System hides it publicly and marks it inapplicable in My Saved Vouchers (MSG47).

2b. All uses of a voucher are used, held, or claimed → System displays it as Fully claimed or All claimed without Save (MSG47).

6a. Voucher is already saved → System claims no extra use and keeps one list entry.

7a. Saved voucher is expired, paused, or fully used → System displays its current status in My Saved Vouchers (MSG47).

Product 3a. Automatic discount minimum exceeds unit price → System keeps the price unchanged and shows the minimum-amount label.

Workshop 3a. Automatic discount minimum exceeds ticket price → System keeps the price unchanged and shows the minimum-amount label.

#### Exception Flows

5a. Guest selects Save → System asks the Guest to sign in (MSG45).

5b. Signed-in Staff, Manager, or Admin views Save → System shows only members can save vouchers instead of Save (MSG58).

5c. Voucher is not in the public list → System responds as if the voucher does not exist.

5d. Session expires, save fails, or another Member's list is requested → System prompts sign-in, retry, or rejects (MSG01, MSG02, MSG05).

6a. No use remains or Member already used their use → System refuses the save and shows the reason (MSG71).

Product 2a. Promotions fail to load → System displays the normal price without an error.

Product 4a. My Saved Vouchers cannot be read → System calculates the price as for a Guest.

Workshop 2a. Promotions fail to load → System displays the normal price without an error.

Workshop 4a. My Saved Vouchers cannot be read → System calculates the price as for a Guest.

#### Business Rules

BR-47-01, BR-47-02, BR-47-03, BR-47-04, BR-47-05

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG05, MSG45, MSG46, MSG47, MSG58, MSG69, MSG70, MSG71, MSG72

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-47-01 | Voucher cards show scope, discount, cap, minimum amount, validity and point combination; public details list only products that are currently published. A voucher whose uses are all used or held, or, for a voucher that must be saved, used, held or claimed, stays visible as "Fully claimed" or "All claimed" and cannot be saved. |
| BR-47-02 | The Voucher Store lists only vouchers that must be saved. Only a signed-in Member can save vouchers, only to their own My Saved Vouchers list, and only vouchers that are in the public list; repeated saves create no extra right. |
| BR-47-03 | Saving a public voucher reserves nothing. Saving a voucher that must be saved claims one of its total uses, allows one use per Member, and only Members who saved it can use it. Eligibility is still checked when the voucher is applied. |
| BR-47-04 | Product pages subtract the largest automatic discount and then the best voucher for one unit of each size, using the retail checkout rules, labelled "Price after voucher {code}", with "· at {branches}" for a voucher limited to branches; on the product details the price is recalculated for the chosen branch. Only usable public vouchers and, for a signed-in Member, usable saved vouchers are counted. The displayed price never replaces the checkout quote, and a failure to read promotions never blocks the product page. |
| BR-47-05 | Workshop package pages subtract the largest workshop automatic discount and then the best workshop voucher for one ticket, labelled "Price after voucher {code}"; vouchers limited to branches are counted with "· at {branches}", vouchers limited to dates or sessions are not, and a signed-in Member's saved vouchers are counted. Points are never subtracted. The displayed price never replaces the workshop cart quote, and a failure to read promotions never blocks the page. |
