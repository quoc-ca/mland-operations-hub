### 6.6 View and Save Vouchers

#### Primary Actors

Guest, Member.

#### Secondary Actors

None.

#### Description

This use case allows a Guest or Member to read promotion cards and their conditions on product pages, workshop package pages and the Voucher hub, and allows a Member to save vouchers that must be saved to their personal My vouchers list. A voucher is either Public, which needs no saving because it is shown on product and package pages and suggested at checkout, or Must be saved, which can be used only after the Member saves it; each save claims one of the voucher's total uses. On the public product cards and product details provided by Product Management, and on the public workshop package pages provided by Workshop Management, it also shows the price after the best automatic discount and the best voucher.

This use case includes the following sub-flows:

- View and Save Vouchers
- View Promotions on Product Pages
- View Promotions on Workshop Package Pages

#### Preconditions

1. The platform is available. Viewing requires no sign-in; saving requires a signed-in Member.

#### Normal Flow

**View and Save Vouchers**

1. The actor opens a product page, a workshop package page or the Voucher hub. The Voucher hub lists only vouchers that must be saved, with the tabs All, Products and Workshops. Public vouchers and automatic discounts need no saving and are shown on the product and package pages.
2. The system displays the voucher cards: scope (Products or Workshops), discount, maximum discount, minimum amount, validity, whether points can be combined, the code and a Conditions link. A product voucher limited to branches shows "Only at: {branch names}". A voucher that must be saved shows "Save to use" (MSG70).
3. The actor opens the conditions of a card. From a product or package page, the link reads "View and save code" for a voucher that must be saved and "View conditions" for a public voucher.
4. The system shows the products or size variants in scope and, for a product voucher, its branches ("All branches" when not limited), or the packages, branches, dates and sessions of a workshop voucher, plus the total limit and uses per Member. Only products that are currently published are listed. A public voucher has no Save button; its details show that it needs no saving (MSG69).
5. The Member selects Save on a voucher that must be saved.
6. The system checks that the voucher is in the public list, that the Member has not used their use and that a use is left to claim (total uses minus used, held and saved but unused uses is at least 1). It claims one use for the Member, adds the voucher to the Member's My vouchers list and confirms (MSG46).
7. The Member opens My vouchers; the system shows each saved voucher with its current conditions and status, filtered by All, Products or Workshops, in the tabs Valid (usable or not started yet) and Expired / Unusable. A voucher that must be saved stays Usable for the Member who saved it while their claimed use is not used, even when no use is left for other Members. Public vouchers saved earlier remain in the list.

**View Promotions on Product Pages**

1. The actor views a product card in the product list or the home page strip, or opens a product's details.
2. The system reads the active product programs and finds, for each size, the programs whose product and size scope it matches.
3. For each size, the system subtracts the largest applicable automatic discount for one unit, using the same rules as the retail checkout: a percentage rounded down, never above the cap or the price.
4. The system then subtracts the best voucher for one unit, calculated on the price after the automatic discount with the same rules. The vouchers counted are the usable public vouchers and, for a signed-in Member, the usable vouchers in their My vouchers list, including vouchers that must be saved which the Member has saved. A voucher that must be saved but is not saved, a voucher that has not started or has no use left for the Member, and a voucher whose minimum amount is above the price after the automatic discount are not counted. A voucher limited to branches is counted when the size is sold at one or more of its branches. On a tie, the earliest created voucher is used.
5. The system shows the resulting price, with the original price struck through and the label "Price after voucher {code}", or "Price after voucher {code} · at {branches}" for a voucher limited to branches (MSG72). The counted voucher is listed first and up to two voucher labels are shown; a voucher that must be saved and is not saved yet shows only the label "Save code: {value} off" (MSG72). A program that applies to only some sizes names those sizes.
6. On the product details, when the actor has chosen a branch, the system counts only vouchers without a branch limit or limited to that branch, and calculates the price again for that branch.

**View Promotions on Workshop Package Pages**

1. The actor views a workshop package card or opens a package's details.
2. The system reads the active workshop programs and finds the programs whose scope the package matches; a workshop automatic discount is matched by package only.
3. For one ticket, the system subtracts the largest applicable workshop automatic discount, using the same rules as the booking quote: a percentage rounded down, never above the cap or the ticket price, and 0 when the ticket price is below the minimum package fee.
4. The system then subtracts the best workshop voucher for one ticket, calculated on the price after the automatic discount with the same rules and the same vouchers counted as for product pages. A voucher limited to branches is counted; a voucher limited to dates or sessions, and a voucher whose minimum package fee is above the price after the automatic discount, are not counted.
5. The system shows this price with the original price struck through and the label "Price after voucher {code}", with "· at {branches}" for a voucher limited to branches (MSG72). A Guest, or a Member who has not saved a better code, sees the price with the best public voucher; a Member who has saved a better code sees the price with that code. The counted voucher is listed first among the labels; a card shows at most two voucher labels and, when the actor is not signed in, an invitation to sign in because vouchers are for Members only. The package details may list the running vouchers as chips.
6. Loyalty points are never subtracted from a displayed price.

