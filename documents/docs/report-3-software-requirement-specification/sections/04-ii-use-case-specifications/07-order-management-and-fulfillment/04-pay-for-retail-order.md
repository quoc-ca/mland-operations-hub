### 7.4 Pay for Retail Order

| Use Case ID | UC-55 | Use Case Name | Pay for Retail Order |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | VNPay Payment Gateway |

#### Description

As a Member, I want to pay the full frozen retail amount through VNPay so that matching verified evidence completes exactly one sale while expiry or late evidence receives safe handling.

#### Trigger

A Member is sent to VNPay after Đặt hàng or selects Thanh toán on an unpaid order in Chờ thanh toán.

#### Preconditions

1. The platform is available and a complete retail order, original quote, and hold/attempt exist.
2. Human initiation requires verified active Member ownership; signed callbacks require no live session.
3. Applicable VNPay, signature, terminal-status, and recovery contracts are approved for operation.

#### Postconditions

1. Verified payment accepted strictly before hold expiry marks the order paid and consumes held quantities/benefits once.
2. Without timely accepted success, unpaid holds expire at the original boundary; late success becomes a reconciliation exception.
3. Duplicate evidence repeats no settlement effects and never undoes an accepted sale.

#### Normal Flow

1. Member initiates payment for an unpaid retail order from checkout.
2. System checks ownership and retrieves the frozen payable amount, reference, and original hold.
3. System opens the stored VNPay link valid until the 10-minute hold expires.
4. Member completes payment on VNPay and returns to Purchase Order History.
5. VNPay sends signed IPN evidence to the system.
6. System verifies signature, reference, frozen amount, currency, status, and active hold bindings.
7. System accepts payment strictly before hold expiry and settles held quantities and benefits once.
8. System cleans up unchanged purchased cart lines and notifies the Member at approved milestones.
9. System displays the paid order in Purchase Order History for Staff preparation.

#### Alternative Flows

1a. Member presses Thanh toán on an order in Chờ thanh toán → System reopens the same stored VNPay link without creating a second transaction or new deadlines.

4a. Member leaves VNPay without paying → Purchase Order History opens Chờ thanh toán with a countdown; Member may press Thanh toán to reopen the link or Hủy đơn to release held items back to the cart (late payment goes to Manager reconciliation).

6a. Catalogue price changes or item unpublishes after valid hold → System uses the original frozen quote and deadlines without repricing.

#### Exception Flows

2a. Wrong owner, inactive account, or ineligible action → System denies access without exposing another order or creating a new attempt.

3a. Initiation failure or uncertain provider response → System displays the recorded pending/error state and retains the original eligible hold without new deadlines.

4b. Browser return or Member claim reports success without verified evidence → System displays informational status only and does not mark paid, deduct stock, or start preparation.

5a. No valid IPN recorded when the 10-minute hold ends → Signed QueryDR checks an opened unconfirmed payment about every 5 minutes for 60 minutes; verified failure/expiry releases the order, while verified success or unresolved payment goes to Manager reconciliation. (Open difference checked 2026-11-25: Feature-008 requires expiry at 10 minutes; current code keeps opened payments in QueryDR recovery then queues for Manager.)

6b. Invalid signature, reference, amount, currency, status, or unknown payment → System rejects settlement authority, retains actual outcome, and allows no manual paid override.

6c. Duplicate, reordered, or concurrent IPN/QueryDR evidence → System preserves an accepted sale and records a no-change duplicate result without repeating stock/point/quota effects.

7a. First acceptance at/after expiry or hold already released → System queues a payment exception for Manager reconciliation without reviving the hold, fulfilling, or auto-refunding.

7b. No timely accepted success (not started, verified failure, or expiry) → System expires the unpaid order and releases held quantities, voucher, and points once.

8a. Settlement, state, read, or optional email failure → System preserves established evidence and completed sales, showing safe guidance while keeping subsequent cart edits intact.

#### Business Rules

BR-55-01, BR-55-02, BR-55-03, BR-55-04, BR-55-05, BR-55-06, BR-55-07, BR-55-08, BR-55-09

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-55-01 | Only a matching valid signed successful VNPay IPN or eligible signed QueryDR confirms payment; browser returns and human assertions never do. |
| BR-55-02 | Verify the frozen full positive whole-VND amount, currency, reference and original variant/benefit bindings; never reprice or reassign accepted terms. |
| BR-55-03 | Use the single stored VNPay link of the checkout, valid until the original 10-minute hold expires; reopening it creates no second transaction. Eligible QueryDR recovery checks an opened but unconfirmed payment before the hold is released. |
| BR-55-04 | Complete verification and payment acceptance strictly before original hold expiry; first acceptance at/after expiry is an exception regardless of earlier receipt/provider timestamps. |
| BR-55-05 | Payment consumes held quantities/benefits once; duplicates, reordering, recovery and expiry races cannot repeat effects or both settle and release the same resources. |
| BR-55-06 | Release unpaid holds at expiry; late or retired/replaced evidence cannot revive holds, settle replacements or automatically fulfil. |
| BR-55-07 | Catalogue changes/unpublish preserve accepted valid held terms; duplicate evidence never undoes an accepted sale. |
| BR-55-08 | Unknown outcomes retain the original eligible hold; verified definitive failure releases it. Paid cart cleanup and optional notices follow shared policies without reversing sales. |
| BR-55-09 | Record each attempt and its actual outcome under the shared audit policy. |
