### 3.24 Review Late Payment

| Use Case ID | UC-92 | Use Case Name | Review Late Payment |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | Mail Gateway |

#### Description

As a Manager, I want to confirm or refund a late booking payment so that paying customers get a seat or their money.

#### Trigger

A Manager opens the queue of late payments waiting for a decision.

#### Preconditions

1. The Manager is authenticated and active.
2. A verified group payment arrived after the seat hold ended.
3. The System could not confirm the booking group automatically.

#### Postconditions

1. The booking group is confirmed, or a 100% refund request is opened.
2. The decision, reason, Manager, and time are recorded.

#### Normal Flow

1. Actor opens the late payment queue.
2. System displays each booking code, amount, failed conditions, and answer deadline.
3. Actor selects a review and a standard decision reason.
4. Actor chooses to confirm the payment.
5. System rechecks the start time, amount, and seat capacity.
6. System holds the seats again and confirms the whole booking group.

#### Alternative Flows

4a. Actor chooses a refund → System opens a 100% bank refund request, processed in UC-90.

#### Exception Flows

3a. Review is already decided → System refuses a second decision.

4b. A condition other than arrival time or duplicate booking failed on arrival → System allows refund only.

5a. Start time, amount, or capacity check fails, or seats cannot be held → System refuses confirmation.

4c. Refund request cannot be opened → System reports the failure and keeps the review pending.

#### Business Rules

BR-92-01, BR-92-02, BR-92-03, BR-92-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-92-01 | Only a Manager decides a late payment review. |
| BR-92-02 | The System confirms a late payment automatically only when all conditions hold; otherwise the payer is emailed and Managers are alerted. |
| BR-92-03 | A booking group is confirmed for all its lines or not at all; money is never moved to another booking. |
| BR-92-04 | The result is due within 24 hours of the payment, or before the earliest session if sooner. |