#### Alternative Flows

**Step 5 — Guest selects Save**

The system asks the Guest to sign in (MSG45).

**Step 5 — Signed-in actor is not a Member**

A signed-in Staff, Manager or Admin has no My vouchers list; the system shows that only members can save vouchers (MSG58) instead of a Save button.

**Step 6 — Voucher already saved**

Saving again claims no extra use and the list keeps one entry.

**Step 6 — No use left to claim or the Member has used their use**

The system refuses the save and shows "All claimed" or "You have used your use of this code" (MSG71). When two Members try to save the last use at the same time, only one succeeds.

**Step 2 — No product in the program's scope is published**

The program is not shown in the public list. In My vouchers it is shown with the status that no product currently applies (MSG47). A program whose targeted sizes are all no longer published is treated the same way.

**Step 2 — All uses of a voucher are used, held or claimed**

The voucher is still shown on the public cards and details without a Save button, labelled "Fully claimed", or "All claimed" for a voucher that must be saved when all its uses are used, held or claimed (MSG47). This label is for display only; the quote and the order or booking still decide whether the voucher can be used.

**Step 5 — Voucher is not in the public list**

A voucher that is paused, not yet started, expired or has no published product in scope cannot be saved, even by its identifier; the system responds as if the voucher does not exist. A voucher saved earlier stays in My vouchers with its status.

**Step 7 — Saved voucher is expired, paused, fully used or not yet eligible**

My vouchers shows the status, including when all uses of the voucher, or all of the Member's own uses, are used or held (MSG47). Being in My vouchers does not make the voucher usable.

**Product Pages Step 3 / Workshop Package Pages Step 3 — Automatic discount has a minimum above the price of one unit or one ticket**

The price is not reduced; the system shows a label "{discount} off orders from {minimum amount}".

**Product Pages Step 2 / Workshop Package Pages Step 2 — Promotions cannot be loaded**

The page shows the normal price without an error.

**Product Pages Step 4 / Workshop Package Pages Step 4 — My vouchers cannot be read or the session is lost**

The page does not block; the price is calculated as for a Guest.

**Steps 5–7 — Session expired, load or save failure, or request for another Member's list**

The system asks the Member to sign in or retry (MSG02, MSG05), or rejects the request (MSG01). It does not report a save that was not confirmed. A rejected save is recorded as in UC-45.

#### Postconditions

- A saved voucher appears in the Member's own My vouchers list.
- Saving a public voucher does not hold a use, use points or consume the limit, and does not guarantee the voucher can be used; a Member can still enter a public code directly without saving it.
- Saving a voucher that must be saved claims one of its total uses for the Member; only a Member who saved it can use it. This version has no option to remove a saved voucher.
- A price shown on a product page or workshop package page is for viewing only; the amount paid is calculated again by the checkout quote or the workshop cart quote, and the cart does not use the displayed price.

#### Business Rules

BR-47-01, BR-47-02, BR-47-03, BR-47-04, BR-47-05

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-47-01 | Voucher cards show scope, discount, cap, minimum amount, validity and point combination; public details list only products that are currently published. A voucher whose uses are all used or held, or, for a voucher that must be saved, used, held or claimed, stays visible as "Fully claimed" or "All claimed" and cannot be saved. |
| BR-47-02 | The Voucher hub lists only vouchers that must be saved. Only a signed-in Member can save vouchers, only to their own My vouchers list, and only vouchers that are in the public list; repeated saves create no extra right. |
| BR-47-03 | Saving a public voucher reserves nothing. Saving a voucher that must be saved claims one of its total uses, allows one use per Member, and only Members who saved it can use it. Eligibility is still checked when the voucher is applied. |
| BR-47-04 | Product pages subtract the largest automatic discount and then the best voucher for one unit of each size, using the retail checkout rules, labelled "Price after voucher {code}", with "· at {branches}" for a voucher limited to branches; on the product details the price is recalculated for the chosen branch. Only usable public vouchers and, for a signed-in Member, usable saved vouchers are counted. The displayed price never replaces the checkout quote, and a failure to read promotions never blocks the product page. |
| BR-47-05 | Workshop package pages subtract the largest workshop automatic discount and then the best workshop voucher for one ticket, labelled "Price after voucher {code}"; vouchers limited to branches are counted with "· at {branches}", vouchers limited to dates or sessions are not, and a signed-in Member's saved vouchers are counted. Points are never subtracted. The displayed price never replaces the workshop cart quote, and a failure to read promotions never blocks the page. |
