### 3.3 Create Workshop Booking

| Use Case ID | UC-21 | Use Case Name | Create Workshop Booking |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | Payment Gateway, Mail Gateway |

#### Description

As a Guest or Member, I want to book workshop packages with their sessions and participant counts so that the system holds the seats and creates one ticket per person.

#### Trigger

An actor selects a published workshop package and starts booking.

#### Preconditions

1. The platform is available.
2. The package is published and active at the chosen branch.
3. The required sessions are open and have enough capacity.
4. A Guest provides a contact email and phone. A Member may use verified profile information.

#### Postconditions

1. A booking group and one ticket per person are created, all or nothing.
2. The seats are held for 12 minutes while payment is pending.
3. The group stays unconfirmed until UC-22 Pay Workshop Deposit succeeds.

#### Normal Flow

1. Actor selects a published package.
2. Actor selects the branch, the complete required session set, and the participant count.
3. Actor adds the line to the cart.
4. System quotes the cart with each ticket price and booking-time collection. The quote is valid for 30 minutes.
5. Actor enters or confirms contact details and accepts the non-refundable condition.
6. Actor confirms the cart.
7. System validates and holds every line in one transaction, then creates the booking group and tickets.
8. System directs the actor to UC-22 Pay Workshop Deposit.

#### Alternative Flows

3a. Actor selects Book now → System quotes this line directly (step 4).

3b. Actor adds further lines → Actor repeats steps 1–3.

4a. Member applies one voucher and loyalty points → System includes them in the quote.

8a. Booking-time collection is 0 VND → System confirms the group at once, without UC-22.

#### Exception Flows

2a. Package is not active at the branch → System refuses the line; actor chooses another branch or package.

4a. Quote is expired, already used, or changed → System holds nothing and asks the actor to quote again.

6a. Contact details are missing or invalid → System shows validation errors; the cart stays editable.

7a. A line lacks capacity or eligibility → System lists the failing lines and holds nothing; the cart stays editable.

8b. Payment is not verified before the hold expires → Group is not confirmed and the seats are released; late payment is handled by UC-22.

#### Business Rules

BR-40-01, BR-40-02, BR-40-03, BR-40-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-40-01 | A booking line requires a package, its complete required-session set, and a participant count. |
| BR-40-02 | All lines are validated and held together; if one fails, nothing is held. |
| BR-40-03 | Each ticket collects the smaller of its final price and 200,000 VND at booking time. The remaining amount is collected later and is not multiplied by the number of sessions. |
| BR-40-04 | The group has one 12-minute hold; editing or retrying payment never extends it. |
