### 6.9 Release or Refund Promotion Benefits

| Use Case ID | UC-50 | Use Case Name | Release or Refund Promotion Benefits |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | System | Secondary Actors | Order Management and Fulfillment, Workshop Management, Member |

#### Description

As the System, I want to release held benefits or refund finalized workshop benefits once when transactions fail or cancel validly so that Members keep their unused entitlements.

#### Trigger

Order Management and Fulfillment or Workshop Management confirms an expiry or eligible cancellation.

#### Preconditions

1. The order or booking held or used loyalty points or a voucher.
2. Order Management or Workshop Management confirmed the transaction outcome.

#### Postconditions

1. Held or used benefits are returned at most once with linked history entries.
2. Any subsequent order or booking evaluates current promotion conditions anew.

#### Normal Flow

**Release Held Benefits**

1. System receives confirmation of an expired order or booking, or a pre-payment retail cancellation.
2. System returns held points to the available balance and returns the held voucher use.
3. System records a released entry linked to the transaction.

**Release Benefits of a Branch Order**

1. System receives confirmation that a split-checkout branch order expired or was cancelled unpaid.
2. System returns that branch order's held points and closes its earning share.
3. System releases the voucher use after all discounted branch orders end unpaid.

**Refund Finalized Workshop Benefits**

1. System receives confirmation of a valid workshop booking cancellation before completion.
2. System releases held benefits, or refunds exact deducted points linked to the original deduction.
3. System returns one unexpired voucher use at cancellation decision.
4. System excludes the cancelled booking from program usage counts while preserving history.

**Refund Benefits of a Workshop Ticket**

1. System receives confirmation of a valid ticket cancellation or Mland cancellation.
2. System refunds exact points used by that ticket without affecting other tickets.
3. System returns the group's unexpired voucher use once upon Mland cancellation or full ticket cancellation.

#### Alternative Flows

1a. Transaction is a Guest workshop booking → System releases or cancels it with no points or voucher use.

1b. Outcome arrives twice or was partly processed → System completes only the missing part without duplicating returns.

1c. Payment and cancellation occur close together → System follows the confirmed owning-feature outcome without creating new deadlines.

#### Exception Flows

1a. Outcome is a no-show, unconfirmed, or invalid cancellation → System returns no benefits and awaits confirmation.

1b. Whole-transaction result conflicts with per-ticket or per-branch results → System rejects the conflicting path.

1c. Workshop is completed or retail order is returned → System performs no benefit refund or point recovery.

#### Business Rules

BR-50-01, BR-50-02, BR-50-03, BR-50-04, BR-50-05

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-50-01 | Expiry, or cancellation of a retail order before payment, without accepted payment releases held points and voucher uses exactly once, without recording them as used. A released or refunded use of a voucher that must be saved returns to the Member's saved voucher. |
| BR-50-02 | A confirmed valid workshop cancellation before completion, or a cancellation by Mland, refunds exactly the points deducted and, only if the voucher has not expired when the cancellation is decided, one voucher use to the Member and the program; held benefits are always released. It does not convert points to money or extend or reactivate vouchers. |
| BR-50-03 | Release and refund results are tracked per benefit so that retries complete only the missing part and never return a benefit twice. |
| BR-50-04 | No-shows, unconfirmed or invalid cancellations and completed transactions return no benefits; the outcome is decided only by the confirmed result from the owning feature. |
| BR-50-05 | For a split retail checkout or a multi-ticket booking, points are released or refunded per branch order or per ticket. The voucher use returns once: for a checkout, when every branch order with a share of the discount ends unpaid; for a booking, when any ticket is cancelled by Mland or every ticket ends in a valid or Mland cancellation, under the validity condition of BR-50-02. |
