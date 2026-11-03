### 6.6 View and Save Vouchers

#### Primary Actors

- Guest
- Member

#### Secondary Actors

- None

#### Description

This use case allows a Guest or Member to read public promotion cards and their conditions on product pages, workshop package pages and the promotions page, and allows a Member to save voucher codes to a personal Voucher Wallet for quick selection at checkout.

#### Preconditions

1. The platform is available. Viewing requires no sign-in; saving requires a signed-in Member.

#### Normal Flow

**View and Save Vouchers**

1. The actor opens a product page, a workshop package page or the promotions page.
2. The system displays the cards of public programs: scope (Buy products or Book workshop), discount, maximum discount, minimum amount, validity and whether points can be combined.
3. The actor opens the conditions of a card.
4. The system shows the products or size variants in scope, or the packages, branches, dates and sessions, plus the total limit and uses per Member. Only products that are currently published are listed.
5. The Member selects Save.
6. The system adds the voucher to the Member's Voucher Wallet and confirms (MSG46).
7. The Member opens the Voucher Wallet; the system shows each saved voucher with its current conditions and status.

#### Alternative Flows

**Step 5 — Guest selects Save**

The system asks the Guest to sign in (MSG45).

**Step 6 — Voucher already saved**

Saving again creates no extra right and the wallet keeps one entry.

**Step 2 — No product in the program's scope is published**

The program is not shown in the public list. In the Voucher Wallet it is shown with the status "No products currently apply" (MSG47).

**Step 7 — Saved voucher is expired, paused or not yet eligible**

The wallet shows the status. Being in the wallet does not make the voucher usable.

**Steps 5–7 — Session expired, load or save failure, or request for another Member's wallet**

The system asks the Member to sign in or retry (MSG02, MSG05), or rejects the request (MSG01). It does not report a save that was not confirmed.

#### Postconditions

- A saved voucher appears in the Member's own wallet.
- Saving does not hold a use, use points or consume the limit, and does not guarantee the voucher can be used. A Member can still enter a code directly without saving it.

#### Business Rules

- BR-47-01
- BR-47-02
- BR-47-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-47-01 | Public cards show scope, discount, cap, minimum amount, validity and point combination; public details list only products that are currently published. |
| BR-47-02 | Only a signed-in Member can save vouchers, only to their own wallet; repeated saves create no extra right. |
| BR-47-03 | Saving reserves nothing and does not guarantee eligibility; eligibility is checked when the voucher is applied. |
