### 6.3 Redeem Loyalty Points

#### Primary Actors

- Member

#### Secondary Actors

- Checkout and Payment Workflow

#### Description

This use case allows a member to apply eligible loyalty points to an order during checkout. The system validates the member's available balance and the active loyalty policy, creates a temporary point hold for the checkout session, and applies the permitted value to the payable amount.

#### Preconditions

1. The member is signed in.
2. The member has an eligible order in checkout.
3. The member has enough spendable loyalty points for the selected redemption amount.
4. An active loyalty policy defines the conversion rate, minimum order value, and redemption limits.

#### Normal Flow

**Redeem Loyalty Points**

1. The member opens the loyalty point option during checkout.
2. The system displays the available points and the active redemption rules.
3. The member selects the amount of points to redeem.
4. The system validates the balance, conversion rate, minimum order value, maximum redemption limit, and order eligibility.
5. The system creates a temporary point hold for the checkout session.
6. The system recalculates and displays the payable order amount.
7. The member continues to payment.
8. After successful payment verification, the system converts the hold into a completed redemption transaction.

#### Alternative Flows

**Step 4 — Insufficient points**

1. The selected amount exceeds the member's spendable point balance.
2. The system rejects the redemption and asks the member to select a lower amount.

**Step 4 — Order does not meet the policy**

1. The order does not meet the minimum amount, product scope, or other loyalty policy conditions.
2. The system prevents redemption and explains the unmet condition.

**Step 4 — Redemption limit exceeded**

1. The selected points exceed the configured per-order or account limit.
2. The system restricts the amount to the permitted limit or asks the member to choose a lower amount.

**Step 7 — Payment fails or checkout expires**

1. Payment fails, is cancelled, or the checkout session expires.
2. The system releases the temporary point hold and does not complete the redemption.

**Step 4 — Balance changes during checkout**

1. The member's spendable balance changes before payment completion.
2. The system revalidates the balance and asks the member to confirm a new redemption amount.

#### Postconditions

- A valid redemption is applied to the order after successful payment verification.
- A temporary point hold is released when payment or checkout does not complete.
- The member's loyalty ledger records the completed or released transaction.
- The order may use one eligible voucher together with loyalty points.

#### Business Rules

- BR-40-01
- BR-40-02
- BR-40-03
- BR-40-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-40-01 | Loyalty points are non-cash and may be redeemed only according to the active loyalty policy. |
| BR-40-02 | A member may apply at most one eligible voucher to an order, and the voucher may be combined with loyalty points when policy conditions are satisfied. |
| BR-40-03 | Points must not be permanently debited before the related payment is successfully verified. |
| BR-40-04 | Redemption is subject to configured conversion, minimum order, per-order, and account-level limits. |

