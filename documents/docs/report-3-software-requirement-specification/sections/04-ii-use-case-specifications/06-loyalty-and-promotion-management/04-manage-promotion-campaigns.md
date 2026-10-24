### 6.4 Manage Promotion Campaigns

#### Primary Actors

- Manager

#### Secondary Actors

- Admin

#### Description

This use case allows a manager to create, update, activate, pause, or deactivate promotion campaigns. A campaign can define its discount type, value, validity period, eligible products or workshops, minimum order amount, usage limit, and branch or customer scope.

#### Preconditions

1. The manager is authenticated.
2. The manager has permission to manage promotion campaigns.
3. The system can access the promotion configuration and audit log.

#### Normal Flow

**Manage Promotion Campaigns**

1. The manager opens the promotion campaign management function.
2. The system displays existing campaigns and their statuses.
3. The manager creates a new campaign or selects an existing campaign to update.
4. The manager enters or updates the campaign name, discount rule, validity period, eligibility conditions, usage limit, and applicable products or workshops.
5. The system validates the campaign data and checks for conflicting active campaigns.
6. The manager saves the campaign or changes its lifecycle status.
7. The system records the manager, timestamp, configuration changes, and status transition in the audit log.
8. The system makes the campaign available to the customer-facing promotion validation flow when it is active.

#### Alternative Flows

**Step 5 — Invalid campaign data**

1. A required field is missing or a discount, date, limit, or eligibility value is invalid.
2. The system rejects the campaign and highlights the invalid fields.

**Step 5 — Conflicting active campaign**

1. The campaign conflicts with an existing campaign under the configured promotion rules.
2. The system asks the manager to revise the scope, validity period, or priority.

**Step 6 — Pause or deactivate campaign**

1. The manager chooses to pause or deactivate the campaign.
2. The system changes the status and prevents new voucher validation under the inactive campaign while preserving historical transactions.

**Step 7 — Save failure**

1. The system cannot persist the campaign configuration.
2. The system displays an error message and keeps the unsaved values available for retry.

#### Postconditions

- The promotion campaign is stored with a valid lifecycle status.
- Changes are recorded in the audit log.
- Only active and eligible campaigns are considered by customer-facing discount validation.
- Historical orders retain the promotion information applied at the time of checkout.

#### Business Rules

- BR-41-01
- BR-41-02
- BR-41-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-41-01 | Only authorized managers may create, update, activate, pause, or deactivate promotion campaigns. |
| BR-41-02 | A campaign must have valid discount, eligibility, validity, and usage-limit configuration before activation. |
| BR-41-03 | Deactivating a campaign prevents new use but does not alter historical promotion transactions. |

