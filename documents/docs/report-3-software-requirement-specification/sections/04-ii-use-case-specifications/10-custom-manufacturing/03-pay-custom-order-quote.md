### 10.3 Pay Custom Order Quote

| Use Case ID | UC-69 | Use Case Name | Pay Custom Order Quote |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | Payment Gateway |

#### Description

As a Member, I want to pay the whole approved quote through VNPay from my custom order within a 10-minute payment session so that production can begin.

#### Trigger

A Member opens their custom order with an approved, unexpired quote and selects full payment.

#### Preconditions

1. The Member is signed in and owns the custom order.
2. The latest quote is approved, unpaid, and not expired (quote validity: 24–72 hours).

#### Postconditions

1. A payment attempt exists for the exact quoted amount.
2. The individual VNPay gateway checkout session is set to expire in 10 minutes (`vnp_ExpireDate = min(now + 10m, quote.expiresAt)`), decoupled from quote validity.
3. The browser return URL carries payment outcome query parameters triggering immediate confirmation and reactive status polling until verified IPN completion under UC71.

#### Normal Flow

1. Member opens the custom order.
2. System displays the quoted amount, quote version, and quote expiry deadline.
3. Member selects full payment.
4. System checks ownership, quote state, and quote deadline.
5. System creates a VNPay payment for the exact quoted amount with a 10-minute session expiry cap and redirects the browser to VNPay.
6. Member completes payment on VNPay.
7. Gateway redirects browser back to `/account/my-custom-orders/{orderRef}?vnp_ResponseCode=00&vnp_TransactionStatus=00`.
8. System displays an immediate confirmation banner ("Thanh toán thành công! Hệ thống đang đồng bộ xác nhận...") and starts reactive polling every 1.5 seconds.
9. Verified payment webhook (IPN) arrives and records `PAID` under UC71; polling updates the screen, hides the payment button, and reveals production progress.

#### Alternative Flows

4a. Member requests a new quote after quote expiry → System returns the order to waiting for a quote while keeping earlier quote versions.

5a. Member navigates Back from gateway to order page → Browser fires `pageshow` restoration event, resetting action loading state and instantly unfreezing the payment button.

5b. 10-minute VNPay session expires without payment → Gateway session cancels safely; while the 24–72h quote remains valid, Member may click full payment again to generate a fresh 10-minute payment link.

#### Exception Flows

4b. Actor does not own the order → System denies access.

4c. Quote not approved or order already paid → System rejects the payment.

4d. Quote has expired → System marks the quote expired and creates no payment; Member may request a new quote.

4e. Member requests a new quote while a quote is still valid → System refuses the request.

#### Business Rules

BR-69-01, BR-69-02, BR-69-03

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
| BR-69-03 | VNPay gateway session duration is capped at 10 minutes, decoupled from quote validity (24–72h); browser back navigation restores payment availability without page refresh. |
