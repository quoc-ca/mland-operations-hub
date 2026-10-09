### 6.5 Manage Voucher Code

#### Primary Actors

- Manager

#### Secondary Actors

- Admin

#### Description

This use case allows a manager to create and maintain voucher codes linked to a promotion campaign. The manager configures the voucher value, validity period, minimum order amount, usage quota, eligible scope, and activation status. The system ensures that voucher codes are unique and can be validated consistently during checkout.

#### Preconditions

1. The manager is authenticated.
2. The manager has permission to manage voucher codes.
3. The related promotion campaign exists or can be created during the operation.

#### Normal Flow

**Manage Voucher Code**

1. The manager opens the voucher code management function.
2. The system displays existing voucher codes and their statuses.
3. The manager creates a new voucher code or selects an existing code to update.
4. The manager enters or updates the code, discount type, discount value, validity period, minimum order amount, usage limit, and eligible scope.
5. The system validates the code format, uniqueness, campaign relation, date range, discount limit, and usage quota.
6. The manager saves the voucher or changes its activation status.
7. The system records the configuration change and lifecycle event in the audit log.
8. The system makes the voucher available for checkout validation when it is active and eligible.

#### Alternative Flows

**Step 5 — Duplicate voucher code**

1. The entered code already exists.
2. The system rejects the code and asks the manager to enter a unique value.

**Step 5 — Invalid voucher configuration**

1. The discount, validity period, minimum amount, scope, or quota is invalid.
2. The system highlights the invalid data and prevents saving.

**Step 6 — Voucher is expired, exhausted, or deactivated**

1. The manager deactivates the voucher or its validity or quota has ended.
2. The system marks the voucher as unavailable for new checkout validation.

**Step 7 — Save failure**

1. The system cannot persist the voucher configuration.
2. The system displays an error message and allows the manager to retry.

#### Postconditions

- The voucher code is stored with a valid configuration and lifecycle status.
- The voucher code is available only when it is active, within its validity period, and eligible for the order.
- Configuration and lifecycle changes are recorded for audit.
- Historical orders retain the voucher information applied at checkout.

#### Business Rules

- BR-42-01
- BR-42-02
- BR-42-03
- BR-42-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-42-01 | Voucher codes must be unique and must belong to a valid promotion campaign or promotion rule. |
| BR-42-02 | A voucher may be applied only when its status, validity period, eligibility, and usage quota are valid. |
| BR-42-03 | At most one voucher may be applied to a retail order. |
| BR-42-04 | An eligible voucher may be combined with loyalty point redemption when the active policy permits it. |

