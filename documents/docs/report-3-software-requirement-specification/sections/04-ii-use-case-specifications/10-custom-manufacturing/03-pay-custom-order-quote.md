### 10.3 Pay Custom Order Quote

| Use Case ID | UC-69 | Use Case Name | Pay Custom Order Quote |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | Payment Gateway |

#### Description

As a Member, I want to pay the whole approved quote through VNPay from my custom order so that production can begin.

#### Trigger

A Member opens their custom order with an approved, unexpired quote and selects full payment.

#### Preconditions

1. The Member is signed in and owns the custom order.
2. The latest quote is approved, unpaid, and not expired.

#### Postconditions

1. A payment attempt exists for the exact quoted amount.
2. The browser return alone does not mark the order paid; verified outcome follows UC71.

#### Normal Flow

1. Member opens the custom order.
2. System displays the quoted amount, quote version, and payment deadline.
3. Member selects full payment.
4. System checks ownership, quote state, and payment deadline.
5. System creates a VNPay payment for the exact quoted amount and opens VNPay.
6. System records the verified payment outcome under UC71.

#### Alternative Flows

4a. Member requests a new quote after expiry → System returns the order to waiting for a quote while keeping earlier quote versions.

#### Exception Flows

4b. Actor does not own the order → System denies access.

4c. Quote not approved or order already paid → System rejects the payment.

4d. Quote has expired → System marks the quote expired and creates no payment; Member may request a new quote.

4e. Member requests a new quote while a quote is still valid → System refuses the request.

#### Business Rules

BR-69-01, BR-69-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-69-01 | A custom order is paid in one payment equal to the quote; there is no deposit, partial payment or later balance. |
| BR-69-02 | Only an approved, unexpired quote of an own order can be paid. |
