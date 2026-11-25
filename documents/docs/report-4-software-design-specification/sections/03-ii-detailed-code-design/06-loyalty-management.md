## 6. Loyalty and Promotion Management (FE-06)

**Implementation status:** Backend/frontend implementation exists in the repository; current feature spec is the authority for voucher scope, branch restrictions, points and concurrency.

### 6.1 Class Diagram

{{r4-cd-fe06-loyalty-promotion width=100% align=center}}

The module owns promotion programs, Member voucher state, loyalty wallet/ledger and policy snapshots. Checkout and workshop booking use exported benefit facades and never write loyalty repositories directly.

### 6.2 Sequence Diagram — Manager Creates and Publishes Promotion

{{r4-sd-fe06-create-promotion width=100% align=center}}

### 6.3 Sequence Diagram — Apply Voucher and Points at Checkout

{{r4-sd-fe06-apply-benefits width=100% align=center}}

### 6.4 Sequence Diagram — Settle Benefits after Verified Payment

{{r4-sd-fe06-settle-benefits width=100% align=center}}
