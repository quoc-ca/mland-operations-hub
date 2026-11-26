### 10.5 Record Custom Payment Outcome

| Use Case ID | UC-71 | Use Case Name | Record Custom Payment Outcome |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | System | Secondary Actors | Payment Gateway |

#### Description

As the System, I want to record the verified full payment of the active custom order quote or its expiry so that paid orders enter production accurately.

#### Trigger

The Payment Gateway sends a payment result for an approved custom order quote, or the quote deadline passes.

#### Preconditions

1. A payment was started for an approved quote (use case 69).

#### Postconditions

1. The order is paid in full and enters waiting for production, or remains unpaid/expired.

#### Normal Flow

1. System receives a verified payment result from Payment Gateway.
2. System matches the order and checks the amount against the active quote.
3. System marks the quote and order paid and records the transaction reference.
4. System moves the order to the waiting-for-production stage.

#### Alternative Flows

None.

#### Exception Flows

1a. Result is not verified or not successful → System does not mark the order paid.

2a. Late, replaced, or mismatched payment → System keeps the evidence for manual review, acknowledges the notification, and does not mark the order paid.

2b. Deadline passes without payment → System treats the quote as expired and creates no final balance.

#### Business Rules

BR-71-01, BR-71-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-71-01 | Only a verified payment of the exact amount of the latest, approved and unexpired quote for which it was started marks the order paid; other successful evidence is kept for manual review. |
| BR-71-02 | An expired quote cannot be paid; a new quote version is required. |
