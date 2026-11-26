### 3.5 Look Up Booking Details

| Use Case ID | UC-23 | Use Case Name | Look Up Booking Details |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | Mail Gateway |

#### Description

As a Guest or Member, I want to open my booking group so that I can see its tickets, their status and money, and the check-in QR codes.

#### Trigger

An actor opens their Member booking list, submits group lookup credentials, or opens a Magic Link.

#### Preconditions

1. The platform is available.
2. A booking group exists.
3. The actor is the owning Member, holds the group code with one stored contact value, or holds the Magic Link.

#### Postconditions

1. The actor sees the read-only group with each ticket, or a safe refusal.
2. No booking, payment, or capacity changes.

#### Normal Flow

1. Actor opens the Member booking list, enters the group code with one contact value, or opens the Magic Link.
2. System verifies ownership, the contact proof, or the Magic Link.
3. System displays the group and each ticket with status, attendance, collected amount, and remaining balance.
4. System displays each ticket QR code to the buyer.
5. Actor requests the confirmation email again from Booking Lookup.

#### Alternative Flows

3a. Group is still awaiting payment → Buyer may retry payment or release the hold from this view.

5a. Actor opened the read-only Magic Link view → Confirmation email resend action is not offered.

#### Exception Flows

2a. Proof does not match → System refuses without saying which value was wrong and rate-limits Guest attempts.

2b. Magic Link is expired or revoked → System refuses the link; buyer verifies via Booking Lookup before requesting another email.

#### Business Rules

BR-19-01, BR-19-02, BR-19-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-19-01 | A Guest needs the group code and exactly one stored contact value, or the Magic Link; the group code alone is never enough. |
| BR-19-02 | The Member list shows at most 30 of the Member own groups, newest first. |
| BR-19-03 | Only the buyer sees ticket QR codes; a ticket recipient QR email gives no group view. |
