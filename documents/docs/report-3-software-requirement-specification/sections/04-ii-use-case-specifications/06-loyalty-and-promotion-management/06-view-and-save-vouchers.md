### 6.6 View and Save Vouchers

#### Primary Actors

Guest, Member.

#### Secondary Actors

None.

#### Description

This use case allows a Guest or Member to read public promotion cards and their conditions on product pages, workshop package pages and the promotions page, and allows a Member to save voucher codes to a personal Voucher Wallet for quick selection at checkout. On the public product cards and product details provided by Product Management, it also shows the active product promotions next to the price.

This use case includes the following sub-flows:

- View and Save Vouchers
- View Promotions on Product Pages

#### Preconditions

1. The platform is available. Viewing requires no sign-in; saving requires a signed-in Member.

#### Normal Flow

**View and Save Vouchers**

1. The actor opens a product page, a workshop package page or the promotions page.
2. The system displays the cards of public programs: scope (Buy products or Book workshop), discount, maximum discount, minimum amount, validity and whether points can be combined. A product voucher limited to branches shows "Applies at: {branch names}".
3. The actor opens the conditions of a card.
4. The system shows the products or size variants in scope and, for a product voucher, its branches ("All branches" when not limited), or the packages, branches, dates and sessions of a workshop voucher, plus the total limit and uses per Member. Only products that are currently published are listed.
5. The Member selects Save.
6. The system adds the voucher to the Member's Voucher Wallet and confirms (MSG46).
7. The Member opens the Voucher Wallet; the system shows each saved voucher with its current conditions and status.

**View Promotions on Product Pages**

1. The actor views a product card in the product list or the home page strip, or opens a product's details.
2. The system reads the active product programs and finds, for each size, the programs whose product, size and branch scope it matches.
3. For each size, the system subtracts the largest applicable automatic discount for one unit, using the same rules as the retail checkout: a percentage rounded down, never above the cap or the price.
4. The system shows the reduced price, with the original price struck through. Vouchers are not subtracted; up to two voucher labels show their discount. A program that applies to only some sizes names those sizes.

#### Alternative Flows

**Step 5 — Guest selects Save**

The system asks the Guest to sign in (MSG45).

**Step 6 — Voucher already saved**

Saving again creates no extra right and the wallet keeps one entry.

**Step 2 — No product in the program's scope is published**

The program is not shown in the public list. In the Voucher Wallet it is shown with the status "No products currently apply" (MSG47).

**Step 5 — Voucher is not in the public list**

A voucher that is paused, not yet started, expired or has no published product in scope cannot be saved, even by its identifier; the system responds as if the voucher does not exist. A voucher saved earlier stays in the wallet with its status.

**Step 7 — Saved voucher is expired, paused, fully used or not yet eligible**

The wallet shows the status, including when all uses of the voucher, or all of the Member's own uses, are used or held. Being in the wallet does not make the voucher usable.

**Product Pages Step 3 — Automatic discount has a minimum above the price of one unit**

The price is not reduced; the system shows a label "{discount} for orders from {minimum amount}".

**Product Pages Step 2 — Promotions cannot be loaded**

The page shows the normal price without an error.

**Steps 5–7 — Session expired, load or save failure, or request for another Member's wallet**

The system asks the Member to sign in or retry (MSG02, MSG05), or rejects the request (MSG01). It does not report a save that was not confirmed. A rejected save is recorded as in UC-45.

#### Postconditions

- A saved voucher appears in the Member's own wallet.
- Saving does not hold a use, use points or consume the limit, and does not guarantee the voucher can be used. A Member can still enter a code directly without saving it.
- A price shown on a product page is for viewing only; the amount paid is calculated again at checkout.

#### Business Rules

BR-47-01, BR-47-02, BR-47-03, BR-47-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-47-01 | Public cards show scope, discount, cap, minimum amount, validity and point combination; public details list only products that are currently published. |
| BR-47-02 | Only a signed-in Member can save vouchers, only to their own wallet; repeated saves create no extra right. |
| BR-47-03 | Saving reserves nothing and does not guarantee eligibility; eligibility is checked when the voucher is applied. |
| BR-47-04 | Product pages subtract only the largest automatic discount for one unit of each size, using the retail checkout rules; vouchers appear as labels only. The displayed price never replaces the checkout quote, and a failure to read promotions never blocks the product page. |
