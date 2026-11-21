### 6.6 View and Save Vouchers

#### Primary Actors

Guest, Member.

#### Secondary Actors

None.

#### Description

This use case allows a Guest or Member to read public promotion cards and their conditions on product pages, workshop package pages and the Voucher hub, and allows a Member to save voucher codes to their personal My vouchers list for quick selection at checkout or booking. On the public product cards and product details provided by Product Management, and on the public workshop package pages provided by Workshop Management, it also shows the active promotions next to the price.

This use case includes the following sub-flows:

- View and Save Vouchers
- View Promotions on Product Pages
- View Promotions on Workshop Package Pages

#### Preconditions

1. The platform is available. Viewing requires no sign-in; saving requires a signed-in Member.

#### Normal Flow

**View and Save Vouchers**

1. The actor opens a product page, a workshop package page or the Voucher hub. The Voucher hub lists only vouchers, with the tabs All, Products and Workshops; automatic discounts need no saving and are shown on the product and package pages.
2. The system displays the cards of public programs: scope (Buy products or Book workshop), discount, maximum discount, minimum amount, validity, whether points can be combined, the code and a Conditions link. A product voucher limited to branches shows "Applies at: {branch names}".
3. The actor opens the conditions of a card.
4. The system shows the products or size variants in scope and, for a product voucher, its branches ("All branches" when not limited), or the packages, branches, dates and sessions of a workshop voucher, plus the total limit and uses per Member. Only products that are currently published are listed.
5. The Member selects Save.
6. The system adds the voucher to the Member's My vouchers list and confirms (MSG46).
7. The Member opens My vouchers; the system shows each saved voucher with its current conditions and status, in the tabs Valid (usable or not started yet) and Expired / Unusable.

**View Promotions on Product Pages**

1. The actor views a product card in the product list or the home page strip, or opens a product's details.
2. The system reads the active product programs and finds, for each size, the programs whose product, size and branch scope it matches.
3. For each size, the system subtracts the largest applicable automatic discount for one unit, using the same rules as the retail checkout: a percentage rounded down, never above the cap or the price.
4. The system shows the reduced price, with the original price struck through. Vouchers are not subtracted; up to two voucher labels show their discount. A program that applies to only some sizes names those sizes.

**View Promotions on Workshop Package Pages**

1. The actor views a workshop package card or opens a package's details.
2. The system reads the active workshop programs and finds the programs whose scope the package matches; a workshop automatic discount is matched by package only.
3. For one ticket, the system subtracts the largest applicable workshop automatic discount, using the same rules as the booking quote: a percentage rounded down, never above the cap or the ticket price, and 0 when the ticket price is below the minimum package fee.
4. The system then subtracts the workshop voucher with the largest discount for one ticket, calculated on the price after the automatic discount with the same rules; a voucher whose minimum package fee is above that price is not counted.
5. The system shows this price with the original price struck through and the label "Price after voucher {code}". The price is shown to everyone who views the page, whether or not the voucher has been saved. The counted voucher is listed first among the labels; a card shows at most two voucher labels and, when the actor is not signed in, an invitation to sign in because vouchers are for Members only. The package details may list the running vouchers as chips.
6. Loyalty points are never subtracted from a displayed price.

#### Alternative Flows

**Step 5 — Guest selects Save**

The system asks the Guest to sign in (MSG45).

**Step 5 — Signed-in actor is not a Member**

A signed-in Staff, Manager or Admin has no My vouchers list; the system shows that only members can save vouchers (MSG58) instead of a Save button.

**Step 6 — Voucher already saved**

Saving again creates no extra right and the list keeps one entry.

**Step 2 — No product in the program's scope is published**

The program is not shown in the public list. In My vouchers it is shown with the status "No products currently apply" (MSG47). A program whose targeted sizes are all no longer published is treated the same way.

**Step 2 — All uses of a voucher are used or held**

The voucher is still shown on the public cards and details, with the label "Fully claimed" and without a Save button (MSG47). This label is for display only; the quote and the order or booking still decide whether the voucher can be used.

**Step 5 — Voucher is not in the public list**

A voucher that is paused, not yet started, expired or has no published product in scope cannot be saved, even by its identifier; the system responds as if the voucher does not exist. A voucher saved earlier stays in My vouchers with its status.

**Step 7 — Saved voucher is expired, paused, fully used or not yet eligible**

My vouchers shows the status, including when all uses of the voucher, or all of the Member's own uses, are used or held. Being in My vouchers does not make the voucher usable.

**Product Pages Step 3 / Workshop Package Pages Step 3 — Automatic discount has a minimum above the price of one unit or one ticket**

The price is not reduced; the system shows a label "{discount} off orders from {minimum amount}".

**Product Pages Step 2 / Workshop Package Pages Step 2 — Promotions cannot be loaded**

The page shows the normal price without an error.

**Steps 5–7 — Session expired, load or save failure, or request for another Member's list**

The system asks the Member to sign in or retry (MSG02, MSG05), or rejects the request (MSG01). It does not report a save that was not confirmed. A rejected save is recorded as in UC-45.

#### Postconditions

- A saved voucher appears in the Member's own My vouchers list.
- Saving does not hold a use, use points or consume the limit, and does not guarantee the voucher can be used. A Member can still enter a code directly without saving it.
- A price shown on a product page or workshop package page is for viewing only; the amount paid is calculated again by the checkout quote or the workshop cart quote, and the workshop cart does not use the displayed package price.

#### Business Rules

BR-47-01, BR-47-02, BR-47-03, BR-47-04, BR-47-05

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-47-01 | Public cards show scope, discount, cap, minimum amount, validity and point combination; public details list only products that are currently published. A voucher whose uses are all used or held stays visible as "Fully claimed" and cannot be saved. |
| BR-47-02 | Only a signed-in Member can save vouchers, only to their own My vouchers list, and only vouchers that are in the public list; repeated saves create no extra right. |
| BR-47-03 | Saving reserves nothing and does not guarantee eligibility; eligibility is checked when the voucher is applied. |
| BR-47-04 | Product pages subtract only the largest automatic discount for one unit of each size, using the retail checkout rules; vouchers appear as labels only. The displayed price never replaces the checkout quote, and a failure to read promotions never blocks the product page. |
| BR-47-05 | Workshop package pages subtract the largest workshop automatic discount and then the workshop voucher with the largest discount for one ticket, labelled "Price after voucher {code}", for every viewer; points are never subtracted. The displayed price never replaces the workshop cart quote, and a failure to read promotions never blocks the page. |
